package com.hlaia.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 【更新个人资料请求 DTO】—— 用户修改个人信息时提交的数据
 *
 * 可修改的字段：
 *   - nickname：昵称（可选）
 *   - email：邮箱（可选）
 *
 * 注意：用户名（username）不允许修改，因为它是登录凭证的一部分，
 * 修改用户名需要更复杂的流程（如验证邮箱、重新登录等）。
 */
@Data
public class UpdateProfileRequest {

    /**
     * 昵称
     * 非必填，最多 15 个字符
     * 传 null 或空字符串表示清除昵称
     */
    @Size(max = 15, message = "昵称不能多于15个字符")
    private String nickname;

    /**
     * 邮箱地址
     * 非必填，需符合邮箱格式
     */
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
}
