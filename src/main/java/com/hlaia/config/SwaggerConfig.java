package com.hlaia.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【Knife4j / Swagger API 文档配置类】—— 自动生成和展示项目的 API 文档
 *
 * ============================================================
 * 一、为什么需要 API 文档？
 * ============================================================
 *   在前后端分离的开发模式下，后端开发者需要告诉前端开发者：
 *     - 有哪些接口可以使用？（URL 路径）
 *     - 每个接口接受什么参数？（请求体 / 查询参数）
 *     - 每个接口返回什么数据？（响应格式）
 *     - 需要什么权限？（是否需要 Token）
 *
 *   如果没有 API 文档，前后端只能靠口头沟通，效率极低且容易出错。
 *
 *   自动生成 API 文档的好处：
 *     1. **前端开发者可以查看所有接口定义**（路径、参数、返回值）
 *     2. **可以在线测试接口**：在网页上直接发送请求，不需要安装 Postman
 *     3. **接口变更有迹可循**：每次修改接口，文档会自动更新，避免文档与代码不同步
 *
 * ============================================================
 * 二、Swagger / OpenAPI / Knife4j 三者的关系
 * ============================================================
 *   可以用一个类比来理解：
 *
 *   OpenAPI  = 规范 / 标准（类似于"USB 接口标准"）
 *     OpenAPI 是一个规范（以前叫 Swagger 规范），它定义了 API 文档应该怎么写：
 *     - 用什么格式描述接口（JSON / YAML）
 *     - 接口有哪些属性（路径、方法、参数、响应等）
 *     - 所有人只要遵循这个规范，文档就能互相通用
 *
 *   Swagger  = 实现规范的工具集（类似于"生产 USB 数据线的工厂"）
 *     Swagger 是实现 OpenAPI 规范的一系列工具：
 *     - Swagger Editor：在线编辑 API 文档
 *     - Swagger UI：将文档渲染成可交互的网页
 *     - Swagger Codegen：根据文档自动生成代码
 *
 *   Knife4j  = 增强版的 Swagger UI（类似于"更漂亮的 USB 数据线"）
 *     Knife4j 是中国开发者 xiaoymin 开源的增强版 Swagger UI：
 *     - 界面更美观、更符合中国开发者的使用习惯
 *     - 支持离线文档导出（Markdown、Word）
 *     - 支持接口搜索、全局参数配置等增强功能
 *     - 访问地址是 /doc.html（原生 Swagger UI 是 /swagger-ui.html）
 *
 *   本项目使用的是：knife4j-openapi3-jakarta-spring-boot-starter
 *     - openapi3：基于 OpenAPI 3.0 规范
 *     - jakarta：适配 Jakarta EE（Spring Boot 3.x 的新命名空间）
 *     - spring-boot-starter：Spring Boot 自动配置，几乎零配置即可使用
 *
 * ============================================================
 * 三、访问地址
 * ============================================================
 *   启动项目后，在浏览器访问以下地址查看 API 文档：
 *
 *     http://localhost:8080/doc.html
 *
 *   这个页面会自动收集所有 Controller 上的注解信息：
 *     - @Tag：Controller 类上的分组标签（如 AuthController 上的 "Authentication"）
 *     - @Operation：方法上的接口描述（如 "Register a new user"）
 *     - @Schema：DTO 类上字段的描述
 *
 *   Knife4j 会读取这些注解，自动生成美观的 API 文档页面。
 *
 * ============================================================
 * 四、与 Controller 注解的关系
 * ============================================================
 *   本配置类（SwaggerConfig）只负责"全局设置"（标题、安全认证等）。
 *   每个接口的具体文档信息是在 Controller 中通过注解定义的：
 *
 *   在 AuthController 中：
 *     @Tag(name = "Authentication", description = "User authentication APIs")
 *       → 在文档页面中创建 "Authentication" 分组
 *
 *     @Operation(summary = "Register a new user")
 *       → 在文档中显示该接口的摘要说明
 *
 *   在 FolderController 中：
 *     @Tag(name = "Folders", description = "Folder management APIs")
 *       → 在文档页面中创建 "Folders" 分组
 *
 *   这些注解会被 Knife4j 自动扫描并收集到文档中，不需要额外配置。
 *
 *   类比：
 *     SwaggerConfig = 文档的"封面"（标题、版本、全局规则）
 *     Controller 注解 = 文档的"正文"（每个接口的详细说明）
 *
 * ============================================================
 * 五、SecurityConfig 中已放行的路径
 * ============================================================
 *   注意：SecurityConfig 中已经将 API 文档相关路径设为公开访问：
 *     "/doc.html"           → Knife4j 文档页面
 *     "/webjars/**"          → 前端静态资源（JS、CSS）
 *     "/v3/api-docs/**"      → OpenAPI 3.0 规范的 JSON/YAML 数据
 *     "/swagger-resources/**" → Swagger 资源配置
 *
 *   如果不放行，访问 /doc.html 会被 Spring Security 拦截返回 401 未授权。
 */
@Configuration   // 告诉 Spring：这是一个配置类，需要读取其中的 Bean 定义
public class SwaggerConfig {

    /**
     * 配置 OpenAPI 全局信息
     *
     * 这个方法返回的 OpenAPI 对象会被 Knife4j 读取，用于生成文档页面的：
     *   - 顶部标题和描述
     *   - "Authorize" 按钮（JWT 认证输入框）
     *   - 全局安全策略
     *
     * ============================================================
     * Info：API 文档的基本信息
     * ============================================================
     *   .title()        → 文档标题，显示在页面顶部
     *   .description()  → 文档描述，显示在标题下方
     *   .version()      → API 版本号，方便追踪接口变更
     *
     *   这些信息会显示在 /doc.html 页面的顶部区域。
     *
     * ============================================================
     * SecurityRequirement + SecurityScheme：配置 JWT 认证
     * ============================================================
     *   为什么需要配置安全认证？
     *     我们的接口大部分需要 JWT Token 才能访问。
     *     如果不配置认证，在 Knife4j 页面上测试接口时，
     *     每次都要手动在请求头中添加 Authorization: Bearer xxx，非常麻烦。
     *
     *   配置后的效果：
     *     1. Knife4j 页面顶部会出现一个 "Authorize"（授权）按钮
     *     2. 点击按钮，弹出输入框，输入 JWT Token
     *     3. 输入后，后续所有请求会自动在请求头中添加：
     *        Authorization: Bearer 你输入的Token
     *     4. 不需要每次手动添加 Authorization 头了！
     *
     *   SecurityRequirement：
     *     声明"这个 API 全局需要某种认证方式"。
     *     .addList("Bearer") 表示使用名为 "Bearer" 的安全方案。
     *
     *   SecurityScheme：
     *     定义安全方案的具体细节（怎么认证）。
     *     .type(HTTP)          → 使用 HTTP 认证方式
     *     .scheme("bearer")    → 使用 Bearer 认证方案
     *     .bearerFormat("JWT") → Bearer Token 的格式是 JWT
     *
     *   什么是 Bearer 认证？
     *     Bearer 是 OAuth 2.0 / JWT 的标准认证方式：
     *     - Bearer 意思是"持有者"——谁持有这个 Token，谁就被认为是合法用户
     *     - 请求格式：Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *     - 就像持有电影票就能进场看电影，不需要证明你是谁
     *
     * @return 配置好的 OpenAPI 对象
     */
    @Bean   // 把方法的返回值注册为 Spring Bean，Knife4j 会自动发现并使用它
    public OpenAPI openAPI() {
        return new OpenAPI()
                // ============================================================
                // 配置 API 文档的基本信息（标题、描述、版本号）
                // ============================================================
                .info(new Info()
                        .title("HLAIANavigationBar API")              // 文档标题
                        .description("Navigation bar management system API documentation")  // 文档描述
                        .version("1.0.0"))                            // API 版本号

                // ============================================================
                // 配置全局安全要求：声明这个 API 需要 Bearer Token 认证
                // ============================================================
                // addSecurityItem 会告诉 Knife4j：这个 API 需要认证。
                // 页面上会出现 "Authorize" 按钮，输入 Token 后所有请求自动带上 Authorization 头。
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))

                // ============================================================
                // 定义安全方案的详细信息（名为 "Bearer" 的方案）
                // ============================================================
                // schemaRequirement 定义了一个安全方案，名称必须和上面的 "Bearer" 一致。
                // 这里配置的是：HTTP Bearer 认证，Token 格式为 JWT。
                .schemaRequirement("Bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)    // 认证类型：HTTP（还有 API_KEY、OAUTH2 等类型）
                        .scheme("bearer")                   // HTTP 认证方案：Bearer
                        .bearerFormat("JWT"));              // Token 格式：JWT（JSON Web Token）
    }
}
