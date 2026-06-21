package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 【创建文件夹请求 DTO】—— 用户新建文件夹时提交的数据
 *
 * 这个 DTO 中有两种字段：
 *   必填字段：加了校验注解的（如 name）
 *   可选字段：没有校验注解的（如 parentId、icon）
 *
 * 可选字段的处理：
 *   - parentId：如果为 null，Service 层会默认设为 0（顶级文件夹）
 *   - icon：如果为 null，前端可以显示一个默认图标
 *   这体现了 RESTful API 设计原则：客户端只需要提供必要信息，服务端提供合理默认值
 */
@Data
public class FolderCreateRequest {

    /**
     * 父文件夹ID（可选）
     * null 表示在顶级（根目录）创建文件夹
     * 有值表示在指定文件夹下创建子文件夹
     * 不需要校验注解，因为 null 是合法值
     */
    private Long parentId;

    /**
     * 文件夹名称（必填）
     *
     * @Size(max=100)：最大 100 个字符，与数据库 VARCHAR(100) 对应
     * 不设 min 是因为文件夹名称只需要"不为空"即可，1个字符的名字也是合法的
     */
    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 100, message = "文件夹名称不能超过100个字符")
    private String name;

    /**
     * 文件夹图标（可选）
     * 可以是 emoji 表情（如 "📁"）或图标标识名
     * 如果前端不传，后端可以设置一个默认图标
     */
    private String icon;
}
