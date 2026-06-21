package com.hlaia.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

/**
 * 【ES 文档模型 —— 文件夹搜索文档】
 *
 * 文件夹的搜索字段比书签少，只有 name 需要全文搜索。
 * 但搜索结果需要展示 icon、parentId 等信息，所以也要包含这些字段。
 */
@Data
@Document(indexName = "hlaia_nav_folder")
@Setting(shards = 1, replicas = 0)
public class FolderDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long parentId;

    /**
     * 文件夹名称 —— 文件夹的主要搜索字段
     * 使用 IK 分词器，与 BookmarkDocument 保持一致的中文分词策略
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Keyword)
    private String icon;

    /**
     * 创建时间和更新时间 —— 使用 Keyword 类型（与 BookmarkDocument 相同的原因）
     * 避免 ES 日期格式与 LocalDateTime 之间的转换错误。
     */
    @Field(type = FieldType.Keyword)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Keyword)
    private LocalDateTime updatedAt;
}
