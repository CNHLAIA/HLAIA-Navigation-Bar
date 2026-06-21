package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.MoveToFolderRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.request.StagingUpdateRequest;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.StagingItem;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 【暂存区业务逻辑层】—— 处理暂存区相关的所有核心业务逻辑
 *
 * 什么是暂存区（Staging Area）？
 *   暂存区是一个"临时收件箱"，用于存放用户通过浏览器扩展快速保存的网页。
 *   它的核心定位是"快存慢整"：
 *   - 快存：浏览器扩展一键保存，不需要选文件夹、不需要填描述，保存速度极快
 *   - 慢整：用户之后在网页端慢慢查看暂存区的内容，决定每个网页放到哪个文件夹
 *
 * 暂存区的典型使用流程：
 *   1. 用户浏览网页时，通过浏览器扩展右键菜单/快捷键一键保存 → 进入暂存区
 *   2. 暂存区中的网页有"保质期"（默认1天），过期后自动清理
 *   3. 用户在网页端打开暂存区，查看所有待整理的网页
 *   4. 对于感兴趣的网页，点击"移到文件夹"将其转化为正式书签
 *   5. 不需要的网页可以直接删除，或者等待它自动过期
 *
 * 暂存区 vs 书签的对比：
 *   | 特性       | 暂存区（Staging）         | 书签（Bookmark）              |
 *   |------------|--------------------------|------------------------------|
 *   | 保存方式   | 浏览器扩展一键保存         | 网页端手动创建                 |
 *   | 文件夹归属 | 无（扁平列表）             | 必须属于某个文件夹             |
 *   | 过期时间   | 有（默认1天）              | 无（永久保存）                 |
 *   | 可编辑字段 | 仅过期时间                | 标题、链接、描述              |
 *   | 排序       | 按创建时间倒序             | 支持手动排序                  |
 *
 * 本类的核心方法：
 *   - getStagingItems：查询当前用户未过期的暂存项列表
 *   - addToStaging：浏览器扩展调用，快速保存网页到暂存区
 *   - updateExpiry：修改暂存项的过期时间
 *   - deleteStagingItem：直接删除暂存项
 *   - moveToFolder：将暂存项"转化"为正式书签（核心业务逻辑）
 *
 * @Service 注解的作用：
 *   告诉 Spring "这是一个业务逻辑类，请创建它的实例并纳入容器管理"。
 *   这样 Controller 层就可以通过构造器注入来使用它。
 *
 * @RequiredArgsConstructor 注解的作用：
 *   Lombok 自动为所有 final 字段生成构造方法。
 *   Spring 看到"只有一个构造方法"时，会自动进行依赖注入。
 *   等价于手写：
 *     public StagingService(StagingItemMapper stagingItemMapper, BookmarkMapper bookmarkMapper) {
 *         this.stagingItemMapper = stagingItemMapper;
 *         this.bookmarkMapper = bookmarkMapper;
 *     }
 */
@Service
@RequiredArgsConstructor
public class StagingService {

    // 依赖注入：通过 final + @RequiredArgsConstructor 实现
    // final 表示这些字段必须在构造方法中赋值，赋值后不可修改
    private final StagingItemMapper stagingItemMapper;
    private final BookmarkMapper bookmarkMapper;

    /**
     * 获取当前用户的所有未过期暂存项
     *
     * 这个方法的查询逻辑：
     *   1. 只查询属于当前用户的暂存项（数据隔离）
     *   2. 过滤掉已过期的暂存项（只返回还有效的）
     *   3. 按创建时间倒序排列（最新的排在最前面）
     *
     * .gt(StagingItem::getExpireAt, LocalDateTime.now()) 是什么意思？
     *   .gt 是 "greater than" 的缩写，即 SQL 中的 >（大于）操作符。
     *   整行等价于 SQL: WHERE expire_at > NOW()
     *   含义：只查过期时间大于当前时间的记录，即"还没过期的暂存项"。
     *
     *   为什么不直接查出所有记录再在 Java 中过滤？
     *   - 让数据库做过滤比在 Java 中过滤效率更高（数据库有索引优化）
     *   - 如果暂存项很多（比如用户攒了500个），全部查出来再过滤浪费内存和网络带宽
     *   - 使用 .gt 条件，数据库只返回符合条件的记录，减少数据传输量
     *
     * .orderByDesc(StagingItem::getCreatedAt) 的作用：
     *   按创建时间降序排列（DESC），最新的暂存项排在最前面。
     *   等价于 SQL: ORDER BY created_at DESC
     *   这样用户打开暂存区，最先看到的就是最近保存的网页。
     *
     * @param userId 当前登录用户的 ID（用于数据隔离：只能查自己的暂存项）
     * @return 暂存项响应 DTO 列表（已按创建时间倒序排列，且已排除过期项）
     */
    public List<StagingItemResponse> getStagingItems(Long userId) {
        // 构建查询条件：
        // WHERE user_id = ? AND expire_at > NOW() ORDER BY created_at DESC
        List<StagingItem> items = stagingItemMapper.selectList(
                new LambdaQueryWrapper<StagingItem>()
                        .eq(StagingItem::getUserId, userId)           // 只查当前用户的
                        .gt(StagingItem::getExpireAt, LocalDateTime.now()) // 过滤已过期项
                        .orderByDesc(StagingItem::getCreatedAt));     // 最新的排最前
        // 将 Entity 列表转换为 DTO 列表
        // stream().map() 是 Java Stream API 的常见用法：
        //   stream() —— 把 List 转为流
        //   map(this::toResponse) —— 对每个元素调用 toResponse 方法进行转换
        //   collect(Collectors.toList()) —— 把流转回 List
        return items.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 添加网页到暂存区（浏览器扩展调用）
     *
     * 添加流程：
     *   1. 将请求 DTO 转为 Entity 对象
     *   2. 计算过期时间（默认1天）
     *   3. 插入数据库
     *   4. 返回创建的暂存项信息
     *
     * 默认过期时间的设计（1440 分钟 = 1 天）：
     *   暂存区的核心定位是"临时存放"，所以每个暂存项都有保质期。
     *   默认值 1440 分钟 = 24 小时 = 1 天，意味着：
     *   - 用户保存到暂存区的网页，1 天后会自动过期
     *   - 过期后查询时不会被返回（getStagingItems 方法过滤掉了）
     *   - 定时任务会定期清理过期的暂存项（释放数据库空间）
     *
     *   为什么选择 1 天作为默认值？
     *   - 太短（如1小时）：用户可能来不及整理就过期了
     *   - 太长（如7天）：暂存区会积累太多内容，失去"临时"的意义
     *   - 1 天是个平衡点，给用户足够时间整理，又不会堆积太久
     *
     *   用户可以自定义过期时间：
     *   - expireMinutes = 60：1小时后过期（紧急收集的信息）
     *   - expireMinutes = 1440：1天后过期（默认值）
     *   - expireMinutes = 10080：7天后过期（长期待阅读）
     *
     * LocalDateTime.now().plusMinutes() 的时间计算：
     *   LocalDateTime.now() 获取当前时间（精确到纳秒）
     *   .plusMinutes(1440) 在当前时间基础上加 1440 分钟
     *   结果就是一个"未来时刻"，存入 expire_at 字段
     *   例如：现在是 2024-01-15 10:30:00
     *         过期时间就是 2024-01-16 10:30:00（1天后）
     *
     * @Transactional 注解的作用：
     *   保证方法内的数据库操作是原子性的。
     *   虽然这个方法只有一次 insert 操作，但加上 @Transactional 是好习惯：
     *   - 保持与其他方法风格一致
     *   - 如果将来需要增加额外操作（如记录日志），事务保护已经准备好了
     *
     * @param userId  当前登录用户的 ID
     * @param request 添加到暂存区的请求数据（包含 title、url、可选的 expireMinutes）
     * @return 新创建的暂存项信息（转为 StagingItemResponse 返回）
     */
    @Transactional
    public StagingItemResponse addToStaging(Long userId, StagingCreateRequest request) {
        // ============ 第一步：构建 Entity 对象 ============
        StagingItem item = new StagingItem();
        item.setUserId(userId);
        item.setTitle(request.getTitle());
        item.setUrl(request.getUrl());

        // ============ 第二步：计算过期时间 ============
        // 如果前端没传 expireMinutes，使用默认值 1440 分钟（1天）
        // 三元运算符：条件 ? 真值 : 假值
        // request.getExpireMinutes() != null → 前端传了自定义过期时间
        // 否则使用 1440（1天 = 24小时 × 60分钟 = 1440分钟）
        int expireMinutes = request.getExpireMinutes() != null ? request.getExpireMinutes() : 1440;
        // LocalDateTime.now() 获取当前时间，plusMinutes() 加上指定分钟数
        // 例如：现在是 10:30，plusMinutes(1440) 后就是明天的 10:30
        item.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));

        // ============ 第三步：插入数据库 ============
        // insert 方法会自动填充 createdAt（如果配置了自动填充）
        // 插入成功后，item.getId() 会自动获得数据库生成的自增主键
        stagingItemMapper.insert(item);
        return toResponse(item);
    }

    /**
     * 更新暂存项的过期时间
     *
     * 为什么暂存区只支持修改过期时间？
     *   暂存区的定位是"临时快照"，保存的是某个时刻的网页信息。
     *   如果用户想修改标题或链接，应该删除后重新保存（而不是编辑）。
     *   只允许修改过期时间，既保持了暂存区"快存"的简单性，
     *   又给用户一个"延长保质期"的选择（比如看到一个感兴趣的网页但暂时没时间看）。
     *
     * 过期时间的重新计算方式：
     *   以当前时间 + 新的 expireMinutes 重新计算，而不是在原有过期时间上加减。
     *   例如：原来过期时间是明天，用户现在传了 60（分钟），
     *   新的过期时间就是"当前时间 + 60分钟"，而不是"原过期时间 + 60分钟"。
     *
     * @Transactional 注解的作用：
     *   保护"查询 + 更新"两步操作的原子性。
     *   防止在查询和更新之间有其他线程修改了这条记录。
     *
     * @param userId  当前登录用户的 ID
     * @param itemId  要更新的暂存项 ID
     * @param request 更新请求（包含新的 expireMinutes）
     * @return 更新后的暂存项信息
     */
    @Transactional
    public StagingItemResponse updateExpiry(Long userId, Long itemId, StagingUpdateRequest request) {
        // 先校验暂存项是否存在且属于当前用户，不属于则抛出异常
        StagingItem item = getStagingItemForUser(userId, itemId);
        // 以当前时间为基准重新计算过期时间
        item.setExpireAt(LocalDateTime.now().plusMinutes(request.getExpireMinutes()));
        stagingItemMapper.updateById(item);
        return toResponse(item);
    }

    /**
     * 删除暂存项
     *
     * 删除场景：
     *   1. 用户觉得暂存区里的某个网页不需要了，主动删除
     *   2. 注意：过期的暂存项会被定时任务自动删除，不需要用户手动操作
     *
     * 删除流程：
     *   1. 校验暂存项是否存在且属于当前用户
     *   2. 执行删除操作
     *
     * 注意：这里是物理删除（直接从数据库中移除记录）。
     * 因为暂存区本身就是临时数据，没有恢复的必要。
     *
     * @param userId 当前登录用户的 ID
     * @param itemId 要删除的暂存项 ID
     */
    @Transactional
    public void deleteStagingItem(Long userId, Long itemId) {
        // 校验权限：确保暂存项存在且属于当前用户
        getStagingItemForUser(userId, itemId);
        stagingItemMapper.deleteById(itemId);
    }

    /**
     * 将暂存项移动到文件夹（转化为正式书签）
     *
     * 这是暂存区最核心的业务操作——"转正"：
     *   暂存项 → 正式书签
     *
     * moveToFolder 的"转移"逻辑（两步操作）：
     *   第一步：从暂存项创建书签
     *     - 把暂存项的 title、url、iconUrl 复制到新的 Bookmark 对象
     *     - 设置目标文件夹 ID（用户指定的）
     *     - 计算 sortOrder（排在文件夹内最后面）
     *     - 插入 bookmark 表
     *
     *   第二步：删除暂存项
     *     - 从 staging_item 表中删除原暂存项
     *
     *   为什么这是一次原子操作？
     *   因为"创建书签 + 删除暂存项"必须同时成功或同时失败：
     *   - 如果创建了书签但没删暂存项 → 暂存区里还留着一个"幽灵记录"，用户可能再次操作
     *   - 如果删了暂存项但没创建书签 → 用户的数据丢失了！
     *   @Transactional 保证了这两步操作在同一个数据库事务中执行：
     *   - 两步都成功 → 事务提交
     *   - 任何一步失败 → 事务回滚，数据库恢复到操作前的状态
     *
     * sortOrder 的计算方式：
     *   统计目标文件夹下已有多少书签，新书签的 sortOrder 就是已有数量（排在最后）。
     *   例如：文件夹下已有 3 个书签（sortOrder 为 0、1、2），新书签的 sortOrder 就是 3。
     *   这样新移动过来的书签会排在文件夹的最后面。
     *
     * @Transactional 注解的重要性：
     *   这个方法涉及两次写操作（insert + delete），@Transactional 保证了原子性。
     *   如果 insert 成功但 delete 失败（如数据库异常），事务会回滚，书签也不会被创建。
     *
     * @param userId  当前登录用户的 ID
     * @param itemId  要移动的暂存项 ID
     * @param request 移动请求（包含目标文件夹 ID）
     */
    @Transactional
    public void moveToFolder(Long userId, Long itemId, MoveToFolderRequest request) {
        // ============ 第一步：校验暂存项 ============
        StagingItem item = getStagingItemForUser(userId, itemId);

        // ============ 第二步：从暂存项创建书签 ============
        // 把暂存项的数据"复制"到新的 Bookmark 对象中
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(request.getFolderId());  // 用户指定的目标文件夹
        bookmark.setTitle(item.getTitle());            // 复制标题
        bookmark.setUrl(item.getUrl());                // 复制链接
        bookmark.setIconUrl(item.getIconUrl());        // 复制图标

        // 计算排序序号：统计目标文件夹下已有多少书签，新书签排在最后
        Long count = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getFolderId()));
        bookmark.setSortOrder(count.intValue());

        // 插入 bookmark 表（暂存项 → 正式书签）
        bookmarkMapper.insert(bookmark);

        // ============ 第三步：删除暂存项 ============
        // 书签创建成功后，从暂存区移除这条记录
        // 因为有 @Transactional，如果 insert 或 delete 任何一步失败，整个操作回滚
        stagingItemMapper.deleteById(itemId);
    }

    /**
     * 权限校验辅助方法 —— 获取暂存项并验证是否属于当前用户
     *
     * 这是最常见的权限校验模式（与 BookmarkService.getBookmarkForUser 相同）：
     *   1. 根据主键 ID 查询实体
     *   2. 判断实体的 userId 是否等于当前用户 ID
     *   3. 如果不是，抛出"资源不存在"异常（而不是"无权限"，避免泄露信息）
     *
     *   为什么返回"不存在"而不是"无权限"？
     *   - 安全考虑：如果返回"无权限"，攻击者就知道这个 ID 对应的资源确实存在
     *   - 返回"不存在"，攻击者无法判断是资源不存在还是没有权限
     *   - 这种做法叫做"模糊响应"，是安全的最佳实践
     *
     * @param userId  当前登录用户的 ID
     * @param itemId  要操作的暂存项 ID
     * @return 查询到的 StagingItem 实体（已验证属于当前用户）
     * @throws BusinessException 如果暂存项不存在或不属于当前用户
     */
    private StagingItem getStagingItemForUser(Long userId, Long itemId) {
        StagingItem item = stagingItemMapper.selectById(itemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.STAGING_NOT_FOUND);
        }
        return item;
    }

    /**
     * 实体转 DTO 的辅助方法 —— 将 StagingItem 实体转为 StagingItemResponse
     *
     * 为什么要转换？
     *   - Entity（实体）对应数据库表结构，包含所有字段（包括 userId 等敏感字段）
     *   - DTO（数据传输对象）对应前端需要的数据格式，只包含必要的字段
     *   - 分离 Entity 和 DTO 可以：
     *     1. 控制返回给前端的数据（如不返回 userId，防止信息泄露）
     *     2. Entity 结构变化不影响前端接口
     *     3. DTO 可以添加额外字段或改变字段格式
     *
     * @param item 暂存项实体对象
     * @return 暂存项响应 DTO
     */
    private StagingItemResponse toResponse(StagingItem item) {
        StagingItemResponse dto = new StagingItemResponse();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setUrl(item.getUrl());
        dto.setIconUrl(item.getIconUrl());
        dto.setExpireAt(item.getExpireAt());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
