package com.hlaia.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 【导出数据响应类】—— GET /api/bookmarks/export-data 的响应体
 *
 * 设计思路（见 design.md §1、§2.1）：
 *   后端只负责出 JSON（完整文件夹树 + 每个文件夹直属书签详情），
 *   HTML 的拼装由前端纯函数 renderExportHtml(data) 完成。
 *   这样改样式零后端重启，预览页和生产下载共用同一套模板。
 *
 * 为什么是 exportedAt 字符串而不是 LocalDateTime？
 *   后端直接格式化成 ISO 字符串（如 "2026-07-22T15:30:00"），前端放进页头即可显示，
 *   无需前端再处理时区/格式——导出是一次性快照，时间精度到秒足够。
 *
 * 字段说明：
 *   - exportedAt：导出时间 ISO 字符串，前端放页头展示「导出于 yyyy-MM-dd」
 *   - folders：根文件夹列表（parentId == null 的文件夹），已递归装好 children + bookmarks
 */
@Data
public class ExportDataResponse {

    /** 导出时间 ISO 字符串，前端用于页头展示 */
    private String exportedAt;

    /**
     * 根文件夹列表（已含嵌套 children + 每个文件夹直属 bookmarks）
     * 根文件夹 = parentId 为 null 的文件夹。
     * 没有任何文件夹时为空列表（不是 null）。
     */
    private List<ExportFolderNode> folders;
}
