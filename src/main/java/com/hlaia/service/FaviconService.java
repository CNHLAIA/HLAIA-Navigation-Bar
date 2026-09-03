package com.hlaia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 【Favicon 代理服务】—— 代替浏览器去目标服务器抓取网站图标
 *
 * 为什么需要"代理"？
 *   前端 <img> 标签请求 /favicon.ico 时，如果目标服务器需要认证（如 frpc），
 *   浏览器会弹出登录窗口。改成后端去请求就不会弹窗——后端收到 401 只是
 *   一个"请求失败"的响应，不会触发任何 UI 弹窗。
 *
 * 缓存策略：
 *   Redis 缓存，key 格式：favicon:{domain}
 *   - 成功拿到：缓存 Base64 编码的图片数据，TTL 7 天
 *   - 拿不到（401/404/超时）：缓存空标记 "NOT_FOUND"，TTL 1 小时
 *     （避免短时间内反复请求一个拿不到图标的服务器）
 *
 * @Service：标记为 Spring 管理的业务类，其他类可以通过构造器注入使用
 * @RequiredArgsConstructor：Lombok 自动为 final 字段生成构造方法
 * @Slf4j：Lombok 自动生成 log 对象，可以用 log.info()、log.warn() 记录日志
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaviconService {

    /** Redis 操作模板，用于缓存 favicon 数据 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 出站 HTTP 客户端提供者——按系统设置（管理员可在设置页改）
     * 动态决定直连还是走代理（如 mihomo），配置变化自动重建。
     */
    private final ProxyHttpClientProvider httpClientProvider;

    /** favicon 缓存的 Redis key 前缀 */
    private static final String CACHE_PREFIX = "favicon:";

    /** 缓存命中时的过期时间：7 天 */
    private static final long CACHE_TTL_DAYS = 7;

    /** "未找到"标记的过期时间：1 小时（避免频繁重试） */
    private static final long NOT_FOUND_TTL_HOURS = 1;

    /**
     * Redis 降级标志：true 表示缓存最近一次操作失败，处于"跳过缓存"模式。
     *
     * 为什么需要这个标志？
     *   缓存读写失败时如果每个请求都打一条 warn，一次页面加载几百个书签
     *   会产生几百条重复日志。置位后只在"首次失败"和"恢复"时各记一条。
     *   每次请求仍会真实尝试 Redis（不短路），Redis 恢复后自动回到缓存模式。
     *
     * 为什么只有写成功才标记恢复（读成功不算）？
     *   典型故障"只读副本"恰好是读成功、写失败——读成功并不代表缓存可用，
     *   若据此清除标志，稳定故障下每个请求都会重复 degraded/recovered 日志。
     */
    private final AtomicBoolean cacheDegraded = new AtomicBoolean(false);

    /**
     * 获取指定网站的 favicon 图片数据
     *
     * 调用流程：
     *   1. 尝试从 Redis 缓存读取（Redis 故障时视为未命中，直接现抓）
     *   2. 缓存未命中 → 用 HttpClient 请求目标服务器的 /favicon.ico
     *   3. 请求成功 → 尝试写缓存（失败不影响返回）并返回图片字节
     *   4. 请求失败 → 尝试缓存 "NOT_FOUND" 标记（失败不影响），返回 null
     *
     * @param origin 目标网站的源地址（如 "https://frpc.example.com"）
     * @return favicon 图片的字节数组，获取失败返回 null
     */
    public byte[] getFavicon(String origin) {
        String cacheKey = CACHE_PREFIX + origin;

        // ============ 第一步：查 Redis 缓存 ============
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            if ("NOT_FOUND".equals(cached)) {
                // 之前请求过但没拿到，直接返回 null
                return null;
            }
            // 缓存中有图片数据，Base64 解码后返回
            try {
                return Base64.getDecoder().decode(cached);
            } catch (IllegalArgumentException e) {
                // 缓存数据损坏（非法 Base64），视为未命中继续现抓
            }
        }

        // ============ 第二步：缓存未命中，发起 HTTP 请求 ============
        try {
            // 构造请求 URL：origin + /favicon.ico
            // 例如：https://frpc.example.com → https://frpc.example.com/favicon.ico
            String faviconUrl = origin + "/favicon.ico";

            // 构建 HTTP 请求
            // HttpRequest.newBuilder() 是建造者模式，链式调用设置各种参数
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(faviconUrl))
                    .timeout(Duration.ofSeconds(5))      // 请求超时 5 秒
                    .GET()                                 // GET 请求
                    .build();

            // 发送请求，获取响应
            // BodyHandlers.ofByteArray()：把响应体读取为 byte[]（图片是二进制数据）
            // HttpClient 实例来自 provider：当前配置了代理时自动走代理出站
            HttpResponse<byte[]> response = httpClientProvider.faviconHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            // 检查响应状态码
            // 200 表示成功，其他（401 未认证、404 未找到、500 服务器错误等）都是失败
            if (response.statusCode() == 200 && response.body().length > 0) {
                byte[] imageData = response.body();

                // 写缓存（Base64 编码，因为 StringRedisTemplate 只能存字符串）
                // 缓存写失败不影响返回——图片已经拿到了，大不了下次现抓
                String base64Data = Base64.getEncoder().encodeToString(imageData);
                cachePut(cacheKey, base64Data, CACHE_TTL_DAYS, TimeUnit.DAYS);

                log.debug("Favicon fetched successfully: {}", origin);
                return imageData;
            }

            // 响应状态码不是 200（如 401、404），记录日志
            log.debug("Favicon fetch failed, status {}: {}", response.statusCode(), origin);

        } catch (Exception e) {
            // 网络异常、超时、URL 格式错误等
            // 只记录 warn 日志，不抛异常——favicon 获取失败不应该影响正常功能
            log.warn("Favicon fetch error for {}: {}", origin, e.getMessage());
        }

        // ============ 第三步：请求失败，缓存 NOT_FOUND 标记 ============
        // 为什么缓存失败结果？
        //   如果不缓存，每次打开文件夹都会重新请求这个拿不到 favicon 的服务器，
        //   浪费时间和网络资源。缓存 1 小时后重试，给服务器"恢复"的机会。
        cachePut(cacheKey, "NOT_FOUND", NOT_FOUND_TTL_HOURS, TimeUnit.HOURS);

        return null;
    }

    /**
     * 读缓存，Redis 故障时降级为"未命中"
     *
     * favicon 是装饰性功能，Redis 挂了（连接失败、只读副本拒绝写、认证失败等）
     * 不应该让 /api/favicon 直接 500——降级为跳过缓存、每次现抓即可。
     * 读成功不重置降级标志（写成功才算恢复，见字段注释）。
     */
    private String cacheGet(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            markCacheDegraded(e);
            return null;
        }
    }

    /**
     * 写缓存，Redis 故障时静默放弃
     *
     * 调用方拿到的图片数据不受影响，只是放弃了缓存加速。
     * 写成功是缓存真正恢复可用的标志。
     */
    private void cachePut(String cacheKey, String value, long ttl, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(cacheKey, value, ttl, unit);
            markCacheRecovered();
        } catch (Exception e) {
            markCacheDegraded(e);
        }
    }

    /** 记录缓存降级：只在状态从正常变为降级时打一条 warn，避免每个请求刷日志 */
    private void markCacheDegraded(Exception e) {
        if (cacheDegraded.compareAndSet(false, true)) {
            log.warn("Favicon cache degraded, falling back to fetch-on-every-request: {}",
                    e.getMessage());
        }
    }

    /** 记录缓存恢复：写成功说明 Redis 重新可用，打一条 info 方便确认故障已消除 */
    private void markCacheRecovered() {
        if (cacheDegraded.compareAndSet(true, false)) {
            log.info("Favicon cache recovered");
        }
        // 本来就正常时无事可记
    }
}