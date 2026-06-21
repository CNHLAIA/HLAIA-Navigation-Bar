package com.hlaia.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 【书签排序请求 DTO】—— 用户拖拽排序书签时提交的数据
 *
 * 结构与 FolderSortRequest 完全相同，只是语义不同：
 *   - FolderSortRequest：排序文件夹
 *   - BookmarkSortRequest：排序书签（同一个文件夹内的书签）
 *
 * 为什么不直接复用 FolderSortRequest？
 *   虽然结构相同，但它们代表的业务含义不同。分开定义有以下好处：
 *   1. 语义清晰：看类名就知道是书签排序还是文件夹排序
 *   2. 独立演进：未来书签排序可能需要额外字段（比如跨文件夹排序），分开定义方便扩展
 *   3. 校验差异：未来可能需要不同的校验规则
 *   这是"相似但不相同"的典型场景，推荐分开定义。
 */
@Data
public class BookmarkSortRequest {

    /**
     * 排序项列表（必填）
     * 每个排序项包含书签 ID 和新的排序序号
     */
    @NotNull(message = "排序列表不能为空")
    @Valid   // 启用嵌套校验，校验 SortItem 内部的字段
    private List<SortItem> items;

    /**
     * 【排序项】—— 书签排序的具体项
     * 与 FolderSortRequest.SortItem 结构相同
     * id 在这里代表书签ID
     */
    @Data
    public static class SortItem {

        /** 书签ID */
        @NotNull(message = "书签ID不能为空")
        private Long id;

        /** 排序序号，数字越小越靠前 */
        @NotNull(message = "排序序号不能为空")
        private Integer sortOrder;
    }
}
