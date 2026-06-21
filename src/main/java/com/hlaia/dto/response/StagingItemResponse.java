package com.hlaia.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【暂存项响应 DTO】—— 返回给前端的暂存区网页数据
 *
 * 与 BookmarkResponse 的对比：
 *   StagingItemResponse 字段更少，因为暂存项是"临时快照"，信息精简：
 *   - 没有 folderId（暂存项不属于任何文件夹）
 *   - 没有 description（暂存时不需要写描述）
 *   - 没有 sortOrder（暂存区按时间排序，不需要手动排序）
 *   - 没有 updatedAt（暂存项创建后不会被修改）
 *   - 多了 expireAt（暂存项有过期时间，书签没有）
 */
@Data
public class StagingItemResponse {

    /** 暂存项ID */
    private Long id;

    /** 网页标题 */
    private String title;

    /** 网页链接地址 */
    private String url;

    /**
     * 网站图标 URL
     * 前端可以用这个图标让列表更美观
     */
    private String iconUrl;

    /**
     * 过期时间
     * 前端可以根据这个时间显示倒计时，如"还剩 2 小时过期"
     * 过期后，定时任务会自动删除这条记录，前端无需处理
     */
    private LocalDateTime expireAt;

    /** 创建时间（即保存到暂存区的时间） */
    private LocalDateTime createdAt;
}
