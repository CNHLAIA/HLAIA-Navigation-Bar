package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【操作日志 Mapper 接口】—— 操作日志表的数据库操作接口
 *
 * 继承 BaseMapper<OperationLog> 后自动拥有对 operation_log 表的 CRUD 操作。
 *
 * 操作日志的使用方式：
 *   - 写入：由 AOP 切面自动捕获操作并插入记录（不需要手动调用）
 *   - 查询：管理员可以分页查询操作日志，用于审计
 *   - 不需要更新和删除：日志是只增不改的
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
