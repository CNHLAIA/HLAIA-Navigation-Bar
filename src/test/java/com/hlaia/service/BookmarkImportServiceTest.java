package com.hlaia.service;

import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.BookmarkImportResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【BookmarkImportService 单元测试】—— 测试浏览器书签导入功能的 HTML 解析和数据库导入逻辑
 *
 * 什么是单元测试？
 *   单元测试是针对代码中最小可测试单元（通常是一个方法）编写的自动化测试。
 *   目的是验证每个方法在各种输入条件下的行为是否符合预期。
 *   好的单元测试应该是：快速、独立、可重复、自验证的。
 *
 * 为什么使用 Mockito（Mock 框架）？
 *   BookmarkImportService 依赖 FolderMapper 和 BookmarkMapper（数据库操作层）。
 *   在单元测试中，我们不希望真正连接数据库，原因有：
 *   1. 速度慢：数据库操作比内存操作慢几个数量级
 *   2. 不可控：数据库状态会影响测试结果，导致测试不稳定
 *   3. 隔离性：单元测试应该只测试一个类的逻辑，不应该受其他层影响
 *
 *   Mockito 可以创建 Mapper 接口的"假实现"（Mock 对象），
 *   我们可以精确控制这些 Mock 对象的行为（比如 insert 返回什么值），
 *   从而专注于测试 BookmarkImportService 自身的业务逻辑。
 *
 * @ExtendWith(MockitoExtension.class) 注解的作用：
 *   告诉 JUnit 5 启用 Mockito 扩展，自动处理 @Mock 和 @InjectMocks 注解。
 *   不需要手动创建 Mockito 环境。
 *
 * 测试命名约定：
 *   方法名使用 "should_预期行为_when_条件" 格式，一目了然地表达测试意图。
 *   配合 @DisplayName 注解提供中文描述。
 */
@ExtendWith(MockitoExtension.class)
class BookmarkImportServiceTest {

    /**
     * @Mock 注解：让 Mockito 创建 FolderMapper 的 Mock 对象
     *
     * Mock 对象是什么？
     *   Mock 对象是接口或类的"替身"，它不会执行真正的逻辑，
     *   而是根据我们的配置返回预设的值。
     *   比如调用 mockFolderMapper.insert(folder) 不会真的往数据库插入数据，
     *   而是返回我们指定的值（如 1，表示影响行数）。
     */
    @Mock
    private FolderMapper folderMapper;

    @Mock
    private BookmarkMapper bookmarkMapper;

    /**
     * @InjectMocks 注解：让 Mockito 自动创建 BookmarkImportService 实例，
     * 并把上面声明的 @Mock 对象注入到它的构造方法参数中。
     *
     * 为什么能自动注入？
     *   BookmarkImportService 使用了 @RequiredArgsConstructor，
     *   Lombok 为它的 final 字段生成了构造方法。
     *   Mockito 会找到这个构造方法，把 Mock 对象作为参数传进去。
     *   这模拟了 Spring 的依赖注入过程，但不需要启动 Spring 容器。
     */
    @InjectMocks
    private BookmarkImportService bookmarkImportService;

    /**
     * 用于模拟 MyBatis-Plus 自增主键的 ID 生成器
     *
     * 为什么需要这个？
     *   在真实环境中，当 MyBatis-Plus 执行 insert 后，数据库会自动生成自增主键，
     *   MyBatis-Plus 会把这个 ID 回填到实体对象的 id 字段中。
     *   在测试中，我们需要模拟这个行为：
     *   当 folderMapper.insert(folder) 被调用时，通过反射设置 folder.id 为一个递增的值。
     *
     * 为什么用 AtomicLong 而不是普通 Long？
     *   AtomicLong 是线程安全的，即使在并发场景下也能保证 ID 递增。
     *   虽然我们的测试是单线程的，但这是一个好习惯。
     *   更重要的是，AtomicLong 可以在 Lambda 表达式中修改（普通 long 不行）。
     */
    private AtomicLong mockFolderId;
    private AtomicLong mockBookmarkId;

    /**
     * @BeforeEach 注解：在每个测试方法执行前都会运行这个方法
     *
     * 用途：初始化测试所需的公共状态，避免每个测试方法重复相同的初始化代码。
     */
    @BeforeEach
    void setUp() {
        // 从 100 开始递增，避免和 null 或 0 混淆
        mockFolderId = new AtomicLong(100);
        mockBookmarkId = new AtomicLong(200);
    }

    // ========================================================================
    // 辅助方法：创建测试用的 Mock HTML 文件
    // ========================================================================

    /**
     * 创建模拟的书签 HTML 文件（MockMultipartFile）
     *
     * MockMultipartFile 是 Spring Test 提供的测试工具类，
     * 它模拟了 MultipartFile 接口的行为，不需要真正上传文件。
     *
     * @param html 书签 HTML 内容字符串
     * @return 可以传给 importBookmarks 方法的 MockMultipartFile 对象
     */
    private MockMultipartFile createBookmarkHtml(String html) {
        return new MockMultipartFile(
                "file",
                "bookmarks.html",
                "text/html",
                html.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 配置 FolderMapper 的 insert Mock 行为：模拟 MyBatis-Plus 自增主键回填
     *
     * 核心原理：
     *   when(folderMapper.insert(any(Folder.class))).thenAnswer(...)
     *   - when(...)：定义"当某个方法被调用时"的行为
     *   - any(Folder.class)：匹配任何 Folder 参数（不关心具体值）
     *   - thenAnswer(...)：动态计算返回值，可以访问方法参数
     *
     *   invocation.getArgument(0) 获取传入的 Folder 对象，
     *   然后通过反射设置其 id 字段（因为 id 是 private 的，不能直接赋值）。
     *
     * 为什么用 thenAnswer 而不是 thenReturn？
     *   thenReturn 只能返回固定值，而我们需要在 insert 时修改传入对象的状态（设置 ID）。
     *   thenAnswer 提供了 InvocationOnMock 参数，可以访问方法参数并执行自定义逻辑。
     *
     * 为什么用反射设置 id？
     *   Folder.id 是 private 字段，正常情况下只能通过 setter 设置。
     *   但在 MyBatis-Plus 的 insert 过程中，ID 是框架通过反射回填的，
     *   我们的 Mock 也需要模拟这个反射过程。
     */
    private void setupFolderMapperInsert() {
        when(folderMapper.insert(any(Folder.class))).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            // 使用反射设置 id 字段，模拟 MyBatis-Plus 的自增主键回填
            var idField = Folder.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(folder, mockFolderId.incrementAndGet());
            return 1; // insert 返回影响的行数，1 表示成功插入一行
        });
    }

    /**
     * 配置 BookmarkMapper 的 insert Mock 行为：模拟自增主键回填
     *
     * 同时将所有插入的 Bookmark 收集到 allInsertedBookmarks 列表中，
     * 方便后续断言验证每次 insert 的参数。
     *
     * @param allInsertedBookmarks 用于收集所有 insert 调用参数的列表（由调用方提供）
     */
    private void setupBookmarkMapperInsert(List<Bookmark> allInsertedBookmarks) {
        when(bookmarkMapper.insert(any(Bookmark.class))).thenAnswer(invocation -> {
            Bookmark bookmark = invocation.getArgument(0);
            var idField = Bookmark.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(bookmark, mockBookmarkId.incrementAndGet());
            // 收集每次 insert 的书签对象，用于后续断言
            allInsertedBookmarks.add(bookmark);
            return 1;
        });
    }

    /**
     * 配置 BookmarkMapper 的 insert Mock 行为（不收集参数的简化版本）
     */
    private void setupBookmarkMapperInsert() {
        when(bookmarkMapper.insert(any(Bookmark.class))).thenAnswer(invocation -> {
            Bookmark bookmark = invocation.getArgument(0);
            var idField = Bookmark.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(bookmark, mockBookmarkId.incrementAndGet());
            return 1;
        });
    }

    // ========================================================================
    // 测试用例
    // ========================================================================

    /**
     * 测试用例 1：解析单个书签
     *
     * 测试目标：HTML 中只有一个 <A> 标签时，能正确创建 Bookmark 记录
     * 验证内容：title、url、iconUrl、folderId、userId、sortOrder 都正确设置
     *
     * 这个测试验证的是最简单的场景——没有文件夹嵌套，只有一个平铺的书签。
     * 如果这个基础场景不通，更复杂的嵌套场景肯定也不通。
     */
    @Test
    @DisplayName("解析单个书签：验证 bookmark 字段正确设置")
    void shouldCreateBookmark_whenSingleBookmarkInHtml() {
        // ===== 准备测试数据 =====
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><A HREF="https://example.com" ICON="data:image/png;base64,abc123">Example Site</A>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 配置 Mock 行为：用户没有任何已有书签（空列表）
        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupBookmarkMapperInsert();

        // ===== 执行测试 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证结果 =====

        // 1. 统计数据正确：0 个文件夹，1 个书签，0 个更新/跳过
        assertThat(response.getFoldersCreated()).isEqualTo(0);
        assertThat(response.getBookmarksCreated()).isEqualTo(1);
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);
        assertThat(response.getBookmarksSkipped()).isEqualTo(0);

        // 2. 捕获传入 insert 的 Bookmark 对象，验证各字段值
        // ArgumentCaptor 是 Mockito 提供的工具，可以"捕获"Mock 方法调用时的参数值，
        // 然后在断言中检查这些参数是否符合预期。
        ArgumentCaptor<Bookmark> bookmarkCaptor = ArgumentCaptor.forClass(Bookmark.class);
        // times(1) 表示验证 insert 被调用了 1 次（也可以省略，默认就是 1）
        verify(bookmarkMapper, times(1)).insert(bookmarkCaptor.capture());

        Bookmark created = bookmarkCaptor.getValue();
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getFolderId()).isNull(); // 根级别，无父文件夹
        assertThat(created.getTitle()).isEqualTo("Example Site");
        assertThat(created.getUrl()).isEqualTo("https://example.com");
        assertThat(created.getIconUrl()).isEqualTo("data:image/png;base64,abc123");
        assertThat(created.getSortOrder()).isEqualTo(0); // 第一个元素，sortOrder = 0
    }

    /**
     * 测试用例 2：解析包含书签的文件夹
     *
     * 测试目标：HTML 中有一个 <H3> 文件夹，里面包含多个 <A> 书签
     * 验证内容：
     *   - 文件夹被正确创建（name、parentId、sortOrder）
     *   - 书签的 folderId 指向新创建的文件夹 ID
     *   - 文件夹的 parentId 为 null（根级别）
     *
     * Chrome 书签 HTML 的典型结构：
     *   <DL>             <- 根列表
     *     <DT><H3>书签栏</H3>    <- 文件夹
     *     <DL>           <- 文件夹的子内容
     *       <DT><A ...>书签1</A>
     *       <DT><A ...>书签2</A>
     *     </DL>
     *   </DL>
     *
     * 注意：Chrome 导出的 HTML 中，<DT><H3> 和 <DL> 是相邻标签。
     * Jsoup 解析时会把 <DL> 自动嵌套到 <DT> 内部作为子元素，
     * 所以代码中使用 child.selectFirst("DL") 来查找子 <DL>。
     */
    @Test
    @DisplayName("解析文件夹中的书签：验证 folder 和 bookmark 的父子关系")
    void shouldCreateFolderWithBookmarks_whenHtmlContainsFolder() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><H3>书签栏</H3>
                    <DL><p>
                        <DT><A HREF="https://github.com" ICON="data:image/png;base64,gh">GitHub</A>
                        <DT><A HREF="https://stackoverflow.com">Stack Overflow</A>
                    </DL><p>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        List<Bookmark> insertedBookmarks = new ArrayList<>();
        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupFolderMapperInsert();
        setupBookmarkMapperInsert(insertedBookmarks);

        // ===== 执行 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证 =====

        // 统计数据：1 个文件夹，2 个书签
        assertThat(response.getFoldersCreated()).isEqualTo(1);
        assertThat(response.getBookmarksCreated()).isEqualTo(2);
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);
        assertThat(response.getBookmarksSkipped()).isEqualTo(0);

        // 验证文件夹创建：parentId 为 null（根级别），sortOrder 为 0
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderMapper, times(1)).insert(folderCaptor.capture());
        Folder createdFolder = folderCaptor.getValue();
        assertThat(createdFolder.getName()).isEqualTo("书签栏");
        assertThat(createdFolder.getParentId()).isNull();
        assertThat(createdFolder.getSortOrder()).isEqualTo(0);

        // 验证书签的 folderId 指向新创建的文件夹
        Long expectedFolderId = createdFolder.getId();
        assertThat(expectedFolderId).isNotNull();
        assertThat(insertedBookmarks).hasSize(2);
        assertThat(insertedBookmarks.get(0).getFolderId()).isEqualTo(expectedFolderId);
        assertThat(insertedBookmarks.get(0).getTitle()).isEqualTo("GitHub");
        assertThat(insertedBookmarks.get(1).getFolderId()).isEqualTo(expectedFolderId);
        assertThat(insertedBookmarks.get(1).getTitle()).isEqualTo("Stack Overflow");
    }

    /**
     * 测试用例 3：多层嵌套文件夹
     *
     * 测试目标：验证 2 层以上的文件夹嵌套时，parentId 链正确传递
     *
     * 嵌套结构示例：
     *   书签栏 (parentId=null)
     *     +-- 前端 (parentId=书签栏.id)
     *         +-- Vue (parentId=前端.id)
     *             +-- [书签: vuejs.org]
     *
     * 这个测试验证递归算法在多层嵌套时的正确性：
     * 每次递归调用都会把当前文件夹的 ID 作为下一层的 parentId 传入。
     */
    @Test
    @DisplayName("多层嵌套文件夹：验证 parent-child 层级关系正确")
    void shouldCreateNestedFolders_whenHtmlHasMultipleLevels() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><H3>书签栏</H3>
                    <DL><p>
                        <DT><H3>前端</H3>
                        <DL><p>
                            <DT><H3>Vue</H3>
                            <DL><p>
                                <DT><A HREF="https://vuejs.org">Vue.js</A>
                            </DL><p>
                        </DL><p>
                    </DL><p>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupFolderMapperInsert();
        setupBookmarkMapperInsert();

        // ===== 执行 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证 =====

        // 3 个文件夹：书签栏 -> 前端 -> Vue，1 个书签
        assertThat(response.getFoldersCreated()).isEqualTo(3);
        assertThat(response.getBookmarksCreated()).isEqualTo(1);

        // 捕获所有文件夹 insert 调用，验证层级关系
        // Mock ID 从 101 开始递增：书签栏=101, 前端=102, Vue=103
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderMapper, times(3)).insert(folderCaptor.capture());
        List<Folder> allFolders = folderCaptor.getAllValues();

        // 第 1 个文件夹：书签栏，根级别
        assertThat(allFolders.get(0).getName()).isEqualTo("书签栏");
        assertThat(allFolders.get(0).getParentId()).isNull();
        assertThat(allFolders.get(0).getId()).isEqualTo(101L);

        // 第 2 个文件夹：前端，父级是书签栏
        assertThat(allFolders.get(1).getName()).isEqualTo("前端");
        assertThat(allFolders.get(1).getParentId()).isEqualTo(101L);
        assertThat(allFolders.get(1).getId()).isEqualTo(102L);

        // 第 3 个文件夹：Vue，父级是前端
        assertThat(allFolders.get(2).getName()).isEqualTo("Vue");
        assertThat(allFolders.get(2).getParentId()).isEqualTo(102L);
        assertThat(allFolders.get(2).getId()).isEqualTo(103L);

        // 验证书签在最内层文件夹下
        ArgumentCaptor<Bookmark> bookmarkCaptor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkMapper, times(1)).insert(bookmarkCaptor.capture());
        Bookmark createdBookmark = bookmarkCaptor.getValue();
        assertThat(createdBookmark.getFolderId()).isEqualTo(103L); // Vue 文件夹
        assertThat(createdBookmark.getTitle()).isEqualTo("Vue.js");
    }

    /**
     * 测试用例 4：SKIP 模式处理重复 URL
     *
     * 测试目标：当导入的 URL 已存在于数据库中，duplicateMode=SKIP 时，
     *   不应创建新书签，而是跳过并增加 skipped 计数。
     *
     * SKIP 模式适用场景：
     *   用户已经整理好了书签信息（修改了标题、添加了描述），
     *   不希望被导入数据覆盖，选择跳过重复的书签。
     */
    @Test
    @DisplayName("SKIP 模式：重复 URL 不创建新书签，跳过计数 +1")
    void shouldSkipBookmark_whenDuplicateUrlAndSkipMode() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><A HREF="https://example.com" ICON="data:image/png;base64,newicon">New Title</A>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟数据库中已存在相同 URL 的书签
        Bookmark existingBookmark = new Bookmark();
        existingBookmark.setId(50L);
        existingBookmark.setUserId(userId);
        existingBookmark.setUrl("https://example.com");
        existingBookmark.setTitle("Original Title");
        existingBookmark.setIconUrl("data:image/png;base64,oldicon");

        // 配置 Mock：selectList 返回包含已有书签的列表
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(existingBookmark));

        // ===== 执行：使用 SKIP 模式 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "SKIP");

        // ===== 验证 =====

        // 统计数据：0 个新建，0 个更新，1 个跳过
        assertThat(response.getBookmarksCreated()).isEqualTo(0);
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);
        assertThat(response.getBookmarksSkipped()).isEqualTo(1);

        // 验证：没有创建新书签（insert 没被调用）
        verify(bookmarkMapper, never()).insert(any(Bookmark.class));
        // 验证：没有更新已有书签（updateById 没被调用）
        verify(bookmarkMapper, never()).updateById(any(Bookmark.class));
    }

    /**
     * 测试用例 5：OVERWRITE 模式处理重复 URL
     *
     * 测试目标：当导入的 URL 已存在于数据库中，duplicateMode=OVERWRITE（默认）时，
     *   应更新已有书签的标题和图标，并增加 updated 计数。
     *
     * OVERWRITE 模式适用场景：
     *   用户想用浏览器最新的书签信息更新系统中的旧数据，
     *   比如网站换了图标、用户在浏览器里改了书签标题。
     *
     * 为什么 icon 只在有新值时才更新？
     *   导入的书签可能没有 ICON 属性（有些书签确实没有图标），
     *   如果直接用 null 覆盖，已有的图标就丢失了。
     *   所以代码逻辑是：有新图标就更新，没有就保留原来的。
     */
    @Test
    @DisplayName("OVERWRITE 模式：重复 URL 更新标题和图标，updated 计数 +1")
    void shouldUpdateBookmark_whenDuplicateUrlAndOverwriteMode() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><A HREF="https://example.com" ICON="data:image/png;base64,newicon">Updated Title</A>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟数据库中已存在相同 URL 的书签
        Bookmark existingBookmark = new Bookmark();
        existingBookmark.setId(50L);
        existingBookmark.setUserId(userId);
        existingBookmark.setUrl("https://example.com");
        existingBookmark.setTitle("Original Title");
        existingBookmark.setIconUrl("data:image/png;base64,oldicon");

        when(bookmarkMapper.selectList(any())).thenReturn(List.of(existingBookmark));

        // ===== 执行：使用 OVERWRITE 模式（默认行为） =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证 =====

        // 统计数据：0 个新建，1 个更新，0 个跳过
        assertThat(response.getBookmarksCreated()).isEqualTo(0);
        assertThat(response.getBookmarksUpdated()).isEqualTo(1);
        assertThat(response.getBookmarksSkipped()).isEqualTo(0);

        // 捕获传给 updateById 的书签对象，验证标题和图标被更新
        ArgumentCaptor<Bookmark> updateCaptor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkMapper, times(1)).updateById(updateCaptor.capture());
        Bookmark updatedBookmark = updateCaptor.getValue();
        assertThat(updatedBookmark.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedBookmark.getIconUrl()).isEqualTo("data:image/png;base64,newicon");

        // 验证没有创建新书签
        verify(bookmarkMapper, never()).insert(any(Bookmark.class));
    }

    /**
     * 测试用例 6：空 HTML 文件（没有 <DL> 标签）
     *
     * 测试目标：当上传的 HTML 文件中不包含 <DL> 标签时，
     *   应抛出 BusinessException，错误码为 IMPORT_FAILED。
     *
     * 为什么要测试异常场景？
     *   用户可能上传错误的文件（比如随便选了一个 HTML 文件），
     *   系统需要优雅地处理这种情况，给出明确的错误提示，
     *   而不是抛出 NullPointerException 或其他未预期的异常。
     *
     * assertThatThrownBy 是什么？
     *   AssertJ 提供的流式异常断言 API，可以优雅地验证：
     *   - 抛出了什么类型的异常
     *   - 异常的错误码是什么
     *   比 try-catch + fail() 的写法更简洁易读。
     */
    @Test
    @DisplayName("空 HTML 文件：无 DL 标签时抛出 IMPORT_FAILED 异常")
    void shouldThrowImportFailed_whenHtmlHasNoDlTag() {
        // 这个 HTML 没有任何书签结构，只是一个普通网页
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Not a bookmark file</title></head>
                <body><p>Hello World</p></body>
                </html>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // ===== 执行并验证异常 =====
        assertThatThrownBy(() -> bookmarkImportService.importBookmarks(userId, file, null, "OVERWRITE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessEx = (BusinessException) ex;
                    assertThat(businessEx.getCode()).isEqualTo(ErrorCode.IMPORT_FAILED.getCode());
                });

        // 验证：没有执行任何数据库操作
        verify(folderMapper, never()).insert(any(Folder.class));
        verify(bookmarkMapper, never()).insert(any(Bookmark.class));
    }

    /**
     * 测试用例 7：导入到指定目标文件夹
     *
     * 测试目标：当 targetFolderId 不为 null 时，
     *   导入的文件夹和书签应创建在该目标文件夹下。
     *
     * 使用场景：
     *   用户在系统中已经创建了"浏览器书签"文件夹（id=50），
     *   希望把导入的书签都放到这个文件夹下面，而不是散落在根级别。
     *
     * 验证逻辑：
     *   1. folderMapper.selectById(targetFolderId) 返回目标文件夹（验证存在性）
     *   2. 新创建的文件夹的 parentId 应该等于 targetFolderId
     *   3. 文件夹内的书签的 folderId 应该等于新创建的文件夹 ID
     */
    @Test
    @DisplayName("导入到目标文件夹：验证 parentId 正确设置")
    void shouldCreateUnderTargetFolder_whenTargetFolderIdSpecified() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><H3>导入的文件夹</H3>
                    <DL><p>
                        <DT><A HREF="https://example.com">Example</A>
                    </DL><p>
                </DL><p>
                """;

        Long userId = 1L;
        Long targetFolderId = 50L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟目标文件夹存在且属于当前用户
        Folder targetFolder = new Folder();
        targetFolder.setId(targetFolderId);
        targetFolder.setUserId(userId);
        targetFolder.setName("浏览器书签");
        when(folderMapper.selectById(targetFolderId)).thenReturn(targetFolder);

        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupFolderMapperInsert();
        setupBookmarkMapperInsert();

        // ===== 执行 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, targetFolderId, "OVERWRITE");

        // ===== 验证 =====

        // 成功导入：1 个文件夹，1 个书签
        assertThat(response.getFoldersCreated()).isEqualTo(1);
        assertThat(response.getBookmarksCreated()).isEqualTo(1);

        // 验证新创建的文件夹的 parentId 是目标文件夹 ID
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderMapper, times(1)).insert(folderCaptor.capture());
        Folder createdFolder = folderCaptor.getValue();
        assertThat(createdFolder.getParentId()).isEqualTo(targetFolderId);
        assertThat(createdFolder.getName()).isEqualTo("导入的文件夹");
    }

    /**
     * 测试用例 7 扩展：目标文件夹不存在时抛出异常
     *
     * 测试目标：验证传入无效的 targetFolderId 时，系统正确抛出 FOLDER_NOT_FOUND 异常
     *
     * 为什么需要这个测试？
     *   这是防御性编程的体现。虽然前端通常不会传一个不存在的 ID，
     *   但 API 是公开的，任何人都可以构造 HTTP 请求。
     *   如果不校验，可能会导致数据混乱（parentId 指向不存在的文件夹）。
     */
    @Test
    @DisplayName("目标文件夹不存在：抛出 FOLDER_NOT_FOUND 异常")
    void shouldThrowFolderNotFound_whenTargetFolderNotExists() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                    <DT><A HREF="https://example.com">Example</A>
                </DL><p>
                """;

        Long userId = 1L;
        Long targetFolderId = 999L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟目标文件夹不存在（selectById 返回 null）
        when(folderMapper.selectById(targetFolderId)).thenReturn(null);

        // ===== 执行并验证异常 =====
        assertThatThrownBy(() -> bookmarkImportService.importBookmarks(
                userId, file, targetFolderId, "OVERWRITE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessEx = (BusinessException) ex;
                    assertThat(businessEx.getCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND.getCode());
                });
    }

    /**
     * 测试用例 8：同一文件夹中多个书签的 sortOrder 递增
     *
     * 测试目标：验证同一个 <DL> 下的多个书签，sortOrder 按出现顺序依次为 0, 1, 2...
     *
     * sortOrder 的设计意义：
     *   用户在浏览器中整理书签时，通常有一个顺序（比如常用的放前面）。
     *   导入时保留这个顺序，用户就不需要重新排列。
     *   sortOrder 越小排在越前面，所以第一个书签的 sortOrder = 0。
     *
     * 验证方式：
     *   使用 setupBookmarkMapperInsert(List) 的收集功能，
     *   将所有 insert 调用参数收集到列表中，然后逐一验证 sortOrder。
     */
    @Test
    @DisplayName("多个书签的 sortOrder 递增分配")
    void shouldAssignSortOrderSequentially_whenMultipleBookmarksInFolder() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><A HREF="https://first.com">First</A>
                    <DT><A HREF="https://second.com">Second</A>
                    <DT><A HREF="https://third.com">Third</A>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        List<Bookmark> insertedBookmarks = new ArrayList<>();
        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupBookmarkMapperInsert(insertedBookmarks);

        // ===== 执行 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证 =====
        assertThat(response.getBookmarksCreated()).isEqualTo(3);

        // 验证 insert 被调用了 3 次
        verify(bookmarkMapper, times(3)).insert(any(Bookmark.class));

        // 通过收集到的列表验证每次 insert 的参数
        assertThat(insertedBookmarks).hasSize(3);
        assertThat(insertedBookmarks.get(0).getTitle()).isEqualTo("First");
        assertThat(insertedBookmarks.get(0).getSortOrder()).isEqualTo(0);
        assertThat(insertedBookmarks.get(1).getTitle()).isEqualTo("Second");
        assertThat(insertedBookmarks.get(1).getSortOrder()).isEqualTo(1);
        assertThat(insertedBookmarks.get(2).getTitle()).isEqualTo("Third");
        assertThat(insertedBookmarks.get(2).getSortOrder()).isEqualTo(2);
    }

    /**
     * 测试用例 9：混合导入的统计准确性（SKIP 模式）
     *
     * 测试目标：在一次导入中混合新建文件夹、新建书签、跳过等操作，
     *   验证最终返回的统计数字完全准确。
     *
     * 为什么需要测试统计数据？
     *   统计信息是用户判断"导入是否成功"的直接依据。
     *   如果统计数字不准确（比如明明创建了 5 个书签却显示 3 个），
     *   用户会误以为导入出了问题，影响用户体验。
     *
     * 测试场景设计（SKIP 模式）：
     *   - 新建文件夹：2 个（"工具" 和 "开发"）
     *   - 新建书签：1 个（https://new-site.com）
     *   - 跳过：2 个（https://existing.com 和 https://skip-me.com）
     *   - 更新：0 个（SKIP 模式不会覆盖更新）
     */
    @Test
    @DisplayName("SKIP 模式混合导入：统计信息准确")
    void shouldReturnAccurateStatistics_whenMixedImportWithSkip() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><H3>工具</H3>
                    <DL><p>
                        <DT><A HREF="https://existing.com" ICON="data:image/png;base64,new">Updated Title</A>
                        <DT><A HREF="https://new-site.com">Brand New</A>
                    </DL><p>
                    <DT><H3>开发</H3>
                    <DL><p>
                        <DT><A HREF="https://skip-me.com">Skip This</A>
                    </DL><p>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟数据库中已有两个 URL
        Bookmark existing1 = new Bookmark();
        existing1.setId(10L);
        existing1.setUserId(userId);
        existing1.setUrl("https://existing.com");
        existing1.setTitle("Old Title");
        existing1.setIconUrl("old-icon");

        Bookmark existing2 = new Bookmark();
        existing2.setId(11L);
        existing2.setUserId(userId);
        existing2.setUrl("https://skip-me.com");
        existing2.setTitle("Original");
        existing2.setIconUrl("original-icon");

        when(bookmarkMapper.selectList(any())).thenReturn(List.of(existing1, existing2));
        setupFolderMapperInsert();
        setupBookmarkMapperInsert();

        // ===== 执行：使用 SKIP 模式 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "SKIP");

        // ===== 验证统计 =====
        assertThat(response.getFoldersCreated()).isEqualTo(2);    // 工具、开发
        assertThat(response.getBookmarksCreated()).isEqualTo(1);  // new-site.com
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);  // SKIP 模式不更新
        assertThat(response.getBookmarksSkipped()).isEqualTo(2);  // existing.com, skip-me.com
    }

    /**
     * 测试用例 9 扩展：OVERWRITE 模式下混合导入的统计准确性
     *
     * 与上面的 SKIP 模式对比，验证两种模式在同一 HTML 输入下产生不同的统计结果。
     * 这有助于确保 duplicateMode 参数确实影响了导入行为。
     *
     * 测试场景设计（OVERWRITE 模式）：
     *   - 新建文件夹：2 个（"工具" 和 "开发"）
     *   - 新建书签：1 个（https://new-site.com）
     *   - 更新：2 个（existing.com 和 skip-me.com 被覆盖更新）
     *   - 跳过：0 个（OVERWRITE 模式不跳过）
     */
    @Test
    @DisplayName("OVERWRITE 模式混合导入：统计信息准确")
    void shouldReturnAccurateStatistics_whenMixedImportWithOverwrite() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><H3>工具</H3>
                    <DL><p>
                        <DT><A HREF="https://existing.com" ICON="data:image/png;base64,new">Updated Title</A>
                        <DT><A HREF="https://new-site.com">Brand New</A>
                    </DL><p>
                    <DT><H3>开发</H3>
                    <DL><p>
                        <DT><A HREF="https://skip-me.com">Skip This</A>
                    </DL><p>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 模拟数据库中已有两个 URL
        Bookmark existing1 = new Bookmark();
        existing1.setId(10L);
        existing1.setUserId(userId);
        existing1.setUrl("https://existing.com");
        existing1.setTitle("Old Title");
        existing1.setIconUrl("old-icon");

        Bookmark existing2 = new Bookmark();
        existing2.setId(11L);
        existing2.setUserId(userId);
        existing2.setUrl("https://skip-me.com");
        existing2.setTitle("Original");
        existing2.setIconUrl("original-icon");

        when(bookmarkMapper.selectList(any())).thenReturn(List.of(existing1, existing2));
        setupFolderMapperInsert();
        setupBookmarkMapperInsert();

        // ===== 执行：使用 OVERWRITE 模式 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证统计 =====
        assertThat(response.getFoldersCreated()).isEqualTo(2);    // 工具、开发
        assertThat(response.getBookmarksCreated()).isEqualTo(1);  // new-site.com
        assertThat(response.getBookmarksUpdated()).isEqualTo(2);  // existing.com, skip-me.com
        assertThat(response.getBookmarksSkipped()).isEqualTo(0);  // OVERWRITE 模式不跳过
    }

    /**
     * 测试用例 10：同一批次内重复 URL 只导入一次
     *
     * 测试目标：当 HTML 文件中同一个 URL 出现多次时，
     *   第一次遇到时创建新书签，后续遇到时 URL 已在 existingUrls 集合中
     *   （由代码在创建后 add 进去），但不在 existingByUrl 中（非数据库已有），
     *   所以第二次会被当作"覆盖"处理，但 existingByUrl.get(url) 返回 null，
     *   实际上不做任何操作（不会 insert 也不会 update）。
     *
     * 为什么同一文件中会有重复 URL？
     *   用户可能在不同文件夹中收藏了同一个网站。
     *   Chrome 导出时会保留所有副本。
     *   导入时需要正确处理这种情况，不能创建重复的书签。
     */
    @Test
    @DisplayName("同一批次内重复 URL：第一次创建，后续不重复创建")
    void shouldHandleDuplicateUrlWithinSameBatch() {
        String html = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>
                    <DT><A HREF="https://example.com">First Occurrence</A>
                    <DT><A HREF="https://example.com">Second Occurrence</A>
                </DL><p>
                """;

        Long userId = 1L;
        MultipartFile file = createBookmarkHtml(html);

        // 数据库中没有已有书签
        when(bookmarkMapper.selectList(any())).thenReturn(Collections.emptyList());
        setupBookmarkMapperInsert();

        // ===== 执行 =====
        BookmarkImportResponse response = bookmarkImportService.importBookmarks(
                userId, file, null, "OVERWRITE");

        // ===== 验证 =====
        // 第一次遇到时创建（bookmarksCreated=1），URL 加入 existingUrls。
        // 第二次遇到时 existingUrls.contains(url)=true，进入重复处理分支，
        // OVERWRITE 模式但 existingByUrl.get(url)=null（因为是本批次新建的，不是数据库已有的），
        // 所以 existing==null，不会执行 updateById，也不会增加任何计数。
        assertThat(response.getBookmarksCreated()).isEqualTo(1);
        // 第二个重复 URL 没有被计入 updated（因为 existingByUrl 中没有对应实体）
        assertThat(response.getBookmarksUpdated()).isEqualTo(0);
        // insert 只被调用 1 次（第一次）
        verify(bookmarkMapper, times(1)).insert(any(Bookmark.class));
    }
}
