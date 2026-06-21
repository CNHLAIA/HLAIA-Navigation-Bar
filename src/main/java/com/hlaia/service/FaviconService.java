package com.hlaia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

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
     * Java 11+ 内置的 HTTP 客户端，用于向目标服务器发起请求。
     *
     * 为什么用 java.net.http.HttpClient 而不是 Spring 的 RestTemplate？
     *   - HttpClient 是 Java 标准库自带的，不需要额外依赖
     *   - 支持异步请求、HTTP/2 等现代特性
     *   - 对于简单的 GET 请求足够用了
     *
     * 为什么设置 connectTimeout？
     *   防止目标服务器不响应时一直等待。3 秒连不上就放弃。
     *   内网服务器通常响应很快，3 秒足够了。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)  // 自动跟随重定向
            .build();

    /** favicon 缓存的 Redis key 前缀 */
    private static final String CACHE_PREFIX = "favicon:";

    /** 缓存命中时的过期时间：7 天 */
    private static final long CACHE_TTL_DAYS = 7;

    /** "未找到"标记的过期时间：1 小时（避免频繁重试） */
    private static final long NOT_FOUND_TTL_HOURS = 1;

    /**
     * 获取指定网站的 favicon 图片数据
     *
     * 调用流程：
     *   1. 尝试从 Redis 缓存读取
     *   2. 缓存未命中 → 用 HttpClient 请求目标服务器的 /favicon.ico
     *   3. 请求成功 → 缓存并返回图片字节
     *   4. 请求失败 → 缓存 "NOT_FOUND" 标记，返回 null
     *
     * @param origin 目标网站的源地址（如 "https://frpc.example.com"）
     * @return favicon 图片的字节数组，获取失败返回 null
     */
    public byte[] getFavicon(String origin) {
        String cacheKey = CACHE_PREFIX + origin;

        // ============ 第一步：查 Redis 缓存 ============
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NOT_FOUND".equals(cached)) {
                // 之前请求过但没拿到，直接返回 null
                return null;
            }
            // 缓存中有图片数据，Base64 解码后返回
            return Base64.getDecoder().decode(cached);
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
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            // 检查响应状态码
            // 200 表示成功，其他（401 未认证、404 未找到、500 服务器错误等）都是失败
            if (response.statusCode() == 200 && response.body().length > 0) {
                byte[] imageData = response.body();

                // 缓存到 Redis（Base64 编码，因为 StringRedisTemplate 只能存字符串）
                String base64Data = Base64.getEncoder().encodeToString(imageData);
                redisTemplate.opsForValue().set(cacheKey, base64Data,
                        CACHE_TTL_DAYS, TimeUnit.DAYS);

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
        redisTemplate.opsForValue().set(cacheKey, "NOT_FOUND",
                NOT_FOUND_TTL_HOURS, TimeUnit.HOURS);

        return null;
    }
}