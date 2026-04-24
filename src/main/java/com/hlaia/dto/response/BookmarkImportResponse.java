package com.hlaia.dto.response;

import lombok.Data;

/**
 * 【书签导入结果响应类】—— 浏览器书签导入操作完成后返回的统计信息
 *
 * 什么是书签导入？
 *   用户可以将 Chrome 浏览器导出的书签 HTML 文件上传到系统，
 *   系统会自动解析其中的文件夹和书签层级结构，批量导入到数据库中。
 *   导入完成后，需要告诉用户"导入了多少东西"，这就是这个 DTO 的作用。
 *
 * 导入统计包含四项数据：
 *   - foldersCreated：新建了多少个文件夹（Chrome 书签栏里的文件夹结构）
 *   - bookmarksCreated：新建了多少个书签（之前系统中不存在的 URL）
 *   - bookmarksUpdated：覆盖更新了多少个书签（URL 已存在，选择了"覆盖"模式）
 *   - bookmarksSkipped：跳过了多少个书签（URL 已存在，选择了"跳过"模式）
 *
 * 什么是重复处理模式（duplicateMode）？
 *   当导入的书签 URL 在系统中已经存在时，有两种处理方式：
 *   - OVERWRITE（覆盖，默认）：用导入数据中的标题、图标等信息更新已有书签
 *   - SKIP（跳过）：保留系统中的已有书签，不导入重复的
 *
 * 为什么需要返回统计信息？
 *   导入操作可能涉及成百上千个书签，用户需要知道导入了多少、
 *   跳过了多少，以便确认导入结果是否符合预期。
 */
@Data
public class BookmarkImportResponse {

    /** 新建的文件夹数量 */
    private int foldersCreated;

    /** 新建的书签数量 */
    private int bookmarksCreated;

    /**
     * 覆盖更新的书签数量
     * 仅在 duplicateMode = OVERWRITE 时才会有值
     * 表示这些 URL 在系统中已存在，被导入数据覆盖更新了
     */
    private int bookmarksUpdated;

    /**
     * 跳过的书签数量
     * 仅在 duplicateMode = SKIP 时才会有值
     * 表示这些 URL 在系统中已存在，保留原数据未导入
     */
    private int bookmarksSkipped;
}
