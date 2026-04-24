package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.ChangePasswordRequest;
import com.hlaia.dto.request.UpdateProfileRequest;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 【用户控制器】—— 处理当前登录用户的个人资料相关请求
 *
 * 路径设计：/api/user/...
 *   与 /api/auth（认证）、/api/admin（管理员）区分开来。
 *   这些接口都需要登录后才能访问（由 SecurityConfig 的 anyRequest().authenticated() 保证）。
 *
 * RESTful 设计：
 *   GET  /api/user/profile   → 查看个人资料
 *   PUT  /api/user/profile   → 修改个人资料（全量更新用 PUT）
 *   PUT  /api/user/password  → 修改密码
 *
 * @AuthenticationPrincipal 的用法：
 *   与 BookmarkController 中相同，通过 Spring Security 自动注入当前用户的 ID。
 *   这个值来自 JwtAuthFilter 中设置的 principal。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Current user profile management APIs")
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户的个人资料
     *
     * @param userId 当前登录用户 ID（由 Spring Security 自动注入）
     * @return 用户信息（不含密码）
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current user's profile")
    public Result<UserResponse> getProfile(@AuthenticationPrincipal Long userId) {
        return Result.success(userService.getProfile(userId));
    }

    /**
     * 更新当前用户的个人资料（昵称、邮箱）
     *
     * @param userId  当前登录用户 ID
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    @PutMapping("/profile")
    @Operation(summary = "Update current user's profile")
    public Result<UserResponse> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(userId, request));
    }

    /**
     * 修改密码
     *
     * 返回 Result<Void> 因为修改密码后不需要返回任何数据，
     * 前端只需知道成功或失败。
     *
     * @param userId  当前登录用户 ID
     * @param request 修改密码请求（包含旧密码和新密码）
     */
    @PutMapping("/password")
    @Operation(summary = "Change current user's password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return Result.success();
    }
}
