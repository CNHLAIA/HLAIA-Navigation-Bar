# 浏览器收藏夹导入功能

## Goal

支持用户将 Chrome 浏览器导出的书签 HTML 文件上传到导航栏系统，自动解析其中的文件夹和书签层级结构，映射到系统的 Folder + Bookmark 数据模型并批量导入。

## Requirements

### 后端

* 新增书签导入 API：`POST /api/bookmarks/import`，接收 MultipartFile（HTML 文件）+ 目标文件夹 ID（可选）+ 重复处理模式参数
* 新增 `BookmarkImportService`，封装解析和导入逻辑
* 使用 Jsoup 解析 Netscape Bookmark HTML 文件
* 递归解析 `<DL>/<DT>/<H3>/<A>` 标签层级，构建文件夹树
* 按层级关系递归创建 Folder 和 Bookmark 记录
* Chrome base64 ICON 直接存入 iconUrl 字段
* 重复 URL 处理：默认覆盖更新，可通过参数 `duplicateMode=SKIP` 切换为跳过
* 事务保证：导入失败时全部回滚
* 导入完成后返回统计信息（新建文件夹数、新书签数、覆盖数/跳过数）

### 前端

* 在书签管理页面添加"导入书签"按钮，打开导入对话框
* 对话框包含：文件选择（仅 .html）、目标文件夹选择（树形下拉）、重复处理模式选择
* 上传后显示导入结果统计

## Acceptance Criteria

* [ ] 上传 Chrome 导出的书签 HTML 文件，系统成功解析并创建对应的文件夹和书签
* [ ] 文件夹层级关系正确保持（包括无限嵌套）
* [ ] 用户可选择导入到指定的目标文件夹
* [ ] 重复 URL 默认覆盖更新，可选择跳过模式
* [ ] 导入结果返回统计信息（新建文件夹数、新书签数、覆盖/跳过数）
* [ ] 导入失败时数据库无脏数据（事务回滚）
* [ ] 前端导入对话框可正常使用

## Definition of Done

* 单元测试覆盖 HTML 解析逻辑
* Swagger API 文档可正常展示
* 中文学习注释完整
* 前端功能可正常交互
* Lint / CI green

## Out of Scope

* 不支持 Firefox/Safari/Edge 等其他浏览器的书签格式（未来可扩展）
* 不支持书签导出功能
* 不支持增量同步（仅一次性导入）

## Technical Approach

1. 新增 Jsoup 依赖（pom.xml）
2. 新增 `BookmarkImportService` — 负责解析 HTML + 批量创建 Folder/Bookmark
3. 新增 `BookmarkImportController`（或在现有 BookmarkController 中添加端点）
4. 新增请求/响应 DTO：`BookmarkImportRequest`、`BookmarkImportResponse`
5. 前端：在书签页面添加导入对话框组件

### 解析算法

递归遍历 `<DL>` 标签：
- 遇到 `<DT><H3>` → 创建 Folder，递归处理其下的 `<DL>`
- 遇到 `<DT><A HREF="">` → 创建 Bookmark
- 用 Map<Long, Long> 维护"临时 ID → 数据库 ID"的映射，处理父子关系

### 重复检测

导入前一次性查出用户所有已有书签 URL 集合，导入过程中实时比对。

## Technical Notes

* Chrome 书签 HTML 使用 `<DL>/<DT>/<H3>/<A>` 标签嵌套表示层级
* 文件可能较大（当前样本 330KB），需要注意解析性能
* base64 ICON 数据直接存入 iconUrl 字段（现有设计已支持 data URI）
* 现有 Service 层已有 FolderService 和 BookmarkService，批量导入在新的 ImportService 中实现
* 开发时使用 Context7 获取 Jsoup 和 Spring Boot 文件上传的最新文档
* Spring Boot 4.x 使用 `jakarta.*` 包名
