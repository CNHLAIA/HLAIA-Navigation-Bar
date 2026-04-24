# Favicon 获取改进 + 书签移动功能 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改进后端 favicon 获取成功率（HTML 解析），并实现书签移动到其他文件夹功能（后端 API + 前端右键菜单 + 文件夹选择器弹窗）。

**Architecture:** 两个独立功能。功能一：用 Jsoup 解析 HTML `<link>` 标签获取 favicon，回退到 `/favicon.ico`。功能二：新增 `PUT /api/bookmarks/move` 端点，前端右键菜单 + FolderPickerDialog 组件。

**Tech Stack:** Java 17, Spring Boot 4, Jsoup 1.18.3, MyBatis-Plus, Vue 3, Element Plus, Pinia

---

## File Structure

### 新增文件
- `src/main/java/com/hlaia/dto/request/BookmarkMoveRequest.java` — 书签移动请求 DTO
- `frontend/src/components/FolderPickerDialog.vue` — 文件夹选择器弹窗组件

### 修改文件
- `pom.xml` — 新增 jsoup 依赖
- `src/main/java/com/hlaia/kafka/IconFetchConsumer.java` — 重写 favicon 获取逻辑
- `src/main/java/com/hlaia/controller/BookmarkController.java` — 新增 moveBookmarks 端点
- `src/main/java/com/hlaia/service/BookmarkService.java` — 新增 moveBookmarks 方法
- `frontend/src/api/bookmark.js` — 新增 moveBookmarks API 函数
- `frontend/src/stores/bookmark.js` — 新增 moveBookmarks action
- `frontend/src/components/BookmarkGrid.vue` — 右键菜单增加"移动到..."选项，引入 FolderPickerDialog
- `frontend/src/i18n/zh-CN.js` — 新增移动相关翻译
- `frontend/src/i18n/en-US.js` — 新增移动相关翻译

---

### Task 1: 添加 Jsoup 依赖到 pom.xml

**Files:**
- Modify: `pom.xml:36` (properties section)
- Modify: `pom.xml:128` (after Lombok dependency)

- [ ] **Step 1: 添加 jsoup 版本属性和依赖**

在 `pom.xml` 的 `<properties>` 中新增 jsoup 版本：

```xml
<jsoup.version>1.18.3</jsoup.version>
```

在 `</dependency>` (Lombok 依赖之后) 添加 jsoup 依赖：

```xml
<!-- HTML 解析库 —— 用于从网页 HTML 中解析 favicon 链接 -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>${jsoup.version}</version>
</dependency>
```

- [ ] **Step 2: 验证依赖下载**

Run: `cd "E:/Hello World/JAVA/HLAIANavigationBar" && ./mvnw dependency:resolve -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: add jsoup dependency for favicon HTML parsing"
```

---

### Task 2: 改进 IconFetchConsumer 的 favicon 获取逻辑

**Files:**
- Modify: `src/main/java/com/hlaia/kafka/IconFetchConsumer.java`

- [ ] **Step 1: 重写 IconFetchConsumer**

将 `fetchFavicon` 方法替换为新的多策略获取逻辑，保留原有的中文学习注释风格。完整替换 `IconFetchConsumer.java`：

```java
package com.hlaia.kafka;

import com.hlaia.entity.Bookmark;
import com.hlaia.mapper.BookmarkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 【Kafka 消费者 —— 异步获取网站图标（favicon）】
 *
 * 改进版：通过解析 HTML 中的 <link> 标签获取 favicon，大幅提高成功率。
 * 获取优先级：
 *   1. HTML 中 <link rel="icon"> / <link rel="shortcut icon"> 声明的图标
 *   2. <link rel="apple-touch-icon"> 声明的图标（兜底）
 *   3. 默认路径 domain/favicon.ico
 *   4. 以上均失败 → iconUrl 保持 null，前端用 Google Favicon 服务兜底
 *
 * Jsoup 简介：
 *   Jsoup 是一个 Java HTML 解析库，可以像 jQuery 一样用 CSS 选择器查找元素。
 *   这里用 Jsoup.connect(url).get() 获取网页 HTML 并解析为 Document 对象，
 *   然后用 select("link[rel~=(?i)icon]") 查找所有 favicon 声明标签。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IconFetchConsumer {

    private final BookmarkMapper bookmarkMapper;
    private final JsonMapper jsonMapper;

    /**
     * 共享的 HttpClient 实例（避免每次请求都创建新实例）
     * 设置 5 秒连接超时和自动跟随重定向
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @KafkaListener(topics = "bookmark-icon-fetch", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = jsonMapper.readTree(message);
            Long bookmarkId = node.get("bookmarkId").asLong();
            String url = node.get("url").asText();

            String iconUrl = fetchFavicon(url);

            if (iconUrl != null) {
                Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
                if (bookmark != null) {
                    bookmark.setIconUrl(iconUrl);
                    bookmarkMapper.updateById(bookmark);
                    log.info("Updated icon for bookmark {}: {}", bookmarkId, iconUrl);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process icon fetch: {}", e.getMessage());
        }
    }

    /**
     * 多策略获取 favicon
     *
     * 策略流程：
     *   1. 用 Jsoup 获取页面 HTML
     *   2. 解析 <link> 标签中的 icon 声明（按 rel 属性优先级）
     *   3. 将 relative URL 转为 absolute URL
     *   4. 若 HTML 中未找到，回退到 /favicon.ico
     *
     * @param pageUrl 页面 URL
     * @return favicon 的 absolute URL，失败返回 null
     */
    private String fetchFavicon(String pageUrl) {
        try {
            URI uri = new URI(pageUrl);
            String domain = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() > 0 ? ":" + uri.getPort() : "");

            // ---- 策略1：解析 HTML 中的 <link> 标签 ----
            String htmlIcon = fetchFromHtml(pageUrl, domain);
            if (htmlIcon != null) return htmlIcon;

            // ---- 策略2：尝试默认 /favicon.ico ----
            String defaultIcon = checkUrl(domain + "/favicon.ico");
            if (defaultIcon != null) return defaultIcon;

        } catch (Exception e) {
            log.debug("Could not fetch favicon for {}: {}", pageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 从 HTML 中解析 favicon URL
     *
     * 使用 Jsoup 获取网页并查找 <link rel="icon"> 等标签。
     * 按 rel 属性优先级查找：
     *   1. rel 包含 "icon"（包括 "icon", "shortcut icon"）
     *   2. rel 包含 "apple-touch-icon"（兜底，通常是大尺寸图标）
     *
     * @param pageUrl 页面完整 URL
     * @param domain  协议 + 域名（用于拼接 relative URL）
     * @return absolute favicon URL，未找到返回 null
     */
    private String fetchFromHtml(String pageUrl, String domain) {
        try {
            // Jsoup.connect() 会发送 GET 请求并解析返回的 HTML
            // .userAgent() 设置浏览器标识，避免被某些网站拒绝
            // .timeout() 设置超时时间
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (compatible; HlaiaNav/1.0)")
                    .timeout(5000)
                    .get();

            // 按优先级查找 favicon 声明
            // CSS 选择器 "link[rel~=(?i)icon]" 含义：
            //   link           → 找 <link> 标签
            //   [rel~=...]     → rel 属性匹配正则（~=(?i) 表示大小写不敏感的 contains）
            //   (?i)icon       → 匹配 "icon", "Icon", "shortcut icon" 等
            //   但排除 "apple-touch-icon"（优先级较低，后面单独处理）
            for (Element link : doc.select("link[rel~=(?i)^(?!apple-touch).*$]")) {
                String rel = link.attr("rel").toLowerCase();
                if (rel.contains("icon")) {
                    String href = link.attr("abs:href");
                    if (!href.isEmpty()) {
                        // 验证 URL 可访问
                        String verified = checkUrl(href);
                        if (verified != null) return verified;
                    }
                }
            }

            // 兜底：apple-touch-icon（通常是苹果设备图标，尺寸较大但也可用）
            Element appleIcon = doc.selectFirst("link[rel~=(?i)apple-touch-icon]");
            if (appleIcon != null) {
                String href = appleIcon.attr("abs:href");
                if (!href.isEmpty()) {
                    return checkUrl(href);
                }
            }
        } catch (Exception e) {
            log.debug("HTML parsing failed for {}: {}", pageUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 验证 URL 是否可访问（HEAD 请求返回 200）
     */
    private String checkUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return url;
            }
        } catch (Exception e) {
            log.debug("URL check failed for {}: {}", url, e.getMessage());
        }
        return null;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd "E:/Hello World/JAVA/HLAIANavigationBar" && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hlaia/kafka/IconFetchConsumer.java
git commit -m "feat: improve favicon fetch with HTML parsing using Jsoup"
```

---

### Task 3: 新增 BookmarkMoveRequest DTO

**Files:**
- Create: `src/main/java/com/hlaia/dto/request/BookmarkMoveRequest.java`

- [ ] **Step 1: 创建 BookmarkMoveRequest.java**

```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 【书签移动请求 DTO】—— 将一个或多个书签移动到目标文件夹时提交的数据
 *
 * 支持批量移动：前端可以一次传递多个书签 ID，后端统一验证权限后批量更新 folderId。
 *
 * @NotEmpty vs @NotNull：
 *   bookmarkIds 用 @NotEmpty 确保：不能为 null 且不能为空列表 []
 *   targetFolderId 用 @NotNull 确保：必须选择一个目标文件夹
 */
@Data
public class BookmarkMoveRequest {

    /**
     * 要移动的书签 ID 列表（必填，至少包含一个）
     */
    @NotEmpty(message = "书签列表不能为空")
    private List<Long> bookmarkIds;

    /**
     * 目标文件夹 ID（必填）
     */
    @NotNull(message = "目标文件夹不能为空")
    private Long targetFolderId;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/hlaia/dto/request/BookmarkMoveRequest.java
git commit -m "feat: add BookmarkMoveRequest DTO"
```

---

### Task 4: 后端 BookmarkService 新增 moveBookmarks 方法

**Files:**
- Modify: `src/main/java/com/hlaia/service/BookmarkService.java`

- [ ] **Step 1: 在 BookmarkService 中添加 FolderMapper 依赖和 moveBookmarks 方法**

首先在类的依赖字段中（`private final KafkaProducer kafkaProducer;` 之后）添加：

```java
private final com.hlaia.mapper.FolderMapper folderMapper;
```

然后在 `batchCopyLinks` 方法之后、`getBookmarkForUser` 方法之前，新增 `moveBookmarks` 方法：

```java
    /**
     * 批量移动书签到目标文件夹
     *
     * 移动流程：
     *   1. 验证所有书签属于当前用户
     *   2. 验证目标文件夹存在且属于当前用户
     *   3. 计算目标文件夹当前最大 sortOrder
     *   4. 批量更新 folderId 和 sortOrder（追加到末尾）
     *
     * @Transactional：保证所有更新操作的原子性
     *
     * @param userId  当前登录用户的 ID
     * @param request 移动请求（包含 bookmarkIds 和 targetFolderId）
     */
    @Transactional
    public void moveBookmarks(Long userId, BookmarkMoveRequest request) {
        // 第一步：验证所有书签属于当前用户
        // 同时收集到实体列表，避免后续重复查询
        java.util.List<Bookmark> bookmarks = new java.util.ArrayList<>();
        for (Long id : request.getBookmarkIds()) {
            bookmarks.add(getBookmarkForUser(userId, id));
        }

        // 第二步：验证目标文件夹存在且属于当前用户
        com.hlaia.entity.Folder targetFolder = folderMapper.selectById(request.getTargetFolderId());
        if (targetFolder == null || !targetFolder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
        }

        // 第三步：计算目标文件夹当前最大 sortOrder
        // 使用 SQL 查询：SELECT MAX(sort_order) FROM bookmark WHERE folder_id = ?
        // 如果目标文件夹没有书签，从 0 开始
        Bookmark maxSortBookmark = bookmarkMapper.selectOne(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getTargetFolderId())
                        .orderByDesc(Bookmark::getSortOrder)
                        .last("LIMIT 1"));
        int nextSortOrder = (maxSortBookmark != null) ? maxSortBookmark.getSortOrder() + 1 : 0;

        // 第四步：批量更新 folderId 和 sortOrder
        for (Bookmark bookmark : bookmarks) {
            bookmark.setFolderId(request.getTargetFolderId());
            bookmark.setSortOrder(nextSortOrder++);
            bookmarkMapper.updateById(bookmark);
        }
    }
```

同时在文件顶部 import 区域确认已有（若无则添加）：

```java
import com.hlaia.entity.Folder;
```

- [ ] **Step 2: 编译验证**

Run: `cd "E:/Hello World/JAVA/HLAIANavigationBar" && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hlaia/service/BookmarkService.java
git commit -m "feat: add moveBookmarks method to BookmarkService"
```

---

### Task 5: 后端 BookmarkController 新增移动端点

**Files:**
- Modify: `src/main/java/com/hlaia/controller/BookmarkController.java`

- [ ] **Step 1: 在 BookmarkController 中添加 moveBookmarks 端点**

在 `batchCopy` 方法（`}` 之前）添加新端点：

```java
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
```

- [ ] **Step 2: 编译验证**

Run: `cd "E:/Hello World/JAVA/HLAIANavigationBar" && ./mvnw compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hlaia/controller/BookmarkController.java
git commit -m "feat: add PUT /api/bookmarks/move endpoint"
```

---

### Task 6: 前端 API 层新增 moveBookmarks 函数

**Files:**
- Modify: `frontend/src/api/bookmark.js`

- [ ] **Step 1: 在 bookmark.js 末尾添加 moveBookmarks 函数**

在 `batchCopyLinks` 函数之后添加：

```javascript
/**
 * 批量移动书签到目标文件夹
 * @param {Array} bookmarkIds - 书签 ID 数组
 * @param {number} targetFolderId - 目标文件夹 ID
 * @returns {Promise}
 */
export function moveBookmarks(bookmarkIds, targetFolderId) {
  return request.put('/bookmarks/move', { bookmarkIds, targetFolderId })
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/bookmark.js
git commit -m "feat: add moveBookmarks API function"
```

---

### Task 7: 前端 Bookmark Store 新增 moveBookmarks action

**Files:**
- Modify: `frontend/src/stores/bookmark.js`

- [ ] **Step 1: 添加 moveBookmarks import 和 action**

在 import 区域添加 `moveBookmarks` 到导入列表：

```javascript
import {
  getBookmarks as getBookmarksApi,
  createBookmark as createBookmarkApi,
  updateBookmark as updateBookmarkApi,
  deleteBookmark as deleteBookmarkApi,
  sortBookmarks as sortBookmarksApi,
  batchDeleteBookmarks as batchDeleteApi,
  batchCopyLinks as batchCopyApi,
  moveBookmarks as moveBookmarksApi
} from '@/api/bookmark'
```

同时在 store 中导入 folder store（用于刷新树）：

```javascript
import { useFolderStore } from './folder'
```

在 `clearSelection` 函数之后、`return` 之前添加：

```javascript
  /**
   * 批量移动书签到目标文件夹
   * 移动后刷新当前书签列表和文件夹树（bookmarkCount 变化）
   * @param {Array} bookmarkIds - 书签 ID 数组
   * @param {number} targetFolderId - 目标文件夹 ID
   */
  async function moveBookmarks(bookmarkIds, targetFolderId) {
    await moveBookmarksApi(bookmarkIds, targetFolderId)
    // 刷新当前文件夹的书签列表
    if (currentFolderId.value) {
      await fetchBookmarks(currentFolderId.value)
    }
    // 刷新文件夹树（bookmarkCount 变化）
    const folderStore = useFolderStore()
    await folderStore.fetchTree()
  }
```

在 `return` 对象中添加 `moveBookmarks`：

```javascript
    moveBookmarks,
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/stores/bookmark.js
git commit -m "feat: add moveBookmarks action to bookmark store"
```

---

### Task 8: 新增 FolderPickerDialog 组件

**Files:**
- Create: `frontend/src/components/FolderPickerDialog.vue`

- [ ] **Step 1: 创建 FolderPickerDialog.vue**

这个组件参考 StagingView.vue 中已有的文件夹选择器模式（扁平化树 + 点击选中），封装为独立弹窗组件。

```vue
<!--
  FolderPickerDialog.vue — 文件夹选择器弹窗

  功能：
  - 展示用户文件夹树（扁平化列表，缩进显示层级）
  - 单击选中目标文件夹
  - 排除指定文件夹（如当前文件夹，防止无意义移动）
  - 点击确认触发 @confirm 事件

  设计：与 StagingView 的文件夹选择器一致的风格
-->
<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="400px"
    :append-to-body="true"
    class="bookmark-dialog"
    @close="$emit('update:visible', false)"
  >
    <p class="move-dialog-hint">{{ t('bookmarks.moveDialog.description') }}</p>
    <div v-if="flatFolders.length > 0" class="folder-picker">
      <div
        v-for="node in flatFolders"
        :key="node.id"
        class="folder-picker-item"
        :class="{ 'is-selected': selectedId === node.id }"
        :style="{ paddingLeft: `${12 + node.depth * 20}px` }"
        @click="selectedId = node.id"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>{{ node.name }}</span>
      </div>
    </div>
    <div v-else class="folder-picker-empty">
      <p>{{ t('bookmarks.moveDialog.empty') }}</p>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :disabled="!selectedId"
        @click="handleConfirm"
      >
        {{ t('bookmarks.moveDialog.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'

const { t } = useI18n()
const folderStore = useFolderStore()

const props = defineProps({
  /** 弹窗是否可见（支持 v-model:visible） */
  visible: Boolean,
  /** 弹窗标题 */
  title: {
    type: String,
    default: ''
  },
  /** 要排除的文件夹 ID（通常是当前文件夹） */
  excludeFolderId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

/** 当前选中的文件夹 ID */
const selectedId = ref(null)

/** 扁平化的文件夹列表（过滤掉 excludeFolderId） */
const flatFolders = computed(() => {
  return flattenTree(folderStore.folderTree).filter(n => n.id !== props.excludeFolderId)
})

/** 弹窗打开时重置选中状态 */
watch(() => props.visible, (val) => {
  if (val) selectedId.value = null
})

function handleConfirm() {
  if (selectedId.value) {
    emit('confirm', selectedId.value)
  }
}

/**
 * 将嵌套的文件夹树扁平化为一维数组
 * @param {Array} nodes - 树节点数组
 * @param {number} depth - 当前深度
 * @returns {Array} - [{ id, name, depth }]
 */
function flattenTree(nodes, depth = 0) {
  const result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name, depth })
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}
</script>

<style scoped>
.move-dialog-hint {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  margin: 0 0 12px;
}

.folder-picker {
  max-height: 300px;
  overflow-y: auto;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
}

.folder-picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--hlaia-text-muted);
  border-bottom: 1px solid var(--hlaia-border);
}

.folder-picker-item:last-child {
  border-bottom: none;
}

.folder-picker-item:hover {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text);
}

.folder-picker-item.is-selected {
  background: rgba(74, 127, 199, 0.08);
  color: var(--hlaia-primary);
}

.folder-picker-item span {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.folder-picker-empty {
  padding: 24px;
  text-align: center;
}

.folder-picker-empty p {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/FolderPickerDialog.vue
git commit -m "feat: add FolderPickerDialog component"
```

---

### Task 9: 修改 BookmarkGrid 右键菜单增加"移动到..."选项

**Files:**
- Modify: `frontend/src/components/BookmarkGrid.vue`

- [ ] **Step 1: 添加 FolderPickerDialog 导入和状态**

在 `<script setup>` 的 import 区域添加：

```javascript
import FolderPickerDialog from './FolderPickerDialog.vue'
import { useFolderStore } from '@/stores/folder'
```

在 `const bookmarkStore = useBookmarkStore()` 之后添加：

```javascript
const folderStore = useFolderStore()
```

在对话框状态区域之后（`dialogForm` 之后）添加移动弹窗状态：

```javascript
// ---- 移动弹窗状态 ----
const moveDialogVisible = ref(false)
const moveBookmarkIds = ref([])
```

- [ ] **Step 2: 修改右键菜单模板，添加"移动到..."选项**

在右键菜单的 `</teleport>` 之前，在"编辑"按钮和"删除"按钮之间添加"移动到..."按钮。找到现有的删除按钮：

```html
        <button class="ctx-item ctx-item-danger" @click="handleContextDelete">
```

在其前面添加：

```html
        <button class="ctx-item" @click="handleMoveToFolder">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1v12M4 4l3-3 3 3M1 9v3a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V9" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ bookmarkStore.hasSelection ? t('bookmarks.contextMenu.moveSelectedTo') : t('bookmarks.contextMenu.moveTo') }}
        </button>
```

- [ ] **Step 3: 在模板最后（`</div>` 关闭 wrapper 之前）添加 FolderPickerDialog 组件**

在编辑对话框 `</el-dialog>` 之后、`</div>` (关闭 `.bookmark-grid-wrapper`) 之前添加：

```html
    <!-- 移动到文件夹弹窗 -->
    <FolderPickerDialog
      v-model:visible="moveDialogVisible"
      :title="t('bookmarks.moveDialog.title')"
      :exclude-folder-id="folderId"
      @confirm="handleMoveConfirm"
    />
```

- [ ] **Step 4: 添加移动相关的事件处理函数**

在 `handleContextDelete` 函数之后添加：

```javascript
/**
 * 打开移动到文件夹弹窗
 * 单个移动：右键菜单的书签
 * 批量移动：所有已选中的书签
 */
function handleMoveToFolder() {
  const bm = contextMenu.value.bookmark
  if (!bm) return
  contextMenu.value.visible = false

  if (bookmarkStore.hasSelection) {
    // 多选模式：移动所有选中的书签
    moveBookmarkIds.value = [...bookmarkStore.selectedIds]
  } else {
    // 单个移动
    moveBookmarkIds.value = [bm.id]
  }
  moveDialogVisible.value = true
}

/**
 * 确认移动书签到目标文件夹
 */
async function handleMoveConfirm(targetFolderId) {
  try {
    await bookmarkStore.moveBookmarks(moveBookmarkIds.value, targetFolderId)
    moveDialogVisible.value = false
    bookmarkStore.clearSelection()
    ElMessage.success(
      moveBookmarkIds.value.length > 1
        ? t('bookmarks.toast.movedBatch', { count: moveBookmarkIds.value.length })
        : t('bookmarks.toast.moved')
    )
  } catch {
    ElMessage.error(t('bookmarks.toast.moveFailed'))
  }
}
```

- [ ] **Step 5: 验证前端编译**

Run: `cd "E:/Hello World/JAVA/HLAIANavigationBar/frontend" && npx vite build 2>&1 | tail -10`
Expected: 构建成功，无错误

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/BookmarkGrid.vue
git commit -m "feat: add move-to-folder option in bookmark context menu"
```

---

### Task 10: 添加 i18n 翻译

**Files:**
- Modify: `frontend/src/i18n/zh-CN.js`
- Modify: `frontend/src/i18n/en-US.js`

- [ ] **Step 1: 更新 zh-CN.js**

在 `bookmarks.contextMenu` 对象中（`delete: '删除'` 之后）添加：

```javascript
      moveTo: '移动到...',
      moveSelectedTo: '移动选中到...'
```

在 `bookmarks.toast` 对象中（`orderFailed: '排序更新失败'` 之后）添加：

```javascript
      moved: '书签已移动',
      movedBatch: '已移动 {count} 个书签',
      moveFailed: '移动失败'
```

在 `bookmarks` 对象中（`batch` 之后）添加 `moveDialog` 节点：

```javascript
    moveDialog: {
      title: '移动到文件夹',
      description: '选择目标文件夹：',
      confirm: '移动',
      empty: '暂无可用文件夹'
    }
```

- [ ] **Step 2: 更新 en-US.js**

在 `bookmarks.contextMenu` 对象中（`delete: 'Delete'` 之后）添加：

```javascript
      moveTo: 'Move to...',
      moveSelectedTo: 'Move selected to...'
```

在 `bookmarks.toast` 对象中（`orderFailed: 'Failed to update bookmark order'` 之后）添加：

```javascript
      moved: 'Bookmark moved',
      movedBatch: 'Moved {count} bookmark(s)',
      moveFailed: 'Failed to move bookmark(s)'
```

在 `bookmarks` 对象中（`batch` 之后）添加 `moveDialog` 节点：

```javascript
    moveDialog: {
      title: 'Move to Folder',
      description: 'Select a target folder:',
      confirm: 'Move',
      empty: 'No folders available'
    }
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/i18n/zh-CN.js frontend/src/i18n/en-US.js
git commit -m "feat: add i18n translations for bookmark move feature"
```

---

## Self-Review Checklist

### Spec Coverage
- [x] Favicon HTML 解析 — Task 1 + Task 2
- [x] 回退 /favicon.ico — Task 2
- [x] 前端 Google Favicon 兜底 — 无需改动（已存在）
- [x] BookmarkMoveRequest DTO — Task 3
- [x] BookmarkService.moveBookmarks — Task 4
- [x] PUT /api/bookmarks/move 端点 — Task 5
- [x] 前端 API 函数 — Task 6
- [x] Bookmark Store action — Task 7
- [x] FolderPickerDialog 组件 — Task 8
- [x] 右键菜单"移动到..." — Task 9
- [x] 单个移动 + 批量移动 — Task 9
- [x] i18n 翻译 — Task 10

### Placeholder Scan
- 无 TBD、TODO、或 placeholder 代码

### Type Consistency
- `BookmarkMoveRequest.bookmarkIds` (List<Long>) → `moveBookmarks(Long userId, BookmarkMoveRequest request)` → `request.getBookmarkIds()` ✓
- `BookmarkMoveRequest.targetFolderId` (Long) → `request.getTargetFolderId()` ✓
- Frontend API: `moveBookmarks(bookmarkIds, targetFolderId)` → store: `moveBookmarks(bookmarkIds, targetFolderId)` → 组件: `bookmarkStore.moveBookmarks(moveBookmarkIds.value, targetFolderId)` ✓
- i18n keys: `bookmarks.contextMenu.moveTo` / `bookmarks.moveDialog.title` 等在 zh-CN 和 en-US 中完全匹配 ✓
