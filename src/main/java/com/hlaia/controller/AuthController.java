package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.LoginRequest;
import com.hlaia.dto.request.RegisterRequest;
import com.hlaia.dto.response.AuthResponse;
import com.hlaia.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 【认证控制器】—— 提供用户注册、登录、登出、Token 刷新的 REST API
 *
 * ============================================================
 * 什么是 Controller？@RestController 和 @Controller 有什么区别？
 * ============================================================
 *   Controller 是 Spring MVC 中处理 HTTP 请求的组件。
 *   它是"入口"，客户端发来的请求由它接收，再分发给 Service 层处理。
 *
 *   @Controller vs @RestController：
 *     @Controller：返回视图页面（JSP、Thymeleaf 模板），适用于传统 Web 应用
 *     @RestController = @Controller + @ResponseBody
 *       - @ResponseBody 表示方法返回值直接作为 HTTP 响应体（JSON 格式）
 *       - 适用于前后端分离的项目（前端 Vue/React，后端只返回 JSON 数据）
 *       - 我们的项目是前后端分离的，所以用 @RestController
 *
 * ============================================================
 * RESTful API 设计原则
 * ============================================================
 *   REST（Representational State Transfer）是一种 API 设计风格：
 *
 *   核心思想：用 HTTP 方法（动词）+ URL（名词）来表达操作
 *     POST   /api/auth/register  → 注册（创建资源）
 *     POST   /api/auth/login     → 登录（创建会话/Token）
 *     POST   /api/auth/logout    → 登出（销毁会话/Token）
 *     POST   /api/auth/refresh   → 刷新（更新 Token）
 *
 *   为什么所有方法都用 POST？
 *     - 注册和登录包含敏感信息（密码），不应出现在 URL 中
 *     - GET 请求的参数会出现在浏览器历史和服务器日志中，不安全
 *     - POST 的请求体是加密传输的（HTTPS），更安全
 *
 *   为什么统一前缀 /api/auth/？
 *     - /api 表示这是一个 API 接口（不是页面）
 *     - /auth 表示属于认证模块（和书签 /bookmarks、管理 /admin 等区分）
 *     - 这样 SecurityConfig 中可以用 /api/auth/** 统一配置权限
 *
 * ============================================================
 * Swagger / Knife4j API 文档注解
 * ============================================================
 *   @Tag：给整个 Controller 打标签，在 API 文档页面中分组显示
 *     效果：在 Knife4j 文档页面中，这个 Controller 的所有接口
 *     会归到 "Authentication" 分组下
 *
 *   @Operation：给每个接口添加描述信息
 *     在文档页面中显示接口的摘要说明
 *     让其他开发者（或前端同事）快速了解每个接口的用途
 *
 *   这些注解不影响代码逻辑，纯粹是为了自动生成 API 文档。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication APIs")
public class AuthController {

    /** 认证业务逻辑层 —— Controller 不处理业务，全部委托给 Service */
    private final AuthService authService;

    /**
     * 用户注册接口
     *
     * HTTP 方法：POST
     * URL：/api/auth/register
     * 请求体示例：{"username": "alice", "password": "123456"}
     * 响应示例：{"code": 200, "message": "success", "data": {"accessToken": "...", ...}}
     *
     * @Valid 的作用：
     *   触发 RegisterRequest 中定义的参数校验规则（@NotBlank、@Size 等）。
     *   如果校验不通过，Spring 会自动返回 400 错误，不需要我们手写 if-else。
     *   校验发生在进入方法体之前，相当于"门卫检查"。
     *
     * @RequestBody 的作用：
     *   将 HTTP 请求体中的 JSON 数据自动反序列化为 Java 对象。
     *   Jackson 库会按照字段名自动映射：
     *     JSON 的 "username" → RegisterRequest 的 username 字段
     *     JSON 的 "password" → RegisterRequest 的 password 字段
     *
     * @param request 注册请求数据（由 Spring 从请求体 JSON 自动映射）
     * @return 统一响应，包含认证信息（Token + 用户信息）
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 用户登录接口
     *
     * HTTP 方法：POST
     * URL：/api/auth/login
     * 请求体示例：{"username": "alice", "password": "123456"}
     * 响应示例：{"code": 200, "message": "success", "data": {"accessToken": "...", ...}}
     *
     * @param request 登录请求数据
     * @return 统一响应，包含认证信息（Token + 用户信息）
     */
    @PostMapping("/login")
    @Operation(summary = "Login and get JWT tokens")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 用户登出接口
     *
     * HTTP 方法：POST
     * URL：/api/auth/logout
     * 请求头：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     * 响应示例：{"code": 200, "message": "success", "data": null}
     *
     * @RequestHeader("Authorization") 的作用：
     *   从 HTTP 请求头中获取指定名称的头部值。
     *   JWT Token 的标准传递方式是放在 Authorization 头中：
     *     Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *
     *   为什么用 "Bearer" 前缀？
     *     这是 OAuth 2.0 / JWT 的规范格式：
     *     - Bearer 表示"持有者令牌"——谁持有这个 Token，谁就是合法用户
     *     - 方便服务器区分不同类型的认证信息（Bearer、Basic 等）
     *
     *   authHeader.substring(7) 的作用：
     *     "Bearer eyJhbG..." 的前 7 个字符是 "Bearer "（注意空格），
     *     截取第 7 个字符之后的部分，得到纯 Token 字符串。
     *
     * 为什么返回 Result<Void> 而不是 Result<String>？
     *   登出操作不需要返回任何数据（只要知道成功或失败就行），
     *   用 Void 表示 data 字段为 null，避免泛型警告。
     *
     * @param authHeader Authorization 请求头的值，格式为 "Bearer {token}"
     * @return 无数据的成功响应
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout and blacklist the token")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        // 截取 "Bearer " 之后的部分，得到纯 Token 字符串
        String token = authHeader.substring(7);
        authService.logout(token);
        return Result.success();
    }

    /**
     * 刷新 Token 接口
     *
     * HTTP 方法：POST
     * URL：/api/auth/refresh?refreshToken=eyJhbGciOiJIUzI1NiJ9...
     * 响应示例：{"code": 200, "message": "success", "data": {"accessToken": "...", ...}}
     *
     * @RequestParam 的作用：
     *   从 URL 的查询字符串中获取参数值。
     *   例如：/api/auth/refresh?refreshToken=xxx
     *   @RequestParam String refreshToken 会获取 "xxx" 这个值。
     *
     * 为什么 refresh 用 @RequestParam 而不是 @RequestBody？
     *   - refresh 只有一个参数，不值得为此创建一个 Request DTO
     *   - 作为查询参数更简单，前端不需要构造 JSON 请求体
     *   - 这也是业界常见的 Token 刷新接口设计方式
     *
     * @param refreshToken 刷新令牌（从 URL 查询参数获取）
     * @return 统一响应，包含新的认证信息
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public Result<AuthResponse> refresh(@RequestParam String refreshToken) {
        return Result.success(authService.refresh(refreshToken));
    }
}
