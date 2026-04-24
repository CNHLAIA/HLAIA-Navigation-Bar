package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【操作日志实体类】—— 对应数据库中的 operation_log 表
 *
 * 什么是操作日志？
 *   记录用户在系统中的所有重要操作，用于：
 *   1. 安全审计：追溯谁在什么时候做了什么
 *   2. 问题排查：出现 bug 时可以查看操作记录
 *   3. 数据统计：分析用户行为
 *
 * 日志通过 AOP（面向切面编程）+ 注解自动记录，
 * 不需要在每个方法里手写日志记录代码。
 *
 * 示例记录：
 *   action: "CREATE_BOOKMARK", target: "Bookmark", detail: "创建了书签: GitHub", ip: "192.168.8.100"
 *   action: "DELETE_FOLDER",   target: "Folder",   detail: "删除了文件夹: 临时", ip: "192.168.8.100"
 */
@Data
@TableName("operation_log")   // 对应数据库的 operation_log 表
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户ID
     * 记录是谁执行了这个操作
     */
    private Long userId;

    /**
     * 操作类型，如：
     * "CREATE_BOOKMARK"  创建书签
     * "DELETE_BOOKMARK"  删除书签
     * "CREATE_FOLDER"    创建文件夹
     * "DELETE_FOLDER"    删除文件夹
     * "MOVE_BOOKMARK"    移动书签
     * "LOGIN"            用户登录
     * "BAN_USER"         封禁用户
     */
    private String action;

    /**
     * 操作对象类型，如 "Bookmark"、"Folder"、"User"
     */
    private String target;

    /**
     * 操作详情，自由格式的描述文本
     * 如 "创建了书签: GitHub (https://github.com)"
     */
    private String detail;

    /**
     * 操作者的 IP 地址
     * 从 HTTP 请求中获取，用于安全审计
     */
    private String ip;

    /**
     * 操作时间
     * 注意：日志表只有 createdAt，没有 updatedAt，因为日志只增不改
     */
    private LocalDateTime createdAt;
}
