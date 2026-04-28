# Research: IK Analysis 中文分词器方案 (ES 9.x)

- **Query**: IK Analysis 插件 ES 9.x 兼容性、Docker 安装方式、分词模式、自定义词典
- **Scope**: External
- **Date**: 2026-04-27

## Findings

### 1. IK Analysis 插件与 ES 9.x 兼容性

**结论：IK Analysis 完全支持 ES 9.x。**

IK Analysis 插件已从原始仓库 `medcl/elasticsearch-analysis-ik` 迁移到 **`infinilabs/analysis-ik`**（由 INFINI Labs 维护）。新仓库同时支持 Elasticsearch 和 OpenSearch。

已通过 HTTP HEAD 请求验证，以下 ES 9.x 版本均有对应的 IK 插件构建包：

| ES 版本 | IK 插件可用 | 验证结果 |
|---------|------------|---------|
| 9.0.0 - 9.0.3 | Yes | HTTP 200 |
| 9.1.0 - 9.1.5 | Yes | HTTP 200 |
| 9.2.0 - 9.2.8 | Yes | HTTP 200 (发布页列出) |
| 9.3.0 - 9.3.3 | Yes | HTTP 200 (发布页列出) |

安装 URL 格式：
```
https://get.infini.cloud/elasticsearch/analysis-ik/{ES_VERSION}
```

例如 ES 9.1.4：
```
https://get.infini.cloud/elasticsearch/analysis-ik/9.1.4
```

**安装前需要确认实际运行的 ES 版本**（通过 `curl http://192.168.8.6:9200` 查看），然后使用对应版本的 URL。

### 2. Docker 环境安装 IK 插件

#### 方式 A：docker exec 安装（推荐，适合已在运行的容器）

```bash
# 1. 进入 ES 容器
docker exec -it <elasticsearch-container-name> bash

# 2. 安装插件（替换为实际 ES 版本）
bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/9.1.4

# 3. 重启 ES 容器
docker restart <elasticsearch-container-name>
```

**重要**：安装插件后必须重启 ES 才能生效。插件安装是持久化的（写入容器文件系统），但如果容器是用 `docker run` 创建的且没有 volume 挂载 plugins 目录，容器重建后插件会丢失。

#### 方式 B：自定义 Dockerfile（推荐，适合长期维护）

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:9.1.4

# 安装 IK 分词插件
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/9.1.4
```

`--batch` 参数跳过确认提示，适合 Dockerfile 自动化构建。

#### 方式 C：docker-compose 中挂载 plugins 目录

```yaml
volumes:
  - /path/to/es/plugins:/usr/share/elasticsearch/plugins
```

此方式需要手动下载插件 zip 包解压到宿主机目录。

#### 安装验证

安装完成后，通过以下命令验证：

```bash
# 检查插件是否已安装
curl -X GET "http://192.168.8.6:9200/_cat/plugins?v"

# 测试分词效果
curl -X POST "http://192.168.8.6:9200/_analyze?pretty" -H 'Content-Type: application/json' -d'
{
  "analyzer": "ik_max_word",
  "text": "前端开发工具"
}
'
```

### 3. IK 分词器的两种模式

#### ik_max_word（最细粒度切分）

索引时使用。将文本尽可能多地切分出词语，产生所有可能的组合。

示例：`"中华人民共和国国歌"`
切分结果：`["中华人民共和国", "中华人民", "中华", "华人", "人民共和国", "人民", "人", "民", "共和国", "共和", "和", "国国", "国歌"]`

特点：
- 切分粒度最细，穷举所有可能词语组合
- 建立的倒排索引更全面
- 适合**索引阶段**使用，确保尽可能多的词条被索引入倒排索引
- 索引体积会比 ik_smart 大

#### ik_smart（最粗粒度切分）

搜索时使用。只做最粗粒度的切分，返回最合理的分词结果。

示例：`"中华人民共和国国歌"`
切分结果：`["中华人民共和国", "国歌"]`

特点：
- 切分粒度最粗，只返回最合理的词语
- 适合**搜索阶段**使用，用户输入的关键词用此模式切分
- 切分出的词条少，查询更高效

#### 为什么索引和搜索用不同模式？

这是经典的 **"索引细、搜索粗"** 策略：

1. **索引时用 ik_max_word**：文档写入时尽可能多地建立词条映射。例如"前端开发工具"被切分为 `["前端", "开发", "工具", "前端开发", "开发工具", "前端开发工具"]`，这样无论用户搜"前端"、"开发"还是"前端开发"都能命中。

2. **搜索时用 ik_smart**：用户输入的关键词只做粗粒度切分。例如用户搜"前端开发"，切分为 `["前端开发"]`，直接在索引中查找，匹配更精准。

如果索引和搜索都用 ik_max_word：搜索"前端开发"会被切分为 `["前端", "开发"]`，默认做 OR 匹配，可能匹配到只含"前端"或只含"开发"但不相关的文档。
如果都用 ik_smart：文档"前端开发工具"可能只被切分为 `["前端开发工具"]`，用户搜"前端"就搜不到。

#### Spring Data ES 中的配置方式

在 `@Field` 注解中指定：

```java
// 索引时用 ik_max_word，搜索时用 ik_smart
@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private String title;
```

### 4. IK 自定义词典

#### 是否需要自定义词典？

**本项目大概率不需要**。IK 自带的词典已经覆盖了大多数常见中文词汇。如果后续发现某些特定术语（如特定的技术名词）分词不准，再考虑添加自定义词典。

#### 词典配置文件位置

安装 IK 插件后，配置文件位于：
```
{ES_HOME}/config/analysis-ik/IKAnalyzer.cfg.xml
```
或
```
{ES_HOME}/plugins/elasticsearch-analysis-ik-*/config/IKAnalyzer.cfg.xml
```

#### 配置文件格式

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <!-- 本地扩展词典（分号分隔多个文件） -->
    <entry key="ext_dict">custom/mydict.dic;custom/single_word_low_freq.dic</entry>

    <!-- 本地扩展停用词词典 -->
    <entry key="ext_stopwords">custom/ext_stopword.dic</entry>

    <!-- 远程扩展词典（支持热更新） -->
    <entry key="remote_ext_dict">http://yoursite.com/mydict.dic</entry>

    <!-- 远程扩展停用词词典 -->
    <entry key="remote_ext_stopwords">http://yoursite.com/stopword.dic</entry>
</properties>
```

#### 自定义词典文件格式

- UTF-8 编码的文本文件
- 每行一个词语
- 以 `#` 开头的行为注释

示例 `mydict.dic`：
```
# 技术术语
Spring Boot
Vue3
MyBatis Plus
Pinia
```

#### 热更新（远程词典）

IK 支持远程词典热更新，无需重启 ES。远程 HTTP 服务需要满足：
1. 返回 `Last-Modified` 和 `ETag` 响应头
2. 内容格式为每行一个词，`\n` 换行

当远程词典内容变化时（Last-Modified 或 ETag 变化），IK 会自动重新加载。

### 5. 替代方案对比

如果 IK 插件不可用，还有以下替代方案：

| 方案 | 类型 | 中文效果 | 安装难度 | 备注 |
|------|------|---------|---------|------|
| **IK Analysis** | 第三方插件 | 优秀 | 低 | 最流行，社区活跃，推荐方案 |
| **SmartCN** | ES 内置插件 | 一般 | 无需安装 | ES 自带 `analysis-smartcn` 插件，使用 Hidden Markov Model，效果不如 IK |
| **HanLP** | 第三方插件 | 优秀 | 中 | 功能最全（命名实体识别、依存句法分析等），但对本场景来说过重 |
| **ANSJ** | 第三方插件 | 良好 | 中 | 更新不如 IK 活跃 |
| **standard** | ES 内置 | 差 | 无需安装 | 当前项目使用的方案，中文按单字切分 |

对于本项目的书签搜索场景，**IK Analysis 是最佳选择**：
- 安装简单（一条命令）
- 分词效果好（专门针对中文优化）
- 社区最活跃，文档最全
- 完全支持 ES 9.x

### 项目当前状态

项目当前的 ES 相关配置：

| 文件 | 当前配置 |
|------|---------|
| `BookmarkDocument.java` | 所有 Text 字段使用 `analyzer = "standard"` |
| `FolderDocument.java` | name 字段使用 `analyzer = "standard"` |
| `SearchService.java` | 使用 `NativeQuery` + `multiMatch`，无需修改 |
| `application-dev.yml` | ES 地址 `http://192.168.8.6:9200` |
| `application-prod.yml` | ES 地址 `http://elasticsearch:9200` |
| `ElasticsearchConfig.java` | 启动时创建索引，仅在索引不存在时创建 |
| `pom.xml` | `spring-boot-starter-data-elasticsearch` + Spring Data ES 6.0.4 |

实施 IK 分词器需要修改的步骤：
1. 在 ES 容器中安装 IK 插件并重启
2. 修改 `BookmarkDocument.java` 和 `FolderDocument.java` 的 `@Field` 注解
3. 删除旧索引（mapping 不可原地修改 analyzer）
4. 重启应用让 `ElasticsearchConfig.createIndices()` 用新 mapping 创建索引
5. 触发 `reindexAll()` 重新导入数据

## External References

- [IK Analysis 插件 GitHub 仓库 (infinilabs/analysis-ik)](https://github.com/infinilabs/analysis-ik) -- 新维护地址
- [IK 插件下载页](https://release.infinilabs.com/analysis-ik/stable/) -- 所有版本的打包文件
- [IK 插件安装 URL](https://get.infini.cloud/elasticsearch/analysis-ik/) -- CLI 安装地址，路径末尾加版本号
- [IK 原始仓库 (medcl/elasticsearch-analysis-ik)](https://github.com/medcl/elasticsearch-analysis-ik) -- 已迁移到 infinilabs

## Caveats / Not Found

- **未确认实际运行的 ES 版本号**：需要执行 `curl http://192.168.8.6:9200` 获取集群信息，以确定使用哪个版本的 IK 插件安装 URL。PRD 提到是 ES 9.x，Spring Data ES 6.0.4 也确认兼容 9.x。
- **未确认 ES 容器名称**：ES 不在项目的 docker-compose.yml 中，是外部部署的。需要确认容器名才能执行 `docker exec`。
- **未验证 IK 插件是否已经安装**：PRD 中列为 open question，需要实际检查 `curl http://192.168.8.6:9200/_cat/plugins` 确认。
- Context7 配额已用尽，未能通过该工具获取文档。所有外部信息均通过 GitHub API 和 IK 官方仓库 README 获取。
