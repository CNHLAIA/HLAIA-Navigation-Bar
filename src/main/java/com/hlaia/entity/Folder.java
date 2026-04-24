package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【文件夹实体类】—— 对应数据库中的 folder 表
 *
 * 文件夹的核心设计：自引用（树形结构）
 *   通过 parentId 字段指向自己的父文件夹，实现无限层级的树形目录。
 *   - parentId = 0 表示顶级文件夹（根目录）
 *   - parentId = 3 表示这个文件夹的父文件夹 ID 是 3
 *
 *   示例树形结构：
 *   工作资料 (id=1, parentId=0)
 *   ├── 前端 (id=2, parentId=1)
 *   │   └── Vue (id=5, parentId=2)
 *   ├── 后端 (id=3, parentId=1)
 *   └── 工具 (id=4, parentId=1)
 *
 *   这种设计叫做"邻接表模型"（Adjacency List），是最常用的树形结构存储方式。
 *   优点：简单直观，增删改容易
 *   缺点：查询多层嵌套需要递归（MyBatis-Plus 可以在 service 层处理）
 */
@Data
@TableName("folder")   // 对应数据库的 folder 表
public class Folder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     * 每个用户有自己独立的文件夹树，通过 userId 隔离数据
     */
    private Long userId;

    /**
     * 父文件夹ID
     * 0 = 顶级文件夹（根目录下直接创建的）
     * 大于 0 = 父文件夹的 ID
     * 这是实现树形结构的关键字段
     */
    private Long parentId;

    /**
     * 文件夹名称，如"工作资料"、"学习资源"等
     */
    private String name;

    /**
     * 排序序号，数字越小排在越前面
     * 用于实现用户自定义排序（拖拽排序后更新这个值）
     */
    private Integer sortOrder;

    /**
     * 文件夹图标，可以是 emoji 或图标名称
     * 如 "📁"、"[work]" 等
     */
    private String icon;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 之后还要加组号，同一个文件夹内的标签也可以分组，用不同颜色区分标记*/
}
