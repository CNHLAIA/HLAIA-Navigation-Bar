package com.hlaia.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 【Kafka 消息生产者】—— 向 Kafka Topic 发送异步任务的存根（Stub）
 *
 * 什么是 Kafka？
 *   Kafka 是一个分布式消息队列系统，核心概念是"生产者-消费者模型"：
 *   - 生产者（Producer）：把消息发送到 Kafka 的程序（就是本类）
 *   - 消费者（Consumer）：从 Kafka 读取消息并处理的程序（Task 16 会实现）
 *   - 主题（Topic）：消息的分类/频道，生产者往 Topic 写消息，消费者从 Topic 读消息
 *     可以理解为一个大管道，生产者从一端放消息进去，消费者从另一端取消息出来
 *
 *   为什么要用 Kafka 而不是直接在代码里处理？
 *   - 解耦：发送方不需要知道谁会处理这个消息，双方互不依赖
 *   - 异步：发送消息后立刻返回，不用等处理完成，响应更快
 *   - 可靠：Kafka 会持久化存储消息，即使消费者暂时宕机，消息也不会丢失
 *
 * KafkaTemplate 是什么？
 *   KafkaTemplate 是 Spring Kafka 提供的工具类，封装了 Kafka 客户端的复杂操作。
 *   就像 JdbcTemplate 封装了 JDBC 操作一样，KafkaTemplate 封装了 Kafka 操作。
 *   我们只需要调用它的 send() 方法就能发送消息，不需要关心底层细节。
 *
 * 消息的 Key 和 Value：
 *   Kafka 中的每条消息由两部分组成：
 *   - Key（键）：用于消息路由，相同 Key 的消息会被分配到同一个分区（Partition）
 *     这里使用 bookmarkId / userId / userId 作为 Key，保证同一资源的消息有序
 *   - Value（值）：消息的实际内容，这里使用 JSON 字符串
 *
 * 为什么用 JSON 字符串而不是直接传 Java 对象？
 *   1. 简单：不需要为每种消息定义专门的类，用字符串拼接就能搞定
 *   2. 通用：JSON 是跨语言的，任何语言的消费者都能解析
 *   3. 存根阶段：这只是初期实现，后续如果消息结构复杂了，可以改用对象序列化
 *   4. 调试友好：JSON 字符串可以直接在 Kafka 管理工具中查看，方便排查问题
 *
 * 本类定义的三个 Topic：
 *   - bookmark-icon-fetch：异步获取网站图标（favicon）
 *   - staging-cleanup：清理暂存区数据
 *   - operation-log：记录用户操作日志
 *   这些 Topic 的消费者会在 Task 16 中实现
 *
 * @Component 注解的作用：
 *   告诉 Spring "这是一个组件，请创建它的实例并纳入容器管理"。
 *   其他类可以通过构造器注入来使用 KafkaProducer。
 *
 * @Slf4j 注解的作用：
 *   Lombok 自动生成 log 对象（等价于 private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class)）
 *   之后可以直接用 log.info()、log.error() 等方法输出日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    /**
     * Kafka 消息发送工具（由 Spring 自动注入）
     *
     * 泛型参数说明：
     *   KafkaTemplate<String, String>
     *   - 第一个 String：消息的 Key 类型
     *   - 第二个 String：消息的 Value 类型
     *
     *   这里 Key 和 Value 都是 String 类型：
     *   - Key：使用资源的 ID（如 bookmarkId、userId），保证同一资源的消息有序
     *   - Value：使用 JSON 格式的字符串，包含消息的详细内容
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送"获取网站图标"的异步任务
     *
     * 使用场景：用户创建新书签时，后端需要访问书签的 URL 来获取该网站的 favicon（图标）。
     * 但获取图标可能需要几秒钟（网络请求），如果在创建书签的接口中同步获取，
     * 会导致接口响应变慢，用户体验不好。
     *
     * 异步方案：
     *   1. 创建书签接口直接返回，不等待图标获取完成
     *   2. 通过 Kafka 发送一条消息，告诉消费者"请帮我获取这个网站的图标"
     *   3. 消费者后台异步获取图标，获取成功后更新数据库中的 iconUrl 字段
     *   4. 前端下次刷新时就能看到图标了
     *
     * 消息格式：
     *   Topic: "bookmark-icon-fetch"（主题名称，消费者需要订阅同名 Topic 才能收到消息）
     *   Key:   bookmarkId.toString()（使用书签 ID 作为 Key）
     *   Value: {"bookmarkId": 123, "url": "https://www.baidu.com"}
     *
     * @param bookmarkId 书签 ID（用于消费者定位数据库记录）
     * @param url        书签的 URL（消费者需要访问这个 URL 来获取图标）
     */
    public void sendIconFetchTask(Long bookmarkId, String url) {
        // 构造 JSON 格式的消息内容
        // 使用字符串拼接而非 JSON 库，因为在存根阶段消息结构简单，不需要引入额外依赖
        String message = "{\"bookmarkId\":" + bookmarkId + ",\"url\":\"" + url + "\"}";
        // 发送消息到 Kafka
        // kafkaTemplate.send(主题名称, 消息Key, 消息Value)
        // 这是一个异步操作，调用后立即返回，不会阻塞当前线程
        kafkaTemplate.send("bookmark-icon-fetch", bookmarkId.toString(), message);
        log.info("Sent icon fetch task for bookmark {}", bookmarkId);
    }

    /**
     * 发送"清理暂存区"的异步任务
     *
     * 使用场景：当暂存区的数据被用户正式采纳（转为书签）后，
     * 需要异步清理暂存区中的原始数据，避免暂存区数据堆积。
     *
     * 为什么不直接在 Service 中删除暂存区数据？
     *   - 删除操作可能涉及多个数据库操作，放在消费者中可以实现更好的解耦
     *   - 如果删除失败，消费者可以重试，而不会影响主业务流程
     *   - 可以批量处理清理任务，提高效率
     *
     * @param stagingItemId 暂存区项目的 ID
     * @param userId        用户 ID
     */
    public void sendStagingCleanup(Long stagingItemId, Long userId) {
        String message = "{\"stagingItemId\":" + stagingItemId + ",\"userId\":" + userId + "}";
        kafkaTemplate.send("staging-cleanup", stagingItemId.toString(), message);
        log.info("Sent staging cleanup task for item {}", stagingItemId);
    }

    /**
     * 发送"操作日志"消息
     *
     * 使用场景：记录用户的关键操作（创建书签、删除文件夹等），
     * 用于安全审计和用户行为分析。
     *
     * 为什么用 Kafka 而不是直接写数据库？
     *   - 写日志是一个"非关键路径"操作，不应该影响主业务的响应速度
     *   - 通过 Kafka 异步处理，即使日志数据库暂时不可用，也不会影响用户操作
     *   - 消费者可以对日志做批量写入、聚合分析等后处理
     *
     * @param userId  执行操作的用户 ID
     * @param action  操作类型（如 "CREATE_BOOKMARK"、"DELETE_FOLDER"）
     * @param target  操作目标（如 "bookmark:123"、"folder:456"）
     */
    public void sendOperationLog(Long userId, String action, String target) {
        String message = "{\"userId\":" + userId + ",\"action\":\"" + action
                + "\",\"target\":\"" + target + "\"}";
        kafkaTemplate.send("operation-log", userId.toString(), message);
        log.info("Sent operation log: {} {} by user {}", action, target, userId);
    }
}
