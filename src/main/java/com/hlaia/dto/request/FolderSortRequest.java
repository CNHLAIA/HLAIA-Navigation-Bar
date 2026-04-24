package com.hlaia.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 【文件夹排序请求 DTO】—— 用户拖拽排序文件夹时提交的数据
 *
 * 拖拽排序的工作原理：
 *   前端拖拽排序后，会把所有受影响的文件夹 ID 和新的排序序号打包成一个列表发送过来。
 *   后端遍历这个列表，批量更新每条记录的 sortOrder 字段。
 *
 * 示例数据（JSON 格式）：
 * {
 *   "items": [
 *     {"id": 1, "sortOrder": 0},
 *     {"id": 2, "sortOrder": 1},
 *     {"id": 3, "sortOrder": 2}
 *   ]
 * }
 *
 * 什么是 @Valid 注解？
 *   当 DTO 中嵌套了其他对象（如 List<SortItem>），外层的校验注解只能校验 items 本身不为 null，
 *   无法校验 SortItem 内部的字段（如 id 不能为 null）。
 *   加上 @Valid 后，校验会"穿透"到内部对象，递归校验 SortItem 的每个字段。
 */
@Data
public class FolderSortRequest {

    /**
     * 排序项列表（必填）
     *
     * @NotNull：列表本身不能为 null（但可以是空列表）
     * @Valid：启用嵌套校验，让 SortItem 内部的校验注解也生效
     *
     * 为什么用 @NotNull 而不是 @NotEmpty？
     *   因为理论上用户可以传一个空列表（虽然没有实际意义），
     *   Service 层会判断如果列表为空就直接返回，不执行数据库操作。
     *   而 @NotEmpty 会拒绝空列表，导致"取消排序操作"这种场景无法处理。
     */
    @NotNull(message = "排序列表不能为空")
    @Valid
    private List<SortItem> items;

    /**
     * 【排序项】—— 静态内部类
     *
     * 什么是静态内部类（static inner class）？
     *   用 static 修饰的内部类，它和外部类的关系只是"逻辑上的归属"，
     *   不需要先创建外部类对象才能创建内部类对象。
     *
     *   为什么用静态内部类而不是普通类？
     *   1. 高内聚：SortItem 只在 FolderSortRequest 中使用，没有独立存在的意义，
     *      把它放在外面会增加项目结构的复杂度。
     *   2. 命名空间：通过 FolderSortRequest.SortItem 使用，一看就知道是文件夹排序的排序项。
     *   3. 复用：BookmarkSortRequest 也有相同的结构，所以这里只是示例，
     *      实际项目中也可以把 SortItem 提取为独立的公共类。
     *
     *   static 与非 static 内部类的区别：
     *   - static 内部类：不持有外部类的引用，可以独立创建 new SortItem()
     *   - 非 static 内部类：持有外部类的引用，必须先有外部类对象才能创建
     */
    @Data
    public static class SortItem {

        /**
         * 文件夹ID（必填）
         * 指定要更新排序序号的文件夹
         */
        @NotNull(message = "文件夹ID不能为空")
        private Long id;

        /**
         * 排序序号（必填）
         * 数字越小排在越前面，从 0 开始
         */
        @NotNull(message = "排序序号不能为空")
        private Integer sortOrder;
    }
}
