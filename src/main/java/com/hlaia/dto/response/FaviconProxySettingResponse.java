package com.hlaia.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 【图标抓取代理设置响应 DTO】—— 设置页回显当前生效的代理配置
 */
@Data
@AllArgsConstructor
public class FaviconProxySettingResponse {

    /** 是否启用代理 */
    private boolean enabled;

    /** 代理主机（未配置时为空字符串） */
    private String host;

    /** 代理端口（未配置时为 0） */
    private int port;
}
