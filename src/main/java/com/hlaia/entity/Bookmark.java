package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【书签实体类】—— 对应数据库中的 bookmark 表
 *
 * 书签和文件夹的关系：
 *   一个书签必须属于一个文件夹（多对一关系）。
 *   通过 folderId 字段关联到 folder 表。
 *
 *   文件夹（1）─── 包含 ───→ （多）书签
 *   例如：
 *     文件夹"前端工具" 下有多个书签：
 *     - VS Code (folderId = 2)
 *     - GitHub (folderId = 2)
 *     - npm (folderId = 2)
 */
@Data
@TableName("bookmark")   // 对应数据库的 bookmark 表
public class Bookmark {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     * 每个用户只能看到和操作自己的书签
     */
    private Long userId;

    /**
     * 所属文件夹ID
     * 外键关联到 folder 表的 id
     * 书签必须放在某个文件夹里，不能"漂浮"在外面
     */
    private Long folderId;

    /**
     * 书签标题，显示在导航栏上的文字
     * 如 "百度"、"GitHub" 等
     */
    private String title;

    /**
     * 书签链接地址（URL）
     * 如 "https://www.baidu.com"
     * 数据库中设置了唯一索引 (user_id, url)，同一个用户的相同 URL 只能保存一次
     */
    private String url;

    /**
     * 书签描述/备注（可选）
     * 用户可以给书签添加一些说明，如 "常用的前端框架"
     */
    private String description;

    /**
     * 网站图标 URL（favicon）
     * 浏览器标签页上显示的小图标，如 "https://www.baidu.com/favicon.ico"
     * 浏览器扩展可以自动获取并保存这个图标
     */
    private String iconUrl;

    /**
     * 排序序号，同一个文件夹内的书签按此字段排序
     * 数字越小越靠前
     */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
