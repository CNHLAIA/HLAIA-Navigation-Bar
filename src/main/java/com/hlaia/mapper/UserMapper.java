package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 【用户 Mapper 接口】—— 用户表的数据库操作接口
 *
 * 什么是 Mapper？
 *   Mapper 是 MyBatis-Plus 中的数据访问层（DAO）接口。
 *   它定义了如何对数据库表进行增删改查（CRUD）操作。
 *
 * 什么是 BaseMapper<T>？
 *   这是 MyBatis-Plus 提供的通用 Mapper，泛型 T 指定实体类类型。
 *   继承它之后，不需要写任何方法，就自动拥有了以下常用操作：
 *
 *   - insert(entity)              插入一条记录
 *   - deleteById(id)              根据 ID 删除
 *   - updateById(entity)          根据 ID 更新
 *   - selectById(id)              根据 ID 查询
 *   - selectList(wrapper)         条件查询列表
 *   - selectPage(page, wrapper)   分页查询
 *   - selectCount(wrapper)        查询总数
 *   ... 还有更多
 *
 *   这些方法都是 MyBatis-Plus 自动实现的，不需要我们写 SQL！
 *
 * 如果需要自定义 SQL，可以在这里声明方法，然后在 resources/mapper/ 下写 XML 映射文件。
 */
@Mapper   // MyBatis 注解：告诉 Spring 这是一个 Mapper 接口，让 Spring 自动创建它的实现类（代理对象）
public interface UserMapper extends BaseMapper<User> {
    // 不需要写任何方法！BaseMapper 已经提供了所有常用的 CRUD 操作
    // 如果以后有复杂的自定义查询需求，可以在这里添加方法声明
}
