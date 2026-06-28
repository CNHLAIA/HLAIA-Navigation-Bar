package com.hlaia.service;

import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.BookmarkImportResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【BookmarkExportService 单元测试】—— 测试书签导出功能的 HTML 生成逻辑
 *
 * 与 BookmarkImportServiceTest 对称：导入测"HTML→DB"，导出测"DB→HTML"。
 *
 * 最核心的测试是 AC5 闭环验证（{@link #shouldRoundTrip_whenExportThenImport}）：
 *   把导出服务生成的 HTML 字节，原样喂给导入服务（BookmarkImportService），
 *   验证能被正确解析并还原出原来的文件夹+书签结构。
 *   这是"导出→再导入"兼容性的硬指标——只有通过它，导出文件才有灾备/迁移价值。
 *
 * Mock 策略与导入测试一致：
 *   - Mock FolderMapper / BookmarkMapper，避免真实数据库
 *   - selectList 返回预设的文件夹/书签数据（模拟数据库中的内容）
 *   - 导出是纯读操作，只需 mock selectList，不需要 mock insert
 *
 * 闭环测试中，导入端的 insert 需要 mock 自增主键回填（与导入测试的 setupFolderMapperInsert 同款）。
 */
@ExtendWith(MockitoExtension.class)
class BookmarkExportServiceTest {

    @Mock
    private FolderMapper folderMapper;

    @Mock
    private BookmarkMapper bookmarkMapper;

    @InjectMocks
    private BookmarkExportService bookmarkExportService;

    /**
     * 辅助：构造一个文件夹实体
     */
    private Folder folder(Long id, Long parentId, String name, int sortOrder) {
        Folder f = new Folder();
        f.setId(id);
        f.setUserId(1L);
        f.setParentId(parentId);
        f.setName(name);
        f.setSortOrder(sortOrder);
        return f;
    }

    /**
     * 辅助：构造一个书签实体
     */
    private Bookmark bookmark(Long id, Long folderId, String title, String url, String iconUrl, int sortOrder) {
        Bookmark b = new Bookmark();
        b.setId(id);
        b.setUserId(1L);
        b.setFolderId(folderId);
        b.setTitle(title);
        b.setUrl(url);
        b.setIconUrl(iconUrl);
        b.setSortOrder(sortOrder);
        return b;
    }

    // ========================================================================
    // 测试用例
    // ========================================================================

    /**
     * 测试用例 1：导出单个文件夹 + 书签，验证 Netscape HTML 基本结构
     *
     * 验证导出 HTML 包含：
     *   - Netscape 文件头（DOCTYPE + META + TITLE + H1）
     *   - 文件夹名作为 <H3>
     *   - 书签的 HREF、ICON、标题文本
     *   - 正确的 <DL> 嵌套结构
     */
    @Test
    @DisplayName("导出基本结构：单文件夹+书签生成合法 Netscape HTML")
    void shouldGenerateValidNetscapeHtml_whenSingleFolderWithBookmark() {
        Long userId = 1L;
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(10L, null, "工具", 0)
        ));
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(20L, 10L, "GitHub", "https://github.com",
                        "data:image/png;base64,abc", 0)
        ));

        byte[] result = bookmarkExportService.exportBookmarks(userId);
        String html = new String(result, StandardCharsets.UTF_8);

        // 文件头
        assertThat(html).contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>");
        assertThat(html).contains("charset=UTF-8");
        assertThat(html).contains("<TITLE>Bookmarks</TITLE>");
        assertThat(html).contains("<H1>Bookmarks</H1>");

        // 文件夹
        assertThat(html).contains("<H3>工具</H3>");
        // 书签：HREF、ICON、标题都在
        assertThat(html).contains("HREF=\"https://github.com\"");
        assertThat(html).contains("ICON=\"data:image/png;base64,abc\"");
        assertThat(html).contains("GitHub</A>");

        // 顶层 <DL><p> 存在
        assertThat(html).contains("<DL><p>");
    }

    /**
     * 测试用例 2：导出多层嵌套文件夹
     *
     * 结构：
     *   工作 (root)
     *   └── 前端
     *       └── Vue
     *           └── [书签: vuejs.org]
     *
     * 验证递归正确生成层级（虽然字符串拼接不强制缩进对应层级，
     * 但文件夹与书签的从属关系必须由 <DL> 嵌套正确表达）。
     */
    @Test
    @DisplayName("多层嵌套：递归生成正确的文件夹层级")
    void shouldGenerateNestedStructure_whenMultipleFolderLevels() {
        Long userId = 1L;
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(1L, null, "工作", 0),
                folder(2L, 1L, "前端", 0),
                folder(3L, 2L, "Vue", 0)
        ));
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(100L, 3L, "Vue.js", "https://vuejs.org", null, 0)
        ));

        String html = new String(bookmarkExportService.exportBookmarks(userId),
                StandardCharsets.UTF_8);

        // 三个文件夹名都出现
        assertThat(html).contains("<H3>工作</H3>");
        assertThat(html).contains("<H3>前端</H3>");
        assertThat(html).contains("<H3>Vue</H3>");
        // 最内层书签
        assertThat(html).contains("HREF=\"https://vuejs.org\"");
        assertThat(html).contains("Vue.js</A>");

        // 结构正确性：三个 <H3> + 对应的闭合 </DL><p>
        // 每个文件夹对应一个开 <DL> 和一个 </DL><p>，加上顶层 1 个，共 4 对
        long dlOpen = countOccurrences(html, "<DL><p>");
        long dlClose = countOccurrences(html, "</DL><p>");
        assertThat(dlOpen).isEqualTo(4); // 顶层 + 3 个文件夹
        assertThat(dlClose).isEqualTo(4);
    }

    /**
     * 测试用例 3：书签标题/URL 含特殊字符时正确转义
     *
     * 这是最容易出 bug 的场景：如果转义缺失，特殊字符会破坏 HTML 结构，
     * 导致导入端解析失败。
     *   - 标题含 < > &：文本节点需转义
     *   - URL 含 " &：属性值需转义
     */
    @Test
    @DisplayName("HTML 转义：特殊字符不破坏结构")
    void shouldEscapeSpecialChars_whenTitleOrUrlContainsThem() {
        Long userId = 1L;
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(10L, null, "测试&目录", 0)
        ));
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(20L, 10L, "A<B & C>", "https://x.com/?q=\"hi\"&p=1",
                        null, 0)
        ));

        String html = new String(bookmarkExportService.exportBookmarks(userId),
                StandardCharsets.UTF_8);

        // 文件夹名中的 & 转义为 &amp;
        assertThat(html).contains("<H3>测试&amp;目录</H3>");
        // 标题中的 < > & 转义
        assertThat(html).contains("A&lt;B &amp; C&gt;</A>");
        // URL 属性中的 " 转义为 &quot;，& 转义为 &amp;
        assertThat(html).contains("HREF=\"https://x.com/?q=&quot;hi&quot;&amp;p=1\"");
    }

    /**
     * 测试用例 4：书签无 icon_url 时不输出 ICON 属性
     *
     * 验证：icon_url 为 null 时，<A> 标签不带 ICON 属性（Chrome 也能处理无图标书签）。
     */
    @Test
    @DisplayName("无图标书签：不输出 ICON 属性")
    void shouldOmitIconAttr_whenIconUrlIsNull() {
        Long userId = 1L;
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(10L, null, "无图", 0)
        ));
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(
                bookmark(20L, 10L, "无图标站", "https://noicon.com", null, 0)
        ));

        String html = new String(bookmarkExportService.exportBookmarks(userId),
                StandardCharsets.UTF_8);

        // 含 HREF 但不含 ICON
        assertThat(html).contains("HREF=\"https://noicon.com\"");
        assertThat(html).doesNotContain("ICON=");
    }

    /**
     * 测试用例 5：空数据导出合法 HTML（AC8）
     *
     * 用户没有任何文件夹和书签时，导出不报错，
     * 返回包含空根 <DL> 的合法 Netscape HTML。
     */
    @Test
    @DisplayName("空数据：返回合法的空 Netscape HTML")
    void shouldReturnValidEmptyHtml_whenNoData() {
        Long userId = 1L;
        when(folderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());

        byte[] result = bookmarkExportService.exportBookmarks(userId);
        String html = new String(result, StandardCharsets.UTF_8);

        // 仍含文件头和顶层 DL
        assertThat(html).contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>");
        assertThat(html).contains("<DL><p>");
        assertThat(html).contains("</DL><p>");
        // 不含任何 H3 或 A
        assertThat(html).doesNotContain("<H3");
        assertThat(html).doesNotContain("<A ");
    }

    /**
     * 测试用例 6（核心 · AC5）：导出 → 导入 闭环
     *
     * 这是最重要的测试：验证导出服务生成的 HTML 能被导入服务完整解析还原。
     *
     * 流程：
     *   1. 准备一组文件夹+书签数据
     *   2. 调 exportBookmarks 生成 HTML 字节
     *   3. 把 HTML 字节包成 MockMultipartFile，喂给 BookmarkImportService.importBookmarks
     *   4. 断言导入端能解析出正确数量的文件夹和书签（结构被还原）
     *
     * 如果导出格式与导入解析逻辑不兼容，这个测试会失败，
     * 说明导出的文件无法在本系统内恢复，灾备/迁移价值丧失。
     */
    @Test
    @DisplayName("AC5 闭环：导出的 HTML 能被导入服务完整还原")
    void shouldRoundTrip_whenExportThenImport() {
        Long userId = 1L;

        // ===== 准备源数据：2 个根文件夹，1 个子文件夹，3 个书签 =====
        List<Folder> sourceFolders = List.of(
                folder(1L, null, "开发", 0),
                folder(2L, null, "工具", 1),
                folder(3L, 1L, "前端", 0)
        );
        List<Bookmark> sourceBookmarks = List.of(
                bookmark(100L, 1L, "Java", "https://java.com",
                        "data:image/png;base64,java", 0),
                bookmark(101L, 3L, "Vue", "https://vuejs.org",
                        "data:image/png;base64,vue", 0),
                bookmark(102L, 2L, "VS Code", "https://code.visualstudio.com",
                        null, 0)
        );

        when(folderMapper.selectList(any())).thenReturn(sourceFolders);
        when(bookmarkMapper.selectList(any())).thenReturn(sourceBookmarks);

        // ===== Step A：导出 =====
        byte[] htmlBytes = bookmarkExportService.exportBookmarks(userId);
        assertThat(htmlBytes).isNotEmpty();

        // ===== Step B：把导出的 HTML 喂给导入服务 =====
        // 重新配置 Mock：导入端会先 selectList 查已有书签（返回空，模拟全新导入），
        // 然后 insert 文件夹和书签。insert 需要 mock 自增主键回填。
        MockMultipartFile importFile = new MockMultipartFile(
                "file", "bookmarks.html", "text/html", htmlBytes);

        AtomicLong folderIdGen = new AtomicLong(1000);
        AtomicLong bookmarkIdGen = new AtomicLong(2000);
        List<Bookmark> importedBookmarks = new ArrayList<>();

        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(folderMapper.insert(any(Folder.class))).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            var idField = Folder.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(f, folderIdGen.incrementAndGet());
            return 1;
        });
        when(bookmarkMapper.insert(any(Bookmark.class))).thenAnswer(inv -> {
            Bookmark b = inv.getArgument(0);
            var idField = Bookmark.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(b, bookmarkIdGen.incrementAndGet());
            importedBookmarks.add(b);
            return 1;
        });

        BookmarkImportService importService = new BookmarkImportService(folderMapper, bookmarkMapper);
        BookmarkImportResponse response = importService.importBookmarks(
                userId, importFile, null, "OVERWRITE");

        // ===== 验证：导入端能完整还原结构 =====
        // 源数据有 3 个文件夹（开发、工具、前端）和 3 个书签（Java、Vue、VS Code）
        assertThat(response.getFoldersCreated()).isEqualTo(3);
        assertThat(response.getBookmarksCreated()).isEqualTo(3);
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);
        assertThat(response.getBookmarksSkipped()).isEqualTo(0);

        // 验证书签标题和 URL 都被还原
        assertThat(importedBookmarks).hasSize(3);
        assertThat(importedBookmarks).extracting(Bookmark::getTitle)
                .containsExactlyInAnyOrder("Java", "Vue", "VS Code");
        assertThat(importedBookmarks).extracting(Bookmark::getUrl)
                .containsExactlyInAnyOrder(
                        "https://java.com", "https://vuejs.org",
                        "https://code.visualstudio.com");
        // 含图标的书签，图标也被还原
        Bookmark javaBm = importedBookmarks.stream()
                .filter(b -> "Java".equals(b.getTitle())).findFirst().orElseThrow();
        assertThat(javaBm.getIconUrl()).isEqualTo("data:image/png;base64,java");

        // 验证文件夹层级被还原：前端文件夹的 parentId 应指向"开发"文件夹的新 ID
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderMapper, times(3)).insert(folderCaptor.capture());
        List<Folder> importedFolders = folderCaptor.getAllValues();
        Folder devFolder = importedFolders.stream()
                .filter(f -> "开发".equals(f.getName())).findFirst().orElseThrow();
        Folder frontFolder = importedFolders.stream()
                .filter(f -> "前端".equals(f.getName())).findFirst().orElseThrow();
        assertThat(frontFolder.getParentId()).isEqualTo(devFolder.getId());

        // 验证书签归属正确：Vue 书签应在"前端"文件夹下
        Bookmark vueBm = importedBookmarks.stream()
                .filter(b -> "Vue".equals(b.getTitle())).findFirst().orElseThrow();
        assertThat(vueBm.getFolderId()).isEqualTo(frontFolder.getId());
    }

    /**
     * 辅助：统计子串出现次数
     */
    private long countOccurrences(String text, String sub) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
