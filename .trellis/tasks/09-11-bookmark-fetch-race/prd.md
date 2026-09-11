# 修复快速切换文件夹时书签列表被过期响应覆盖的竞态 bug

## Goal

前端 bookmark store 的 fetchBookmarks 在并发请求下缺少过期响应保护:快速连续点击目录A、B时,慢返回的A响应会覆盖B的数据。通过请求序号守卫丢弃过期响应,保证界面始终显示当前目录的书签。

## 背景与复现

用户报告的复现路径(2026-09-11):

1. 刷新页面(清空前端 store 内存缓存;后端缓存也是冷的)
2. 在很短的时间内先点击目录 A,再点击目录 B
3. 预期:界面最终显示 B 的书签(最后点击的目录)
4. 实际(偶发):约 1 秒后界面显示的是 A 的书签,而目录树高亮/面包屑是 B
5. 再点一次刷新按钮(或重新进入 B)后恢复正常显示 B

触发条件:两个 GET `/api/folders/{id}/bookmarks` 并发在途,且 A 的响应比 B 晚到达(首次访问无缓存、A 的数据量更大或后端查询更慢时容易命中)。

## 根因(排查结论)

后端无问题:`BookmarkService.getBookmarksByFolder` 是独立 DB 查询,并发 HTTP 请求本就不保证完成顺序,FIFO 不是 HTTP 的契约,不应依赖。

Bug 在前端 `frontend/src/stores/bookmark.js` 的 `fetchBookmarks`(77-112 行):

```js
async function fetchBookmarks(folderId) {
  currentFolderId.value = folderId
  ...
  loading.value = true
  try {
    const res = await getBookmarksApi(folderId)   // ← A、B 两个请求并发在途
    const data = res.data || []
    bookmarks.value = data                        // ← 无守卫:晚到的旧响应无条件覆盖
    bookmarkCache.value.set(folderId, data)
  } finally {
    loading.value = false                         // ← 附带问题:A 的 finally 会提前关掉 B 的 loading
  }
}
```

时序:点击 A → 发起请求 A;快速点击 B → 发起请求 B;B 先返回 → 界面显示 B;约 1 秒后 A 返回 → `bookmarks.value` 被 A 的数据覆盖 → 界面显示 A(与用户所见完全吻合)。

同一函数还有两处同族问题:

- **缓存分支的后台静默刷新**(90-98 行)存在相同竞态:回访目录 A 时缓存秒出 + 后台刷新,若用户在刷新返回前切到 B,A 的刷新响应同样会覆盖 B 的数据
- **`loading` 共享标志**:旧请求的 `finally` 会把新请求的 loading 提前置 false
- 关联的 `BookmarkGrid.vue` watcher(579 行)的 `.finally()` 也会被过期请求触发,清掉新目录的骨架屏定时器并播放错乱动画

## Requirements

- R1: `fetchBookmarks` 必须丢弃过期响应——响应返回时,若已经不是当前(最新)请求,不得写 `bookmarks`、不得影响 loading
- R2: 缓存分支的后台静默刷新同样受过期守卫约束(过期则不覆盖界面)
- R3: `loading` 的置位/复位只反映最新请求的状态
- R4: 现有行为不得回归:写操作(CRUD/排序/移动/批量)后的 `fetchBookmarks(currentFolderId)` 刷新、缓存秒出策略、骨架屏与 stagger 动画逻辑保持不变
- R5: 修复方案不改后端;`bookmarkCache` 按 folderId 为 key,过期响应更新缓存本身无害,可保持

## Acceptance Criteria

- [ ] 冷启动(刷新页面)后快速连点目录 A→B(A、B 均无前端缓存),界面最终显示 B 的书签,且不再被 A 的数据覆盖
- [ ] 带缓存的目录 A 与无缓存的目录 B 快速交替切换,界面始终与最后选中的目录一致
- [ ] B 在途时 A 的旧响应返回:骨架屏/加载状态不闪烁,loading 不被提前终止
- [ ] 正常单目录切换、回访缓存目录秒出、写操作后列表刷新等既有行为无回归
- [ ] `npm run build`(frontend)通过

## Notes

- 技术方案(请求序号守卫的具体实现)见 `design.md`
- 这是 lightweight 任务:PRD + 简要 design.md,不单列 implement.md
