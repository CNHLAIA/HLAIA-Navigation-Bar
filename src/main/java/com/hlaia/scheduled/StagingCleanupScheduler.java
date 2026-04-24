package com.hlaia.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.entity.StagingItem;
import com.hlaia.kafka.KafkaProducer;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【定时任务 —— 扫描并清理过期暂存项】
 *
 * =====================================================================
 *  一、什么是定时任务（Scheduled Task）？
 * =====================================================================
 *
 *   定时任务是让程序在特定的时间间隔或时间点自动执行某段代码的机制。
 *   类似于手机上的闹钟：到了设定的时间，闹钟就会响。
 *
 *   在 Spring Boot 中，只需要两步就能启用定时任务：
 *   1. 在主类上添加 @EnableScheduling 注解（开启定时任务支持）
 *   2. 在 Bean 的方法上添加 @Scheduled 注解（标记这是一个定时任务方法）
 *
 * =====================================================================
 *  二、@Scheduled 注解详解
 * =====================================================================
 *
 *   @Scheduled 有两种主要的配置方式：
 *
 *   1. fixedRate —— 固定频率执行
 *      固定频率，每 60 秒执行一次：
 *      @Scheduled(fixedRate = 60000)
 *      - 从上一次任务开始执行时计时，每隔固定时间执行一次
 *      - 不管上一次任务是否执行完毕（如果任务执行时间 > 间隔，会出现重叠）
 *      - 适合"周期性检查"的场景
 *
 *   2. cron —— Cron 表达式（更灵活）
 *      每天凌晨 2 点执行：
 *      @Scheduled(cron = "0 0 2 * * ?")
 *      - 格式：秒 分 时 日 月 星期
 *      - 示例：
 *        "0 0 * * * ?"    每小时整点执行
 *        "0 0/5 * * * ?"  每 5 分钟执行
 *        "0 0 9-17 * * ?" 每天 9 点到 17 点，每小时执行一次
 *        "0 0 0 1 * ?"    每月 1 号零点执行
 *      - 适合"在特定时间执行"的场景
 *
 *   本类使用 fixedRate = 60000（每 60 秒执行一次），
 *   因为清理过期暂存项需要频繁检查，而不是在特定时间点执行。
 *
 * =====================================================================
 *  三、为什么扫描和清理要分开？（架构设计思路）
 * =====================================================================
 *
 *   本类（StagingCleanupScheduler）只负责"扫描"，不负责"删除"。
 *   实际的删除操作由 StagingCleanupConsumer（Kafka 消费者）完成。
 *
 *   完整流程：
 *   StagingCleanupScheduler（定时扫描过期数据）
 *     -- 通过 Kafka 发送消息 -->
 *   StagingCleanupConsumer（执行实际删除操作）
 *
 *   为什么不直接在 Scheduler 中调用 stagingItemMapper.deleteById()？
 *
 *   1. 解耦（单一职责原则）：
 *      - Scheduler 的职责是"发现需要清理的数据"
 *      - Consumer 的职责是"执行清理操作"
 *      - 各司其职，代码更清晰
 *
 *   2. 可靠性：
 *      - 如果直接删除，删除失败后没有重试机制
 *      - 通过 Kafka 传递，如果 Consumer 处理失败，Kafka 会重新投递消息
 *      - 保证过期数据最终一定会被清理
 *
 *   3. 可扩展性：
 *      - 如果未来删除逻辑变复杂（如需要先备份、需要通知用户等），
 *        只需要修改 Consumer，Scheduler 不需要改动
 *
 *   4. 负载均衡：
 *      - 如果部署了多个服务实例，Kafka 会自动分配消息给不同实例
 *      - 避免所有删除操作都集中在定时任务所在的服务器上
 *
 * =====================================================================
 *  四、@EnableScheduling 注解的作用
 * =====================================================================
 *
 *   @EnableScheduling 是一个"开关"，必须添加在主类（或任意 @Configuration 类）上，
 *   Spring 才会扫描并执行 @Scheduled 注解标记的方法。
 *
 *   如果不添加 @EnableScheduling：
 *   - @Scheduled 注解会被 Spring 忽略
 *   - 定时任务方法永远不会被执行
 *   - 不会报错，但功能不生效（这是一个常见的遗漏！）
 *
 *   所以在主类 HlaiaNavigationBarApplication 上必须添加这个注解。
 *
 * =====================================================================
 *  五、LambdaQueryWrapper 简介
 * =====================================================================
 *
 *   LambdaQueryWrapper 是 MyBatis-Plus 提供的查询构造器，
 *   使用 Lambda 表达式（方法引用）来构建 WHERE 条件，优点：
 *   - 类型安全：编译期就能检查字段名是否正确
 *   - 重构友好：修改实体类字段名时，IDE 会自动更新所有引用
 *   - 可读性好：比手写 SQL 字符串更直观
 *
 *   示例：
 *   new LambdaQueryWrapper<StagingItem>()
 *       .le(StagingItem::getExpireAt, LocalDateTime.now())
 *
 *   等价于 SQL：
 *   SELECT * FROM staging_item WHERE expire_at <= '2024-01-01 12:00:00'
 *
 *   .le() 表示 "less than or equal"（小于等于）
 *   类似方法还有：.eq()（等于）、.gt()（大于）、.like()（模糊匹配）等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StagingCleanupScheduler {

    /**
     * 暂存项 Mapper —— 用于查询过期的暂存项
     */
    private final StagingItemMapper stagingItemMapper;

    /**
     * Kafka 生产者 —— 用于发送清理消息
     * 不直接删除数据，而是通过 Kafka 发送消息，由消费者异步执行删除
     */
    private final KafkaProducer kafkaProducer;

    /**
     * 扫描过期的暂存项，并通过 Kafka 发送清理消息
     *
     * 执行频率：每 60 秒执行一次（fixedRate = 60000，单位是毫秒）
     *
     * 处理流程：
     * 1. 查询 staging_item 表中 expireAt <= 当前时间的所有记录
     * 2. 遍历每条过期记录，通过 Kafka 发送清理消息
     * 3. StagingCleanupConsumer 消费消息后执行实际的删除操作
     *
     * 注意：本方法只负责"发现"过期数据，不负责"删除"
     */
    @Scheduled(fixedRate = 60000)  // 每 60000 毫秒（60秒）执行一次
    public void scanExpiredItems() {
        // ---- 第1步：查询所有过期的暂存项 ----
        // 使用 LambdaQueryWrapper 构建 WHERE 条件
        // .le(StagingItem::getExpireAt, LocalDateTime.now())
        //   等价于 WHERE expire_at <= 当前时间
        // 意思是：找出所有"过期时间已经过去"的暂存项
        List<StagingItem> expired = stagingItemMapper.selectList(
                new LambdaQueryWrapper<StagingItem>()
                        .le(StagingItem::getExpireAt, LocalDateTime.now()));

        // ---- 第2步：遍历每条过期记录，通过 Kafka 发送清理消息 ----
        for (StagingItem item : expired) {
            // 调用 KafkaProducer 发送消息到 "staging-cleanup" Topic
            // StagingCleanupConsumer 会消费消息并执行删除操作
            kafkaProducer.sendStagingCleanup(item.getId(), item.getUserId());
        }

        // ---- 第3步：记录日志 ----
        // 只在有过期数据时才输出日志，避免每分钟都输出一条无用的日志
        if (!expired.isEmpty()) {
            log.info("Scheduled cleanup: {} expired staging items", expired.size());
        }
    }
}
