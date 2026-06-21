package com.hlaia.kafka;

import com.hlaia.entity.OperationLog;
import com.hlaia.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 【Kafka 消费者 —— 记录操作日志】
 *
 * ═══════════════════════════════════════════════════════════════
 *  一、本消费者的职责
 * ═══════════════════════════════════════════════════════════════
 *
 *   监听 "operation-log" Topic，当收到消息时，将操作日志写入数据库。
 *
 *   操作日志记录用户在系统中的关键操作，例如：
 *   - 创建/删除书签
 *   - 创建/删除文件夹
 *   - 用户登录/登出
 *   - 管理员封禁用户
 *
 *   这些日志用于安全审计、问题排查和用户行为分析。
 *
 * ═══════════════════════════════════════════════════════════════
 *  二、为什么用 Kafka 异步写日志而不是直接写数据库？
 * ═══════════════════════════════════════════════════════════════
 *
 *   日志写入属于"非关键路径"操作（即使用户看不到日志，系统也能正常工作）。
 *   如果在每个接口中同步写日志：
 *   - 增加了接口响应时间（多一次数据库写入）
 *   - 如果日志表出问题（锁等待、磁盘满），会影响主业务
 *   - 日志写入失败可能导致用户操作也失败
 *
 *   使用 Kafka 异步方案：
 *   - 主业务接口只管发送 Kafka 消息，立即返回
 *   - 本消费者后台异步写入日志，不影响接口响应速度
 *   - 即使日志数据库暂时不可用，消息也不会丢失（Kafka 会持久化）
 *   - 消费者恢复后会继续消费积压的消息
 *
 * ═══════════════════════════════════════════════════════════════
 *  三、消息格式与字段说明
 * ═══════════════════════════════════════════════════════════════
 *
 *   由 KafkaProducer.sendOperationLog() 发送的 JSON 消息格式：
 *   {
 *     "userId": 1,                        // 操作用户的 ID
 *     "action": "CREATE_BOOKMARK",         // 操作类型
 *     "target": "bookmark:123"             // 操作目标
 *   }
 *
 *   字段说明：
 *   - userId：谁执行的操作。如果用户未登录（如公开 API），此字段可能不存在
 *   - action：操作类型，是一个枚举值（如 "CREATE_BOOKMARK"、"DELETE_FOLDER"）
 *   - target：操作目标，格式为 "资源类型:资源ID"（如 "bookmark:123"）
 *
 *   使用 node.has("userId") 检查字段是否存在：
 *   - 有些操作可能没有关联的用户（如系统自动操作）
 *   - 使用 has() 先检查再获取，避免 NullPointerException
 *
 * ═══════════════════════════════════════════════════════════════
 *  四、关于日志消费者不使用 AOP 的说明
 * ═══════════════════════════════════════════════════════════════
 *
 *   注意：Task 17 会实现基于 AOP 的操作日志切面，那个切面会在方法执行时
 *   自动捕获操作信息并调用 KafkaProducer.sendOperationLog()。
 *
 *   所以完整的日志记录流程是：
 *   Controller 方法 → AOP 切面拦截 → KafkaProducer 发消息 → 本消费者写入数据库
 *
 *   本消费者只负责最后一步：从 Kafka 读取消息并写入数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogConsumer {

    /**
     * 操作日志 Mapper —— 用于将日志记录插入数据库
     * 继承了 BaseMapper，自带 insert 方法
     */
    private final OperationLogMapper operationLogMapper;

    /**
     * Jackson ObjectMapper —— JSON 解析工具
     * 用于解析 KafkaProducer.sendOperationLog() 发送的 JSON 消息
     */
    private final JsonMapper jsonMapper;

    /**
     * 消费"操作日志"消息
     *
     * 处理流程：
     * 1. 解析 JSON 消息，获取 userId、action、target 等字段
     * 2. 创建 OperationLog 实体对象并设置字段值
     * 3. 设置 createdAt 为当前时间
     * 4. 调用 Mapper 插入数据库
     *
     * @param message Kafka 消息内容（JSON 字符串）
     *                 格式：{"userId": 1, "action": "CREATE_BOOKMARK", "target": "bookmark:123"}
     */
    @KafkaListener(topics = "operation-log", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            // 解析 JSON 消息为 JsonNode 对象
            JsonNode node = jsonMapper.readTree(message);

            // 创建 OperationLog 实体对象
            OperationLog logEntry = new OperationLog();

            // ---- 设置各个字段 ----

            // userId：操作用户 ID
            // 使用 node.has("userId") 先检查字段是否存在
            // 如果存在则取出值转为 Long，不存在则设为 null
            // 这样可以兼容没有用户信息的操作（如系统自动操作）
            logEntry.setUserId(node.has("userId") ? node.get("userId").asLong() : null);

            // action：操作类型（必填字段）
            // 如 "CREATE_BOOKMARK"、"DELETE_FOLDER"、"LOGIN" 等
            logEntry.setAction(node.get("action").asString());

            // target：操作目标（可选字段）
            // 如 "bookmark:123" 表示操作了 ID 为 123 的书签
            logEntry.setTarget(node.has("target") ? node.get("target").asText() : null);

            // createdAt：操作时间
            // 使用服务器当前时间而非消息发送时间
            // 因为消费者可能因为 Kafka 积压而延迟处理消息
            logEntry.setCreatedAt(LocalDateTime.now());

            // ---- 插入数据库 ----
            // insert 是 MyBatis-Plus BaseMapper 自带的方法
            // 底层执行 SQL：INSERT INTO operation_log (user_id, action, target, created_at) VALUES (?, ?, ?, ?)
            operationLogMapper.insert(logEntry);

        } catch (Exception e) {
            // 捕获异常，避免消费者崩溃
            // 可能的异常：JSON 解析失败、数据库写入失败等
            // 日志写入失败不应影响其他消息的消费
            log.error("Failed to save operation log: {}", e.getMessage());
        }
    }
}
