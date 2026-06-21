package com.hlaia.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【文件夹树响应 DTO】—— 以树形结构返回文件夹层级关系
 *
 * 为什么需要树形结构？
 *   文件夹是层级嵌套的（文件夹里可以有子文件夹），数据在数据库中是扁平存储的（每行一个文件夹）。
 *   但前端展示时需要嵌套的树形结构（方便渲染树形控件），所以后端需要把扁平数据组装成树。
 *
 *   数据库存储（扁平）：              前端需要的（树形）：
 *   id=1, parentId=0, name="工作"    工作资料
 *   id=2, parentId=1, name="前端"    ├── 前端
 *   id=3, parentId=1, name="后端"    ├── 后端
 *   id=4, parentId=2, name="Vue"     │   └── Vue
 *
 *   这个 DTO 的 children 字段就是用来承载子文件夹的，形成递归嵌套结构。
 *
 * 为什么不用 @Builder？
 *   树形结构的构建通常是递归的，需要在 Service 层手动设置 children 列表。
 *   使用 @Data（提供 setter）比 @Builder 更方便，因为组装过程中需要频繁地 set 字段。
 */
@Data
public class FolderTreeResponse {

    /** 文件夹ID */
    private Long id;

    /**
     * 父文件夹ID
     * 0 = 顶级文件夹
     * 这里保留 parentId 是为了前端判断层级关系和实现拖拽移动
     */
    private Long parentId;

    /** 文件夹名称 */
    private String name;

    /** 文件夹图标 */
    private String icon;

    /** 排序序号，数字越小越靠前 */
    private Integer sortOrder;

    /**
     * 子文件夹列表（递归结构）
     *
     * 这是树形结构的核心：FolderTreeResponse 中包含 List<FolderTreeResponse>，
     * 每个 FolderTreeResponse 又可以有自己的 children，形成无限层级的嵌套。
     *
     * 这种"自己包含自己的列表"的设计叫做"递归数据结构"或"自引用结构"，
     * 在树形、链表等数据结构中非常常见。
     *
     * 如果没有子文件夹，这个列表为空列表（不是 null），方便前端遍历时不用判空。
     */
    private List<FolderTreeResponse> children;

    /**
     * 子文件夹数量
     * 冗余字段，方便前端在不展开 children 的情况下显示数量角标
     * 如："工作资料 (3)" 表示有 3 个子文件夹
     */
    private Integer childFolderCount;

    /**
     * 该文件夹下的书签数量
     * 方便前端显示角标，如："前端 (5)" 表示有 5 个书签
     */
    private Integer bookmarkCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
