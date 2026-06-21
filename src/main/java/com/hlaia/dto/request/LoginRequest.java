package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 【登录请求 DTO】—— 用户登录时提交的数据
 *
 * 什么是 DTO（Data Transfer Object，数据传输对象）？
 *   DTO 是专门用于在不同层之间传递数据的 Java 类。
 *   它不对应数据库表（那是 Entity 的事），而是描述"前端发给后端的数据"或"后端返回给前端的数据"。
 *
 *   为什么需要 DTO？直接用 Entity 不行吗？
 *   1. 安全性：Entity 包含所有数据库字段（如 id、role、status），如果直接用 Entity 接收前端数据，
 *      用户可能会恶意传入 role=ADMIN 来提权。DTO 只暴露必要的字段，杜绝了这种风险。
 *   2. 职责分离：Entity 是"数据库映射"，DTO 是"接口契约"，各司其职。
 *   3. 灵活性：不同接口需要不同字段，比如注册需要 confirmPassword，但登录不需要。
 *      用 DTO 可以为每个接口定制字段组合。
 *
 *   DTO 的分类：
 *   - Request DTO（请求 DTO）：前端 → 后端，如 LoginRequest
 *   - Response DTO（响应 DTO）：后端 → 前端，如 AuthResponse
 *
 * 什么是参数校验（Validation）？
 *   通过注解（如 @NotBlank）在数据进入 Controller 之前自动校验。
 *   如果校验不通过，Spring 会自动返回 400 错误，我们不需要手写 if-else 判断。
 *   前提：Controller 方法参数需要加 @Validated 或 @Valid 注解。
 */
@Data   // Lombok：自动生成 getter、setter、toString、equals、hashCode
public class LoginRequest {

    /**
     * 用户名
     *
     * @NotBlank：不能为空白（不能是 null、""、或纯空格 "   "）
     * 为什么登录时只用 @NotBlank 而不加 @Size？
     *   因为注册时已经校验过长度了，登录只需检查"不为空"即可，
     *   这样可以给出更精确的错误提示（"用户名或密码错误"而不是"长度不合法"）。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     * 注意：这里接收的是用户输入的明文密码，校验通过后再与数据库中的密文对比
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
