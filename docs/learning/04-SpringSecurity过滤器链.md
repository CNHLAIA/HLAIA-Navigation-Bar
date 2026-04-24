# 阶段 04：Spring Security 过滤器链 —— 从"请求进门"到"确认身份"的全过程

> 学习目标：理解 Spring Security 如何通过过滤器链保护你的 API，以及 JWT Token 是如何被解析并注入到 Controller 中的。

---

## 目录

1. [前置知识：Servlet Filter 与责任链模式](#1-前置知识servlet-filter-与责任链模式)
2. [概念讲解：Spring Security 过滤器链](#2-概念讲解spring-security-过滤器链)
3. [代码逐行解读](#3-代码逐行解读)
4. [关键 Java 语法点](#4-关键-java-语法点)
5. [动手练习建议](#5-动手练习建议)

---

## 1. 前置知识：Servlet Filter 与责任链模式

在进入 Spring Security 之前，我们需要先搞懂两个基础概念。它们是理解整个安全框架的地基。

### 1.1 什么是 Servlet？

你可能已经知道：当你用浏览器访问一个网站时，浏览器会发送 HTTP 请求，服务器会返回 HTTP 响应。那么，服务器内部是谁在"接收请求"和"返回响应"呢？

在 Java 的世界里，这个角色是 **Servlet**（服务器小程序）。你可以把 Servlet 理解为一个"接待员"：

```
浏览器发来请求 → Servlet 接收 → Servlet 处理 → Servlet 返回响应 → 浏览器收到响应
```

Spring Boot 底层用的 Servlet 容器叫 **Tomcat**（内嵌在 Spring Boot 中，你不需要单独安装）。当一个 HTTP 请求到达 Tomcat 时，Tomcat 会创建两个对象：

- `HttpServletRequest` —— 封装了请求的所有信息（URL、请求头、请求体等）
- `HttpServletResponse` —— 用来写入响应的内容（状态码、响应头、响应体等）

Spring MVC 的 `@RestController` 中的方法，本质上就是一个 Servlet 处理逻辑的高级封装。

### 1.2 什么是 Filter（过滤器）？

现在想象一个场景：你是一个写字楼的租户，每天上班需要经过以下步骤：

```
大门口（检查是否带工牌）→ 安检机（检查是否携带危险物品）→ 前台登记（签到）→ 电梯 → 办公室
```

这里的"大门口检查"、"安检机"、"前台登记"就是 **Filter（过滤器）**。每一个 Filter 只负责一项检查，不负责具体的工作（那是"办公室"里的事）。

在 Java Web 中，**Filter 是在请求到达 Servlet（Controller）之前执行的一段代码**。它可以在请求到达 Controller 之前做预处理，也可以在 Controller 返回响应之后做后处理。

```
HTTP 请求 → Filter A → Filter B → Filter C → Controller（Servlet）→ 响应
```

Filter 的核心接口是 `jakarta.servlet.Filter`，它只有一个关键方法：

```java
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    // 请求到达 Controller 之前的逻辑（前置处理）
    // ...

    // 调用 chain.doFilter() 把请求传给下一个 Filter（或最终的 Controller）
    chain.doFilter(request, response);

    // Controller 处理完之后的逻辑（后置处理）
    // ...
}
```

**关键点**：`chain.doFilter(request, response)` 这行代码的意思是"我已经处理完了，请把请求传给下一个环节"。如果不调用这行代码，请求就会在这里"卡住"，永远不会到达 Controller。

### 1.3 责任链模式（Chain of Responsibility）

你可能会问：为什么要用"一串 Filter"而不是"一个超级 Filter 把所有事都干了"？

这涉及一个经典的设计模式 —— **责任链模式**。

想象一个工厂的质检流水线：

```
原材料 → 外观检查员 → 尺寸检查员 → 重量检查员 → 包装 → 出厂
```

每个检查员只负责自己那一项。如果发现不合格，直接打回去。如果合格，传给下一个检查员。

这样做的好处：

1. **职责单一**：每个 Filter 只做一件事，代码简单清晰
2. **灵活组合**：需要新功能？加一个 Filter 就行。不需要了？去掉即可
3. **顺序可控**：可以调整 Filter 的执行顺序（比如"认证"必须在"授权"之前）

Spring Security 正是基于这种模式构建的。

---

## 2. 概念讲解：Spring Security 过滤器链

### 2.1 Spring Security 是什么？

Spring Security 是 Spring 官方提供的安全框架。它主要解决两个问题：

| 概念 | 英文 | 作用 | 类比 |
|------|------|------|------|
| 认证 | Authentication | 你是谁？ | 门口刷门禁卡，验证你是业主 |
| 授权 | Authorization | 你能做什么？ | 不同门禁卡能进不同的区域 |

Spring Security 的核心思路是：**在请求到达 Controller 之前，先经过一系列安全过滤器**。这些过滤器会检查：

- 你有没有登录？（认证）
- 你有没有权限访问这个 URL？（授权）
- 请求是否合法？（CSRF、CORS 等）

### 2.2 Spring Security 过滤器链长什么样？

Spring Security 内置了很多过滤器，它们按固定顺序排列。以下是我们项目中涉及的主要过滤器（简化版）：

```
HTTP 请求
  ↓
[1] CorsFilter              ← 处理跨域请求
  ↓
[2] CsrfFilter              ← 防止 CSRF 攻击（我们禁用了）
  ↓
[3] ... 其他内置过滤器 ...
  ↓
[4] JwtAuthFilter           ← 我们自定义的！验证 JWT Token（插在 [5] 之前）
  ↓
[5] UsernamePasswordAuthenticationFilter  ← 处理表单登录（我们不用）
  ↓
[6] ... 更多过滤器 ...
  ↓
[7] FilterSecurityInterceptor  ← 最终的权限检查（检查 URL 访问权限）
  ↓
  通过 → Controller
  拒绝 → 返回 401 或 403
```

你不需要记住所有过滤器的名字和顺序。你只需要知道：

- **过滤器是有顺序的**，排在前面先执行
- **我们的 JwtAuthFilter 被插在了第 [5] 个之前**，这意味着它在权限检查之前运行
- **FilterSecurityInterceptor 是最终"把关人"**，它根据 SecurityConfig 中定义的规则决定放行还是拒绝

### 2.3 SecurityContext：Spring Security 的"临时储物柜"

当 JwtAuthFilter 成功解析了 JWT Token 后，用户的身份信息需要被"存起来"，这样后续的过滤器和 Controller 都能知道"当前用户是谁"。

这个"存起来"的地方就是 **SecurityContext**。

```java
// 存入用户信息
SecurityContextHolder.getContext().setAuthentication(authentication);

// 后续任何地方都可以取出来
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```

**SecurityContextHolder 使用 ThreadLocal 实现**。这是什么意思呢？

想象一家餐厅有很多服务员（线程），每个服务员手上都有一个托盘（SecurityContext）。客人 A 的服务员 A 把客人 A 的点菜单放在自己的托盘上，客人 B 的服务员 B 把客人 B 的点菜单放在自己的托盘上。他们互不干扰。

```
线程 1（处理用户 A 的请求）→ SecurityContext 中存的是用户 A 的信息
线程 2（处理用户 B 的请求）→ SecurityContext 中存的是用户 B 的信息
线程 3（处理用户 C 的请求）→ SecurityContext 中存的是用户 C 的信息
```

这就是 ThreadLocal 的魔力 —— 每个线程都有自己独立的变量副本，不会互相串数据。

### 2.4 SecurityConfig：告诉 Spring "哪些路径要认证、哪些不用"

有了过滤器和 SecurityContext，还需要一个"规则手册"来告诉 Spring Security：

- `/api/auth/login`、`/api/auth/register` —— 这些是登录注册用的，不需要认证
- `/doc.html`、`/webjars/**` —— API 文档页面，也不需要认证
- `/api/admin/**` —— 只有 ADMIN 角色才能访问
- 其他所有路径 —— 必须登录才能访问

这个"规则手册"就是 **SecurityConfig** 类。我们在里面配置了一个 `SecurityFilterChain` Bean，定义了所有的安全规则。

### 2.5 整体流程：从请求到 Controller

把以上所有概念串起来，完整的认证流程如下：

```
浏览器发送请求（携带 JWT Token）
  ↓
Tomcat 接收请求
  ↓
Spring Security 过滤器链开始工作
  ↓
CorsFilter：处理跨域（通过）
  ↓
CsrfFilter：CSRF 检查（已禁用，跳过）
  ↓
JwtAuthFilter（我们自定义的）：
  ① 从请求头提取 Token
  ② 验证 Token 签名和过期时间
  ③ 检查 Token 是否在 Redis 黑名单中
  ④ 提取用户信息（userId, role）
  ⑤ 创建认证对象，存入 SecurityContext
  ↓
FilterSecurityInterceptor：
  检查当前 URL 的访问权限
  - 如果是 /api/auth/** → 放行（不需要认证）
  - 如果是 /api/admin/** → 检查是否有 ADMIN 角色
  - 其他 → 检查是否已认证（SecurityContext 中是否有认证信息）
  ↓
通过 → 到达 Controller
Controller 中通过 @AuthenticationPrincipal Long userId 获取用户 ID
  ↓
拒绝 → 返回 401（未认证）或 403（无权限）
```

---

## 3. 代码逐行解读

现在让我们结合项目的真实代码，一步步搞懂每个文件。

### 3.1 JwtAuthFilter：JWT 认证过滤器

> 文件路径：`src/main/java/com/hlaia/security/JwtAuthFilter.java`

这是我们自定义的过滤器，也是整个认证流程的"核心引擎"。

#### 3.1.1 类声明：继承 OncePerRequestFilter

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
```

逐个解释：

- **`extends OncePerRequestFilter`**：继承 Spring 提供的抽象类。它保证同一个请求只经过这个过滤器一次（避免请求转发时重复执行）。我们只需要实现它的 `doFilterInternal` 方法。
- **`@Component`**：把这个类注册为 Spring Bean，Spring 会自动创建它的实例。
- **`@RequiredArgsConstructor`**：Lombok 注解，自动为所有 `final` 字段生成构造函数，实现依赖注入。
- **`@Slf4j`**：Lombok 注解，自动生成 `log` 对象，用于记录日志。

#### 3.1.2 依赖注入

```java
private final JwtTokenProvider jwtTokenProvider;
private final StringRedisTemplate stringRedisTemplate;
```

- `jwtTokenProvider`：JWT 工具类，负责验证 Token、提取用户信息
- `stringRedisTemplate`：Redis 操作工具，用于检查 Token 黑名单

#### 3.1.3 核心方法 doFilterInternal（完整流程）

这个方法是整个过滤器的"主函数"，每个 HTTP 请求都会经过它。

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {
```

参数说明：
- `request`：当前 HTTP 请求（可以获取请求头、参数等）
- `response`：当前 HTTP 响应（可以写入错误响应等）
- `filterChain`：过滤器链，调用它的 `doFilter` 方法表示"继续往下传"

**第 1 步：从请求头提取 Token**

```java
String token = resolveToken(request);
```

调用了 `resolveToken` 私有方法，后面会详细讲。它从 `Authorization` 请求头中提取 Bearer Token。

**第 2 步：验证 Token 有效性**

```java
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
```

这里做了两个检查：
- `StringUtils.hasText(token)`：Token 不为 null、不为空字符串、不全为空白字符
- `jwtTokenProvider.validateToken(token)`：验证 Token 的签名是否正确、是否已过期

**第 3 步：检查 Redis 黑名单**

```java
String blacklistKey = "jwt:blacklist:" + token;
Boolean isBlacklisted = stringRedisTemplate.hasKey(blacklistKey);

if (Boolean.TRUE.equals(isBlacklisted)) {
    log.warn("Token 已被加入黑名单（用户已登出）");
} else {
    // 继续认证...
}
```

为什么要检查黑名单？因为 JWT Token 本身是无法"撤销"的 —— 一旦签发，在过期之前都有效。如果用户主动登出，我们就把 Token 存入 Redis（key = `jwt:blacklist:{token}`），这样即使 Token 没过期，也不能再用了。

注意 `Boolean.TRUE.equals(isBlacklisted)` 这种写法。为什么不直接写 `if (isBlacklisted)`？因为 `stringRedisTemplate.hasKey()` 返回的是 `Boolean`（包装类），可能为 `null`。如果直接写 `if (isBlacklisted)` 且 `isBlacklisted` 为 `null`，会抛出 `NullPointerException`。用 `Boolean.TRUE.equals()` 可以安全地处理 `null`。

**第 4 步：提取用户信息，创建认证对象**

```java
Long userId = jwtTokenProvider.getUserIdFromToken(token);
String role = jwtTokenProvider.getRoleFromToken(token);

UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userId,                          // principal（主体）
                null,                            // credentials（凭证）
                Collections.singletonList(        // authorities（权限列表）
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
```

这里创建了一个 Spring Security 的"认证令牌"。三个参数的含义：

| 参数 | 含义 | 我们传的值 | 为什么 |
|------|------|-----------|--------|
| principal | 主体（"谁"） | userId | 轻量级，避免每次查数据库 |
| credentials | 凭证（"证明"） | null | 已经通过 JWT 验证了，不需要密码 |
| authorities | 权限列表 | `ROLE_ADMIN` 或 `ROLE_USER` | Spring Security 的角色命名约定 |

`Collections.singletonList()` 创建一个只包含一个元素的不可变列表。因为我们的用户只有一种角色，所以列表中只有一个元素。

`SimpleGrantedAuthority` 是 Spring Security 对"权限"的封装。"ROLE_" 前缀是 Spring Security 的约定 —— 当你在 Controller 上写 `@PreAuthorize("hasRole('ADMIN')")` 时，Spring Security 会自动加上 "ROLE_" 前缀去匹配 "ROLE_ADMIN"。

**第 5 步：将认证信息存入 SecurityContext**

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

这一步是关键！设置之后：
- Spring Security 就知道"当前用户已认证"
- 后续的过滤器（权限检查）不会再拦截这个请求
- Controller 中可以通过 `@AuthenticationPrincipal` 获取用户信息

**第 6 步：继续过滤器链**

```java
filterChain.doFilter(request, response);
```

**无论 Token 是否有效，都要调用这行代码。** 如果没有有效 Token，SecurityContext 中就没有认证信息，后续的 FilterSecurityInterceptor 会自动返回 401 Unauthorized。

#### 3.1.4 resolveToken 方法：提取 Bearer Token

```java
private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");

    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }

    return null;
}
```

HTTP 请求头中，JWT Token 的标准格式是：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.xxx
```

- `request.getHeader("Authorization")` 获取整个请求头的值
- `startsWith("Bearer ")` 检查是否有 Bearer 前缀（注意 Bearer 后面有空格）
- `substring(7)` 截取第 7 个字符之后的部分（"Bearer " 正好是 7 个字符）

### 3.2 SecurityConfig：安全配置

> 文件路径：`src/main/java/com/hlaia/config/SecurityConfig.java`

这个类是 Spring Security 的"规则手册"，定义了所有的安全策略。

#### 3.2.1 类声明和注解

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
```

- **`@Configuration`**：告诉 Spring 这是一个配置类，里面定义了各种 Bean
- **`@EnableWebSecurity`**：Spring Security 的总开关，不加它安全配置不会生效
- **`@EnableMethodSecurity`**：启用方法级别的权限控制（允许使用 `@PreAuthorize` 等注解）
- **`@RequiredArgsConstructor`**：为 `final` 字段生成构造函数

#### 3.2.2 filterChain 方法：配置安全过滤器链

这是最核心的方法，返回一个 `SecurityFilterChain` Bean。

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
```

`HttpSecurity` 是 Spring Security 提供的"配置建造者"。你通过链式调用来告诉它各种规则。

**禁用 CSRF**

```java
.csrf(AbstractHttpConfigurer::disable)
```

CSRF（跨站请求伪造）攻击是利用浏览器自动携带 Cookie 来伪造请求。但我们使用 JWT（存在请求头中，不在 Cookie 中），浏览器不会自动携带 JWT，所以 CSRF 攻击对我们无效，可以安全地禁用。

**会话管理：无状态**

```java
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

传统 Web 应用使用 Session（会话）来记住用户。但 JWT 本身就包含了认证信息，不需要服务器存储任何状态。设置为 `STATELESS` 告诉 Spring Security："别创建 Session，每个请求自己带 Token 来"。

**URL 访问权限配置**

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers(
        "/doc.html",
        "/webjars/**",
        "/v3/api-docs/**",
        "/swagger-resources/**"
    ).permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
```

规则的匹配顺序很重要 —— **从上到下，第一个匹配的规则生效**：

| 路径模式 | 规则 | 含义 |
|---------|------|------|
| `/api/auth/**` | `permitAll()` | 登录注册接口，任何人都能访问 |
| `/doc.html` 等 | `permitAll()` | API 文档页面，任何人都能访问 |
| `/api/admin/**` | `hasRole("ADMIN")` | 只有 ADMIN 角色能访问 |
| 其他所有路径 | `authenticated()` | 必须登录才能访问 |

注意 `**` 是 Ant 风格的通配符：
- `*` 匹配一层路径（如 `/api/auth/*` 匹配 `/api/auth/login`）
- `**` 匹配任意多层路径（如 `/api/auth/**` 匹配 `/api/auth/login`、`/api/auth/a/b/c` 等）

**注册 JwtAuthFilter**

```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

`addFilterBefore(A, B)` 的意思是"在过滤器 B 之前插入过滤器 A"。我们把 JwtAuthFilter 放在 `UsernamePasswordAuthenticationFilter`（Spring Security 默认的表单登录过滤器）之前。

为什么要放在它之前？因为 Spring Security 的过滤器有严格的顺序要求。认证过滤器必须在权限检查过滤器之前执行，这样 SecurityContext 中才有认证信息供后续检查。

#### 3.2.3 passwordEncoder 方法：密码加密器

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

这里创建了一个 BCrypt 密码加密器的 Bean。在 `AuthService` 中，注册时用它加密密码，登录时用它比对密码。

BCrypt 的核心特点：每次加密同样的密码，结果都不同（因为自动添加了随机盐值），这样即使数据库泄露，攻击者也无法通过"相同的密文"来判断哪些用户使用了相同的密码。

#### 3.2.4 authenticationManager 方法：认证管理器

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
        throws Exception {
    return config.getAuthenticationManager();
}
```

`AuthenticationManager` 是 Spring Security 认证的核心入口。它做的事情是：

1. 调用 `UserDetailsService.loadUserByUsername()` 获取用户信息
2. 用 `PasswordEncoder` 比对密码
3. 认证成功返回 `Authentication` 对象，失败抛出异常

在我们项目中，`AuthenticationManager` 主要用在 `UserDetailsServiceImpl` 相关的认证流程中（比如如果以后要支持表单登录的话）。当前的 `AuthService.login()` 方法采用手动查数据库 + 手动比对密码的方式，没有使用 `AuthenticationManager`。

### 3.3 CorsConfig：跨域配置

> 文件路径：`src/main/java/com/hlaia/config/CorsConfig.java`

这个配置类和 SecurityConfig 是"搭档"关系，专门处理跨域问题。

#### 为什么需要跨域配置？

在开发环境中：
- 前端运行在 `http://localhost:5173`（Vue 开发服务器）
- 后端运行在 `http://localhost:8080`（Spring Boot）

端口不同，浏览器认为这是"跨域"请求，默认会阻止。CORS 配置就是告诉浏览器"我允许跨域"。

#### 配置内容解读

```java
configuration.setAllowedOriginPatterns(List.of("*"));           // 允许所有来源
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // 允许的 HTTP 方法
configuration.setAllowedHeaders(List.of("*"));                  // 允许所有请求头
configuration.setAllowCredentials(true);                        // 允许携带凭证
configuration.setMaxAge(3600L);                                 // 预检请求缓存 1 小时
```

其中 `OPTIONS` 方法是浏览器的"预检请求"—— 在发送真正的跨域请求之前，浏览器会先发一个 OPTIONS 请求询问服务器"我能不能跨域"。服务器回复"可以"之后，浏览器才发送真正的请求。

### 3.4 @AuthenticationPrincipal：在 Controller 中获取当前用户

> 涉及文件：`FolderController.java`、`ExtensionController.java`

当 JwtAuthFilter 成功解析 Token 并将认证信息存入 SecurityContext 后，Controller 中就可以通过 `@AuthenticationPrincipal` 注解直接获取当前用户的 ID。

**在 FolderController 中的用法：**

```java
@GetMapping("/tree")
public Result<List<FolderTreeResponse>> getTree(@AuthenticationPrincipal Long userId) {
    return Result.success(folderService.getFolderTree(userId));
}
```

**在 ExtensionController 中的用法：**

```java
@GetMapping("/folders/tree")
public Result<List<FolderTreeResponse>> getFolderTree(@AuthenticationPrincipal Long userId) {
    return Result.success(folderService.getFolderTree(userId));
}
```

#### 它是怎么工作的？

回顾一下 JwtAuthFilter 中的这行代码：

```java
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userId,     // 第一个参数：principal
                null,
                authorities
        );
```

`@AuthenticationPrincipal` 注解的作用就是从 SecurityContext 中取出当前认证对象的 `principal` 字段。因为我们在 `principal` 中放的是 `Long` 类型的 `userId`，所以 Controller 方法参数直接写 `Long userId` 就能接收到。

整个数据流：

```
JwtAuthFilter 设置: SecurityContext.authentication.principal = userId(Long)
        ↓
Spring MVC 发现方法参数有 @AuthenticationPrincipal 注解
        ↓
从 SecurityContext 中取出 authentication.getPrincipal()
        ↓
转换为 Long 类型，注入到方法参数 userId 中
```

### 3.5 AuthController：公开路径的 Controller

> 文件路径：`src/main/java/com/hlaia/controller/AuthController.java`

AuthController 的路径是 `/api/auth/**`，在 SecurityConfig 中配置为 `permitAll()` —— 不需要认证就能访问。

这很合理：登录和注册接口本身就是用来"获取身份"的，如果要求先登录才能登录，那就成了"鸡生蛋蛋生鸡"的问题了。

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // POST /api/auth/register  → 注册
    // POST /api/auth/login     → 登录
    // POST /api/auth/logout    → 登出（需要 Token，但路径本身是公开的）
    // POST /api/auth/refresh   → 刷新 Token
}
```

注意：虽然 `/api/auth/**` 是公开路径，但 `logout` 接口仍然需要 Token（通过 `@RequestHeader("Authorization")` 手动获取）。SecurityConfig 不会拦截这个路径，但 Controller 方法本身会读取请求头。这是一种灵活的设计 —— SecurityConfig 负责"URL 级别"的放行，具体的业务逻辑由 Controller 自己处理。

---

## 4. 关键 Java 语法点

### 4.1 继承（extends）

```java
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) { ... }
}
```

**继承**是面向对象编程的核心概念之一。子类（JwtAuthFilter）继承父类（OncePerRequestFilter），自动获得父类的所有功能，只需要重写（Override）自己关心的方法。

类比：OncePerRequestFilter 是"通用安检门模板"，定义了安检门的通用流程。JwtAuthFilter 是"JWT 专用安检门"，只需要填写"如何检查 JWT Token"这部分逻辑。

### 4.2 方法重写（@Override）

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {
```

`@Override` 注解告诉编译器："我是要重写父类（或实现接口）的方法，请帮我检查方法签名是否正确。"

如果方法名拼错了（比如写成 `doFilter` 而不是 `doFilterInternal`），编译器会报错。这个注解是可选的，但强烈建议加上 —— 它能帮你避免因为拼写错误导致的隐蔽 Bug。

### 4.3 接口实现

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> ...)
        .authorizeHttpRequests(auth -> ...)
        .addFilterBefore(...);
    return http.build();
}
```

`SecurityFilterChain` 是一个接口。我们通过 `http.build()` 返回了它的一个实现对象。Spring Security 内部会自动使用这个实现对象来构建过滤器链。

`@Bean` 注解告诉 Spring："这个方法返回一个对象，请把它注册到 Spring 容器中，让其他地方可以注入使用。"

### 4.4 Optional 与 null 安全处理

在 JwtAuthFilter 中，我们没有使用 `Optional`，但使用了一种类似的 null 安全处理技巧：

```java
Boolean isBlacklisted = stringRedisTemplate.hasKey(blacklistKey);

// 错误写法（可能 NullPointerException）：
if (isBlacklisted) { ... }

// 正确写法（安全处理 null）：
if (Boolean.TRUE.equals(isBlacklisted)) { ... }
```

`Boolean.TRUE.equals(null)` 返回 `false`，不会抛出异常。

`Optional` 是 Java 8 引入的一个容器类，专门用来优雅地处理可能为 `null` 的值：

```java
// Optional 的典型用法
Optional<String> token = Optional.ofNullable(resolveToken(request));

// 如果 token 存在才执行
token.ifPresent(t -> System.out.println("Token: " + t));

// 如果 token 存在返回它，否则返回默认值
String result = token.orElse("default");

// 如果 token 存在返回它，否则抛出异常
String result = token.orElseThrow(() -> new RuntimeException("Token missing"));
```

### 4.5 集合类型：Collection, List, Collections

在 JwtAuthFilter 中创建权限列表时，我们用了 `Collections.singletonList()`：

```java
Collections.singletonList(
    new SimpleGrantedAuthority("ROLE_" + role)
)
```

Java 集合框架的层次关系：

```
Collection（接口，所有集合的根）
  ├── List（接口，有序可重复）
  │     ├── ArrayList（常用实现类）
  │     ├── LinkedList
  │     └── ...
  ├── Set（接口，无序不重复）
  │     ├── HashSet
  │     └── ...
  └── Queue（接口，队列）
        └── ...
```

`Collections` 是一个工具类（注意有个 `s`），提供了很多静态方法：

| 方法 | 作用 |
|------|------|
| `Collections.singletonList(e)` | 创建只包含一个元素的不可变列表 |
| `Collections.emptyList()` | 创建空列表 |
| `Collections.unmodifiableList(list)` | 把列表包装为不可变列表 |

### 4.6 Lambda 表达式与方法引用

在 SecurityConfig 中大量使用了 Lambda 表达式：

```java
// Lambda 表达式
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

// 更复杂的 Lambda
.authorizeHttpRequests(auth -> {
    auth.requestMatchers("/api/auth/**").permitAll();
    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
    auth.anyRequest().authenticated();
})
```

Lambda 的语法：`(参数) -> { 方法体 }`。它本质上是一个"匿名函数"——不需要定义类和方法，直接写逻辑。

当方法体只有一行时，可以省略大括号和 `return`：

```java
// 完整写法
session -> { return session.sessionCreationPolicy(SessionCreationPolicy.STATELESS); }

// 简写
session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

**方法引用**是 Lambda 的更简写法：

```java
// Lambda 写法
.csrf(config -> config.disable())

// 方法引用写法（效果完全相同）
.csrf(AbstractHttpConfigurer::disable)
```

`类名::方法名` 的含义是"调用这个类的这个方法"。`AbstractHttpConfigurer::disable` 等价于 `config -> config.disable()`。

### 4.7 HttpServletRequest / HttpServletResponse

这两个接口是 Servlet API 的核心，封装了 HTTP 请求和响应的所有信息。

**HttpServletRequest 常用方法：**

```java
request.getMethod();              // "GET"、"POST" 等
request.getRequestURI();          // "/api/folders/tree"
request.getHeader("Authorization"); // 获取请求头
request.getParameter("name");     // 获取查询参数 ?name=xxx
request.getInputStream();         // 获取请求体（JSON 数据）
```

**HttpServletResponse 常用方法：**

```java
response.setStatus(401);                              // 设置状态码
response.setContentType("application/json");          // 设置响应类型
response.setHeader("Access-Control-Allow-Origin", "*"); // 设置响应头
response.getWriter().write("{\"error\":\"Unauthorized\"}"); // 写入响应体
```

在我们的 JwtAuthFilter 中，我们主要从 `request` 中读取了 `Authorization` 请求头，但没有直接操作 `response`。如果认证失败，Spring Security 的后续过滤器会自动写入 401 响应。

---

## 5. 动手练习建议

### 练习 1：用浏览器观察过滤器链的行为

1. 启动项目
2. 打开浏览器的开发者工具（F12），切换到 Network 标签
3. 访问 `http://localhost:8080/api/auth/login`（不带 Token），观察响应状态码
4. 用 POST 方法发送登录请求，获取 Token
5. 携带 Token 访问 `http://localhost:8080/api/folders/tree`，观察正常响应
6. 不携带 Token 访问同一个 URL，观察 401 响应

### 练习 2：在 JwtAuthFilter 中添加日志

在 `doFilterInternal` 方法的开头添加一行日志：

```java
log.info("JwtAuthFilter 处理请求: {} {}", request.getMethod(), request.getRequestURI());
```

然后重启项目，访问几个接口，观察控制台输出。你会看到每个请求都会打印这行日志。

### 练习 3：理解"黑名单"的效果

1. 登录获取 Token
2. 用这个 Token 访问 `/api/folders/tree`（成功）
3. 调用 `/api/auth/logout`（登出，Token 加入黑名单）
4. 用同一个 Token 再次访问 `/api/folders/tree`（应该返回 401）

### 练习 4：思考题

1. 如果在 SecurityConfig 中把 `.anyRequest().authenticated()` 放在 `.requestMatchers("/api/auth/**").permitAll()` 前面，会发生什么？
   - 提示：规则是按顺序匹配的
2. 为什么 JwtAuthFilter 中即使 Token 无效也要调用 `filterChain.doFilter()`？
3. 如果我们把 `userId` 改成整个 `User` 对象作为 `principal`，会有什么优缺点？

### 练习 5：画出完整的认证流程图

尝试在纸上画出从"用户点击登录按钮"到"返回文件夹列表数据"的完整流程图。包括：

- 前端发送请求
- Tomcat 接收
- 各个过滤器依次处理
- SecurityContext 的存入和读取
- Controller 获取 userId
- 返回响应

> **参考答案**

```mermaid
flowchart TD
    subgraph 登录流程["🔐 登录流程（POST /api/auth/login）"]
        A1["👤 用户点击登录按钮"] --> A2["🌐 浏览器发送 POST /api/auth/login\n携带 username + password"]
        A2 --> A3["🖥️ Tomcat 接收请求"]
        A3 --> A4["🔗 CorsFilter\n处理跨域请求头"]
        A4 --> A5["🛡️ CsrfFilter\n已禁用，直接放行"]
        A5 --> A6["🔑 JwtAuthFilter\n检查 Authorization 头\n登录请求没有 Token → 跳过"]
        A6 --> A7["🎯 AuthController.login()"]
        A7 --> A8["📋 AuthService 处理登录"]
        A8 --> A9{"密码验证通过？"}
        A9 -- 是 --> A10["✅ 生成 JWT Token"]
        A9 -- 否 --> A11["❌ 返回 401 错误"]
        A10 --> A12["📤 返回 Token 给浏览器\n{ token: 'eyJhbG...' }"]
    end

    subgraph 后续请求流程["📦 后续请求流程（GET /api/ext/folders/tree）"]
        B1["👤 用户打开扩展插件\n（自动请求文件夹树）"] --> B2["🌐 浏览器发送 GET /api/ext/folders/tree\n携带 Authorization: Bearer eyJhbG..."]
        B2 --> B3["🖥️ Tomcat 接收请求"]
        B3 --> B4["🔗 CorsFilter\n处理跨域请求头"]
        B4 --> B5["🛡️ CsrfFilter\n已禁用，直接放行"]
        B5 --> B6["🔑 JwtAuthFilter\n提取 Authorization 头"]
        B6 --> B7{"Token 存在？"}
        B7 -- 否 --> B8["⚠️ 无 Token\n设置 SecurityContext 为空\n继续过滤链"]
        B7 -- 是 --> B9["🔍 验证 JWT 签名"]
        B9 --> B10{"签名有效？"}
        B10 -- 否 --> B11["❌ Token 无效\n设置 SecurityContext 为空\n继续过滤链"]
        B10 -- 是 --> B12["📋 检查 Token 黑名单（Redis）"]
        B12 --> B13{"在黑名单中？"}
        B13 -- 是 --> B14["❌ Token 已登出\n设置 SecurityContext 为空\n继续过滤链"]
        B13 -- 否 --> B15["🆔 提取 userId\n存入 SecurityContext"]
        B15 --> B16["🟢 认证信息已就绪"]
        B8 --> B17{"路径需要认证？"}
        B11 --> B17
        B14 --> B17
        B16 --> B18["🎯 ExtensionController.getFolderTree()"]
        B17 -- 是 --> B19["❌ 返回 401 未认证"]
        B17 -- 否 --> B18
        B18 --> B20["@AuthenticationPrincipal 获取 userId\n从 SecurityContext 中读取"]
        B20 --> B21["📂 FolderService.getFolderTree(userId)"]
        B21 --> B22["🗄️ 查询数据库\n组装文件夹树形结构"]
        B22 --> B23["📤 返回 Result＜List＜FolderTreeResponse＞＞"]
        B23 --> B24["🌐 浏览器接收响应\n渲染文件夹树"]
    end

    A12 -. "浏览器保存 Token" .-> B1
```

---

## 总结

这一阶段我们学习了 Spring Security 的核心机制：

| 概念 | 作用 | 类比 |
|------|------|------|
| Servlet Filter | 请求到达 Controller 之前的预处理 | 安检通道 |
| 责任链模式 | 多个 Filter 串联处理 | 工厂质检流水线 |
| Spring Security 过滤器链 | 一组安全相关的 Filter | 小区门禁系统 |
| JwtAuthFilter | 验证 JWT Token 并设置认证信息 | 查验身份证的保安 |
| SecurityContext | 存储当前用户的认证信息 | 服务员的托盘 |
| SecurityConfig | 定义安全规则（哪些路径放行/需要认证） | 门禁系统的规则手册 |
| @AuthenticationPrincipal | 在 Controller 中获取当前用户 | 从托盘中取点菜单 |

Spring Security 确实是 Java 后端最难学的部分之一，但只要理解了"过滤器链 + SecurityContext + 配置规则"这个核心三角，大部分问题都能迎刃而解。
