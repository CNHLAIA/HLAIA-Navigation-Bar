package com.hlaia.aspect;

import com.hlaia.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 【AOP 切面 —— 操作日志自动记录】—— 只记录管理员的越权写操作
 *
 * ============================================================
 * 这个切面是做什么的？
 * ============================================================
 *   记录管理员执行的"越权写操作"（封禁/解封用户、删除他人文件夹）到 operation_log 表，
 *   用于安全审计。万一管理员误操作或账号被盗，可以通过这张表追溯"谁在何时动了谁的数据"。
 *
 * ============================================================
 * 为什么只审计 AdminController，不再审计所有 Controller？
 * ============================================================
 *   历史版本曾拦截 controller 包下所有方法（仅排除 AuthController），结果：
 *     1. 用户改自己的书签/文件夹也被记，日志被高频噪音淹没
 *     2. 用户操作自己的数据本就不属于审计范畴——审计关心的是"越权"
 *     3. detail 字段永远是 null（只有方法名，没有业务上下文），记下来也没人看
 *
 *   真正有审计价值的只有管理员的 3 个写操作：
 *     - banUser       封禁用户（剥夺他人登录权限）
 *     - unbanUser     解封用户（恢复他人登录权限）
 *     - deleteFolder  删除任意用户的文件夹（破坏他人数据）
 *
 *   收窄后日志量从"每请求一条"降到"管理员越权时才记"，回归审计本质。
 *
 * ============================================================
 * 为什么不用自定义注解 @OperationLog？
 * ============================================================
 *   注解 + SpEL 方案更灵活（任意标注方法 + 任意 detail 模板），但当前需要审计的方法
 *   只有 3 个且全部集中在 AdminController，切面内 switch 方法名完全够用。
 *   YAGNI——真要扩展时再上注解也不迟。
 *
 * @Aspect     标记这是一个 AOP 切面
 * @Component  注册为 Spring Bean，让 Spring 管理这个切面的生命周期
 * @RequiredArgsConstructor Lombok 注解，为 final 字段生成构造函数（注入 OperationLogService）
 * @Slf4j      日志门面，用于在 catch 块输出警告（而非空 catch）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /**
     * 操作日志服务 —— 异步写入数据库
     *
     * 为什么用 @Async 而不是同步直接 insert？
     *   1. 不影响性能：日志记录是"非关键操作"，不应该拖慢管理员请求的响应速度
     *   2. 可靠性：OperationLogService.record 内部 try/catch 兜底，日志失败不冒泡
     *   3. 解耦：Controller 不需要知道日志怎么存储，切面负责调用，Service 负责落库
     */
    private final OperationLogService operationLogService;

    /**
     * 环绕通知 —— 只拦截 AdminController 的方法，记录越权写操作
     *
     * ============================================================
     * 切点表达式
     * ============================================================
     *   @Around("execution(* com.hlaia.controller.AdminController.*(..))")
     *
     *   只匹配 AdminController 下的所有方法。不再需要排除 AuthController 的子句——
     *   AdminController 本身不含认证接口。
     *
     *   注意：切点匹配 AdminController 的全部 5 个方法（listUsers / getUserFolders /
     *   banUser / unbanUser / deleteFolder），但 GET 只读操作不需要审计，
     *   方法体内会通过反射跳过。
     *
     * ============================================================
     * 为什么先 proceed() 再记录日志？
     * ============================================================
     *   只记录成功的操作——若 proceed() 抛异常，下面的日志代码不执行，
     *   避免审计表里出现"失败的越权尝试"造成误导。
     *
     * @param joinPoint 连接点，包含被拦截方法的信息
     * @return 目标方法的返回值
     * @throws Throwable 目标方法可能抛出的任何异常
     */
    @Around("execution(* com.hlaia.controller.AdminController.*(..))")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {

        // 第 1 步：先执行目标方法
        Object result = joinPoint.proceed();

        // 第 2 步：操作成功，判断是否需要记录日志
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            java.lang.reflect.Method method = signature.getMethod();

            // 只审计写操作（POST/PUT/DELETE）。GET 请求（listUsers / getUserFolders）跳过——
            // 管理员查看用户列表不构成"动他人数据"，无需审计。
            if (method.isAnnotationPresent(GetMapping.class)) {
                return result;
            }

            String methodName = method.getName();
            String className = signature.getDeclaringType().getSimpleName();
            String target = className + "." + methodName;

            // 第 3 步：根据方法名生成语义化的 action 常量和带业务上下文的 detail
            // detail 取方法的第一个参数（3 个目标方法的第一个参数都是 @PathVariable 的 ID）
            String action;
            String detail;
            switch (methodName) {
                case "banUser" -> {
                    action = "BAN_USER";
                    detail = "banned user " + firstArg(joinPoint);
                }
                case "unbanUser" -> {
                    action = "UNBAN_USER";
                    detail = "unbanned user " + firstArg(joinPoint);
                }
                case "deleteFolder" -> {
                    action = "DELETE_FOLDER";
                    detail = "deleted folder " + firstArg(joinPoint);
                }
                default -> {
                    // 兜底：未来新增的 AdminController 写操作方法，action 用方法名大写、detail 为 null
                    // 避免新方法被切面拦截后 action 为 null 导致记录不可读
                    action = methodName.toUpperCase();
                    detail = null;
                }
            }

            // 第 4 步：获取当前管理员 ID（principal 在 JwtAuthFilter 中设为 Long 类型的 userId）
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long adminId = null;
            if (auth != null && auth.getPrincipal() instanceof Long id) {
                adminId = id;
            }

            // 第 5 步：异步落库
            operationLogService.record(adminId, action, target, detail);

        } catch (Exception e) {
            // 核心原则：日志记录失败不应该影响正常的业务请求。
            // 这里用 log.warn 记录（不再用空 catch），便于运维发现日志系统问题。
            // 只 catch Exception 不 catch Throwable——Error 级别异常应让 JVM 处理。
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 取连接点的第一个方法参数（3 个目标方法的第一个参数都是 @PathVariable 的 ID）
     */
    private Object firstArg(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        return args.length > 0 ? args[0] : null;
    }
}
