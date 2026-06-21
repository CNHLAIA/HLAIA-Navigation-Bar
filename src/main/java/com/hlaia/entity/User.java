package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 【用户实体类】—— 对应数据库中的 user 表
 *
 * 什么是实体类（Entity）？
 *   实体类是 Java 对象和数据库表之间的桥梁。
 *   类名对应表名，字段名对应列名，一个对象对应表中的一行记录。
 *   例如：一个 User 对象就代表 user 表中的一个用户。
 *
 * 什么是 ORM（对象关系映射）？
 *   ORM 框架（我们用的是 MyBatis-Plus）会自动完成：
 *   - 查询数据库 → 把结果自动映射成 Java 对象
 *   - 保存 Java 对象 → 自动生成 INSERT SQL 语句
 *   这样我们就不需要手写大量 SQL 了
 */
@Data   // Lombok 注解：自动生成 getter、setter、toString、equals、hashCode 方法
@TableName("user")   // MyBatis-Plus 注解：指定这个类对应数据库中的 "user" 表
public class User {

    /**
     * @TableId 主键注解
     * type = IdType.AUTO 表示主键自增策略（数据库自动生成递增的 ID）
     * 其他策略还有：ASSIGN_ID（雪花算法）、INPUT（手动输入）等
     */
    @TableId(type = IdType.AUTO)
    private Long id;           // 用户ID，数据库中是 BIGINT，Java 中用 Long（包装类型）

    private String username;   // 用户名，对应数据库 VARCHAR(50)
    private String password;   // 密码，存储的是 BCrypt 加密后的密文，不是明文！
    private String email;      // 邮箱地址（可选字段）
    private String nickname;   // 昵称 (可选字段x`)

    /**
     * 角色：用字符串存储
     * "ADMIN" = 管理员，可以管理所有用户
     * "USER"  = 普通用户，只能操作自己的数据
     * 不用 ENUM 类型是因为：VARCHAR 更灵活，以后添加新角色不需要改表结构
     */
    private String role;

    /**
     * 状态：0=正常，1=封禁
     * 管理员可以封禁违规用户，封禁后用户无法登录
     */
    private Integer status;

    /**
     * 创建时间和更新时间
     * 使用 Java 8 的 LocalDateTime 类型，对应数据库的 DATETIME
     * createdAt 由数据库自动填充（DEFAULT CURRENT_TIMESTAMP）
     * updatedAt 由数据库自动更新（ON UPDATE CURRENT_TIMESTAMP）
     */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
