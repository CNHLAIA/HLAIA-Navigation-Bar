package com.hlaia.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.entity.StagingItem;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
 *  三、清理策略（直接批量删除）
 * =====================================================================
 *
 *   本类扫描到过期数据后，直接调用 deleteBatchIds 批量删除。
 *
 *   历史背景：此前的版本会把过期 ID 发到 Kafka 的 staging-cleanup Topic，
 *   再由 StagingCleanupConsumer 异步逐条删除。这种"扫描+发消息+消费者再删"的链路
 *   在单机部署场景下纯属冗余——过期记录已经被 selectList 拉进内存，
 *   再绕一圈消息队列只会增加网络跳、JSON 序列化和 MQ 持久化开销，
 *   还多了一个 Kafka 单点故障风险。
 *
 *   直接批量删除的优势：
 *   1. 简单：一条 SQL `DELETE ... WHERE id IN (...)`，无中间件依赖
 *   2. 高效：批量单次往返，比逐条 deleteById 快得多
 *   3. 可靠：本任务在 @Scheduled 线程跑，天然就在后台，不会阻塞请求；
 *      若本次清理失败，下次扫描时这些记录仍然会被选中并清理（最终一致）
 *
 *   @Transactional 的作用：
 *   保证整个删除操作要么全部成功要么全部回滚——避免删到一半失败导致部分过期记录残留。
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
     * 暂存项 Mapper —— 查询过期记录 + 批量删除
     */
    private final StagingItemMapper stagingItemMapper;

    /**
     * 扫描过期的暂存项并直接批量删除
     *
     * 执行频率：每 60 秒执行一次（fixedRate = 60000，单位是毫秒）
     *
     * 处理流程：
     * 1. 查询 staging_item 表中 expireAt <= 当前时间的所有记录
     * 2. 用 deleteBatchIds 一次性批量删除（单条 SQL DELETE ... WHERE id IN (...)）
     *
     * @Transactional：保证批量删除的原子性——要么全部删除，要么全部回滚。
     */
    @Scheduled(fixedRate = 60000)  // 每 60000 毫秒（60秒）执行一次
    @Transactional
    public void scanExpiredItems() {
        // ---- 第1步：查询所有过期的暂存项 ----
        // 使用 LambdaQueryWrapper 构建 WHERE 条件
        // .le(StagingItem::getExpireAt, LocalDateTime.now())
        //   等价于 WHERE expire_at <= 当前时间
        // 意思是：找出所有"过期时间已经过去"的暂存项
        List<StagingItem> expired = stagingItemMapper.selectList(
                new LambdaQueryWrapper<StagingItem>()
                        .le(StagingItem::getExpireAt, LocalDateTime.now()));

        if (expired.isEmpty()) {
            return;  // 没有过期数据，避免每分钟输出一条无用日志
        }

        // ---- 第2步：批量删除 ----
        // 把过期记录的 ID 收集成 List，一次性交给 deleteBatchIds
        // 底层 SQL：DELETE FROM staging_item WHERE id IN (?, ?, ...)
        List<Long> expiredIds = expired.stream()
                .map(StagingItem::getId)
                .collect(Collectors.toList());
        stagingItemMapper.deleteBatchIds(expiredIds);

        log.info("Scheduled cleanup: {} expired staging items", expired.size());
    }
}
