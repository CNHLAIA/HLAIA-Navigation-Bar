package com.hlaia.aspect;

import com.hlaia.kafka.KafkaProducer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 【AOP 切面 —— 操作日志自动记录】—— 自动记录所有 Controller 方法的操作日志
 *
 * ============================================================
 * 这个切面是做什么的？
 * ============================================================
 *   当用户在系统中进行操作（创建书签、删除文件夹等）时，
 *   我们需要记录一条日志，比如"用户 42 执行了 createBookmark"，
 *   用于安全审计和用户行为分析。
 *
 *   如果在每个 Controller 方法里手动写日志记录代码，会有几个问题：
 *     1. 代码重复 —— 几十个方法都要写
 *     2. 容易遗漏 —— 新增方法时忘了加日志
 *     3. 业务逻辑被非业务代码淹没 —— 核心逻辑和日志代码混在一起
 *
 *   用 AOP 切面解决：自动拦截所有 Controller 方法，统一记录日志。
 *   开发者只需要写业务逻辑，日志由切面自动处理。
 *
 * ============================================================
 * 与 RateLimitAspect 的对比
 * ============================================================
 *   RateLimitAspect   —— 拦截标注了 @RateLimit 注解的方法（按注解匹配）
 *   OperationLogAspect —— 拦截 controller 包下的所有方法（按包路径匹配）
 *
 *   切面的匹配方式有两种：
 *     1. 注解匹配：@Around("@annotation(com.hlaia.annotation.RateLimit)")
 *     2. 表达式匹配：@Around("execution(* com.hlaia.controller.*.*(..))")
 *     本类使用的是表达式匹配。
 *
 * @Aspect     标记这是一个 AOP 切面
 * @Component  注册为 Spring Bean，让 Spring 管理这个切面的生命周期
 * @RequiredArgsConstructor Lombok 注解，为 final 字段生成构造函数（注入 KafkaProducer）
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /**
     * Kafka 消息生产者 —— 用于异步发送操作日志到 Kafka
     *
     * 为什么用 Kafka 而不是直接写数据库？
     *   1. 不影响性能：日志记录是"非关键操作"，不应该拖慢用户请求的响应速度
     *   2. 可靠性：即使日志数据库暂时不可用，消息暂存在 Kafka 中，不会丢失
     *   3. 解耦：Controller 不需要知道日志怎么存储，切面负责发送，Consumer 负责存储
     */
    private final KafkaProducer kafkaProducer;

    /**
     * 环绕通知 —— 拦截所有 Controller 方法（排除 AuthController），记录操作日志
     *
     * ============================================================
     * execution 切点表达式语法详解
     * ============================================================
     *   @Around("execution(* com.hlaia.controller.*.*(..)) && " +
     *           "!execution(* com.hlaia.controller.AuthController.*(..))")
     *
     *   execution() 是最常用的切点表达式，用于匹配方法执行。
     *   语法格式：execution(返回类型 包名.类名.方法名(参数类型))
     *
     *   逐部分解析：execution(* com.hlaia.controller.*.*(..))
     *     *                         → 返回类型：匹配任意返回类型（通配符）
     *     com.hlaia.controller      → 包名：com.hlaia.controller 包下
     *     .*                        → 类名：匹配该包下的任意类（* 是通配符）
     *     .*                        → 方法名：匹配类中的任意方法（* 是通配符）
     *     (..)                      → 参数：匹配任意参数（.. 表示零个或多个任意类型的参数）
     *
     *   通配符说明：
     *     *   —— 匹配一个元素（一个类名、一个方法名、一个参数类型）
     *     ..  —— 匹配零个或多个元素（用于参数列表或包路径）
     *
     *   && 运算符：逻辑"与"，同时满足两个条件
     *     第一个条件：匹配 controller 包下的所有方法
     *     第二个条件：排除 AuthController 的方法
     *
     *   !execution() —— 排除匹配（取反）
     *     !execution(* com.hlaia.controller.AuthController.*(..))
     *     表示"不要匹配 AuthController 中的任何方法"
     *
     * ============================================================
     * 为什么排除 AuthController？
     * ============================================================
     *   AuthController 包含注册、登录、刷新 Token、登出等接口。
     *   排除的原因：
     *     1. 登录和注册是高频操作，每次都记日志会产生大量无用日志
     *     2. 用户还没登录时没有 userId，日志信息不完整（principal 是 null）
     *     3. 登录操作本身就是认证行为，不属于"业务操作"，不需要记录
     *     4. 避免日志中充斥大量登录/注册记录，影响对关键业务操作的审计
     *
     *   如果你想让某些 Controller 也被排除，可以在 && 后面继续添加：
     *     && !execution(* com.hlaia.controller.HealthController.*(..))
     *
     * ============================================================
     * 为什么先 proceed() 再记录日志？
     * ============================================================
     *   Object result = joinPoint.proceed();  // 先执行目标方法
     *   // 然后再记录日志
     *
     *   原因：我们只想记录**成功的操作**。
     *     - 如果 proceed() 之前的代码记录日志，即使操作失败（如参数错误、权限不足），
     *       日志中也会出现这条"失败的操作"记录，容易造成误导。
     *     - 先执行操作，只有成功返回后才记录日志，确保日志中都是有效的操作记录。
     *
     *   如果你想记录失败的操作怎么办？
     *     可以在 proceed() 外面加 try-catch：
     *       try {
     *           result = joinPoint.proceed();
     *       } catch (Throwable t) {
     *           // 记录失败日志
     *           throw t;
     *       }
     *     但目前的需求只记录成功操作，所以不需要。
     *
     * @param joinPoint 连接点，包含被拦截方法的信息
     * @return 目标方法的返回值
     * @throws Throwable 目标方法可能抛出的任何异常
     */
    @Around("execution(* com.hlaia.controller.*.*(..)) && " +
            "!execution(* com.hlaia.controller.AuthController.*(..))")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {

        // ============================================================
        // 第 1 步：先执行目标方法（Controller 中的业务逻辑）
        // ============================================================
        // proceed() 会调用被拦截的原始 Controller 方法，获取返回值。
        // 如果目标方法抛出异常，这里会直接向上传播（throws Throwable），
        // 下面的日志记录代码不会执行——这正是我们想要的效果（只记录成功的操作）。
        Object result = joinPoint.proceed();

        // ============================================================
        // 第 2 步：操作成功，记录操作日志
        // ============================================================
        // 用 try-catch 包裹整个日志记录逻辑，确保日志失败不影响业务。
        // 详见下方 catch 块的注释。
        try {
            // 获取被拦截方法的签名信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();

            // 获取方法名 —— 作为 action（操作类型）
            // 例如方法名 "createBookmark" 表示"创建书签"操作
            String action = signature.getMethod().getName();

            // 获取声明该方法的类名 —— 作为 target 的一部分
            // 例如 "BookmarkController"
            String className = signature.getDeclaringType().getSimpleName();

            // ============================================================
            // 获取当前登录用户的 ID
            // ============================================================
            // 从 SecurityContextHolder 获取认证信息（和 RateLimitAspect 中相同）
            //
            // auth.getPrincipal() instanceof Long 是什么意思？
            //   instanceof 是 Java 的类型检查运算符，用于判断对象是否是某个类的实例。
            //   在 JwtAuthFilter 中，我们把 userId（Long 类型）设为了 principal：
            //     new UsernamePasswordAuthenticationToken(userId, null, authorities)
            //                                 ^^^^^^ 这就是 principal
            //
            //   为什么需要 instanceof 检查？
            //     1. 安全性：principal 的类型取决于 JwtAuthFilter 的实现，
            //        如果将来有人修改了 JwtAuthFilter 的代码，principal 可能不再是 Long。
            //     2. 防御性编程：不假设 principal 一定是 Long，先检查类型再使用。
            //     3. 避免 ClassCastException：如果不检查直接强转 (Long) auth.getPrincipal()，
            //        当 principal 不是 Long 时会抛出 ClassCastException，导致请求失败。
            //
            //   instanceof 的用法：
            //     if (obj instanceof String) {       // 检查 obj 是否是 String 类型
            //         String str = (String) obj;     // 安全地强转
            //     }
            //
            //   Java 16+ 的模式匹配（Pattern Matching）写法更简洁：
            //     if (auth.getPrincipal() instanceof Long userId) {
            //         // userId 已经自动声明和赋值了，不需要再强转
            //     }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (auth != null && auth.getPrincipal() instanceof Long) {
                userId = (Long) auth.getPrincipal();
            }

            // 拼接操作目标：类名.方法名
            // 例如 "BookmarkController.createBookmark"
            String target = className + "." + action;

            // ============================================================
            // 通过 Kafka 异步发送操作日志
            // ============================================================
            // sendOperationLog 会将日志消息发送到 Kafka 的 "operation-log" Topic，
            // 由 OperationLogConsumer 异步消费并写入数据库。
            //
            // 异步的好处：发送日志消息只需要几毫秒，不会阻塞当前请求的返回。
            kafkaProducer.sendOperationLog(userId, action, target);

        } catch (Exception e) {
            // ============================================================
            // 为什么 catch 块是空的？
            // ============================================================
            // 核心原则：**日志记录失败不应该影响正常的业务请求**
            //
            // 可能导致日志记录失败的场景：
            //   1. Kafka 服务不可用（网络故障、Kafka 宕机）
            //   2. SecurityContextHolder 中没有认证信息（某些边缘场景）
            //   3. 方法签名解析异常
            //
            // 如果不 catch，异常会向上传播，导致用户的操作成功了但返回 500 错误，
            // 用户会以为操作失败了，实际上操作已经成功了——这是非常糟糕的体验。
            //
            // 正式项目中建议在这里添加日志记录：
            //   log.warn("操作日志记录失败", e);
            // 这样开发人员可以通过日志系统发现问题，而不会影响用户。
            //
            // 为什么只 catch Exception 而不是 Throwable？
            //   Error 类型的异常（如 OutOfMemoryError）通常表示 JVM 级别的严重问题，
            //   不应该被吞掉，应该让它传播上去让 JVM 处理。
            //   Exception 是应用程序级别的异常，可以安全地忽略。
        }

        // 返回目标方法的原始返回值
        return result;
    }
}
