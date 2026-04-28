# fix: ES搜索标签搜不出来的bug

## Goal

修复 Elasticsearch 中文搜索匹配率低的问题。当前所有 ES 文本字段使用 `standard` 分词器，对中文按单字切分，导致大量书签标题和文件夹名称搜不到。

## Requirements

- 在 ES 容器中安装 IK 中文分词器插件
- 修改 BookmarkDocument 和 FolderDocument 的分词器为 `ik_max_word`（索引）/ `ik_smart`（搜索）
- 删除旧索引 → 重启应用 → 自动重建索引并 reimport
- 确保 Kafka 增量同步也使用新分词器（自动生效，无需额外代码）

## Acceptance Criteria

- [ ] 中文书签标题能被正确搜索（如搜"前端"能匹配"前端开发工具"）
- [ ] 英文搜索不受影响
- [ ] 混合中英文搜索正常
- [ ] 已有数据重新索引后全部可搜
- [ ] 新增书签/文件夹自动使用新分词器

## Definition of Done

- ES 分词器变更经过测试验证
- 中文搜索测试用例通过
- Lint / typecheck / CI green
- 代码注释面向初学者，解释 WHY

## Technical Approach

### 根因

`standard` 分词器对中文按单字切分（"前端开发" → ["前","端","开","发"]），导致搜索"前端"时匹配效率极低。

### 方案：安装 IK Analysis 插件

**为什么选 IK**：最流行的中文分词插件，完全支持 ES 9.x，安装简单，社区活跃。

**核心原理（"索引细、搜索粗"策略）**：
- 索引时用 `ik_max_word`：将文本尽可能多地切分出词语，穷举所有组合
  - "前端开发工具" → ["前端开发工具", "前端开发", "开发工具", "前端", "开发", "工具"]
- 搜索时用 `ik_smart`：只做最粗粒度切分
  - "前端" → ["前端"]，直接在索引中查找，匹配更精准

### 代码变更

**仅需修改 2 个文件**（`SearchService.java` 查询逻辑无需改动）：

1. `BookmarkDocument.java` — title/url/description 字段改为 `analyzer = "ik_max_word", searchAnalyzer = "ik_smart"`
2. `FolderDocument.java` — name 字段同样修改

### 迁移流程

ES 不允许在已有索引上修改 analyzer，必须删除重建：

1. 确认 ES 版本 → 安装 IK 插件 → 重启 ES 容器
2. 修改 Document 类的 `@Field` 注解
3. 删除旧索引（`DELETE /bookmark` + `DELETE /folder`）
4. 重启应用 → `ElasticsearchConfig.createIndices()` 用新 mapping 创建索引
5. `ElasticsearchDataInitializer` 检测到空索引 → 自动调用 `reindexAll()` 从 MySQL 重新导入

**注意**：现有代码已完整支持此流程，无需修改 `ElasticsearchConfig` 或 `ElasticsearchDataInitializer`。

## Decision (ADR-lite)

**Context**: standard 分词器中文支持差，搜索匹配率低
**Decision**: 安装 IK Analysis 插件，使用 ik_max_word/ik_smart 组合
**Consequences**: 需要在 ES 容器中安装插件（一次性操作），需删除旧索引重建（短暂搜索不可用）

## Out of Scope

- 不改前端搜索逻辑
- 不改 Kafka 同步机制
- 不添加搜索高亮功能
- 不添加 fuzziness（对中文无意义）
- 不改 multiMatch type（best_fields 对中文足够）

## Research References

- [`research/ik-analyzer.md`](research/ik-analyzer.md) — IK 插件 ES 9.x 兼容性、安装方式、分词模式
- [`research/springdata-es-analyzer.md`](research/springdata-es-analyzer.md) — Spring Data ES 6.0 analyzer 配置方式

## Technical Notes

### 前置确认（部署步骤）

```bash
# 1. 确认 ES 版本
curl http://192.168.8.6:9200

# 2. 检查 IK 是否已安装
curl http://192.168.8.6:9200/_cat/plugins?v

# 3. 安装 IK 插件（替换 {VERSION} 为实际 ES 版本）
docker exec -it <es-container> bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/{VERSION}

# 4. 重启 ES 容器
docker restart <es-container>

# 5. 验证安装
curl -X POST "http://192.168.8.6:9200/_analyze?pretty" -H 'Content-Type: application/json' -d'
{"analyzer": "ik_max_word", "text": "前端开发工具"}'
```

### 关键文件

| 文件 | 变更 |
|------|------|
| `document/BookmarkDocument.java` | title/url/description 的 analyzer 改为 ik_max_word + searchAnalyzer ik_smart |
| `document/FolderDocument.java` | name 的 analyzer 改为 ik_max_word + searchAnalyzer ik_smart |
