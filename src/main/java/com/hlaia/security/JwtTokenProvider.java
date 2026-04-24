package com.hlaia.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 【JWT Token 工具类】—— 负责生成、解析、验证 JWT Token
 *
 * ============================================================
 * 什么是 JWT（JSON Web Token）？
 * ============================================================
 *   JWT 是一种开放标准（RFC 7519），用于在各方之间安全地传输信息。
 *   它的工作原理可以类比为我们日常生活中的"电子门票"：
 *
 *   想象你去游乐园：
 *     1. 你在入口处出示身份证（用户名+密码）→ 管理员给你一张手环（JWT Token）
 *     2. 之后玩任何项目，只需出示手环即可 → 不用每次都出示身份证
 *     3. 手环上刻了你的信息（姓名、VIP等级等）→ 不需要查系统就知道你是谁
 *     4. 手环有过期时间 → 过期后需要重新在入口处领取
 *
 *   JWT 的核心优势：**无状态（Stateless）**
 *     服务器不需要存储 Session，所有信息都包含在 Token 中，
 *     这让水平扩展（部署多台服务器）变得非常简单。
 *
 * ============================================================
 * JWT 的结构：Header.Payload.Signature
 * ============================================================
 *   一个 JWT 看起来像这样：
 *     eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYiLCJyb2xlIjoiQURNSU4ifQ.xxx
 *     \_____________/  \___________________________________/  \_/
 *         Header                   Payload                   Signature
 *
 *   1. Header（头部）—— 告诉接收方这是一个 JWT，以及使用的签名算法
 *      { "alg": "HS256", "typ": "JWT" }
 *      - alg: 签名算法，我们用的是 HMAC-SHA256
 *      - typ: 固定为 "JWT"
 *
 *   2. Payload（载荷）—— 实际传输的数据，也就是"手环上刻的信息"
 *      { "sub": "123456", "username": "admin", "role": "ADMIN", "exp": 1700000000 }
 *      - sub（Subject）：主题，这里存的是用户 ID
 *      - username：自定义字段，存用户名
 *      - role：自定义字段，存用户角色
 *      - iat（Issued At）：签发时间
 *      - exp（Expiration）：过期时间
 *
 *   3. Signature（签名）—— 用来验证 Token 没有被篡改
 *      HMACSHA256(base64(header) + "." + base64(payload), secret)
 *      - 只有持有 secret 密钥的人才能生成正确的签名
 *      - 任何对 Token 的篡改都会导致签名验证失败
 *
 * ============================================================
 * 这个类在整个认证流程中的位置：
 * ============================================================
 *   用户注册/登录 → AuthController → AuthService → JwtTokenProvider.generateToken()
 *                                                       ↓ 生成 Token 返回给客户端
 *   后续请求携带 Token → JwtAuthFilter → JwtTokenProvider.validateToken()
 *                                           ↓ 验证通过，提取用户信息
 *                                      SecurityContextHolder 设置认证信息
 *
 * @Component 注解：将这个类注册为 Spring Bean，由 Spring 容器管理其生命周期。
 *   其他类可以通过构造函数注入来使用它。
 */
@Slf4j   // Lombok 注解：自动生成 log 对象（等价于 private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class)）
@Component
public class JwtTokenProvider {

    /**
     * 签名密钥 —— 用于签名和验证 Token 的"钥匙"
     *
     * 什么是 SecretKey？
     *   HMAC-SHA256 算法需要一个密钥来签名和验证。
     *   可以类比为：
     *     - secret 配置值就像"公章上的刻字"
     *     - 只有拥有这个"公章"的人才能签发 Token（盖章）
     *     - 任何人都可以验证签名（检查公章真伪），但无法伪造
     *
     *   安全要求：密钥长度必须 >= 256 位（32 字节），否则 HMAC-SHA256 不安全
     */
    private final SecretKey key;

    /** Access Token（访问令牌）的过期时间，单位：毫秒 */
    private final long accessTokenExpiration;

    /** Refresh Token（刷新令牌）的过期时间，单位：毫秒 */
    private final long refreshTokenExpiration;

    /**
     * 构造函数注入 —— Spring 推荐的依赖注入方式
     *
     * 什么是 @Value？
     *   从 application.yml 配置文件中读取值注入到字段中。
     *   例如 application.yml 中有：
     *     jwt:
     *       secret: "my-secret-key"
     *       access-token-expiration: 86400000
     *       refresh-token-expiration: 604800000
     *
     *   @Value("${jwt.secret}") 就会读取 jwt.secret 的值。
     *
     * 为什么用构造函数注入而不是 @Autowired 字段注入？
     *   1. 不可变性：字段可以是 final 的，一旦赋值就不能修改
     *   2. 强制依赖：如果缺少必要的依赖，Spring 启动时就会报错
     *   3. 方便测试：测试时可以直接 new 对象传入 mock 值
     *   4. 线程安全：final 字段天然线程安全
     *
     * @param secret                    JWT 签名密钥字符串
     * @param accessTokenExpiration     访问令牌过期时间（毫秒）
     * @param refreshTokenExpiration    刷新令牌过期时间（毫秒）
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        // Keys.hmacShaKeyFor() 是 jjwt 库提供的方法
        // 它将字符串密钥转换为 HMAC-SHA 算法所需的 SecretKey 对象
        // 内部会处理 Base64 编码和密钥长度的校验
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // ==================== Token 生成方法 ====================

    /**
     * 生成 Access Token（访问令牌）
     *
     * 什么是 Access Token？
     *   Access Token 是客户端在每次请求时携带的凭证，用于证明"我是谁"。
     *   类比：Access Token 就像"临时通行证"，有效期短（通常 15 分钟 ~ 24 小时）
     *
     * 为什么需要两种 Token（Access + Refresh）？
     *   - Access Token 有效期短 → 即使泄露，危害时间有限
     *   - Refresh Token 有效期长 → 用于在 Access Token 过期后获取新的，而不需要重新登录
     *   - 这样设计可以在安全性和用户体验之间取得平衡
     *
     * @param userId   用户 ID（唯一标识）
     * @param username 用户名
     * @param role     用户角色（ADMIN 或 USER）
     * @return 生成的 JWT Token 字符串
     */
    public String generateAccessToken(Long userId, String username, String nickname, String role) {
        Date now = new Date();
        // 计算过期时间 = 当前时间 + 过期时长
        Date expiration = new Date(now.getTime() + accessTokenExpiration);
        return buildToken(userId, username, nickname, role, now, expiration);
    }

    /**
     * 生成 Refresh Token（刷新令牌）
     *
     * 什么是 Refresh Token？
     *   Refresh Token 的有效期比 Access Token 长得多（通常 7 天 ~ 30 天）。
     *   它的唯一用途：当 Access Token 过期后，用它来换取新的 Access Token。
     *   用户不需要重新输入用户名密码。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     用户角色
     * @return 生成的 Refresh Token 字符串
     */
    public String generateRefreshToken(Long userId, String username, String nickname, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);
        return buildToken(userId, username, nickname, role, now, expiration);
    }

    /**
     * 构建 Token 的核心方法 —— 把用户信息打包成 JWT 字符串
     *
     * Jwts.builder() 的工作流程（建造者模式）：
     *   就像填写一张"通行证表格"：
     *   1. subject()  → 填写"持证人编号"（用户 ID）
     *   2. claim()    → 填写"持证人姓名"、"持证人等级"（自定义字段）
     *   3. issuedAt() → 填写"签发日期"
     *   4. expiration() → 填写"有效期至"
     *   5. signWith() → 盖上"公章"（签名）
     *   6. compact()  → 将表格塑封成一张不可篡改的卡片（生成字符串）
     *
     * @param userId     用户 ID，作为 JWT 的 subject
     * @param username   用户名，作为自定义 claim
     * @param nickname   用户昵称，作为自定义 claim（可为 null）
     * @param role       用户角色，作为自定义 claim
     * @param now        签发时间
     * @param expiration 过期时间
     * @return 完整的 JWT 字符串
     */
    private String buildToken(Long userId, String username, String nickname, String role,
                              Date now, Date expiration) {
        return Jwts.builder()
                // subject（主题）：JWT 的标准字段，通常存放唯一标识符
                // 这里存用户 ID，解析时可以通过 getSubject() 获取
                .subject(userId.toString())
                // claim（声明）：自定义字段，可以存放任意键值对
                // 这里存放用户名、昵称和角色，方便后续使用
                .claim("username", username)
                .claim("nickname", nickname)
                .claim("role", role)
                // iat（Issued At）：Token 的签发时间
                .issuedAt(now)
                // exp（Expiration）：Token 的过期时间
                .expiration(expiration)
                // signWith：使用密钥和算法进行签名
                // key 是 HMAC-SHA256 的密钥，Jwts.SIG.HS256 是签名算法
                .signWith(key, Jwts.SIG.HS256)
                // compact()：将所有信息序列化为 Base64Url 编码的字符串
                // 最终格式：xxxxx.yyyyy.zzzzz（Header.Payload.Signature）
                .compact();
    }

    // ==================== Token 解析方法 ====================

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT Token 字符串
     * @return 用户 ID
     */
    public Long getUserIdFromToken(String token) {
        // getSubject() 返回的是我们在 buildToken 中设置的 userId.toString()
        // 所以这里需要把字符串转换回 Long 类型
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 从 Token 中提取用户角色
     *
     *
     * @param token JWT Token 字符串
     * @return 角色字符串（如 "ADMIN" 或 "USER"）
     */
    public String getRoleFromToken(String token) {
        // get("role", String.class) 从 Payload 的自定义字段中获取 role 的值
        return parseToken(token).get("role", String.class);
    }

    /**
     * 从 Token 中提取过期时间
     *
     * @param token JWT Token 字符串
     * @return 过期时间的 Date 对象
     */
    public Date getExpirationFromToken(String token) {
        return parseToken(token).getExpiration();
    }

    /**
     * 解析 Token 的核心方法 —— 将 JWT 字符串拆解为 Claims 对象
     *
     * 什么是 Claims？
     *   Claims 就是 JWT Payload 部分的数据。它是一个键值对集合，
     *   包含了标准字段（sub, iat, exp 等）和自定义字段（username, role）。
     *
     * parseSignedClaims 的流程：
     *   1. 将 Token 按 "." 分割成 3 部分
     *   2. Base64 解码 Header 和 Payload
     *   3. 用密钥重新计算签名，对比是否一致（验证完整性）
     *   4. 检查 exp 是否过期
     *   5. 返回 Claims 对象
     *
     *   如果签名不匹配或 Token 过期，会抛出相应的异常
     *
     * @param token JWT Token 字符串
     * @return Claims 对象，包含 Token 中的所有声明（字段）
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                // verifyWith(key)：设置验证签名用的密钥
                // Token 中的签名会用这个密钥重新计算并比对
                .verifyWith(key)
                .build()
                // parseSignedClaims(token)：解析并验证 Token
                // 这一步会同时验证签名和过期时间
                .parseSignedClaims(token)
                // getPayload()：获取 Payload 部分，即 Claims 对象
                .getPayload();
    }

    // ==================== Token 验证方法 ====================

    /**
     * 验证 Token 是否有效
     *
     * 为什么需要单独的验证方法？
     *   在 JwtAuthFilter 中，我们需要先检查 Token 是否有效，
     *   然后再提取用户信息。如果直接调用 parseToken 可能会抛异常，
     *   所以需要一个返回 boolean 的方法来优雅地处理无效 Token。
     *
     * 可能抛出的异常（JwtException 的子类）：
     *   - ExpiredJwtException：Token 已过期
     *   - UnsupportedJwtException：Token 格式不支持
     *   - MalformedJwtException：Token 格式错误（被篡改或损坏）
     *   - SignatureException：签名验证失败（密钥不匹配）
     *   - IllegalArgumentException：Token 为 null 或空字符串
     *
     * @param token JWT Token 字符串
     * @return true 表示 Token 有效，false 表示无效
     */
    public boolean validateToken(String token) {
        try {
            // 尝试解析 Token，如果没抛异常就说明有效
            parseToken(token);
            return true;
        } catch (JwtException e) {
            // JwtException 是所有 JWT 相关异常的父类
            // 捕获它就能处理所有类型的无效 Token 情况
            log.warn("JWT Token 验证失败: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            // Token 为 null 或空字符串
            log.warn("JWT Token 为空");
            return false;
        }
    }
}
