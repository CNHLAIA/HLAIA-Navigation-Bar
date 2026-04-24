package com.hlaia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling 注解的作用：
 *   开启 Spring 的定时任务支持。只有添加了这个注解，
 *   Spring 才会扫描并执行 @Scheduled 注解标记的方法。
 *   如果不加这个注解，StagingCleanupScheduler 中的定时任务不会执行。
 *   这是一个常见的遗漏——代码不会报错，但定时任务就是不运行！
 */
@SpringBootApplication
@EnableScheduling
public class HlaiaNavigationBarApplication {

    public static void main(String[] args) {
        SpringApplication.run(HlaiaNavigationBarApplication.class, args);
    }

}
