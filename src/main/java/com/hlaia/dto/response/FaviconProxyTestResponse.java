package com.hlaia.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 【代理连通性测试结果 DTO】—— 管理员点击"测试"按钮后的返回
 *
 * 测试目标固定为 Google 的 generate_204（无正文的轻量探测端点），
 * 它在大陆网络直连不通、走代理可达，恰好能验证"代理是否真的能带我们出去"。
 */
@Data
@AllArgsConstructor
public class FaviconProxyTestResponse {

    /** 测试是否成功（成功 = 通过该代理访问到了探测端点） */
    private boolean success;

    /** 请求耗时（毫秒），失败时无意义 */
    private long latencyMs;

    /** 失败原因（如 "connect timed out"），成功时为空 */
    private String message;
}
