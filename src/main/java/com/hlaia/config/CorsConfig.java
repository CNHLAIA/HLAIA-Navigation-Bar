package com.hlaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 【CORS 跨域配置类】—— 允许前端（浏览器）跨域访问后端 API
 *
 * ============================================================
 * 什么是 CORS（Cross-Origin Resource Sharing，跨域资源共享）？
 * ============================================================
 *
 * 什么是"跨域"？
 *   当前端和后端部署在不同的域名或端口时，就存在"跨域"问题。
 *   例如：
 *     前端地址：http://localhost:5173  （Vue 开发服务器）
 *     后端地址：http://localhost:8080  （Spring Boot 服务）
 *     ↓
 *     端口不同（5173 vs 8080）→ 浏览器认为这是"跨域"请求
 *
 * 为什么浏览器要限制跨域？
 *   这是浏览器的**同源策略**（Same-Origin Policy），是一种安全机制。
 *
 *   想象一个场景：
 *     1. 你登录了银行网站 bank.com，浏览器保存了你的登录 Cookie
 *     2. 你又访问了恶意网站 evil.com
 *     3. evil.com 的 JavaScript 可以向 bank.com 发送请求
 *     4. 如果没有同源策略，浏览器会自动携带 bank.com 的 Cookie
 *     5. 恶意网站就冒充你操作了银行账户！
 *
 *   同源策略防止了这种情况：evl.com 的 JS 不能访问 bank.com 的响应内容。
 *
 * 为什么我们需要跨域？
 *   开发环境中，前端在 localhost:5173，后端在 localhost:8080，
 *   前端需要调用后端 API → 浏览器会拦截这种跨域请求。
 *
 *   CORS 就是告诉浏览器："我（后端）允许这些来源访问我的 API"。
 *   相当于门卫拿到了一份"允许进入的白名单"。
 *
 * ============================================================
 * CORS 的工作流程（预检请求）
 * ============================================================
 *
 *   对于非简单请求（如带 Authorization 头的请求），浏览器会先发一个
 *   OPTIONS 请求（称为"预检请求"），询问服务器是否允许跨域：
 *
 *   浏览器 → OPTIONS /api/auth/login
 *            Origin: http://localhost:5173
 *            Access-Control-Request-Method: POST
 *            Access-Control-Request-Headers: Authorization, Content-Type
 *
 *   服务器 → 200 OK
 *            Access-Control-Allow-Origin: http://localhost:5173
 *            Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
 *            Access-Control-Allow-Headers: Authorization, Content-Type
 *            Access-Control-Max-Age: 3600
 *
 *   浏览器收到允许后，才发送真正的请求。
 *
 * @Configuration 注解：标记这是一个 Spring 配置类
 */
@Configuration
public class CorsConfig {

    /**
     * 创建 CORS 配置源 —— 定义跨域规则
     *
     * @return CorsConfigurationSource 供 Spring Security 使用
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // CorsConfiguration 封装了所有 CORS 相关的配置
        CorsConfiguration configuration = new CorsConfiguration();

        // ============================================================
        // allowedOriginPatterns：允许的来源（Origin）
        // ============================================================
        // "*" 表示允许所有来源访问（开发阶段使用）
        //
        // 生产环境中应该改为具体的域名，例如：
        //   configuration.setAllowedOriginPatterns(
        //       List.of("https://www.myapp.com", "https://admin.myapp.com")
        //   );
        //
        // 为什么用 allowedOriginPatterns 而不是 allowedOrigins？
        //   allowedOrigins("*") + allowCredentials(true) 在 Spring 中会报错，
        //   因为允许携带凭证（Cookie）时不能使用通配符。
        //   allowedOriginPatterns("*") 更灵活，支持通配符模式。
        configuration.setAllowedOriginPatterns(List.of("*"));

        // ============================================================
        // allowedMethods：允许的 HTTP 方法
        // ============================================================
        // GET     → 查询数据（获取书签列表、文件夹列表等）
        // POST    → 创建数据（新增书签、注册、登录等）
        // PUT     → 更新数据（修改书签、修改用户信息等）
        // DELETE  → 删除数据（删除书签、删除文件夹等）
        // OPTIONS → 预检请求（浏览器自动发送的 CORS 检查请求）
        //
        // 如果不列出 OPTIONS，浏览器发预检请求时会被拒绝，导致跨域失败
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // ============================================================
        // allowedHeaders：允许的请求头
        // ============================================================
        // "*" 表示允许所有请求头
        //
        // 主要是为了允许以下头：
        //   Authorization：携带 JWT Token（Bearer xxx）
        //   Content-Type：指定请求体格式（application/json）
        configuration.setAllowedHeaders(List.of("*"));

        // ============================================================
        // allowCredentials：是否允许携带凭证（Cookie 等）
        // ============================================================
        // true 表示允许前端在跨域请求中携带 Cookie 等凭证
        // 虽然我们主要用 JWT Token（存在请求头中），但开启这个选项
        // 可以提供更好的兼容性（某些场景可能需要 Cookie）
        configuration.setAllowCredentials(true);

        // ============================================================
        // maxAge：预检请求的缓存时间（秒）
        // ============================================================
        // 浏览器在 3600 秒（1 小时）内不会对同一请求重复发送 OPTIONS 预检请求
        // 这样可以减少 OPTIONS 请求的数量，提升性能
        configuration.setMaxAge(3600L);

        // ============================================================
        // 将 CORS 配置应用到所有路径
        // ============================================================
        // UrlBasedCorsConfigurationSource：基于 URL 路径的 CORS 配置源
        // "/**" 表示所有路径都使用这个 CORS 配置
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
