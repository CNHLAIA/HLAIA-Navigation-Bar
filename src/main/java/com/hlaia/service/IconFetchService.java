package com.hlaia.service;

import com.hlaia.entity.Bookmark;
import com.hlaia.mapper.BookmarkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 【网站图标抓取服务】—— 异步抓取并回填书签的 favicon
 *
 * 取代原 Kafka 链路（BookmarkService → KafkaProducer → bookmark-icon-fetch Topic → IconFetchConsumer）。
 * 现在：BookmarkService.createBookmark → 本服务 fetchAndSaveIcon（@Async 虚拟线程） → 抓取并 update。
 *
 * 为什么 favicon 必须异步？
 *   抓取 favicon 需要请求外部网站，耗时数秒（受目标站点响应速度影响）。
 *   若在创建书签接口内同步执行，会严重拖慢响应。异步化后接口立即返回，
 *   图标在后台抓取成功后回填 bookmark.icon_url，前端下次刷新即可看到。
 *
 * 为什么用 @Async 而不是 Kafka？
 *   单体内异步任务最轻量的方案；虚拟线程对 I/O 密集的 HTTP 抓取特别友好。
 *   原 Kafka 方案的"解耦/可靠/重试"优势在单机场景被其部署成本盖过。
 *
 * favicon 获取优先级（保留原 IconFetchConsumer 全部逻辑）：
 *   1. HTML 中 <link rel="icon"> / <link rel="shortcut icon"> 声明的图标
 *   2. <link rel="apple-touch-icon"> 声明的图标（兜底）
 *   3. 默认路径 domain/favicon.ico
 *   4. 以上均失败 → iconUrl 保持 null，前端用 Google Favicon 服务兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IconFetchService {

    private final BookmarkMapper bookmarkMapper;

    /**
     * 共享的 HttpClient 实例（避免每次请求都创建新实例）
     * 设置 5 秒连接超时和自动跟随重定向
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 异步抓取 favicon 并回填到 bookmark.icon_url
     *
     * @Async 让此方法在虚拟线程上执行，调用方（BookmarkService）立即返回。
     * 方法内部 try/catch 兜底所有异常——抓取失败只是 iconUrl 保持 null，
     * 绝不能让异步任务的异常冒泡到 Spring 的异步执行器日志噪音里。
     *
     * @param bookmarkId 书签 ID（用于回填数据库记录）
     * @param url        书签的 URL（消费者需要访问这个 URL 来获取图标）
     */
    @Async
    public void fetchAndSaveIcon(Long bookmarkId, String url) {
        try {
            String iconUrl = fetchFavicon(url);

            if (iconUrl != null) {
                Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
                if (bookmark != null) {
                    bookmark.setIconUrl(iconUrl);
                    bookmarkMapper.updateById(bookmark);
                    log.info("Updated icon for bookmark {}: {}", bookmarkId, iconUrl);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch icon for bookmark {}: {}", bookmarkId, e.getMessage());
        }
    }

    /**
     * 多策略获取 favicon
     *
     * 策略流程：
     *   1. 用 Jsoup 获取页面 HTML
     *   2. 解析 <link> 标签中的 icon 声明（按 rel 属性优先级）
     *   3. 将 relative URL 转为 absolute URL
     *   4. 若 HTML 中未找到，回退到 /favicon.ico
     *
     * @param pageUrl 页面 URL
     * @return favicon 的 absolute URL，失败返回 null
     */
    private String fetchFavicon(String pageUrl) {
        try {
            URI uri = new URI(pageUrl);

            // SSRF 防护：只允许 http/https 协议，拒绝内网地址
            if (!isSafeUrl(uri)) {
                log.debug("URL rejected (SSRF protection): {}", pageUrl);
                return null;
            }

            String domain = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "");

            // 策略1：解析 HTML 中的 <link> 标签
            String htmlIcon = fetchFromHtml(pageUrl, domain);
            if (htmlIcon != null) return htmlIcon;

            // 策略2：尝试默认 /favicon.ico
            String defaultIcon = checkUrl(domain + "/favicon.ico");
            if (defaultIcon != null) return defaultIcon;

        } catch (Exception e) {
            log.debug("Could not fetch favicon for {}: {}", pageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 从 HTML 中解析 favicon URL
     *
     * 使用 Jsoup 获取网页并查找 <link rel="icon"> 等标签。
     *
     * @param pageUrl 页面完整 URL
     * @param domain  协议 + 域名（用于拼接 relative URL）
     * @return absolute favicon URL，未找到返回 null
     */
    private String fetchFromHtml(String pageUrl, String domain) {
        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (compatible; HlaiaNav/1.0)")
                    .timeout(5000)
                    .get();

            // 按 rel 优先级查找：先找 "icon" / "shortcut icon"（排除 apple-touch-icon）
            for (Element link : doc.select("link[rel~=(?i)^(?!apple-touch).*$]")) {
                String rel = link.attr("rel").toLowerCase();
                if (rel.contains("icon")) {
                    String href = link.attr("abs:href");
                    if (!href.isEmpty()) {
                        String verified = checkUrl(href);
                        if (verified != null) return verified;
                    }
                }
            }

            // 兜底：apple-touch-icon
            Element appleIcon = doc.selectFirst("link[rel~=(?i)apple-touch-icon]");
            if (appleIcon != null) {
                String href = appleIcon.attr("abs:href");
                if (!href.isEmpty()) {
                    return checkUrl(href);
                }
            }
        } catch (Exception e) {
            log.debug("HTML parsing failed for {}: {}", pageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 验证 URL 是否可访问（HEAD 请求返回 200）
     */
    private String checkUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!isSafeUrl(uri)) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return url;
            }
        } catch (Exception e) {
            log.debug("URL check failed for {}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * SSRF 防护：验证 URL 是否安全
     *
     * 只允许 http/https 协议，拒绝：
     *   - 回环地址（127.0.0.1、localhost）
     *   - 私有网络（10.x、172.16-31.x、192.168.x）
     *   - 链路本地地址（169.254.x.x）
     *
     * 这防止了攻击者通过创建指向内网地址的书签来探测内部服务。
     */
    private boolean isSafeUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        try {
            String host = uri.getHost();
            if (host == null) return false;
            InetAddress address = InetAddress.getByName(host);
            return !address.isLoopbackAddress()
                    && !address.isSiteLocalAddress()
                    && !address.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
