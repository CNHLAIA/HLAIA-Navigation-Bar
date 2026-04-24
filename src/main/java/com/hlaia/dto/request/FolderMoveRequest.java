package com.hlaia.dto.request;

import lombok.Data;

/**
 * 【文件夹移动请求 DTO】—— 用户将文件夹移动到另一个位置时提交的数据
 *
 * 移动操作只需要一个参数：目标父文件夹的 ID。
 * 文件夹本身的 ID 通过 URL 路径传递（如 PUT /api/folders/{id}/move），不需要放在请求体中。
 *
 * RESTful API 的设计原则：
 *   - 资源的标识（ID）放在 URL 路径中：/api/folders/{id}/move
 *   - 操作的参数放在请求体中：{"parentId": 5}
 *   这样 URL 本身就能表达"对哪个资源做什么操作"，语义清晰
 *
 * 为什么 parentId 没有校验注解？
 *   因为 null 是合法值：null 表示移动到根目录（顶级）。
 *   如果加了 @NotNull，就无法表达"移动到根目录"这个操作了。
 */
@Data
public class FolderMoveRequest {

    /**
     * 目标父文件夹ID
     * null 或 0 = 移动到根目录（顶级）
     * 其他值 = 移动到指定文件夹下
     *
     * Service 层会做以下校验：
     *   1. 不能移动到自己内部（防止循环引用）
     *   2. 目标文件夹必须存在且属于当前用户
     *   3. 不能移动到自己的子文件夹中（会导致树形结构断裂）
     */
    private Long parentId;
}
