# Research: Spring Data Elasticsearch 6.0 Custom Analyzer Configuration

- **Query**: How to configure custom analyzers in Spring Data ES 6.0 (Spring Boot 4.0.5)
- **Scope**: Mixed (internal codebase + external documentation from bytecode analysis)
- **Date**: 2026-04-27

## Context7 Note

Context7 monthly quota was exceeded during this research. Findings are based on:
- Bytecode analysis of `spring-data-elasticsearch-6.0.4.jar` from local Maven repository
- Internal codebase analysis of the project's ES configuration
- Known Spring Data Elasticsearch 6.x documentation patterns

---

## Findings

### 1. `@Field` Annotation: `analyzer` and `searchAnalyzer` Attributes

The `@Field` annotation in Spring Data ES 6.0.4 (confirmed via bytecode) has the following key attributes:

| Attribute | Type | Purpose |
|---|---|---|
| `analyzer` | String | Name of the analyzer to use when indexing (writing) the field |
| `searchAnalyzer` | String | Name of the analyzer to use when searching the field |
| `normalizer` | String | Name of the normalizer to use (for keyword fields) |
| `type` | FieldType | Field type (Text, Keyword, etc.) |
| `index` | boolean | Whether to index the field |
| `similarity` | String | Similarity algorithm (e.g., "BM25") |
| `termVector` | TermVector | Term vector storage strategy |
| `copyTo` | String[] | Copy field values to other fields |
| `ignoreAbove` | int | Skip indexing strings longer than this |

**Key point**: When `analyzer` is specified but `searchAnalyzer` is omitted, the `analyzer` value is used for both indexing and searching. You can set them independently for asymmetric analysis strategies.

**Example usage** (what the code should look like):
```java
@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private String title;
```

- `ik_max_word` for indexing: finest granularity ("前端开发工具" -> "前端开发工具", "前端开发", "开发工具", "前端", "开发", "工具")
- `ik_smart` for searching: coarsest granularity ("前端" -> "前端")

### 2. `@Setting` Annotation: `settingPath` for Custom Analyzer JSON

The `@Setting` annotation (confirmed via bytecode) has these attributes:

| Attribute | Type | Purpose |
|---|---|---|
| `settingPath` | String | Path to a JSON file in classpath defining index settings |
| `shards` | int | Number of primary shards (default: 1) |
| `replicas` | int | Number of replica shards (default: 1) |
| `useServerConfiguration` | boolean | Use ES server defaults |
| `refreshInterval` | long | Refresh interval in milliseconds |
| `indexStoreType` | String | Index store type |
| `sortFields` | String[] | Fields to sort index by |
| `sortOrders` | SortOrder[] | Sort orders |
| `sortModes` | SortMode[] | Sort modes |
| `sortMissingValues` | SortMissing[] | Sort missing value handling |

**`settingPath` supports custom analyzer definitions via JSON file.** The `Settings` class has a `fromJson` method, confirming it can parse JSON configuration files. The file should be placed on the classpath (e.g., `src/main/resources/`) and referenced as:

```java
@Document(indexName = "bookmark")
@Setting(settingPath = "/es-settings/bookmark-settings.json")
public class BookmarkDocument { ... }
```

**The JSON settings file format** (ES 9.x compatible):
```json
{
  "index": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "analysis": {
    "analyzer": {
      "ik_max_word": {
        "type": "custom",
        "tokenizer": "ik_max_word"
      },
      "ik_smart": {
        "type": "custom",
        "tokenizer": "ik_smart"
      }
    }
  }
}
```

**Important**: The `settingPath` JSON defines index-level settings including the `analysis` section. However, for built-in ES analyzers like `standard`, `simple`, etc., you do NOT need a settings file -- just reference them by name in `@Field(analyzer = "...")`. For **plugin-provided** analyzers like `ik_max_word`, you also do NOT need to define them in `settingPath` because they are registered globally by the plugin at the ES node level. The `settingPath` is only needed for **truly custom** analyzers that combine tokenizers, token filters, and char filters.

**If using the IK plugin**, the `@Setting` annotation only needs `shards` and `replicas` (as it currently does), and `@Field` annotations just reference the analyzer names provided by the IK plugin:
```java
@Setting(shards = 1, replicas = 0)
// ...
@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
```

### 3. Index Mapping Change: Best Practices

#### Can analyzer be changed on an existing index?

**No.** Elasticsearch does not allow changing the analyzer of an existing field mapping. The analyzer is applied at index time -- existing documents were indexed with the old analyzer and would need to be re-indexed anyway. Attempting to update the mapping with a different analyzer will throw an error.

Reference from Elasticsearch docs: "You cannot change the analyzer of an existing field. You must create a new index with the desired mapping and reindex your data."

#### The correct flow for changing analyzers:

1. **Delete the old index** (or create a new index with a different name)
2. **Create the new index** with the updated mapping (analyzer configuration)
3. **Reimport all data** from MySQL (so it gets indexed with the new analyzer)

#### Current code analysis

The project's `ElasticsearchConfig.createIndices()` (lines 52-65) currently only creates indices if they don't exist:

```java
@PostConstruct
public void createIndices() {
    IndexOperations bookmarkOps = elasticsearchOperations.indexOps(BookmarkDocument.class);
    if (!bookmarkOps.exists()) {
        bookmarkOps.createWithMapping();
    }
    // ... same for folder
}
```

**This is the correct pattern for normal operation** -- it avoids deleting data on restart. However, after changing the analyzer, the old index must be deleted first (either manually via ES API or by modifying this init logic temporarily).

#### Recommended approach for the one-time migration:

1. Change `@Field` annotations on `BookmarkDocument` and `FolderDocument` to use `ik_max_word` / `ik_smart`
2. Manually delete the old ES indices: `DELETE /bookmark` and `DELETE /folder` via ES REST API
3. Restart the application -- `createIndices()` will create new indices with the new analyzer mapping
4. `ElasticsearchDataInitializer` will detect empty indices and auto-reimport from MySQL via `SearchService.reindexAll()`

This flow is already supported by the existing code -- no changes to `ElasticsearchConfig` or `ElasticsearchDataInitializer` are needed. The `ElasticsearchDataInitializer` (line 46) checks `if (bookmarkCount.getTotalHits() == 0)` and calls `reindexAll()`.

#### Alternative: Add a programmatic index rebuild endpoint

The `SearchService` already has a `reindex(Long userId)` method (line 194) and `reindexAll()` method (line 252). However, these only re-save documents -- they do NOT delete and recreate the index. For a clean analyzer change, the index must be deleted first.

If a programmatic approach is desired, a method like this could be added to `ElasticsearchConfig`:

```java
public void rebuildIndex(Class<?> documentClass) {
    IndexOperations ops = elasticsearchOperations.indexOps(documentClass);
    if (ops.exists()) {
        ops.delete();
    }
    ops.createWithMapping();
}
```

Then call `rebuildIndex(BookmarkDocument.class)` and `rebuildIndex(FolderDocument.class)`, followed by `searchService.reindexAll()`.

### 4. `multiMatch` Query and Chinese Text Search

#### Current code analysis

The project uses `multiMatch` queries in `SearchService.java` (lines 70-73 and 97-98):

```java
.must(m -> m.multiMatch(mm -> mm
    .query(keyword)
    .fields(List.of("title", "url", "description"))))
```

No `type` or `fuzziness` is specified, so it uses ES defaults:
- **type**: defaults to `best_fields`
- **fuzziness**: none (exact matching after analyzer tokenization)

#### `multiMatch` type options

| Type | Behavior | Best for |
|---|---|---|
| `best_fields` (default) | Takes the best score from any single field. Treats all fields as competing. | Searching for a complete phrase in any one field |
| `most_fields` | Scores from all matching fields are summed. | Same content in multiple fields with different analyzers |
| `cross_fields` | Treats all fields as one big field. Each term is searched across all fields. | Multi-word queries where each word could be in a different field |
| `phrase` | Runs a `match_phrase` query on each field, picks the best. | Phrase matching |
| `phrase_prefix` | Runs `match_phrase_prefix` on each field. | Autocomplete / search-as-you-type |

**For Chinese search, `best_fields` is generally fine** when searching across title/url/description. The key improvement comes from using a proper Chinese analyzer (ik_max_word/ik_smart), not from changing the multiMatch type.

If the goal is to match a query like "前端 工具" across title and description (each word could be in a different field), `cross_fields` would be better. But for single-term or single-field matches, `best_fields` works well.

#### Fuzziness for Chinese

**Fuzziness is NOT useful for Chinese text.** Fuzzy matching in ES works on edit distance (Levenshtein distance) at the character level. Chinese characters are logograms, not phonetic, so:
- "前端" and "前端" -- same, no fuzziness needed
- "前端" and "前段" -- 1 edit distance, but semantically completely different words

**Fuzziness is useful for English typos** (e.g., "sprng" -> "spring"), but should NOT be enabled for Chinese text. If needed, it should be conditional on the input language or set to a low value like `"AUTO"`.

For the current project, **not adding fuzziness is the correct choice** for Chinese. The main improvement should come from the analyzer change, not from fuzzy matching.

### Files Found (Internal Codebase)

| File Path | Description |
|---|---|
| `src/main/java/com/hlaia/config/ElasticsearchConfig.java` | ES index auto-creation on startup; uses `IndexOperations.createWithMapping()` |
| `src/main/java/com/hlaia/config/ElasticsearchDataInitializer.java` | Auto-imports data on startup when ES index is empty |
| `src/main/java/com/hlaia/document/BookmarkDocument.java` | ES document model for bookmarks; uses `analyzer = "standard"` on text fields |
| `src/main/java/com/hlaia/document/FolderDocument.java` | ES document model for folders; uses `analyzer = "standard"` on name field |
| `src/main/java/com/hlaia/service/SearchService.java` | Search logic using NativeQuery + multiMatch; has `reindexAll()` method |
| `src/main/java/com/hlaia/kafka/SearchSyncConsumer.java` | Kafka consumer for incremental ES sync |
| `src/main/resources/application-dev.yml` | ES connection: `http://192.168.8.6:9200` |
| `pom.xml` | Uses `spring-boot-starter-data-elasticsearch` (Spring Boot 4.0.5 -> SD ES 6.0.4) |

### External References

- [Spring Data Elasticsearch Reference - Mapping](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/mapping.html) -- @Field and @Setting annotation documentation
- [Spring Data Elasticsearch Reference - Index Settings](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/index-settings.html) -- settingPath usage
- [Elasticsearch Guide - Analysis](https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis.html) -- ES analyzer concepts
- [Elasticsearch Guide - multi_match query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-multi-match-query.html) -- multiMatch type options
- [IK Analysis Plugin for ES](https://github.com/infinilabs/analysis-ik) -- Chinese analyzer plugin (needs ES version-compatible release)

### Related Specs

- `.trellis/tasks/04-27-fix-es-tag-search/prd.md` -- Task PRD describing the bug and requirements

## Caveats / Not Found

1. **Context7 quota exceeded** -- Could not fetch official Spring Data ES 6.0 documentation via context7. Findings are based on bytecode analysis of the actual 6.0.4 jar and established knowledge of Spring Data ES patterns.

2. **IK plugin version compatibility with ES 9.x** -- Not verified. The IK plugin (infinilabs/analysis-ik) must be installed on the ES server. The version must match the ES server version. The project's ES runs on 192.168.8.6:9200 in Docker; the IK plugin needs to be installed in that container. Check compatibility at https://github.com/infinilabs/analysis-ik/releases.

3. **Alternative to IK plugin** -- If IK installation proves difficult, ES 9.x has improved CJK (Chinese/Japanese/Korean) tokenization support built-in. The `cjk` tokenizer (part of the `standard` analysis package) does bigram tokenization ("前端开发" -> ["前端", "端开", "开发"]), which is better than single-character but worse than IK's dictionary-based approach. ES 9.x may also have newer CJK features worth investigating.

4. **The `@Field` annotation's `analyzer` attribute only works when `createWithMapping()` is called** -- If the index already exists with old mappings, changing the Java annotation has no effect until the index is deleted and recreated. This is the core of the migration challenge.

5. **`@Setting(settingPath)` is NOT needed when using plugin-provided analyzers** -- Since IK analyzers are registered at the ES node level by the plugin, you only need to reference them by name in `@Field(analyzer = "ik_max_word")`. The `@Setting(shards = 1, replicas = 0)` annotation currently used is sufficient.
