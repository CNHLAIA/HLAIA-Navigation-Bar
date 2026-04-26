# Elasticsearch 集成教程 —— 从零开始

> 本教程面向 Elasticsearch 零基础的 Java 后端初学者。
> 你将亲手完成：ES 概念理解 → Docker 部署 → Spring Boot 集成 → Kafka 数据同步 → 搜索 API。
> 教程分 6 个步骤，每步完成后都可以验证。

---

## Step 0: Elasticsearch 基础概念

在写代码之前，先理解几个核心概念。如果你已经了解，可以跳过。

### Elasticsearch 是什么？

Elasticsearch（简称 ES）是一个**分布式搜索和分析引擎**。你可以把它想象成一个"超级数据库"，但它不像 MySQL 那样精确匹配，而是擅长**全文搜索**（模糊匹配、关键词搜索、相关性排序）。

### MySQL vs Elasticsearch 对比

| 概念 | MySQL | Elasticsearch |
|------|-------|---------------|
| 数据库 | Database | Index（索引） |
| 表 | Table | Index（索引） |
| 行 | Row | Document（文档） |
| 列 | Column | Field（字段） |
| 主键 | Primary Key | `_id` |
| SQL 查询 | `SELECT * FROM bookmark WHERE title LIKE '%github%'` | Search API (JSON 格式查询) |
| 全文搜索 | `LIKE '%keyword%'`（慢，不准确） | 倒排索引（快，支持相关性排序、高亮） |

### 为什么不用 MySQL 的 LIKE？

MySQL 的 `LIKE '%keyword%'` 有几个致命问题：
1. **性能差** — `%keyword%` 无法使用索引，每次查询都要全表扫描
2. **不支持相关性排序** — LIKE 要么匹配要么不匹配，无法按"有多匹配"排序
3. **不支持高亮** — 无法告诉你匹配的关键词在结果的哪个位置
4. **不支持分词** — 搜 "Spring Boot" 搜不到只包含 "Spring" 的记录（LIKE 是精确匹配子串）

### 倒排索引（ES 快的秘密）

MySQL 用的是"正排索引"：id → 内容（通过 id 找内容）
ES 用的是"倒排索引"：关键词 → 文档 id 列表（通过关键词找文档）

例如，有三条书签：
- 文档1: "GitHub 代码托管平台"
- 文档2: "GitLab 代码管理工具"
- 文档3: "Spring Boot 官方文档"

ES 会对每条文档进行**分词**，然后建立倒排索引：

| 关键词 | 出现在哪些文档 |
|--------|--------------|
| github | [文档1] |
| gitlab | [文档2] |
| 代码 | [文档1, 文档2] |
| spring | [文档3] |
| boot | [文档3] |
| 官方 | [文档3] |

当你搜索 "代码" 时，ES 直接查倒排索引，瞬间返回 [文档1, 文档2]，不需要扫描所有文档。

### 本项目的数据流

```
用户操作（增删改书签/文件夹）
  → BookmarkService / FolderService 写 MySQL
  → KafkaProducer 发送同步消息到 Kafka
  → SearchSyncConsumer 消费消息
  → ElasticsearchOperations 更新 ES 索引

用户搜索
  → 前端调 GET /api/search?keyword=xxx
  → SearchController → SearchService
  → ElasticsearchOperations 查询 ES 索引
  → 返回搜索结果（带高亮）
```

---

## Step 1: Docker 部署 Elasticsearch

### 1.1 修改 docker-compose.yml

在项目的 `docker-compose.yml` 中添加 ES 服务。在 `hlaia-nav` 服务**之前**添加：

```yaml
  # Elasticsearch 搜索引擎
  # ES 是一个 Java 应用，比较吃内存，建议分配至少 1GB
  # single-node 模式：单节点运行，适合开发和小规模部署
  elasticsearch:
    image: elasticsearch:9.2.8
    container_name: hlaia-elasticsearch
    restart: unless-stopped
    environment:
      # 【重要】必须设置为 true，否则 ES 检测到生产环境会拒绝启动
      # 因为我们是单节点部署，没有配置集群的安全认证
      - discovery.type=single-node
      # JVM 堆内存设置：-Xms（初始）-Xmx（最大）
      # 建议两者设为相同值，避免动态扩缩带来的性能波动
      # 对于书签导航栏这种小项目，512MB 足够
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      # 关闭安全认证（学习环境，生产环境需要开启）
      - xpack.security.enabled=false
      # 关闭机器学习功能（不需要，节省内存）
      - xpack.ml.enabled=false
    ports:
      # 9200: ES 的 REST API 端口（HTTP 请求）
      # 开发环境需要暴露到宿主机，方便用浏览器/Postman 调试
      # 生产环境可以不暴露，只通过 Docker 内部网络访问
      - "9200:9200"
    networks:
      - app-network
```

> **注意**：端口 `9200` 暴露到宿主机是方便你在开发时用浏览器访问 `http://192.168.8.6:9200` 来验证 ES 是否正常运行。生产环境可以去掉 `ports` 配置。

### 1.2 启动验证

```bash
cd "E:\Hello World\JAVA\HLAIANavigationBar"
docker-compose up -d elasticsearch
```

等一分钟左右（ES 启动比较慢），然后访问：
```bash
curl http://192.168.8.6:9200
```

如果看到类似下面的 JSON 响应，说明 ES 启动成功：
```json
{
  "name" : "hlaia-elasticsearch",
  "cluster_name" : "docker-cluster",
  "version" : {
    "number" : "9.2.8"
  },
  "tagline" : "You Know, for Search"
}
```

### 1.3 开发环境配置

在 `application-dev.yml` 中添加 ES 连接配置（注意缩进，放在 `spring:` 下面的 `data:` 节点里，和 `redis:` 同级）：

```yaml
spring:
  data:
    redis:
      host: 192.168.8.6
      port: 6379
      key-prefix: "dev:"
    # Elasticsearch 配置
    # Spring Data ES 使用 spring.data.elasticsearch 前缀自动配置连接
    elasticsearch:
      repositories:
        # 启用 Elasticsearch Repository 支持（我们主要用 ElasticsearchOperations，但也开启以备后用）
        enabled: true
```

在 `application-prod.yml` 中也添加相同的配置（放在 `spring.data.redis:` 同级）：

```yaml
    elasticsearch:
      repositories:
        enabled: true
```

**ES 的连接地址怎么配？** Spring Boot 4.x 的 `spring-boot-starter-data-elasticsearch` 会自动配置，但我们需要在配置文件中指定 ES 服务器地址。创建一个新的配置类（在 Step 2 中会做）。

> **注意**：Spring Data ES 6.0 使用新的 `Rest5Client`，不再使用旧的 `RestClient`。Spring Boot 4.x 的自动配置已经处理了这个变化，你只需要添加 starter 依赖即可，不需要手动配置客户端。

---

## Step 2: 添加 Maven 依赖 + ES 配置类

### 2.1 修改 pom.xml

在 `pom.xml` 的 `<dependencies>` 中添加（放在其他 Spring Boot Starter 附近，比如 `spring-boot-starter-data-redis` 后面）：

```xml
        <!-- Elasticsearch 搜索引擎 -->
        <!-- spring-boot-starter-data-elasticsearch 提供了：
             1. ElasticsearchOperations / ElasticsearchRestTemplate —— 编程式查询 API
             2. ElasticsearchRepository —— 类似 MyBatis-Plus 的 Repository 模式
             3. @Document 注解 —— 定义 ES 文档映射
             4. 自动配置 —— 根据 application.yml 自动创建客户端连接
        -->
        <!-- 为什么不需要指定版本？
             Spring Boot 4.0.5 的 BOM（依赖管理）已经管理了 Spring Data ES 的版本，
             会自动使用与 Boot 4.0.5 兼容的 Spring Data ES 6.0.x 版本。
             这就是使用 spring-boot-starter-parent 作为父 POM 的好处之一。-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
        </dependency>
```

### 2.2 刷新 Maven 依赖

在 IDEA 中点击 Maven 面板的刷新按钮，或运行：
```bash
./mvnw dependency:resolve
```

确认依赖下载成功，没有版本冲突。

### 2.3 创建 ES 配置类

新建文件 `src/main/java/com/hlaia/config/ElasticsearchConfig.java`：

```java
package com.hlaia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
// 注意：Spring Data ES 6.0 不再需要手动注册 RestClient bean
// Spring Boot 的自动配置会根据配置文件自动创建 ElasticsearchOperations
// 所以这个配置类目前为空，后续如果需要自定义 ES 行为可以在这里添加

/**
 * 【Elasticsearch 配置类】—— 管理搜索相关的配置
 *
 * Elasticsearch 在 Spring Boot 4.x 中的自动配置：
 *   Spring Boot 的 spring-boot-starter-data-elasticsearch 提供了自动配置，
 *   会自动创建以下 Bean：
 *   - ElasticsearchOperations：用于编程式查询（我们主要用这个）
 *   - ElasticsearchRestTemplate：ElasticsearchOperations 的实现类
 *   - ElasticsearchClient：底层 ES Java 客户端（Rest5Client）
 *
 *   你不需要手动创建这些 Bean，Spring Boot 会根据 application.yml 中的配置自动完成。
 *
 *   这和 MyBatis-Plus 类似：MyBatis-Plus 的自动配置会根据 application.yml 中的
 *   spring.datasource 配置自动创建 SqlSessionFactory 和 Mapper Bean，
 *   你只需要在 application.yml 中配好数据库连接信息就行了。
 *
 * Spring Data ES 6.0 的重大变化（相比 5.x）：
 *   - 使用新的 Rest5Client 替代旧的 RestClient
 *   - Rest5Client 是 ES 9.x 引入的新客户端，性能更好，API 更简洁
 *   - 如果你在网上搜到用 RestClient 的旧代码，需要适配为 Rest5Client
 */
@Configuration
public class ElasticsearchConfig {

    // 后续如果需要自定义配置，可以在这里添加 Bean
    // 例如：自定义索引设置、分词器配置等
}
```

### 2.4 添加 ES 连接地址到 application.yml

在 `application-dev.yml` 中添加（放在文件末尾，与 `jwt:` 和 `logging:` 同级）：

```yaml
elasticsearch:
  # 开发环境：直接指向 NAS 上的 ES 容器（通过宿主机 IP 访问）
  # uris 是 ES 的 REST API 端点，默认端口 9200
  uris: http://192.168.8.6:9200
```

在 `application-prod.yml` 中添加：

```yaml
elasticsearch:
  # 生产环境：使用 Docker 容器名（通过 app-network 内部网络访问）
  # 这和 MySQL 使用 "mysql" 而非 IP 是同一个原理
  uris: http://elasticsearch:9200
```

> **等等，为什么是自定义前缀 `elasticsearch.uris` 而不是 `spring.data.elasticsearch.xxx`？**
> 因为 Spring Boot 4.x 中，ES 的连接属性是 `spring.elasticsearch.uris`。
> 让我们修正一下：

实际应该用 Spring Boot 的标准配置键。在 `application-dev.yml` 中，在 `spring:` 节点下添加：

```yaml
spring:
  # ... 已有的 datasource, data, kafka, flyway 配置 ...

  # Elasticsearch 连接配置
  # Spring Boot 4.x 的标准配置键是 spring.elasticsearch
  elasticsearch:
    uris: http://192.168.8.6:9200
```

在 `application-prod.yml` 中：

```yaml
spring:
  # ... 已有的配置 ...

  elasticsearch:
    uris: http://elasticsearch:9200
```

### 2.5 验证配置是否生效

启动 Spring Boot 应用（开发模式），查看控制台日志中是否有 Elasticsearch 相关的自动配置日志。如果启动成功且没有报错，说明 ES 连接配置正确。

---

## Step 3: 创建 ES 文档模型

ES 的"文档"类似于 MySQL 的"行"。我们需要创建 Java 类来表示 ES 中的文档结构。

### 3.1 新建 ES 文档包

在 `src/main/java/com/hlaia/` 下新建包 `document`（和 `entity`、`dto` 同级）。

为什么叫 `document` 而不叫 `es`？
- ES 中的数据叫"文档"（Document），用 document 命名更准确地表达含义
- 和 `entity` 包（对应 MySQL 表）区分开，避免混淆

### 3.2 创建 BookmarkDocument.java

新建文件 `src/main/java/com/hlaia/document/BookmarkDocument.java`：

```java
package com.hlaia.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

/**
 * 【ES 文档模型 —— 书签搜索文档】
 *
 * BookmarkDocument vs Bookmark（Entity）的区别：
 *   - Bookmark（Entity）：对应 MySQL 表的完整字段，包含所有业务数据
 *   - BookmarkDocument（Document）：只包含搜索需要的字段，是 ES 索引中的文档结构
 *
 *   为什么要分开？
 *   1. 不是所有字段都需要搜索（如 sortOrder 不需要）
 *   2. 搜索结果的展示格式可能与原始数据不同（如需要高亮）
 *   3. ES 和 MySQL 是两个独立的存储系统，各自优化自己的数据结构
 *
 * @Document 注解 —— 声明这是一个 ES 文档
 *   indexName = "bookmark"：ES 中的索引名称（类似 MySQL 的表名）
 *   shards = 1：索引的分片数。分片是 ES 分布式存储的单元，
 *     1 个分片表示所有数据存在一个分片上。对于小项目，1 个分片足够。
 *     大型项目可能需要多个分片来分散存储和并行查询。
 *   replicas = 0：副本数。副本是分片的复制，用于高可用和负载均衡。
 *     单节点模式下副本必须为 0（因为没有其他节点来存放副本）。
 *
 * @Setting 注解 —— 自定义索引设置
 *   在这里我们设置分片和副本数。如果不设置，ES 默认 1 分片 1 副本。
 *   单节点模式下如果设置副本 > 0，索引会一直处于"黄色"警告状态（因为副本无法分配）。
 */
@Data
@Document(indexName = "bookmark", shards = 1, replicas = 0)
@Setting(shards = 1, replicas = 0)
public class BookmarkDocument {

    /**
     * @Id 注解 —— 标记这是文档的唯一标识（等价于 MySQL 的主键）
     * ES 中每个文档都有一个 _id 字段，@Id 注解将这个字段映射到 Java 属性。
     * 我们使用 MySQL 中 bookmark 的 id 作为 ES 文档的 _id，
     * 这样可以确保 MySQL 和 ES 中的数据一一对应。
     */
    @Id
    private Long id;

    /**
     * @Field 注解 —— 定义字段在 ES 中的存储和索引行为
     *
     * type = FieldType.Text：
     *   Text 类型会被分词器分词后建立倒排索引，适合全文搜索。
     *   例如 "Spring Boot 教程" 会被分词为 ["spring", "boot", "教程"]，
     *   搜索 "spring" 就能匹配到。
     *
     * type = FieldType.Keyword：
     *   Keyword 类型不会分词，整个值作为一个整体建立索引。
     *   适合精确匹配，如用户 ID、状态码等。
     *
     * analyzer = "standard"：
     *   指定索引时使用的分词器。standard 是 ES 默认的分词器，
     *   对英文按空格和标点分词，对中文按单个字分词。
     *   注意：standard 对中文的分词效果不好（每个字一个词），
     *   生产环境通常会换成 ik_max_word（中文智能分词插件），
     *   但 ik 插件需要单独安装，MVP 阶段先用 standard。
     */
    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long folderId;

    /**
     * 书签标题 —— 全文搜索的主要字段
     * 使用 Text 类型支持分词搜索，同时配置了 searchAnalyzer
     * analyzer：索引时使用的分词器（写入数据时如何分词）
     * searchAnalyzer：搜索时使用的分词器（用户输入的关键词如何分词）
     * 大多数情况下两者相同，但某些场景下可以不同（如搜索时用更粗粒度的分词）
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    /**
     * URL —— 使用 Keyword 类型
     * URL 通常不需要分词搜索（用户不会搜 "https://com"），
     * 但用户可能想搜域名，所以也添加 Text 类型的子字段
     *
     * 不过为简化，MVP 阶段 URL 也用 Text 类型
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String url;

    /**
     * 描述 —— 全文搜索的辅助字段
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    /**
     * 图标 URL —— 不需要搜索，但搜索结果需要显示
     * 不添加 @Field 注解也会被 ES 存储，但不会被索引（不可搜索）
     * 显式标注 Keyword 类型表示我们不搜索它，只存储和展示
     */
    @Field(type = FieldType.Keyword)
    private String iconUrl;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;
}
```

### 3.3 创建 FolderDocument.java

新建文件 `src/main/java/com/hlaia/document/FolderDocument.java`：

```java
package com.hlaia.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

/**
 * 【ES 文档模型 —— 文件夹搜索文档】
 *
 * 文件夹的搜索字段比书签少，只有 name 需要全文搜索。
 * 但搜索结果需要展示 icon、parentId 等信息，所以也要包含这些字段。
 */
@Data
@Document(indexName = "folder", shards = 1, replicas = 0)
@Setting(shards = 1, replicas = 0)
public class FolderDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Long)
    private Long parentId;

    /**
     * 文件夹名称 —— 文件夹的主要搜索字段
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Keyword)
    private String icon;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;
}
```

### 3.4 验证文档模型

启动 Spring Boot 应用。Spring Data ES 会自动根据 @Document 注解在 ES 中创建对应的索引。
启动成功后，可以访问 `http://192.168.8.6:9200/bookmark` 和 `http://192.168.8.6:9200/folder` 来验证索引是否创建。

---

## Step 4: Kafka 数据同步 —— 让 MySQL 和 ES 保持一致

这是最核心的步骤。每当书签或文件夹被增删改时，我们需要同步更新 ES 中的数据。

### 4.1 在 KafkaProducer 中添加同步方法

打开 `src/main/java/com/hlaia/kafka/KafkaProducer.java`，添加以下方法：

```java
    /**
     * 发送"书签 ES 同步"消息
     *
     * 数据同步流程：
     *   1. BookmarkService 在增删改书签后调用此方法
     *   2. 消息发送到 Kafka 的 "search-sync" topic
     *   3. SearchSyncConsumer 消费消息，根据操作类型更新 ES 索引
     *
     * 为什么用 "search-sync" 而不是 "bookmark-es-sync"？
     *   使用统一的 topic 名称，书签和文件夹的同步都发到同一个 topic，
     *   通过消息中的 type 字段区分是书签还是文件夹。
     *   这样只需要一个 Consumer、一个 Topic，简化架构。
     *
     * 消息格式：
     *   {"action":"CREATE","type":"bookmark","id":123}
     *   - action: 操作类型（CREATE / UPDATE / DELETE）
     *   - type: 数据类型（bookmark / folder）
     *   - id: 数据 ID（消费者根据 ID 从 MySQL 查询最新数据，再写入 ES）
     *
     * 为什么不直接把完整数据放在消息里，而是只传 ID？
     *   1. 解耦：消费者从 MySQL 查询最新数据，保证 ES 和 MySQL 完全一致
     *   2. 减少消息体积：只传 ID，消息更小
     *   3. 避免数据不一致：如果消息中的数据和 MySQL 不一致（比如被并发修改），
     *      直接用消息数据写入 ES 会导致 ES 和 MySQL 不同步
     *
     * @param action 操作类型（CREATE / UPDATE / DELETE）
     * @param type   数据类型（bookmark / folder）
     * @param id     数据 ID
     */
    public void sendSearchSync(String action, String type, Long id) {
        String message = "{\"action\":\"" + action + "\",\"type\":\"" + type + "\",\"id\":" + id + "}";
        kafkaTemplate.send("search-sync", id.toString(), message);
        log.info("Sent search sync: {} {} {}", action, type, id);
    }
```

### 4.2 在 BookmarkService 中添加同步调用

打开 `src/main/java/com/hlaia/service/BookmarkService.java`，在以下方法的末尾添加同步调用：

**在 createBookmark 方法中**（`return toResponse(bookmark);` 之前添加）：

```java
        // ============ 第五步：发送 ES 同步消息 ============
        // 创建书签后，需要把新书签同步到 Elasticsearch，这样用户才能搜到它
        kafkaProducer.sendSearchSync("CREATE", "bookmark", bookmark.getId());
```

**在 updateBookmark 方法中**（`return toResponse(bookmark);` 之前添加）：

```java
        // 更新书签后，同步到 ES
        kafkaProducer.sendSearchSync("UPDATE", "bookmark", bookmarkId);
```

**在 deleteBookmark 方法中**（`bookmarkMapper.deleteById(bookmarkId);` 之后添加）：
```java
        // 删除书签后，从 ES 中也删除对应的文档
        kafkaProducer.sendSearchSync("DELETE", "bookmark", bookmarkId);
```

**在 batchDelete 方法中**（`bookmarkMapper.deleteBatchIds(request.getIds());` 之后添加）：
```java
        // 批量删除后，逐个发送 ES 同步消息
        for (Long id : request.getIds()) {
            kafkaProducer.sendSearchSync("DELETE", "bookmark", id);
        }
```

**在 moveBookmarks 方法中**（for 循环中的 `bookmarkMapper.updateById(bookmark);` 之后添加）：
```java
            // 移动书签后同步 ES（folderId 变了）
            kafkaProducer.sendSearchSync("UPDATE", "bookmark", bookmark.getId());
```

### 4.3 在 FolderService 中添加同步调用

打开 `src/main/java/com/hlaia/service/FolderService.java`，在以下方法中添加：

**在 createFolder 方法中**（`return toTreeResponse(folder);` 之前添加）：

```java
        // 创建文件夹后同步 ES
        kafkaProducer.sendSearchSync("CREATE", "folder", folder.getId());
```

**在 updateFolder 方法中**（`return toTreeResponse(folder);` 之前添加）：
```java
        // 更新文件夹后同步 ES
        kafkaProducer.sendSearchSync("UPDATE", "folder", folderId);
```

**在 deleteFolder 方法中**（`folderMapper.deleteById(folderId);` 之后添加）：
```java
        // 删除文件夹后从 ES 中删除
        kafkaProducer.sendSearchSync("DELETE", "folder", folderId);
```

> **注意**：FolderService 目前没有注入 KafkaProducer。你需要在 FolderService 的字段中添加：
>
> ```java
> private final com.hlaia.kafka.KafkaProducer kafkaProducer;
> ```
> 由于使用了 `@RequiredArgsConstructor`，只需添加 final 字段即可，Lombok 会自动在构造方法中注入。

### 4.4 创建 SearchSyncConsumer

新建文件 `src/main/java/com/hlaia/kafka/SearchSyncConsumer.java`：

```java
package com.hlaia.kafka;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【Kafka 消费者 —— MySQL 到 Elasticsearch 的数据同步】
 *
 * 这个消费者的职责：
 *   监听 Kafka 的 "search-sync" topic，收到消息后根据操作类型更新 ES 索引。
 *   这就是 "Kafka 异步同步" 的消费者端。
 *
 * 为什么用 Kafka 而不是直接在 Service 中写 ES？
 *   1. 解耦：BookmarkService/FolderService 不需要知道 ES 的存在
 *   2. 可靠：如果 ES 暂时不可用，Kafka 消息不会被丢失，消费者恢复后继续处理
 *   3. 异步：写 MySQL 后立即返回，ES 同步在后台完成，不影响接口响应速度
 *   4. 可扩展：如果将来需要把数据同步到其他系统（如推荐系统），
 *      只需要添加新的 Consumer，不需要修改业务代码
 *
 * ElasticsearchOperations 是什么？
 *   是 Spring Data ES 提供的核心接口，用于对 ES 进行 CRUD 操作。
 *   类似于 MyBatis-Plus 的 BaseMapper，但操作的是 ES 而不是 MySQL。
 *   常用方法：
 *   - save(document)：创建或更新文档（如果 _id 已存在则更新）
 *   - delete(id, class)：删除文档
 *   - search(query, class)：搜索文档
 *
 * @KafkaListener 注解的作用：
 *   告诉 Spring Kafka "这个方法是一个 Kafka 消费者"。
 *   - topics = "search-sync"：订阅的 Topic 名称
 *   - groupId = "hlaia-nav"：消费者组 ID
 *     同一消费者组中的消费者不会重复消费同一条消息（负载均衡）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final BookmarkMapper bookmarkMapper;
    private final FolderMapper folderMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final JsonMapper jsonMapper;

    /**
     * 消费搜索同步消息
     *
     * 消息格式：{"action":"CREATE","type":"bookmark","id":123}
     *
     * 处理逻辑：
     *   CREATE/UPDATE → 从 MySQL 查询最新数据 → 转为 Document → 写入 ES
     *   DELETE → 从 ES 中删除对应文档
     *
     * 为什么 CREATE 和 UPDATE 都是查询 MySQL 再写入 ES？
     *   因为 ES 的 save 方法是"upsert"语义（存在则更新，不存在则创建），
     *   所以无论是新增还是更新，都统一走"查 MySQL → 写 ES"的流程。
     *   这样还能保证 ES 中的数据始终和 MySQL 一致。
     *
     * @param message Kafka 消息内容
     */
    @KafkaListener(topics = "search-sync", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = jsonMapper.readTree(message);
            String action = node.get("action").asText();
            String type = node.get("type").asText();
            Long id = node.get("id").asLong();

            switch (type) {
                case "bookmark":
                    syncBookmark(action, id);
                    break;
                case "folder":
                    syncFolder(action, id);
                    break;
                default:
                    log.warn("Unknown sync type: {}", type);
            }
        } catch (Exception e) {
            // 同步失败只记录日志，不抛异常（避免 Kafka 无限重试）
            // 生产环境应该把失败消息发到死信队列（DLT），人工处理
            log.error("Failed to process search sync: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步书签到 ES
     *
     * 核心思路：
     *   CREATE/UPDATE → 从 MySQL 查询 Bookmark → 转为 BookmarkDocument → save 到 ES
     *   DELETE → 直接从 ES 删除
     *
     * 为什么要从 MySQL 查询而不是直接用消息中的数据？
     *   因为消息中只有 ID，没有完整数据。而且即使消息中有数据，
     *   在消息发送和消费之间，MySQL 中的数据可能已经被其他操作修改了。
     *   所以始终以 MySQL 为准（MySQL 是"single source of truth"）。
     */
    private void syncBookmark(String action, Long id) {
        if ("DELETE".equals(action)) {
            // 从 ES 中删除文档
            // ElasticsearchOperations.delete() 需要 ID 和文档类
            elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
            log.info("Deleted bookmark {} from ES", id);
        } else {
            // CREATE 或 UPDATE：从 MySQL 查最新数据，写入 ES
            Bookmark bookmark = bookmarkMapper.selectById(id);
            if (bookmark != null) {
                // Entity → Document 转换
                BookmarkDocument doc = new BookmarkDocument();
                doc.setId(bookmark.getId());
                doc.setUserId(bookmark.getUserId());
                doc.setFolderId(bookmark.getFolderId());
                doc.setTitle(bookmark.getTitle());
                doc.setUrl(bookmark.getUrl());
                doc.setDescription(bookmark.getDescription());
                doc.setIconUrl(bookmark.getIconUrl());
                doc.setCreatedAt(bookmark.getCreatedAt());
                doc.setUpdatedAt(bookmark.getUpdatedAt());

                // save() 是 upsert 语义：文档存在则更新，不存在则创建
                elasticsearchOperations.save(doc);
                log.info("Synced bookmark {} to ES (action={})", id, action);
            } else {
                // MySQL 中已经查不到这条数据了（可能被其他操作删除了）
                // 从 ES 中也删掉，保持一致
                elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
                log.info("Bookmark {} not found in MySQL, removed from ES", id);
            }
        }
    }

    /**
     * 同步文件夹到 ES（逻辑和 syncBookmark 完全一样，只是数据类型不同）
     */
    private void syncFolder(String action, Long id) {
        if ("DELETE".equals(action)) {
            elasticsearchOperations.delete(id.toString(), FolderDocument.class);
            log.info("Deleted folder {} from ES", id);
        } else {
            Folder folder = folderMapper.selectById(id);
            if (folder != null) {
                FolderDocument doc = new FolderDocument();
                doc.setId(folder.getId());
                doc.setUserId(folder.getUserId());
                doc.setParentId(folder.getParentId());
                doc.setName(folder.getName());
                doc.setIcon(folder.getIcon());
                doc.setCreatedAt(folder.getCreatedAt());
                doc.setUpdatedAt(folder.getUpdatedAt());

                elasticsearchOperations.save(doc);
                log.info("Synced folder {} to ES (action={})", id, action);
            } else {
                elasticsearchOperations.delete(id.toString(), FolderDocument.class);
                log.info("Folder {} not found in MySQL, removed from ES", id);
            }
        }
    }
}
```

### 4.5 验证同步是否工作

1. 启动 Spring Boot 应用
2. 通过 API 创建一个书签
3. 访问 `http://192.168.8.6:9200/bookmark/_search` 查看 ES 中是否有数据

---

## Step 5: 搜索 API —— SearchService + SearchController

### 5.1 创建搜索响应 DTO

新建文件 `src/main/java/com/hlaia/dto/response/SearchResponse.java`：

```java
package com.hlaia.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 【搜索结果响应 DTO】
 *
 * 搜索结果和普通列表不同，它包含：
 *   1. 匹配的文档列表
 *   2. 每个文档的类型（书签还是文件夹）
 *   3. 分页信息（总数、当前页、每页大小）
 *   4. 高亮的文本片段
 */
@Data
public class SearchResponse {

    /** 搜索结果列表 */
    private List<SearchItem> items;

    /** 总匹配数（用于分页） */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /**
     * 单条搜索结果
     */
    @Data
    public static class SearchItem {
        /** 结果类型：bookmark 或 folder */
        private String type;

        /** 书签 ID 或文件夹 ID */
        private Long id;

        /** 标题（书签的 title 或文件夹的 name） */
        private String title;

        /** URL（仅书签有） */
        private String url;

        /** 描述（仅书签有） */
        private String description;

        /** 图标 */
        private String icon;

        /** 所属文件夹 ID（仅书签有） */
        private Long folderId;

        /**
         * 高亮片段列表
         * ES 返回的"高亮"是指：匹配的关键词被 HTML 标签包裹的文本片段。
         * 例如：搜索 "github"，高亮结果可能是 "<em>GitHub</em> 代码托管平台"
         * 前端可以用 v-html 渲染高亮标签，实现关键词高亮效果。
         */
        private List<String> highlights;
    }
}
```

### 5.2 创建 SearchService

新建文件 `src/main/java/com/hlaia/service/SearchService.java`：

```java
package com.hlaia.service;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import com.hlaia.dto.response.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 【搜索业务逻辑层】—— 处理全文搜索的核心逻辑
 *
 * ElasticsearchOperations 的两种查询方式：
 *   1. NativeQuery（本类使用）：类似 SQL，用 Java 对象构建查询条件，灵活但需要了解 ES 查询语法
 *   2. CriteriaQuery：更高级的抽象，类似 MyBatis-Plus 的 QueryWrapper，但功能较少
 *
 * 搜索结果的分页：
 *   ES 的分页和 MySQL 类似，通过 from（偏移量）和 size（每页大小）控制。
 *   from = (page - 1) * size，例如第 1 页 from=0，第 2 页 from=20（size=20 时）
 *
 * 搜索结果的高亮：
 *   ES 的 Highlight 功能会在搜索结果中返回包含匹配关键词的文本片段，
 *   并用 <em> 标签包裹匹配的关键词。前端用 v-html 渲染就能看到高亮效果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 全文搜索 —— 搜索书签和文件夹
     *
     * 搜索策略：
     *   分别搜索书签索引和文件夹索引，合并结果返回。
     *   为什么不合并成一个索引？
     *   因为书签和文件夹的字段不同（书签有 url/description，文件夹没有），
     *   分开索引更清晰，也方便后续独立优化。
     *
     * @param userId  当前用户 ID（数据隔离）
     * @param keyword 搜索关键词
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     * @return 搜索结果（包含书签和文件夹的混合列表）
     */
    public SearchResponse search(Long userId, String keyword, int page, int size) {
        List<SearchResponse.SearchItem> allItems = new ArrayList<>();
        long totalBookmarks = 0;
        long totalFolders = 0;

        // ============ 搜索书签 ============
        // 构建高亮配置：哪些字段需要高亮，高亮标签用什么
        // HighlightParameters 定义高亮行为：
        //   withPreTags("<em>") / withPostTags("</em>") —— 高亮标签
        //     ES 默认用 <em> 标签，也可以自定义，如 <mark>
        //   withNumberOfFragments(3) —— 最多返回 3 个高亮片段
        //   withFragmentSize(100) —— 每个片段最多 100 个字符
        HighlightParameters bookmarkHighlightParams = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withNumberOfFragments(3)
                .withFragmentSize(100)
                .build();

        // HighlightField 定义哪些字段需要高亮
        // title、url、description 是书签的可搜索字段，都需要高亮
        List<HighlightField> bookmarkHighlightFields = List.of(
                new HighlightField("title"),
                new HighlightField("url"),
                new HighlightField("description")
        );

        Highlight bookmarkHighlight = new Highlight(bookmarkHighlightParams, bookmarkHighlightFields);

        // NativeQuery 是 Spring Data ES 的查询构建器
        // withQuery(q -> q.bool(b -> ...)) —— 构建 bool 查询（组合多个条件）
        //   bool 查询是 ES 最常用的查询类型，包含：
        //   - must：必须匹配（类似 SQL 的 AND）
        //   - should：可选匹配（类似 SQL 的 OR）
        //   - filter：过滤条件（不参与评分，用于精确匹配）
        //
        // 本查询的含义：
        //   must: 多字段匹配 keyword（搜 title、url、description）
        //   filter: 精确匹配 userId（数据隔离，不影响搜索评分）
        //
        // 为什么 userId 用 filter 而不是 must？
        //   filter 不参与"相关性评分"（_score）。
        //   相关性评分决定了搜索结果的排序顺序——越匹配的排越前。
        //   userId 是固定值（当前用户），不是搜索关键词，不应该影响评分。
        //   用 filter 可以提升查询性能（ES 会缓存 filter 结果）。
        NativeQuery bookmarkQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("title", "url", "description")
                        ))
                        .filter(f -> f.term(t -> t
                                .field("userId")
                                .value(userId)
                        ))
                ))
                .withHighlightQuery(new HighlightQuery(bookmarkHighlight, BookmarkDocument.class))
                .withPageable(org.springframework.data.domain.PageRequest.of(page - 1, size))
                .build();

        SearchHits<BookmarkDocument> bookmarkHits = elasticsearchOperations.search(bookmarkQuery, BookmarkDocument.class);
        totalBookmarks = bookmarkHits.getTotalHits();

        // 将 ES 的搜索结果转换为我们的 DTO
        // SearchHit 是 Spring Data ES 的搜索结果封装，包含：
        //   - getContent()：文档内容
        //   -getHighlightFields()：高亮片段
        for (SearchHit<BookmarkDocument> hit : bookmarkHits.getSearchHits()) {
            BookmarkDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("bookmark");
            item.setId(doc.getId());
            item.setTitle(doc.getTitle());
            item.setUrl(doc.getUrl());
            item.setDescription(doc.getDescription());
            item.setIcon(doc.getIconUrl());
            item.setFolderId(doc.getFolderId());
            // 获取高亮片段
            item.setHighlights(hit.getHighlightFields().get("title"));
            // 如果 title 没有高亮，尝试 url 和 description 的高亮
            if (item.getHighlights() == null || item.getHighlights().isEmpty()) {
                item.setHighlights(hit.getHighlightFields().get("url"));
            }
            if (item.getHighlights() == null || item.getHighlights().isEmpty()) {
                item.setHighlights(hit.getHighlightFields().get("description"));
            }
            if (item.getHighlights() == null) {
                item.setHighlights(List.of());
            }
            allItems.add(item);
        }

        // ============ 搜索文件夹 ============
        // 逻辑和书签类似，只是字段更少（只有 name 需要搜索和高亮）
        HighlightParameters folderHighlightParams = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withNumberOfFragments(3)
                .withFragmentSize(100)
                .build();

        List<HighlightField> folderHighlightFields = List.of(new HighlightField("name"));
        Highlight folderHighlight = new Highlight(folderHighlightParams, folderHighlightFields);

        NativeQuery folderQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("name")
                        ))
                        .filter(f -> f.term(t -> t
                                .field("userId")
                                .value(userId)
                        ))
                ))
                .withHighlightQuery(new HighlightQuery(folderHighlight, FolderDocument.class))
                .withPageable(org.springframework.data.domain.PageRequest.of(page - 1, size))
                .build();

        SearchHits<FolderDocument> folderHits = elasticsearchOperations.search(folderQuery, FolderDocument.class);
        totalFolders = folderHits.getTotalHits();

        for (SearchHit<FolderDocument> hit : folderHits.getSearchHits()) {
            FolderDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("folder");
            item.setId(doc.getId());
            item.setTitle(doc.getName());
            item.setIcon(doc.getIcon());
            item.setHighlights(hit.getHighlightFields().get("name"));
            if (item.getHighlights() == null) {
                item.setHighlights(List.of());
            }
            allItems.add(item);
        }

        // ============ 构建响应 ============
        SearchResponse response = new SearchResponse();
        response.setItems(allItems);
        response.setTotal(totalBookmarks + totalFolders);
        response.setPage(page);
        response.setSize(size);
        return response;
    }

    /**
     * 搜索建议（Autocomplete）
     *
     * 搜索建议和全文搜索的区别：
     *   全文搜索：用户输入完关键词后点击"搜索"，返回完整结果列表
     *   搜索建议：用户正在输入时实时返回匹配的标题，显示在下拉列表中
     *
     * 实现方式：
     *   使用 ES 的 multi_match 查询，但只返回少量结果（最多 10 条），
     *   且只返回 title/name 字段（不需要完整数据）。
     *
     * @param userId  当前用户 ID
     * @param keyword 用户正在输入的关键词（通常比较短）
     * @return 建议列表（最多 10 条）
     */
    public List<SearchResponse.SearchItem> suggest(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        List<SearchResponse.SearchItem> suggestions = new ArrayList<>();

        // 搜索书签标题
        NativeQuery bookmarkQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("title", "url")
                        ))
                        .filter(f -> f.term(t -> t
                                .field("userId")
                                .value(userId)
                        ))
                ))
                .withMaxResults(5)  // 书签最多返回 5 条建议
                .build();

        SearchHits<BookmarkDocument> bookmarkHits = elasticsearchOperations.search(bookmarkQuery, BookmarkDocument.class);
        for (SearchHit<BookmarkDocument> hit : bookmarkHits.getSearchHits()) {
            BookmarkDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("bookmark");
            item.setId(doc.getId());
            item.setTitle(doc.getTitle());
            item.setUrl(doc.getUrl());
            item.setIcon(doc.getIconUrl());
            item.setFolderId(doc.getFolderId());
            suggestions.add(item);
        }

        // 搜索文件夹名称
        NativeQuery folderQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("name")
                        ))
                        .filter(f -> f.term(t -> t
                                .field("userId")
                                .value(userId)
                        ))
                ))
                .withMaxResults(5)  // 文件夹最多返回 5 条建议
                .build();

        SearchHits<FolderDocument> folderHits = elasticsearchOperations.search(folderQuery, FolderDocument.class);
        for (SearchHit<FolderDocument> hit : folderHits.getSearchHits()) {
            FolderDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("folder");
            item.setId(doc.getId());
            item.setTitle(doc.getName());
            item.setIcon(doc.getIcon());
            suggestions.add(item);
        }

        return suggestions;
    }
}
```

### 5.3 创建 SearchController

新建文件 `src/main/java/com/hlaia/controller/SearchController.java`：

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.response.SearchResponse;
import com.hlaia.service.SearchService;
import com.hlaia.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 【搜索 API 控制器】—— 提供全文搜索和搜索建议接口
 *
 * API 设计：
 *   GET /api/search?keyword=xxx&page=1&size=20  —— 全文搜索
 *   GET /api/search/suggest?keyword=xxx          —— 搜索建议
 *
 * 搜索接口的 RESTful 设计：
 *   - 使用 GET 方法（搜索是读操作，不修改数据）
 *   - keyword 作为查询参数（?keyword=xxx），不是路径参数
 *   - page 和 size 也是查询参数，有默认值
 *
 * 注意：搜索接口需要用户登录（需要 userId 做数据隔离），
 * 所以不需要在 SecurityConfig 中额外配置（默认就需要认证）。
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 全文搜索
     *
     * 请求示例：GET /api/search?keyword=github&page=1&size=20
     *
     * @param keyword 搜索关键词
     * @param page    页码（默认第 1 页）
     * @param size    每页大小（默认 20 条）
     * @param token   JWT Token（从 Authorization 头部获取）
     * @return 搜索结果（包含书签和文件夹）
     */
    @GetMapping
    public Result<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String token) {
        // 从 JWT Token 中解析用户 ID
        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        SearchResponse result = searchService.search(userId, keyword, page, size);
        return Result.success(result);
    }

    /**
     * 搜索建议（Autocomplete）
     *
     * 请求示例：GET /api/search/suggest?keyword=git
     *
     * 用途：用户在搜索框中输入时，前端实时调用此接口获取匹配建议，
     * 在搜索框下方以下拉列表的形式展示。
     *
     * @param keyword 用户正在输入的关键词
     * @param token   JWT Token
     * @return 建议列表（最多 10 条）
     */
    @GetMapping("/suggest")
    public Result<List<SearchResponse.SearchItem>> suggest(
            @RequestParam String keyword,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtTokenProvider.getUserIdFromToken(token.replace("Bearer ", ""));
        List<SearchResponse.SearchItem> suggestions = searchService.suggest(userId, keyword);
        return Result.success(suggestions);
    }
}
```

> **注意**：获取 userId 的方式需要根据你的 SecurityConfig 来调整。
> 检查你的项目中其他 Controller 是如何获取当前用户 ID 的（可能是通过 `@AuthenticationPrincipal` 或自定义注解），
> 保持一致的写法。

### 5.4 编译验证

```bash
./mvnw compile
```

确保代码编译通过，没有错误。如果有编译错误，根据错误信息修复（常见的可能是 import 路径不对）。

---

## Step 6: 验证与测试

### 6.1 手动测试数据同步

1. 启动 Spring Boot 应用
2. 通过 API 创建一个书签
3. 访问 ES 查看数据：`curl http://192.168.8.6:9200/bookmark/_search?pretty`
4. 你应该能看到刚创建的书签文档

### 6.2 手动测试搜索

```bash
# 搜索关键词 "github"
curl "http://localhost:8080/api/search?keyword=github" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 搜索建议
curl "http://localhost:8080/api/search/suggest?keyword=git" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 6.3 单元测试

后续可以添加单元测试，使用 `@DataElasticsearchTest` 注解来测试搜索逻辑。

---

## 总结

你完成了以下工作：

| 步骤 | 创建/修改的文件 | 学到的核心概念 |
|------|----------------|--------------|
| Step 1 | docker-compose.yml, application-*.yml | ES Docker 部署、Spring Boot ES 配置 |
| Step 2 | pom.xml, ElasticsearchConfig.java | Spring Data ES 依赖、自动配置 |
| Step 3 | BookmarkDocument.java, FolderDocument.java | ES 文档模型、@Document/@Field 注解、分词器 |
| Step 4 | KafkaProducer.java, SearchSyncConsumer.java, BookmarkService.java, FolderService.java | Kafka 异步同步、事件驱动架构 |
| Step 5 | SearchService.java, SearchController.java, SearchResponse.java | ES 查询（NativeQuery）、bool 查询、高亮、分页 |
| Step 6 | 验证测试 | 端到端测试 |

接下来我会在前端实现搜索 UI 组件。
