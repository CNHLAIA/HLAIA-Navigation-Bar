package com.hlaia.controller;

import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.common.Result;
import com.hlaia.dto.request.FaviconProxySettingRequest;
import com.hlaia.dto.response.FaviconProxySettingResponse;
import com.hlaia.dto.response.FaviconProxyTestResponse;
import com.hlaia.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 【系统设置控制器】—— 管理员修改系统级配置
 *
 * 路径设计：/api/admin/settings/...
 *   SecurityConfig 已将 /api/admin/** 限定为 ADMIN 角色，
 *   代理配置是全局设置（影响所有用户的图标抓取），不能开放给普通用户。
 *
 * RESTful 设计：
 *   GET  /api/admin/settings/favicon-proxy        → 读取当前配置
 *   PUT  /api/admin/settings/favicon-proxy        → 保存配置（立即生效，无需重启）
 *   POST /api/admin/settings/favicon-proxy/test   → 用"请求体里的配置"测试连通性
 *     （不要求先保存——管理员填完地址立刻能测，体验更顺）
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "System-level settings management (admin only)")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    /**
     * 读取图标抓取代理配置
     */
    @GetMapping("/favicon-proxy")
    @Operation(summary = "Get favicon proxy settings")
    public Result<FaviconProxySettingResponse> getFaviconProxy() {
        var proxy = systemSettingService.getFaviconProxy();
        return Result.success(new FaviconProxySettingResponse(
                proxy.enabled(), proxy.host(), proxy.port()));
    }

    /**
     * 保存图标抓取代理配置
     *
     * 保存即生效：SystemSettingService 内部刷新内存缓存，
     * 抓取链路（FaviconService/IconFetchService）下一个请求就用新配置。
     */
    @PutMapping("/favicon-proxy")
    @Operation(summary = "Update favicon proxy settings")
    public Result<FaviconProxySettingResponse> updateFaviconProxy(
            @Valid @RequestBody FaviconProxySettingRequest request) {
        var proxy = toProxy(request);
        systemSettingService.saveFaviconProxy(proxy);
        return Result.success(new FaviconProxySettingResponse(
                proxy.enabled(), proxy.host(), proxy.port()));
    }

    /**
     * 测试代理连通性
     *
     * 用请求体里的配置（而非已保存的配置）构建一次性 HttpClient，
     * 访问 Google 的 generate_204 探测端点——它在大陆直连不通、走代理可达，
     * 能真实回答"这个代理能不能带我们访问国外站"。
     */
    @PostMapping("/favicon-proxy/test")
    @Operation(summary = "Test favicon proxy connectivity")
    public Result<FaviconProxyTestResponse> testFaviconProxy(
            @Valid @RequestBody FaviconProxySettingRequest request) {
        var proxy = toProxy(request);

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy.enabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
        }

        HttpRequest probe = HttpRequest.newBuilder()
                .uri(URI.create("https://www.google.com/generate_204"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        long start = System.currentTimeMillis();
        try {
            HttpResponse<Void> response = builder.build()
                    .send(probe, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;
            boolean ok = response.statusCode() == 204 || response.statusCode() == 200;
            return Result.success(new FaviconProxyTestResponse(
                    ok, elapsed, ok ? "" : "HTTP " + response.statusCode()));
        } catch (Exception e) {
            // 测试失败是预期内的结果（代理配错/不通），不是服务器错误，
            // 返回 success=false + 原因而不是抛异常
            return Result.success(new FaviconProxyTestResponse(
                    false, System.currentTimeMillis() - start, e.getMessage()));
        }
    }

    /**
     * 请求体 → 值对象 + 联动校验
     *
     * 跨字段规则（Bean Validation 表达不了）：
     *   enabled=true 时 host 必填非空、port 必填。
     *   enabled=false 时忽略 host/port（Service 会清空保存）。
     */
    private SystemSettingService.FaviconProxy toProxy(FaviconProxySettingRequest request) {
        if (Boolean.TRUE.equals(request.getEnabled())) {
            if (request.getHost() == null || request.getHost().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (request.getPort() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return new SystemSettingService.FaviconProxy(
                    true, request.getHost().trim(), request.getPort());
        }
        return SystemSettingService.FaviconProxy.direct();
    }
}
