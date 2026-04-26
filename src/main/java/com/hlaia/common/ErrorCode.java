package com.hlaia.common;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 【错误码枚举】—— 定义系统中所有可能的错误码和对应的错误信息
 *
 * 什么是枚举（enum）？
 *   枚举是一种特殊的类，它只能有固定数量的实例。
 *   在这里，每个枚举值（如 USER_EXISTS）就是一个 ErrorCode 对象，包含 code 和 message。
 *
 * 为什么要用枚举而不是直接写数字？
 *   1. 避免魔法数字：代码里写 1001 谁也不知道什么意思，写 ErrorCode.USER_EXISTS 一目了然
 *   2. 集中管理：所有错误码都定义在一个地方，方便查找和修改
 *   3. 类型安全：编译器会帮你检查，不会传错错误码
 *
 * 错误码规划规则：
 *   - 200-500：标准 HTTP 状态码
 *   - 1001-1999：用户认证相关错误（登录、注册、Token）
 *   - 2001-2999：业务逻辑相关错误（书签、文件夹、权限等）
 */
@Getter                          // Lombok 注解：自动生成所有字段的 getter 方法
@AllArgsConstructor               // Lombok 注解：自动生成包含所有参数的构造方法
                                  // 等价于手写 public ErrorCode(int code, String message) { ... }
public enum ErrorCode {

    // ========== 标准 HTTP 错误码 ==========
    SUCCESS(200, "success"),                    // 请求成功
    BAD_REQUEST(400, "Bad request"),            // 请求参数错误
    UNAUTHORIZED(401, "Unauthorized"),          // 未登录/未认证
    FORBIDDEN(403, "Forbidden"),                // 没有权限访问
    NOT_FOUND(404, "Not found"),                // 资源不存在
    INTERNAL_ERROR(500, "Internal server error"), // 服务器内部错误

    // ========== 认证相关错误（1001-1999） ==========
    USER_EXISTS(1001, "Username already exists"),       // 注册时用户名已存在
    INVALID_CREDENTIALS(1002, "Invalid username or password"), // 用户名或密码错误
    TOKEN_EXPIRED(1003, "Token expired"),               // JWT Token 已过期
    TOKEN_INVALID(1004, "Invalid token"),               // JWT Token 无效（被篡改或格式错误）
    NICKNAME_EXISTS(1005, "Nickname already exists"),   //注册时昵称已存在

    // ========== 业务相关错误（2001-2999） ==========
    FOLDER_NOT_FOUND(2001, "Folder not found"),         // 文件夹不存在
    BOOKMARK_NOT_FOUND(2002, "Bookmark not found"),     // 书签不存在
    STAGING_NOT_FOUND(2003, "Staging item not found"),  // 暂存区项目不存在
    USER_NOT_FOUND(2004, "User not found"),             // 用户不存在
    USER_BANNED(2005, "User is banned"),                // 用户已被封禁
    ACCESS_DENIED(2006, "Access denied"),               // 无权操作该资源
    RATE_LIMITED(2007, "Too many requests"),            // 请求过于频繁（限流）
    BOOKMARK_DUPLICATE(2009, "Bookmark already exists"), // 书签已存在（同一用户相同 URL）
    IMPORT_FAILED(2008, "Bookmark import failed"),      // 书签导入失败

    // ========== 自定义错误码 (9999 - 99999) (练习用) ==========
    SO_HANDSOME(10086, "You are so handsome");

    // 枚举的字段
    private final int code;        // 错误码数字，如 1001
    private final String message;  // 错误描述信息，如 "Username already exists"
}
