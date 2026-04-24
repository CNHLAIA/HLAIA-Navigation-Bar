package com.hlaia.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【MyBatis-Plus 配置类】—— 配置 MyBatis-Plus 的插件和全局设置
 *
 * 什么是 @Configuration？
 *   标记这个类是一个"配置类"，相当于一个 XML 配置文件。
 *   Spring 启动时会读取这个类中的 @Bean 方法，创建并注册对应的 Bean（Spring 管理的对象）。
 *
 * 什么是 @Bean？
 *   标记在方法上，表示这个方法返回的对象要交给 Spring 容器管理。
 *   其他地方需要用到时，可以通过 @Autowired 注入，或者 Spring 自动装配。
 *
 * 什么是分页插件（PaginationInnerInterceptor）？
 *   MyBatis-Plus 的分页插件会自动拦截分页查询，添加 LIMIT/OFFSET 语句。
 *   使用方式（在 Service 层）：
 *     Page<Bookmark> page = new Page<>(pageNum, pageSize);  // 第几页，每页几条
 *     bookmarkMapper.selectPage(page, queryWrapper);          // 自动添加分页
 *     page.getRecords();  // 获取当前页的数据列表
 *     page.getTotal();    // 获取总记录数
 *
 *   没有这个插件的话，selectPage 不会自动添加 LIMIT，会返回所有数据！
 */
@Configuration   // 告诉 Spring：这是一个配置类，需要读取其中的 Bean 定义
public class MyBatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器，包含分页插件
     *
     * MybatisPlusInterceptor 是一个拦截器链，可以添加多个内部拦截器：
     * - PaginationInnerInterceptor  分页插件（必须）
     * - OptimisticLockerInnerInterceptor  乐观锁插件（可选，本项目不用）
     * - BlockAttackInnerInterceptor  防全表更新删除插件（可选）
     *
     * @return 配置好的 MyBatis-Plus 拦截器
     */
    @Bean   // 把方法的返回值注册为 Spring Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建拦截器（可以理解为 MyBatis-Plus 的"插件管理器"）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件，指定数据库类型为 MySQL
        // DbType.MYSQL 告诉插件生成 MySQL 语法的分页 SQL（即 LIMIT offset, size）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
