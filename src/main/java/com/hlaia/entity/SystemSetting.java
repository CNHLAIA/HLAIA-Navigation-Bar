package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【系统设置实体类】—— 对应数据库中的 system_setting 表
 *
 * key-value 结构的系统级配置，当前用途：
 *   favicon.proxy.enabled / favicon.proxy.host / favicon.proxy.port
 *   （图标抓取代理，管理员在设置页配置，见 SystemSettingService）
 *
 * 值统一存字符串（setting_value），类型转换由读取方负责——
 * 这样未来增加新设置项不需要改表结构。
 */
@Data
@TableName("system_setting")
public class SystemSetting {

    /** 设置键，如 "favicon.proxy.host" */
    @TableId(type = IdType.INPUT)   // 主键是业务指定的字符串键，不是自增
    private String settingKey;

    /** 设置值（统一字符串存储），NULL 视为未配置 */
    private String settingValue;

    /** 最后修改时间，由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    private LocalDateTime updatedAt;
}
