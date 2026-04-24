package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【创建书签请求 DTO】—— 用户新建书签时提交的数据
 *
 * 书签创建时需要指定所属文件夹（folderId），
 * 因为每个书签必须属于一个文件夹，不能"漂浮"在文件夹之外。
 *
 * 可选字段的设计思路：
 *   - description：用户可能不想写描述，不写也不影响功能
 *   - iconUrl：如果前端没传，后端可以根据 URL 自动获取网站的 favicon
 *   可选字段不用加校验注解，前端不传时自动为 null
 */
@Data
public class BookmarkCreateRequest {

    /**
     * 所属文件夹ID（必填）
     *
     * @NotNull：Long 类型不能用 @NotBlank（@NotBlank 只适用于 String），
     *   因为 Long 的默认值是 null，@NotBlank 会报类型不匹配的错误。
     *   对于数值类型和对象类型的非空校验，统一使用 @NotNull。
     *
     * @NotNull vs @NotBlank vs @NotEmpty 的区别：
     *   @NotNull    → 不能为 null（适用于任何类型）
     *   @NotBlank   → 不能为 null 且内容不能为空白（仅适用于 String）
     *   @NotEmpty   → 不能为 null 且不能为空（String 不能为""，集合不能为空）
     */
    @NotNull(message = "文件夹ID不能为空")
    private Long folderId;

    /**
     * 书签标题（必填）
     * 显示在导航栏上的文字，如 "百度"、"GitHub"
     */
    @NotBlank(message = "书签标题不能为空")
    private String title;

    /**
     * 书签链接地址（必填）
     * 如 "https://www.baidu.com"
     *
     * 注意：这里没有用 @URL 注解来校验格式，因为 URL 格式校验比较严格，
     * 可能会拒绝一些合法但非标准的 URL（如 localhost:8080）。
     * 格式校验放在 Service 层会更灵活。
     */
    @NotBlank(message = "书签链接不能为空")
    private String url;

    /**
     * 书签描述/备注（可选）
     * 用户可以给书签添加备注说明
     */
    private String description;

    /**
     * 网站图标 URL（可选）
     * 如 "https://www.baidu.com/favicon.ico"
     * 前端创建/编辑书签时可传入，若未传则由后端通过 Kafka 异步获取
     */
    private String iconUrl;
}
