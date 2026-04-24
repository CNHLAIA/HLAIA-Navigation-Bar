# 阶段 03：用户登录与 JWT 认证

> 学习目标：理解"登录"这件事在后端到底发生了什么，掌握 JWT 的核心概念，读懂项目中完整的认证链路代码。
>
> 前置阶段：阶段 01（项目骨架搭建）、阶段 02（用户注册完整链路）

---

## 目录

1. [前置知识：需要先懂的 Java 概念](#1-前置知识需要先懂的-java-概念)
2. [概念讲解：认证、Token、JWT](#2-概念讲解认证tokenjwt)
3. [代码逐行解读：登录流程](#3-代码逐行解读登录流程)
4. [代码逐行解读：JwtTokenProvider](#4-代码逐行解读jwttokenprovider)
5. [代码逐行解读：错误处理体系](#5-代码逐行解读错误处理体系)
6. [关键 Java 语法点](#6-关键-java-语法点)
7. [动手练习建议](#7-动手练习建议)

---

## 1. 前置知识：需要先懂的 Java 概念

在阅读本章代码之前，你需要了解以下几个 Java 基础概念。如果你已经熟悉，可以跳过直接看第 2 节。

### 1.1 枚举（enum）

枚举是一种特殊的类，用来定义一组固定的常量。

```java
// 普通常量的写法（不推荐）
public static final int SUCCESS = 200;
public static final int USER_EXISTS = 1001;
// 问题：类型是 int，可以传入任意数字，编译器不会帮你检查

// 枚举的写法（推荐）
public enum ErrorCode {
    SUCCESS(200, "success"),
    USER_EXISTS(1001, "Username already exists");

    private final int code;
    private final String message;
    // ... 构造方法省略
}
// 使用：ErrorCode.SUCCESS —— 类型安全，不可能传错
```

你可以把枚举想象成一个"菜单"——菜单上的菜品是固定的，你不能点一道菜单上没有的菜。在本项目中，`ErrorCode` 就是这样一个菜单，列出了系统所有可能的错误情况。

### 1.2 Map —— 键值对集合

Map 是 Java 中存储"键值对"的数据结构，类似于字典或电话簿。

```java
Map<String, Object> claims = new HashMap<>();
claims.put("username", "admin");   // key = "username", value = "admin"
claims.put("role", "USER");        // key = "role", value = "USER"

String name = claims.get("username");  // 取出 "admin"
```

在 JWT 中，Payload（载荷）部分就是一个键值对集合。jjwt 库用 `Claims` 接口来表示它，`Claims` 本质上就是一个特殊的 `Map<String, Object>`。

### 1.3 Date 类和时间处理

`java.util.Date` 表示一个特定的时间瞬间（精确到毫秒）。

```java
Date now = new Date();                     // 当前时间
long timestamp = now.getTime();            // 转为毫秒数（从 1970-01-01 00:00:00 起）
Date future = new Date(now.getTime() + 3600000); // 1 小时后的时间（3600000 毫秒）
```

在 JWT 中，我们用 `Date` 来设置 Token 的签发时间（iat）和过期时间（exp）。

### 1.4 字符串操作

本阶段代码中用到了两个字符串方法：

```java
// substring(beginIndex)：从指定位置截取到末尾
String authHeader = "Bearer eyJhbGciOi...";
String token = authHeader.substring(7);    // 跳过 "Bearer "（7个字符），得到纯 Token

// String.format()：格式化字符串（类似 C 语言的 printf）
String msg = String.format("JWT Token 验证失败: %s", "expired");
// 结果："JWT Token 验证失败: expired"
```

---

## 2. 概念讲解：认证、Token、JWT

### 2.1 认证（Authentication） vs 授权（Authorization）

这两个英文单词长得很像，但意思完全不同：

| | 认证 Authentication | 授权 Authorization |
|---|---|---|
| 核心问题 | **你是谁？** | **你能做什么？** |
| 生活中的例子 | 出示身份证证明你是张三 | 张三是普通员工，不能进入总经理办公室 |
| 在本项目中 | 用户登录，验证用户名和密码 | 管理员（ADMIN）可以管理用户，普通用户（USER）不行 |
| 发生顺序 | 先认证（先知道你是谁） | 再授权（再决定你能做什么） |

本阶段重点讲**认证**——也就是"登录"这个动作。

### 2.2 Session 方案 vs Token 方案

用户登录成功后，服务器需要记住"这个人已经登录了"。有两种主流方案：

#### Session 方案（传统方案）

```
1. 用户提交用户名+密码
2. 服务器验证通过，创建一个 Session（会话），存在服务器内存中
3. 服务器返回一个 Session ID（如 "abc123"）给浏览器，浏览器存入 Cookie
4. 之后每次请求，浏览器自动带上 Cookie 中的 Session ID
5. 服务器根据 Session ID 找到对应的 Session，就知道是哪个用户了
```

**问题：**

- **扩展困难**：如果部署了 3 台服务器，用户登录时请求到了服务器 A，Session 存在 A 上。下一次请求可能到了服务器 B，B 上没有这个 Session，用户就"掉线"了。虽然可以用 Redis 集中存储 Session 来解决，但增加了系统复杂度。
- **移动端不友好**：手机 App 不像浏览器那样原生支持 Cookie，需要额外处理。
- **CSRF 风险**：Cookie 会被浏览器自动携带，容易被跨站请求伪造攻击。

#### Token 方案（本项目的选择）

```
1. 用户提交用户名+密码
2. 服务器验证通过，生成一个 Token（令牌），Token 中包含用户信息
3. 服务器把 Token 返回给客户端
4. 之后每次请求，客户端在 HTTP 头中手动携带 Token
5. 服务器验证 Token 的签名和过期时间，就知道是哪个用户了
```

**优势：**

- **无状态（Stateless）**：服务器不存储任何会话信息，Token 自带所有必要信息。
- **天然支持多服务器**：任何一台服务器都能验证 Token，不需要共享 Session。
- **跨平台友好**：Web、App、小程序都用同样的方式使用 Token。

### 2.3 JWT（JSON Web Token）的结构

JWT 是目前最流行的 Token 格式。它本质上就是一个**带签名的字符串**，由三部分组成，用 `.` 分隔：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYiLCJyb2xlIjoiQURNSU4ifQ.xxx
\_____________/  \___________________________________/  \_/
    Header                   Payload                   Signature
```

#### 类比：JWT 就像一张"带照片的工作证"

想象你在一家大公司上班：

- **Header**（头部）= 工作证上印的"本公司工作证"字样和防伪技术编号。它告诉验证者："这是一张 JWT，使用 HS256 算法签名。"
- **Payload**（载荷）= 工作证上印的信息——姓名、工号、部门、有效期。它包含用户 ID、用户名、角色、过期时间等数据。
- **Signature**（签名）= 工作证上的钢印或公章。只有公司安保部（服务器）持有公章，其他人无法伪造。任何对工作证的涂改都会导致公章校验失败。

**关键点：JWT 不是加密的，只是签名防篡改。**

Header 和 Payload 只是 Base64 编码（一种简单的编码方式），任何人都能解码查看内容。所以**绝不能在 JWT 中存放密码等敏感信息**。Signature 的作用不是隐藏信息，而是保证信息不被篡改——就像公章不能防止你看到工作证上的内容，但能防止你涂改它。

### 2.4 双 Token 机制：Access Token + Refresh Token

如果只用一个 Token，会面临一个矛盾：

| 策略 | 有效期短 | 有效期长 |
|---|---|---|
| 优点 | 即使泄露，危害时间窗口很小 | 用户不用频繁重新登录，体验好 |
| 缺点 | 用户需要频繁重新登录，体验差 | 泄露后影响大，安全风险高 |

双 Token 机制巧妙地解决了这个矛盾：

- **Access Token**（访问令牌）：有效期短（本项目默认 24 小时）。客户端在每次请求的 `Authorization` 头中携带。就像你的**工牌**——日常进出各个房间都要刷，有效期短。
- **Refresh Token**（刷新令牌）：有效期长（本项目默认 7 天）。只在 Access Token 过期时使用，用来换取新的 Token 对。就像你的**身份证明文件**——不常用，但在需要续期工牌时拿出来。

```
日常访问流程：
  客户端 ──(携带 Access Token)──> 服务器
  客户端 <──(返回数据)──────────── 服务器

Access Token 过期时：
  客户端 ──(携带 Refresh Token)──> 服务器
  客户端 <──(返回新的 Token 对)─── 服务器    ← 用户无感知，不需要重新输入密码

Refresh Token 也过期时：
  用户需要重新输入用户名和密码登录
```

**为什么这样更安全？** Access Token 使用频率高，被截获的概率相对较大，但有效期短，泄露后危害有限。Refresh Token 使用频率低（只在刷新时用一次），被截获的概率小，有效期虽长但被窃取的风险可控。

---

## 3. 代码逐行解读：登录流程

用户点击"登录"按钮后，数据在系统中的流转路径如下：

```
前端提交 JSON ──> LoginRequest（接收数据）
                  ──> AuthController.login()（接收请求，参数校验）
                      ──> AuthService.login()（业务逻辑：查用户、验密码、检查封禁、生成 Token）
                          ──> JwtTokenProvider（生成 Token）
                      <── AuthResponse（Token + 用户信息）
                  <── Result<AuthResponse>（统一响应包装）
前端收到 JSON <──
```

### 3.1 LoginRequest：前端发来的数据长什么样

对应文件：`src/main/java/com/hlaia/dto/request/LoginRequest.java`

```java
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

登录只需要两个字段：用户名和密码。注意这里只用了 `@NotBlank`（不为空），没有像注册时那样加 `@Size`（长度限制）。原因在代码注释里已经解释了：注册时已经校验过长度，登录时只需检查"不为空"，这样可以给攻击者更少的信息（"用户名或密码错误"而不是"用户名长度不合法"）。

### 3.2 AuthController.login()：请求入口

对应文件：`src/main/java/com/hlaia/controller/AuthController.java`

```java
@PostMapping("/login")
@Operation(summary = "Login and get JWT tokens")
public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.success(authService.login(request));
}
```

这一行代码虽然短，但做了几件事：

1. `@PostMapping("/login")`：声明这个方法处理 `POST /api/auth/login` 请求（`/api/auth` 前缀来自类上的 `@RequestMapping`）。
2. `@Valid`：触发 `LoginRequest` 上的校验注解。如果用户名为空，Spring 会自动返回 400 错误，不会进入方法体。
3. `@RequestBody`：把 HTTP 请求体中的 JSON 自动转换成 `LoginRequest` 对象。
4. `authService.login(request)`：把数据交给 Service 层处理，Controller 自己不做任何业务判断。
5. `Result.success(...)`：把 Service 返回的 `AuthResponse` 包装成统一的响应格式。

**Controller 层的核心原则：薄薄一层，只做"收发"。** 就像餐厅服务员——只负责接单和上菜，做菜是厨师（Service）的事。

### 3.3 AuthService.login()：核心业务逻辑

对应文件：`src/main/java/com/hlaia/service/AuthService.java`

这是登录流程的"大脑"，我们来逐块分析：

#### 第一步：根据用户名查询数据库

```java
User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
```

- `userMapper.selectOne(...)`：使用 MyBatis-Plus 查询数据库，期望返回 0 或 1 条记录。
- `LambdaQueryWrapper<User>()`：查询条件构造器，`eq` 表示"等于"。
- `User::getUsername`：Lambda 表达式引用 `username` 字段，编译器会检查字段是否存在。
- 等价 SQL：`SELECT * FROM user WHERE username = 'alice'`

如果用户名不存在，`user` 就是 `null`。

#### 第二步：验证用户存在 + 密码正确

```java
if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
}
```

这一行代码包含了两个重要的安全设计：

**设计一：合并错误信息，防止用户名枚举攻击。**

用户名枚举攻击是什么？假设我们这样写：

```java
// 错误示范！
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);    // "用户不存在"
}
if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
    throw new BusinessException(ErrorCode.WRONG_PASSWORD);    // "密码错误"
}
```

攻击者可以批量尝试用户名：收到"用户不存在"说明这个用户名没注册，收到"密码错误"说明这个用户名已注册。这样攻击者就能扫描出系统中所有的用户名。

我们合并成同一个错误码 `INVALID_CREDENTIALS`（"用户名或密码错误"），攻击者无法区分是哪个不对。

**设计二：BCrypt.matches() 的原理。**

`passwordEncoder.matches(明文, 密文)` 并不是把密文"解密"后再比较，因为 BCrypt 是**不可逆的哈希算法**，没有解密这回事。它的实际流程是：

1. 从密文中提取出盐值（Salt）和计算参数（cost factor）。BCrypt 密文的格式是 `$2a$10$Salt(22字符)Hash(31字符)`，其中 `$2a$10$` 后面的 22 个字符就是盐值。
2. 用提取出的盐值和参数，对用户输入的明文密码进行同样的哈希运算。
3. 比较两次运算产生的哈希值是否一致。

简单说：不是"解开锁看对不对"，而是"用同一把钥匙再锁一次，看锁出来的样子一不一样"。

#### 第三步：检查封禁状态

```java
if (user.getStatus() == 1) {
    throw new BusinessException(ErrorCode.USER_BANNED);
}
```

`status == 1` 表示用户被管理员封禁了。即使密码正确，也不允许登录。注意这个检查在密码验证**之后**——我们不希望攻击者通过封禁状态判断用户名是否存在。

#### 第四步：生成 Token 并返回

```java
return generateAuthResponse(user);
```

调用私有辅助方法，生成 Access Token + Refresh Token，封装成 `AuthResponse` 返回。

### 3.4 generateAuthResponse()：生成 Token 对的辅助方法

```java
private AuthResponse generateAuthResponse(User user) {
    String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId(), user.getUsername(), user.getRole());

    String refreshToken = jwtTokenProvider.generateRefreshToken(
            user.getId(), user.getUsername(), user.getRole());

    return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .username(user.getUsername())
            .role(user.getRole())
            .build();
}
```

逐行分析：

1. 调用 `jwtTokenProvider.generateAccessToken(...)` 生成短期 Token。
2. 调用 `jwtTokenProvider.generateRefreshToken(...)` 生成长期 Token。
3. 使用 `AuthResponse.builder()...build()` 的 Builder 模式构建响应对象。

**为什么这个方法是 `private` 的？** 因为它是 `register`、`login`、`refresh` 三个方法的内部复用逻辑，不对外暴露。把方法设为 `private` 就像把厨房的备菜区隔开——客人不需要知道备菜区在哪里，他们只需要享用最终上桌的菜品。

**Builder 模式**是 `AuthResponse` 上 `@Builder` 注解自动生成的。对比传统写法：

```java
// 传统写法
AuthResponse res = new AuthResponse();
res.setAccessToken(accessToken);
res.setRefreshToken(refreshToken);
res.setUsername(user.getUsername());
res.setRole(user.getRole());

// Builder 写法（更优雅、可读性更强）
AuthResponse res = AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .username(user.getUsername())
        .role(user.getRole())
        .build();
```

### 3.5 AuthResponse：返回给前端的数据长什么样

对应文件：`src/main/java/com/hlaia/dto/response/AuthResponse.java`

```java
@Data
@Builder
public class AuthResponse {
    private String accessToken;     // 访问令牌（短期）
    private String refreshToken;    // 刷新令牌（长期）
    private String username;        // 用户名（前端展示用）
    private String role;            // 角色（前端控制权限展示用）
}
```

登录成功后，前端会收到类似这样的 JSON：

```json
{
    "code": 200,
    "message": "success",
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIi...",
        "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIi...",
        "username": "admin",
        "role": "ADMIN"
    }
}
```

前端拿到后，把 `accessToken` 存在本地（如 localStorage），之后每次请求都在 HTTP 头里带上它：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 4. 代码逐行解读：JwtTokenProvider

对应文件：`src/main/java/com/hlaia/security/JwtTokenProvider.java`

这是整个认证体系的核心工具类，负责 JWT 的生成、解析和验证。

### 4.1 字段和构造方法

```java
@Component
public class JwtTokenProvider {

    private final SecretKey key;                        // 签名密钥
    private final long accessTokenExpiration;           // Access Token 过期时间（毫秒）
    private final long refreshTokenExpiration;          // Refresh Token 过期时间（毫秒）

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}
```

**@Value 注解**从 `application.yml` 配置文件中读取值。例如配置文件中写了：

```yaml
jwt:
  secret: "my-very-long-secret-key-at-least-32-bytes!!"
  access-token-expiration: 86400000     # 24 小时 = 24 * 60 * 60 * 1000 毫秒
  refresh-token-expiration: 604800000   # 7 天 = 7 * 24 * 60 * 60 * 1000 毫秒
```

`@Value("${jwt.secret}")` 就会把 `"my-very-long-secret-key..."` 注入到构造方法参数 `secret` 中。

**Keys.hmacShaKeyFor()** 把字符串转换成 HMAC-SHA256 算法需要的 `SecretKey` 对象。密钥长度必须 >= 32 字节（256 位），否则算法不安全。

**为什么用构造函数注入？** 对比三种注入方式：

| 方式 | 代码 | 优缺点 |
|---|---|---|
| 字段注入 | `@Autowired private Xxx xxx` | 简单但不推荐：字段不能是 final，不利于测试 |
| Setter 注入 | `@Autowired public void setXxx(Xxx x)` | 可选依赖时用，但登录/Token 这些是必须的 |
| **构造函数注入** | `public JwtTokenProvider(Xxx x)` | **Spring 推荐**：字段可以是 final（不可变），强制依赖，方便测试 |

### 4.2 generateAccessToken() 和 generateRefreshToken()：方法重载

```java
public String generateAccessToken(Long userId, String username, String role) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + accessTokenExpiration);    // 当前时间 + 短期时长
    return buildToken(userId, username, role, now, expiration);
}

public String generateRefreshToken(Long userId, String username, String role) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + refreshTokenExpiration);   // 当前时间 + 长期时长
    return buildToken(userId, username, role, now, expiration);
}
```

这两个方法的**参数完全相同，内部逻辑几乎一样，唯一的区别就是过期时间不同**。它们都调用了同一个私有方法 `buildToken()`，只是传入了不同的过期时长。

这就是**方法重载**在实践中的应用——两个方法做"同一类事"（生成 Token），但细节不同（过期时间），用不同的方法名来区分意图。相比之下，如果用一个方法加 boolean 参数（如 `generateToken(userId, username, role, true)`），可读性会差很多——`true` 代表什么？是 Access 还是 Refresh？用方法名来区分更清晰。

### 4.3 buildToken()：Token 生成的核心

```java
private String buildToken(Long userId, String username, String role,
                          Date now, Date expiration) {
    return Jwts.builder()
            .subject(userId.toString())           // sub = 用户 ID
            .claim("username", username)          // 自定义字段：用户名
            .claim("role", role)                  // 自定义字段：角色
            .issuedAt(now)                        // iat = 签发时间
            .expiration(expiration)               // exp = 过期时间
            .signWith(key, Jwts.SIG.HS256)        // 用密钥 + HS256 算法签名
            .compact();                           // 序列化为字符串
}
```

`Jwts.builder()` 使用了**建造者模式（Builder Pattern）**。建造者模式就像填写一张表格：

```
┌─────────────────────────────────────────┐
│          JWT 通行证                       │
│                                         │
│  持证人编号（subject）:  1               │
│  持证人姓名（claim）:    admin           │
│  持证人等级（claim）:    ADMIN           │
│  签发日期（iat）:        2026-04-18      │
│  有效期至（exp）:        2026-04-19      │
│                                         │
│  [公章/签名]                             │
└─────────────────────────────────────────┘
```

每一步都在往这张"通行证"上填写信息：

- `.subject(userId.toString())`：标准字段 `sub`（Subject），存用户 ID。为什么用 ID 而不是用户名？因为用户名可能被修改，而 ID 永远不变。
- `.claim("username", username)`：自定义字段。`claim` 就是"声明"的意思，你在声明"这个用户叫 admin"。
- `.claim("role", role)`：自定义字段。声明"这个用户是 ADMIN 角色"。
- `.issuedAt(now)`：标准字段 `iat`（Issued At），签发时间。
- `.expiration(expiration)`：标准字段 `exp`（Expiration），过期时间。过期之后，这个 Token 就无效了。
- `.signWith(key, Jwts.SIG.HS256)`：签名。用密钥 `key` 和 HMAC-SHA256 算法生成签名。
- `.compact()`：把所有信息打包成最终的 JWT 字符串 `xxxxx.yyyyy.zzzzz`。

### 4.4 parseToken()：Token 解析的核心

```java
private Claims parseToken(String token) {
    return Jwts.parser()
            .verifyWith(key)                   // 设置验证签名的密钥
            .build()
            .parseSignedClaims(token)          // 解析并验证 Token
            .getPayload();                     // 获取 Payload 部分
}
```

`parseToken` 做了什么？想象你在检票口：

1. 拿到一张票（Token 字符串）。
2. 按 `.` 分割成三部分：Header、Payload、Signature。
3. Base64 解码 Header 和 Payload，看到里面的信息。
4. 用密钥重新计算签名，和票上的签名对比——如果不一样，说明票被伪造或篡改了，直接拒绝。
5. 检查过期时间——如果过期了，也拒绝。
6. 全部通过，返回 Payload 的内容（`Claims` 对象）。

如果验证失败，会抛出各种异常：`ExpiredJwtException`（过期）、`SignatureException`（签名不对）、`MalformedJwtException`（格式错误）等。

**什么是 Claims？** Claims 就是 JWT 的 Payload 数据。它本质上是一个 `Map<String, Object>`，包含：

- 标准字段：`sub`（用户 ID）、`iat`（签发时间）、`exp`（过期时间）
- 我们自定义的字段：`username`、`role`

```java
// Claims 内部数据示例
{
    "sub": "1",                    // 用户 ID（toString() 后的字符串）
    "username": "admin",           // 自定义字段
    "role": "ADMIN",               // 自定义字段
    "iat": 1744900000,             // 签发时间（时间戳）
    "exp": 1744986400              // 过期时间（时间戳）
}
```

### 4.5 从 Token 中提取信息的方法

```java
public Long getUserIdFromToken(String token) {
    return Long.parseLong(parseToken(token).getSubject());
    // parseToken(token) 返回 Claims
    // getSubject() 返回 "sub" 字段的值（字符串 "1"）
    // Long.parseLong() 把 "1" 转成数字 1
}

public String getRoleFromToken(String token) {
    return parseToken(token).get("role", String.class);
    // get("role", String.class) 从 Claims 中取 "role" 的值，类型指定为 String
}

public Date getExpirationFromToken(String token) {
    return parseToken(token).getExpiration();
    // getExpiration() 返回 "exp" 字段的值，自动转为 Date 对象
}
```

这三个方法都是对 `parseToken()` 的简单封装。`parseToken` 每次被调用时都会完整验证 Token（签名 + 过期时间），所以调用这些方法时如果 Token 无效，会直接抛异常。

### 4.6 validateToken()：优雅的验证方法

```java
public boolean validateToken(String token) {
    try {
        parseToken(token);
        return true;                        // 没抛异常，说明有效
    } catch (JwtException e) {              // 签名错误、过期等
        log.warn("JWT Token 验证失败: {}", e.getMessage());
        return false;
    } catch (IllegalArgumentException e) {   // Token 为 null 或空字符串
        log.warn("JWT Token 为空");
        return false;
    }
}
```

**为什么需要这个方法？** 在 `JwtAuthFilter`（后续阶段会讲）中，每次请求都要检查 Token 是否有效。如果直接调用 `parseToken()`，无效的 Token 会抛异常，我们需要用 try-catch 包裹。`validateToken()` 就是把这个 try-catch 逻辑封装起来，让调用方只需判断 `true/false`，更简洁。

**`log.warn(...)` 是什么？** 这是日志输出。`@Slf4j` 注解让 Lombok 自动生成了一个 `log` 对象。`warn` 级别表示"警告"——不是严重错误，但值得记录。日志会输出到控制台和日志文件，方便排查问题。

---

## 5. 代码逐行解读：错误处理体系

### 5.1 ErrorCode：错误码枚举

对应文件：`src/main/java/com/hlaia/common/ErrorCode.java`

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 标准 HTTP 错误码
    SUCCESS(200, "success"),
    UNAUTHORIZED(401, "Unauthorized"),

    // 认证相关（1001-1999）
    INVALID_CREDENTIALS(1002, "Invalid username or password"),
    TOKEN_EXPIRED(1003, "Token expired"),
    TOKEN_INVALID(1004, "Invalid token"),

    // 业务相关（2001-2999）
    USER_NOT_FOUND(2004, "User not found"),
    USER_BANNED(2005, "User is banned"),

    // ... 其他错误码

    private final int code;
    private final String message;
}
```

**错误码规划规则：**

- `200-500`：标准 HTTP 状态码，给通用错误用。
- `1001-1999`：认证相关错误。登录、注册、Token 相关的问题都在这个范围。
- `2001-2999`：业务相关错误。书签、文件夹、权限等业务逻辑的问题。

**为什么要用数字编码？** 前端可以根据 `code` 的范围来分类处理错误。比如前端收到 `1002` 就知道是"认证失败"，统一跳转到登录页；收到 `2001-2999` 就弹出对应的错误提示。

**为什么不用 Java 的魔法数字（直接写 1002）？**

```java
// 坏：数字的含义不明确
throw new BusinessException(1002, "Invalid username or password");

// 好：枚举值一眼就知道是什么意思
throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
```

而且枚举有编译器检查——如果你写错了枚举名，编译都过不了。数字写错了编译器不会报错，运行时才发现 bug。

### 5.2 BusinessException：业务异常类

对应文件：`src/main/java/com/hlaia/common/BusinessException.java`

```java
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());   // 设置异常信息
        this.code = errorCode.getCode(); // 设置错误码
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

**为什么继承 `RuntimeException` 而不是 `Exception`？**

Java 异常分两大类：

- **受检异常（Checked Exception）**：继承 `Exception`。必须在方法签名中声明 `throws`，或者用 `try-catch` 处理。适合"可预期的、需要恢复的错误"，比如文件不存在。
- **非受检异常（Unchecked Exception）**：继承 `RuntimeException`。不需要强制处理，会自动向上传递。适合"编程错误或不可恢复的业务错误"，比如用户名不存在。

我们选择继承 `RuntimeException`，这样 Service 层抛出异常时不需要在每个方法上写 `throws BusinessException`，代码更简洁。异常会被 `GlobalExceptionHandler`（全局异常处理器）统一捕获，转换成标准的 JSON 响应返回给前端。

**两个构造方法的设计：**

- 构造方法 1（推荐）：传入 `ErrorCode` 枚举。大多数情况用这个。
- 构造方法 2（备用）：直接传入数字和字符串。应对特殊情况，比如需要动态拼接错误信息。

```java
// 通常这样用
throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
// 效果：code=1002, message="Invalid username or password"

// 特殊情况这样用
throw new BusinessException(9999, "发生了预料之外的情况：" + detail);
```

---

## 6. 关键 Java 语法点

### 6.1 枚举（enum）的高级用法

本项目中的 `ErrorCode` 展示了枚举的高级用法——枚举不只是常量集合，它还可以有字段、构造方法和方法：

```java
public enum ErrorCode {
    // 枚举值 = 调用构造方法创建实例
    SUCCESS(200, "success"),                 // 等价于 new ErrorCode(200, "success")
    INVALID_CREDENTIALS(1002, "Invalid..."); // 等价于 new ErrorCode(1002, "Invalid...")

    // 字段
    private final int code;
    private final String message;

    // 构造方法（枚举的构造方法默认是 private 的，不能从外部调用）
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // getter 方法由 @Getter 注解自动生成
}
```

使用时：

```java
ErrorCode err = ErrorCode.INVALID_CREDENTIALS;
err.getCode();     // 1002
err.getMessage();  // "Invalid username or password"
```

### 6.2 final 关键字的三种用法

在本阶段代码中，`final` 出现在多个地方：

```java
// 1. final 字段：赋值后不能修改
private final SecretKey key;              // 构造函数注入后不再改变
private final int code;                   // BusinessException 的错误码不可变

// 2. final 方法参数：方法内不能修改参数值（本项目未显式使用，但 good practice）
public void doSomething(final String input) {
    // input = "other";  // 编译错误！
}

// 3. final 类：不能被继承（本项目未使用，如 String 就是 final 类）
```

`final` 的核心思想是**不可变性**——一旦创建就不能修改。这在多线程环境下特别重要，因为不可变对象天然线程安全。

### 6.3 Date 类和时间计算

```java
Date now = new Date();                               // 当前时间
Date expiration = new Date(now.getTime() + 86400000); // 24 小时后
long remainingMs = expiration.getTime() - System.currentTimeMillis(); // 剩余毫秒数
```

- `new Date()`：创建表示"此刻"的 Date 对象。
- `now.getTime()`：返回从 1970-01-01 00:00:00 UTC 到此刻的毫秒数（时间戳）。
- `System.currentTimeMillis()`：同样返回当前时间戳，但不需要先创建 Date 对象。

在 Token 相关的时间计算中，我们经常需要做"当前时间 + X 毫秒 = 过期时间"这样的运算。

### 6.4 Map 和 Claims

`Claims` 接口继承自 `Map<String, Object>`，同时添加了一些便捷方法：

```java
Claims claims = parseToken(token);

// 便捷方法（标准 JWT 字段）
claims.getSubject();      // 等价于 claims.get("sub")
claims.getIssuedAt();     // 等价于 claims.get("iat")
claims.getExpiration();   // 等价于 claims.get("exp")

// 通用 Map 方法（自定义字段）
claims.get("username", String.class);    // 获取自定义字段，指定返回类型
claims.get("role", String.class);
```

之所以需要用 `get("role", String.class)` 而不是 `get("role")`，是因为 `Claims` 底层是 `Map<String, Object>`，直接 `get("role")` 返回的是 `Object` 类型，需要强转。传入 `String.class` 让 jjwt 库帮你做类型转换，更安全。

### 6.5 try-catch 异常处理

`validateToken()` 方法展示了 try-catch 的典型用法：

```java
public boolean validateToken(String token) {
    try {
        parseToken(token);          // 尝试执行可能出错的代码
        return true;
    } catch (JwtException e) {      // 捕获特定类型的异常
        log.warn("验证失败: {}", e.getMessage());
        return false;
    } catch (IllegalArgumentException e) {  // 捕获另一种类型的异常
        log.warn("Token 为空");
        return false;
    }
}
```

- `try` 块中的代码如果抛出异常，立即跳到对应的 `catch` 块。
- 多个 `catch` 块按顺序匹配，匹配到第一个合适的就执行。
- `e.getMessage()` 获取异常的描述信息。
- `{}` 在 `log.warn()` 中是占位符，会被后面的参数替换（类似 `String.format`）。

---

## 7. 动手练习建议

### 练习 1：画出完整的登录流程图

拿出纸笔，画出从"用户点击登录按钮"到"前端收到 Token"的完整数据流图。标注每一步涉及的类和方法：

```
用户输入 → LoginRequest → AuthController → AuthService → UserMapper（查数据库）
                                                  → PasswordEncoder（验密码）
                                                  → JwtTokenProvider（生成 Token）
                                                  → AuthResponse（封装返回）
                         ← Result<AuthResponse>
```

> **参考答案**
>
> ```mermaid
> sequenceDiagram
>     participant 浏览器
>     participant AuthController
>     participant AuthService
>     participant UserMapper
>     participant PasswordEncoder
>     participant JwtTokenProvider
>
>     浏览器->>AuthController: POST /api/auth/login<br/>LoginRequest(username, password)
>     AuthController->>AuthService: login(request)
>
>     rect rgb(240, 248, 255)
>         Note over AuthService,UserMapper: 第一步：查询用户
>         AuthService->>UserMapper: selectOne(username)
>         UserMapper-->>AuthService: User 对象 或 null
>     end
>
>     alt 用户不存在
>         AuthService-->>AuthController: 抛出 BusinessException(INVALID_CREDENTIALS)
>         AuthController-->>浏览器: Result.error("用户名或密码错误")
>     else 用户存在，继续验证
>         rect rgb(255, 248, 240)
>             Note over AuthService,PasswordEncoder: 第二步：验证密码
>             AuthService->>PasswordEncoder: matches(明文密码, 数据库密文)
>             PasswordEncoder-->>AuthService: true / false
>         end
>
>         alt 密码不匹配
>             AuthService-->>AuthController: 抛出 BusinessException(INVALID_CREDENTIALS)
>             AuthController-->>浏览器: Result.error("用户名或密码错误")
>         else 密码正确，检查状态
>             alt 用户已被封禁
>                 AuthService-->>AuthController: 抛出 BusinessException(USER_BANNED)
>                 AuthController-->>浏览器: Result.error("账号已被封禁")
>             else 用户正常，生成 Token
>                 rect rgb(240, 255, 240)
>                     Note over AuthService,JwtTokenProvider: 第三步：生成令牌
>                     AuthService->>JwtTokenProvider: generateAccessToken(userId, username, role)
>                     JwtTokenProvider-->>AuthService: accessToken 字符串
>                     AuthService->>JwtTokenProvider: generateRefreshToken(userId, username, role)
>                     JwtTokenProvider-->>AuthService: refreshToken 字符串
>                 end
>
>                 rect rgb(255, 255, 240)
>                     Note over AuthService: 第四步：构建响应
>                     AuthService-->>AuthService: AuthResponse.builder()<br/>.accessToken(...)<br/>.refreshToken(...)<br/>.username(...)<br/>.role(...)<br/>.build()
>                 end
>
>                 AuthService-->>AuthController: 返回 AuthResponse
>                 AuthController-->>浏览器: Result.success(AuthResponse)
>             end
>         end
>     end
> ```
>

### 练习 2：理解"合并错误信息"的安全意义

尝试修改 `AuthService.login()` 方法，把"用户不存在"和"密码错误"分成两个不同的错误码，然后用 curl 或 Postman 发送请求。观察不同的错误响应，体会为什么合并错误信息能防止用户名枚举攻击。

**思考：** 哪些场景下你希望给用户更精确的错误提示？哪些场景下故意模糊更好？

### 练习 3：手动解码一个 JWT

使用在线工具（如 jwt.io）把项目中生成的 Token 粘贴进去，观察：

1. Header 部分：`alg` 和 `typ` 的值是什么？
2. Payload 部分：`sub`、`username`、`role`、`iat`、`exp` 分别是什么？
3. 把 `role` 的值从 `USER` 改成 `ADMIN`，观察签名验证是否通过？

通过这个练习，你会直观地感受到：JWT 的内容是**可读的**（不是加密的），但**不可篡改的**（改了签名就对不上）。

### 练习 4：跟踪代码执行路径

在以下方法中打上断点（如果你使用 IDE 的话），然后用 Postman 发送一个登录请求，一步步跟踪代码的执行路径：

1. `AuthController.login()` —— 入口
2. `AuthService.login()` —— 业务逻辑
3. `JwtTokenProvider.generateAccessToken()` —— Token 生成
4. `JwtTokenProvider.buildToken()` —— Token 构建

观察 `userId`、`username`、`role` 这些值是如何从数据库一步步传递到最终的 JWT 字符串中的。

### 练习 5：思考题

1. 如果密钥（`jwt.secret`）泄露了，会有什么后果？攻击者能做什么？
2. 为什么 Access Token 和 Refresh Token 使用同一个密钥签名？如果用不同的密钥，有什么好处和坏处？
3. `generateAuthResponse()` 方法同时生成了两个 Token。如果只生成 Access Token 不生成 Refresh Token，系统会变成什么样？

---

## 小结

本阶段我们学习了：

- **认证 vs 授权**：认证是"你是谁"，授权是"你能做什么"。
- **Session vs Token**：Token 方案无状态，更适合前后端分离和分布式部署。
- **JWT 的结构**：Header.Payload.Signature，像一张带钢印的工作证。
- **双 Token 机制**：Access Token 短期高频使用，Refresh Token 长期低频使用，平衡安全性和用户体验。
- **BCrypt 密码验证**：不是解密后比较，而是用同样的盐值重新哈希后比较。
- **登录流程的完整链路**：LoginRequest → AuthController → AuthService（查用户、验密码、检查封禁、生成 Token）→ AuthResponse。
- **用户名枚举攻击防护**：用户不存在和密码错误返回同一个错误码。
- **ErrorCode 枚举 + BusinessException**：统一的错误处理体系。
- **JwtTokenProvider**：JWT 生成（Builder 模式）、解析（Claims）、验证（try-catch）。

下一阶段，我们将学习 Spring Security 的过滤器链——了解 JWT Token 是如何在每次请求中被自动验证的，以及如何保护需要登录才能访问的接口。
