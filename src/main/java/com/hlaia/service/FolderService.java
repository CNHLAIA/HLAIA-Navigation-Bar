package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.FolderCreateRequest;
import com.hlaia.dto.request.FolderMoveRequest;
import com.hlaia.dto.request.FolderSortRequest;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【文件夹业务逻辑层】—— 处理文件夹相关的所有核心业务逻辑
 *
 * Service 层是整个后端架构的"大脑"，负责：
 *   1. 业务逻辑处理（如构建树形结构、权限校验）
 *   2. 调用 Mapper 层进行数据库操作
 *   3. 数据转换（Entity -> DTO）
 *   4. 事务管理（保证多个数据库操作的原子性）
 *
 * 为什么要把逻辑放在 Service 而不是 Controller？
 *   - Controller 只负责"接收请求、返回响应"，像一个前台接待员
 *   - Service 负责真正的业务逻辑，像一个后台处理部门
 *   - 好处：逻辑可以复用（多个 Controller 可以调用同一个 Service 方法）
 *
 * @Service 注解的作用：
 *   告诉 Spring "这是一个业务逻辑类，请创建它的实例并纳入容器管理"。
 *   这样其他类就可以通过构造器注入来使用它。
 *
 * @RequiredArgsConstructor 注解的作用：
 *   Lombok 自动为所有 final 字段生成构造方法。
 *   Spring 看到"只有一个构造方法"时，会自动进行依赖注入。
 *   等价于手写：
 *     public FolderService(FolderMapper folderMapper, BookmarkMapper bookmarkMapper) {
 *         this.folderMapper = folderMapper;
 *         this.bookmarkMapper = bookmarkMapper;
 *     }
 */
@Service
@RequiredArgsConstructor
public class FolderService {

    // 依赖注入：通过 final + @RequiredArgsConstructor 实现
    // final 表示这些字段必须在构造方法中赋值，赋值后不可修改
    private final FolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;
    // Elasticsearch 数据同步：文件夹增删改后需要同步到 ES
    private final com.hlaia.kafka.KafkaProducer kafkaProducer;

    /**
     * 获取用户的文件夹树 —— 本项目最核心的方法之一
     *
     * 树形结构构建算法（两次遍历法）：
     *   数据库中文件夹是"扁平存储"的（每行一个文件夹，用 parentId 标识层级），
     *   但前端需要"嵌套结构"（文件夹包含子文件夹，子文件夹又可以包含子文件夹...）。
     *
     *   构建步骤：
     *   第一次遍历（建立 Map）：
     *     把所有文件夹转成 FolderTreeResponse 对象，放入一个 Map<Long, FolderTreeResponse> 中。
     *     Map 的 key 是文件夹 ID，value 是对应的树节点。
     *     目的：可以通过 ID 快速找到任何文件夹节点（O(1) 时间复杂度）。
     *
     *   第二次遍历（挂载 children）：
     *     遍历所有文件夹，根据 parentId 找到父节点，把自己加到父节点的 children 列表中。
     *     如果 parentId 为 null，说明是根节点，加入 roots 列表。
     *
     *   示意图：
     *     数据库数据（扁平）：           构建后的树形结构：
     *     id=1, parentId=null          工作资料 (id=1)
     *     id=2, parentId=1             ├── 前端 (id=2)
     *     id=3, parentId=1             ├── 后端 (id=3)
     *     id=4, parentId=2             │   └── Vue (id=4)
     *     id=5, parentId=null          学习资源 (id=5)
     *
     *   为什么用 Map 而不是双层 for 循环？
     *     如果用双层 for 循环（对每个文件夹遍历所有文件夹找父节点），时间复杂度是 O(n^2)。
     *     用 Map 可以在 O(1) 时间内找到父节点，总时间复杂度降为 O(n)。
     *     当文件夹数量较多时，性能差异非常明显。
     *
     * @param userId 当前登录用户的 ID
     * @return 文件夹树的根节点列表（可能有多个根节点，即顶级文件夹）
     */
    public List<FolderTreeResponse> getFolderTree(Long userId) {
        // ============ 第一步：查询该用户的所有文件夹（扁平列表） ============
        // LambdaQueryWrapper 是 MyBatis-Plus 提供的类型安全查询构造器
        // .eq(Folder::getUserId, userId) 等价于 SQL: WHERE user_id = #{userId}
        // .orderByAsc(Folder::getSortOrder) 等价于 SQL: ORDER BY sort_order ASC
        List<Folder> allFolders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .orderByAsc(Folder::getSortOrder));

        // ============ 第二步：将所有文件夹转为树节点，放入 Map ============
        // Collectors.toMap() 是 Java Stream API 的收集器：
        //   第一个参数 Folder::getId —— Map 的 key（用文件夹 ID 作为键）
        //   第二个参数 this::toTreeResponse —— Map 的 value（把 Folder 转成 FolderTreeResponse）
        //   结果：Map<文件夹ID, 树节点对象>，后续可以通过 ID 快速查找
        Map<Long, FolderTreeResponse> map = allFolders.stream()
                .collect(Collectors.toMap(Folder::getId, this::toTreeResponse));

        // ============ 第三步：查询该用户的所有书签，统计每个文件夹的书签数量 ============
        // Collectors.groupingBy(Bookmark::getFolderId, Collectors.counting()) 的含义：
        //   groupingBy —— 按 folderId 分组（把相同 folderId 的书签放到一组）
        //   counting() —— 对每一组进行计数
        //   结果：Map<文件夹ID, 书签数量>，如 {1: 5, 2: 3} 表示文件夹1有5个书签，文件夹2有3个
        List<Bookmark> allBookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getUserId, userId));
        Map<Long, Long> bookmarkCounts = allBookmarks.stream()
                .collect(Collectors.groupingBy(Bookmark::getFolderId, Collectors.counting()));

        // ============ 第四步：遍历所有文件夹，构建树形结构 ============
        // 这一步是核心：通过 parentId 将扁平列表组装成嵌套树形结构
        List<FolderTreeResponse> roots = new ArrayList<>();
        for (Folder folder : allFolders) {
            FolderTreeResponse node = map.get(folder.getId());
            // 设置书签数量（如果该文件夹没有书签，默认为 0）
            node.setBookmarkCount(bookmarkCounts.getOrDefault(folder.getId(), 0L).intValue());

            if (folder.getParentId() == null) {
                // parentId 为 null，说明是顶级文件夹（根节点），加入 roots 列表
                roots.add(node);
            } else {
                // parentId 不为 null，说明是子文件夹，需要挂到父节点下面
                FolderTreeResponse parent = map.get(folder.getParentId());
                if (parent != null) {
                    // 懒初始化 children 列表：只有当需要添加子节点时才创建列表
                    // 这样没有子文件夹的节点，children 就是 null（而不是空列表）
                    // 可以节省内存，但前端使用时需要注意判空
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                }
                // 如果 parent == null，说明父文件夹不存在（数据不一致），
                // 这种情况下跳过，不挂载，避免 NPE
            }
        }

        // ============ 第五步：计算每个文件夹的子文件夹数量 ============
        // 遍历所有节点，统计 children 列表的大小
        // 这一步必须在树形结构构建完成后才能做
        for (FolderTreeResponse node : map.values()) {
            if (node.getChildren() != null) {
                node.setChildFolderCount(node.getChildren().size());
            } else {
                node.setChildFolderCount(0);
            }
        }

        return roots;
    }

    /**
     * 创建文件夹
     *
     * @Transactional 注解的作用：
     *   保证方法内的所有数据库操作要么全部成功，要么全部失败（回滚）。
     *   如果方法执行过程中抛出 RuntimeException，Spring 会自动回滚所有数据库操作。
     *
     *   什么时候需要加 @Transactional？
     *   - 涉及多个写操作（INSERT/UPDATE/DELETE）时 —— 保证一致性
     *   - 涉及"先查后改"的操作时 —— 防止并发问题（虽然这里没加锁，但开启了事务）
     *   - 只有一个查询操作时 —— 不需要加（查询本身不会修改数据）
     *
     *   这个方法涉及两次数据库操作（selectCount + insert），所以需要加 @Transactional。
     *
     * 排序序号的设计：
     *   新建文件夹的 sortOrder 设为"当前同级文件夹的数量"，
     *   这样新创建的文件夹自然排在最后面。
     *   例如：如果同级已有 3 个文件夹（sortOrder 为 0、1、2），
     *   新文件夹的 sortOrder 就是 3，排在最后。
     *
     * @param userId  当前登录用户的 ID
     * @param request 创建文件夹的请求数据（包含 parentId、name、icon）
     * @return 新创建的文件夹信息（转为 FolderTreeResponse 返回给前端）
     */
    @Transactional
    public FolderTreeResponse createFolder(Long userId, FolderCreateRequest request) {
        Folder folder = new Folder();
        folder.setUserId(userId);
        folder.setParentId(request.getParentId());
        folder.setName(request.getName());
        folder.setIcon(request.getIcon());

        // 统计同级文件夹的数量，作为新文件夹的排序序号
        // LambdaQueryWrapper 的条件构造说明：
        //   .eq(Folder::getUserId, userId) —— 始终生效的条件：WHERE user_id = #{userId}
        //   .eq(request.getParentId() != null, Folder::getParentId, request.getParentId())
        //       —— 第一个参数是 boolean 条件，为 true 时这个 .eq 才会拼接到 SQL 中
        //       —— 当 parentId 不为 null 时：AND parent_id = #{parentId}
        //   .isNull(request.getParentId() == null, Folder::getParentId)
        //       —— 当 parentId 为 null 时：AND parent_id IS NULL
        //   这种"条件拼接"方式可以动态构建 SQL，避免写多个 if-else 分支
        Long count = folderMapper.selectCount(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .eq(request.getParentId() != null, Folder::getParentId, request.getParentId())
                        .isNull(request.getParentId() == null, Folder::getParentId));
        folder.setSortOrder(count.intValue());

        folderMapper.insert(folder);
        // 创建文件夹后同步到 ES
        kafkaProducer.sendSearchSync("CREATE", "folder", folder.getId());
        return toTreeResponse(folder);
    }

    /**
     * 更新文件夹信息（名称、图标）
     *
     * 部分更新策略：
     *   只更新 request 中非 null 的字段，null 字段表示"不修改"。
     *   这是 RESTful API 中 PUT 请求的常见做法：
     *   - 如果用户只想改名称，只传 name 字段即可
     *   - 如果用户只想改图标，只传 icon 字段即可
     *   - 不传的字段保持原值不变
     *
     * @param userId   当前登录用户的 ID
     * @param folderId 要更新的文件夹 ID
     * @param request  更新请求（包含 name 和 icon，都是可选的）
     * @return 更新后的文件夹信息
     */
    @Transactional
    public FolderTreeResponse updateFolder(Long userId, Long folderId, FolderCreateRequest request) {
        // 先校验文件夹是否属于当前用户，不属于则抛出异常
        Folder folder = getFolderForUser(userId, folderId);
        // 只更新非 null 的字段（部分更新）
        if (request.getName() != null) folder.setName(request.getName());
        if (request.getIcon() != null) folder.setIcon(request.getIcon());
        folderMapper.updateById(folder);
        // 更新文件夹后同步到 ES
        kafkaProducer.sendSearchSync("UPDATE", "folder", folderId);
        return toTreeResponse(folder);
    }

    /**
     * 删除文件夹
     *
     * 注意：这里的删除是简单删除，不会级联删除子文件夹和书签。
     * 如果需要级联删除，需要先递归查找所有子文件夹 ID，再删除它们的书签，最后删除文件夹。
     * 级联删除要非常小心，建议使用逻辑删除（is_deleted 字段）而不是物理删除。
     *
     * @param userId   当前登录用户的 ID
     * @param folderId 要删除的文件夹 ID
     */
    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        // 校验权限：确保文件夹存在且属于当前用户
        getFolderForUser(userId, folderId);
        folderMapper.deleteById(folderId);
        // 删除文件夹后从 ES 中删除
        kafkaProducer.sendSearchSync("DELETE", "folder", folderId);
    }

    /**
     * 批量更新文件夹排序序号
     *
     * 拖拽排序的实现原理：
     *   1. 前端用户拖拽文件夹改变顺序后，前端重新计算所有受影响文件夹的 sortOrder
     *   2. 前端把排序数据发送到后端：[{id: 1, sortOrder: 0}, {id: 2, sortOrder: 1}, ...]
     *   3. 后端遍历列表，逐个更新每个文件夹的 sortOrder 字段
     *
     * 为什么需要 @Transactional？
     *   因为涉及多次 UPDATE 操作，必须保证要么全部更新成功，要么全部回滚。
     *   如果更新到一半失败，sortOrder 会处于不一致的状态。
     *
     * @param userId  当前登录用户的 ID
     * @param request 排序请求数据，包含 items 列表
     */
    @Transactional
    public void sortFolders(Long userId, FolderSortRequest request) {
        for (FolderSortRequest.SortItem item : request.getItems()) {
            // 每次更新前都做权限校验，确保用户只能排序自己的文件夹
            Folder folder = getFolderForUser(userId, item.getId());
            folder.setSortOrder(item.getSortOrder());
            folderMapper.updateById(folder);
        }
    }

    /**
     * 移动文件夹到新的父文件夹
     *
     * 移动操作的逻辑：
     *   1. 校验目标文件夹是否是当前文件夹的后代（防止循环移动）
     *   2. 更新文件夹的 parentId 为新的父文件夹 ID
     *
     * 防循环移动的必要性：
     *   假设有这样的树形结构：A -> B -> C（A 是 B 的父级，B 是 C 的父级）
     *   如果把 A 移动到 C 下面，就变成了：C -> A -> B -> C -> A -> ...
     *   这就形成了循环引用，树形结构被破坏，无法正常遍历和显示。
     *
     * @param userId   当前登录用户的 ID
     * @param folderId 要移动的文件夹 ID
     * @param request  移动请求，包含目标 parentId
     */
    @Transactional
    public void moveFolder(Long userId, Long folderId, FolderMoveRequest request) {
        Folder folder = getFolderForUser(userId, folderId);
        if (request.getParentId() != null) {
            // 检查目标文件夹是否是当前文件夹的后代
            // 如果是，则不允许移动（否则会形成循环引用）
            if (isDescendant(folderId, request.getParentId(), userId)) {
                throw new BusinessException(400, "Cannot move folder into its own descendant");
            }
        }
        folder.setParentId(request.getParentId());
        folderMapper.updateById(folder);
        // 移动文件夹后同步 ES
        kafkaProducer.sendSearchSync("UPDATE", "folder", folderId);
    }

    /**
     * 递归判断 candidateId 是否是 ancestorId 的后代
     *
     * 什么是递归？
     *   递归是指方法调用自身。在这个方法中，isDescendant 会调用自身来判断子节点。
     *   递归必须有"终止条件"（也叫"基准情况"），否则会无限循环导致栈溢出。
     *
     *   终止条件：如果 candidateId 等于 ancestorId，返回 true（找到循环了）
     *   递归步骤：对每个子节点递归调用 isDescendant
     *
     *   示例：判断 id=5 是否是 id=1 的后代
     *   树形结构：1 -> 2 -> 5
     *   第一层：查找 id=1 的子节点，找到 id=2，递归检查 id=2
     *   第二层：查找 id=2 的子节点，找到 id=5，递归检查 id=5
     *   第三层：5 == 5，返回 true —— 说明 id=5 是 id=1 的后代！
     *
     *   如果树形结构：1 -> 2 -> 5，判断 id=1 是否是 id=5 的后代
     *   第一层：查找 id=5 的子节点，没找到，返回 false —— 安全，可以移动
     *
     * @param ancestorId  祖先节点 ID（要移动的文件夹）
     * @param candidateId 候选节点 ID（目标位置）
     * @param userId      用户 ID（用于查询时过滤数据）
     * @return true 表示 candidateId 是 ancestorId 的后代（不能移动），false 表示安全
     */
    private boolean isDescendant(Long ancestorId, Long candidateId, Long userId) {
        // 终止条件：如果候选 ID 等于祖先 ID，说明形成了循环
        if (ancestorId.equals(candidateId)) return true;
        // 查找祖先节点的所有直接子节点
        List<Folder> children = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .eq(Folder::getParentId, ancestorId));
        // 对每个子节点递归检查
        for (Folder child : children) {
            if (isDescendant(child.getId(), candidateId, userId)) return true;
        }
        return false;
    }

    /**
     * 权限校验辅助方法 —— 获取文件夹并验证是否属于当前用户
     *
     * 这是最常见的权限校验模式：
     *   1. 根据主键 ID 查询实体
     *   2. 判断实体的 userId 是否等于当前用户 ID
     *   3. 如果不是，抛出"资源不存在"异常（而不是"无权限"，避免泄露信息）
     *
     *   为什么返回"不存在"而不是"无权限"？
     *   - 安全考虑：如果返回"无权限"，攻击者就知道这个 ID 对应的资源确实存在
     *   - 返回"不存在"，攻击者无法判断是资源不存在还是没有权限
     *   - 这种做法叫做"模糊响应"，是安全的最佳实践
     *
     * @param userId   当前登录用户的 ID
     * @param folderId 要操作的文件夹 ID
     * @return 查询到的 Folder 实体（已验证属于当前用户）
     * @throws BusinessException 如果文件夹不存在或不属于当前用户
     */
    private Folder getFolderForUser(Long userId, Long folderId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
        }
        return folder;
    }

    /**
     * 实体转 DTO 的辅助方法 —— 将 Folder 实体转为 FolderTreeResponse
     *
     * 为什么要转换？
     *   - Entity（实体）对应数据库表结构，包含所有字段
     *   - DTO（数据传输对象）对应前端需要的数据格式，只包含必要的字段
     *   - 分离 Entity 和 DTO 可以：
     *     1. 控制返回给前端的数据（隐藏敏感字段）
     *     2. Entity 结构变化不影响前端接口
     *     3. DTO 可以添加额外的计算字段（如 childFolderCount）
     *
     * @param folder 文件夹实体对象
     * @return 文件夹树响应 DTO
     */
    private FolderTreeResponse toTreeResponse(Folder folder) {
        FolderTreeResponse dto = new FolderTreeResponse();
        dto.setId(folder.getId());
        dto.setParentId(folder.getParentId());
        dto.setName(folder.getName());
        dto.setIcon(folder.getIcon());
        dto.setSortOrder(folder.getSortOrder());
        dto.setCreatedAt(folder.getCreatedAt());
        return dto;
    }
}
