package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【操作日志实体类】—— 对应数据库中的 operation_log 表
 *
 * 什么是操作日志？
 *   记录管理员执行的越权写操作，用于安全审计：
 *   追溯"谁在什么时候动了他人的数据"——万一误操作或账号被盗，有据可查。
 *
 * 为什么只记管理员越权操作，不记用户自己的操作？
 *   用户改自己的书签/文件夹不属于审计范畴（审计关心的是"越权"），全量记录只会
 *   产生噪音、淹没真正的关键事件。收窄后日志量从"每请求一条"降到"管理员越权时才记"。
 *
 * 日志通过 AOP 切面（OperationLogAspect）自动记录，只在 AdminController 的
 * 写操作方法（banUser/unbanUser/deleteFolder）上触发，无需在每个方法里手写代码。
 *
 * 示例记录：
 *   action: "BAN_USER",      target: "AdminController.banUser",      detail: "banned user 5"
 *   action: "DELETE_FOLDER", target: "AdminController.deleteFolder", detail: "deleted folder 12"
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
     * 操作类型，大写下划线常量，便于 SQL 检索。当前实际取值：
     * "BAN_USER"         封禁用户
     * "UNBAN_USER"       解封用户
     * "DELETE_FOLDER"    删除任意用户的文件夹
     *
     * 未来新增的 AdminController 写操作方法会由切面的 default 分支兜底，
     * action 取方法名大写（详见 OperationLogAspect）。
     */
    private String action;

    /**
     * 操作定位，格式为"类名.方法名"，如 "AdminController.banUser"。
     * 用于从日志反查代码位置。
     */
    private String target;

    /**
     * 操作详情，带业务上下文的描述文本。
     * 如 "banned user 5"（封禁了 ID=5 的用户）、"deleted folder 12"（删除了 ID=12 的文件夹）。
     * 由 OperationLogAspect 根据方法名 + 第一个参数（@PathVariable 的 ID）生成。
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
