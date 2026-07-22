package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.ExportBookmarkNode;
import com.hlaia.dto.response.ExportDataResponse;
import com.hlaia.dto.response.ExportFolderNode;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【书签导出服务类】—— 把当前用户名下的全部书签导出为可视化 HTML 数据
 *
 * 什么是书签导出？
 *   与 {@link BookmarkImportService} 互为逆操作：
 *   - 导入：读取 Chrome 导出的 Netscape HTML，解析后写入数据库（仍由 BookmarkImportService 负责）
 *   - 导出：读取数据库中的书签树，组装成 ExportDataResponse（JSON），由前端渲染成可视化 HTML
 *
 *   注意：本类不再产出 Netscape HTML 字符串。HTML 的拼装挪到了前端纯函数 renderExportHtml(data)，
 *   这样改样式零后端重启，预览页和生产下载共用同一套模板（见 design.md §1）。
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
     * 导出当前用户的全部书签为可视化 HTML 数据（ExportDataResponse）
     *
     * 完整流程：
     *   1. 两次查询加载该用户的全部文件夹 + 全部书签（与 FolderService.getFolderTree 相同的"全量加载"模式）
     *   2. 书签按 folderId 分组，子文件夹按 parentId 分组
     *   3. 从根文件夹（parentId == null）开始，递归组装 ExportFolderNode（含嵌套 children + 直属 bookmarks）
     *   4. 包上 exportedAt 时间戳返回
     *
     * 为什么不在 SQL 里用 join/递归 CTE 一次性查？
     *   MyBatis-Plus 的 LambdaQueryWrapper 不擅长递归层级；两次全量查询 + 内存分组 + 递归组装，
     *   逻辑清晰、O(n) 复杂度，千级书签无压力。与 FolderService.getFolderTree 完全一致的成熟模式。
     *
     * 为什么在 Service 层用 try-catch 包裹？
     *   按错误处理规范：Service 层抛 BusinessException，Controller 不 try-catch。
     *   这里把任何异常（数据库、NPE 等）统一转成 EXPORT_FAILED，对外不泄露内部细节。
     *
     * @param userId 当前登录用户 ID（数据隔离：只能导出自己的书签）
     * @return ExportDataResponse（根文件夹树 + 直属书签详情 + 导出时间）
     */
    public ExportDataResponse getExportData(Long userId) {
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
            //   组装某个文件夹时，需要快速取出"直属该文件夹的书签"和"直属子文件夹"。
            //   预先分组成 Map，递归时 O(1) 查找，避免每个文件夹都遍历全部数据。
            Map<Long, List<Bookmark>> bookmarksByFolder = allBookmarks.stream()
                    .collect(Collectors.groupingBy(Bookmark::getFolderId));

            // 子文件夹按 parentId 分组（parentId 非 null 的才有父）
            Map<Long, List<Folder>> foldersByParent = allFolders.stream()
                    .filter(f -> f.getParentId() != null)
                    .collect(Collectors.groupingBy(Folder::getParentId));

            // ============ 第四步：从根文件夹递归组装 ExportFolderNode 列表 ============
            // 根文件夹 = parentId 为 null 的文件夹（与 FolderService.getFolderTree 识别根的方式一致）
            List<ExportFolderNode> rootNodes = new ArrayList<>();
            for (Folder folder : allFolders) {
                if (folder.getParentId() == null) {
                    rootNodes.add(buildFolderNode(folder, bookmarksByFolder, foldersByParent));
                }
            }

            // ============ 第五步：组装顶层响应 + 导出时间戳 ============
            ExportDataResponse response = new ExportDataResponse();
            // ISO 字符串（如 "2026-07-22T15:30:00"），前端直接放页头展示
            response.setExportedAt(LocalDateTime.now()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.setFolders(rootNodes);

            log.info("Exported {} folders and {} bookmarks for user {}",
                    allFolders.size(), allBookmarks.size(), userId);
            return response;
        } catch (Exception e) {
            // 任何异常都转为业务异常 EXPORT_FAILED
            // 按错误处理规范：Service 层抛 BusinessException，Controller 不 try-catch
            log.error("Failed to export bookmarks for user {}", userId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    /**
     * 递归组装一个文件夹为 ExportFolderNode（含直属书签 + 嵌套子文件夹）
     *
     * 组装顺序：先填本文件夹字段 → 再挂直属书签 → 最后递归挂子文件夹。
     * 顺序不影响 JSON 序列化结果，但保持一种固定约定便于阅读。
     *
     * 为什么 children / bookmarks 用空列表而不是 null？
     *   空列表让前端遍历时不用判空，与 FolderTreeResponse.children 的约定一致。
     *
     * @param folder           当前要组装的文件夹
     * @param bookmarksByFolder 书签按 folderId 分组的映射
     * @param foldersByParent   子文件夹按 parentId 分组的映射
     * @return 组装好的 ExportFolderNode（含 children + bookmarks）
     */
    private ExportFolderNode buildFolderNode(Folder folder,
                                             Map<Long, List<Bookmark>> bookmarksByFolder,
                                             Map<Long, List<Folder>> foldersByParent) {
        ExportFolderNode node = new ExportFolderNode();
        node.setId(folder.getId());
        node.setName(folder.getName());
        node.setIcon(folder.getIcon());
        node.setSortOrder(folder.getSortOrder());

        // 1. 该文件夹直属的书签（folderId == 该文件夹 id）
        List<Bookmark> folderBookmarks = bookmarksByFolder.getOrDefault(folder.getId(), new ArrayList<>());
        List<ExportBookmarkNode> bookmarkNodes = new ArrayList<>(folderBookmarks.size());
        for (Bookmark bm : folderBookmarks) {
            bookmarkNodes.add(buildBookmarkNode(bm));
        }
        node.setBookmarks(bookmarkNodes);

        // 2. 递归组装子文件夹
        List<Folder> childFolders = foldersByParent.getOrDefault(folder.getId(), new ArrayList<>());
        List<ExportFolderNode> childNodes = new ArrayList<>(childFolders.size());
        for (Folder child : childFolders) {
            childNodes.add(buildFolderNode(child, bookmarksByFolder, foldersByParent));
        }
        node.setChildren(childNodes);

        return node;
    }

    /**
     * 把单个 Bookmark 实体转成精简的 ExportBookmarkNode
     *
     * 只搬运可视化 HTML 需要的字段（title/url/iconUrl/description/sortOrder），
     * 不暴露 id / folderId / createdAt 等导出场景用不到的字段。
     */
    private ExportBookmarkNode buildBookmarkNode(Bookmark bookmark) {
        ExportBookmarkNode node = new ExportBookmarkNode();
        node.setTitle(bookmark.getTitle());
        node.setUrl(bookmark.getUrl());
        node.setIconUrl(bookmark.getIconUrl());
        node.setDescription(bookmark.getDescription());
        node.setSortOrder(bookmark.getSortOrder());
        return node;
    }
}
