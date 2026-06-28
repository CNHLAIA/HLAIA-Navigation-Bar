package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【书签导出服务类】—— 把当前用户名下的全部书签导出为 Netscape Bookmark HTML 文件
 *
 * 什么是书签导出？
 *   与 {@link BookmarkImportService} 互为逆操作：
 *   - 导入：读取 Chrome 导出的 Netscape HTML，解析后写入数据库
 *   - 导出：读取数据库中的书签树，序列化为同格式的 Netscape HTML
 *
 *   导出的文件必须满足两个兼容性目标（见 prd.md 的 AC5/AC6）：
 *   1. 能被本项目自己的 importBookmarks 重新导入（导出→再导入闭环）
 *   2. 能被 Chrome/Edge/Firefox 直接导入（跨平台迁移）
 *
 * Netscape Bookmark 格式回顾（导出必须严格遵循，否则导入端解析不出）：
 *   <DL><p>                          —— 列表容器，表示一个文件夹的内容
 *     <DT><H3>文件夹名</H3>          —— 一个子文件夹
 *     <DL><p>                        —— 该子文件夹的内容（递归嵌套）
 *       <DT><A HREF="url" ICON="data:...;base64,...">书签标题</A>
 *     </DL><p>
 *     <DT><A HREF="url" ICON="...">书签标题</A>  —— 该文件夹下直属的书签
 *   </DL><p>
 *
 * 数据模型约定（导出树形态）：
 *   bookmark.folder_id 是 NOT NULL，所以每个书签必属一个文件夹，没有"漂浮的根级书签"。
 *   folder.parent_id == null 表示根级文件夹。因此导出树总是：
 *     根文件夹A → [直属书签..., 子文件夹B → [直属书签..., ...]]
 *     根文件夹C → [...]
 *
 * @Service 注解：业务逻辑类，纳入 Spring 容器
 * @RequiredArgsConstructor 注解：Lombok 为 final 字段生成构造方法，Spring 自动注入
 * @Slf4j 注解：Lombok 生成 log 对象
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookmarkExportService {

    // 依赖注入：与 BookmarkImportService 复用同一对 Mapper
    private final FolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;

    /**
     * 导出当前用户的全部书签为 Netscape Bookmark HTML
     *
     * 完整流程：
     *   1. 两次查询加载该用户的全部文件夹 + 全部书签（与 FolderService.getFolderTree 相同的"全量加载"模式）
     *   2. 书签按 folderId 分组，子文件夹按 parentId 分组
     *   3. 字符串拼接生成 Netscape HTML，递归渲染每个根文件夹
     *   4. 输出 HTML 字符串，转 UTF-8 字节返回
     *
     * 为什么用字符串拼接而不用 Jsoup 构建 DOM？
     *   虽然导入端用的是 Jsoup，但导出端用 Jsoup 构建 DOM 有风险：
     *   Netscape 格式里的 <DL><p> 非标准（<p> 出现在 <dl> 直接子级不符合 HTML 规范），
     *   Jsoup 的 HTML5 解析器会做纠错（移动或吞掉 <p>），可能破坏导出结构。
     *   而 Netscape 格式是固定模板，结构简单可预测，字符串拼接 + 手写转义更可控，
     *   也更容易与 Chrome 官方导出格式对齐。
     *
     * @param userId 当前登录用户 ID（数据隔离：只能导出自己的书签）
     * @return Netscape Bookmark HTML 的 UTF-8 字节数组
     */
    public byte[] exportBookmarks(Long userId) {
        try {
            // ============ 第一步：加载该用户的全部文件夹（按 sortOrder 排序）============
            // 与 FolderService.getFolderTree 完全相同的查询：按用户隔离 + 按排序序号升序
            List<Folder> allFolders = folderMapper.selectList(
                    new LambdaQueryWrapper<Folder>()
                            .eq(Folder::getUserId, userId)
                            .orderByAsc(Folder::getSortOrder));

            // ============ 第二步：加载该用户的全部书签（按 sortOrder 排序）============
            List<Bookmark> allBookmarks = bookmarkMapper.selectList(
                    new LambdaQueryWrapper<Bookmark>()
                            .eq(Bookmark::getUserId, userId)
                            .orderByAsc(Bookmark::getSortOrder));

            // ============ 第三步：书签按 folderId 分组，子文件夹按 parentId 分组 ============
            // 为什么提前分组？
            //   渲染某个文件夹时，需要快速取出"直属该文件夹的书签"和"直属子文件夹"。
            //   预先分组成 Map，渲染时 O(1) 查找，避免每个文件夹都遍历全部数据。
            Map<Long, List<Bookmark>> bookmarksByFolder = allBookmarks.stream()
                    .collect(Collectors.groupingBy(Bookmark::getFolderId));

            // 子文件夹按 parentId 分组（parentId 非 null 的才有父）
            Map<Long, List<Folder>> foldersByParent = allFolders.stream()
                    .filter(f -> f.getParentId() != null)
                    .collect(Collectors.groupingBy(Folder::getParentId));

            // ============ 第四步：字符串拼接生成 Netscape HTML ============
            StringBuilder html = new StringBuilder();
            // 文件头：Netscape 格式的固定头部（Chrome 导出的也是这个样子）
            html.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
            html.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
            html.append("<TITLE>Bookmarks</TITLE>\n");
            html.append("<H1>Bookmarks</H1>\n");
            // 顶层 DL 容器：所有根文件夹都挂在它下面
            html.append("<DL><p>\n");

            // ============ 第五步：递归渲染所有根文件夹 ============
            // 根文件夹 = parentId 为 null 的文件夹（与 FolderService.getFolderTree 识别根的方式一致）
            for (Folder folder : allFolders) {
                if (folder.getParentId() == null) {
                    renderFolder(html, folder, 1, bookmarksByFolder, foldersByParent);
                }
            }

            html.append("</DL><p>\n");

            log.info("Exported {} folders and {} bookmarks for user {}",
                    allFolders.size(), allBookmarks.size(), userId);
            return html.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 任何异常都转为业务异常 EXPORT_FAILED
            // 按错误处理规范：Service 层抛 BusinessException，Controller 不 try-catch
            log.error("Failed to export bookmarks for user {}", userId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    /**
     * 递归渲染一个文件夹为 Netscape HTML 结构，拼接到 StringBuilder
     *
     * 渲染规则（对应 Netscape 格式）：
     *   <DT><H3>文件夹名</H3>
     *   <DL><p>
     *     ... 该文件夹直属的书签（<DT><A>）...
     *     ... 递归渲染子文件夹 ...
     *   </DL><p>
     *
     * 渲染顺序：先书签后子文件夹（顺序不影响导入解析，但保持一种固定约定便于阅读）。
     * 用缩进（depth）让导出文件可读，Chrome 官方导出也带缩进。
     *
     * @param html             目标 StringBuilder
     * @param folder           当前要渲染的文件夹
     * @param depth            当前缩进深度（根=1），仅用于格式化美观
     * @param bookmarksByFolder 书签按 folderId 分组的映射
     * @param foldersByParent   子文件夹按 parentId 分组的映射
     */
    private void renderFolder(StringBuilder html, Folder folder, int depth,
                              Map<Long, List<Bookmark>> bookmarksByFolder,
                              Map<Long, List<Folder>> foldersByParent) {
        String indent = "    ".repeat(depth);

        // 1. <DT><H3>文件夹名</H3>（文件夹名需 HTML 转义，防止特殊字符破坏结构）
        html.append(indent).append("<DT><H3>")
                .append(escapeHtml(folder.getName()))
                .append("</H3>\n");

        // 2. 该文件夹的内容容器 <DL><p>
        html.append(indent).append("<DL><p>\n");

        // 3. 先渲染该文件夹直属的书签
        List<Bookmark> folderBookmarks = bookmarksByFolder.getOrDefault(folder.getId(), new ArrayList<>());
        for (Bookmark bm : folderBookmarks) {
            renderBookmark(html, bm, depth + 1);
        }

        // 4. 再递归渲染子文件夹
        List<Folder> childFolders = foldersByParent.getOrDefault(folder.getId(), new ArrayList<>());
        for (Folder child : childFolders) {
            renderFolder(html, child, depth + 1, bookmarksByFolder, foldersByParent);
        }

        // 5. 闭合 <DL>
        html.append(indent).append("</DL><p>\n");
    }

    /**
     * 渲染单个书签为 <DT><A HREF ICON>书签标题</A>
     *
     * ICON 属性：
     *   数据库 bookmark.icon_url 存的就是 base64 data URI（如 "data:image/png;base64,iVBOR..."），
     *   直接作为 ICON 属性值输出即可，导入端会原样读回。
     *   icon_url 为 null/空的书签不输出 ICON 属性（Chrome 也能处理无图标的书签）。
     *
     * @param html      目标 StringBuilder
     * @param bookmark  要渲染的书签
     * @param depth     当前缩进深度
     */
    private void renderBookmark(StringBuilder html, Bookmark bookmark, int depth) {
        String indent = "    ".repeat(depth);
        String url = bookmark.getUrl() != null ? bookmark.getUrl() : "";
        String title = bookmark.getTitle() != null ? bookmark.getTitle() : "";
        String icon = bookmark.getIconUrl();

        // URL 和 ICON 是属性值，需要转义 " 和 &；标题是文本内容，转义 <>&
        html.append(indent).append("<DT><A HREF=\"")
                .append(escapeAttr(url))
                .append("\"");
        if (icon != null && !icon.isEmpty()) {
            html.append(" ICON=\"").append(escapeAttr(icon)).append("\"");
        }
        html.append(">")
                .append(escapeHtml(title))
                .append("</A>\n");
    }

    /**
     * HTML 文本内容转义（用于 <H3>、<A>标签之间的文本）
     *
     * 只需转义会破坏标签结构的字符：< > &。
     * 不转义 " ' （它们在文本内容中无害，转义反而让导出文件可读性变差）。
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * HTML 属性值转义（用于 HREF="..."、ICON="..." 的属性值）
     *
     * 属性值用 " 包裹，所以必须转义 " 和 &，防止提前结束属性。
     * < > 一并转义保持 XML/HTML 兼容。
     */
    private String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
