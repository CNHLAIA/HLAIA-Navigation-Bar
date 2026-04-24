package com.hlaia.annotation;

import java.lang.annotation.*;

/**
 * 【自定义注解 —— 接口限流标记】—— 用于标记需要进行访问频率限制的接口
 *
 * ============================================================
 * 什么是 Java 自定义注解？
 * ============================================================
 *   注解（Annotation）是 Java 提供的一种"标记"机制。
 *   你可以把它理解成一张**便利贴**——贴在方法或类上，携带一些额外信息。
 *
 *   Java 自带的一些注解你已经见过了：
 *     @Override   —— 标记这个方法是重写父类的
 *     @Deprecated —— 标记这个方法已过时
 *     @SuppressWarnings —— 告诉编译器忽略某些警告
 *
 *   自定义注解就是：我们自己定义一种新的"便利贴"，
 *   然后在代码中贴到需要的地方，其他代码（比如 AOP 切面）读到这个注解就能做相应处理。
 *
 *   在本项目中，@RateLimit 注解的作用是：
 *     在 Controller 方法上贴上 @RateLimit，AOP 切面就会自动对该接口进行限流。
 *     开发者不需要写任何限流代码，加一个注解就行！
 *
 * ============================================================
 * 注解定义语法详解
 * ============================================================
 *
 *   public @interface RateLimit { ... }
 *            ^^^^^^^^^
 *            关键字 @interface 表示"这是一个注解类型"
 *            它和 class、interface、enum 一样，是 Java 的一种类型声明方式
 *
 *   注解中不能有普通的方法体，只能声明"属性"（看起来像方法，但其实是属性）：
 *     int permits() default 10;
 *     ^^^         ^            ^^^^^^^^^^
 *     类型        属性名        默认值
 *
 *   这里的 int permits() 不是方法，而是声明了一个名为 "permits" 的属性，
 *   类型是 int，默认值是 10。
 *   使用时可以这样赋值：@RateLimit(permits = 20)
 *
 *   为什么看起来像方法？
 *     这是 Java 注解的语法规定，注解的属性声明语法和方法签名很像，
 *     但它不是方法，没有方法体，不能被调用。
 *
 * ============================================================
 * 元注解（Meta-Annotation）详解
 * ============================================================
 *   元注解就是"注解的注解"——用来定义注解本身的行为。
 *   就像"法律的法律"（宪法）规定了一般法律怎么制定一样。
 *
 *   @Target(ElementType.METHOD)
 *     指定这个注解能用在什么地方。
 *     ElementType.METHOD 表示只能标注在**方法**上。
 *     其他常见取值：
 *       ElementType.TYPE       —— 类、接口、枚举
 *       ElementType.FIELD      —— 字段（成员变量）
 *       ElementType.PARAMETER  —— 方法参数
 *       ElementType.CONSTRUCTOR —— 构造方法
 *
 *     为什么限制为 METHOD？
 *       因为限流是针对具体的接口方法的，标注在类或字段上没有意义。
 *
 *   @Retention(RetentionPolicy.RUNTIME)
 *     指定注解的"存活时间"（生命周期）。
 *     RetentionPolicy 有三个取值：
 *       SOURCE   —— 只在源代码中存在，编译后丢弃（如 @Override）
 *       CLASS    —— 编译后存在于 .class 文件中，但运行时不可见（默认值）
 *       RUNTIME  —— 运行时也存在，可以通过反射获取
 *
 *     为什么必须是 RUNTIME？
 *       因为我们的 AOP 切面需要在**程序运行时**通过反射读取方法上的 @RateLimit 注解，
 *       如果是 SOURCE 或 CLASS，运行时就读不到了。
 *
 *   @Documented
 *     表示这个注解会出现在 Javadoc 文档中。
 *     当你用 javadoc 工具生成 API 文档时，被 @Documented 标记的注解会被包含在文档里。
 *     这是一个"文档友好"的做法，方便其他开发者查看 API 文档时知道这个接口有限流。
 *
 * ============================================================
 * 使用示例
 * ============================================================
 *   // 默认配置：60 秒内最多 10 次请求
 *   @RateLimit
 *   @GetMapping("/api/bookmarks")
 *   public Result listBookmarks() { ... }
 *
 *   // 自定义配置：30 秒内最多 5 次请求
 *   @RateLimit(permits = 5, seconds = 30)
 *   @PostMapping("/api/bookmarks")
 *   public Result createBookmark() { ... }
 *
 *   注解属性如果不指定，就使用 default 定义的默认值。
 *   default 的意义：让使用注解的人不必每次都写全部属性，只写需要自定义的部分即可。
 */
@Target(ElementType.METHOD)             // 此注解只能标注在方法上
@Retention(RetentionPolicy.RUNTIME)     // 运行时保留，可通过反射获取
@Documented                              // 包含在 Javadoc 中
public @interface RateLimit {

    /**
     * 允许的最大请求次数（许可数）
     *
     * 在指定的时间窗口（seconds）内，同一用户对同一接口最多允许请求的次数。
     * 例如 permits = 10 表示 60 秒内最多请求 10 次。
     *
     * @return 最大请求次数，默认 10 次
     */
    int permits() default 10;

    /**
     * 时间窗口大小（单位：秒）
     *
     * 统计请求次数的时间范围。例如 seconds = 60 表示统计最近 60 秒内的请求次数。
     *
     * @return 时间窗口秒数，默认 60 秒
     */
    int seconds() default 60;
}
