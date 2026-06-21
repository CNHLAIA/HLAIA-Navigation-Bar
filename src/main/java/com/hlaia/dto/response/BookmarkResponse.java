package com.hlaia.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【书签响应 DTO】—— 返回给前端的书签详情数据
 *
 * Response DTO 与 Entity 的区别：
 *   Entity（Bookmark）包含所有数据库字段，包括 userId、sortOrder 等敏感或内部字段。
 *   Response DTO 只返回前端需要展示的信息，隐藏了实现细节。
 *
 *   例如：userId 不需要返回给前端（前端不应该知道其他用户的 ID）
 *   sortOrder 不需要返回（前端用列表顺序来决定展示顺序即可）
 *
 *   但在这个项目中我们返回了 sortOrder，因为前端拖拽排序时需要知道当前的排序值，
 *   以便计算新的排序位置。这体现了 DTO 设计的灵活性：根据业务需要选择返回哪些字段。
 */
@Data
public class BookmarkResponse {

    /** 书签ID */
    private Long id;

    /**
     * 所属文件夹ID
     * 前端需要知道书签属于哪个文件夹，以便在正确的文件夹下展示
     */
    private Long folderId;

    /** 书签标题，显示在导航栏上 */
    private String title;

    /** 书签链接地址 */
    private String url;

    /**
     * 书签描述/备注
     * 可能为 null（用户没有填写描述）
     * 前端展示时需要判空：description != null ? description : ""
     */
    private String description;

    /**
     * 网站图标 URL（favicon）
     * 如 "https://www.baidu.com/favicon.ico"
     * 前端可以用 <img> 标签直接显示这个图标
     */
    private String iconUrl;

    /** 排序序号 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
