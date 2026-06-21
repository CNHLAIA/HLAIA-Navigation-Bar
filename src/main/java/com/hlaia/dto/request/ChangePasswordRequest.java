package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 【修改密码请求 DTO】—— 用户修改密码时提交的数据
 *
 * 为什么需要同时传入旧密码和新密码？
 *   安全考虑：防止攻击者在用户已登录的情况下（如用户忘了退出）直接修改密码。
 *   要求输入旧密码可以验证操作者确实是用户本人。
 *
 * 前端通常还会要求"确认新密码"，但那是前端的校验逻辑，
 * 后端只需要接收最终的新密码即可。
 */
@Data
public class ChangePasswordRequest {

    /**
     * 旧密码（当前密码）
     * 用于验证操作者身份，防止未授权的密码修改
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * 新密码
     * 最少 6 个字符，与注册时的密码规则一致
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度必须在6到100个字符之间")
    private String newPassword;
}
