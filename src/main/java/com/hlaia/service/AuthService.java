package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.LoginRequest;
import com.hlaia.dto.request.RegisterRequest;
import com.hlaia.dto.response.AuthResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import com.hlaia.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 【认证业务逻辑层】—— 处理用户注册、登录、登出、Token 刷新等核心认证逻辑
 *
 * ============================================================
 * 什么是 Service 层？为什么业务逻辑不写在 Controller 里？
 * ============================================================
 *   Spring MVC 的经典三层架构：
 *     Controller（控制器层）→ Service（业务逻辑层）→ Mapper（数据访问层）
 *
 *   Controller 的职责：接收请求、参数校验、调用 Service、返回响应。
 *     它是"前台接待员"，只负责"收发"，不处理具体业务。
 *
 *   Service 的职责：执行具体的业务逻辑。
 *     它是"业务专家"，知道注册要检查用户名是否存在、密码要加密等规则。
 *
 *   为什么要分层？
 *     1. 职责单一：每个层只做一件事，代码更清晰
 *     2. 复用性：同一个 Service 方法可以被多个 Controller 调用
 *        （比如管理员也可以调用注册方法来创建用户）
 *     3. 可测试性：测试 Service 时不需要启动整个 Web 服务器
 *     4. 事务管理：@Transactional 注解只能加在 Service 层
 *
 *   类比：
 *     Controller = 餐厅服务员（点单、上菜）
 *     Service    = 后厨厨师（做菜、调味）
 *     Mapper     = 仓库管理员（取食材、存食材）
 *
 * ============================================================
 * 本类用到的依赖注入（通过构造函数注入）
 * ============================================================
 *   - UserMapper          数据库操作，查用户、插用户
 *   - PasswordEncoder     BCrypt 密码加密与比对
 *   - JwtTokenProvider    JWT Token 的生成与验证
 *   - StringRedisTemplate Redis 操作，用于 Token 黑名单
 *
 * @Service 注解：将这个类标记为 Spring 的 Service Bean，
 *   Spring 容器启动时会自动创建它的实例，并注入所需的依赖。
 *
 * @RequiredArgsConstructor：Lombok 注解，为所有 final 字段生成构造函数。
 *   Spring 会自动调用这个构造函数完成依赖注入。
 *   这是 Spring 推荐的注入方式，比 @Autowired 字段注入更好：
 *     - final 字段保证不可变（线程安全）
 *     - 构造函数参数明确，一眼看出这个类依赖什么
 *     - 方便单元测试（直接 new 对象传入 mock）
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户数据访问层 —— 用于查询和保存用户信息到数据库 */
    private final UserMapper userMapper;

    /**
     * 密码编码器 —— BCrypt 算法实现
     * 注册时用来加密密码，登录时用来比对密码
     */
    private final PasswordEncoder passwordEncoder;

    /** JWT Token 工具类 —— 用于生成 Access Token 和 Refresh Token */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Redis 模板 —— 用于操作 Redis 缓存
     * 在这里主要用于实现"Token 黑名单"机制（logout 时将 Token 加入黑名单）
     *
     * StringRedisTemplate 是 Spring Data Redis 提供的工具类，
     * 专门用于操作字符串类型的 Redis 数据。
     */
    private final StringRedisTemplate redisTemplate;

    // ==================== 注册 ====================

    /**
     * 用户注册 —— 创建新用户并返回认证信息
     *
     * 业务流程：
     *   1. 检查用户名是否已存在（用户名必须唯一）
     *   2. 创建 User 对象，设置用户名和加密后的密码
     *   3. 设置默认角色为 "USER"（普通用户）
     *   4. 设置状态为 0（正常，未封禁）
     *   5. 插入数据库
     *   6. 生成并返回 JWT Token（注册成功后自动"登录"）
     *
     * 为什么要用 LambdaQueryWrapper？
     *   LambdaQueryWrapper 是 MyBatis-Plus 提供的查询构造器。
     *   使用 Lambda 表达式（User::getUsername）引用字段名，
     *   这样即使数据库字段名改了，编译器也会报错提醒，不会出现"字段名拼错"的 bug。
     *   等价 SQL：SELECT COUNT(*) FROM user WHERE username = ?
     *
     * @param request 注册请求数据（包含用户名和密码）
     * @return 认证响应（包含 Token 和用户信息）
     * @throws BusinessException 如果用户名已存在
     */
    public AuthResponse register(RegisterRequest request) {
        // 第一步：检查用户名是否已被注册
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            // 用户名已存在，抛出业务异常
            // 不用 return error，而是 throw 异常，让 GlobalExceptionHandler 统一处理
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        //检查昵称是否已存在 (昵称需唯一)
        if(request.getNickname() != null && !request.getNickname().isEmpty()) {
            Long nicknameCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getNickname, request.getNickname())
            );
            if (nicknameCount > 0) {
                //昵称已存在，抛出业务异常
                throw new BusinessException(ErrorCode.NICKNAME_EXISTS);
            }
        }

        // 第二步：创建用户对象并设置属性
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname()); //如果没填昵称，这里为null

        // 密码加密：绝不能明文存储密码！
        // passwordEncoder.encode() 使用 BCrypt 算法加密密码
        // 每次调用都会生成不同的密文（因为内置了随机盐值）
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // 默认角色为 "USER"（普通用户）
        // 不允许注册时自己指定角色（防止提权攻击）
        user.setRole("USER");

        // 状态 0 = 正常（未封禁）
        user.setStatus(0);

        // 第三步：保存到数据库
        userMapper.insert(user);

        // 第四步：生成 Token 并返回（注册成功 = 自动登录）
        return generateAuthResponse(user);
    }

    // ==================== 登录 ====================

    /**
     * 用户登录 —— 验证身份并返回认证信息
     *
     * 业务流程：
     *   1. 根据用户名查询数据库
     *   2. 验证密码是否正确
     *   3. 检查用户是否被封禁
     *   4. 生成并返回 JWT Token
     *
     * 安全设计要点：
     *   - 用户不存在和密码错误返回相同的错误信息（INVALID_CREDENTIALS）
     *     这是为了防止攻击者通过不同的错误信息来判断某个用户名是否存在
     *     （用户名枚举攻击）
     *   - 密码比对使用 passwordEncoder.matches() 而不是 String.equals()
     *     因为数据库中存储的是 BCrypt 密文，不能直接比较明文
     *
     * BCrypt.matches(明文, 密文) 的原理：
     *   它并不是"解密密文后与明文比较"，而是：
     *   1. 从密文中提取出盐值（Salt）和 cost factor
     *      BCrypt 密文格式：$2a$10$Salt(22字符)Hash(31字符)
     *   2. 用提取出的盐值和 cost factor 对明文进行同样的哈希运算
     *   3. 比较两个哈希值是否相同
     *   所以 BCrypt 是不可逆的——只能验证，不能解密。
     *
     * @param request 登录请求数据（包含用户名和密码）
     * @return 认证响应（包含 Token 和用户信息）
     * @throws BusinessException 如果用户名或密码错误，或用户被封禁
     */
    public AuthResponse login(LoginRequest request) {
        // 第一步：根据用户名查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        // 第二步：验证用户是否存在 + 密码是否正确
        // 注意：用户不存在和密码错误使用同一个错误码，防止用户名枚举攻击
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 第三步：检查用户是否被封禁
        // status == 1 表示被封禁，需要拒绝登录
        if (user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        // 第四步：验证通过，生成 Token
        return generateAuthResponse(user);
    }

    // ==================== 登出 ====================

    /**
     * 用户登出 —— 将 Token 加入 Redis 黑名单
     *
     * ============================================================
     * 为什么需要"黑名单"机制？JWT 不是说过期就自动失效吗？
     * ============================================================
     *
     * JWT 的核心特性是"无状态"——服务器不存储 Token 信息，
     * 只靠 Token 自身的过期时间来判断是否有效。
     * 这带来一个问题：如果用户点"退出登录"，或者 Token 被盗，
     * 在 Token 过期之前，它依然是有效的！
     *
     * 黑名单机制的思路：
     *   在 Redis 中记录"哪些 Token 已被主动注销"，
     *   每次验证 Token 时，除了检查过期时间，还要检查它是否在黑名单中。
     *   如果在黑名单中，即使没过期也视为无效。
     *
     * 具体实现：
     *   - Key：  "jwt:blacklist:" + Token 字符串
     *   - Value："1"（任意值，我们只需要知道这个 Key 存不存在）
     *   - TTL：  Token 的剩余有效时间（毫秒）
     *            过了这个时间，Token 本身就过期了，黑名单记录也可以自动删除
     *
     * 为什么 TTL 要设为 Token 的剩余有效时间？
     *   1. 节省 Redis 内存：Token 过期后黑名单记录也没用了，自动清除
     *   2. 精确控制：黑名单的有效期和 Token 的有效期完全同步
     *
     *   类比：Token 就像一张电影票（有效期到电影结束），
     *         黑名单就像"已退票记录"。退票后这张票即使还没到期也不能用了。
     *         电影结束后（Token 过期），退票记录也没用了，可以清理掉。
     *
     * @param token JWT Token 字符串（不含 "Bearer " 前缀）
     */
    public void logout(String token) {
        // 计算 Token 的剩余有效时间（毫秒）
        // getExpirationFromToken 返回过期时间的 Date，需要减去当前时间得到剩余毫秒数
        Date expiration = jwtTokenProvider.getExpirationFromToken(token);
        long remainingMs = expiration.getTime() - System.currentTimeMillis();

        if (remainingMs > 0) {
            // 将 Token 存入 Redis 黑名单，TTL = 剩余有效时间
            // TimeUnit.MILLISECONDS 表示 remainingMs 的单位是毫秒
            redisTemplate.opsForValue().set(
                    "jwt:blacklist:" + token, "1", remainingMs, TimeUnit.MILLISECONDS);
        }
        // 如果 remainingMs <= 0，说明 Token 已经过期了，不需要加入黑名单
        // 过期的 Token 本身就无法通过验证，加黑名单是多余的操作
    }

    // ==================== 刷新 Token ====================

    /**
     * 刷新 Token —— 用 Refresh Token 换取新的 Access Token
     *
     * 业务流程：
     *   1. 验证 Refresh Token 是否有效（格式正确、未过期、签名正确）
     *   2. 检查 Refresh Token 是否在黑名单中（防止重复使用）
     *   3. 从 Token 中提取用户 ID，查询用户是否存在
     *   4. 将旧的 Refresh Token 加入黑名单（一次性使用，用完即废）
     *   5. 生成新的 Token 对返回
     *
     * 为什么要"用完即废"（One-Time Use）？
     *   如果 Refresh Token 可以重复使用，那么：
     *   - 攻击者截获了一个 Refresh Token
     *   - 他可以用它无限次地获取新的 Access Token
     *   - 即使原始用户刷新了一次，攻击者之前截获的 Token 依然有效
     *   所以每次使用后必须作废旧的，发一个新的。
     *
     * 为什么 logout 和 refresh 中都要检查黑名单？
     *   - logout 中不需要检查黑名单（把已注销的 Token 再注销一次没有副作用）
     *   - refresh 中必须检查，因为如果 Refresh Token 已被注销（用户已登出），
     *     就不应该允许用它来获取新的 Token
     *
     * @param refreshToken 刷新令牌
     * @return 新的认证响应（包含新的 Access Token 和 Refresh Token）
     * @throws BusinessException 如果 Token 无效或用户不存在
     */
    public AuthResponse refresh(String refreshToken) {
        // 第一步：验证 Token 的基本有效性（格式、签名、过期时间）
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 第二步：检查 Token 是否在黑名单中（是否已被注销）
        String blacklistKey = "jwt:blacklist:" + refreshToken;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            // Token 已被加入黑名单，说明用户已登出或该 Token 已被使用过
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 第三步：从 Token 中提取用户 ID，查询用户信息
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            // 用户已被删除（比如管理员删除了该用户）
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 第四步：将旧的 Refresh Token 加入黑名单（一次性使用，用完即废）
        Date expiration = jwtTokenProvider.getExpirationFromToken(refreshToken);
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(blacklistKey, "1", remainingMs, TimeUnit.MILLISECONDS);
        }

        // 第五步：生成新的 Token 对
        return generateAuthResponse(user);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成认证响应 —— 生成 Access Token + Refresh Token 并封装为 AuthResponse
     *
     * 为什么这个方法是 private 的？
     *   这是一个内部辅助方法，只在 register、login、refresh 中复用。
     *   不对外暴露，因为"生成 Token"是实现细节，外部不需要知道。
     *
     * 为什么要同时生成 Access Token 和 Refresh Token？
     *   双 Token 机制的安全设计：
     *   - Access Token：有效期短（如 30 分钟），用于日常接口访问
     *     即使泄露，影响时间窗口很小
     *   - Refresh Token：有效期长（如 7 天），仅用于刷新 Access Token
     *     使用频率低，被截获的概率更小
     *
     *   如果只用一个 Token：
     *     有效期短 → 用户频繁重新登录，体验差
     *     有效期长 → 泄露后影响大，安全风险高
     *     双 Token 完美解决了这个矛盾。
     *
     * AuthResponse.builder() 的用法：
     *   因为 AuthResponse 使用了 @Builder 注解，
     *   所以可以用链式调用创建对象，比传统的 new + setter 更优雅。
     *
     * @param user 用户实体
     * @return 包含 Token 和用户信息的认证响应
     */
    private AuthResponse generateAuthResponse(User user) {
        // 生成 Access Token（短期，用于接口访问）
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getNickname(), user.getRole());

        // 生成 Refresh Token（长期，用于刷新 Access Token）
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), user.getUsername(), user.getNickname(), user.getRole());

        // 使用 Builder 模式构建响应对象
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }
}
