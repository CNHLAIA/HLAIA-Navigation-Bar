package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 【暂存区创建请求 DTO】—— 浏览器扩展快速保存网页到暂存区时提交的数据
 *
 * 暂存区的使用场景：
 *   用户在浏览网页时，通过浏览器扩展的右键菜单或快捷键，一键保存当前页面到暂存区。
 *   不需要选择文件夹、不需要填写描述——保存速度要尽可能快。
 *
 * 这就是暂存区和书签的核心区别：
 *   - 书签：精心整理，需要选文件夹、填描述
 *   - 暂存区：快速保存，只存标题和链接，后续再整理
 *
 * 字段设计：
 *   必填：title、url（保存一个网页至少需要这两个信息）
 *   可选：expireMinutes（过期时间，灵活控制暂存区的"保质期"）
 */
@Data
public class StagingCreateRequest {

    /**
     * 网页标题（必填）
     * 由浏览器扩展自动从当前标签页获取 document.title
     */
    @NotBlank(message = "网页标题不能为空")
    private String title;

    /**
     * 网页链接地址（必填）
     * 由浏览器扩展自动从当前标签页获取 window.location.href
     */
    @NotBlank(message = "网页链接不能为空")
    private String url;

    /**
     * 过期时间（分钟）（可选）
     * null = 使用默认值（1天 = 1440分钟）
     * 用户也可以自定义过期时间，比如：
     *   60 = 1小时后过期
     *   1440 = 1天后过期（默认）
     *   10080 = 1周后过期
     *
     * 为什么用 Integer 而不是 int？
     *   int 是基本类型，默认值是 0，无法区分"没传"和"传了0"。
     *   Integer 是包装类型，默认值是 null，可以明确区分"前端没传这个字段"和"传了某个值"。
     */
    private Integer expireMinutes;
}
