package com.hlaia.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 【动态代理 HttpClient 提供者】—— 按系统设置构建/复用出站 HTTP 客户端
 *
 * 为什么需要它？
 *   java.net.http.HttpClient 不会自动走系统代理或 http_proxy 环境变量——
 *   生产服务器上挂着 mihomo（7890 端口），但后端抓国外站一直直连超时，
 *   国外书签的图标全部 NOT_FOUND。这个组件让抓取链路可以按管理员在
 *   设置页配置的代理出站，国内/国外分流交给 mihomo 自己的规则处理。
 *
 * 为什么要"动态"？
 *   代理配置存在数据库（管理员随时在前端改），改完必须立即生效。
 *   这里按当前配置缓存 HttpClient 实例：配置没变就复用（保住连接池），
 *   变了才重建。volatile + 竞争重建的写法在并发下最多多建几个实例被
 *   覆盖，无害。
 *
 * 为什么是 @Component 而不是放进某个 Service？
 *   FaviconService（代理显示）和 IconFetchService（创建书签时回填 icon_url）
 *   都需要这套逻辑，抽成独立组件避免重复。
 */
@Component
@RequiredArgsConstructor
public class ProxyHttpClientProvider {

    private final SystemSettingService systemSettingService;

    /** 缓存的（代理配置 → HttpClient）对；配置 equals 判断是否需要重建 */
    private volatile CachedClient cached;

    private record CachedClient(SystemSettingService.FaviconProxy proxy, HttpClient client) {
        /** record 自动生成的 equals 恰好是这里需要的语义：配置没变就复用 client */
        boolean matches(SystemSettingService.FaviconProxy current) {
            return proxy.equals(current);
        }
    }

    /**
     * 获取抓取用的 HttpClient（按当前代理配置直连或走代理）
     *
     * 连接超时 3 秒：连接建立的对象是代理服务器（通常在内网，如 mihomo），
     * 3 秒足够；慢页面的整体超时由各调用方在 HttpRequest 上自己设置。
     */
    public HttpClient faviconHttpClient() {
        SystemSettingService.FaviconProxy proxy = systemSettingService.getFaviconProxy();

        CachedClient current = cached;
        if (current != null && current.matches(proxy)) {
            return current.client();
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy.enabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
        }
        current = new CachedClient(proxy, builder.build());
        cached = current;
        return current.client();
    }

    /**
     * 当前生效的代理配置（IconFetchService 的 Jsoup 也需要它）
     */
    public SystemSettingService.FaviconProxy faviconProxy() {
        return systemSettingService.getFaviconProxy();
    }
}
