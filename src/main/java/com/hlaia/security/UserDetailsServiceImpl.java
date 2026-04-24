package com.hlaia.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 【Spring Security 用户详情服务实现】—— 从数据库加载用户信息
 *
 * ============================================================
 * 这个类在认证流程中的位置：
 * ============================================================
 *   用户登录请求（POST /api/auth/login，携带用户名和密码）
 *       ↓
 *   AuthController 调用 AuthService.login()
 *       ↓
 *   AuthService 通过 AuthenticationManager.authenticate() 进行认证
 *       ↓
 *   Spring Security 自动调用 UserDetailsService.loadUserByUsername()
 *       ↓
 *   本类从数据库查询用户 → 返回 UserDetails 对象
 *       ↓
 *   Spring Security 自动比对密码（BCrypt 匹配）
 *       ↓
 *   认证成功 → AuthService 生成 JWT Token 返回给客户端
 *
 * ============================================================
 * 什么是 UserDetailsService？
 * ============================================================
 *   UserDetailsService 是 Spring Security 提供的一个接口，
 *   它只有一个方法：loadUserByUsername(username)
 *
 *   Spring Security 的认证流程需要知道：
 *     1. 用户是谁？（用户名、ID 等）
 *     2. 密码是什么？（用来和用户输入的密码比对）
 *     3. 有什么权限？（用来做权限控制）
 *
 *   这三个问题的答案都来自 UserDetails 接口。
 *   UserDetailsService 的职责就是：根据用户名，从数据源（数据库、LDAP 等）
 *   加载用户信息，封装成 UserDetails 对象返回给 Spring Security。
 *
 * ============================================================
 * 什么是 SimpleGrantedAuthority？
 * ============================================================
 *   GrantedAuthority 代表一个权限（或者说"角色"）。
 *   Spring Security 的约定：角色名需要加 "ROLE_" 前缀。
 *
 *   例如数据库中存储的 role 是 "ADMIN"，
 *   那么对应的 Authority 就是 "ROLE_ADMIN"。
 *
 *   之后在控制器上可以使用注解进行权限控制：
 *     @PreAuthorize("hasRole('ADMIN')")   ← 会自动匹配 "ROLE_ADMIN"
 *     @PreAuthorize("hasAuthority('ROLE_ADMIN')")  ← 直接匹配
 *
 * @Service 注解：标记这是一个业务层组件，由 Spring 容器管理。
 *   和 @Component 功能一样，但语义更明确——表示这是业务逻辑层。
 *
 * @RequiredArgsConstructor：Lombok 注解，自动为所有 final 字段生成构造函数。
 *   等价于手写：
 *     public UserDetailsServiceImpl(UserMapper userMapper) {
 *         this.userMapper = userMapper;
 *     }
 *   Spring 会自动调用这个构造函数，把 UserMapper 的实现注入进来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * 用户 Mapper —— 用于查询数据库中的用户信息
     *
     * 为什么用 final？
     *   final 字段必须在构造函数中赋值，配合 @RequiredArgsConstructor，
     *   Lombok 会自动生成包含这个字段的构造函数，实现依赖注入。
     *   final 还保证了引用不可变——一旦注入就不能被替换，线程安全。
     */
    private final UserMapper userMapper;

    /**
     * 根据用户名从数据库加载用户信息 —— Spring Security 认证流程的入口
     *
     * 这个方法什么时候被调用？
     *   当调用 AuthenticationManager.authenticate() 时，
     *   Spring Security 会自动调用这个方法来获取用户信息，
     *   然后将返回的密码与用户输入的密码进行比对。
     *
     * 什么是 UsernameNotFoundException？
     *   Spring Security 定义的异常，表示"找不到该用户"。
     *   Spring Security 会把它转换为 401 Unauthorized 响应。
     *
     * @param username 用户输入的用户名
     * @return UserDetails 对象，包含用户名、密码和权限列表
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ============================================================
        // LambdaQueryWrapper —— MyBatis-Plus 的条件构造器
        // ============================================================
        // LambdaQueryWrapper<User> 使用 Lambda 表达式引用字段名，
        // 优点是编译时检查字段名，拼错会报错（而不是运行时才发现）。
        //
        // 等价的 SQL：SELECT * FROM user WHERE username = 'admin'
        //
        // 与手写 SQL 对比：
        //   手写：WHERE username = 'admin'  → 拼错字段名运行时才报错
        //   Lambda：eq(User::getUsername, ...) → 编译时就能发现错误
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        // 用户不存在 → 抛出 UsernameNotFoundException
        // Spring Security 会捕获这个异常，返回认证失败
        if (user == null) {
            log.warn("登录失败：用户名不存在 - {}", username);
            throw new UsernameNotFoundException("用户名不存在: " + username);
        }

        // 用户被封禁 → 抛出 BusinessException
        // status 字段：0=正常，1=封禁
        // 这里不抛 UsernameNotFoundException 而是抛 BusinessException，
        // 是因为"被封禁"和"不存在"是不同的业务含义，前端需要不同的提示
        if (user.getStatus() == 1) {
            log.warn("登录失败：用户已被封禁 - {}", username);
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        // ============================================================
        // 构建 Spring Security 的 User 对象
        // ============================================================
        // org.springframework.security.core.userdetails.User 是 Spring Security
        // 提供的 UserDetails 接口的内置实现类。
        //
        // 构造参数说明：
        //   1. username → 用户名（用于显示和日志）
        //   2. password → 加密后的密码（BCrypt 密文，Spring Security 会自动比对）
        //   3. authorities → 权限列表（决定用户能做什么）
        //
        // Collections.singletonList() 创建一个只包含一个元素的不可变列表。
        // 我们的用户只有一种角色，所以列表中只有一个元素。

        // "ROLE_" + role 是 Spring Security 的命名约定
        // 例如 role="ADMIN" → authority="ROLE_ADMIN"
        // 之后使用 @PreAuthorize("hasRole('ADMIN')") 时，Spring Security 会自动加 "ROLE_" 前缀匹配
        String authority = "ROLE_" + user.getRole();

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(authority))
        );
    }

    /**
     * 根据用户名获取用户实体 —— 供 Service 层调用
     *
     * 为什么需要这个方法？
     *   loadUserByUsername 返回的是 Spring Security 的 UserDetails（安全框架用），
     *   而 Service 层（如 AuthService）需要的是我们的 User 实体（包含 ID、邮箱等完整信息）。
     *   所以需要一个单独的方法来获取 User 实体。
     *
     * 为什么不直接复用 loadUserByUsername？
     *   1. 返回类型不同：UserDetails vs User
     *   2. 异常处理不同：loadUserByUsername 抛 UsernameNotFoundException，
     *      而业务层需要抛 BusinessException（被 GlobalExceptionHandler 统一处理）
     *   3. 封禁检查逻辑不同：安全层需要区分"不存在"和"被封禁"，业务层可能不需要
     *
     * @param username 用户名
     * @return User 实体对象
     * @throws BusinessException 用户不存在时抛出 USER_NOT_FOUND
     */
    public User getUserByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return user;
    }
}
