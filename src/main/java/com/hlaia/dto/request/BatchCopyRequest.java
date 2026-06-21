package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 【批量复制请求 DTO】—— 用户批量复制书签时提交的数据
 *
 * 批量复制的使用场景：
 *   用户选中多个书签，复制到另一个文件夹中。
 *   复制操作会创建新的书签记录（新的 ID），不影响原有的书签。
 *
 * 与 BatchDeleteRequest 的对比：
 *   结构完全相同（都是一个 ID 列表），但语义不同：
 *   - BatchDeleteRequest：删除这些 ID 对应的资源
 *   - BatchCopyRequest：复制这些 ID 对应的资源（目标文件夹通过 URL 路径指定）
 *
 *   同样遵循"相似但不相同"的原则，分开定义以保持语义清晰。
 */
@Data
public class BatchCopyRequest {

    /**
     * 要复制的书签ID列表（必填，至少包含一个ID）
     *
     * @NotEmpty：确保列表不为 null 且不为空
     * 复制操作需要知道"复制哪些书签"，空列表没有意义
     */
    @NotEmpty(message = "复制列表不能为空")
    private List<Long> ids;
}
