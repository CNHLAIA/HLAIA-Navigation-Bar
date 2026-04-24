package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.MoveToFolderRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.request.StagingUpdateRequest;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.service.StagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【暂存区控制器】—— 处理暂存区相关的 HTTP 请求
 *
 * Controller 层的职责（像一个前台接待员）：
 *   1. 接收 HTTP 请求（解析 URL、请求体、路径参数等）
 *   2. 调用 Service 层处理业务逻辑
 *   3. 将结果包装成统一的 Result 格式返回给前端
 *   Controller 不应该包含任何业务逻辑！
 *
 * RESTful API 路径设计 —— 简洁路径风格：
 *   本控制器使用 /api/staging 作为基础路径，而不是 /api/staging-items。
 *   理由如下：
 *
 *   1. 语义清晰：staging（暂存区）本身就是一个完整的概念，不需要加 -items 后缀
 *      - /api/staging → 暂存区
 *      - /api/staging-items → 暂存区项（啰嗦且不自然）
 *
 *   2. RESTful 资源命名最佳实践：
 *      - 使用简洁的名词，而非名词短语
 *      - 避免在资源名中使用复数形式 + 修饰词（如 staging-items、bookmark-entries）
 *      - 好的例子：/users、/folders、/bookmarks、/staging
 *      - 不好的例子：/user-list、/folder-items、/staging-entries
 *
 *   3. 与项目中其他控制器保持风格一致：
 *      - /api/folders → 文件夹
 *      - /api/bookmarks → 书签
 *      - /api/staging → 暂存区
 *
 * 完整的 API 路由列表：
 *   - GET    /api/staging                    —— 获取暂存区列表
 *   - POST   /api/staging                    —— 添加到暂存区
 *   - PUT    /api/staging/{id}               —— 更新过期时间
 *   - DELETE /api/staging/{id}               —— 删除暂存项
 *   - POST   /api/staging/{id}/move-to-folder —— 移动到文件夹
 *
 * @RestController 注解的作用：
 *   = @Controller + @ResponseBody 的组合注解
 *   - @Controller：告诉 Spring 这是一个控制器类
 *   - @ResponseBody：方法返回值自动序列化为 JSON 响应体
 *   这样每个方法的返回值（Result 对象）会自动转成 JSON 返回给前端
 *
 * @RequestMapping("/api/staging") 的作用：
 *   为控制器中所有方法设置统一的基础路径。
 *   所有方法的路径都会自动加上 /api/staging 前缀。
 *
 * @Tag 注解的作用：
 *   Swagger/OpenAPI 文档分组标签，在 API 文档页面中，
 *   属于同一 @Tag 的接口会被归类到同一个分组下显示。
 */
@RestController
@RequestMapping("/api/staging")
@RequiredArgsConstructor
@Tag(name = "Staging", description = "Staging area APIs")
public class StagingController {

    // 通过构造器注入 StagingService（@RequiredArgsConstructor 自动生成构造方法）
    private final StagingService stagingService;

    /**
     * 获取暂存区列表
     *
     * 路径设计说明 —— @GetMapping 无子路径：
     *   当 @GetMapping 不指定子路径时，它直接匹配基础路径 /api/staging。
     *   也就是说：GET /api/staging 就会调用这个方法。
     *
     *   为什么不写成 @GetMapping("/list") 之类的？
     *   - RESTful 设计原则：GET + 资源根路径 = 获取该资源的列表
     *   - GET /api/staging 语义上就是"获取暂存区的所有内容"
     *   - 不需要加 /list，因为 GET 方法本身就是"查询"的语义
     *   - 业界标准做法：
     *     GET /api/users → 获取用户列表
     *     GET /api/orders → 获取订单列表
     *     GET /api/staging → 获取暂存区列表
     *
     *   与项目中其他控制器的对比：
     *   - BookmarkController 的列表接口是 GET /api/folders/{folderId}/bookmarks
     *     因为书签必须指定文件夹，所以需要路径参数
     *   - StagingController 的列表接口是 GET /api/staging
     *     因为暂存区没有文件夹的概念，直接查询所有即可
     *
     * @AuthenticationPrincipal Long userId 的原理：
     *   Spring Security 在认证成功后，会把用户信息存入 SecurityContext。
     *   在我们的 JwtAuthFilter 中，认证通过后把 userId（Long 类型）设为 principal：
     *     UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
     *         userId,   // principal —— 就是这里 @AuthenticationPrincipal 拿到的值
     *         null,     // credentials（密码，JWT 认证不需要）
     *         authorities  // 权限列表
     *     );
     *   所以 @AuthenticationPrincipal Long userId 可以直接拿到用户的 ID。
     *
     * @param userId 当前登录用户的 ID（由 Spring Security 自动注入）
     * @return 暂存项列表（已按创建时间倒序排列，且已排除过期项）
     */
    @GetMapping
    @Operation(summary = "Get staging items")
    public Result<List<StagingItemResponse>> list(@AuthenticationPrincipal Long userId) {
        return Result.success(stagingService.getStagingItems(userId));
    }

    /**
     * 添加网页到暂存区
     *
     * @Valid 注解的作用：
     *   触发请求体中 DTO 字段上的校验注解（如 @NotBlank、@NotNull）。
     *   如果校验不通过，Spring 会自动抛出 MethodArgumentNotValidException，
     *   GlobalExceptionHandler 会捕获它并返回 400 错误响应。
     *   不加 @Valid 的话，DTO 上的校验注解形同虚设，不会执行校验逻辑。
     *
     * @RequestBody 注解的作用：
     *   告诉 Spring 从 HTTP 请求体（body）中读取 JSON 数据，
     *   并自动反序列化为 Java 对象（StagingCreateRequest）。
     *   例如前端发送 {"title": "百度", "url": "https://www.baidu.com"}，
     *   Spring 会自动把这个 JSON 转成 StagingCreateRequest 对象。
     *
     * @param userId  当前登录用户的 ID
     * @param request 添加到暂存区的请求数据（包含 title、url、可选的 expireMinutes）
     * @return 新创建的暂存项信息
     */
    @PostMapping
    @Operation(summary = "Add to staging area")
    public Result<StagingItemResponse> add(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody StagingCreateRequest request) {
        return Result.success(stagingService.addToStaging(userId, request));
    }

    /**
     * 更新暂存项的过期时间
     *
     * @PathVariable 注解的作用：
     *   从 URL 路径中提取变量值。
     *   URL 中的 {id} 是路径变量占位符，@PathVariable Long id 会把它的值赋给方法参数。
     *   例如：PUT /api/staging/42，那么 id 的值就是 42。
     *
     * @param userId  当前登录用户的 ID
     * @param id      要更新的暂存项 ID（从 URL 路径中获取）
     * @param request 更新请求（包含新的 expireMinutes）
     * @return 更新后的暂存项信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update expiry time")
    public Result<StagingItemResponse> update(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody StagingUpdateRequest request) {
        return Result.success(stagingService.updateExpiry(userId, id, request));
    }

    /**
     * 删除暂存项
     *
     * 注意：删除操作返回 Result<Void>（没有 data 数据），
     * 因为删除成功后，该资源已不存在，没有需要返回的信息。
     *
     * @param userId 当前登录用户的 ID
     * @param id     要删除的暂存项 ID
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete staging item")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        stagingService.deleteStagingItem(userId, id);
        return Result.success();
    }

    /**
     * 将暂存项移动到文件夹（转化为正式书签）
     *
     * POST /{id}/move-to-folder —— 动作型子资源路径设计
     *
     * 什么是"动作型子资源路径"？
     *   在 RESTful API 中，有些操作很难用简单的 CRUD 动词（GET/POST/PUT/DELETE）表达。
     *   "移动暂存项到文件夹"就是这样一个操作——它既不是单纯地创建资源，
     *   也不是更新资源，而是一个"业务动作"（把暂存项转化为书签）。
     *
     *   对于这种"动作型"操作，业界有两种常见的设计方式：
     *
     *   方式一：动作型子资源路径（本项目的选择）
     *     POST /api/staging/{id}/move-to-folder
     *     - 优点：路径本身就能说明操作类型，一看就懂
     *     - 优点：符合"资源 + 动作"的直觉思维
     *     - 缺点：严格来说不符合 REST 纯粹主义（REST 不提倡 URL 中包含动词）
     *
     *   方式二：纯资源路径
     *     POST /api/bookmarks（用已有的创建书签接口）
     *     - 前端在创建书签后，再调 DELETE /api/staging/{id} 删除暂存项
     *     - 优点：严格遵守 REST 规范
     *     - 缺点：前端需要发两次请求，且无法保证原子性
     *
     *   本项目选择方式一，原因：
     *   1. "移动到文件夹"是一个原子操作（创建书签 + 删除暂存项），后端事务保证一致性
     *   2. 如果让前端发两次请求，无法保证原子性（可能创建了书签但没删暂存项）
     *   3. 路径的可读性和实用性优先于 REST 纯粹主义
     *
     *   路径结构解析：
     *   POST /api/staging/{id}/move-to-folder
     *        ↑资源路径↑    ↑子资源动作↑
     *   - /api/staging/{id} → 定位到某个暂存项
     *   - /move-to-folder → 对该暂存项执行"移动到文件夹"的动作
     *   - 请求体中的 folderId → 目标文件夹
     *
     *   这种设计在业界广泛使用：
     *   - GitHub: POST /repos/{owner}/{repo}/forks（Fork 一个仓库）
     *   - Stripe: POST /charges/{id}/capture（捕获一笔支付）
     *   - Slack: POST /channels/{id}/archive（归档一个频道）
     *
     * 为什么用 POST 而不是 PUT 或 PATCH？
     *   - PUT/PATCH 通常用于"更新资源的部分字段"
     *   - "移动到文件夹"不是更新暂存项，而是"触发一个动作"
     *   - 动作型操作通常使用 POST 方法
     *
     * @param userId  当前登录用户的 ID
     * @param id      要移动的暂存项 ID（从 URL 路径中获取）
     * @param request 移动请求（包含目标文件夹 ID）
     * @return 成功响应（无数据）
     */
    @PostMapping("/{id}/move-to-folder")
    @Operation(summary = "Move staging item to a folder")
    public Result<Void> moveToFolder(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody MoveToFolderRequest request) {
        stagingService.moveToFolder(userId, id, request);
        return Result.success();
    }
}
