package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.BookmarkCreateRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.service.BookmarkService;
import com.hlaia.service.FolderService;
import com.hlaia.service.StagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【浏览器扩展专用控制器】—— 为 Chrome/Edge 浏览器扩展提供精简的 API 接口
 *
 * ============================================================
 * 为什么需要单独的 Extension Controller，而不是复用普通用户的 Controller？
 * ============================================================
 *
 *   虽然这个控制器调用的 Service 方法和普通 Controller 完全一样，
 *   但把它独立出来有以下四个重要原因：
 *
 *   1. 扩展的 API 需求是普通用户 API 的子集
 *      - 浏览器扩展只需要"查询文件夹树"和"快速添加"这两个核心功能
 *      - 不需要"更新书签"、"删除文件夹"、"批量操作"等完整的管理功能
 *      - 单独的控制器让扩展的接口范围一目了然，不会暴露不必要的接口
 *
 *   2. 未来可以针对扩展做不同的限流策略
 *      - 浏览器扩展的使用频率和网页不同（用户可能疯狂收藏，也可能长时间不用）
 *      - 扩展可能需要更宽松或更严格的请求频率限制
 *      - 独立的路径（/api/ext/**）方便在限流组件中单独配置规则
 *
 *   3. 接口路径独立（/api/ext/**），方便在 SecurityConfig 中做不同的安全策略
 *      - SecurityConfig 中可以对 /api/ext/** 路径设置专属的安全规则
 *      - 例如：扩展的 Token 过期时间可以更短，或者强制使用 HTTPS
 *      - 如果和普通 API 混在一起，很难针对扩展做差异化配置
 *
 *   4. Swagger 文档分组更清晰
 *      - @Tag(name = "Extension") 让扩展接口在 API 文档中有独立的分组
 *      - 前端开发者和扩展开发者可以各自查看自己关心的接口
 *      - 文档结构更清晰，降低沟通成本
 *
 * ============================================================
 * 浏览器扩展的完整工作流程
 * ============================================================
 *
 *   浏览器扩展（Chrome Extension / Edge Add-on）的工作流程如下：
 *
 *   第一步：用户在扩展选项页登录 → 获取 JWT Token
 *     - 用户安装扩展后，打开扩展的"设置页"（options_page）
 *     - 在设置页输入用户名和密码，点击"登录"
 *     - 扩展调用 POST /api/auth/login 接口，获取 JWT Token
 *     - 扩展使用 chrome.storage.local 把 Token 保存到本地
 *       （chrome.storage.local 是 Chrome 扩展专用的持久化存储 API，
 *        类似于网页的 localStorage，但数据存储在扩展的独立空间中，
 *        不受网页域名隔离的限制，关闭浏览器后数据也不会丢失）
 *
 *   第二步：扩展在右键菜单中调用 GET /api/ext/folders/tree 获取文件夹列表
 *     - 用户打开任意网页后，扩展读取之前保存的 Token
 *     - 调用 GET /api/ext/folders/tree，在 HTTP 请求头中携带 JWT Token：
 *       Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 *     - 后端验证 Token 后返回用户的文件夹树结构
 *     - 扩展根据文件夹树动态生成右键菜单项：
 *       ├── 添加到 "工作" 文件夹
 *       ├── 添加到 "学习" 文件夹
 *       ├── 添加到 "娱乐" 文件夹
 *       └── 稍后阅读（暂存区）
 *
 *   第三步：用户右键"添加到XX文件夹" → 调用 POST /api/ext/bookmarks
 *     - 用户在网页上点击右键，选择"添加到 '工作' 文件夹"
 *     - 扩展自动收集当前标签页的信息：
 *       - title：document.title（网页标题）
 *       - url：window.location.href（网页地址）
 *       - folderId：用户在菜单中选择的文件夹 ID
 *     - 调用 POST /api/ext/bookmarks，将网页保存为书签
 *     - 成功后弹出一个小提示："已添加到 '工作' 文件夹"
 *
 *   第四步：用户右键"稍后阅读" → 调用 POST /api/ext/staging
 *     - 用户不想现在整理，只想快速保存
 *     - 点击右键菜单中的"稍后阅读"
 *     - 扩展只需收集 title 和 url（不需要选文件夹）
 *     - 调用 POST /api/ext/staging，将网页添加到暂存区
 *     - 用户后续可以在 Web 端的暂存区中整理这些网页
 *
 * ============================================================
 * Manifest V3 Service Worker 中如何发送带 JWT 的请求
 * ============================================================
 *
 *   Chrome 扩展从 Manifest V3 开始，使用 Service Worker 替代了 Background Page。
 *   Service Worker 是一种特殊的 Web Worker，没有 DOM 访问能力，
 *   但可以拦截网络请求、监听浏览器事件。
 *
 *   在 Service Worker 中发送带 JWT 的 API 请求示例代码：
 *
 *   // 1. 从 chrome.storage.local 获取之前保存的 JWT Token
 *   const { token } = await chrome.storage.local.get('token');
 *
 *   // 2. 使用 fetch API 发送请求，在请求头中携带 Token
 *   const response = await fetch('https://your-api.com/api/ext/folders/tree', {
 *     method: 'GET',
 *     headers: {
 *       'Authorization': `Bearer ${token}`,   // JWT 标准格式：Bearer + 空格 + Token
 *       'Content-Type': 'application/json'
 *     }
 *   });
 *
 *   // 3. 解析响应
 *   const result = await response.json();
 *   // result = { code: 200, message: "success", data: [...] }
 *
 *   为什么不能用 Cookie 自动携带 Token？
 *     - 浏览器扩展的 Service Worker 运行在独立的上下文中（chrome-extension://xxx）
 *     - API 服务器运行在不同的域名下（如 https://api.example.com）
 *     - 跨域请求默认不会携带 Cookie（Same-Origin Policy 同源策略限制）
 *     - 所以必须手动在请求头中添加 Authorization 字段
 *
 *   关于 Token 刷新：
 *     - 如果服务器返回 401（未授权），说明 Token 已过期
 *     - 扩展应尝试调用 Token 刷新接口（POST /api/auth/refresh）
 *     - 如果刷新也失败，跳转到扩展的登录页让用户重新登录
 *
 * ============================================================
 * API 路由列表（仅三个精简接口）
 * ============================================================
 *
 *   - GET  /api/ext/folders/tree  —— 获取文件夹树（用于构建右键菜单）
 *   - POST /api/ext/bookmarks     —— 快速添加书签（右键"添加到文件夹"）
 *   - POST /api/ext/staging       —— 快速添加到暂存区（右键"稍后阅读"）
 *
 * ============================================================
 * 关键注解解释
 * ============================================================
 *
 * @RestController 注解的作用：
 *   = @Controller + @ResponseBody 的组合注解
 *   - @Controller：告诉 Spring 这是一个控制器类
 *   - @ResponseBody：方法返回值自动序列化为 JSON 响应体
 *   这样每个方法的返回值（Result 对象）会自动转成 JSON 返回给浏览器扩展
 *
 * @RequestMapping("/api/ext") 的作用：
 *   为控制器中所有方法设置统一的基础路径。
 *   所有方法的路径都会自动加上 /api/ext 前缀。
 *   /ext 是 extension（扩展）的缩写，简洁且语义明确。
 *   这个路径在 SecurityConfig 中会匹配 anyRequest().authenticated()，
 *   即所有扩展接口都需要携带有效的 JWT Token 才能访问。
 *
 * @RequiredArgsConstructor 注解的作用：
 *   Lombok 注解，为所有 final 字段自动生成构造函数。
 *   Spring 会通过这个构造函数完成依赖注入（构造器注入是推荐的注入方式）。
 *   等价于手写：
 *     public ExtensionController(FolderService folderService,
 *                                BookmarkService bookmarkService,
 *                                StagingService stagingService) {
 *         this.folderService = folderService;
 *         this.bookmarkService = bookmarkService;
 *         this.stagingService = stagingService;
 *     }
 *
 * @Tag 注解的作用：
 *   Swagger/OpenAPI 文档分组标签。在 Knife4j API 文档页面中，
 *   属于同一 @Tag 的接口会被归类到同一个分组下显示。
 *   @Tag(name = "Extension") 会创建一个名为 "Extension" 的分组，
 *   方便浏览器扩展的开发者快速找到所有相关接口。
 */
@RestController
@RequestMapping("/api/ext")
@RequiredArgsConstructor
@Tag(name = "Extension", description = "Browser extension APIs")
public class ExtensionController {

    // ================================================================
    // 依赖注入的三个 Service
    // ================================================================
    // 通过构造器注入（@RequiredArgsConstructor 自动生成构造方法）
    // 这三个 Service 和普通 Controller 使用的是同一个实例（Spring Bean 默认是单例）
    // 所以扩展 Controller 不需要重复编写任何业务逻辑

    /**
     * 文件夹服务 —— 提供文件夹树的查询功能
     * 扩展用它的 getFolderTree() 方法来获取右键菜单的文件夹列表
     */
    private final FolderService folderService;

    /**
     * 书签服务 —— 提供书签的创建功能
     * 扩展用它的 createBookmark() 方法来快速保存网页为书签
     */
    private final BookmarkService bookmarkService;

    /**
     * 暂存区服务 —— 提供暂存区的添加功能
     * 扩展用它的 addToStaging() 方法来快速保存网页到暂存区（"稍后阅读"）
     */
    private final StagingService stagingService;

    /**
     * 获取文件夹树 —— 为扩展的右键上下文菜单提供文件夹列表
     *
     * ============================================================
     * 这个接口在浏览器扩展中的用途
     * ============================================================
     *
     *   浏览器扩展在安装后，会注册一个右键菜单（Context Menu）。
     *   为了让用户能把网页快速保存到指定的文件夹，扩展需要知道
     *   用户有哪些文件夹。这个接口就是返回文件夹的树形结构。
     *
     *   扩展端的大致流程：
     *   1. 用户打开浏览器，扩展的 Service Worker 被激活
     *   2. Service Worker 从 chrome.storage.local 读取 JWT Token
     *   3. 调用 GET /api/ext/folders/tree，携带 Token
     *   4. 收到文件夹树数据后，动态创建右键菜单项
     *   5. 每个文件夹对应一个菜单项，点击后即可将网页保存到该文件夹
     *
     *   为什么扩展要在每次激活时重新获取文件夹树？
     *   - 用户可能在 Web 端新增或修改了文件夹
     *   - Service Worker 没有持久的内存状态（随时可能被浏览器回收）
     *   - 每次激活时获取最新数据，确保右键菜单和服务器保持同步
     *
     * ============================================================
     * 接口设计说明
     * ============================================================
     *
     *   GET /api/ext/folders/tree
     *
     *   为什么是 GET 而不是 POST？
     *     - GET 是"查询"操作，不会修改任何数据，是安全的、幂等的
     *     - 浏览器和 CDN 可以缓存 GET 请求的响应
     *     - RESTful 设计原则：查询用 GET，创建用 POST，修改用 PUT，删除用 DELETE
     *
     *   为什么路径和 FolderController 中的不同？
     *     - FolderController：GET /api/folders/tree（普通用户使用）
     *     - ExtensionController：GET /api/ext/folders/tree（扩展专用）
     *     - 虽然底层调用的是同一个 Service 方法，但路径不同，
     *       方便在 SecurityConfig、限流、日志等方面做差异化配置
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
     *   对于浏览器扩展来说，JWT Token 是在扩展的选项页登录时获取的，
     *   之后每次 API 请求都通过 Authorization 请求头携带这个 Token。
     *   JwtAuthFilter 会拦截请求、解析 Token、验证签名、提取 userId，
     *   并将其设置为 SecurityContext 中的 principal。
     *
     * @param userId 当前登录用户的 ID（由 Spring Security 自动注入）
     * @return 文件夹树的根节点列表（嵌套的树形结构，包含所有层级）
     */
    @GetMapping("/folders/tree")
    @Operation(summary = "Get folder tree for extension context menu")
    public Result<List<FolderTreeResponse>> getFolderTree(@AuthenticationPrincipal Long userId) {
        return Result.success(folderService.getFolderTree(userId));
    }

    /**
     * 快速添加书签 —— 从浏览器扩展一键保存网页到指定文件夹
     *
     * ============================================================
     * 这个接口在浏览器扩展中的用途
     * ============================================================
     *
     *   当用户在右键菜单中点击"添加到 XX 文件夹"时，扩展会：
     *   1. 获取当前标签页的标题（document.title）和地址（window.location.href）
     *   2. 获取用户在菜单中选择的文件夹 ID（folderId）
     *   3. 组装成 BookmarkCreateRequest 对象
     *   4. 调用 POST /api/ext/bookmarks，在请求体中发送 JSON 数据
     *   5. 成功后显示一个小的通知提示（chrome.notifications API）
     *
     *   扩展端的示例代码：
     *   const response = await fetch('https://your-api.com/api/ext/bookmarks', {
     *     method: 'POST',
     *     headers: {
     *       'Authorization': `Bearer ${token}`,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       folderId: selectedFolderId,
     *       title: tab.title,
     *       url: tab.url
     *     })
     *   });
     *
     * ============================================================
     * 与 BookmarkController 的 create 方法对比
     * ============================================================
     *
     *   BookmarkController.create()：POST /api/bookmarks
     *     - 路径：/api/bookmarks（普通 Web 端使用）
     *     - 功能完全相同：都调用 bookmarkService.createBookmark(userId, request)
     *     - 区别仅在于路径前缀不同（/api vs /api/ext）
     *
     *   ExtensionController.addBookmark()：POST /api/ext/bookmarks
     *     - 路径：/api/ext/bookmarks（扩展专用）
     *     - 底层调用的是同一个 Service 方法
     *     - 但未来可以在这里添加扩展专属的逻辑（如自动获取 favicon、记录来源等）
     *
     * @Valid 注解的作用：
     *   触发请求体中 DTO 字段上的校验注解（如 @NotBlank、@NotNull）。
     *   BookmarkCreateRequest 中定义了：
     *     @NotNull Long folderId   —— 文件夹 ID 不能为空
     *     @NotBlank String title   —— 书签标题不能为空
     *     @NotBlank String url     —— 书签链接不能为空
     *   如果校验不通过，Spring 会自动抛出 MethodArgumentNotValidException，
     *   GlobalExceptionHandler 会捕获它并返回 400 错误响应。
     *   浏览器扩展收到错误后，可以提示用户"保存失败，请重试"。
     *
     * @RequestBody 注解的作用：
     *   告诉 Spring 从 HTTP 请求体（body）中读取 JSON 数据，
     *   并自动反序列化为 Java 对象（BookmarkCreateRequest）。
     *   例如扩展发送 {"folderId": 1, "title": "百度", "url": "https://www.baidu.com"}，
     *   Spring 会自动把这个 JSON 转成 BookmarkCreateRequest 对象。
     *
     * @param userId  当前登录用户的 ID（由 Spring Security 自动注入）
     * @param request 创建书签的请求数据（包含 folderId、title、url）
     * @return 新创建的书签信息（包含自动生成的 id、createTime 等）
     */
    @PostMapping("/bookmarks")
    @Operation(summary = "Quick-add bookmark from extension")
    public Result<BookmarkResponse> addBookmark(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.createBookmark(userId, request));
    }

    /**
     * 快速添加到暂存区 —— 从浏览器扩展一键保存网页到"稍后阅读"列表
     *
     * ============================================================
     * 这个接口在浏览器扩展中的用途
     * ============================================================
     *
     *   当用户在右键菜单中点击"稍后阅读"时，扩展会：
     *   1. 获取当前标签页的标题和地址
     *   2. 只需要 title 和 url，不需要选择文件夹（暂存区没有文件夹概念）
     *   3. 组装成 StagingCreateRequest 对象
     *   4. 调用 POST /api/ext/staging，在请求体中发送 JSON 数据
     *   5. 成功后显示通知："已添加到稍后阅读"
     *
     *   暂存区 vs 书签的使用场景区别：
     *     - 书签（Bookmark）：用户明确知道要分类到哪个文件夹，一步到位
     *     - 暂存区（Staging）：用户只想快速保存，来不及分类，后续再整理
     *     - 类比：书签是"把书放到书架的指定位置"，暂存区是"先把书扔到桌上"
     *
     *   扩展端的示例代码：
     *   const response = await fetch('https://your-api.com/api/ext/staging', {
     *     method: 'POST',
     *     headers: {
     *       'Authorization': `Bearer ${token}`,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       title: tab.title,
     *       url: tab.url
     *       // expireMinutes 不传，使用默认值（1天）
     *     })
     *   });
     *
     * ============================================================
     * 与 StagingController 的 add 方法对比
     * ============================================================
     *
     *   StagingController.add()：POST /api/staging
     *     - 路径：/api/staging（普通 Web 端使用）
     *     - 功能完全相同：都调用 stagingService.addToStaging(userId, request)
     *     - 区别仅在于路径前缀不同（/api/staging vs /api/ext/staging）
     *
     *   ExtensionController.addStaging()：POST /api/ext/staging
     *     - 路径：/api/ext/staging（扩展专用）
     *     - 底层调用的是同一个 Service 方法
     *     - 同样的，未来可以在这里添加扩展专属逻辑
     *
     * @Valid 注解的作用：
     *   触发请求体中 DTO 字段上的校验注解。
     *   StagingCreateRequest 中定义了：
     *     @NotBlank String title          —— 网页标题不能为空
     *     @NotBlank String url            —— 网页链接不能为空
     *     Integer expireMinutes（可选）    —— 过期时间（分钟），不传则使用默认值
     *   如果校验不通过，Spring 会自动抛出 MethodArgumentNotValidException。
     *
     * @RequestBody 注解的作用：
     *   告诉 Spring 从 HTTP 请求体中读取 JSON 数据，
     *   并自动反序列化为 Java 对象（StagingCreateRequest）。
     *   例如扩展发送 {"title": "百度", "url": "https://www.baidu.com"}，
     *   Spring 会自动把这个 JSON 转成 StagingCreateRequest 对象。
     *
     * @param userId  当前登录用户的 ID（由 Spring Security 自动注入）
     * @param request 添加到暂存区的请求数据（包含 title、url、可选的 expireMinutes）
     * @return 新创建的暂存项信息（包含自动生成的 id、expireAt 等）
     */
    @PostMapping("/staging")
    @Operation(summary = "Add to staging from extension")
    public Result<StagingItemResponse> addStaging(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody StagingCreateRequest request) {
        return Result.success(stagingService.addToStaging(userId, request));
    }
}
