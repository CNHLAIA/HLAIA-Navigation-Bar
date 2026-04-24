package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.StagingItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【暂存项 Mapper 接口】—— 暂存区表的数据库操作接口
 *
 * 继承 BaseMapper<StagingItem> 后自动拥有对 staging_item 表的 CRUD 操作。
 *
 * 暂存区的特殊操作场景：
 *   - 查询用户的所有暂存项（按创建时间倒序）
 *   - 批量删除过期暂存项（定时任务按 expireAt 字段清理）
 */
@Mapper
public interface StagingItemMapper extends BaseMapper<StagingItem> {
}
