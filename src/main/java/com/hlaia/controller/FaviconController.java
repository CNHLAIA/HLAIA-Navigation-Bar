package com.hlaia.controller;

import com.hlaia.service.FaviconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 【Favicon 代理控制器】—— 提供 favicon 图片代理接口
 *
 * 为什么不直接让前端请求目标服务器的 /favicon.ico？
 *   因为浏览器遇到 401 响应会弹出原生登录对话框。
 *   通过后端代理，浏览器只和你的后端通信，不会触发登录弹窗。
 *
 * 接口设计：
 *   GET /api/favicon?url=https%3A%2F%2Ffrpc.example.com
 *
 *   为什么 url 要编码（encodeURIComponent）？
 *     URL 中的特殊字符（如 :、/、?）在作为查询参数传递时需要编码。
 *     https://frpc.example.com → https%3A%2F%2Ffrpc.example.com
 *     Spring 会自动解码，你在代码里拿到的就是原始的 https://frpc.example.com
 *
 * 返回值设计：
 *   - 成功：HTTP 200 + 图片二进制数据（Content-Type: image/x-icon）
 *   - 失败：HTTP 204 No Content（没有内容，但不是错误）
 *
 *   为什么用 204 而不是 404？
 *     204 表示"请求成功，但没有内容返回"。前端 <img> 标签收到 204
 *     会触发 onerror 事件，自动显示首字母占位图标，符合我们的期望。
 *     404 也可以，但语义上 204 更准确——接口本身是存在的，只是没有数据可返回。
 *
 * @RestController = @Controller + @ResponseBody
 *   方法的返回值自动序列化（byte[] 直接作为响应体返回）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Favicon", description = "Favicon proxy API")
public class FaviconController {

    private final FaviconService faviconService;

    /**
     * 获取网站 favicon 图标
     *
     * 请求示例：
     *   GET /api/favicon?url=https%3A%2F%2Fwww.baidu.com
     *
     * @RequestParam 的作用：
     *   从 URL 查询参数中读取值。
     *   ?url=xxx 中的 "url" 就是参数名，@RequestParam("url") 读取它。
     *   required = true 表示这个参数是必须的，不传会返回 400 错误。
     *
     * ResponseEntity<byte[]> 的含义：
     *   ResponseEntity 是 Spring 提供的响应包装器，可以精确控制：
     *   - HTTP 状态码（200、204 等）
     *   - 响应头（Content-Type、Cache-Control 等）
     *   - 响应体（byte[] 图片数据）
     *   byte[] 是图片的二进制数据，Spring 会原样写入响应流。
     *
     * @param url 目标网站的 URL（前端会 encodeURIComponent 编码）
     * @return favicon 图片数据（成功）或 204（失败）
     */
    @GetMapping("/favicon")
    @Operation(summary = "Proxy fetch favicon for a website")
    public ResponseEntity<byte[]> getFavicon(@RequestParam("url") String url) {
        // 从 URL 中提取 origin（协议 + 域名 + 端口）
        // 例如：https://frpc.example.com:8080/panel → https://frpc.example.com:8080
        String origin;
        try {
            origin = new URI(url).getScheme() + "://" + new URI(url).getAuthority();
        } catch (Exception e) {
            // URL 格式不合法，返回 204
            return ResponseEntity.noContent().build();
        }

        // 调用 Service 获取 favicon 图片
        byte[] imageData = faviconService.getFavicon(origin);

        if (imageData != null && imageData.length > 0) {
            // 拿到了图片，返回 200 + 图片数据
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/x-icon"))  // 告诉浏览器这是图标
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")  // 浏览器缓存 7 天
                    .body(imageData);
        }

        // 没拿到图片，返回 204 No Content
        // 前端 <img> 的 @error 回调会被触发，显示首字母占位
        return ResponseEntity.noContent().build();
    }
}
