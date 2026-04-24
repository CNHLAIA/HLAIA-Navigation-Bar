package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【移动到文件夹请求 DTO】—— 将暂存项移动到正式文件夹时提交的数据
 *
 * 这个 DTO 的使用场景：
 *   暂存区 → 正式书签的转化过程。
 *   用户在暂存区中看到一个网页，决定把它放到某个文件夹中永久保存。
 *   后端会：在指定文件夹中创建新书签 + 删除暂存项（原子操作，要么都成功要么都失败）
 *
 * RESTful 设计：
 *   暂存项的 ID 通过 URL 路径传递（如 POST /api/staging/{id}/move）
 *   目标文件夹 ID 放在请求体中
 */
@Data
public class MoveToFolderRequest {

    /**
     * 目标文件夹ID（必填）
     * 指定暂存项要移动到哪个文件夹
     *
     * @NotNull：必须选择一个目标文件夹，不允许为 null
     * Service 层会校验：
     *   1. 文件夹必须存在
     *   2. 文件夹必须属于当前用户
     *   3. 该 URL 在目标文件夹中不能已存在（防止重复）
     */
    @NotNull(message = "目标文件夹ID不能为空")
    private Long folderId;
}
