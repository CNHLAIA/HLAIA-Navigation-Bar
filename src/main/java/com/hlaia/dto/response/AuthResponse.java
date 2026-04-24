package com.hlaia.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 【认证响应 DTO】—— 登录/注册成功后返回给前端的数据
 *
 * 为什么用 @Builder？
 *   @Builder 是 Lombok 提供的设计模式注解，它让对象的创建更加优雅和可读。
 *
 *   不用 Builder（传统方式）：
 *     AuthResponse res = new AuthResponse();
 *     res.setAccessToken("xxx");
 *     res.setRefreshToken("yyy");
 *     res.setUsername("admin");
 *     res.setRole("ADMIN");
 *
 *   用 Builder（链式调用）：
 *     AuthResponse res = AuthResponse.builder()
 *         .accessToken("xxx")
 *         .refreshToken("yyy")
 *         .username("admin")
 *         .role("ADMIN")
 *         .build();
 *
 *   Builder 的好处：
 *   1. 可读性强：一眼就能看出设置了哪些字段
 *   2. 安全：不需要写 setter 方法，对象创建后就是不可变的（如果搭配 @Value 使用）
 *   3. 灵活：可以只设置部分字段，其他字段保持默认值
 *
 * 为什么 Response DTO 不需要校验注解？
 *   校验注解（@NotBlank 等）是用于"接收数据"的（Request DTO），
 *   确保"前端传来的数据是合法的"。
 *   Response DTO 是"返回数据"的，数据由后端 Service 层生成，
 *   我们信任自己的代码，不需要校验自己产生的数据。
 */
@Data   // 生成 getter、setter、toString、equals、hashCode
@Builder   // 生成 Builder 模式的链式创建方法
public class AuthResponse {

    /**
     * 访问令牌（Access Token）
     * JWT 格式的字符串，前端需要在每次请求的 Authorization 头中携带
     * 如："Bearer eyJhbGciOiJIUzI1NiJ9..."
     * 有效期较短（如 30 分钟），用于日常的接口访问
     */
    private String accessToken;

    /**
     * 刷新令牌（Refresh Token）
     * 当 Access Token 过期后，前端用这个令牌获取新的 Access Token
     * 有效期较长（如 7 天），用户不需要重新输入密码
     * 这样设计的好处：Access Token 短期有效即使泄露影响有限，Refresh Token 不常使用更安全
     */
    private String refreshToken;

    /**
     * 用户名
     * 返回给前端显示在页面上（如导航栏右上角显示 "欢迎，admin"）
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 用户角色
     * 前端根据角色显示不同的功能按钮
     * 如：ADMIN 可以看到"管理面板"按钮，USER 看不到
     */
    private String role;
}
