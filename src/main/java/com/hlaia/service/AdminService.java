package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.FolderMapper;
import com.hlaia.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 【管理员业务逻辑层】—— 处理管理员后台的所有核心业务逻辑
 *
 * AdminService 与普通用户 Service 的核心区别：
 *   普通用户 Service（如 FolderService）的所有方法都需要做权限校验：
 *   先通过 getFolderForUser() 确认资源属于当前用户，再执行操作。
 *   这是因为普通用户只能操作自己的数据。
 *
 *   而 AdminService 代表的是"管理员"身份，管理员可以操作任何用户的数据：
 *   - 查看任意用户的列表（分页查询所有用户）
 *   - 查看任意用户的文件夹树（不需要 getFolderForUser 校验）
 *   - 删除任意用户的文件夹（不需要确认文件夹属于哪个用户）
 *   - 封禁/解封任意用户
 *
 *   管理员操作的权限保障在 Controller 层之上——由 Spring Security 的 SecurityConfig 保证：
 *   SecurityConfig 中配置了 /api/admin/** 路径需要 hasRole("ADMIN")，
 *   只有拥有 ADMIN 角色的用户才能访问这些接口，非管理员请求会被直接拦截返回 403。
 *
 *   所以 AdminService 内部不需要再做角色校验，直接执行操作即可。
 *
 * @Service 注解的作用：
 *   告诉 Spring "这是一个业务逻辑类，请创建它的实例并纳入容器管理"。
 *   这样 AdminController 就可以通过构造器注入来使用它。
 *
 * @RequiredArgsConstructor 注解的作用：
 *   Lombok 自动为所有 final 字段生成构造方法。
 *   Spring 看到"只有一个构造方法"时，会自动进行依赖注入。
 *   等价于手写：
 *     public AdminService(UserMapper userMapper, FolderMapper folderMapper, FolderService folderService) {
 *         this.userMapper = userMapper;
 *         this.folderMapper = folderMapper;
 *         this.folderService = folderService;
 *     }
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    // 依赖注入：通过 final + @RequiredArgsConstructor 实现
    // final 表示这些字段必须在构造方法中赋值，赋值后不可修改（不可变性保证）

    /** 用户 Mapper：用于查询和管理用户数据 */
    private final UserMapper userMapper;

    /** 文件夹 Mapper：用于删除用户的文件夹 */
    private final FolderMapper folderMapper;

    /**
     * 文件夹 Service：复用已有的文件夹树构建逻辑
     * 直接调用 FolderService.getFolderTree(userId) 即可获取任意用户的文件夹树，
     * 不需要在 AdminService 中重复实现树形结构的构建算法。
     * 这就是 Service 层分层的优势——逻辑可以在不同的地方复用。
     */
    private final FolderService folderService;

    /**
     * 分页查询用户列表 —— 管理员后台最常用的功能
     *
     * 什么是分页（Pagination）？为什么要分页？
     *   当系统有大量用户（如 10000 个）时，如果一次性查询所有用户返回给前端：
     *   1. 数据库查询慢（需要扫描全部数据）
     *   2. 网络传输慢（数据量大，JSON 序列化耗时长）
     *   3. 前端渲染慢（一次性渲染 10000 行 DOM 元素会卡顿）
     *   4. 用户体验差（用户通常只需要看第一页的数据）
     *
     *   分页就是把数据分成固定大小的"页"，每次只查询一页的数据。
     *   例如 page=1, size=20 表示查询第 1 页，每页 20 条数据。
     *   数据库会使用 LIMIT 和 OFFSET 实现：
     *   SELECT * FROM user ORDER BY created_at DESC LIMIT 20 OFFSET 0  -- 第1页
     *   SELECT * FROM user ORDER BY created_at DESC LIMIT 20 OFFSET 20 -- 第2页
     *
     * Page 对象的工作原理（MyBatis-Plus 提供的分页模型）：
     *   Page<T> 对象包含以下关键信息：
     *   - records：当前页的数据列表（List<T>）
     *   - current：当前页码（从 1 开始）
     *   - size：每页大小（每页多少条数据）
     *   - total：总记录数（数据库中总共有多少条数据）
     *   - pages：总页数（自动计算：total / size，向上取整）
     *
     *   MyBatis-Plus 的 selectPage 方法会自动执行两条 SQL：
     *   1. SELECT COUNT(*) FROM user ... —— 查询总数（用于计算总页数）
     *   2. SELECT * FROM user ... LIMIT 20 OFFSET 0 —— 查询当前页数据
     *   两条 SQL 都使用相同的查询条件（WHERE、ORDER BY 等），保证数据一致。
     *
     * 为什么按创建时间倒序排列（orderByDesc）？
     *   管理员通常最关心最新注册的用户，倒序排列让新用户显示在第一页。
     *
     * @param page 页码（从 1 开始，1 表示第一页）
     * @param size 每页大小（每页显示多少条数据）
     * @return 分页结果（包含用户列表和分页信息）
     */
    public Page<UserResponse> getUserList(int page, int size) {
        // ============ 第一步：查询数据库，获取分页数据 ============
        // Page<User> 指定页码和每页大小，LambdaQueryWrapper 指定查询条件和排序
        Page<User> userPage = userMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));

        // ============ 第二步：将 Entity 分页对象转换为 DTO 分页对象 ============
        // 创建一个新的 Page<UserResponse>，保留分页信息（页码、大小、总数）
        // 但 records（数据列表）需要从 List<User> 转换为 List<UserResponse>
        Page<UserResponse> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());

        // ============ 第三步：转换数据列表 ============
        // userPage.getRecords() 返回当前页的 User 实体列表
        // stream().map(this::toUserResponse) 对每个 User 调用 toUserResponse 方法进行转换
        // collect(Collectors.toList()) 把 Stream 收集回 List
        result.setRecords(userPage.getRecords().stream().map(this::toUserResponse).collect(Collectors.toList()));
        return result;
    }

    /**
     * 查看指定用户的文件夹树 —— 管理员可以查看任何用户的文件夹结构
     *
     * 管理员操作与普通用户操作的区别：
     *   普通用户在 FolderController 中获取文件夹树时：
     *     @AuthenticationPrincipal Long userId  → 只能获取自己的文件夹树
     *     userId 从 JWT Token 中提取，无法伪造
     *
     *   管理员在 AdminController 中获取文件夹树时：
     *     @PathVariable Long userId  → 可以指定任意用户的 ID
     *     管理员输入 userId=5，就能看到 ID 为 5 的用户的文件夹树
     *
     *   为什么 AdminService 不需要像 FolderService 那样做 getFolderForUser 权限校验？
     *   - FolderService 的 getFolderForUser 确保用户只能操作自己的数据
     *   - AdminService 代表管理员，管理员有权限操作所有用户的数据
     *   - 权限控制在 SecurityConfig 层面已经完成（只有 ADMIN 角色才能访问 /api/admin/**）
     *   - 所以 Service 层不需要再次校验
     *
     * @param userId 要查看的用户 ID（由管理员在请求 URL 中指定）
     * @return 该用户的文件夹树
     */
    public List<FolderTreeResponse> getUserFolderTree(Long userId) {
        // 直接复用 FolderService 的 getFolderTree 方法
        // FolderService.getFolderTree 中的查询条件 .eq(Folder::getUserId, userId)
        // 会自动只查询指定用户的文件夹，不需要额外的权限校验
        return folderService.getFolderTree(userId);
    }

    /**
     * 删除用户的文件夹 —— 管理员可以删除任何用户的文件夹
     *
     * 与 FolderService.deleteFolder 的区别：
     *   FolderService.deleteFolder(userId, folderId)：
     *     需要先调用 getFolderForUser 验证文件夹是否属于当前用户
     *     如果不属于，抛出 FOLDER_NOT_FOUND 异常
     *
     *   AdminService.deleteUserFolder(folderId)：
     *     只需要 folderId，不需要 userId
     *     管理员有权删除任何用户的文件夹，所以不需要验证归属
     *
     * @Transactional 注解的作用：
     *   保证方法内的数据库操作在一个事务中执行。
     *   如果执行过程中出现异常，所有操作都会回滚（撤销），
     *   保证数据的一致性。
     *
     * @param folderId 要删除的文件夹 ID
     */
    @Transactional
    public void deleteUserFolder(Long folderId) {
        // 直接根据 ID 删除，不做归属校验（管理员权限）
        // 注意：这是简单的物理删除，不会级联删除子文件夹和书签
        // 如果需要级联删除，需要先递归查找所有子文件夹 ID，再批量删除
        folderMapper.deleteById(folderId);
    }

    /**
     * 封禁用户 —— 将用户状态设置为"封禁"
     *
     * status 字段的设计思路：
     *   User 实体中 status 字段的含义：
     *   - 0 = 正常状态（用户可以正常登录和使用系统）
     *   - 1 = 封禁状态（用户无法登录，所有接口返回"用户已被封禁"）
     *
     *   为什么用 0/1 而不是布尔值或字符串？
     *   - 数据库中用 TINYINT(1) 存储，占用空间最小（1 字节）
     *   - Java 中用 Integer 包装类型（不能用 int，因为数据库可能为 NULL）
     *   - 0/1 是最常见的状态标记方式，简单直观
     *   - 如果以后需要更多状态（如 2=待审核、3=已注销），扩展也很方便
     *
     *   为什么不用布尔值 boolean？
     *   - 数据库没有原生的布尔类型（MySQL 的 BOOLEAN 实际是 TINYINT(1)）
     *   - 用 Integer 更灵活，未来可以扩展为多种状态
     *
     * 封禁后的效果：
     *   在登录接口（AuthService）中，认证成功后会检查用户状态：
     *   if (user.getStatus() != 0) throw new BusinessException(ErrorCode.USER_BANNED);
     *   这样被封禁的用户即使密码正确也无法登录。
     *
     * @Transactional 注解的作用：
     *   这个方法涉及"先查后改"两步操作（selectById + updateById），
     *   加 @Transactional 可以保证这两步在同一个事务中执行，
     *   防止并发问题（虽然简单的封禁操作并发风险很低）。
     *
     * @param userId 要封禁的用户 ID
     * @throws BusinessException 如果用户不存在（ErrorCode.USER_NOT_FOUND）
     */
    @Transactional
    public void banUser(Long userId) {
        // 第一步：根据 ID 查询用户，确认用户存在
        User user = userMapper.selectById(userId);
        // 如果用户不存在，抛出业务异常
        // GlobalExceptionHandler 会捕获异常并返回统一的 JSON 错误响应
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        // 第二步：修改状态为 1（封禁）
        user.setStatus(1);
        // 第三步：更新数据库
        userMapper.updateById(user);
    }

    /**
     * 解封用户 —— 将用户状态恢复为"正常"
     *
     * 与 banUser 的逻辑完全对称：
     *   banUser 设置 status = 1（封禁）
     *   unbanUser 设置 status = 0（正常）
     *
     * 为什么需要单独的 ban/unban 方法，而不是一个 toggleStatus 方法？
     *   1. 语义更清晰：banUser 和 unbanUser 一看就知道是封禁还是解封
     *   2. API 设计更明确：PUT /api/admin/users/{id}/ban 和 /unban 是两个独立操作
     *   3. 安全性更高：前端明确指定操作类型，避免误操作
     *   4. 如果用 toggle（切换），前端需要先查询当前状态再决定显示"封禁"还是"解封"按钮，
     *      增加了前端复杂度
     *
     * @param userId 要解封的用户 ID
     * @throws BusinessException 如果用户不存在（ErrorCode.USER_NOT_FOUND）
     */
    @Transactional
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        // 恢复状态为 0（正常）
        user.setStatus(0);
        userMapper.updateById(user);
    }

    /**
     * 实体转 DTO 的辅助方法 —— 将 User 实体转为 UserResponse
     *
     * 为什么要手动转换而不是用 BeanUtils.copyProperties？
     *   BeanUtils.copyProperties 可以自动复制同名字段，但有几个缺点：
     *   1. 性能稍差（使用反射，比手动 set 慢）
     *   2. 无法在编译时发现字段名不匹配的错误
     *   3. 如果 Entity 和 DTO 字段类型不一致，会悄悄忽略（不报错）
     *   4. 不够直观，其他开发者不知道具体复制了哪些字段
     *
     *   手动 set 虽然代码多一点，但：
     *   1. 编译时就能发现类型不匹配
     *   2. 一目了然地知道哪些字段被复制了
     *   3. 可以在复制过程中添加额外的逻辑（如格式转换）
     *
     *   最重要的是：这里刻意不复制 password 字段！
     *   这是最关键的安全措施——确保密码绝对不会出现在响应 DTO 中。
     *   如果用 BeanUtils.copyProperties，万一以后 DTO 中不小心加了 password 字段，
     *   密码就会被悄悄地复制到响应中，造成严重的安全漏洞。
     *
     * @param user 用户实体对象（包含所有数据库字段，包括 password）
     * @return 用户响应 DTO（不包含 password，安全地返回给前端）
     */
    private UserResponse toUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        // 注意：没有 set password！这是刻意省略的，确保密码不会泄露给前端
        return dto;
    }
}
