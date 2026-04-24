package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 【注册请求 DTO】—— 用户注册时提交的数据
 *
 * 相比 LoginRequest，注册时需要更严格的校验规则：
 *   - 用户名有长度限制（太短容易重复，太长不好显示）
 *   - 密码有最小长度要求（保障安全性）
 *
 * @Size vs @NotBlank 的区别：
 *   @NotBlank 只检查"不为空"，不检查长度
 *   @Size 检查长度范围，但允许空值（所以需要搭配 @NotBlank 使用）
 *   两者组合使用 = 既不能为空，又要在指定长度范围内
 */
@Data
public class RegisterRequest {

    /**
     * 用户名
     *
     * @Size(min=3, max=50)：长度必须在 3~50 个字符之间
     *   min=3：太短的用户名没有辨识度，也容易被别人猜到
     *   max=50：与数据库字段 VARCHAR(50) 对应，防止超长数据插入失败
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50个字符之间")
    private String username;

    /**
     * 密码
     *
     * @Size(min=6, max=100)：长度必须在 6~100 个字符之间
     *   min=6：密码太短容易被暴力破解，6位是最低安全要求
     *   max=100：因为 BCrypt 加密后会变长（60字符），这里留够余量
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
    private String password;

    /**
     * 昵称
     * 非必填
     * 最多15个字符
     */
    @Size(max = 15, message = "昵称不能多于15个字符")
    private String nickname;
}
