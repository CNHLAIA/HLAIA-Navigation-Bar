package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.Bookmark;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【书签 Mapper 接口】—— 书签表的数据库操作接口
 *
 * 继承 BaseMapper<Bookmark> 后自动拥有对 bookmark 表的 CRUD 操作。
 *
 * 书签的特殊查询场景：
 *   - 查询某个文件夹下的所有书签：按 folderId 查询，按 sortOrder 排序
 *   - 检查 URL 是否已存在：按 userId + url 查询（数据库有唯一索引）
 */
@Mapper
public interface BookmarkMapper extends BaseMapper<Bookmark> {
}
