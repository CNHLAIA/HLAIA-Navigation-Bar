package com.hlaia.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hlaia.common.Result;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【管理员控制器】—— 处理管理员后台的所有 HTTP 请求
 *
 * /api/admin/** 路径与权限控制：
 *   在 SecurityConfig（Spring Security 配置类）中，我们配置了类似这样的规则：
 *
 *   http.authorizeHttpRequests(auth -> auth
 *       .requestMatchers("/api/admin/**").hasRole("ADMIN")  // 只有 ADMIN 角色才能访问
 *       .requestMatchers("/api/**").authenticated()          // 其他 API 只需登录即可
 *       ...
 *   );
 *
 *   这意味着：
 *   - 所有以 /api/admin/ 开头的请求，Spring Security 会自动检查当前用户是否有 ADMIN 角色
 *   - 如果用户未登录（没有 JWT Token）→ 返回 401 Unauthorized
 *   - 如果用户已登录但角色是 USER → 返回 403 Forbidden
 *   - 只有角色是 ADMIN 的用户才能通过拦截，请求才会到达这个 Controller
 *
 *   所以 Controller 内部不需要手动检查用户角色，SecurityConfig 已经保证了安全性。
 *
 * 为什么管理员接口不需要 @AuthenticationPrincipal？
 *   普通用户的 Controller（如 FolderController）中，每个方法都有：
 *     @AuthenticationPrincipal Long userId
 *   这是为了获取当前登录用户的 ID，确保用户只能操作自己的数据。
 *
 *   但管理员接口的操作对象不是"自己"的数据，而是"任意用户"的数据：
 *   - 查看用户列表 → 不需要当前用户的 ID，查的是所有用户
 *   - 查看某个用户的文件夹 → 目标用户 ID 由 URL 路径参数指定（@PathVariable Long userId）
 *   - 封禁/解封用户 → 目标用户 ID 由 URL 路径参数指定
 *   - 删除文件夹 → 文件夹 ID 由 URL 路径参数指定
 *
 *   所以管理员接口使用 @PathVariable 来指定操作目标，而不是 @AuthenticationPrincipal。
 *   SecurityConfig 已经保证了只有 ADMIN 角色才能访问这些接口。
 *
 * RESTful API 路径设计：
 *   本控制器的基础路径是 /api/admin，所有接口都以此为前缀：
 *   - GET    /api/admin/users                    —— 分页查询用户列表
 *   - GET    /api/admin/users/{userId}/folders/tree —— 查看指定用户的文件夹树
 *   - DELETE /api/admin/folders/{id}             —— 删除任意文件夹
 *   - PUT    /api/admin/users/{userId}/ban       —— 封禁用户
 *   - PUT    /api/admin/users/{userId}/unban     —— 解封用户
 *
 * @RestController 注解的作用：
 *   = @Controller + @ResponseBody 的组合注解
 *   方法返回值自动序列化为 JSON 响应体。
 *
 * @RequestMapping("/api/admin") 的作用：
 *   为控制器中所有方法设置统一的基础路径 /api/admin。
 *   例如方法上标注 @GetMapping("/users")，完整路径就是 /api/admin/users。
 *
 * @Tag 注解的作用：
 *   Swagger/OpenAPI 文档分组标签，在 Knife4j API 文档页面中，
 *   所有管理员接口会被归类到 "Admin" 分组下显示。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management APIs")
public class AdminController {

    /** 通过构造器注入 AdminService（@RequiredArgsConstructor 自动生成构造方法） */
    private final AdminService adminService;

    /**
     * 分页查询用户列表 —— 管理员后台最核心的功能
     *
     * @RequestParam 注解的作用：
     *   从 URL 查询参数中获取值。
     *   例如 GET /api/admin/users?page=2&size=50，
     *   @RequestParam("page") int page 会获取到 "page" 参数的值 2。
     *
     *   当方法参数名和 URL 参数名一致时，可以省略 @RequestParam 中的 value 属性。
     *   即 @RequestParam int page 等价于 @RequestParam("page") int page。
     *
     * defaultValue 属性的用法：
     *   @RequestParam(defaultValue = "1") int page
     *   如果请求中没有传 page 参数，就使用默认值 1。
     *   例如：
     *     GET /api/admin/users         → page=1, size=20（使用默认值）
     *     GET /api/admin/users?page=3  → page=3, size=20（page 使用传入值，size 使用默认值）
     *     GET /api/admin/users?page=2&size=50 → page=2, size=50（都使用传入值）
     *
     *   这比 @RequestParam(required = false) 更好，因为：
     *   - 不需要在方法内部手动判断 null 并设置默认值
     *   - 代码更简洁，意图更明确
     *   - 前端可以省略这些参数，后端自动使用合理的默认值
     *
     * 分页参数的设计约定：
     *   - page 从 1 开始（不是 0！），1 表示第一页
     *     这是面向前端开发者的习惯：日常生活中"第1页"就是开始，不是"第0页"
     *     MyBatis-Plus 的 Page 对象也是从 1 开始的，天然匹配
     *   - 默认每页 20 条（size = 20）
     *     20 是一个经验值：太少需要频繁翻页，太多页面加载慢
     *     管理员可以根据需要调整（如 size=50 或 size=100）
     *
     * @param page 页码（从 1 开始，默认 1）
     * @param size 每页大小（默认 20）
     * @return 分页结果，包含用户列表和分页信息
     */
    @GetMapping("/users")
    @Operation(summary = "Get paginated user list")
    public Result<Page<UserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.getUserList(page, size));
    }

    /**
     * 查看指定用户的文件夹树 —— 管理员可以查看任何用户的导航栏结构
     *
     * URL 路径设计：GET /api/admin/users/{userId}/folders/tree
     *   这是一种"嵌套资源"的 RESTful 路径设计：
     *   - /api/admin/users/{userId} —— 先定位到"某个用户"
     *   - /folders/tree —— 再获取这个用户的"文件夹树"
     *   路径层级反映了资源的归属关系：用户的文件夹树
     *
     * @PathVariable 注解的作用：
     *   从 URL 路径中提取变量值。
     *   URL 中的 {userId} 是路径变量占位符，
     *   @PathVariable Long userId 会把它的值赋给方法参数。
     *   例如：GET /api/admin/users/5/folders/tree → userId = 5
     *
     * @param userId 要查看的用户 ID（从 URL 路径中获取）
     * @return 该用户的文件夹树
     */
    @GetMapping("/users/{userId}/folders/tree")
    @Operation(summary = "View a user's folder tree")
    public Result<List<FolderTreeResponse>> getUserFolders(@PathVariable Long userId) {
        return Result.success(adminService.getUserFolderTree(userId));
    }

    /**
     * 删除任意用户的文件夹 —— 管理员可以删除违规或无效的文件夹
     *
     * 为什么这个接口在 /api/admin/folders/{id} 而不是 /api/admin/users/{userId}/folders/{id}？
     *   因为文件夹 ID 是全局唯一的（数据库主键），不需要通过 userId 来定位。
     *   只需要 folderId 就能唯一确定一个文件夹，直接删除即可。
     *   这样 URL 更短、更简洁。
     *
     *   对比普通用户的删除接口：
     *   - 普通用户：DELETE /api/folders/{id}（需要验证文件夹属于当前用户）
     *   - 管理员：DELETE /api/admin/folders/{id}（不需要验证归属，直接删除）
     *
     * @param id 要删除的文件夹 ID（从 URL 路径中获取）
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/folders/{id}")
    @Operation(summary = "Delete any user's folder")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        adminService.deleteUserFolder(id);
        return Result.success();
    }

    /**
     * 封禁用户 —— 将用户状态设为"封禁"，使其无法登录
     *
     * URL 设计：PUT /api/admin/users/{userId}/ban
     *   使用 PUT 方法的原因：
     *   - 封禁操作本质上是"修改用户状态"（把 status 从 0 改为 1）
     *   - PUT 用于更新已有资源，这里更新的是用户的 status 字段
     *   - 也可以用 POST，但 PUT 的语义更准确（幂等的更新操作）
     *
     *   /ban 是一个"子资源操作"，表示对用户的"封禁"这个动作。
     *   类似的设计：GitHub API 中 PUT /repos/{owner}/{repo}/actions/runners/{id}/disable
     *
     * @param userId 要封禁的用户 ID（从 URL 路径中获取）
     * @return 成功响应（无数据）
     */
    @PutMapping("/users/{userId}/ban")
    @Operation(summary = "Ban a user")
    public Result<Void> banUser(@PathVariable Long userId) {
        adminService.banUser(userId);
        return Result.success();
    }

    /**
     * 解封用户 —— 将用户状态恢复为"正常"，使其可以重新登录
     *
     * 与 banUser 对称的接口：
     *   ban:   PUT /api/admin/users/{userId}/ban   → status = 1
     *   unban: PUT /api/admin/users/{userId}/unban → status = 0
     *
     *   前端管理员面板的典型 UI 设计：
     *   用户列表中，每个用户旁边有操作按钮：
     *   - 状态为"正常"的用户 → 显示红色"封禁"按钮（点击调用 /ban）
     *   - 状态为"封禁"的用户 → 显示绿色"解封"按钮（点击调用 /unban）
     *   前端根据 user.status 的值来决定显示哪个按钮。
     *
     * @param userId 要解封的用户 ID（从 URL 路径中获取）
     * @return 成功响应（无数据）
     */
    @PutMapping("/users/{userId}/unban")
    @Operation(summary = "Unban a user")
    public Result<Void> unbanUser(@PathVariable Long userId) {
        adminService.unbanUser(userId);
        return Result.success();
    }
}
