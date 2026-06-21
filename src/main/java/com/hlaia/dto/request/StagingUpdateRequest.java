package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【暂存区更新请求 DTO】—— 用户修改暂存项的过期时间时提交的数据
 *
 * 为什么这个 DTO 只有一个字段？
 *   暂存区的定位是"临时存放"，不支持编辑标题、链接等核心信息。
 *   用户能修改的只有过期时间——延长或缩短暂存项的存活时间。
 *
 *   这体现了最小权限原则：只暴露必要的修改能力，减少误操作的可能性。
 *   如果用户想修改标题或链接，应该删除后重新保存。
 *
 * RESTful 设计：
 *   暂存项的 ID 通过 URL 路径传递（如 PATCH /api/staging/{id}）
 *   请求体中只包含需要修改的字段（expireMinutes）
 */
@Data
public class StagingUpdateRequest {

    /**
     * 新的过期时间（分钟）（必填）
     * 例如：
     *   传入 60 = 将过期时间延长到1小时后
     *   传入 1440 = 将过期时间延长到1天后
     *
     * @NotNull：必须提供新的过期时间，不允许传 null
     * Service 层会用 当前时间 + expireMinutes 重新计算 expireAt
     */
    @NotNull(message = "过期时间不能为空")
    private Integer expireMinutes;
}
