# 阶段 05：Token 黑名单与 Redis

> 前置知识：阶段 01-04（项目骨架、注册、登录 JWT、Spring Security 过滤器链）
>
> 本阶段你将学到：
> - Redis 是什么，为什么我们的项目需要它
> - Token 黑名单机制的设计思路
> - logout 和 refresh 流程中 Redis 是如何被使用的
> - 一些关键的 Java 语法小技巧

---

## 一、前置知识：Key-Value 存储与缓存

### 1.1 什么是 Key-Value 存储？

你已经在用 MySQL 了。MySQL 是一个**关系型数据库**，数据存放在"表"里，有行有列，可以用复杂的 SQL 查询。

但是有一种更简单的数据存储方式：**Key-Value（键值对）存储**。

类比一下：
- MySQL 像一个**档案柜**，每个档案有编号、姓名、日期、部门等字段，你可以按任何字段查找
- Key-Value 存储像一个**便签板**，每张便签上只有一个标签（Key）和一句话（Value），你只能通过标签找到对应的便签

```
Key-Value 示例：
┌──────────────────────────┬─────────┐
│          Key             │  Value  │
├──────────────────────────┼─────────┤
│ jwt:blacklist:eyJhbG... │   "1"   │
│ jwt:blacklist:eyJzdW... │   "1"   │
│ user:token:abc123        │  "42"   │
└──────────────────────────┴─────────┘
```

它简单到极致：给一个 Key，返回一个 Value。没有表、没有列、没有 JOIN。

你可能会问：这么简单的东西有什么用？别急，接着看。

### 1.2 什么是"缓存"？

想象一个场景：你有一个经常被查询的数据（比如"某个 Token 是否已被注销"），每次都去 MySQL 查太慢了。能不能把结果临时放在一个**更快**的地方？

**缓存**就是"把数据临时放在更快的地方"。

类比：
- MySQL（硬盘）= 家里的书架，容量大但找书慢
- Redis（内存）= 你桌上的便签板，容量小但看一眼就知道

当你需要频繁检查某个数据是否存在时，把数据放在内存里比放在硬盘上快得多（内存读取速度大约是硬盘的 100 倍以上）。

---

## 二、概念讲解

### 2.1 Redis 是什么？

**Redis**（Remote Dictionary Server，远程字典服务器）是一个开源的、基于内存的 Key-Value 数据库。

用最通俗的话说：Redis 就是一个跑在服务器上的、超快的、支持过期时间的"大号 HashMap"。

```
你写的 Java 程序        Redis 服务器
┌──────────┐           ┌──────────────────────┐
│          │ ──SET──→  │  "jwt:blacklist:xxx" │
│          │           │  → "1"               │
│          │           │                      │
│          │ ──GET──→  │  "jwt:blacklist:xxx"  │
│          │ ←──"1"──  │                      │
│          │           │                      │
│          │ ──HAS──→  │  "jwt:blacklist:yyy"  │
│          │ ←─false── │  (不存在)             │
└──────────┘           └──────────────────────┘
```

**Redis vs MySQL 对比：**

| 对比项 | MySQL | Redis |
|--------|-------|-------|
| 数据存放 | 硬盘 | 内存 |
| 速度 | 较慢（毫秒级） | 极快（微秒级） |
| 数据结构 | 表（行、列） | Key-Value（键值对） |
| 查询能力 | SQL，支持复杂查询 | 只能通过 Key 查找 |
| 持久性 | 断电不丢数据 | 默认断电会丢（可配置持久化） |
| 适用场景 | 存储核心业务数据 | 缓存、临时数据、会话 |

我们的项目两者都用：MySQL 存用户、书签等重要数据；Redis 存 Token 黑名单这种临时数据。

### 2.2 为什么 Token 黑名单需要 Redis？

还记得阶段 03 中讲过 JWT 的核心特性吗？**无状态（Stateless）**。

服务器签发 JWT 之后，就不再存储任何关于这个 Token 的信息。每次请求，服务器只靠 Token 自身的签名和过期时间来判断它是否有效。

这带来一个大问题：

> **如果用户主动点了"退出登录"，在 Token 过期之前，它依然是有效的！**

举个例子：
1. 用户 Alice 登录，拿到了一个有效期 24 小时的 Access Token
2. Alice 在第 1 小时点了"退出登录"
3. 但是这个 Token 在接下来的 23 小时内，签名依然正确、过期时间还没到
4. 如果有人（比如 Alice 的室友）拿到了这个 Token，它可以照常使用！

**黑名单机制**就是为了解决这个问题：

```
正常请求：
  Token 有效 → 通过 → 访问资源

加了黑名单之后：
  Token 有效？ → 是 → Token 在黑名单中？ → 否 → 通过 → 访问资源
                      → 是 → 拒绝！返回 401
```

把"已注销的 Token"记录在 Redis 中，每次验证 Token 时多检查一步：**这个 Token 在不在黑名单里？**

为什么不用 MySQL 存黑名单？
- 每次 API 请求都要查一次黑名单（检查 Token 是否被注销）
- MySQL 查一次大约 1-5ms，Redis 查一次大约 0.01-0.1ms
- 一个高并发的接口，这个差距会被放大 100 倍

所以：**频繁读取的临时数据，用 Redis；持久化的核心数据，用 MySQL。**

### 2.3 TTL（Time To Live）—— 过期时间

Redis 有一个非常实用的特性：**可以给每个 Key 设置过期时间（TTL）**。超过这个时间，Key 会自动被 Redis 删除。

在 Token 黑名单场景中，TTL 非常关键：

```
用户 Alice 的 Token 过期时间是今天 24:00
她在 12:00 点了退出登录

我们把 Token 加入黑名单，TTL 设为 12 小时（剩余有效时间）

效果：
  12:00 ~ 24:00  → 黑名单中有这个 Token → 这个 Token 无法使用
  24:00 之后     → 黑名单自动清除（因为 Token 本身也过期了，没必要再留着）
```

**为什么黑名单的 TTL 要设为 Token 的剩余有效时间？**

1. **节省内存**：Token 过期后，黑名单记录也没用了，自动删除不占空间
2. **精确同步**：黑名单的"有效期"和 Token 的"有效期"完全一致，不多不少

类比：Token 就像一张**电影票**（有效期到电影结束），黑名单就像**退票记录**。退票后这张票即使还没到期也不能用了。**电影结束后（Token 过期），退票记录也没用了，自动清理掉。**

### 2.4 Refresh Token 的"一次性使用"（One-Time Use）

我们的项目使用**双 Token 机制**（Access Token + Refresh Token）。Refresh Token 的有效期很长（7 天），用来在 Access Token 过期后换一个新的。

但这里有一个安全隐患：

> 如果 Refresh Token 可以重复使用，那攻击者截获一个 Refresh Token 后，就可以无限次地获取新的 Access Token！

所以我们的设计是：**每个 Refresh Token 只能用一次，用完即废。**

```
Refresh 流程：
1. 用户拿着 Refresh Token A 来换新 Token
2. 服务器验证 A 有效 → 生成新的 Token 对（Access + Refresh B）
3. 把 Refresh Token A 加入黑名单（一次性使用！）
4. 返回新的 Token 对给用户

如果攻击者之后也拿着 Refresh Token A 来：
  → 服务器检查发现 A 在黑名单中 → 拒绝！
```

### 2.5 StringRedisTemplate vs RedisTemplate

Spring Data Redis 提供了两个操作 Redis 的工具类：

| 类名 | Key 类型 | Value 类型 | 适用场景 |
|------|----------|------------|----------|
| `RedisTemplate` | Object | Object | 存储复杂 Java 对象（需要序列化） |
| `StringRedisTemplate` | String | String | 只存字符串（简单、通用） |

我们的项目使用 `StringRedisTemplate`，因为黑名单的 Key 和 Value 都是简单的字符串：
- Key: `"jwt:blacklist:" + token`（字符串）
- Value: `"1"`（字符串，我们只需要知道这个 Key 存不存在，值不重要）

`StringRedisTemplate` 是 `RedisTemplate<String, String>` 的子类，不需要额外的序列化配置，开箱即用，是最简单的选择。

---

## 三、代码逐行解读

### 3.1 Redis 连接配置

先看 Redis 是怎么连上的。在 `application-dev.yml` 中：

```yaml
# 文件：src/main/resources/application-dev.yml

spring:
  data:
    redis:
      host: 192.168.8.6    # Redis 服务器的地址（开发环境指向飞牛 NAS）
      port: 6379            # Redis 的默认端口号
      key-prefix: "dev:"    # Key 前缀，区分不同环境
```

就这么简单！Spring Boot 会根据这些配置自动创建 `StringRedisTemplate` 对象，你直接注入就能用。

在 `application-prod.yml`（生产环境）中：

```yaml
# 文件：src/main/resources/application-prod.yml

spring:
  data:
    redis:
      host: redis    # Docker 容器名（在同一个 Docker 网络中，用容器名访问）
      port: 6379
```

注意生产环境的 `host` 是 `redis` 而不是 IP 地址。这是因为在 Docker 环境中，应用和 Redis 在同一个网络（`app-network`）里，可以直接用容器名互相访问。

`key-prefix: "dev:"` 是开发环境独有的配置。它的作用是在所有 Key 前面加上 `dev:` 前缀，这样开发环境和生产环境即使共用同一个 Redis 服务器，数据也不会冲突。

### 3.2 AuthController.logout() —— 登出入口

```java
// 文件：src/main/java/com/hlaia/controller/AuthController.java

@PostMapping("/logout")
@Operation(summary = "Logout and blacklist the token")
public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
    // 截取 "Bearer " 之后的部分，得到纯 Token 字符串
    String token = authHeader.substring(7);
    authService.logout(token);
    return Result.success();
}
```

逐行解读：

1. `@RequestHeader("Authorization")`：从 HTTP 请求头中获取 `Authorization` 头的值。客户端发来的格式是 `Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...`

2. `authHeader.substring(7)`：`"Bearer "` 这 7 个字符（B-e-a-r-e-r-空格）是固定前缀，截掉它们就得到纯 Token 字符串。

3. 调用 `authService.logout(token)` 把具体逻辑交给 Service 层。

4. 返回 `Result.success()`，不需要返回数据（`Void` 表示 data 为 null）。

**为什么 Token 放在请求头而不是请求体里？**
- 登出是一个简单的操作，只有一个参数（Token）
- Token 放在请求头里是 HTTP 标准做法（Authorization 头）
- 浏览器和 HTTP 客户端都支持在请求头中设置 Token

### 3.3 AuthService.logout() —— 核心黑名单逻辑

这是本阶段最重要的代码之一，请仔细看：

```java
// 文件：src/main/java/com/hlaia/service/AuthService.java

public void logout(String token) {
    // 第一步：从 Token 中获取过期时间
    Date expiration = jwtTokenProvider.getExpirationFromToken(token);

    // 第二步：计算 Token 的剩余有效时间（毫秒）
    long remainingMs = expiration.getTime() - System.currentTimeMillis();

    if (remainingMs > 0) {
        // 第三步：将 Token 存入 Redis 黑名单
        redisTemplate.opsForValue().set(
            "jwt:blacklist:" + token,  // Key：黑名单前缀 + Token 字符串
            "1",                        // Value：任意值，我们只关心 Key 是否存在
            remainingMs,                // 过期时间：Token 剩余的毫秒数
            TimeUnit.MILLISECONDS       // 时间单位：毫秒
        );
    }
    // 如果 remainingMs <= 0，说明 Token 已经过期了
    // 过期的 Token 本身就无法通过验证，不需要加黑名单
}
```

**逐步拆解：**

**第一步：获取过期时间**

```java
Date expiration = jwtTokenProvider.getExpirationFromToken(token);
```

JWT Token 的 Payload 中有一个 `exp`（Expiration）字段，记录了 Token 的过期时间。`getExpirationFromToken` 方法解析 Token 并返回这个时间。

**第二步：计算剩余毫秒数**

```java
long remainingMs = expiration.getTime() - System.currentTimeMillis();
```

- `expiration.getTime()`：返回过期时间的毫秒数（从 1970 年 1 月 1 日算起）
- `System.currentTimeMillis()`：返回当前时间的毫秒数
- 两者相减 = Token 还有多久过期（剩余毫秒数）

**第三步：存入 Redis**

```java
redisTemplate.opsForValue().set(
    "jwt:blacklist:" + token, "1", remainingMs, TimeUnit.MILLISECONDS);
```

这行代码做了什么？
- `redisTemplate.opsForValue()`：获取操作字符串 Value 的接口
- `.set(key, value, timeout, unit)`：向 Redis 存入一个 Key-Value 对，同时设置过期时间
  - Key = `"jwt:blacklist:" + token`（用 Token 本身作为 Key 的一部分，保证唯一性）
  - Value = `"1"`（任意值，我们只需要知道 Key 存不存在）
  - 过期时间 = `remainingMs` 毫秒后自动删除

为什么用 `if (remainingMs > 0)` 判断？
- 如果 Token 已经过期了（remainingMs <= 0），它本身就不能通过验证了
- 加黑名单是多余的，还浪费 Redis 内存
- 所以只对还没过期的 Token 加黑名单

### 3.4 AuthService.refresh() —— Token 刷新中的 Redis 使用

```java
// 文件：src/main/java/com/hlaia/service/AuthService.java

public AuthResponse refresh(String refreshToken) {
    // 第一步：验证 Token 的基本有效性
    if (!jwtTokenProvider.validateToken(refreshToken)) {
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    // 第二步：检查 Token 是否在黑名单中
    String blacklistKey = "jwt:blacklist:" + refreshToken;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    // 第三步：提取用户信息
    Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
    User user = userMapper.selectById(userId);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    // 第四步：将旧的 Refresh Token 加入黑名单（一次性使用）
    Date expiration = jwtTokenProvider.getExpirationFromToken(refreshToken);
    long remainingMs = expiration.getTime() - System.currentTimeMillis();
    if (remainingMs > 0) {
        redisTemplate.opsForValue().set(blacklistKey, "1", remainingMs, TimeUnit.MILLISECONDS);
    }

    // 第五步：生成新的 Token 对
    return generateAuthResponse(user);
}
```

和 `logout` 相比，`refresh` 多了两个关键操作：

**检查黑名单（第二步）：**

```java
String blacklistKey = "jwt:blacklist:" + refreshToken;
if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
    throw new BusinessException(ErrorCode.TOKEN_INVALID);
}
```

- `redisTemplate.hasKey(key)`：检查 Redis 中是否存在指定的 Key，返回 `Boolean`
- `Boolean.TRUE.equals(...)`：null 安全的比较方式（后面会详细讲）
- 如果这个 Refresh Token 已经在黑名单中，说明它已经被使用过或者用户已登出，拒绝刷新

**加入黑名单（第四步）：**

```java
Date expiration = jwtTokenProvider.getExpirationFromToken(refreshToken);
long remainingMs = expiration.getTime() - System.currentTimeMillis();
if (remainingMs > 0) {
    redisTemplate.opsForValue().set(blacklistKey, "1", remainingMs, TimeUnit.MILLISECONDS);
}
```

这和 `logout` 中的代码一模一样！逻辑也相同：把当前 Refresh Token 加入黑名单，TTL 设为剩余有效时间。

**为什么 refresh 之后要把旧的 Refresh Token 加入黑名单？**

这就是"一次性使用"的设计。流程如下：

```
1. 用户登录 → 获得 Refresh Token A

2. 用户用 A 刷新 → 服务器验证 A 有效
   → 把 A 加入黑名单
   → 生成新的 Refresh Token B 返回给用户

3. 攻击者拿着之前截获的 A 来刷新
   → 服务器检查发现 A 在黑名单中 → 拒绝！

4. 用户拿着 B 刷新 → 服务器验证 B 有效
   → 把 B 加入黑名单
   → 生成新的 Refresh Token C 返回给用户
```

每一次刷新，旧的 Token 就作废，新的 Token 生效。如果攻击者截获了某个旧 Token，它已经被加入了黑名单，无法使用。

### 3.5 JwtAuthFilter 中的黑名单检查

每次 API 请求，`JwtAuthFilter` 都会执行。在验证 Token 的过程中，有一段黑名单检查的代码：

```java
// 文件：src/main/java/com/hlaia/security/JwtAuthFilter.java

// 构造黑名单 Key
String blacklistKey = "jwt:blacklist:" + token;

// 检查 Redis 中是否存在这个 Key
Boolean isBlacklisted = stringRedisTemplate.hasKey(blacklistKey);

if (Boolean.TRUE.equals(isBlacklisted)) {
    // Token 在黑名单中 → 用户已登出
    log.warn("Token 已被加入黑名单（用户已登出）");
    // 不设置认证信息，请求会被 Spring Security 拦截返回 401
} else {
    // Token 不在黑名单中 → 正常提取用户信息，设置认证
    Long userId = jwtTokenProvider.getUserIdFromToken(token);
    String role = jwtTokenProvider.getRoleFromToken(token);
    // ... 创建认证对象，设置到 SecurityContextHolder
}
```

这段代码在过滤器链中的位置（回顾阶段 04）：

```
HTTP 请求 → JwtAuthFilter.doFilterInternal()
                ↓
            1. 提取 Token
            2. 验证 Token 签名和过期时间
            3. ★ 检查黑名单（本节重点）★
            4. 提取用户信息
            5. 设置认证信息
                ↓
            Controller
```

关键方法：`stringRedisTemplate.hasKey(key)`

- 功能：检查 Redis 中是否存在指定的 Key
- 返回值：`Boolean`（注意：可能为 `null`！这是 Redis 客户端的特殊行为）
- 时间复杂度：O(1)，无论 Redis 中有多少数据，检查速度都一样快

### 3.6 getExpirationFromToken() —— 获取 Token 过期时间

```java
// 文件：src/main/java/com/hlaia/security/JwtTokenProvider.java

public Date getExpirationFromToken(String token) {
    return parseToken(token).getExpiration();
}
```

这个方法非常简洁：
1. `parseToken(token)`：解析 JWT Token，返回 Claims 对象（包含 Token 中的所有字段）
2. `.getExpiration()`：从 Claims 中取出 `exp`（过期时间）字段，返回 `Date` 对象

`Date` 对象的 `getTime()` 方法返回从 1970 年 1 月 1 日 00:00:00 UTC 到该时间点的毫秒数（时间戳），这正是我们计算剩余毫秒数时需要的东西。

---

## 四、关键 Java 语法点

### 4.1 TimeUnit 枚举

```java
import java.util.concurrent.TimeUnit;

// TimeUnit 是一个枚举类，表示时间单位
TimeUnit.MILLISECONDS  // 毫秒（1/1000 秒）
TimeUnit.SECONDS       // 秒
TimeUnit.MINUTES       // 分钟
TimeUnit.HOURS         // 小时
TimeUnit.DAYS          // 天
```

`TimeUnit` 在本项目中用于告诉 Redis："这个过期时间的单位是毫秒"。

```java
// 如果 remainingMs 是毫秒数：
redisTemplate.opsForValue().set(key, value, remainingMs, TimeUnit.MILLISECONDS);

// 如果你想用秒来计算，也可以：
long remainingSeconds = remainingMs / 1000;
redisTemplate.opsForValue().set(key, value, remainingSeconds, TimeUnit.SECONDS);
```

**为什么方法需要接收时间单位参数？**
因为 Java 不能"知道"你传入的数字是毫秒还是秒。比如 `1000` 是 1000 毫秒还是 1000 秒？必须通过参数明确指定。

`TimeUnit` 还提供了方便的时间转换方法：

```java
TimeUnit.HOURS.toMillis(1)      // 1 小时 = 3600000 毫秒
TimeUnit.DAYS.toSeconds(7)      // 7 天 = 604800 秒
TimeUnit.MINUTES.toMillis(30)   // 30 分钟 = 1800000 毫秒
```

### 4.2 Date.getTime() 和时间计算

```java
Date expiration = jwtTokenProvider.getExpirationFromToken(token);
long remainingMs = expiration.getTime() - System.currentTimeMillis();
```

**`Date.getTime()` 的返回值是什么？**

它返回一个 `long` 类型的整数，表示从"Unix 纪元"（1970 年 1 月 1 日 00:00:00 UTC）到该时间点的毫秒数。这个数字在 2025 年大约是 `1,740,000,000,000`（1.74 万亿）。

**为什么用 `long` 而不是 `int`？**

`int` 的最大值是约 21 亿（2,147,483,647），而当前时间戳的毫秒数已经超过了 1.7 万亿，远超 `int` 的范围。所以时间相关计算必须用 `long`。

**时间计算的基本模式：**

```java
// 计算两个时间点之间的差值
long diff = endTime.getTime() - startTime.getTime();  // 结果是毫秒数

// 转换为更友好的单位
long diffSeconds = diff / 1000;                        // 秒
long diffMinutes = diff / (1000 * 60);                 // 分钟
long diffHours   = diff / (1000 * 60 * 60);            // 小时
```

### 4.3 Boolean.TRUE.equals() 的 null 安全用法

在 `JwtAuthFilter` 和 `AuthService` 中，你可能注意到了这种写法：

```java
// 不要这样写：
if (stringRedisTemplate.hasKey(blacklistKey)) { ... }

// 要这样写：
if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) { ... }
```

**为什么？**

`stringRedisTemplate.hasKey(key)` 的返回值是 `Boolean`（包装类型），而不是 `boolean`（基本类型）。在某些情况下（比如 Redis 连接异常），它可能返回 `null`。

如果我们直接写 `if (hasKey(...))`：
- Java 会尝试把 `Boolean` 自动拆箱（unboxing）为 `boolean`
- 如果值为 `null`，拆箱会抛出 `NullPointerException`！

`Boolean.TRUE.equals(null)` 会返回 `false`，不会抛异常。所以：

| `hasKey()` 返回值 | 直接 `if (hasKey(...))` | `Boolean.TRUE.equals(hasKey(...))` |
|---|---|---|
| `true` | `true` | `true` |
| `false` | `false` | `false` |
| `null` | **NullPointerException！** | `false`（安全） |

**记住这个模式：当你处理可能为 `null` 的 `Boolean` 时，用 `Boolean.TRUE.equals()`。**

### 4.4 字符串拼接和 substring

本项目中的两种字符串操作：

**字符串拼接（构建 Redis Key）：**

```java
String blacklistKey = "jwt:blacklist:" + token;
```

用 `+` 号拼接字符串是最简单的方式。当拼接次数少（只有 2 次）时，用 `+` 完全没问题。

注意：如果在循环中大量拼接字符串，应该用 `StringBuilder`，因为 `+` 号每次拼接都会创建新对象。但在我们这个场景中只拼接一次，用 `+` 号是最简洁的选择。

**substring（截取 Token）：**

```java
String token = authHeader.substring(7);
```

`substring(7)` 表示从索引 7 开始截取到字符串末尾。

```
索引：  0  1  2  3  4  5  6  7  8  9 ...
字符：  B  e  a  r  e  r  _  e  y  J ...
                             ↑
                        索引 7 开始截取
```

`"Bearer "` 正好是 7 个字符（B、e、a、r、e、r、空格），所以 `substring(7)` 截取出来的就是纯 Token 字符串。

---

## 五、完整流程图

### 5.1 登出（Logout）流程

```
用户点击"退出登录"
       ↓
前端发送 POST /api/auth/logout
请求头：Authorization: Bearer eyJhbG...
       ↓
AuthController.logout()
  → authHeader.substring(7) → 得到纯 Token
  → 调用 authService.logout(token)
       ↓
AuthService.logout(token)
  → jwtTokenProvider.getExpirationFromToken(token) → 获取过期时间
  → expiration.getTime() - System.currentTimeMillis() → 计算剩余毫秒
  → redisTemplate.opsForValue().set("jwt:blacklist:" + token, "1", remainingMs, MILLISECONDS)
       ↓
Redis 中新增一条记录：
  Key:   jwt:blacklist:eyJhbG...
  Value: "1"
  TTL:   Token 剩余有效时间
       ↓
返回成功响应

之后，任何使用这个 Token 的请求：
  → JwtAuthFilter 检查发现 Token 在黑名单中
  → 拒绝访问，返回 401
```

### 5.2 刷新（Refresh）流程

```
前端发现 Access Token 过期
  → 发送 POST /api/auth/refresh?refreshToken=eyJhbG...
       ↓
AuthController.refresh(refreshToken)
       ↓
AuthService.refresh(refreshToken)
  → jwtTokenProvider.validateToken(refreshToken)     → 验证基本有效性
  → redisTemplate.hasKey("jwt:blacklist:" + token)    → 检查黑名单
  → 如果在黑名单 → 抛出 TOKEN_INVALID 异常
  → jwtTokenProvider.getUserIdFromToken(token)         → 提取用户 ID
  → userMapper.selectById(userId)                      → 查询用户
  → redisTemplate.set("jwt:blacklist:" + token, "1", ...) → 旧 Token 加入黑名单
  → generateAuthResponse(user)                         → 生成新的 Token 对
       ↓
返回新的 Access Token 和 Refresh Token
```

---

## 六、动手练习建议

### 练习 1：观察 Redis 数据

如果你本地装了 Redis（或者可以连接飞牛 NAS 的 Redis），用 `redis-cli` 连接后尝试：

```bash
# 连接 Redis
redis-cli -h 192.168.8.6 -p 6379

# 查看所有黑名单 Key
KEYS jwt:blacklist:*

# 查看某个 Key 的剩余过期时间（秒）
TTL jwt:blacklist:eyJhbG...

# 查看某个 Key 的值
GET jwt:blacklist:eyJhbG...
```

在项目运行后，调用 logout 接口，然后观察 Redis 中是否出现了对应的 Key。

### 练习 2：追踪一次完整的登出请求

1. 先调用登录接口获取 Token
2. 用这个 Token 调用 logout 接口
3. 再用同一个 Token 调用任何需要认证的接口
4. 观察返回结果应该是 401（未授权）

思考：如果在步骤 2 和步骤 3 之间等待很长时间（超过 Token 的有效期），步骤 3 会返回什么？为什么？

### 练习 3：理解 TTL 的意义

思考题：

> 假设我们把黑名单的 TTL 设为永不过期（不设置 TTL），会发生什么？

提示：
1. Redis 的内存会怎样？
2. 随着用户不断登出，Redis 中会积累多少无用的 Key？
3. Token 已经过期了，黑名单中的记录还有意义吗？

### 练习 4：代码阅读挑战

阅读 `AuthService.refresh()` 方法，回答以下问题：

1. 为什么要先验证 Token（第一步），再检查黑名单（第二步）？反过来行不行？
2. 如果用户连续快速调用两次 refresh 接口，用同一个 Refresh Token，会发生什么？
3. 第四步把旧 Token 加入黑名单之后，为什么不需要检查 `remainingMs > 0`？（提示：第一步已经验证了 Token 没过期）

---

## 七、小结

本阶段你学到了：

| 概念 | 要点 |
|------|------|
| Redis | 基于内存的 Key-Value 数据库，适合存频繁读取的临时数据 |
| Token 黑名单 | 解决 JWT 无状态特性带来的"无法主动注销"问题 |
| TTL | 给 Redis Key 设置过期时间，过期自动删除，节省内存 |
| 一次性使用 | Refresh Token 用完即废，防止 Token 被截获后无限使用 |
| StringRedisTemplate | Spring 提供的 Redis 操作工具，Key 和 Value 都是 String |
| Boolean.TRUE.equals() | null 安全的 Boolean 比较方式 |
| Date.getTime() | 获取时间戳（毫秒），用于计算时间差 |

**下一阶段预告**：阶段 06 将进入新的功能模块——书签文件夹的树形结构设计，我们会学习数据库中的邻接表模型和 MyBatis-Plus 的树形查询。

---

*本文档基于 HLAIANavigationBar 项目源码编写，文件路径：*
- *`src/main/java/com/hlaia/service/AuthService.java`*
- *`src/main/java/com/hlaia/controller/AuthController.java`*
- *`src/main/java/com/hlaia/security/JwtAuthFilter.java`*
- *`src/main/java/com/hlaia/security/JwtTokenProvider.java`*
- *`src/main/resources/application-dev.yml`*
- *`src/main/resources/application-prod.yml`*
