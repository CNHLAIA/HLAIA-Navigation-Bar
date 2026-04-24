# 第九课：全局异常处理与 AOP

> 学完这课，你会理解 Java 异常体系、全局异常处理器的工作原理、AOP（面向切面编程）的核心概念、自定义注解的定义与使用，以及限流和操作日志两大切面的实现。

---

## 目录

1. [前置知识](#1-前置知识)
2. [概念讲解](#2-概念讲解)
3. [代码逐行解读](#3-代码逐行解读)
4. [关键 Java 语法点](#4-关键-java-语法点)
5. [动手练习建议](#5-动手练习建议)

---

## 1. 前置知识

在进入全局异常处理和 AOP 之前，我们需要先复习两个基础概念：Java 异常体系和面向对象中的继承。别担心，它们并不难。

### 1.1 你应该已经会的东西

- **CRUD 操作**：知道增删改查怎么写（阶段 01-07 学过的）
- **JWT 认证**：了解如何通过 Token 识别当前用户（阶段 03-05）
- **Redis 基础**：知道 Redis 可以用来存键值对、设过期时间（阶段 05）
- **Spring Boot 注解基础**：`@RestController`、`@Service`、`@Component` 等
- **try-catch**：写过基本的异常捕获代码

### 1.2 Java 异常体系回顾

在之前的课程中，你可能见过 `throw new BusinessException(ErrorCode.USER_NOT_FOUND)` 这样的代码。为什么 Java 要设计异常机制？异常又是怎么分类的？

想象你在快递站寄包裹：

```
Throwable（所有"不正常情况"的总称）
├── Error（严重故障 —— 快递站塌了，你处理不了）
│   ├── OutOfMemoryError        内存耗尽
│   └── StackOverflowError      栈溢出（递归太深）
│
└── Exception（一般性问题 —— 包裹地址写错了，你可以处理）
    ├── RuntimeException（非受检异常 —— 你不强制要求客户填手机号）
    │   ├── NullPointerException       空指针
    │   ├── IllegalArgumentException   非法参数
    │   └── BusinessException          ← 我们自定义的！
    │
    └── 其他 Exception（受检异常 —— 你强制要求客户签收）
        ├── IOException                输入输出异常
        └── SQLException               数据库异常
```

**受检异常 vs 非受检异常**，这是 Java 初学者最容易混淆的概念：

| | 受检异常（Checked Exception） | 非受检异常（RuntimeException） |
|---|---|---|
| 编译器检查 | 强制要求 try-catch 或 throws 声明 | 不强制，可以不处理 |
| 代表什么 | 可预期的外部问题（文件找不到、网络断开） | 程序逻辑错误（空指针、参数非法） |
| 处理策略 | 在当前方法处理或声明抛出 | 修复代码，或让全局处理器兜底 |
| 举例 | `IOException`、`SQLException` | `NullPointerException`、`BusinessException` |

为什么 `BusinessException` 继承 `RuntimeException`？因为业务异常（如"用户不存在"）属于"调用方应该知道的逻辑问题"，我们**不希望**每个方法都写 `throws BusinessException`，而是让它自动向上传递，直到被全局异常处理器捕获。

### 1.3 面向对象中的继承回顾

在之前的学习中，你已经见过 `BaseMapper<Bookmark>` 这样的泛型继承。现在我们需要理解**类继承**（extends）：

```java
// 父类：定义通用的行为
class Animal {
    void eat() { System.out.println("吃东西"); }
}

// 子类：继承父类，可以扩展新的行为
class Dog extends Animal {
    void bark() { System.out.println("汪汪汪"); }
    // 自动拥有 eat() 方法，不需要重写
}
```

在本阶段，`BusinessException extends RuntimeException` 就是这样的关系：
- `RuntimeException` 提供了 `getMessage()` 等通用方法
- `BusinessException` 在此基础上增加了 `code` 字段，携带错误码

---

## 2. 概念讲解

### 2.1 全局异常处理器：系统的"急诊科"

#### 一个生活中的类比

想象一家大医院：

```
普通门诊（Controller 方法）
├── 内科医生正在看诊
├── 外科医生正在做手术
└── 眼科医生正在检查
        ↓ 突然出了问题！
急诊科（GlobalExceptionHandler）
├── 内科病人过敏了 → 过敏急诊室（handleBusinessException）
├── 病人挂错号了 → 分诊台（handleValidation）
└── 未知突发状况 → 抢救室（handleException —— 兜底处理）
```

不管哪个科室出了问题，最终都会送到急诊科统一处理。**全局异常处理器就是你的"急诊科"**——Controller 抛出的任何异常，都会被它捕获并转为统一的 JSON 响应返回给前端。

#### 没有全局异常处理器时的问题

```java
@GetMapping("/users/{id}")
public Result<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    if (user == null) {
        // 没有 GlobalExceptionHandler 时，你需要这样写：
        return Result.error(404, "User not found");
    }
    // 如果这里抛出 NullPointerException，前端会收到一段丑陋的 HTML 错误页面
    return Result.success(user);
}
```

问题：
1. **每个方法都要写错误处理**：几十个接口就要写几十次
2. **容易遗漏**：忘了处理某个异常，前端就会看到默认的错误页面
3. **格式不统一**：有的接口返回 `Result.error()`，有的直接抛异常，前端不知道该怎么解析

#### 有全局异常处理器后

```java
@GetMapping("/users/{id}")
public Result<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        // 抛出异常，GlobalExceptionHandler 自动捕获并返回标准 JSON
    }
    return Result.success(user);
}
```

开发者只需要 `throw`，不用关心异常怎么被处理、怎么返回给前端。**专注于业务逻辑，把异常处理交给全局处理器**。

#### 两个关键注解

- **`@RestControllerAdvice`**：告诉 Spring "这个类是全局异常处理器，负责拦截所有 Controller 的异常"
- **`@ExceptionHandler(XXXException.class)`**：标记一个方法，指定它能处理哪种类型的异常

工作流程：

```
Controller 方法抛出 BusinessException
        ↓
Spring 搜索 GlobalExceptionHandler
        ↓
找到 @ExceptionHandler(BusinessException.class) 标记的方法
        ↓
调用该方法，返回 Result JSON 给前端
```

### 2.2 AOP（面向切面编程）：系统的"安检员"

#### 什么是 AOP？

AOP 全称 **A**spect-**O**riented **P**rogramming，翻译为"面向切面编程"。它是 OOP（面向对象编程）的补充，用来解决**横切关注点**（Cross-Cutting Concern）的问题。

#### 一个生活中的类比 —— 安检员

想象你管理一栋办公大楼，有很多办公室（Controller 方法），每个办公室都有人在办公（业务逻辑）。

```
不用 AOP（没有安检员）：
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ 办公室 A     │  │ 办公室 B     │  │ 办公室 C     │
│             │  │             │  │             │
│ [业务代码]   │  │ [业务代码]   │  │ [业务代码]   │
│ [限流代码]   │  │ [限流代码]   │  │ [限流代码]   │  ← 重复！
│ [日志代码]   │  │ [日志代码]   │  │ [日志代码]   │  ← 重复！
└─────────────┘  └─────────────┘  └─────────────┘
  问题：每新增一个办公室，都要重新写限流和日志代码

用 AOP（设置安检员）：
                    ┌────────────────────────┐
                    │     安检员（AOP 切面）    │
                    │  1. 检查访问频率（限流）  │
                    │  2. 记录出入日志         │
                    └──────────┬─────────────┘
                               │ 不管进哪个办公室
                    ┌──────────┼─────────────┐
                    ↓          ↓             ↓
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ 办公室 A     │  │ 办公室 B     │  │ 办公室 C     │
│             │  │             │  │             │
│ [业务代码]   │  │ [业务代码]   │  │ [业务代码]   │  ← 干净！
│             │  │             │  │             │
└─────────────┘  └─────────────┘  └─────────────┘
  优势：新开办公室自动享受安检服务
```

**安检员 = AOP 切面**。不管谁进大楼（什么 Controller 方法），都要先过安检（切面逻辑）。开发者只需要在办公室里办公（写业务代码），安检的事情交给切面自动处理。

#### AOP 的核心概念

理解 AOP 需要掌握四个术语。不要被英文吓到，用类比就很好懂：

| 术语 | 英文 | 含义 | 类比 |
|---|---|---|---|
| **切面** | Aspect | 封装横切关注点的类 | 安检员（包含安检规则和安检动作） |
| **切点** | Pointcut | 定义"在哪里拦截" | 大楼入口（规定哪些地方需要安检） |
| **通知** | Advice | 定义"拦截后做什么" | 安检的具体动作（查证件、量体温） |
| **连接点** | Join Point | 被拦截到的那个方法执行 | 正在过安检的某个人 |

#### 五种通知类型

通知决定了切面逻辑在什么时候执行。Spring 提供了五种通知类型：

```
方法执行的时间线：

    @Before            proceed()           @AfterReturning        @After
    前置通知       ───→ 目标方法执行 ───→    返回通知           ───→ 最终通知
    （进门查证件）        │                  （成功后发通行证）      （离开时关灯）
                         │
                    如果抛异常 ↓
                         │
                    @AfterThrowing
                    异常通知
                   （出事故叫救护车）

    @Around（环绕通知）可以包裹以上整个过程：
    ┌─── @Around 前半部分 ───┬─── 目标方法 ───┬─── @Around 后半部分 ───┐
    │   （收费站：看证件）   │  （通行）      │   （收费站：抬杆放行） │
    └───────────────────────┴────────────────┴───────────────────────┘
```

| 通知类型 | 执行时机 | 常见用途 | 能否阻止方法执行 |
|---|---|---|---|
| `@Before` | 方法执行之前 | 参数校验、权限检查 | 不能 |
| `@AfterReturning` | 方法正常返回之后 | 记录成功日志 | 不能 |
| `@AfterThrowing` | 方法抛出异常时 | 异常告警 | 不能 |
| `@After` | 方法执行之后（无论成功/失败） | 资源清理 | 不能 |
| `@Around` | 包裹整个方法执行 | 限流、缓存、性能监控 | **能**（不调用 proceed 即可） |

本项目使用 `@Around`（环绕通知），因为它最强大——可以决定是否执行目标方法。限流场景需要这个能力：如果请求太频繁，直接抛异常，不执行 Controller 方法。

### 2.3 自定义注解：声明式编程

#### 什么是注解（Annotation）？

你已经在项目中见过很多注解了：

```java
@RestController    // 告诉 Spring：这是一个 REST 控制器
@Service           // 告诉 Spring：这是一个业务逻辑类
@Override          // 告诉编译器：这个方法是重写父类的
@Transactional     // 告诉 Spring：这个方法需要事务管理
```

注解的本质是**一种标记**，就像便利贴。你把便利贴贴在方法或类上，其他代码（如 Spring 框架、AOP 切面）看到这个便利贴就知道该做什么。

#### 自定义注解的意义 —— 声明式编程

编程风格有两种：

| 风格 | 含义 | 举例 |
|---|---|---|
| **命令式** | 告诉计算机"怎么做" | 手动写 if-else 判断限流 |
| **声明式** | 告诉计算机"我要什么" | 加一个 `@RateLimit` 注解就完事 |

```java
// 命令式（不用注解）—— 每个接口都要写这段代码
@GetMapping("/api/bookmarks")
public Result listBookmarks() {
    String key = "ratelimit:" + userId + ":/api/bookmarks";
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) redisTemplate.expire(key, 60, TimeUnit.SECONDS);
    if (count > 10) return Result.error(429, "Too many requests");
    // ... 真正的业务逻辑 ...
}

// 声明式（用注解）—— 加一行注解，限流逻辑由切面自动处理
@RateLimit(permits = 10, seconds = 60)
@GetMapping("/api/bookmarks")
public Result listBookmarks() {
    // 只有业务逻辑，干净清爽
}
```

声明式编程的好处：
1. **代码更简洁**：业务方法中只有业务逻辑
2. **不容易出错**：不用每次都复制粘贴限流代码
3. **统一管理**：修改限流逻辑只改切面类，不需要改每个 Controller

#### 元注解：注解的注解

定义注解时，需要用"元注解"来说明这个注解的行为：

```java
@Target(ElementType.METHOD)            // 这个注解能用在什么地方？（方法上）
@Retention(RetentionPolicy.RUNTIME)    // 这个注解能活多久？（运行时保留）
@Documented                             // 这个注解要不要出现在文档中？
public @interface RateLimit { ... }     // 定义注解
```

就像"法律的法律"——宪法规定了一般法律怎么制定，元注解规定了注解怎么定义。

### 2.4 限流（Rate Limiting）

#### 为什么需要限流？

想象一家餐厅：

```
没有限流：
  餐厅门口没有排队 → 1000 个人同时涌入 → 厨房崩溃 → 所有人都吃不上饭

有限流：
  餐厅门口设置排队叫号 → 每分钟只放 10 人进入 → 厨房正常运转 → 每个人都能吃上饭
```

在 Web 应用中也是一样的：
- **正常情况**：用户每秒发 1-2 个请求，服务器轻松应对
- **异常情况**：恶意用户用脚本每秒发 1000 个请求，服务器可能崩溃
- **限流的作用**：限制每个用户在一段时间内的请求次数，保护系统稳定

#### 固定窗口计数器算法

本项目采用的是**固定窗口计数器**（Fixed Window Counter）算法，思路非常简单：

```
时间线（60秒内允许10次请求）：

第0秒        第30秒       第60秒       第90秒
  |            |            |            |
  ↓ 请求1: count=1           ↓ key过期，count重置
  ↓ 请求2: count=2           ↓ 请求1: count=1（新一轮）
  ↓ ...                      ↓ ...
  ↓ 请求10: count=10         ↓ 请求10: count=10
  ↓ 请求11: count=11 → 拒绝！ ↓ 请求11: count=11 → 拒绝！
```

使用 Redis 实现：
1. 每次请求到来，将计数器 +1（`INCR` 命令）
2. 如果是第一次请求，设置 60 秒过期时间
3. 如果计数超过 10，拒绝请求
4. 60 秒后 key 自动过期，计数归零

这个算法有一个已知的"边界突发"问题：在第 55 秒到第 65 秒之间，理论上可以通过 20 次请求（两个窗口各 10 次）。但对于本项目来说，这个精度完全够用了。更精确的算法有滑动窗口、令牌桶等。

### 2.5 操作日志：AOP 的另一个应用

操作日志是 AOP 的典型应用场景。每当用户执行重要操作（创建书签、删除文件夹等），系统自动记录一条日志。

**为什么用 AOP 而不是手动记日志？**

```java
// 不用 AOP：每个方法都要写日志代码
public void createBookmark(...) {
    // ... 业务逻辑 ...
    logService.log(userId, "createBookmark", "BookmarkController.createBookmark");
    // 如果有 50 个方法，就要写 50 次日志代码
}

// 用 AOP：切面自动拦截所有 Controller，统一记日志
// 开发者完全不用写日志代码，切面在幕后自动工作
```

本项目更进一步：日志不是直接写数据库，而是通过 **Kafka 消息队列**异步处理。切面只负责"发送消息"，另一个消费者组件负责"写数据库"。这样日志操作不会拖慢用户请求的响应速度。

---

## 3. 代码逐行解读

现在我们来逐文件解读项目中的真实代码。建议你打开 IDE 对照阅读。

### 3.1 ErrorCode.java —— 错误码枚举

> 文件路径：`src/main/java/com/hlaia/common/ErrorCode.java`

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 标准 HTTP 错误码 ==========
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error"),

    // ========== 认证相关错误（1001-1999） ==========
    USER_EXISTS(1001, "Username already exists"),
    INVALID_CREDENTIALS(1002, "Invalid username or password"),
    TOKEN_EXPIRED(1003, "Token expired"),
    TOKEN_INVALID(1004, "Invalid token"),

    // ========== 业务相关错误（2001-2999） ==========
    FOLDER_NOT_FOUND(2001, "Folder not found"),
    BOOKMARK_NOT_FOUND(2002, "Bookmark not found"),
    STAGING_NOT_FOUND(2003, "Staging item not found"),
    USER_NOT_FOUND(2004, "User not found"),
    USER_BANNED(2005, "User is banned"),
    ACCESS_DENIED(2006, "Access denied"),
    RATE_LIMITED(2007, "Too many requests");

    private final int code;
    private final String message;
}
```

逐段解读：

**`enum` 关键字**：声明一个枚举类型。枚举是一种特殊的类，它的实例数量是固定的——在这里，实例就是 `SUCCESS`、`BAD_REQUEST`、`USER_EXISTS` 等。每个实例在声明时就确定了 `code` 和 `message` 的值。

**错误码分区设计**：

| 范围 | 含义 | 好处 |
|---|---|---|
| 200-500 | 标准 HTTP 状态码 | 前端开发者一看就知道是什么问题 |
| 1001-1999 | 认证相关 | 通过数字范围就能定位到认证模块 |
| 2001-2999 | 业务相关 | 通过数字范围就能定位到具体业务 |

这种"分区编号"的设计在大型项目中非常重要——当客服说"用户报了 2004 错误"，开发人员立刻就知道是"用户不存在"，不需要查文档。

**`@Getter`**：Lombok 注解，自动为所有字段生成 `getCode()` 和 `getMessage()` 方法。

**`@AllArgsConstructor`**：Lombok 注解，自动生成包含所有参数的构造方法。等价于手写：

```java
public ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
}
```

**为什么用枚举而不是直接用数字？**

```java
// 差：魔法数字，没人知道 2004 是什么意思
throw new BusinessException(2004, "User not found");

// 好：语义清晰，编译器还能帮你检查拼写
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```

### 3.2 BusinessException.java —— 业务异常类

> 文件路径：`src/main/java/com/hlaia/common/BusinessException.java`

```java
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());   // 调用父类 RuntimeException 的构造方法
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

逐行解读：

**`extends RuntimeException`**：继承自 `RuntimeException`，这意味着：
- 在 service 层抛出时不需要写 `throws BusinessException`
- 异常会自动向上传递，直到被 `GlobalExceptionHandler` 捕获
- 如果不小心选了 `extends Exception`（受检异常），那么 service 层的每个方法都要加 `throws` 声明，代码会变得非常啰嗦

**`private final int code`**：`final` 表示这个字段一旦赋值就不能修改。这保证了异常对象的不可变性——抛出的异常信息不会被意外篡改。

**两个构造方法**（重载）：

```java
// 方式1（推荐）：用枚举创建
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
// code = 2004, message = "User not found"

// 方式2（灵活）：直接传数字和字符串
throw new BusinessException(999, "自定义错误");
// code = 999, message = "自定义错误"
```

方式 1 更推荐，因为枚举值是集中管理的，不容易写错。方式 2 适合临时性的、不常出现的错误。

**`super(errorCode.getMessage())`**：调用父类 `RuntimeException` 的构造方法，把错误信息传递上去。这样 `e.getMessage()` 就能获取到错误信息了。

### 3.3 GlobalExceptionHandler.java —— 全局异常处理器

> 文件路径：`src/main/java/com/hlaia/common/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    // 处理请求体参数校验失败
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return Result.error(400, msg);
    }

    // 处理路径参数/查询参数校验失败
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.error(400, e.getMessage());
    }

    // 处理登录凭证错误
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(ErrorCode.INVALID_CREDENTIALS);
    }

    // 处理权限不足
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(ErrorCode.FORBIDDEN);
    }

    // 兜底：处理所有未知异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
```

逐方法解读：

#### `@RestControllerAdvice` —— 开启全局异常处理

这个注解等价于 `@ControllerAdvice` + `@ResponseBody`：
- `@ControllerAdvice`：告诉 Spring "这个类对所有 Controller 生效"
- `@ResponseBody`：返回值自动转为 JSON

#### handleBusinessException —— 处理业务异常

```java
@ExceptionHandler(BusinessException.class)  // 只捕获 BusinessException
@ResponseStatus(HttpStatus.OK)              // HTTP 状态码返回 200
public Result<Void> handleBusinessException(BusinessException e) {
    return Result.error(e.getCode(), e.getMessage());
}
```

**为什么 HTTP 状态码是 200？** 因为我们的错误信息是通过 `Result` 中的 `code` 字段传递的（如 1002、2004），而不是通过 HTTP 状态码。前端统一按 200 接收，然后检查 `Result.code` 判断成功还是失败。

这种设计的优点：所有响应的 HTTP 状态码都是 200，前端只需要解析 JSON 中的 `code` 字段，逻辑更简单。

#### handleValidation —— 处理请求体校验失败

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public Result<Void> handleValidation(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return Result.error(400, msg);
}
```

当使用 `@Valid` + `@RequestBody` 校验请求体时，如果校验失败，Spring 会抛出 `MethodArgumentNotValidException`。这段代码从异常中提取第一个字段的错误信息返回。

**Stream API 链式调用解析**：

```java
e.getBindingResult()          // 获取绑定结果（包含所有校验错误）
 .getFieldErrors()            // 获取所有字段错误列表
 .stream()                    // 转为 Stream（可以链式操作）
 .map(f -> f.getField() + ": " + f.getDefaultMessage())  // 拼接 "字段名: 错误信息"
 .findFirst()                 // 只取第一个错误
 .orElse("Validation failed") // 如果没有错误信息，用默认值
```

例如：用户注册时 `username` 为空，返回 `"username: 不能为空"`。

#### handleConstraintViolation —— 处理路径/查询参数校验失败

和上面的区别：
- `MethodArgumentNotValidException`：校验 **JSON 请求体**（`@RequestBody` + `@Valid`）
- `ConstraintViolationException`：校验 **路径参数/查询参数**（`@PathVariable`、`@RequestParam` + `@Validated`）

#### handleBadCredentials —— 处理登录失败

Spring Security 在用户名或密码错误时会抛出 `BadCredentialsException`。我们捕获它并返回"用户名或密码错误"（而不是告诉用户具体是哪个错了，这是安全最佳实践）。

#### handleAccessDenied —— 处理权限不足

当普通用户尝试访问管理员接口时，Spring Security 会抛出 `AccessDeniedException`。

#### handleException —— 兜底处理

```java
@ExceptionHandler(Exception.class)  // 匹配所有异常
public Result<Void> handleException(Exception e) {
    return Result.error(500, "Internal server error: " + e.getMessage());
}
```

这是**最后一道防线**。所有未被上面方法处理的异常（如 `NullPointerException`、数据库连接失败等）都会被这个方法捕获。

**异常匹配顺序**：Spring 从上到下匹配，先匹配具体的异常类型（如 `BusinessException`），最后才匹配 `Exception`（最宽泛的）。就像急诊科的"分诊"——先看是不是过敏，再看是不是骨折，最后"未知病因"送抢救室。

### 3.4 RateLimit.java —— 限流自定义注解

> 文件路径：`src/main/java/com/hlaia/annotation/RateLimit.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    int permits() default 10;    // 最大请求次数，默认 10
    int seconds() default 60;    // 时间窗口（秒），默认 60
}
```

逐行解读：

**`@interface` 关键字**：定义一个注解类型。和 `class`、`interface`、`enum` 一样，是 Java 的类型声明方式。注意它不是 `interface`（接口），而是 `@interface`（注解）。

**三个元注解**：

```java
@Target(ElementType.METHOD)            // 只能标注在方法上
@Retention(RetentionPolicy.RUNTIME)    // 运行时保留（AOP 需要读取）
@Documented                             // 包含在 Javadoc 中
```

`@Retention` 的三个取值对比：

| 取值 | 生命周期 | 能否运行时读取 | 举例 |
|---|---|---|---|
| `SOURCE` | 只在源代码中，编译后消失 | 不能 | `@Override` |
| `CLASS` | 存在于 .class 文件，运行时不可见 | 不能 | 默认值 |
| `RUNTIME` | 运行时存在，可通过反射读取 | **能** | `@RateLimit` |

为什么必须是 `RUNTIME`？因为 AOP 切面需要在程序运行时通过反射读取方法上的 `@RateLimit` 注解。如果是 `SOURCE` 或 `CLASS`，运行时就读不到了。

**注解属性**：

```java
int permits() default 10;
int seconds() default 60;
```

看起来像方法声明，但其实是属性。`default` 定义默认值，使用时可以省略：

```java
@RateLimit                           // 使用默认值：60秒内10次
@RateLimit(permits = 5)              // 自定义次数：60秒内5次
@RateLimit(permits = 5, seconds = 30) // 全部自定义：30秒内5次
```

### 3.5 RateLimitAspect.java —— 限流切面

> 文件路径：`src/main/java/com/hlaia/aspect/RateLimitAspect.java`

这是本阶段最核心的文件。我们分段解读。

#### 类声明和依赖注入

```java
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;
```

- **`@Aspect`**：告诉 Spring "这是一个 AOP 切面"
- **`@Component`**：注册为 Spring Bean（和 `@Service` 类似，但切面不是业务逻辑，用 `@Component` 更语义化）
- **`StringRedisTemplate`**：Redis 操作工具，用于存储和递增请求计数

为什么用 Redis 而不是 Java 本地变量（如 `AtomicInteger`）？

```
单机部署：本地变量可以工作
分布式部署（多台服务器）：
  ┌─── 服务器A ───┐   ┌─── 服务器B ───┐
  │ count = 5      │   │ count = 3      │   ← 各自计数，不准确！
  └────────────────┘   └────────────────┘
         ↓                     ↓
  ┌──────── Redis ────────┐
  │ count = 8（共享计数器）│   ← 所有服务器共享，准确！
  └───────────────────────┘
```

#### 环绕通知方法

```java
@Around("@annotation(com.hlaia.annotation.RateLimit)")
public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
```

**切点表达式**：`@annotation(com.hlaia.annotation.RateLimit)` 表示"匹配所有标注了 `@RateLimit` 注解的方法"。这是一种**注解匹配**的方式——只要有这个注解的方法，就会被拦截。

**`ProceedingJoinPoint`**：连接点对象，包含被拦截方法的所有信息：
- 可以获取方法名、参数、注解
- 可以调用 `proceed()` 执行原方法
- 可以决定是否执行原方法（不调用 `proceed()` 就不执行）

**`throws Throwable`**：因为 `proceed()` 可能抛出任何异常，所以这里声明抛出最顶层的 `Throwable`。

#### 第1步：获取注解配置

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();
RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);
```

这是**Java 反射**的应用：
1. `joinPoint.getSignature()` 获取方法签名
2. 强转为 `MethodSignature`（Spring AOP 提供的接口）
3. `getMethod()` 获取被拦截的 Method 对象
4. `getAnnotation(RateLimit.class)` 从方法上读取 `@RateLimit` 注解实例

之后就可以读取注解中配置的属性：`rateLimit.permits()` 和 `rateLimit.seconds()`。

#### 第2步：构建 Redis key

```java
String userId = getCurrentUserId();
String api = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
        .getRequest().getRequestURI();
String key = "ratelimit:" + userId + ":" + api;
```

key 格式示例：`ratelimit:42:/api/bookmarks`

为什么要包含 `userId` 和 `api`？
- 包含 `userId`：不同用户有各自独立的计数器，A 用户频繁访问不会影响 B 用户
- 包含 `api`：不同接口的计数独立，频繁访问接口 A 不影响访问接口 B

**RequestContextHolder** 是 Spring 提供的工具类，使用 `ThreadLocal` 保存当前请求的 `HttpServletRequest` 对象。在 Controller 方法中可以直接用方法参数获取 request，但在 AOP 切面中没有这个参数，所以需要通过 `RequestContextHolder` 获取。

#### 第3-4步：Redis 原子递增和过期时间

```java
Long count = redisTemplate.opsForValue().increment(key);

if (count != null && count == 1) {
    redisTemplate.expire(key, rateLimit.seconds(), TimeUnit.SECONDS);
}
```

`increment(key)` 对应 Redis 的 `INCR` 命令：
- 如果 key 不存在，创建并设值为 1，返回 1
- 如果 key 存在，值 +1，返回递增后的值

`INCR` 是原子操作——即使多个请求同时执行，Redis 也会逐个处理，不会出现并发冲突。就像银行排队叫号机，每次按一下号码 +1，不会重复。

**为什么只在 `count == 1` 时设置过期时间？**

```
假设 seconds = 60（60秒窗口）

如果每次都重新设置过期时间：
  第 1 秒：count=1，expire 60秒 → 将在第 61 秒过期
  第 59 秒：count=2，expire 60秒 → 将在第 119 秒过期！窗口被拉长了

只在 count==1 时设置过期时间：
  第 1 秒：count=1，expire 60秒 → 将在第 61 秒过期（窗口从此刻开始）
  第 59 秒：count=2，不设置 → 仍然在第 61 秒过期（窗口不变）
  第 61 秒：key 过期，计数重置 → 新窗口开始
```

#### 第5步：检查是否超过阈值

```java
if (count != null && count > rateLimit.permits()) {
    throw new BusinessException(ErrorCode.RATE_LIMITED);
}
```

注意是 `>` 而不是 `>=`：`permits = 10` 表示允许 10 次请求，第 10 次放行，第 11 次才拒绝。

#### 第6步：通过，执行目标方法

```java
return joinPoint.proceed();
```

`proceed()` 会调用被拦截的原始 Controller 方法，返回值直接透传给调用方。

#### getCurrentUserId —— 获取当前用户 ID

```java
private String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() != null) {
        return auth.getPrincipal().toString();
    }
    return "anonymous";
}
```

从 Spring Security 的 `SecurityContextHolder` 获取当前认证用户的 ID。如果用户未登录，返回 `"anonymous"`，表示未登录用户共享一个限流计数器。

### 3.6 OperationLogAspect.java —— 操作日志切面

> 文件路径：`src/main/java/com/hlaia/aspect/OperationLogAspect.java`

```java
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final KafkaProducer kafkaProducer;

    @Around("execution(* com.hlaia.controller.*.*(..)) && " +
            "!execution(* com.hlaia.controller.AuthController.*(..))")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {

        Object result = joinPoint.proceed();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String action = signature.getMethod().getName();
            String className = signature.getDeclaringType().getSimpleName();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (auth != null && auth.getPrincipal() instanceof Long) {
                userId = (Long) auth.getPrincipal();
            }

            String target = className + "." + action;
            kafkaProducer.sendOperationLog(userId, action, target);

        } catch (Exception e) {
            // 日志记录失败不影响业务
        }

        return result;
    }
}
```

逐段解读：

#### 切点表达式：execution 表达式

```java
@Around("execution(* com.hlaia.controller.*.*(..)) && " +
        "!execution(* com.hlaia.controller.AuthController.*(..))")
```

这是一种**表达式匹配**的方式（和限流切面的注解匹配不同）。

解析 `execution(* com.hlaia.controller.*.*(..))`：

```
execution(  *        com.hlaia.controller  .  *    .  *     (..)    )
           ↑                ↑               ↑    ↑    ↑       ↑
        任意返回类型    controller包下     任意类  任意方法  任意参数
```

`&& !execution(...)` 表示"排除 AuthController"——因为登录/注册是高频操作，不需要记录操作日志。

#### 为什么先 proceed() 再记录日志？

```java
Object result = joinPoint.proceed();   // 先执行业务方法
// 然后再记录日志
```

因为我们只想记录**成功的操作**。如果 `proceed()` 抛出异常（操作失败），下面的日志代码不会执行。

#### 获取方法信息

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();
String action = signature.getMethod().getName();                   // 如 "createBookmark"
String className = signature.getDeclaringType().getSimpleName();   // 如 "BookmarkController"
```

通过反射获取被拦截方法的名称和所属类名，作为日志的 `action`（操作类型）和 `target`（操作目标）。

#### instanceof 类型检查

```java
if (auth != null && auth.getPrincipal() instanceof Long) {
    userId = (Long) auth.getPrincipal();
}
```

`instanceof` 是 Java 的类型检查运算符，判断对象是否是某个类的实例。这里检查 `principal` 是否是 `Long` 类型，因为：
- 在 `JwtAuthFilter` 中，我们把 `userId`（Long 类型）设为了 `principal`
- 但 `principal` 的类型是 `Object`，不能直接当 `Long` 用
- 先用 `instanceof` 检查类型，再安全地强转

#### 空 catch 块的设计

```java
try {
    // ... 日志记录逻辑 ...
} catch (Exception e) {
    // 空！日志记录失败不影响业务
}
```

核心原则：**日志记录失败不应该影响用户的正常操作**。即使 Kafka 服务暂时不可用，用户的创建书签、删除文件夹等操作仍然应该正常完成。

如果这里不 catch，异常会向上传播，用户的操作虽然成功了，但会收到 500 错误响应——用户会以为操作失败了，实际上操作已经完成。这是非常糟糕的体验。

为什么只 catch `Exception` 而不是 `Throwable`？因为 `Error` 类型的异常（如 `OutOfMemoryError`）表示 JVM 级别的严重问题，不应该被吞掉，应该让它传播上去让 JVM 处理。

#### 通过 Kafka 异步发送日志

```java
kafkaProducer.sendOperationLog(userId, action, target);
```

为什么不直接写数据库？

```
同步写数据库：
  用户请求 → Controller → Service → 写数据库（可能100ms） → 返回响应
  总耗时 = 业务逻辑 + 100ms 日志写入

异步写 Kafka：
  用户请求 → Controller → Service → 发 Kafka 消息（2ms） → 返回响应
  总耗时 = 业务逻辑 + 2ms 消息发送

  后台 Consumer 慢慢把消息写入数据库，不影响用户请求速度
```

### 3.7 Result.java —— 统一响应包装

> 文件路径：`src/main/java/com/hlaia/common/Result.java`

这个类在前面的课程中已经见过，这里重点看和异常处理相关的部分：

```java
public static <T> Result<T> error(int code, String message) {
    Result<T> r = new Result<>();
    r.setCode(code);
    r.setMessage(message);
    return r;
}

public static <T> Result<T> error(ErrorCode errorCode) {
    return error(errorCode.getCode(), errorCode.getMessage());
}
```

`GlobalExceptionHandler` 就是调用这些静态方法把异常转为统一格式的 JSON 响应：

```json
// BusinessException 被捕获后返回：
{"code": 2004, "message": "User not found", "data": null}

// 校验失败被捕获后返回：
{"code": 400, "message": "username: 不能为空", "data": null}
```

---

## 4. 关键 Java 语法点

### 4.1 @interface —— 自定义注解

`@interface` 是定义注解的关键字，和 `class`、`interface`、`enum` 并列为 Java 的四种类型声明方式。

```java
// 注解定义
public @interface MyAnnotation {
    String value() default "";         // 属性：字符串类型，默认空字符串
    int count() default 0;             // 属性：整数类型，默认 0
    String[] tags() default {};        // 属性：字符串数组，默认空数组
}

// 注解使用
@MyAnnotation(value = "hello", count = 5, tags = {"a", "b"})
public void myMethod() { ... }

// 如果只设置 value 属性，可以省略 "value ="
@MyAnnotation("hello")
public void myMethod() { ... }
```

注解的限制：
- 属性类型只能是基本类型、String、Class、枚举、注解，以及这些类型的数组
- 不能有方法体
- 不能有泛型

### 4.2 @Target 和 @Retention —— 元注解

| 元注解 | 作用 | 常见取值 |
|---|---|---|
| `@Target` | 指定注解能用在什么地方 | `METHOD`（方法）、`TYPE`（类）、`FIELD`（字段）、`PARAMETER`（参数） |
| `@Retention` | 指定注解的生命周期 | `SOURCE`（源码）、`CLASS`（编译后）、`RUNTIME`（运行时） |
| `@Documented` | 注解是否出现在 Javadoc 中 | 无取值，标记注解 |
| `@Inherited` | 子类是否继承父类的注解 | 无取值，标记注解 |

`@Target` 可以同时指定多个位置：

```java
@Target({ElementType.METHOD, ElementType.TYPE})  // 可以标注在方法或类上
```

### 4.3 反射：从 JoinPoint 获取方法信息

AOP 切面大量使用了 Java 反射机制。反射让你在**运行时**获取类、方法、字段的信息。

本项目中的反射用法：

```java
// 1. 获取方法签名
MethodSignature signature = (MethodSignature) joinPoint.getSignature();

// 2. 获取 Method 对象
Method method = signature.getMethod();

// 3. 从 Method 上读取注解
RateLimit annotation = method.getAnnotation(RateLimit.class);

// 4. 获取方法名
String name = method.getName();  // 如 "createBookmark"

// 5. 获取声明方法的类名
String className = signature.getDeclaringType().getSimpleName();  // 如 "BookmarkController"
```

反射是 Java 的高级特性，平时写业务代码不太用到，但在框架开发（如 Spring、MyBatis）中无处不在。你现在只需要理解"AOP 切面通过反射获取方法信息"这一点就够了。

### 4.4 instanceof 关键字

`instanceof` 用于判断一个对象是否是某个类的实例：

```java
Object obj = "hello";

// 基本用法
if (obj instanceof String) {
    String str = (String) obj;   // 安全地强转
    System.out.println(str.length());
}

// Java 16+ 的模式匹配写法（更简洁）
if (obj instanceof String str) {
    // str 已经自动声明和赋值了，不需要再强转
    System.out.println(str.length());
}
```

在本项目中，`instanceof` 用于检查 `SecurityContextHolder` 中的 `principal` 是否是 `Long` 类型：

```java
if (auth.getPrincipal() instanceof Long) {
    Long userId = (Long) auth.getPrincipal();  // 安全强转
}
```

为什么需要检查？因为 `getPrincipal()` 的返回类型是 `Object`，我们"知道"它应该是 `Long`，但编译器不知道。如果不检查直接强转，一旦 `principal` 不是 `Long`（比如是字符串 `"anonymousUser"`），就会抛出 `ClassCastException`，导致请求失败。

### 4.5 try-catch-finally

完整的异常处理结构：

```java
try {
    // 可能出错的代码
    int result = 10 / 0;
} catch (ArithmeticException e) {
    // 处理特定类型的异常
    System.out.println("除数不能为零: " + e.getMessage());
} catch (Exception e) {
    // 处理其他所有异常
    System.out.println("发生了异常: " + e.getMessage());
} finally {
    // 无论是否发生异常都会执行（通常用于资源清理）
    System.out.println("清理资源");
}
```

在本项目的 `OperationLogAspect` 中，catch 块是空的——这是一种有意为之的设计（上面已经解释过了）。

### 4.6 Stream API 中的 findFirst 和 orElse

```java
String msg = errors.stream()
        .map(f -> f.getField() + ": " + f.getDefaultMessage())
        .findFirst()                  // 取第一个元素，返回 Optional<String>
        .orElse("Validation failed"); // 如果没有元素，返回默认值
```

`Optional` 是 Java 8 引入的容器类，用来优雅地处理"可能为空"的情况：

| 方法 | 含义 |
|---|---|
| `isPresent()` | 是否有值 |
| `get()` | 获取值（没有值会抛异常，不推荐单独使用） |
| `orElse(默认值)` | 有值返回值，没值返回默认值 |
| `orElseThrow()` | 没值时抛异常 |

推荐使用 `orElse` 或 `orElseThrow`，避免直接调用 `get()`。

---

## 5. 动手练习建议

### 练习 1：理解异常继承链

阅读以下代码，回答问题：

```java
public class MyException extends RuntimeException {
    private final int code;
    public MyException(int code, String message) {
        super(message);
        this.code = code;
    }
    public int getCode() { return code; }
}
```

1. `MyException` 是受检异常还是非受检异常？
2. `super(message)` 这一行做了什么？
3. 如果改成 `extends Exception`，使用时会有什么不同？

<details>
<summary>点击查看答案</summary>

1. 非受检异常（因为 `RuntimeException` 是非受检的）
2. 调用父类 `RuntimeException` 的构造方法，把 `message` 传递给父类，之后可以通过 `e.getMessage()` 获取
3. 每个抛出 `MyException` 的方法都需要声明 `throws MyException`，或者在方法内 try-catch 处理，代码会变得更啰嗦
</details>

### 练习 2：理解异常处理顺序

假设 `GlobalExceptionHandler` 中有以下方法（按代码顺序排列）：

```java
@ExceptionHandler(RuntimeException.class)
public Result<Void> handleRuntime(RuntimeException e) { ... }

@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusiness(BusinessException e) { ... }

@ExceptionHandler(Exception.class)
public Result<Void> handleAll(Exception e) { ... }
```

当 Controller 抛出 `BusinessException` 时，哪个方法会被调用？

<details>
<summary>点击查看答案</summary>

`handleBusiness` 会被调用。Spring 会匹配**最具体的**异常类型。`BusinessException` 比 `RuntimeException` 和 `Exception` 更具体，所以匹配它。

注意：如果把 `handleRuntime` 放在 `handleBusiness` 前面，有些 IDE 可能会警告"已经由 handleRuntime 处理了 BusinessException"，因为 `BusinessException extends RuntimeException`。但 Spring 的机制是按"最具体匹配"来选择的，不是按代码顺序。不过为了代码可读性，建议按从具体到宽泛的顺序排列。
</details>

### 练习 3：设计一个自定义注解

假设你需要一个 `@ExecutionTime` 注解，用于标记需要记录执行时间的方法。请写出注解定义：

要求：
- 只能标注在方法上
- 运行时可通过反射读取
- 有一个 `logLevel` 属性，可选值为 `"INFO"` 或 `"DEBUG"`，默认 `"INFO"`

<details>
<summary>点击查看答案</summary>

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExecutionTime {
    String logLevel() default "INFO";
}
```

使用示例：

```java
@ExecutionTime                           // 默认 INFO 级别
@ExecutionTime(logLevel = "DEBUG")       // DEBUG 级别
```
</details>

### 练习 4：阅读代码，回答问题

打开 `RateLimitAspect.java`，找到 `around` 方法，回答以下问题：

1. 如果 `permits = 10`，第 10 次请求会被放行还是拒绝？
2. 为什么 `increment` 和 `expire` 不是原子操作？在极端情况下可能出现什么问题？
3. 如果项目部署了两台服务器，用 `AtomicInteger` 做计数器有什么问题？

<details>
<summary>点击查看答案</summary>

1. 放行。代码用的是 `count > rateLimit.permits()`（严格大于），第 10 次 count=10，10 > 10 是 false，所以放行。第 11 次 count=11，11 > 10 是 true，才拒绝。

2. `increment` 和 `expire` 是两个独立的 Redis 命令。如果在 `increment` 返回 1 之后、`expire` 执行之前程序崩溃了，这个 key 就永远不会过期（没有 TTL），用户会被永久限流。解决方法是用 Redis Lua 脚本把两个操作合并为一个原子操作，但本项目认为发生概率极低，可以接受。

3. `AtomicInteger` 是 JVM 进程内的计数器，只能在单台服务器内统计。两台服务器各自维护各自的计数器，用户在 A 服务器请求了 10 次，在 B 服务器的计数器仍然是 0，限流失效了。
</details>

### 练习 5：扩展思考——如何给操作日志加上自定义注解？

当前的 `OperationLogAspect` 是按包路径匹配所有 Controller 方法的。如果你想更精细地控制——只有标注了 `@OperationLog` 注解的方法才记录日志，你会怎么修改？

提示：参考 `RateLimitAspect` 的切点表达式写法。

<details>
<summary>点击查看思路</summary>

第一步：创建自定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String value() default "";   // 操作描述，如 "创建书签"
}
```

第二步：修改切点表达式

```java
// 原来：按包路径匹配
@Around("execution(* com.hlaia.controller.*.*(..)) && " +
        "!execution(* com.hlaia.controller.AuthController.*(..))")

// 改为：按注解匹配
@Around("@annotation(com.hlaia.annotation.OperationLog)")
```

第三步：在 Controller 方法上使用

```java
@OperationLog("创建书签")
@PostMapping
public Result<BookmarkResponse> create(...) { ... }
```

这种"注解驱动"的方式更灵活——开发者可以选择性地标注需要记录日志的方法，而不是一刀切地记录所有方法。
</details>

### 练习 6：思考题——限流切面和全局异常处理器如何协作？

当请求被限流时，整个调用链是怎样的？请按顺序排列以下步骤：

A. `RateLimitAspect` 检查 Redis 计数器
B. Controller 方法执行
C. 返回 `{"code": 2007, "message": "Too many requests"}`
D. `GlobalExceptionHandler` 捕获 `BusinessException`
E. `joinPoint.proceed()` 被调用
F. 抛出 `BusinessException(ErrorCode.RATE_LIMITED)`

<details>
<summary>点击查看答案</summary>

正常请求（限流通过）：A → E → B → 返回业务数据

被限流的请求：A → F → D → C

具体流程：
1. 请求到达标注了 `@RateLimit` 的 Controller 方法
2. AOP 拦截，进入 `RateLimitAspect.around()`
3. 检查 Redis 计数器（步骤 A）
4. 如果超过阈值，抛出 `BusinessException`（步骤 F）
5. Controller 方法不会执行（B 被跳过）
6. `GlobalExceptionHandler` 捕获异常（步骤 D）
7. 返回错误 JSON（步骤 C）
</details>

---

## 小结

这节课我们学习了很多内容，来回顾一下核心要点：

1. **Java 异常体系**：`Throwable` 分为 `Error`（严重故障）和 `Exception`（一般问题），`Exception` 又分为受检异常（必须处理）和非受检异常（可选处理）。`BusinessException` 继承 `RuntimeException`，属于非受检异常。

2. **全局异常处理器**：用 `@RestControllerAdvice` + `@ExceptionHandler` 统一捕获和处理异常。类比"急诊科"——不管哪个 Controller 出了问题，都送到这里统一处理。避免了在每个方法里重复写 try-catch。

3. **AOP（面向切面编程）**：解决横切关注点（日志、限流、权限等）的代码重复问题。核心概念：切面（Aspect，封装横切逻辑的类）、切点（Pointcut，定义拦截位置）、通知（Advice，定义拦截时机）、连接点（JoinPoint，被拦截的方法执行）。类比"安检员"——不管谁进大楼，都要过安检。

4. **自定义注解**：用 `@interface` 定义注解，用 `@Target` 指定使用位置，用 `@Retention(RetentionPolicy.RUNTIME)` 保证运行时可通过反射读取。注解实现了"声明式编程"——加一个注解就能获得功能。

5. **限流切面**：使用 `@Around` 环绕通知 + Redis 固定窗口计数器算法。每个"用户+接口"组合有独立的 Redis 计数器，超过阈值抛出 `BusinessException`。

6. **操作日志切面**：使用 `execution()` 表达式匹配所有 Controller 方法（排除 AuthController），通过 Kafka 异步发送日志。空 catch 块保证日志失败不影响业务。

核心收获：
- 全局异常处理器是"急诊科"，统一处理所有异常
- AOP 是"安检员"，在不修改业务代码的前提下增加公共功能
- 自定义注解是"便利贴"，声明式地告诉框架"我需要什么功能"
- 限流保护系统稳定性，操作日志支持安全审计
