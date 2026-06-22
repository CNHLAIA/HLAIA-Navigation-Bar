package com.hlaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 【异步任务配置】—— 为 @Async 方法提供基于虚拟线程的执行器
 *
 * 为什么用虚拟线程（Virtual Thread，Java 21 预览、Java 25 LTS 稳定）？
 *   1. I/O 密集型任务最受益：favicon 抓取会发起外部 HTTP 请求，等待时虚拟线程让出
 *      载体线程给其他任务，吞吐远高于固定大小的平台线程池
 *   2. 免调参：不需要手动设置 corePoolSize / maxPoolSize / queueCapacity，
 *      也不存在"队列满 → 拒绝策略"的调优负担
 *   3. Spring Boot 4 官方推荐写法
 *
 * 共用一个执行器够吗？
 *   操作日志（轻量、高频）和 favicon 抓取（重量、低频）共用一个执行器。
 *   虚拟线程之间互不阻塞——每个任务一个虚拟线程，I/O 等待时让出载体线程，
 *   所以两类任务不会互相拖累，不需要按任务类型拆分执行器。
 *
 * @EnableAsync：
 *   开启对 @Async 注解的支持。没有这个注解，@Async 方法会被当作普通同步方法执行。
 *   （之前项目无需异步，所以这个开关此前未启用。）
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 应用级异步执行器：每来一个任务就开一个虚拟线程。
     *
     * 为什么 Bean 名用 applicationTaskExecutor？
     *   Spring Boot 在 TaskExecutionAutoConfiguration 中默认查找名为 applicationTaskExecutor
     *   的 Bean。覆盖它可以让 @Async / 异步 MVC 等所有依赖默认执行器的组件统一走虚拟线程，
     *   配置收敛在一处。
     *
     * 为什么用 SimpleAsyncTaskExecutor 而不是 ThreadPoolTaskExecutor？
     *   SimpleAsyncAsyncTaskExecutor + setVirtualThreads(true) 是 Spring 6.1+ 官方推荐的
     *   虚拟线程配置方式：每个任务创建一个虚拟线程，无需池化（虚拟线程本身就是廉价的）。
     *   ThreadPoolTaskExecutor 适合平台线程池场景，虚拟线程不需要它。
     */
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        return executor;
    }
}
