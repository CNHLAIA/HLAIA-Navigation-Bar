# Design: 请求序号守卫(last-request-wins)

## 方案选型

| 候选 | 结论 |
|------|------|
| **A. 请求序号守卫(选定)** | store 闭包内维护递增 `fetchSeq`,每次 `fetchBookmarks` 取号,`await` 返回后若号码已过期则丢弃。改动小、无依赖、语义清晰 |
| B. AbortController 取消旧请求 | 需要传 axios per-request signal、处理 cancel 错误分支,侵入更大;且"取消"只是手段,"忽略过期结果"才是目的,收益不抵复杂度 |
| C. 后端保证 FIFO | 不成立:并发 HTTP 请求完成顺序无契约,后端无 bug,不应依赖 |

## 改动点

### 1. `frontend/src/stores/bookmark.js` — 核心

store setup 闭包内新增非响应式序号(`fetchSeq` 不需要进 state,纯内部控制流):

```js
let fetchSeq = 0   // 标识最新一次 fetchBookmarks;递增取号,过期即弃

async function fetchBookmarks(folderId) {
  const seq = ++fetchSeq
  currentFolderId.value = folderId
  selectedIds.value = new Set()

  const cached = bookmarkCache.value.get(folderId)
  if (cached) {
    bookmarks.value = [...cached]                    // 缓存秒出(同步,无竞态窗口)
    try {
      const res = await getBookmarksApi(folderId)
      if (seq !== fetchSeq) {
        // 过期:用户已切走。bookmarkCache 按 folderId 隔离,写入无害且利于回访,只挡 UI 写入
        bookmarkCache.value.set(folderId, res.data || [])
        return
      }
      bookmarks.value = res.data || []
      bookmarkCache.value.set(folderId, res.data || [])
    } catch (e) { console.error(...) }
  } else {
    loading.value = true
    try {
      const res = await getBookmarksApi(folderId)
      bookmarkCache.value.set(folderId, res.data || [])   // 缓存写入不受过期影响
      if (seq !== fetchSeq) return                        // 过期:不写 bookmarks、不动 loading
      bookmarks.value = res.data || []
    } finally {
      if (seq === fetchSeq) loading.value = false          // 只有最新请求有权关 loading
    }
  }
}
```

关键语义:

- **过期判定**:响应落地时 `seq !== fetchSeq` ⇒ 此刻已有更新的 fetch 在途/已完成 ⇒ 本响应对 UI 而言作废
- **缓存仍写入**:按 folderId 为 key,晚到的 A 响应写 A 的缓存天然正确,回访 A 时还能秒出
- **loading 归属**:旧请求的 `finally` 不再提前关掉新请求的 loading
- **loading 接管兜底**(实现复核时补):无缓存请求置 `loading=true` 在途时切到有缓存目录,缓存路径不碰 loading、过期请求的 finally 又无权关,进度条会永久卡住——因此缓存分支填充缓存数据时同步 `loading.value = false`(此刻尚无 await,seq 必然最新,无竞态)
- **写操作路径不受影响**:CRUD/排序/移动后的 `fetchBookmarks(currentFolderId.value)` 是最新取号者,正常生效

### 2. `frontend/src/components/BookmarkGrid.vue` — watcher 的 `.finally()`

579 行附近的 `.finally()` 由"该次 fetch 完成"触发,过期请求完成时它会误清新目录的骨架屏定时器并播放错乱动画。补一行守卫:

```js
.finally(() => {
  if (newId !== props.folderId) return   // 过期请求:新目录的 watcher 已重置过状态,不越权清理
  clearTimeout(skeletonTimer)
  showSkeleton.value = false
  if (!isCached) triggerCardAnimation()
})
```

## 明确不改

- 后端:无 bug,不动
- `staging.js` 的 `fetchItems`:全局单一列表、无目录参数,不存在"不同目标并发覆盖"的竞态
- `folder.js` 的 `fetchTree`:全量树数据,晚到响应只是稍旧的全量,风险低;如未来出现同类症状再按本方案加守卫

## 验证

1. `cd frontend && npm run build` 通过
2. 人工复现原路径:刷新页面 → 快速连点 A→B → 界面最终为 B,不再被 A 覆盖
3. 交替切换"有缓存目录"与"无缓存目录",界面始终跟随最后选中项;骨架屏不闪烁
4. 写操作(新增/删除/拖拽排序)后列表刷新正常
