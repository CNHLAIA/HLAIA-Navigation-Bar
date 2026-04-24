package com.hlaia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 【JWT 认证过滤器】—— 从请求头提取 Token 并设置认证信息
 *
 * ============================================================
 * Spring Security 的过滤器链（Filter Chain）概念
 * ============================================================
 *   Spring Security 的核心工作机制是"过滤器链"。
 *   可以把它想象成一条**安检流水线**：
 *
 *   请求到达的顺序：
 *     HTTP 请求 → CorsFilter → CsrfFilter → ... → JwtAuthFilter → ... → Controller
 *                  ↑ 跨域检查    ↑ CSRF检查      ↑ 我们自定义的JWT认证
 *
 *   每个 Filter 做一件特定的事情：
 *     - CorsFilter：处理跨域请求
 *     - CsrfFilter：防止 CSRF 攻击
 *     - JwtAuthFilter：（我们的）验证 JWT Token，设置认证信息
 *     - UsernamePasswordAuthenticationFilter：处理表单登录（我们不用，因为用 JWT）
 *
 *   OncePerRequestFilter 保证了同一个请求只会经过这个过滤器一次，
 *   避免在请求转发（forward）或包含（include）时重复执行。
 *
 * ============================================================
 * 这个过滤器的工作流程：
 * ============================================================
 *   1. 从请求头 Authorization 中提取 Bearer Token
 *   2. 验证 Token 是否有效（签名、过期时间）
 *   3. 检查 Token 是否在 Redis 黑名单中（用于登出功能）
 *   4. 从 Token 中提取用户信息（userId, role）
 *   5. 创建认证对象，设置到 SecurityContextHolder
 *   6. 继续执行后续过滤器和 Controller
 *
 * ============================================================
 * 什么是 SecurityContextHolder？
 * ============================================================
 *   SecurityContextHolder 是 Spring Security 存储**当前用户认证信息**的地方。
 *   它使用 ThreadLocal 来保证**线程安全**——每个请求线程都有自己独立的上下文。
 *
 *   工作原理：
 *     请求1（线程A）→ SecurityContextHolder 存入用户A的信息
 *     请求2（线程B）→ SecurityContextHolder 存入用户B的信息
 *     两个线程互不干扰！
 *
 *   一旦认证信息被设置到 SecurityContextHolder 中，
 *   后续的所有 Spring Security 组件（过滤器、注解等）都能读取到当前用户是谁。
 *
 *   在 Controller 中，可以通过以下方式获取当前登录用户：
 *     @AuthenticationPrincipal UserDetails userDetails
 *     SecurityContextHolder.getContext().getAuthentication()
 *
 * @Slf4j   Lombok 注解：自动生成 log 对象，用于记录日志
 * @Component 注册为 Spring Bean
 * @RequiredArgsConstructor Lombok 注解：为 final 字段生成构造函数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** JWT Token 工具类，用于验证和解析 Token */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Redis 模板 —— 用于检查 Token 黑名单
     *
     * 什么是 Token 黑名单？
     *   当用户主动登出时，我们不应该让他的 Token 继续有效。
     *   但 JWT 本身是无状态的——一旦签发就无法撤销。
     *   解决方案：在 Redis 中维护一个"黑名单"，
     *   每次请求都检查 Token 是否在黑名单中。
     *
     *   登出流程：
     *     用户点击"退出登录" → 后端将 Token 存入 Redis（key=jwt:blacklist:{token}, value="1"）
     *     → 设置过期时间 = Token 的剩余有效期
     *     → Token 过期后自动从 Redis 中移除
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 过滤器的核心方法 —— 每个请求都会执行
     *
     * doFilterInternal 的执行时机：
     *   HTTP 请求到达 → Spring Security 过滤器链 → ... → JwtAuthFilter.doFilterInternal() → Controller
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain  过滤器链，调用 filterChain.doFilter() 表示"继续执行下一个过滤器"
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ============================================================
        // 第 1 步：从请求头中提取 Token
        // ============================================================
        String token = resolveToken(request);

        // ============================================================
        // 第 2 步：验证 Token 有效性
        // ============================================================
        // StringUtils.hasText(token)：检查 token 不为 null、不为空、不全为空白字符
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            // ============================================================
            // 第 3 步：检查 Token 是否在 Redis 黑名单中
            // ============================================================
            // 如果用户已经登出，Token 会被加入黑名单，即使它还没过期也不能使用
            // 黑名单 key 格式：jwt:blacklist:{token值}
            String blacklistKey = "jwt:blacklist:" + token;
            Boolean isBlacklisted = stringRedisTemplate.hasKey(blacklistKey);

            if (Boolean.TRUE.equals(isBlacklisted)) {
                // Token 在黑名单中 → 说明用户已经登出
                log.warn("Token 已被加入黑名单（用户已登出）");
                // 不设置认证信息，请求会被 Spring Security 拦截返回 401
            } else {
                // ============================================================
                // 第 4 步：提取用户信息，创建认证对象
                // ============================================================
                // 从 Token 的 Payload 中提取用户 ID 和角色
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

                // UsernamePasswordAuthenticationToken 是 Spring Security 的认证令牌
                // 它携带了当前用户的身份信息（principal）和权限（authorities）
                //
                // 构造参数说明：
                //   1. principal → 主体，这里放用户 ID（后续可通过 SecurityContextHolder 获取）
                //   2. credentials → 凭证，这里不需要密码了（已经通过 JWT 验证），放 null
                //   3. authorities → 权限列表，基于角色构建
                //
                // 为什么 principal 放 userId 而不是 User 对象？
                //   1. 轻量级：避免每次请求都查数据库
                //   2. Token 中已经包含了必要信息（userId, role）
                //   3. 如果需要完整用户信息，可以在 Controller 中按需查询
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,                     // principal：用户 ID
                                null,                       // credentials：密码（不需要）
                                Collections.singletonList(   // authorities：权限列表
                                        new SimpleGrantedAuthority("ROLE_" + role)
                                )
                        );

                // ============================================================
                // 第 5 步：将认证信息设置到 SecurityContextHolder
                // ============================================================
                // 这一步是关键！设置之后：
                //   - Spring Security 就知道"当前用户已认证"
                //   - Controller 中可以通过 @AuthenticationPrincipal 获取用户信息
                //   - @PreAuthorize 等注解可以正常工作
                //   - Spring Security 不会再拦截这个请求
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证成功，用户ID: {}，角色: {}", userId, role);
            }
        }

        // ============================================================
        // 第 6 步：继续执行后续过滤器和最终的 Controller
        // ============================================================
        // 无论 Token 是否有效，都要调用 filterChain.doFilter()，
        // 让请求继续往后传递。如果没有有效 Token，
        // Spring Security 的后续机制会返回 401 Unauthorized。
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     *
     * HTTP 请求头的格式：
     *   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.xxx
     *                 ↑ 固定前缀    ↑ 实际的 JWT Token
     *
     * 为什么用 "Bearer" 前缀？
     *   "Bearer" 的意思是"持有者"——谁持有这个 Token，谁就是合法用户。
     *   这是 OAuth 2.0 / JWT 的标准写法（RFC 6750）。
     *
     * @param request HTTP 请求
     * @return Token 字符串，如果不存在则返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        // 获取 Authorization 请求头的值
        String bearerToken = request.getHeader("Authorization");

        // 检查是否以 "Bearer " 开头（注意 Bearer 后面有空格）
        // StringUtils.hasText() 确保字符串非空
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // 截取 "Bearer " 后面的部分，就是实际的 Token
            // "Bearer eyJhbG..." → "eyJhbG..."
            return bearerToken.substring(7);
        }

        return null;
    }
}
