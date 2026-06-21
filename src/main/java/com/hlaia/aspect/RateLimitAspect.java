package com.hlaia.aspect;

import com.hlaia.annotation.RateLimit;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 【AOP 切面 —— 基于计数器的接口限流】—— 保护系统免受过多请求的冲击
 *
 * ============================================================
 * 什么是 AOP（面向切面编程）？
 * ============================================================
 *   AOP 全称 Aspect-Oriented Programming（面向切面编程）。
 *   它是 OOP（面向对象编程）的补充，用于解决**横切关注点**的问题。
 *
 *   什么是横切关注点？
 *     有些功能不是某个业务类独有的，而是"横跨"多个业务类的公共需求，比如：
 *       - 日志记录：每个方法都要记日志
 *       - 权限检查：很多接口都要验证权限
 *       - 事务管理：Service 方法都需要事务
 *       - **限流**：很多接口都需要限制访问频率
 *
 *     如果在每个方法里都写一遍这些代码，会导致：
 *       1. 代码重复（到处复制粘贴）
 *       2. 难以维护（改一处要改几十处）
 *       3. 业务逻辑被"非业务代码"淹没（看不清核心逻辑）
 *
 *   AOP 的解决方案：
 *     把这些公共功能（横切关注点）抽取出来，写成"切面"，
 *     然后通过配置告诉 Spring："在哪些方法执行时，自动插入这些公共逻辑"。
 *
 *   通俗比喻 —— 监控摄像头：
 *     想象你开了一家超市（你的应用程序），有很多收银台（Controller 方法）。
 *     你想在每个收银台都安装监控摄像头（限流、日志等公共功能）。
 *     - 不用 AOP：每个收银台都要手动装一个摄像头，接线、调试
 *     - 用 AOP：在超市管理制度中规定"所有收银台自动配备监控"，新开收银台自动生效
 *
 *   AOP 的另一种比喻 —— 高速公路收费站：
 *     不管你开什么车（什么 Controller 方法），经过收费站（切面）时都要：
 *       1. 减速（ProceedingJoinPoint.proceed() 之前）
 *       2. 交费（切面逻辑）
 *       3. 离开（ProceedingJoinPoint.proceed() 之后）
 *
 * ============================================================
 * AOP 核心概念
 * ============================================================
 *
 *   1. 切面（Aspect）—— @Aspect
 *      切面 = 横切关注点的模块化封装。
 *      本类 RateLimitAspect 就是一个切面，封装了"限流"这个关注点。
 *      类比：切面就是"监控摄像头系统"，包含了摄像头和录像机。
 *
 *   2. 切点（Pointcut）—— @Around("@annotation(...)") 中的表达式
 *      切点定义了"在哪里应用切面逻辑"，即"哪些方法会被拦截"。
 *      本类中的切点是"所有标注了 @RateLimit 注解的方法"。
 *      类比：切点就是"哪些收银台装了摄像头"。
 *
 *   3. 连接点（Join Point）—— ProceedingJoinPoint 参数
 *      连接点表示"被拦截到的那个方法执行"。
 *      通过 ProceedingJoinPoint 可以：
 *        - 获取被拦截方法的信息（方法名、参数等）
 *        - 决定是否继续执行原方法（调用 proceed()）
 *        - 修改方法的参数或返回值
 *      类比：连接点就是"当前正在经过收费站的某辆车"。
 *
 *   4. 通知（Advice）—— @Around / @Before / @After 等
 *      通知定义了"在切点处做什么"，以及"什么时候做"。
 *      通知有 5 种类型：
 *
 *      @Before（前置通知）：
 *        在目标方法执行**之前**执行。
 *        用途：参数校验、权限检查
 *        类比：进门之前检查门禁卡
 *
 *      @After（后置通知 / 最终通知）：
 *        在目标方法执行**之后**执行（无论成功还是异常）。
 *        用途：资源清理
 *        类比：离开房间后关灯（不管你是正常走还是被赶出去的）
 *
 *      @AfterReturning（返回通知）：
 *        在目标方法**成功返回后**执行（如果方法抛异常则不执行）。
 *        用途：处理返回值、记录成功日志
 *        类比：只有考试及格了才发奖品
 *
 *      @AfterThrowing（异常通知）：
 *        在目标方法**抛出异常时**执行。
 *        用途：异常日志、错误告警
 *        类比：只有出事故了才叫救护车
 *
 *      @Around（环绕通知）—— 本类使用的是这种：
 *        最强大的通知类型，包裹了整个目标方法的执行。
 *        可以控制：是否执行目标方法、何时执行、修改参数和返回值。
 *        用途：限流、缓存、性能监控、事务管理
 *        类比：收费站 —— 你可以放行（proceed()），也可以拦住（抛异常）
 *
 *        执行流程：
 *          @Around 通知的前半部分 → proceed() → 目标方法执行 → @Around 通知的后半部分
 *
 *        为什么限流用 @Around？
 *          因为限流需要在方法执行**之前**判断是否允许，
 *          如果不允许就直接抛异常，根本不执行目标方法。
 *          @Before 也能做到，但 @Around 更灵活，未来可以扩展。
 *
 * ============================================================
 * @Component 注解的作用
 * ============================================================
 *   告诉 Spring "这是一个组件，请创建它的实例并纳入容器管理"。
 *   与 @Service、@Repository 类似，都是让 Spring 管理 Bean 的。
 *   这里用 @Component 而不是 @Service，因为切面不是业务逻辑，而是基础设施组件。
 *
 * @RequiredArgsConstructor 注解的作用
 *   Lombok 注解，为所有 final 字段生成构造函数。
 *   Spring 会自动通过构造函数注入 StringRedisTemplate。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    /**
     * Redis 操作模板 —— 用于存储和递增请求计数
     *
     * 为什么用 Redis 而不是本地变量（如 AtomicInteger）？
     *   1. 分布式环境：如果部署了多台服务器，本地变量只能统计单机的请求量，
     *      而Redis 是集中式的，所有服务器共享同一个计数器。
     *   2. 自动过期：Redis 的 key 可以设置过期时间（TTL），过期自动删除，
     *      不需要我们手动清理。
     *   3. 原子操作：Redis 的 INCR（increment）命令是原子的，
     *      不会出现两个请求同时读到 count=5，都写成 count=6 的问题。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 环绕通知 —— 拦截所有标注了 @RateLimit 的方法，执行限流检查
     *
     * ============================================================
     * 切点表达式解释
     * ============================================================
     *   @Around("@annotation(com.hlaia.annotation.RateLimit)")
     *
     *   @annotation(注解类) 表示"匹配所有标注了指定注解的方法"。
     *   这里匹配所有标注了 @RateLimit 注解的 Controller 方法。
     *
     *   当一个请求到达标注了 @RateLimit 的方法时：
     *     1. Spring AOP 拦截这个方法调用
     *     2. 创建 ProceedingJoinPoint 对象（包含被拦截方法的所有信息）
     *     3. 调用本方法 around()，执行限流逻辑
     *     4. 如果限流通过，调用 joinPoint.proceed() 继续执行原方法
     *     5. 如果限流不通过，抛出 BusinessException，原方法不会执行
     *
     * ============================================================
     * 限流算法：基于 Redis 的固定窗口计数器
     * ============================================================
     *   算法思路：
     *     1. 为每个"用户+接口"组合维护一个 Redis key（如 ratelimit:1:/api/bookmarks）
     *     2. 每次请求到来，将 key 的值 +1（INCR 操作）
     *     3. 如果是第一次请求（值为 1），设置过期时间
     *     4. 如果计数超过允许值，拒绝请求
     *
     *   举例：@RateLimit(permits = 10, seconds = 60)
     *     - 第 1 次请求：count = 1，设置过期时间 60 秒  → 放行
     *     - 第 2 次请求：count = 2                      → 放行
     *     - ...
     *     - 第 10 次请求：count = 10                    → 放行（等于阈值也放行）
     *     - 第 11 次请求：count = 11                    → 拒绝（超过阈值）
     *     - 60 秒后：key 过期自动删除，计数归零
     *
     *   注意：这是"固定窗口"算法，存在"边界突发"问题：
     *     假设 60 秒限制 10 次：
     *     - 第 55 秒发送了 10 次请求（刚好达到限制）
     *     - 第 61 秒 key 过期，计数重置
     *     - 第 61 秒又可以发 10 次
     *     - 所以在 55 秒到 61 秒这 6 秒内，实际通过了 20 次请求
     *     对于本项目来说，这个精度足够了。更精确的算法可以用"滑动窗口"或"令牌桶"。
     *
     * @param joinPoint 连接点，包含被拦截方法的信息，可以控制方法的执行
     * @return 目标方法的返回值
     * @throws Throwable 目标方法可能抛出的任何异常
     */
    @Around("@annotation(com.hlaia.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // ============================================================
        // 第 1 步：获取方法上的 @RateLimit 注解配置
        // ============================================================
        // MethodSignature 包含了被拦截方法的签名信息（方法名、返回类型、参数、注解等）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 从方法上获取 @RateLimit 注解实例，读取用户配置的 permits 和 seconds
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        // ============================================================
        // 第 2 步：构建限流的 Redis key
        // ============================================================
        // key 格式：ratelimit:{userId}:{api路径}
        // 例如：ratelimit:42:/api/bookmarks
        //
        // 为什么要包含 userId？
        //   限流是"按用户"限的，不同用户有各自独立的计数器。
        //   如果不区分用户，一个用户的频繁请求会影响到其他用户。
        //
        // 为什么要包含 api 路径？
        //   不同接口的限流计数应该独立。
        //   用户频繁访问接口 A 不应该影响他访问接口 B。
        String userId = getCurrentUserId();

        // ============================================================
        // 通过 RequestContextHolder 获取当前 HTTP 请求信息
        // ============================================================
        // 什么是 RequestContextHolder？
        //   RequestContextHolder 是 Spring 提供的工具类，
        //   它使用 ThreadLocal 保存了当前线程关联的 HTTP 请求和响应对象。
        //
        //   为什么需要它？
        //     在 Controller 方法中，我们可以直接用参数获取 request：
        //       @GetMapping public Result method(HttpServletRequest request) { ... }
        //     但在 AOP 切面中，方法签名由 Spring AOP 决定（ProceedingJoinPoint），
        //     没有 HttpServletRequest 参数。所以需要通过 RequestContextHolder 获取。
        //
        //   工作原理：
        //     Spring MVC 在处理请求时，会将 request 存入 ThreadLocal：
        //       请求到达 → DispatcherServlet → RequestContextHolder.setRequestAttributes()
        //       → Controller / AOP 切面 → 通过 RequestContextHolder.getRequestAttributes() 获取
        //
        //   currentRequestAttributes() 返回 RequestAttributes 接口，
        //   我们需要强转为 ServletRequestAttributes 来获取 HttpServletRequest。
        String api = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getRequestURI();

        // 拼接最终的 Redis key
        String key = "ratelimit:" + userId + ":" + api;

        // ============================================================
        // 第 3 步：执行 Redis 原子递增操作
        // ============================================================
        // increment(key) 对应 Redis 的 INCR 命令：
        //   - 如果 key 不存在，创建并设值为 1，返回 1
        //   - 如果 key 存在，值 +1，返回递增后的值
        //
        // INCR 是原子操作（Atomic Operation）：
        //   即使多个请求同时执行 INCR，Redis 也会逐个处理，不会出现并发冲突。
        //   类比：银行柜台前的排队叫号机，每次按一下号码 +1，不会重复。
        Long count = redisTemplate.opsForValue().increment(key);

        // ============================================================
        // 第 4 步：如果是第一次请求，设置过期时间
        // ============================================================
        // 为什么只在 count == 1 时设置过期时间？
        //
        //   关键点：increment 和 expire 是两个独立的 Redis 命令，不是原子操作！
        //
        //   如果每次 increment 后都执行 expire：
        //     假设 seconds = 60，用户在第 1 秒请求了 1 次（count=1），
        //     然后第 59 秒又请求了 1 次（count=2），此时重新设置 expire 60 秒，
        //     那 key 会在第 119 秒才过期，实际上用户在 60 秒内只有 2 次请求，
        //     但 key 存活了 119 秒，计数没有正确重置。
        //
        //   只在 count == 1 时设置过期时间：
        //     表示"这个时间窗口刚开始"，从第一次请求时开始计时。
        //     之后的请求只递增计数，不重置过期时间。
        //     过期后 key 自动删除，下一次请求会重新从 1 开始计数。
        //
        //   注意：这里有一个极端情况——
        //     如果 increment 返回 1 之后、expire 执行之前程序崩溃了，
        //     这个 key 就永远不会过期（没有 TTL）。
        //     解决方案是使用 Redis 的 Lua 脚本保证原子性，但对于本项目来说概率极低，可以接受。
        if (count != null && count == 1) {
            redisTemplate.expire(key, rateLimit.seconds(), TimeUnit.SECONDS);
        }

        // ============================================================
        // 第 5 步：检查是否超过限流阈值
        // ============================================================
        // count > permits 表示请求次数超过了允许的最大值
        // 注意是 > 而不是 >=，因为 permits 表示"允许的最大次数"
        // 例如 permits = 10，第 10 次请求应该放行，第 11 次才拒绝
        if (count != null && count > rateLimit.permits()) {
            // 抛出业务异常，GlobalExceptionHandler 会捕获并返回错误响应
            // 使用 ErrorCode.RATE_LIMITED（2007, "Too many requests"）
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        // ============================================================
        // 第 6 步：限流通过，继续执行目标方法
        // ============================================================
        // joinPoint.proceed() 会执行被拦截的原始 Controller 方法
        // 它的返回值就是 Controller 方法的返回值，直接透传回去
        return joinPoint.proceed();
    }

    /**
     * 获取当前登录用户的 ID
     *
     * ============================================================
     * 通过 SecurityContextHolder 获取当前用户的原理
     * ============================================================
     *   在 JwtAuthFilter 中，认证成功后会将用户信息存入 SecurityContextHolder：
     *     SecurityContextHolder.getContext().setAuthentication(authentication);
     *   其中 authentication 的 principal 字段是 Long 类型的 userId。
     *
     *   SecurityContextHolder 使用 ThreadLocal 存储认证信息：
     *     每个请求线程都有自己的 SecurityContext，互不干扰。
     *
     *   获取流程：
     *     SecurityContextHolder.getContext()      → 获取安全上下文
     *     .getAuthentication()                     → 获取认证对象
     *     .getPrincipal()                          → 获取主体（用户ID）
     *
     *   如果用户未登录（如白名单接口），auth 为 null，返回 "anonymous"。
     *   这意味着未登录用户共享 "anonymous" 的限流计数器。
     *
     * @return 用户 ID 字符串，未登录返回 "anonymous"
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            return auth.getPrincipal().toString();
        }
        return "anonymous";
    }
}
