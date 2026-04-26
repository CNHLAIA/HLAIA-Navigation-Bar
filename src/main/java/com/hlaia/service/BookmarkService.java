package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.*;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.kafka.KafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 【书签业务逻辑层】—— 处理书签相关的所有核心业务逻辑
 *
 * Service 层是整个后端架构的"大脑"，负责：
 *   1. 业务逻辑处理（如权限校验、排序计算、数据转换）
 *   2. 调用 Mapper 层进行数据库操作
 *   3. 调用 KafkaProducer 发送异步任务消息
 *   4. 数据转换（Entity -> Response DTO）
 *   5. 事务管理（保证多个数据库操作的原子性）
 *
 * 本类与 FolderService 的对比：
 *   FolderService 需要处理树形结构（递归构建文件夹树、防止循环移动），
 *   而 BookmarkService 的逻辑相对简单——书签是扁平结构，只需要在文件夹内排序。
 *   但 BookmarkService 引入了异步任务（通过 Kafka）和批量操作的概念。
 *
 * @Service 注解的作用：
 *   告诉 Spring "这是一个业务逻辑类，请创建它的实例并纳入容器管理"。
 *   这样其他类就可以通过构造器注入来使用它。
 *
 * @RequiredArgsConstructor 注解的作用：
 *   Lombok 自动为所有 final 字段生成构造方法。
 *   Spring 看到"只有一个构造方法"时，会自动进行依赖注入。
 *   等价于手写：
 *     public BookmarkService(BookmarkMapper bookmarkMapper, KafkaProducer kafkaProducer) {
 *         this.bookmarkMapper = bookmarkMapper;
 *         this.kafkaProducer = kafkaProducer;
 *     }
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    // 依赖注入：通过 final + @RequiredArgsConstructor 实现
    // final 表示这些字段必须在构造方法中赋值，赋值后不可修改
    private final BookmarkMapper bookmarkMapper;
    private final KafkaProducer kafkaProducer;
    private final com.hlaia.mapper.FolderMapper folderMapper;

    /**
     * 获取指定文件夹下的所有书签
     *
     * 这个方法的流程：
     *   1. 构建查询条件：userId（只能查自己的）+ folderId（指定文件夹）
     *   2. 按 sortOrder 升序排列（数字越小越靠前）
     *   3. 将查询结果（Entity 列表）转换为 Response DTO 列表
     *
     * LambdaQueryWrapper 是什么？
     *   MyBatis-Plus 提供的类型安全查询构造器。
     *   .eq(Bookmark::getUserId, userId)  等价于 SQL: WHERE user_id = #{userId}
     *   .orderByAsc(Bookmark::getSortOrder)  等价于 SQL: ORDER BY sort_order ASC
     *
     *   使用 Lambda 表达式（Bookmark::getUserId）而非字符串（"user_id"）的好处：
     *   - 编译期检查：如果字段名拼写错误，编译时就会报错，而不是运行时才发现
     *   - 重构友好：如果用 IDE 重命名字段，Lambda 表达式会自动跟着改
     *
     * @param userId   当前登录用户的 ID（用于数据隔离：只能查自己的书签）
     * @param folderId 文件夹 ID（查询该文件夹下的所有书签）
     * @return 书签响应 DTO 列表（已按 sortOrder 排序）
     */
    public List<BookmarkResponse> getBookmarksByFolder(Long userId, Long folderId) {
        // 查询数据库：WHERE user_id = ? AND folder_id = ? ORDER BY sort_order ASC
        List<Bookmark> bookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, userId)
                        .eq(Bookmark::getFolderId, folderId)
                        .orderByAsc(Bookmark::getSortOrder));
        // 将 Entity 列表转换为 DTO 列表
        // stream().map() 是 Java Stream API 的常见用法：
        //   stream() —— 把 List 转为流
        //   map(this::toResponse) —— 对每个元素调用 toResponse 方法进行转换
        //   collect(Collectors.toList()) —— 把流转回 List
        return bookmarks.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 创建书签
     *
     * 创建流程：
     *   1. 将请求 DTO 转为 Entity 对象
     *   2. 计算排序序号（新书签排在文件夹内最后面）
     *   3. 插入数据库
     *   4. 发送 Kafka 异步任务：让消费者去获取该网站的图标（favicon）
     *   5. 返回创建的书签信息
     *
     * @Transactional 注解的作用：
     *   保证方法内的所有数据库操作要么全部成功，要么全部失败（回滚）。
     *   这个方法涉及 selectCount + insert 两次数据库操作，需要事务保护。
     *   （注意：Kafka 发送是异步的，不在事务范围内，发送失败不影响书签创建）
     *
     * 异步获取图标的用途：
     *   用户创建书签时，后端需要访问书签的 URL 来获取网站的 favicon（小图标）。
     *   这个过程可能需要几秒钟（网络请求），如果同步获取会拖慢接口响应。
     *   所以我们通过 Kafka 异步处理：
     *   - 创建书签时立即返回，iconUrl 字段暂时为 null
     *   - Kafka 消费者在后台获取图标，成功后更新 iconUrl 字段
     *   - 前端下次刷新或通过轮询就能看到图标了
     *
     * @param userId  当前登录用户的 ID
     * @param request 创建书签的请求数据（包含 folderId、title、url、description）
     * @return 新创建的书签信息（转为 BookmarkResponse 返回给前端）
     */
    @Transactional
    public BookmarkResponse createBookmark(Long userId, BookmarkCreateRequest request) {
        // ============ 第零步：检查是否重复 ============
        // 唯一约束为 (user_id, folder_id, url)，同一文件夹内不能重复收藏相同 URL。
        long exists = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, userId)
                        .eq(Bookmark::getFolderId, request.getFolderId())
                        .eq(Bookmark::getUrl, request.getUrl()));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATE);
        }

        // ============ 第一步：构建 Entity 对象 ============
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(request.getFolderId());
        bookmark.setTitle(request.getTitle());
        bookmark.setUrl(request.getUrl());
        bookmark.setDescription(request.getDescription());
        bookmark.setIconUrl(request.getIconUrl());

        // ============ 第二步：计算排序序号 ============
        // 统计该文件夹下已有多少书签，新书签的 sortOrder 就是已有数量（排在最后）
        // 例如：文件夹下已有 3 个书签（sortOrder 为 0、1、2），新书签的 sortOrder 就是 3
        Long count = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getFolderId()));
        bookmark.setSortOrder(count.intValue());

        // ============ 第三步：插入数据库 ============
        // insert 方法会自动填充 createdAt 和 updatedAt（如果配置了自动填充）
        // 插入成功后，bookmark.getId() 会自动获得数据库生成的自增主键
        bookmarkMapper.insert(bookmark);

        // ============ 第四步：发送 Kafka 异步任务 ============
        // 让消费者异步获取该网站的图标（favicon）
        // 这是一个非阻塞操作，不会影响接口响应速度
        kafkaProducer.sendIconFetchTask(bookmark.getId(), bookmark.getUrl());

        // ============ 第五步：发送 ES 同步消息 ============
        // 创建书签后，需要把新书签同步到 Elasticsearch，这样用户才能搜到它
        kafkaProducer.sendSearchSync("CREATE", "bookmark",  bookmark.getId());
        return toResponse(bookmark);
    }

    /**
     * 更新书签信息（标题、URL、描述）
     *
     * 部分更新策略：
     *   只更新 request 中非 null 的字段，null 字段表示"不修改"。
     *   例如：如果用户只想改标题，只传 title 字段即可，url 和 description 保持不变。
     *
     * @param userId     当前登录用户的 ID
     * @param bookmarkId 要更新的书签 ID
     * @param request    更新请求（包含 title、url、description，都是可选的）
     * @return 更新后的书签信息
     */
    @Transactional
    public BookmarkResponse updateBookmark(Long userId, Long bookmarkId, BookmarkCreateRequest request) {
        // 先校验书签是否属于当前用户，不属于则抛出异常
        Bookmark bookmark = getBookmarkForUser(userId, bookmarkId);
        // 只更新非 null 的字段（部分更新）
        if (request.getTitle() != null) bookmark.setTitle(request.getTitle());
        if (request.getUrl() != null) bookmark.setUrl(request.getUrl());
        if (request.getDescription() != null) bookmark.setDescription(request.getDescription());
        if (request.getIconUrl() != null) bookmark.setIconUrl(request.getIconUrl());
        bookmarkMapper.updateById(bookmark);
        // 更新书签后，同步到 ES
        kafkaProducer.sendSearchSync("UPDATE", "bookmark", bookmarkId);
        return toResponse(bookmark);
    }

    /**
     * 删除书签
     *
     * 删除流程：
     *   1. 校验书签是否存在且属于当前用户
     *   2. 执行删除操作
     *
     * 注意：这里是物理删除（直接从数据库中移除记录）。
     * 在生产环境中，通常会使用逻辑删除（设置 is_deleted = 1），
     * 以便数据恢复和审计追踪。
     *
     * @param userId     当前登录用户的 ID
     * @param bookmarkId 要删除的书签 ID
     */
    @Transactional
    public void deleteBookmark(Long userId, Long bookmarkId) {
        // 校验权限：确保书签存在且属于当前用户
        getBookmarkForUser(userId, bookmarkId);
        bookmarkMapper.deleteById(bookmarkId);
        // 删除书签后，从 ES 中也删除对应的文档
        kafkaProducer.sendSearchSync("DELETE", "bookmark", bookmarkId);
    }

    /**
     * 批量更新书签排序序号
     *
     * 拖拽排序的实现原理：
     *   1. 前端用户拖拽书签改变顺序后，前端重新计算所有受影响书签的 sortOrder
     *   2. 前端把排序数据发送到后端：[{id: 1, sortOrder: 0}, {id: 2, sortOrder: 1}, ...]
     *   3. 后端遍历列表，逐个更新每个书签的 sortOrder 字段
     *
     * 为什么需要 @Transactional？
     *   因为涉及多次 UPDATE 操作，必须保证要么全部更新成功，要么全部回滚。
     *   如果更新到一半失败，sortOrder 会处于不一致的状态。
     *
     * @param userId  当前登录用户的 ID
     * @param request 排序请求数据，包含 items 列表（每个 item 有 id 和 sortOrder）
     */
    @Transactional
    public void sortBookmarks(Long userId, BookmarkSortRequest request) {
        for (BookmarkSortRequest.SortItem item : request.getItems()) {
            // 每次更新前都做权限校验，确保用户只能排序自己的书签
            Bookmark bookmark = getBookmarkForUser(userId, item.getId());
            bookmark.setSortOrder(item.getSortOrder());
            bookmarkMapper.updateById(bookmark);
        }
    }

    /**
     * 批量删除书签
     *
     * 批量删除的权限校验模式（先逐个验证所有权再批量删除）：
     *   1. 第一步：遍历所有要删除的 ID，逐个调用 getBookmarkForUser 校验所有权
     *      如果任何一个 ID 不属于当前用户，立即抛出异常，整个操作回滚
     *   2. 第二步：所有权限校验通过后，执行批量删除
     *
     * 为什么要"先校验再删除"而不是"边删边校验"？
     *   - 原子性保证：如果第 3 个 ID 不属于当前用户，前 2 个不应该被删除
     *   - 事务回滚：如果在删除过程中发现权限问题，已执行的操作需要全部撤销
     *   - 先校验再删除的模式确保"要么全删，要么一个都不删"
     *
     * 另一种实现方式（使用 SQL IN 条件）：
     *   可以用一条 SQL 完成权限校验和删除：
     *   DELETE FROM bookmark WHERE id IN (?, ?, ?) AND user_id = ?
     *   但这种方式的问题是：如果某些 ID 不属于当前用户，它们会被静默忽略，
     *   调用方不知道哪些 ID 被删除了，哪些没有。逐个校验的方式能提供明确的错误信息。
     *
     * @param userId  当前登录用户的 ID
     * @param request 批量删除请求（包含要删除的书签 ID 列表）
     */
    @Transactional
    public void batchDelete(Long userId, BatchDeleteRequest request) {
        // 第一步：逐个校验所有书签的所有权
        // 如果任何一个 ID 不属于当前用户，getBookmarkForUser 会抛出异常
        for (Long id : request.getIds()) {
            getBookmarkForUser(userId, id);
        }
        // 第二步：所有权限校验通过后，执行批量删除
        // deleteBatchIds 是 MyBatis-Plus 提供的批量删除方法
        // 底层会生成 SQL: DELETE FROM bookmark WHERE id IN (?, ?, ?)
        bookmarkMapper.deleteBatchIds(request.getIds());
        // 批量删除后，逐个发送 ES 同步消息
        for (Long id : request.getIds()) {
            kafkaProducer.sendSearchSync("DELETE", "bookmark", id);
        }
    }

    /**
     * 批量复制书签链接
     *
     * 返回 URL 列表的设计思路：
     *   前端选中多个书签后点击"复制链接"按钮，需要拿到这些书签的 URL，
     *   然后在前端使用 clipboard API 将 URL 列表复制到剪贴板。
     *
     *   为什么不在后端直接复制到剪贴板？
     *   因为剪贴板是浏览器端的功能，后端（服务器端）无法操作用户的剪贴板。
     *   后端只负责提供数据（URL 列表），复制操作由前端完成。
     *
     *   为什么返回 List<String> 而不是 List<BookmarkResponse>？
     *   因为前端只需要 URL 字符串来复制，不需要其他信息（标题、图标等）。
     *   返回精简的数据可以减少网络传输量，也简化了前端处理逻辑。
     *
     * @param userId  当前登录用户的 ID
     * @param request 批量复制请求（包含要复制的书签 ID 列表）
     * @return URL 字符串列表
     */
    public List<String> batchCopyLinks(Long userId, BatchCopyRequest request) {
        // 使用 Stream API 对每个 ID 进行转换：
        // 1. 对每个 ID 调用 getBookmarkForUser 校验权限并获取书签
        // 2. 提取书签的 URL 字段
        // 3. 收集为 List<String> 返回
        return request.getIds().stream()
                .map(id -> {
                    Bookmark b = getBookmarkForUser(userId, id);
                    return b.getUrl();
                })
                .collect(Collectors.toList());
    }

    /**
     * 批量移动书签到目标文件夹
     *
     * 移动流程：
     *   1. 验证所有书签属于当前用户
     *   2. 验证目标文件夹存在且属于当前用户
     *   3. 计算目标文件夹当前最大 sortOrder
     *   4. 批量更新 folderId 和 sortOrder（追加到末尾）
     *
     * @Transactional：保证所有更新操作的原子性
     *
     * @param userId  当前登录用户的 ID
     * @param request 移动请求（包含 bookmarkIds 和 targetFolderId）
     */
    @Transactional
    public void moveBookmarks(Long userId, BookmarkMoveRequest request) {
        // 第一步：验证所有书签属于当前用户
        // 同时收集到实体列表，避免后续重复查询
        java.util.List<Bookmark> bookmarks = new java.util.ArrayList<>();
        for (Long id : request.getBookmarkIds()) {
            bookmarks.add(getBookmarkForUser(userId, id));
        }

        // 第二步：验证目标文件夹存在且属于当前用户
        com.hlaia.entity.Folder targetFolder = folderMapper.selectById(request.getTargetFolderId());
        if (targetFolder == null || !targetFolder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
        }

        // 第三步：计算目标文件夹当前最大 sortOrder
        Bookmark maxSortBookmark = bookmarkMapper.selectOne(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getTargetFolderId())
                        .orderByDesc(Bookmark::getSortOrder)
                        .last("LIMIT 1"));
        int nextSortOrder = (maxSortBookmark != null) ? maxSortBookmark.getSortOrder() + 1 : 0;

        // 第四步：批量更新 folderId 和 sortOrder
        for (Bookmark bookmark : bookmarks) {
            bookmark.setFolderId(request.getTargetFolderId());
            bookmark.setSortOrder(nextSortOrder++);
            bookmarkMapper.updateById(bookmark);
            // 移动书签后同步 ES（folderId 变了）
            kafkaProducer.sendSearchSync("UPDATE", "bookmark", bookmark.getId());
        }
    }

    /**
     * 权限校验辅助方法 —— 获取书签并验证是否属于当前用户
     *
     * 这是最常见的权限校验模式（与 FolderService.getFolderForUser 相同）：
     *   1. 根据主键 ID 查询实体
     *   2. 判断实体的 userId 是否等于当前用户 ID
     *   3. 如果不是，抛出"资源不存在"异常（而不是"无权限"，避免泄露信息）
     *
     *   为什么返回"不存在"而不是"无权限"？
     *   - 安全考虑：如果返回"无权限"，攻击者就知道这个 ID 对应的资源确实存在
     *   - 返回"不存在"，攻击者无法判断是资源不存在还是没有权限
     *   - 这种做法叫做"模糊响应"，是安全的最佳实践
     *
     * @param userId     当前登录用户的 ID
     * @param bookmarkId 要操作的书签 ID
     * @return 查询到的 Bookmark 实体（已验证属于当前用户）
     * @throws BusinessException 如果书签不存在或不属于当前用户
     */
    private Bookmark getBookmarkForUser(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND);
        }
        return bookmark;
    }

    /**
     * 实体转 DTO 的辅助方法 —— 将 Bookmark 实体转为 BookmarkResponse
     *
     * 为什么要转换？
     *   - Entity（实体）对应数据库表结构，包含所有字段（包括 userId 等敏感字段）
     *   - DTO（数据传输对象）对应前端需要的数据格式，只包含必要的字段
     *   - 分离 Entity 和 DTO 可以：
     *     1. 控制返回给前端的数据（如不返回 userId，防止信息泄露）
     *     2. Entity 结构变化不影响前端接口
     *     3. DTO 可以添加额外字段或改变字段格式
     *
     * @param bookmark 书签实体对象
     * @return 书签响应 DTO
     */
    private BookmarkResponse toResponse(Bookmark bookmark) {
        BookmarkResponse dto = new BookmarkResponse();
        dto.setId(bookmark.getId());
        dto.setFolderId(bookmark.getFolderId());
        dto.setTitle(bookmark.getTitle());
        dto.setUrl(bookmark.getUrl());
        dto.setDescription(bookmark.getDescription());
        dto.setIconUrl(bookmark.getIconUrl());
        dto.setSortOrder(bookmark.getSortOrder());
        dto.setCreatedAt(bookmark.getCreatedAt());
        dto.setUpdatedAt(bookmark.getUpdatedAt());
        return dto;
    }
}
