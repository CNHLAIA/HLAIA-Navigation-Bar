package com.hlaia.service;

import com.hlaia.entity.SystemSetting;
import com.hlaia.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 【系统设置服务】—— 系统级配置的读取与保存（favicon 抓取代理等）
 *
 * 为什么落数据库而不是环境变量？
 *   环境变量改一次要重建容器（docker compose restart 不重新注入 env，
 *   这个坑在生产环境踩过）。落库 + 内存缓存后，管理员在前端设置页
 *   保存即生效，抓取链路下一个请求就用新配置。
 *
 * 缓存策略：
 *   ConcurrentHashMap 内存缓存，读多写少场景下把 DB 点查变成内存读。
 *   单体单实例部署，不存在多实例缓存一致性问题；更新时同步刷新缓存。
 *
 * @Service：标记为 Spring 管理的业务类
 * @RequiredArgsConstructor：Lombok 自动为 final 字段生成构造方法
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingMapper systemSettingMapper;

    /** 内存缓存：setting_key → setting_value */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    // ==================== 设置键常量 ====================
    // 集中定义避免魔法字符串散落各处；新增系统设置时在这里加常量

    /** 图标抓取代理：总开关（"true"/"false"） */
    public static final String KEY_FAVICON_PROXY_ENABLED = "favicon.proxy.enabled";

    /** 图标抓取代理：主机（IP 或域名，空 = 未配置） */
    public static final String KEY_FAVICON_PROXY_HOST = "favicon.proxy.host";

    /** 图标抓取代理：端口（1-65535，空 = 未配置） */
    public static final String KEY_FAVICON_PROXY_PORT = "favicon.proxy.port";

    /**
     * 【图标抓取代理配置】—— 不可变值对象
     *
     * record 的 equals 比较全部字段，ProxyHttpClientProvider 依赖这一点
     * 判断"配置是否变化、是否需要重建 HttpClient"。
     *
     * @param enabled true 表示抓取请求应经过 host:port 的代理转发
     */
    public record FaviconProxy(boolean enabled, String host, int port) {
        /** 语义化构造"直连"配置，避免各处手写 new FaviconProxy(false, "", 0) */
        public static FaviconProxy direct() {
            return new FaviconProxy(false, "", 0);
        }
    }

    // ==================== 通用读写 ====================

    /**
     * 读取设置值（带内存缓存）
     *
     * @param key          设置键
     * @param defaultValue 数据库中不存在该键时的返回值（同时也会作为下次查询前的兜底）
     * @return 设置值；键不存在时返回 defaultValue
     */
    public String get(String key, String defaultValue) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SystemSetting setting = systemSettingMapper.selectById(key);
        // 查不到（理论上不会：Flyway 迁移已 INSERT 种子数据）不缓存，
        // 避免把"暂时缺失"长期固化在内存里
        if (setting == null || setting.getSettingValue() == null) {
            return defaultValue;
        }
        cache.put(key, setting.getSettingValue());
        return setting.getSettingValue();
    }

    /**
     * 保存设置值（upsert + 刷新缓存）
     */
    public void set(String key, String value) {
        SystemSetting existing = systemSettingMapper.selectById(key);
        if (existing != null) {
            existing.setSettingValue(value);
            systemSettingMapper.updateById(existing);
        } else {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            systemSettingMapper.insert(setting);
        }
        cache.put(key, value);
        log.info("System setting updated: {} = {}", key, key.contains("password") ? "***" : value);
    }

    // ==================== 图标抓取代理 ====================

    /**
     * 读取图标抓取代理配置
     *
     * 防御性处理：开关打开但 host/port 缺失或非法时视为"直连"，
     * 避免 ProxySelector 拿到空地址在请求时抛异常——坏配置最多让代理失效，
     * 不应该让整个 favicon 功能挂掉。
     */
    public FaviconProxy getFaviconProxy() {
        boolean enabled = Boolean.parseBoolean(get(KEY_FAVICON_PROXY_ENABLED, "false"));
        String host = get(KEY_FAVICON_PROXY_HOST, "").trim();
        String portStr = get(KEY_FAVICON_PROXY_PORT, "").trim();

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            port = 0;
        }

        if (enabled && !host.isEmpty() && port >= 1 && port <= 65535) {
            return new FaviconProxy(true, host, port);
        }
        return FaviconProxy.direct();
    }

    /**
     * 保存图标抓取代理配置（三个键一次写入）
     *
     * @Transactional：三个键要么全部落库要么全部回滚，
     * 避免保存到一半失败留下混合了新旧值的配置。
     */
    @Transactional
    public void saveFaviconProxy(FaviconProxy proxy) {
        boolean effective = proxy.enabled() && !proxy.host().isEmpty() && proxy.port() >= 1;
        set(KEY_FAVICON_PROXY_ENABLED, String.valueOf(effective));
        set(KEY_FAVICON_PROXY_HOST, effective ? proxy.host() : "");
        set(KEY_FAVICON_PROXY_PORT, effective ? String.valueOf(proxy.port()) : "");
    }
}
