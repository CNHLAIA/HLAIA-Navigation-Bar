package com.hlaia.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【图标抓取代理设置请求 DTO】—— 管理员在设置页保存/测试代理时提交
 *
 * 字段语义：
 *   enabled=false → 直连（host/port 会被清空保存）
 *   enabled=true  → 抓取请求经 host:port 转发（如 mihomo 的 mixed 端口）
 *
 * 校验注意：host 不加 @NotBlank——关闭代理的请求体里 host 允许为空，
 * "enabled=true 时 host 必填"这条联动规则由 Controller/Service 校验，
 * Bean Validation 表达不了跨字段约束。
 */
@Data
public class FaviconProxySettingRequest {

    /** 是否启用代理 */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    /** 代理主机（IP 或域名） */
    private String host;

    /** 代理端口（1-65535） */
    @Min(value = 1, message = "端口不能小于 1")
    @Max(value = 65535, message = "端口不能大于 65535")
    private Integer port;
}
