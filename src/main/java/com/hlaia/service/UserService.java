package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.ChangePasswordRequest;
import com.hlaia.dto.request.UpdateProfileRequest;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 【用户服务层】—— 处理当前登录用户的个人资料管理
 *
 * 与 AuthService 的区别：
 *   AuthService 处理的是"认证"相关逻辑（注册、登录、登出、Token 刷新）
 *   UserService 处理的是"用户信息管理"（查看/修改个人资料、修改密码）
 *
 * 职责分离的好处：
 *   - 每个类职责单一，代码更清晰
 *   - 安全配置更容易管理（auth 路径公开，user 路径需要认证）
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取当前用户的个人资料
     *
     * @param userId 用户 ID（从 JWT Token 中提取）
     * @return 用户信息（不含密码）
     */
    public UserResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserResponse(user);
    }

    /**
     * 更新当前用户的个人资料（昵称、邮箱）
     *
     * 为什么需要检查昵称唯一性？
     *   注册时已经确保昵称唯一，修改昵称时同样需要检查。
     *   但要排除用户自己——如果用户没有修改昵称，不应该报"昵称已存在"。
     *
     * @param userId  用户 ID
     * @param request 更新请求（包含 nickname 和 email）
     * @return 更新后的用户信息
     */
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 检查昵称唯一性（排除自己）
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            // 如果昵称与当前不同，才检查唯一性
            if (!request.getNickname().equals(user.getNickname())) {
                Long count = userMapper.selectCount(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getNickname, request.getNickname())
                                .ne(User::getId, userId) // 排除自己
                );
                if (count > 0) {
                    throw new BusinessException(ErrorCode.NICKNAME_EXISTS);
                }
            }
            user.setNickname(request.getNickname());
        } else {
            // 空字符串视为清除昵称
            user.setNickname(null);
        }

        // 更新邮箱
        user.setEmail(request.getEmail());

        userMapper.updateById(user);
        return toUserResponse(user);
    }

    /**
     * 修改密码
     *
     * 为什么修改密码后不需要重新登录？
     *   当前实现中，已有的 Token 仍然有效直到过期。
     *   如果需要修改密码后立即让所有 Token 失效，
     *   可以在此处将用户所有 Token 加入黑名单（需要额外实现）。
     *   目前为了简化，保持已有 Token 有效。
     *
     * @param userId  用户 ID
     * @param request 修改密码请求（包含旧密码和新密码）
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码是否正确
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 设置新密码（加密后存储）
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 将 User 实体转换为 UserResponse DTO
     * 不暴露 password 等敏感字段
     */
    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
