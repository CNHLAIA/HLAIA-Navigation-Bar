package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【暂存区项实体类】—— 对应数据库中的 staging_item 表
 *
 * 什么是暂存区（Staging Area）？
 *   暂存区是一个临时存放书签的地方，类似于"稍后阅读"功能。
 *   用户通过浏览器扩展快速保存网页到暂存区，之后再决定放到哪个文件夹。
 *
 * 暂存区 vs 书签的区别：
 *   1. 暂存区项没有文件夹归属（不需要选文件夹就能保存）
 *   2. 暂存区项有过期时间（默认1天后自动删除）
 *   3. 暂存区项不支持嵌套/分组
 *   4. 暂存区项字段更少（没有 description、sortOrder）
 *
 * 使用流程：
 *   浏览器扩展一键保存 → 暂存区 → 用户在网页端查看 → 移动到正式文件夹（变成书签）
 */
@Data
@TableName("staging_item")   // 对应数据库的 staging_item 表
public class StagingItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 网页标题
     * 由浏览器扩展自动从当前标签页获取
     */
    private String title;

    /**
     * 网页链接地址
     * 由浏览器扩展自动从当前标签页的地址栏获取
     */
    private String url;

    /**
     * 网站图标 URL
     * 由浏览器扩展自动获取
     */
    private String iconUrl;

    /**
     * 过期时间
     * 超过这个时间后，定时任务会自动删除这条记录
     * 默认值：保存时当前时间 + 1天
     */
    private LocalDateTime expireAt;

    /**
     * 创建时间（即保存到暂存区的时间）
     * 注意：暂存区没有 updatedAt 字段，因为暂存项创建后不会被修改
     */
    private LocalDateTime createdAt;
}
