package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.BookmarkImportResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【书签导入服务类】—— 解析 Chrome 浏览器导出的书签 HTML 文件并批量导入到系统中
 *
 * 什么是浏览器书签导入？
 *   Chrome 浏览器可以将用户收藏的书签导出为一个 HTML 文件（Netscape Bookmark 格式），
 *   这个文件里包含了文件夹层级和书签信息（URL、标题、图标等）。
 *   本服务的职责是：读取这个 HTML 文件，解析出文件夹树和书签列表，
 *   然后批量创建到我们的数据库中。
 *
 * Chrome 书签 HTML 文件的结构（Netscape Bookmark 格式）：
 *   <DL><p>         —— 定义列表，表示一个文件夹的内容
 *     <DT><H3>文件夹名</H3>   —— DT+H3 表示一个文件夹
 *     <DL><p>       —— 该文件夹的子内容（递归嵌套）
 *       <DT><A HREF="..." ICON="...">书签标题</A>  —— DT+A 表示一个书签
 *     </DL><p>
 *   </DL><p>
 *
 *   关键特点：
 *   - <DL> 标签可以无限嵌套，表示无限层级的文件夹树
 *   - <DT><H3> 后面紧跟的 <DL> 就是该文件夹的子内容
 *   - <A> 标签的 ICON 属性包含 base64 编码的 favicon 数据（data URI）
 *
 * 重复处理模式（duplicateMode）：
 *   导入时可能遇到系统中已存在的 URL，有两种处理策略：
 *   - OVERWRITE（默认）：用导入数据覆盖更新已有书签的标题和图标
 *   - SKIP：跳过已有书签，保留系统中的原数据
 *
 * 事务保证：
 *   整个导入过程包裹在 @Transactional 中。
 *   如果解析或导入过程中出现任何异常，所有数据库操作都会回滚，
 *   不会留下"导入了一半"的脏数据。
 *
 * @Service 注解：告诉 Spring 这是一个业务逻辑类，纳入容器管理
 * @RequiredArgsConstructor 注解：Lombok 自动为 final 字段生成构造方法，Spring 自动注入依赖
 * @Slf4j 注解：Lombok 自动生成 log（Logger）对象，用于记录日志
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookmarkImportService {

    // 依赖注入：通过 final + @RequiredArgsConstructor 实现
    private final FolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;

    /**
     * 导入浏览器书签 HTML 文件
     *
     * 完整的导入流程：
     *   1. 解析 HTML 文件，找到根 <DL> 标签
     *   2. 预加载用户已有的书签 URL，用于重复检测
     *   3. 递归遍历 <DL> 树，创建 Folder 和 Bookmark 记录
     *   4. 返回导入统计信息
     *
     * @Transactional 注解的作用：
     *   保证整个导入过程的原子性——要么全部成功，要么全部回滚。
     *   导入操作可能涉及数十甚至数百次 INSERT/UPDATE，
     *   如果中途出现异常（如解析失败），所有已创建的文件夹和书签都会被撤销，
     *   数据库不会留下不完整的导入数据。
     *
     * 为什么 duplicateMode 参数是 String 而不是枚举？
     *   因为这个值来自 Controller 的 @RequestParam，HTTP 请求参数都是字符串。
     *   在方法内部通过字符串比较来处理，简单直观。
     *
     * @param userId         当前登录用户的 ID（数据隔离：每个用户只能导入到自己的空间）
     * @param file           上传的 HTML 文件（MultipartFile，Spring 自动处理文件上传）
     * @param targetFolderId 目标文件夹 ID，null 表示导入到根级别
     * @param duplicateMode  重复处理模式："OVERWRITE"（默认）或 "SKIP"
     * @return BookmarkImportResponse 导入统计信息（新建文件夹数、新建/更新/跳过的书签数）
     */
    @Transactional
    public BookmarkImportResponse importBookmarks(Long userId, MultipartFile file,
                                                   Long targetFolderId, String duplicateMode) {
        // ============ 第一步：校验目标文件夹（如果指定了的话） ============
        // 如果用户指定了目标文件夹，需要验证该文件夹存在且属于当前用户
        if (targetFolderId != null) {
            Folder targetFolder = folderMapper.selectById(targetFolderId);
            if (targetFolder == null || !targetFolder.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
            }
        }

        // ============ 第二步：解析 HTML 文件 ============
        // 使用 Jsoup 解析上传的 HTML 文件
        Document doc;
        try (InputStream is = file.getInputStream()) {
            // Jsoup.parse(InputStream, charsetName, baseUri) 方法说明：
            //   - InputStream：从 MultipartFile 获取输入流
            //   - "UTF-8"：Chrome 书签文件使用 UTF-8 编码
            //   - ""：baseUri 设为空字符串（我们不需要解析相对路径）
            doc = Jsoup.parse(is, "UTF-8", "");
        } catch (IOException e) {
            // 文件读取失败时记录日志并抛出业务异常
            // log.error 是 @Slf4j 提供的日志方法，记录错误级别的日志
            log.error("Failed to read bookmark import file", e);
            throw new BusinessException(ErrorCode.IMPORT_FAILED);
        }

        // 找到文档中的第一个 <DL> 标签，它是整个书签树的根节点
        // Chrome 书签文件的根结构：<H1>Bookmarks</H1> 后面紧跟 <DL><p>
        Element rootDl = doc.selectFirst("DL");
        if (rootDl == null) {
            // 如果 HTML 中没有 <DL> 标签，说明文件格式不正确
            log.warn("No <DL> tag found in imported bookmark file");
            throw new BusinessException(ErrorCode.IMPORT_FAILED);
        }

        // ============ 第三步：预加载用户已有的书签 URL，用于重复检测 ============
        // 在导入开始前，一次性查出用户所有已有书签的 URL 集合
        // 为什么在导入前一次性查询而不是每导入一个书签就查一次？
        //   - 性能：一次查询 vs 几百次查询，差距巨大
        //   - 逻辑清晰：先拿到"已有 URL 全集"，后续导入时只需做内存比对

        // existingUrls：用于快速判断某个 URL 是否已存在（O(1) 时间复杂度）
        Set<String> existingUrls = new HashSet<>();
        // existingByUrl：用于在 OVERWRITE 模式下，快速找到已有书签实体进行更新
        Map<String, Bookmark> existingByUrl = new HashMap<>();

        List<Bookmark> userBookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getUserId, userId));
        for (Bookmark b : userBookmarks) {
            // URL 可能为 null（虽然理论上不应该），做安全检查
            if (b.getUrl() != null) {
                existingUrls.add(b.getUrl());
                existingByUrl.put(b.getUrl(), b);
            }
        }

        // ============ 第四步：递归解析并导入 ============
        BookmarkImportResponse response = new BookmarkImportResponse();

        // 递归解析根 <DL> 下的所有子元素
        // targetFolderId 作为第一层文件夹/书签的 parentId
        parseAndImport(userId, rootDl, targetFolderId, duplicateMode,
                existingUrls, existingByUrl, response);

        log.info("Bookmark import completed for user {}: {} folders, {} bookmarks created, "
                        + "{} updated, {} skipped",
                userId, response.getFoldersCreated(), response.getBookmarksCreated(),
                response.getBookmarksUpdated(), response.getBookmarksSkipped());

        return response;
    }

    /**
     * 递归解析 <DL> 元素并导入其中的文件夹和书签
     *
     * 递归算法的核心思想：
     *   对于每个 <DL>（定义列表），遍历它的直接 <DT> 子元素：
     *   - 如果 <DT> 包含 <H3>，说明这是一个文件夹：
     *     创建 Folder 记录，然后递归处理该文件夹对应的 <DL> 子元素
     *   - 如果 <DT> 包含 <A>，说明这是一个书签：
     *     提取 URL、标题、图标信息，创建或更新 Bookmark 记录
     *
     * Chrome 书签 HTML 中 <DT> 和 <DL> 的关系：
     *   <DT><H3>文件夹名</H3>    ← 这个 <DT> 定义了一个文件夹
     *   <DL><p>                  ← 紧跟的 <DL> 包含该文件夹的子内容
     *     <DT><A ...>书签</A>    ← 子内容中的书签
     *   </DL><p>
     *
     *   注意：在 Chrome 导出的 HTML 中，文件夹的 <DT> 和它的子 <DL> 是兄弟节点
     *   （同级关系，而非父子关系）。所以我们需要查找 <DT> 同级或之后的 <DL>。
     *
     * parentId 映射的设计：
     *   递归时，每次创建新的 Folder，数据库会自动生成一个 ID（自增主键）。
     *   我们用这个新 ID 作为下一层递归的 parentId 参数传递下去。
     *   这样就维护了"父文件夹 -> 子文件夹"的正确层级关系。
     *
     * @param userId         当前用户 ID
     * @param dl             当前要解析的 <DL> 元素
     * @param parentId       当前层级的父文件夹 ID（null 表示根级别）
     * @param duplicateMode  重复处理模式
     * @param existingUrls   用户已有的 URL 集合（用于重复检测）
     * @param existingByUrl  URL -> 已有 Bookmark 的映射（用于覆盖更新）
     * @param response       统计信息累加器（在递归过程中不断累加）
     */
    private void parseAndImport(Long userId, Element dl, Long parentId,
                                 String duplicateMode,
                                 Set<String> existingUrls,
                                 Map<String, Bookmark> existingByUrl,
                                 BookmarkImportResponse response) {
        // sortOrder 用于记录同级元素的位置顺序
        // 每个子元素按出现的顺序依次分配 0, 1, 2, ...
        int sortOrder = 0;

        // 遍历 <DL> 下的直接 <DT> 子元素
        // 为什么用 dl.children() 而不是 dl.select("DT")？
        //   dl.select("DT") 会递归查找所有层级的 <DT>，包括嵌套子文件夹中的。
        //   而我们只需要当前层级的 <DT>，所以遍历直接子元素更精确。
        //   Chrome 书签 HTML 中，<DT> 是 <DL> 的直接子元素。
        for (Element child : dl.children()) {
            if (!child.tagName().equalsIgnoreCase("DT")) {
                // 跳过非 <DT> 元素（如文本节点、<p> 标签等）
                continue;
            }

            // 检查这个 <DT> 是否包含 <H3>（文件夹）或 <A>（书签）
            Element h3 = child.selectFirst("H3");
            Element a = child.selectFirst("A");

            if (h3 != null) {
                // ====== 情况一：文件夹（<DT> 包含 <H3>）======
                Folder folder = createFolder(userId, parentId, h3.text(), sortOrder);
                response.setFoldersCreated(response.getFoldersCreated() + 1);

                // 查找该文件夹对应的子 <DL>
                // Chrome 导出的 HTML 中，<DT><H3> 和 <DL> 是相邻标签（兄弟关系），
                // 但 Jsoup 解析时会自动将 <DL> 归为 <DT> 的子元素（因为 HTML 规范中 DL 可以嵌套在 DT 内）。
                // 所以我们优先在 <DT> 内部查找 <DL>（child.selectFirst），
                // 如果没找到再尝试兄弟元素（兼容其他可能的 HTML 结构）。
                Element subDl = child.selectFirst("DL");
                if (subDl == null) {
                    subDl = child.nextElementSibling();
                }
                if (subDl != null && subDl.tagName().equalsIgnoreCase("DL")) {
                    // 递归处理子文件夹的内容
                    // folder.getId() 是数据库刚生成的自增主键，作为子级的 parentId
                    parseAndImport(userId, subDl, folder.getId(), duplicateMode,
                            existingUrls, existingByUrl, response);
                }

            } else if (a != null) {
                // ====== 情况二：书签（<DT> 包含 <A>）======
                String url = a.attr("HREF");
                String title = a.text();
                // ICON 属性包含 base64 编码的 favicon 数据（data:image/png;base64,...）
                String icon = a.attr("ICON");

                // 跳过空 URL（某些书签可能没有有效的 HREF）
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }

                // 重复检测和处理
                if (existingUrls.contains(url)) {
                    // URL 已存在，根据重复处理模式决定行为
                    if ("SKIP".equalsIgnoreCase(duplicateMode)) {
                        // 跳过模式：不做任何操作，只增加跳过计数
                        response.setBookmarksSkipped(response.getBookmarksSkipped() + 1);
                    } else {
                        // 覆盖模式（默认）：更新已有书签的标题和图标
                        Bookmark existing = existingByUrl.get(url);
                        if (existing != null) {
                            existing.setTitle(title);
                            existing.setIconUrl(icon != null && !icon.isEmpty() ? icon : existing.getIconUrl());
                            bookmarkMapper.updateById(existing);
                            response.setBookmarksUpdated(response.getBookmarksUpdated() + 1);
                        }
                    }
                } else {
                    // URL 不存在，创建新书签
                    createBookmark(userId, parentId, title, url, icon, sortOrder);
                    response.setBookmarksCreated(response.getBookmarksCreated() + 1);
                    // 将新 URL 加入集合，防止同一批次中的重复导入
                    existingUrls.add(url);
                }
            }

            // 只有真正处理了文件夹或书签时才递增排序序号
            if (h3 != null || a != null) {
                sortOrder++;
            }
        }
    }

    /**
     * 创建文件夹记录
     *
     * 为什么抽取为独立方法？
     *   虽然 createFolder 和 createBookmark 的代码量不大，
     *   但抽取后 parseAndImport 方法的逻辑更清晰——只关注"解析和分发"，
     *   具体的"创建"细节委托给辅助方法。
     *
     * @param userId    用户 ID
     * @param parentId  父文件夹 ID（null 表示根级别）
     * @param name      文件夹名称
     * @param sortOrder 排序序号
     * @return 创建后的 Folder 实体（包含数据库生成的自增 ID）
     */
    private Folder createFolder(Long userId, Long parentId, String name, int sortOrder) {
        Folder folder = new Folder();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setSortOrder(sortOrder);
        folderMapper.insert(folder);
        // insert 成功后，MyBatis-Plus 会自动回填自增主键到 folder.getId()
        return folder;
    }

    /**
     * 创建书签记录
     *
     * iconUrl 的处理逻辑：
     *   Chrome 书签中的 ICON 属性包含 base64 编码的 favicon 数据，
     *   格式为 "data:image/png;base64,iVBORw0KGgo..."。
     *   这种 data URI 可以直接存在 iconUrl 字段中，
     *   前端用 <img src="data:image/png;base64,..."> 就能显示图标。
     *
     * @param userId    用户 ID
     * @param folderId  所属文件夹 ID
     * @param title     书签标题
     * @param url       书签链接
     * @param iconUrl   网站图标（可能是 data URI 或 null）
     * @param sortOrder 排序序号
     */
    private void createBookmark(Long userId, Long folderId, String title,
                                 String url, String iconUrl, int sortOrder) {
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(folderId);
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setIconUrl(iconUrl != null && !iconUrl.isEmpty() ? iconUrl : null);
        bookmark.setSortOrder(sortOrder);
        bookmarkMapper.insert(bookmark);
    }
}
