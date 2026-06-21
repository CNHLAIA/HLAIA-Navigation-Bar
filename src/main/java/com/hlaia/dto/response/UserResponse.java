package com.hlaia.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【用户响应 DTO】—— 返回给前端的用户信息（用于管理员查看用户列表）
 *
 * 为什么 Response DTO 不包含 password 字段？
 *   这是后端安全的基本原则：永远不要把密码返回给前端！
 *   即使密码是加密存储的（如 BCrypt），泄露密文仍然存在安全隐患：
 *   1. 攻击者可能通过密文推断出加密算法和部分信息
 *   2. 前端代码、浏览器网络请求、日志等都可能泄露数据
 *   3. 安全最小化原则：前端不需要的信息，后端就不应该提供
 *
 *   所以 UserResponse 只包含前端展示用户列表所需的字段：
 *   id、username、email、role、status、createdAt
 *   而 password 字段只存在于 User 实体和数据库中，绝不对外暴露。
 *
 * Entity 和 Response DTO 的分离意义：
 *   - User 实体对应数据库表，包含所有字段（包括敏感的 password）
 *   - UserResponse 只包含前端需要展示的字段
 *   - Service 层负责 User -> UserResponse 的转换（在 toUserResponse 方法中完成）
 *   - 这样即使以后 User 实体增加了新字段，也不会意外暴露给前端
 */
@Data   // Lombok 注解：自动生成 getter、setter、toString、equals、hashCode 方法
public class UserResponse {

    /** 用户ID，数据库主键 */
    private Long id;

    /** 用户名，用于显示和登录 */
    private String username;

    /**
     * 昵称
     * 可能为 null (昵称是可选字段)
     */
    private String nickname;

    /**
     * 邮箱地址
     * 可能为 null（邮箱是可选字段）
     */
    private String email;

    /**
     * 用户角色
     * "ADMIN" = 管理员，可以管理所有用户和数据
     * "USER"  = 普通用户，只能操作自己的数据
     */
    private String role;

    /**
     * 用户状态
     * 0 = 正常（可以正常登录和使用）
     * 1 = 封禁（被管理员封禁，无法登录）
     */
    private Integer status;

    /**
     * 账号创建时间
     * 管理员查看用户列表时，可以看到用户是什么时候注册的
     */
    private LocalDateTime createdAt;
}
