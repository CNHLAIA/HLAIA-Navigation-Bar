package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 【批量删除请求 DTO】—— 用户批量删除书签或文件夹时提交的数据
 *
 * 批量操作 vs 单个操作：
 *   单个删除用 DELETE /api/bookmarks/{id}，URL 中的 {id} 标识要删除的资源。
 *   批量删除用一个请求体包含多个 ID，减少网络请求次数，提升用户体验。
 *
 *   示例：用户勾选了 5 个书签，点击"删除"按钮
 *   - 单个操作：需要发 5 次 HTTP 请求
 *   - 批量操作：只需发 1 次 HTTP 请求，效率高很多
 *
 * 为什么用 @NotEmpty 而不是 @NotNull？
 *   @NotNull 允许传入空列表 []（长度为 0），但"删除 0 个东西"没有意义。
 *   @NotEmpty 要求列表至少有 1 个元素，在入口处就拦截了无意义的请求。
 */
@Data
public class BatchDeleteRequest {

    /**
     * 要删除的资源ID列表（必填，且至少包含一个ID）
     *
     * @NotEmpty：不能为 null，也不能是空列表 []
     * 这比 @NotNull 更严格，既拒绝 null 也拒绝空列表
     *
     * Service 层还会做额外校验：
     *   1. 这些 ID 对应的资源必须存在
     *   2. 这些资源必须属于当前用户（防止越权删除别人的数据）
     */
    @NotEmpty(message = "删除列表不能为空")
    private List<Long> ids;
}
