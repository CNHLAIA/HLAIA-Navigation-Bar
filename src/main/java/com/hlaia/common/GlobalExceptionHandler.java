package com.hlaia.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 【全局异常处理器】—— 捕获所有 Controller 层抛出的异常，转换为统一的 JSON 响应
 *
 * 什么是 @RestControllerAdvice？
 *   这是 Spring 提供的一个注解，标记的类会成为"全局异常处理器"。
 *   它会拦截所有 @RestController 中抛出的异常，然后根据异常类型
 *   调用对应的 @ExceptionHandler 方法处理。
 *
 *   可以理解为：一个全局的 try-catch，不需要在每个 Controller 里单独写。
 *
 * 什么是 jakarta 包名？
 *   Java EE 被 Eclipse 基金会接管后，包名从 javax 改成了 jakarta。
 *   Spring Boot 3.x 以上版本使用 jakarta 命名空间。
 *
 * 处理流程：
 *   Controller 方法抛出异常 → Spring 捕获 → GlobalExceptionHandler 匹配对应方法 → 返回 Result JSON
 */
@Slf4j
@RestControllerAdvice   // 告诉 Spring：这是一个全局异常处理组件
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     *
     * 这是最常用的处理器。当我们在 service 层抛出 BusinessException 时：
     *   throw new BusinessException(ErrorCode.USER_NOT_FOUND);
     * 就会被这个方法捕获，提取 code 和 message，返回给前端。
     *
     * 注意：@ResponseStatus(HttpStatus.OK) 表示 HTTP 状态码是 200，
     * 因为我们通过 Result 中的 code 字段来区分成功/失败，HTTP 层面都是 200。
     */
    @ExceptionHandler(BusinessException.class)  // 指定要捕获的异常类型
    @ResponseStatus(HttpStatus.OK)              // HTTP 响应状态码设为 200
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体参数校验失败异常（@Valid 触发）
     *
     * 当使用 @Valid 注解校验请求体（如 @NotBlank 用户名不能为空），
     * 校验不通过时 Spring 会抛出 MethodArgumentNotValidException。
     *
     * 这里提取第一个错误信息返回给前端。
     *
     * 示例：前端提交注册表单时 username 为空 → 返回 "username: 不能为空"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // HTTP 400 错误请求
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        // 从异常中提取所有字段错误，取第一个返回
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())  // 拼接 "字段名: 错误信息"
                .findFirst()                                             // 只取第一个错误
                .orElse("Validation failed");                           // 如果没有错误信息，用默认值
        return Result.error(400, msg);
    }

    /**
     * 处理路径参数/查询参数校验失败异常（@Validated 触发）
     *
     * 当在方法参数上使用 @NotNull、@Size 等注解（配合 @Validated），
     * 校验不通过时会抛出 ConstraintViolationException。
     *
     * 和上面的区别：
     *   - MethodArgumentNotValidException → 校验 JSON 请求体（@RequestBody + @Valid）
     *   - ConstraintViolationException → 校验路径参数/查询参数（@PathVariable, @RequestParam）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理登录凭证错误异常（Spring Security 抛出）
     *
     * 当用户名或密码错误时，Spring Security 会抛出 BadCredentialsException。
     * 我们捕获它并返回统一的错误响应。
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)  // HTTP 401 未认证
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(ErrorCode.INVALID_CREDENTIALS);  // "用户名或密码错误"
    }

    /**
     * 处理权限不足异常（Spring Security 抛出）
     *
     * 当用户已登录但尝试访问没有权限的资源时，Spring Security 会抛出 AccessDeniedException。
     * 例如：普通用户尝试访问管理员接口。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)  // HTTP 403 禁止访问
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(ErrorCode.FORBIDDEN);  // "Forbidden"
    }

    /**
     * 兜底异常处理器：捕获所有未被上面方法处理的异常
     *
     * 这是最后一个防线。如果出现了意料之外的异常（如 NullPointerException、
     * 数据库连接失败等），会被这个方法捕获，返回 500 错误。
     *
     * 注意顺序：Spring 会从上到下匹配，先匹配具体的异常类型，最后才匹配 Exception（最宽泛的）。
     */
    @ExceptionHandler(Exception.class)  // 匹配所有异常
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)  // HTTP 500 服务器内部错误
    public Result<Void> handleException(Exception e) {
        // 必须打印堆栈，否则 500 错误无法排查！
        log.error("未处理异常", e);
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
