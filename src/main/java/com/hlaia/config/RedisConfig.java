package com.hlaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 【Redis 配置类】—— 配置 Redis 连接和序列化方式
 *
 * ============================================================
 * 什么是 Redis？
 * ============================================================
 *   Redis（Remote Dictionary Server，远程字典服务器）是一个
 *   **内存数据库**，数据存储在内存中，读写速度极快（微秒级）。
 *
 *   可以把 Redis 理解为一个"超级快的 HashMap"：
 *     HashMap：数据存在 JVM 内存中，重启后丢失
 *     Redis：数据存在独立进程中，可以持久化到磁盘
 *
 *   在本项目中，Redis 的用途：
 *     1. Token 黑名单：用户登出时将 Token 存入 Redis，设置过期时间
 *     2. 数据缓存：缓存热点数据，减少数据库查询
 *     3. 限流计数：使用 Redis 的原子操作实现 API 限流
 *
 * ============================================================
 * 什么是 StringRedisTemplate？
 * ============================================================
 *   StringRedisTemplate 是 Spring Data Redis 提供的工具类，
 *   用于简化 Redis 操作。它封装了 Redis 的连接管理和序列化逻辑。
 *
 *   常用操作：
 *     stringRedisTemplate.opsForValue().set("key", "value");        // 存入键值对
 *     stringRedisTemplate.opsForValue().set("key", "value", 60, TimeUnit.SECONDS);  // 带过期时间
 *     String val = stringRedisTemplate.opsForValue().get("key");    // 获取值
 *     stringRedisTemplate.delete("key");                            // 删除
 *     Boolean exists = stringRedisTemplate.hasKey("key");           // 检查是否存在
 *
 * ============================================================
 * 什么是序列化（Serializer）？
 * ============================================================
 *   Redis 是用 C 语言写的，它存储的是字节数组（byte[]）。
 *   Java 对象要存入 Redis，需要先"序列化"成字节数组；
 *   从 Redis 读取出来后，需要"反序列化"回 Java 对象。
 *
 *   StringRedisSerializer：把 String 直接转成 byte[]
 *     "hello" → [104, 101, 108, 108, 111]
 *     优点：人类可读，与 redis-cli 交互友好
 *
 *   JdkSerializationRedisSerializer（默认）：用 Java 原生序列化
 *     "hello" → [ -84, -19, 0, 5, 116, 0, 5, 104, 101, 108, ...]
 *     缺点：存储体积大，不可读，存在安全风险
 *
 *   我们选择 StringRedisSerializer，因为：
 *     1. 存储的都是字符串（Token、用户 ID 等），不需要复杂的序列化
 *     2. 在 redis-cli 中可以直接查看数据，方便调试
 *     3. 与其他语言（如 Node.js、Python）共享数据时兼容性好
 *
 * @Configuration 注解：标记这是一个 Spring 配置类
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 StringRedisTemplate Bean
     *
     * 什么是 @Bean？
     *   标注在方法上，表示这个方法返回的对象交给 Spring 容器管理。
     *   其他类可以通过 @Autowired 或构造函数注入来使用它。
     *
     * 什么是 RedisConnectionFactory？
     *   Redis 连接工厂，负责创建和管理与 Redis 服务器的连接。
     *   Spring Boot 根据 application.yml 中的配置自动创建。
     *   我们只需要注入它，不需要关心连接的底层细节。
     *
     * @param connectionFactory Spring Boot 自动配置的 Redis 连接工厂
     * @return 配置好序列化器的 StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // 创建 StringRedisTemplate 实例
        StringRedisTemplate template = new StringRedisTemplate();

        // 设置连接工厂（必须设置，否则模板无法连接 Redis）
        template.setConnectionFactory(connectionFactory);

        // 设置 Key（键）的序列化器为 StringRedisSerializer
        // 这样 Redis 中存储的键就是普通字符串，例如 "jwt:blacklist:eyJhbG..."
        // 如果不设置，默认使用 JDK 序列化，存储的是乱码
        template.setKeySerializer(new StringRedisSerializer());

        // 设置 Value（值）的序列化器为 StringRedisSerializer
        // 这样 Redis 中存储的值也是普通字符串，例如 "1"、"admin" 等
        template.setValueSerializer(new StringRedisSerializer());

        // 设置 Hash Key 的序列化器
        // Hash 是 Redis 的一种数据结构（类似 Java 的 HashMap）
        // 例如：HSET user:1 name "admin"  → "name" 就是 Hash Key
        template.setHashKeySerializer(new StringRedisSerializer());

        // 设置 Hash Value 的序列化器
        // 例如：HSET user:1 name "admin"  → "admin" 就是 Hash Value
        template.setHashValueSerializer(new StringRedisSerializer());

        // 初始化模板，让配置生效
        // afterPropertiesSet() 是 InitializingBean 接口的方法，
        // 在所有属性设置完成后调用，执行初始化逻辑
        template.afterPropertiesSet();

        return template;
    }
}
