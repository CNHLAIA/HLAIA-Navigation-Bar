package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.*;
import com.hlaia.dto.response.BookmarkImportResponse;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.service.BookmarkImportService;
import com.hlaia.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 【书签控制器】—— 处理书签相关的 HTTP 请求
 *
 * Controller 层的职责（像一个前台接待员）：
 *   1. 接收 HTTP 请求（解析 URL、请求体、路径参数等）
 *   2. 调用 Service 层处理业务逻辑
 *   3. 将结果包装成统一的 Result 格式返回给前端
 *   Controller 不应该包含任何业务逻辑！
 *
 * RESTful API 路径设计 —— 嵌套资源路径：
 *   本控制器使用了"嵌套资源路径"的设计模式，用 / 表示资源之间的从属关系：
 *   - GET  /api/folders/{folderId}/bookmarks   —— 获取文件夹下的书签
 *     含义：文件夹(id=folderId) 下的 书签列表
 *     folderId 是父资源，bookmarks 是子资源
 *
 *   嵌套资源路径 vs 平铺路径：
 *     嵌套路径：/api/folders/3/bookmarks
 *       优点：语义清晰，一看就知道是"文件夹3下的书签"
 *       缺点：URL 较长，多层嵌套时可能太深
 *     平铺路径：/api/bookmarks?folderId=3
 *       优点：URL 简短，灵活
 *       缺点：语义不够直观
 *     本项目选择嵌套路径，因为书签和文件夹的从属关系是核心业务概念。
 *
 * 其他接口的路径设计：
 *   书签的 CRUD 操作直接使用 /api/bookmarks 作为基础路径（不再嵌套在 folders 下）：
 *   - POST   /api/bookmarks              —— 创建书签
 *   - PUT    /api/bookmarks/{id}         —— 更新书签
 *   - DELETE /api/bookmarks/{id}         —— 删除书签
 *   - PUT    /api/bookmarks/sort         —— 批量排序
 *   - POST   /api/bookmarks/batch-delete —— 批量删除
 *   - POST   /api/bookmarks/batch-copy   —— 批量复制链接
 *
 * 批量操作的 REST 设计（POST /bookmarks/batch-delete 而非 DELETE）：
 *   按照 REST 纯粹主义，删除应该用 DELETE 方法。但批量删除有个问题：
 *   - DELETE 方法通常没有请求体（HTTP 规范不禁止，但很多客户端/代理不支持）
 *   - 批量操作需要传递 ID 列表，只能放在请求体中
 *   - 所以批量删除使用 POST 方法 + 请求体传递数据，这是业界的普遍做法
 *   - 路径中的 /batch-delete 明确表达了操作类型，弥补了 HTTP 方法语义的不足
 *
 * @RestController 注解的作用：
 *   = @Controller + @ResponseBody 的组合注解
 *   - @Controller：告诉 Spring 这是一个控制器类
 *   - @ResponseBody：方法返回值自动序列化为 JSON 响应体
 *   这样每个方法的返回值（Result 对象）会自动转成 JSON 返回给前端
 *
 * @RequestMapping("/api") 的作用：
 *   为控制器中所有方法设置统一的基础路径。
 *   注意：这里的基础路径是 /api 而不是 /api/bookmarks，
 *   因为有些方法（如 /folders/{folderId}/bookmarks）需要以 /api 为前缀。
 *
 * @Tag 注解的作用：
 *   Swagger/OpenAPI 文档分组标签，在 API 文档页面中，
 *   属于同一 @Tag 的接口会被归类到同一个分组下显示。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bookmarks", description = "Bookmark management APIs")
public class BookmarkController {

    // 通过构造器注入 BookmarkService（@RequiredArgsConstructor 自动生成构造方法）
    private final BookmarkService bookmarkService;
    // 书签导入服务：解析浏览器导出的书签 HTML 文件并批量导入
    private final BookmarkImportService bookmarkImportService;

    /**
     * 获取指定文件夹下的书签列表
     *
     * 嵌套资源路径设计：
     *   GET /api/folders/{folderId}/bookmarks
     *   - /api/folders/{folderId} —— 定位到某个文件夹
     *   - /bookmarks —— 获取该文件夹下的书签
     *   这种 URL 结构清晰地表达了"文件夹包含书签"的从属关系
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
     * @param userId   当前登录用户的 ID（由 Spring Security 自动注入）
     * @param folderId 文件夹 ID（从 URL 路径中获取）
     * @return 该文件夹下的书签列表（按 sortOrder 排序）
     */
    @GetMapping("/folders/{folderId}/bookmarks")
    @Operation(summary = "Get bookmarks in a folder")
    public Result<List<BookmarkResponse>> list(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long folderId) {
        return Result.success(bookmarkService.getBookmarksByFolder(userId, folderId));
    }

    /**
     * 创建书签
     *
     * @Valid 注解的作用：
     *   触发请求体中 DTO 字段上的校验注解（如 @NotBlank、@NotNull）。
     *   如果校验不通过，Spring 会自动抛出 MethodArgumentNotValidException，
     *   GlobalExceptionHandler 会捕获它并返回 400 错误响应。
     *   不加 @Valid 的话，DTO 上的校验注解形同虚设，不会执行校验逻辑。
     *
     * @RequestBody 注解的作用：
     *   告诉 Spring 从 HTTP 请求体（body）中读取 JSON 数据，
     *   并自动反序列化为 Java 对象（BookmarkCreateRequest）。
     *   例如前端发送 {"folderId": 1, "title": "百度", "url": "https://www.baidu.com"}，
     *   Spring 会自动把这个 JSON 转成 BookmarkCreateRequest 对象。
     *
     * @param userId  当前登录用户的 ID
     * @param request 创建书签的请求数据
     * @return 新创建的书签信息
     */
    @PostMapping("/bookmarks")
    @Operation(summary = "Create a bookmark")
    public Result<BookmarkResponse> create(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.createBookmark(userId, request));
    }

    /**
     * 更新书签信息
     *
     * @PathVariable 注解的作用：
     *   从 URL 路径中提取变量值。
     *   URL 中的 {id} 是路径变量占位符，@PathVariable Long id 会把它的值赋给方法参数。
     *   例如：PUT /api/bookmarks/42，那么 id 的值就是 42。
     *
     * @param userId  当前登录用户的 ID
     * @param id      要更新的书签 ID（从 URL 路径中获取）
     * @param request 更新请求数据
     * @return 更新后的书签信息
     */
    @PutMapping("/bookmarks/{id}")
    @Operation(summary = "Update a bookmark")
    public Result<BookmarkResponse> update(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long id,
                                            @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.updateBookmark(userId, id, request));
    }

    /**
     * 删除书签
     *
     * 注意：删除操作返回 Result<Void>（没有 data 数据），
     * 因为删除成功后，该资源已不存在，没有需要返回的信息。
     *
     * @param userId 当前登录用户的 ID
     * @param id     要删除的书签 ID
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/bookmarks/{id}")
    @Operation(summary = "Delete a bookmark")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        bookmarkService.deleteBookmark(userId, id);
        return Result.success();
    }

    /**
     * 批量更新书签排序
     *
     * 为什么用 PUT 而不是 POST？
     *   - 排序是"修改已有资源"的操作，不是"创建新资源"
     *   - PUT 表示幂等的更新操作：无论调用多少次，排序结果相同
     *   - POST 一般用于"创建新资源"这种非幂等操作
     *
     * @param userId  当前登录用户的 ID
     * @param request 排序请求数据（包含书签 ID 和新排序序号的列表）
     * @return 成功响应（无数据）
     */
    @PutMapping("/bookmarks/sort")
    @Operation(summary = "Batch update bookmark sort order")
    public Result<Void> sort(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody BookmarkSortRequest request) {
        bookmarkService.sortBookmarks(userId, request);
        return Result.success();
    }

    /**
     * 批量删除书签
     *
     * 批量操作的 REST 设计：POST /api/bookmarks/batch-delete
     *
     * 为什么用 POST 而不是 DELETE？
     *   - DELETE 方法通常不携带请求体，但批量删除需要传递 ID 列表
     *   - HTTP 规范没有明确禁止 DELETE 带请求体，但很多客户端和代理服务器不支持
     *   - 使用 POST + /batch-delete 路径是业界标准做法（GitHub API 也这样做）
     *   - 路径中的 "batch-delete" 明确表达了操作类型
     *
     * 为什么路径是 /batch-delete 而不是 /batch/{ids}？
     *   - 把 ID 列表放在 URL 路径中会导致 URL 过长（浏览器和服务器都有 URL 长度限制）
     *   - 放在请求体中更灵活，可以传递任意数量的 ID
     *   - 例如：删除 100 个书签，URL 里放 100 个 ID 会非常长
     *
     * @param userId  当前登录用户的 ID
     * @param request 批量删除请求（包含要删除的书签 ID 列表）
     * @return 成功响应（无数据）
     */
    @PostMapping("/bookmarks/batch-delete")
    @Operation(summary = "Batch delete bookmarks")
    public Result<Void> batchDelete(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody BatchDeleteRequest request) {
        bookmarkService.batchDelete(userId, request);
        return Result.success();
    }

    /**
     * 批量复制书签链接
     *
     * POST /api/bookmarks/batch-copy
     *
     * 返回值是 List<String>（URL 字符串列表），前端拿到后用 clipboard API 复制到剪贴板。
     * 例如：["https://www.baidu.com", "https://github.com", "https://stackoverflow.com"]
     *
     * @param userId  当前登录用户的 ID
     * @param request 批量复制请求（包含要复制的书签 ID 列表）
     * @return URL 字符串列表
     */
    @PostMapping("/bookmarks/batch-copy")
    @Operation(summary = "Batch copy bookmark links")
    public Result<List<String>> batchCopy(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody BatchCopyRequest request) {
        return Result.success(bookmarkService.batchCopyLinks(userId, request));
    }

    /**
     * 批量移动书签到目标文件夹
     *
     * PUT /api/bookmarks/move
     *
     * 为什么用 PUT 而不是 POST？
     *   移动是"修改已有资源"的操作（改变书签的 folderId），PUT 语义更合适。
     *
     * @param userId  当前登录用户的 ID
     * @param request 移动请求（包含 bookmarkIds 和 targetFolderId）
     * @return 成功响应（无数据）
     */
    @PutMapping("/bookmarks/move")
    @Operation(summary = "Move bookmarks to another folder")
    public Result<Void> moveBookmarks(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody BookmarkMoveRequest request) {
        bookmarkService.moveBookmarks(userId, request);
        return Result.success();
    }

    /**
     * 导入浏览器书签（从 Chrome 导出的 HTML 文件）
     *
     * POST /api/bookmarks/import
     *
     * @RequestParam vs @RequestBody 的区别：
     *   - @RequestBody：从 HTTP 请求体中读取 JSON 数据并反序列化为 Java 对象
     *     适用于：提交结构化数据（如创建书签的 JSON 请求体）
     *   - @RequestParam：从 URL 查询参数或 multipart/form-data 表单字段中读取值
     *     适用于：简单的键值对参数（如 ?duplicateMode=SKIP）和文件上传
     *
     * 什么是 multipart/form-data 文件上传？
     *   普通的 HTTP 请求体是单一格式的（如 JSON）。
     *   但文件上传需要同时传输"文件二进制数据"和"其他表单字段"，
     *   所以使用 multipart/form-data 编码——它把请求体分成多个"部分"（part），
     *   每个 part 可以是文件或普通字段，各自有独立的内容类型。
     *   Spring 的 MultipartFile 接口就是对文件 part 的封装。
     *
     * MultipartFile 的常见方法：
     *   - file.getInputStream()：获取文件输入流（用于读取文件内容）
     *   - file.getOriginalFilename()：获取上传时的原始文件名
     *   - file.getSize()：获取文件大小（字节）
     *   - file.isEmpty()：判断文件是否为空
     *
     * 为什么 targetFolderId 用 @RequestParam(required = false)？
     *   因为这个参数是可选的——如果不指定目标文件夹，书签会导入到根级别。
     *   required = false 允许客户端不传这个参数，此时方法收到的值为 null。
     *
     * 为什么 duplicateMode 用 @RequestParam(defaultValue = "OVERWRITE")？
     *   默认值设计：大部分用户期望导入时覆盖已有的重复书签，
     *   所以 OVERWRITE 作为默认值，用户可以通过传 "SKIP" 来改变行为。
     *
     * @param userId         当前登录用户的 ID（由 Spring Security 自动注入）
     * @param file           上传的 Chrome 书签 HTML 文件
     * @param targetFolderId 目标文件夹 ID（可选，null = 导入到根级别）
     * @param duplicateMode  重复处理模式（默认 "OVERWRITE"，可选 "SKIP"）
     * @return 导入统计信息（新建文件夹数、新建/覆盖/跳过的书签数）
     */
    @PostMapping("/bookmarks/import")
    @Operation(summary = "Import bookmarks from browser HTML file")
    public Result<BookmarkImportResponse> importBookmarks(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
            @RequestParam(value = "duplicateMode", defaultValue = "OVERWRITE") String duplicateMode) {
        return Result.success(bookmarkImportService.importBookmarks(userId, file, targetFolderId, duplicateMode));
    }
}
