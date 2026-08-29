package com.hlaia.service;

import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.AuthResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import com.hlaia.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【AuthService 单元测试】—— 验证"持久登录"策略下的 refresh / logout 行为
 *
 * ============================================================
 * 这些测试守护的核心契约（单人部署的产品决策）
 * ============================================================
 *   1. Refresh Token 不轮换：refresh 成功后旧 Token 必须仍然可用
 *      （不能进黑名单）。这是"多标签页并发刷新不互踢"的前提，
 *      曾是"经常莫名退出登录"的根因，回归风险必须被测试盯住。
 *   2. 登出必须拉黑 Refresh Token：否则长期 Token 在登出后仍可静默续期，
 *      "一处登出，全端下线"就失效了。
 *   3. 兼容旧客户端：logout 不带 refreshToken 时退回旧行为（只拉黑 Access）。
 *
 * 测试技巧说明：
 *   StringRedisTemplate 的 opsForValue() 返回 ValueOperations 接口，
 *   需要单独 Mock 这个返回值，才能验证黑名单的写入（set 调用）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("admin");
        mockUser.setNickname("管理员");
        mockUser.setRole("ADMIN");
    }

    @Test
    @DisplayName("refresh 成功后不应将旧 Refresh Token 加入黑名单（不轮换契约）")
    void should_not_blacklist_old_refresh_token_when_refresh_succeeds() {
        // Arrange：一个格式有效、不在黑名单中的 Refresh Token
        String oldRefreshToken = "old-refresh-token";
        when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:" + oldRefreshToken)).thenReturn(false);
        when(jwtTokenProvider.getUserIdFromToken(oldRefreshToken)).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(mockUser);
        when(jwtTokenProvider.generateAccessToken(1L, "admin", "管理员", "ADMIN"))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(1L, "admin", "管理员", "ADMIN"))
                .thenReturn("new-refresh-token");

        // Act
        AuthResponse response = authService.refresh(oldRefreshToken);

        // Assert：签发了新 token 对，但从未向 Redis 写入任何黑名单条目
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("refresh 同一 Token 第二次调用也应成功（并发/重复刷新不互踢）")
    void should_allow_repeated_refresh_with_same_token() {
        String refreshToken = "shared-refresh-token";
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:" + refreshToken)).thenReturn(false);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(mockUser);
        when(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn("access-1", "access-2");
        when(jwtTokenProvider.generateRefreshToken(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn("refresh-1", "refresh-2");

        // Act：同一个 refreshToken 连续刷新两次（模拟两个标签页并发）
        AuthResponse first = authService.refresh(refreshToken);
        AuthResponse second = authService.refresh(refreshToken);

        // Assert：两次都成功，各自拿到新 token 对
        assertThat(first.getAccessToken()).isEqualTo("access-1");
        assertThat(second.getAccessToken()).isEqualTo("access-2");
    }

    @Test
    @DisplayName("登出时应同时拉黑 Access Token 和 Refresh Token（全端下线契约）")
    void should_blacklist_both_tokens_when_logout_with_refresh_token() {
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        // 过期时间设为未来 1 小时，保证 remainingMs > 0 分支被执行
        when(jwtTokenProvider.getExpirationFromToken(anyString()))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000L));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act
        authService.logout(accessToken, refreshToken);

        // Assert：两个 token 都写入了黑名单
        verify(valueOperations).set(eq("jwt:blacklist:" + accessToken), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOperations).set(eq("jwt:blacklist:" + refreshToken), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("登出不带 refreshToken 时只拉黑 Access Token（兼容旧客户端）")
    void should_only_blacklist_access_token_when_logout_without_refresh_token() {
        String accessToken = "access-token";
        when(jwtTokenProvider.getExpirationFromToken(accessToken))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000L));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Act：模拟旧客户端不传 refreshToken
        authService.logout(accessToken, null);

        // Assert：黑名单总共只写入一次，且是 Access Token 的 key
        verify(valueOperations, times(1))
                .set(eq("jwt:blacklist:" + accessToken), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOperations, times(1))
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("refresh 已被登出拉黑的 Token 时应抛 TOKEN_INVALID")
    void should_throw_token_invalid_when_refresh_token_is_blacklisted() {
        String refreshToken = "logged-out-refresh-token";
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        // 模拟用户已在其他端登出：该 token 在 Redis 黑名单中
        when(redisTemplate.hasKey("jwt:blacklist:" + refreshToken)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID.getCode());
        // 黑名单命中后不应再查库签发新 token
        verify(userMapper, never()).selectById(anyLong());
    }
}
