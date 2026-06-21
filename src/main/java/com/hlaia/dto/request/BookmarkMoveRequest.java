package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 【书签移动请求 DTO】—— 将一个或多个书签移动到目标文件夹时提交的数据
 *
 * 支持批量移动：前端可以一次传递多个书签 ID，后端统一验证权限后批量更新 folderId。
 *
 * @NotEmpty vs @NotNull：
 *   bookmarkIds 用 @NotEmpty 确保：不能为 null 且不能为空列表 []
 *   targetFolderId 用 @NotNull 确保：必须选择一个目标文件夹
 */
@Data
public class BookmarkMoveRequest {

    /**
     * 要移动的书签 ID 列表（必填，至少包含一个）
     */
    @NotEmpty(message = "书签列表不能为空")
    private List<Long> bookmarkIds;

    /**
     * 目标文件夹 ID（必填）
     */
    @NotNull(message = "目标文件夹不能为空")
    private Long targetFolderId;
}
