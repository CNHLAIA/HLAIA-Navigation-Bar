package com.hlaia.document;


import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

/**
 * 【ES 文档模型 —— 书签搜索文档】
 *
 * BookmarkDocument vs Bookmark（Entity）的区别：
 *   - Bookmark（Entity）：对应 MySQL 表的完整字段，包含所有业务数据
 *   - BookmarkDocument（Document）：只包含搜索需要的字段，是 ES 索引中的文档结构
 *
 *   为什么要分开？
 *   1. 不是所有字段都需要搜索（如 sortOrder 不需要）
 *   2. 搜索结果的展示格式可能与原始数据不同（如需要高亮）
 *   3. ES 和 MySQL 是两个独立的存储系统，各自优化自己的数据结构
 *
 * @Document 注解 —— 声明这是一个 ES 文档
 *   indexName = "bookmark"：ES 中的索引名称（类似 MySQL 的表名）
 *   shards = 1：索引的分片数。分片是 ES 分布式存储的单元，
 *     1 个分片表示所有数据存在一个分片上。对于小项目，1 个分片足够。
 *     大型项目可能需要多个分片来分散存储和并行查询。
 *   replicas = 0：副本数。副本是分片的复制，用于高可用和负载均衡。
 *     单节点模式下副本必须为 0（因为没有其他节点来存放副本）。
 *
 * @Setting 注解 —— 自定义索引设置
 *   在这里我们设置分片和副本数。如果不设置，ES 默认 1 分片 1 副本。
 *   单节点模式下如果设置副本 > 0，索引会一直处于"黄色"警告状态（因为副本无法分配）。
 */
@Data
@Document(indexName = "bookmark")
@Setting(shards = 1, replicas = 0)
public class BookmarkDocument {

    /**
     * @Id 注解 —— 标记这是文档的唯一标识（等价于 MySQL 的主键）
     * ES 中每个文档都有一个 _id 字段，@Id 注解将这个字段映射到 Java 属性。
     * 我们使用 MySQL 中 bookmark 的 id 作为 ES 文档的 _id，
     * 这样可以确保 MySQL 和 ES 中的数据一一对应。
     */
    @Id
    private Long id;


    /**
     * @Field 注解 —— 定义字段在 ES 中的存储和索引行为
     *
     * type = FieldType.Text：
     *   Text 类型会被分词器分词后建立倒排索引，适合全文搜索。
     *   例如 "Spring Boot 教程" 会被分词为 ["spring", "boot", "教程"]，
     *   搜索 "spring" 就能匹配到。
     *
     * type = FieldType.Keyword：
     *   Keyword 类型不会分词，整个值作为一个整体建立索引。
     *   适合精确匹配，如用户 ID、状态码等。
     *
     * analyzer = "standard"：
     *   指定索引时使用的分词器。standard 是 ES 默认的分词器，
     *   对英文按空格和标点分词，对中文按单个字分词。
     *   注意：standard 对中文的分词效果不好（每个字一个词），
     *   生产环境通常会换成 ik_max_word（中文智能分词插件），
     *   但 ik 插件需要单独安装，MVP 阶段先用 standard。
     */
    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long folderId;

    /**
     * 书签标题 —— 全文搜索的主要字段
     * 使用 Text 类型支持分词搜索，同时配置了 searchAnalyzer
     * analyzer：索引时使用的分词器（写入数据时如何分词）
     * searchAnalyzer：搜索时使用的分词器（用户输入的关键词如何分词）
     * 大多数情况下两者相同，但某些场景下可以不同（如搜索时用更粗粒度的分词）
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    /**
     * URL —— 使用 Keyword 类型
     * URL 通常不需要分词搜索（用户不会搜 "https://com"），
     * 但用户可能想搜域名，所以也添加 Text 类型的子字段
     *
     * 不过为简化，MVP 阶段 URL 也用 Text 类型
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String url;

    /**
     * 描述 —— 全文搜索的辅助字段
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    /**
     * 图标 URL —— 不需要搜索，但搜索结果需要显示
     * 不添加 @Field 注解也会被 ES 存储，但不会被索引（不可搜索）
     * 显式标注 Keyword 类型表示我们不搜索它，只存储和展示
     */
    @Field(type = FieldType.Keyword)
    private String iconUrl;

    /**
     * 创建时间和更新时间 —— 使用 Keyword 类型而非 Date 类型
     *
     * 为什么不用 FieldType.Date？
     *   ES 中存储的日期格式可能是 "2026-04-24"（只有日期），
     *   但 Java 的 LocalDateTime 需要完整的时间戳（如 "2026-04-24T10:30:00"）。
     *   Spring Data ES 无法将 "2026-04-24" 自动转换为 LocalDateTime，会报转换错误。
     *
     * 为什么用 Keyword 可以？
     *   Keyword 不做类型转换，原样存储和返回字符串。
     *   我们不需要按日期范围搜索，只需要在搜索结果中展示，
     *   所以 Keyword 完全够用，还能避免格式转换问题。
     */
    @Field(type = FieldType.Keyword)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Keyword)
    private LocalDateTime updatedAt;
}
