package com.hlaia.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 【搜索结果响应 DTO】
 *
 * 搜索结果和普通列表不同，它包含：
 *   1. 匹配的文档列表（书签和文件夹混合）
 *   2. 每个文档的类型标识（bookmark 或 folder）
 *   3. 分页信息
 *   4. 高亮的文本片段（搜索关键词被 <em> 标签包裹）
 */
@Data
public class SearchResponse {

    /** 搜索结果列表 */
    private List<SearchItem> items;

    /** 总匹配数（用于分页） */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /**
     * 单条搜索结果
     */
    @Data
    public static class SearchItem {
        /** 结果类型：bookmark 或 folder */
        private String type;

        /** 书签 ID 或文件夹 ID */
        private Long id;

        /** 标题（书签的 title 或文件夹的 name） */
        private String title;

        /** URL（仅书签有） */
        private String url;

        /** 描述（仅书签有） */
        private String description;

        /** 图标 */
        private String icon;

        /** 所属文件夹 ID（仅书签有） */
        private Long folderId;

        /**
         * 高亮片段列表
         * ES 返回匹配关键词被 <em> 标签包裹的文本片段，
         * 前端用 v-html 渲染即可实现高亮效果。
         */
        private List<String> highlights;
    }
}
