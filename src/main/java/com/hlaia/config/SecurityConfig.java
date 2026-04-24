package com.hlaia.config;

import com.hlaia.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 【Spring Security 核心配置类】—— 定义安全策略和过滤器链
 *
 * ============================================================
 * 什么是 Spring Security？
 * ============================================================
 *   Spring Security 是 Spring 生态中的安全框架，提供了：
 *     - 身份认证（Authentication）：验证"你是谁"（登录）
 *     - 权限控制（Authorization）：决定"你能做什么"（角色权限）
 *     - 攻击防护：CSRF、CORS、Session Fixation 等
 *
 *   可以把 Spring Security 理解为小区的"门禁系统"：
 *     - 身份认证 = 门口刷门禁卡（验证你是业主）
 *     - 权限控制 = 不同门禁卡能进不同的区域（业主 vs 物业管理员）
 *     - 过滤器链 = 从大门口到单元门的多重安检
 *
 * ============================================================
 * 关键注解解释
 * ============================================================
 *
 * @Configuration：告诉 Spring 这是一个配置类，里面定义了各种 Bean。
 *
 * @EnableWebSecurity：启用 Spring Security 的 Web 安全功能。
 *   这是 Spring Security 的总开关，不加这个注解，安全配置不会生效。
 *
 * @EnableMethodSecurity：启用方法级别的权限控制。
 *   启用后可以在 Controller 或 Service 的方法上使用：
 *     @PreAuthorize("hasRole('ADMIN')")   → 方法执行前检查权限
 *     @PostAuthorize("returnObject.owner == authentication.name")  → 方法执行后检查
 *     @PreAuthorize("hasAuthority('ROLE_USER')")  → 检查具体权限
 *
 *   什么是方法级权限控制？
 *     URL 级别的权限（在 filterChain 中配置）只能控制"谁能访问哪个 URL"，
 *     但有时同一个 URL 根据不同条件需要不同的权限。
 *     方法级权限可以在代码层面做更细粒度的控制。
 *
 *     示例：
 *       @GetMapping("/api/bookmarks/{id}")
 *       @PreAuthorize("hasRole('USER')")  // 只有 USER 角色能访问
 *       public Result getBookmark(@PathVariable Long id) { ... }
 *
 * @RequiredArgsConstructor：Lombok 注解，为 final 字段生成构造函数。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * JWT 认证过滤器 —— 我们自定义的过滤器
     *
     * 在过滤器链中的位置：在 UsernamePasswordAuthenticationFilter 之前执行
     */
    private final JwtAuthFilter jwtAuthFilter;

    /**
     * 配置安全过滤器链 —— 定义"谁能访问什么"
     *
     * 什么是 SecurityFilterChain？
     *   SecurityFilterChain 是 Spring Security 的核心接口，
     *   它定义了一系列安全过滤器和规则，每个 HTTP 请求都会经过这些过滤器。
     *
     *   请求处理流程：
     *     HTTP 请求 → SecurityFilterChain（一系列过滤器）→ Controller
     *                  ↓
     *     CorsFilter → CsrfFilter → ... → JwtAuthFilter → ... → 权限检查 → Controller
     *
     *   如果请求没有通过某个过滤器的检查，会直接返回错误响应（如 401、403）。
     *
     * @param http HttpSecurity 对象，Spring Security 提供的配置建造者
     * @return 配置好的 SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ============================================================
            // 禁用 CSRF（跨站请求伪造）保护
            // ============================================================
            // 什么是 CSRF？
            //   CSRF 攻击：恶意网站诱导用户的浏览器向目标网站发送请求，
            //   利用浏览器自动携带的 Cookie（Session ID）来完成攻击。
            //
            // 为什么可以禁用？
            //   CSRF 保护主要针对"基于 Cookie + Session"的认证方式。
            //   我们使用 JWT Token（存储在请求头中，而不是 Cookie），
            //   浏览器不会自动携带 JWT Token，所以 CSRF 攻击对我们无效。
            //
            //   类比：CSRF 就像有人拿着你的门禁卡（Cookie）偷偷刷卡进门，
            //   但我们的门禁系统不看卡，而是看手机验证码（JWT Token），
            //   手机验证码不会自动发送，所以这种攻击无效。
            .csrf(AbstractHttpConfigurer::disable)

            // ============================================================
            // 配置会话管理策略为 STATELESS（无状态）
            // ============================================================
            // 什么是 Session？
            //   传统 Web 应用使用 Session 来记住用户状态：
            //     用户登录 → 服务器创建 Session → 返回 Session ID（存在 Cookie 中）
            //     后续请求 → 浏览器自动携带 Cookie → 服务器通过 Session ID 找到用户
            //
            // 什么是 STATELESS？
            //   STATELESS 意味着服务器**不会创建或使用 HTTP Session**。
            //   每个请求都是独立的，必须自己携带认证信息（JWT Token）。
            //
            // 为什么使用 STATELESS？
            //   1. JWT 本身就是无状态的，Token 中包含了所有认证信息
            //   2. 不需要服务器存储 Session，节省内存
            //   3. 方便水平扩展：多台服务器之间不需要共享 Session
            //      （传统方式需要用 Redis 存储 Session，增加复杂度）
            //
            //   有状态 vs 无状态类比：
            //     有状态（Session）：酒店前台记住你的房间号，你报姓名就能进
            //     无状态（JWT）：酒店给你一张房卡，你自己刷卡进门，前台不记你
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ============================================================
            // 配置 URL 访问权限
            // ============================================================
            // authorizeHttpRequests 定义了"哪些 URL 需要什么权限"
            //
            // 配置规则从上到下匹配，第一个匹配的规则生效（顺序很重要！）
            .authorizeHttpRequests(auth -> auth
                // ---------- 公开接口（不需要登录就能访问） ----------

                // 认证相关接口：登录、注册、刷新 Token 等
                // 这些接口本身就是用来"获取身份"的，当然不能要求先登录
                .requestMatchers("/api/auth/**").permitAll()

                // API 文档相关路径（Knife4j / Swagger）(改了，不是knife4j了，而是springdoc-openapi)
                // permitAll() 表示"允许所有人访问，不需要认证"
                .requestMatchers(
                    "/swagger-ui.html",              // Knife4j 文档页面
                    "/swagger-ui/**",              // 前端静态资源（JS、CSS）
                    "/v3/api-docs/**",          // OpenAPI 3.0 规范的 JSON/YAML
                    "/swagger-resources/**"     // Swagger 资源配置
                ).permitAll()

                // ---------- 管理员接口（需要 ADMIN 角色） ----------

                // /api/admin/** 下的所有接口只有 ADMIN 角色才能访问
                // hasRole("ADMIN") 会自动匹配 "ROLE_ADMIN" 权限
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // ---------- 其他所有接口（需要登录） ----------

                // anyRequest().authenticated()：除了上面列出的 URL，
                // 其他所有请求都需要认证（必须携带有效的 JWT Token）
                .anyRequest().authenticated()
            )

            // ============================================================
            // 将自定义的 JWT 过滤器添加到过滤器链中
            // ============================================================
            // addFilterBefore(A, B)：在过滤器 B 之前执行过滤器 A
            //
            // 我们把 JwtAuthFilter 放在 UsernamePasswordAuthenticationFilter 之前：
            //   请求 → ... → JwtAuthFilter → UsernamePasswordAuthenticationFilter → ...
            //
            // 原因：
            //   UsernamePasswordAuthenticationFilter 是 Spring Security 默认的认证过滤器，
            //   它负责处理"表单登录"（用户名+密码提交到 /login）。
            //   我们不使用表单登录，而是用 JWT，所以需要：
            //   1. 先用 JwtAuthFilter 从 Token 中提取认证信息
            //   2. 跳过 UsernamePasswordAuthenticationFilter（因为已经有认证信息了）
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器 —— 使用 BCrypt 算法加密密码
     *
     * ============================================================
     * 为什么用 BCrypt 而不是 MD5？
     * ============================================================
     *
     * MD5 的问题：
     *   1. **速度太快**：MD5 设计目标是"快"，GPU 每秒可计算数十亿次。
     *      攻击者可以快速暴力破解（彩虹表攻击）。
     *   2. **无盐值**：相同密码的 MD5 值相同。
     *      如果两个用户都用 "123456"，它们的 MD5 一样，泄露一个就等于泄露所有。
     *   3. **已被破解**：MD5 存在碰撞漏洞，不推荐用于安全场景。
     *
     * BCrypt 的优势：
     *   1. **自带盐值（Salt）**：每次加密自动生成随机盐值，相同密码每次结果不同。
     *      例如：BCrypt("123456") 两次加密的结果完全不同
     *            $2a$10$AbCdEf...xyz  ← 不同的盐值
     *            $2a$10$XyZwVu...abc  ← 不同的盐值
     *   2. **计算慢**：BCrypt 的设计目标就是"慢"（可以调节 cost factor）。
     *      这使得暴力破解的成本极高。
     *   3. **自适应**：随着硬件性能提升，可以增加 cost factor 保持安全性。
     *
     *   安全性对比：
     *     MD5("123456")    → e10adc3949ba59abbe56e057f20f883e  （可以秒查彩虹表）
     *     BCrypt("123456") → $2a$10$N9qo8uLOickgx2ZMRZoMy... （每次不同，无法彩虹表攻击）
     *
     * BCrypt 密文的结构：
     *   $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 *    \__/ \_/ \_____________________/ \_____________________________/
 *     |    |            |                           |
 *   算法  cost        盐值（22字符）              哈希值（31字符）
 *   版本  强度
 *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCryptPasswordEncoder 默认 cost factor = 10（即 2^10 = 1024 轮哈希）
        // 数值越大越安全，但也越慢。通常 10-12 是安全性和性能的平衡点
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器 —— Spring Security 的认证入口
     *
     * 什么是 AuthenticationManager？
     *   AuthenticationManager 是 Spring Security 认证的核心接口。
     *   当我们调用 authenticate() 方法时，它会：
     *     1. 调用 UserDetailsService.loadUserByUsername() 获取用户信息
     *     2. 使用 PasswordEncoder 比对密码
     *     3. 认证成功返回 Authentication 对象，失败抛出异常
     *
     *   使用场景（在 AuthService 中）：
     *     Authentication authentication = authenticationManager.authenticate(
     *         new UsernamePasswordAuthenticationToken(username, password)
     *     );
     *
     * 为什么不从 AuthenticationConfiguration 获取？
     *   AuthenticationConfiguration 是 Spring Security 自动配置的类，
     *   它已经配置好了 UserDetailsService 和 PasswordEncoder，
     *   我们只需要通过它获取 AuthenticationManager 即可。
     *
     * @param config Spring Security 自动提供的认证配置
     * @return AuthenticationManager 实例
     * @throws Exception 获取失败时抛出
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
