package com.hlaia.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 【导出文件夹节点】—— 可视化 HTML 导出时，文件夹树的一个节点（递归结构）
 *
 * 为什么不直接复用 {@link FolderTreeResponse}？
 *   FolderTreeResponse 是侧栏树的契约，前端到处在用，且它只携带 bookmarkCount（一个数字），
 *   并不含书签的 title/url/iconUrl 详情。
 *   如果给它加 bookmarks 字段：
 *     a) 每个 GET /folders/tree 响应体会暴涨（全量书签数据），侧栏加载变慢；
 *     b) 破坏既有调用方的语义（侧栏只要计数，不要详情）。
 *   导出是一次性全量快照，语义不同，独立 DTO 更干净。
 *
 * 递归结构说明：
 *   children 字段是 List&lt;ExportFolderNode&gt;——「自己包含自己的列表」，
 *   形成无限层级的嵌套（文件夹里有子文件夹，子文件夹里还有子文件夹……）。
 *   与 FolderTreeResponse.children 的设计完全一致，只是节点类型换成导出专用。
 *
 * 字段在导出 HTML 中的用途：
 *   - id：前端生成锚点 / TOC 跳转用
 *   - name：区块标题
 *   - icon：文件夹图标（emoji/图标名），可选
 *   - sortOrder：根文件夹之间的排序
 *   - children：嵌套子文件夹（缩进 + 引导线表达层级）
 *   - bookmarks：该文件夹直属的书签卡片网格
 */
@Data
public class ExportFolderNode {

    /** 文件夹 ID */
    private Long id;

    /** 文件夹名称 */
    private String name;

    /** 文件夹图标（emoji 或图标名），可为空 */
    private String icon;

    /** 排序序号，同层级内数字越小越靠前 */
    private Integer sortOrder;

    /**
     * 子文件夹列表（递归嵌套，无限层）
     * 没有子文件夹时为空列表（不是 null），方便前端遍历时不用判空。
     */
    private List<ExportFolderNode> children;

    /**
     * 该文件夹直属的书签列表
     * 注意：「直属」指 folderId == 该文件夹 id 的书签，不含子文件夹里的书签
     * （子文件夹的书签在对应子节点的 bookmarks 字段里）。
     */
    private List<ExportBookmarkNode> bookmarks;
}
