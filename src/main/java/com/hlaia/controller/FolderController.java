package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.FolderCreateRequest;
import com.hlaia.dto.request.FolderMoveRequest;
import com.hlaia.dto.request.FolderSortRequest;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【文件夹控制器】—— 处理文件夹相关的 HTTP 请求
 *
 * Controller 层的职责（像一个前台接待员）：
 *   1. 接收 HTTP 请求（解析 URL、请求体、路径参数等）
 *   2. 调用 Service 层处理业务逻辑
 *   3. 将结果包装成统一的 Result 格式返回给前端
 *   Controller 不应该包含任何业务逻辑！
 *
 * RESTful API 路径设计原则：
 *   本控制器的基础路径是 /api/folders，所有接口都以此为前缀：
 *   - GET    /api/folders/tree       —— 获取文件夹树（查询用 GET）
 *   - POST   /api/folders            —— 创建文件夹（创建用 POST）
 *   - PUT    /api/folders/{id}       —— 更新文件夹（修改用 PUT）
 *   - DELETE /api/folders/{id}       —— 删除文件夹（删除用 DELETE）
 *   - PUT    /api/folders/sort       —— 批量排序（修改用 PUT）
 *   - PUT    /api/folders/{id}/move  —— 移动文件夹（修改用 PUT）
 *
 *   RESTful 设计的核心思想：用 HTTP 方法（GET/POST/PUT/DELETE）表达操作类型，
 *   用 URL 路径表达操作的资源，让接口一看就懂。
 *
 * HTTP 方法选择规则：
 *   - GET    —— 查询数据，不修改任何内容，是安全的、幂等的
 *   - POST   —— 创建新资源，每次调用都可能产生不同的结果
 *   - PUT    —— 更新已有资源，是幂等的（调用一次和多次效果相同）
 *   - DELETE —— 删除资源，是幂等的（删除已删除的资源返回404，但效果相同）
 *
 * @RestController 注解的作用：
 *   = @Controller + @ResponseBody 的组合注解
 *   - @Controller：告诉 Spring 这是一个控制器类
 *   - @ResponseBody：方法返回值自动序列化为 JSON 响应体
 *   这样每个方法的返回值（Result 对象）会自动转成 JSON 返回给前端
 *
 * @RequestMapping("/api/folders") 的作用：
 *   为控制器中所有方法设置统一的基础路径。
 *   例如方法上标注 @GetMapping("/tree")，完整的访问路径就是 /api/folders/tree
 *
 * @Tag 注解的作用：
 *   Swagger/OpenAPI 文档分组标签，在 API 文档页面中，
 *   属于同一 @Tag 的接口会被归类到同一个分组下显示。
 *   例如所有标注 @Tag(name = "Folders") 的接口会在 "Folders" 分组中显示。
 */
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@Tag(name = "Folders", description = "Folder management APIs")
public class FolderController {

    // 通过构造器注入 FolderService（@RequiredArgsConstructor 自动生成构造方法）
    private final FolderService folderService;

    /**
     * 获取当前用户的文件夹树
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
     *   注意：因为 principal 直接就是 Long 类型的 userId（不是 UserDetails 对象），
     *   所以参数类型可以直接用 Long，不需要额外转换。
     *
     * @param userId 当前登录用户的 ID（由 Spring Security 自动注入）
     * @return 文件夹树的根节点列表
     */
    @GetMapping("/tree")
    @Operation(summary = "Get current user's folder tree")
    public Result<List<FolderTreeResponse>> getTree(@AuthenticationPrincipal Long userId) {
        return Result.success(folderService.getFolderTree(userId));
    }

    /**
     * 创建文件夹
     *
     * @Valid 注解的作用：
     *   触发请求体中 DTO 字段上的校验注解（如 @NotBlank、@Size）。
     *   如果校验不通过，Spring 会自动抛出 MethodArgumentNotValidException，
     *   GlobalExceptionHandler 会捕获它并返回 400 错误响应。
     *
     *   不加 @Valid 的话，DTO 上的校验注解形同虚设，不会执行校验逻辑。
     *
     * @RequestBody 注解的作用：
     *   告诉 Spring 从 HTTP 请求体（body）中读取 JSON 数据，
     *   并自动反序列化为 Java 对象（FolderCreateRequest）。
     *   例如前端发送 {"name": "工作", "icon": "📁"}，
     *   Spring 会自动把这个 JSON 转成 FolderCreateRequest 对象。
     *
     * @param userId  当前登录用户的 ID
     * @param request 创建文件夹的请求数据
     * @return 新创建的文件夹信息
     */
    @PostMapping
    @Operation(summary = "Create a folder")
    public Result<FolderTreeResponse> create(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody FolderCreateRequest request) {
        return Result.success(folderService.createFolder(userId, request));
    }

    /**
     * 更新文件夹信息
     *
     * @PathVariable 注解的作用：
     *   从 URL 路径中提取变量值。
     *   URL 中的 {id} 是路径变量占位符，@PathVariable Long id 会把它的值赋给方法参数。
     *   例如：PUT /api/folders/42，那么 id 的值就是 42。
     *
     *   为什么用路径变量而不是查询参数（?id=42）？
     *   - RESTful 风格中，资源的唯一标识放在路径中：/api/folders/{id}
     *   - 查询参数用于过滤和分页：/api/folders?parentId=1&page=2
     *   - 这样 URL 的语义更清晰：/api/folders/42 表示"ID 为 42 的文件夹"
     *
     * @param userId  当前登录用户的 ID
     * @param id      要更新的文件夹 ID（从 URL 路径中获取）
     * @param request 更新请求数据
     * @return 更新后的文件夹信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a folder")
    public Result<FolderTreeResponse> update(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody FolderCreateRequest request) {
        return Result.success(folderService.updateFolder(userId, id, request));
    }

    /**
     * 删除文件夹
     *
     * 注意：删除操作返回 Result<Void>（没有 data 数据），
     * 因为删除成功后，该资源已不存在，没有需要返回的信息。
     *
     * @param userId 当前登录用户的 ID
     * @param id     要删除的文件夹 ID
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a folder and all its children")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        folderService.deleteFolder(userId, id);
        return Result.success();
    }

    /**
     * 批量更新文件夹排序
     *
     * 为什么用 PUT 而不是 POST？
     *   - 排序是"修改已有资源"的操作，不是"创建新资源"
     *   - PUT 表示幂等的更新操作：无论调用多少次，排序结果相同
     *   - POST 一般用于"创建新资源"这种非幂等操作
     *
     * @param userId  当前登录用户的 ID
     * @param request 排序请求数据（包含文件夹 ID 和新排序序号的列表）
     * @return 成功响应（无数据）
     */
    @PutMapping("/sort")
    @Operation(summary = "Batch update folder sort order")
    public Result<Void> sort(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody FolderSortRequest request) {
        folderService.sortFolders(userId, request);
        return Result.success();
    }

    /**
     * 移动文件夹到新的父文件夹
     *
     * URL 设计：PUT /api/folders/{id}/move
     *   - /api/folders/{id} —— 操作的资源是"ID 为 {id} 的文件夹"
     *   - /move —— 具体的操作是"移动"
     *   这种把操作名称放在路径末尾的方式叫做"RESTful 子资源操作"，
     *   适用于不能用标准 CRUD 动词（GET/POST/PUT/DELETE）表达的操作。
     *
     *   另一种设计是把目标 parentId 放在请求体中（本项目的做法），
     *   也可以把目标放在路径中：PUT /api/folders/{id}/move-to/{targetId}
     *   两种方式都是合理的，选择哪一种取决于团队约定。
     *
     * @param userId  当前登录用户的 ID
     * @param id      要移动的文件夹 ID
     * @param request 移动请求（包含目标 parentId）
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}/move")
    @Operation(summary = "Move a folder to a new parent")
    public Result<Void> move(@AuthenticationPrincipal Long userId,
                              @PathVariable Long id,
                              @RequestBody FolderMoveRequest request) {
        folderService.moveFolder(userId, id, request);
        return Result.success();
    }
}
