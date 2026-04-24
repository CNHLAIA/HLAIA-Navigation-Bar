package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.Folder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【文件夹 Mapper 接口】—— 文件夹表的数据库操作接口
 *
 * 继承 BaseMapper<Folder> 后自动拥有对 folder 表的 CRUD 操作。
 *
 * 文件夹的特殊查询场景：
 *   - 查询某个用户的所有文件夹：selectList(new LambdaQueryWrapper<Folder>().eq(Folder::getUserId, userId))
 *   - 查询某个文件夹的子文件夹：selectList(new LambdaQueryWrapper<Folder>().eq(Folder::getParentId, parentId))
 *   这些查询会在 Service 层通过 MyBatis-Plus 的 QueryWrapper 来构建，不需要在 Mapper 里写。
 */
@Mapper
public interface FolderMapper extends BaseMapper<Folder> {
}
