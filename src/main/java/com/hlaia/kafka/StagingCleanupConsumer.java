package com.hlaia.kafka;

import com.hlaia.entity.StagingItem;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【Kafka 消费者 —— 清理过期暂存项】
 *
 * ═══════════════════════════════════════════════════════════════
 *  一、本消费者的职责
 * ═══════════════════════════════════════════════════════════════
 *
 *   监听 "staging-cleanup" Topic，当收到消息时，删除指定的暂存项。
 *
 *   暂存区（Staging Area）是一个临时存放书签的地方，数据有过期时间。
 *   过期的暂存项需要被清理，否则数据库中会堆积大量无用数据。
 *
 * ═══════════════════════════════════════════════════════════════
 *  二、清理流程（Scheduler + Kafka + Consumer 三者配合）
 * ═══════════════════════════════════════════════════════════════
 *
 *   清理过期暂存项不是一个类完成的，而是三个组件协作：
 *
 *   1. StagingCleanupScheduler（定时任务）：
 *      每分钟扫描一次 staging_item 表，找出 expireAt <= 当前时间的记录
 *      → 通过 KafkaProducer 发送消息到 "staging-cleanup" Topic
 *
 *   2. Kafka（消息中间件）：
 *      消息从 Producer 传递到 Consumer 的桥梁
 *
 *   3. 本类 StagingCleanupConsumer（消费者）：
 *      从 "staging-cleanup" Topic 读取消息，执行实际的删除操作
 *
 *   为什么不直接在 Scheduler 中删除？
 *   - 解耦：扫描（定时任务）和删除（消费者）分开，各司其职
 *   - 可靠：如果删除失败，Kafka 会重试投递消息，不会丢失清理任务
 *   - 可扩展：如果未来删除逻辑变复杂（如需要通知用户），只需修改消费者
 *   - 负载均衡：如果有多个消费者实例，Kafka 会自动分配消息
 *
 * ═══════════════════════════════════════════════════════════════
 *  三、消费者组（groupId）说明
 * ═══════════════════════════════════════════════════════════════
 *
 *   groupId = "hlaia-nav" 表示本消费者属于 "hlaia-nav" 消费者组。
 *   同一个组内的消费者会分担同一个 Topic 的消息：
 *   - 如果部署了多个服务实例，每条消息只会被其中一个实例处理
 *   - 避免重复删除同一条暂存项
 *   - 如果某个实例宕机，其他实例会自动接管它的消息
 *
 * ═══════════════════════════════════════════════════════════════
 *  四、异常处理的重要性
 * ═══════════════════════════════════════════════════════════════
 *
 *   消费者中的异常必须用 try-catch 包裹，原因：
 *   - 未捕获的异常会导致消费者线程崩溃
 *   - Kafka 会无限重试失败的消息（取决于配置），导致后续消息阻塞
 *   - 即使某条消息处理失败（如暂存项已被手动删除），也不应影响其他消息的消费
 *
 *   所以这里用 try-catch 包裹所有逻辑，失败只记录日志，继续处理下一条消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StagingCleanupConsumer {

    /**
     * 暂存项 Mapper —— 用于删除暂存项记录
     * 继承了 BaseMapper，自带 deleteById 方法，可以直接按 ID 删除
     */
    private final StagingItemMapper stagingItemMapper;

    /**
     * Jackson ObjectMapper —— JSON 解析工具
     * 用于解析 KafkaProducer.sendStagingCleanup() 发送的 JSON 消息
     * 消息格式：{"stagingItemId": 456, "userId": 1}
     */
    private final JsonMapper jsonMapper;

    /**
     * 消费"清理暂存项"消息
     *
     * 处理流程：
     * 1. 解析 JSON 消息，获取 stagingItemId
     * 2. 根据 stagingItemId 从数据库中删除该暂存项
     *
     * 消息来源：
     *   由 StagingCleanupScheduler 定时扫描到过期暂存项后，
     *   通过 KafkaProducer.sendStagingCleanup() 发送到 "staging-cleanup" Topic
     *
     * @param message Kafka 消息内容（JSON 字符串）
     *                 格式：{"stagingItemId": 456, "userId": 1}
     */
    @KafkaListener(topics = "staging-cleanup", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            // 解析 JSON 消息
            JsonNode node = jsonMapper.readTree(message);

            // 取出暂存项 ID
            Long stagingItemId = node.get("stagingItemId").asLong();

            // 执行删除操作
            // deleteById 是 MyBatis-Plus BaseMapper 自带的方法
            // 底层执行 SQL：DELETE FROM staging_item WHERE id = ?
            // 如果暂存项已经不存在（被手动删除了），deleteById 会返回 0，不会报错
            stagingItemMapper.deleteById(stagingItemId);

            log.info("Cleaned up expired staging item {}", stagingItemId);
        } catch (Exception e) {
            // 捕获异常，避免消费者崩溃
            // 可能的异常：JSON 解析失败、数据库连接异常等
            log.error("Failed to cleanup staging item: {}", e.getMessage());
        }
    }
}
