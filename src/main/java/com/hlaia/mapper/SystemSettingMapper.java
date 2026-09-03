package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.SystemSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【系统设置 Mapper 接口】—— system_setting 表的数据库操作接口
 *
 * 继承 BaseMapper<SystemSetting> 后自动拥有 CRUD 操作。
 * 设置表永远按 setting_key 点查（主键），没有复杂查询场景。
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {
}
