# Research: Spring Data Elasticsearch Compatibility with Spring Boot 4.x

- **Query**: Spring Data Elasticsearch compatibility with Spring Boot 4.0.5 (Java 25), MySQL-to-ES sync patterns
- **Scope**: Mixed (external documentation + internal codebase analysis)
- **Date**: 2026-04-24

## Findings

### 1. Version Compatibility Matrix

| Component | Version | Source |
|---|---|---|
| Spring Boot | 4.0.5 | Project `pom.xml` line 8 |
| Spring Data BOM | 2025.1.4 (managed by Boot 4.0.5) | Spring Boot 4.0.5 BOM (`spring-data-bom.version`) |
| Spring Data Elasticsearch | **6.0.4** (managed by Spring Data BOM 2025.1.4) | Maven Central `spring-data-bom-2025.1.4.pom` |
| Elasticsearch Java Client | **9.2.6** (managed by Boot 4.0.5) | Spring Boot 4.0.5 BOM (`elasticsearch-client.version`) |
| Elasticsearch Server Required | **9.x** | ES Java Client 9.x is compatible with ES Server 9.x |
| Spring Framework | 7.0.6 (managed by Boot 4.0.5) | Spring Boot 4.0.5 BOM |

**Key conclusion**: Spring Boot 4.0.5 manages Spring Data Elasticsearch 6.0.4 via its BOM. No need to specify the Spring Data ES version manually.

### 2. Spring Boot 4.x Starter Structure (NEW)

Spring Boot 4.x introduced a split in Elasticsearch starters. There are now TWO distinct starters:

#### Option A: `spring-boot-starter-data-elasticsearch` (Spring Data ES abstraction)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

Transitive dependencies (verified from Maven Central POM):
- `spring-boot-starter` (core Boot starter)
- `spring-boot-data-elasticsearch` (Boot auto-config for Spring Data ES)
- `spring-boot-elasticsearch` (Boot auto-config for ES client)
- `spring-boot-starter-jackson` (JSON serialization)

This starter provides: `ElasticsearchOperations`, `ElasticsearchRestTemplate`, repository abstractions (`ElasticsearchRepository`), `@Document` annotation, and full Spring Data ES features.

#### Option B: `spring-boot-starter-elasticsearch` (low-level client only)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-elasticsearch</artifactId>
</dependency>
```

Transitive dependencies:
- `spring-boot-starter`
- `spring-boot-elasticsearch`
- `elasticsearch-java` (9.2.6)

This starter provides only the raw `ElasticsearchClient` / `Rest5Client` without Spring Data abstractions.

#### Recommendation

Use **`spring-boot-starter-data-elasticsearch`** for full-text search in this project. It provides `ElasticsearchOperations` for programmatic queries and repository support.

### 3. Spring Data ES 6.0 Breaking Changes and Migration Notes

From the source code of `ElasticsearchConfiguration.java` (Spring Data ES 6.0.0):

1. **Rest5Client replaces RestClient**: Version 6.0 uses the new `Rest5Client` from Elasticsearch 9. The old `RestClient`-based implementation is still available as `ElasticsearchLegacyRestClientConfiguration` but is deprecated.

2. **Elasticsearch server version**: Requires Elasticsearch **9.x** server. The `elasticsearch-java` client 9.2.x communicates with ES server 9.x.

3. **Spring Data Commons 4.0**: Spring Data ES 6.0 is built on Spring Data Commons 4.0 (aligned with Spring Data release train 2025.1.x).

4. **New auto-configuration artifacts**: Spring Boot 4.x split the ES auto-config into separate modules (`spring-boot-data-elasticsearch`, `spring-boot-elasticsearch`), which is why using the starter is important -- it pulls in both.

### 4. Elasticsearch Server Deployment Consideration

The project deploys on a fnNAS (fly牛 NAS) at 192.168.8.6 with Docker on an existing `app-network`. Elasticsearch 9.x server would need to be added as a new Docker container. Key points:

- ES 9.x requires significant memory (recommend at least 1-2GB heap for a small app)
- ES 9.x Docker image: `docker.elastic.co/elasticsearch/elasticsearch:9.2.6` (or matching version)
- Must join `app-network` for inter-container communication
- No ES container exists in current `docker-compose.yml`

### 5. MySQL-to-Elasticsearch Data Sync Patterns

#### Pattern A: Application-Level Dual-Write

Write to MySQL and ES simultaneously in the service layer.

```
Service Method:
  1. mybatisMapper.insert(bookmark)     // write MySQL
  2. elasticsearchOperations.save(doc)  // write ES
```

- **Pros**: Simple, no extra infrastructure, real-time consistency
- **Cons**: Tight coupling, if ES write fails the two stores diverge; no historical backfill
- **Best for**: Very small apps where data volume is low and occasional inconsistency is tolerable

#### Pattern B: Kafka-Based Async Sync (RECOMMENDED for this project)

Application publishes events to Kafka after MySQL write; a consumer reads events and updates ES.

```
Producer (Service):
  1. mybatisMapper.insert(bookmark)
  2. kafkaProducer.send("bookmark-sync", bookmarkJson)

Consumer (ES Sync):
  1. @KafkaListener(topics = "bookmark-sync")
  2. elasticsearchOperations.save(doc)
```

- **Pros**: Decoupled, fault-tolerant (Kafka persists messages), natural fit since the project ALREADY uses Kafka
- **Cons**: Slight delay (eventual consistency), need to handle consumer failures/retries
- **Why it fits this project**:
  - Kafka infrastructure is already deployed (192.168.8.6:9092)
  - `KafkaProducer` class already exists at `src/main/java/com/hlaia/kafka/KafkaProducer.java`
  - Consumer pattern is already established (`IconFetchConsumer`, `StagingCleanupConsumer`, `OperationLogConsumer`)
  - Just need to add a new topic (e.g., `bookmark-es-sync`) and a new consumer

#### Pattern C: Logstash JDBC Input Plugin

Logstash periodically queries MySQL and pipes data into ES.

- **Pros**: No code changes in the application
- **Cons**: Polling-based (not real-time), heavy infrastructure (JVM process), complex config, hard to handle deletes
- **Not recommended**: Overkill for this project size, adds a whole new service to deploy

#### Pattern D: Debezium / Canal Binlog Sync

Read MySQL binlog in real-time and stream changes to Kafka/ES.

- **Pros**: True real-time, zero application code, captures all changes including manual DB edits
- **Cons**: Complex setup (Debezium Connect cluster or Canal Server), binlog must be enabled, operational overhead
- **Not recommended**: Too heavy for a navigation bar app; the project has at most 3-4 tables that need indexing

### 6. Recommended Sync Pattern for This Project

**Kafka-based async sync (Pattern B)** is the best fit because:

1. **Infrastructure exists**: Kafka is already deployed and the project has `spring-boot-starter-kafka`
2. **Code pattern exists**: `KafkaProducer` and multiple `@KafkaListener` consumers are already implemented
3. **Minimal new code**: Add a new topic + one consumer class
4. **Eventual consistency is acceptable**: A navigation bar is not a financial system; a few hundred ms delay in search indexing is fine
5. **Extensible**: Can easily add index rebuilds, batch re-indexing via the same Kafka topic

### Files Found

| File Path | Description |
|---|---|
| `pom.xml` | Project POM with Spring Boot 4.0.5 parent, Kafka starter, Redis starter |
| `docker-compose.yml` | Current Docker deployment (no ES container yet) |
| `src/main/java/com/hlaia/kafka/KafkaProducer.java` | Existing Kafka producer with 3 topics |
| `src/main/java/com/hlaia/kafka/IconFetchConsumer.java` | Existing Kafka consumer pattern |
| `src/main/java/com/hlaia/kafka/StagingCleanupConsumer.java` | Existing Kafka consumer pattern |
| `src/main/java/com/hlaia/kafka/OperationLogConsumer.java` | Existing Kafka consumer pattern |
| `src/main/resources/application-dev.yml` | Dev config with Kafka bootstrap-servers: 192.168.8.6:9092 |
| `src/main/resources/application-prod.yml` | Prod config with Kafka container name |

### Code Patterns

**Kafka Producer Pattern** (`src/main/java/com/hlaia/kafka/KafkaProducer.java`):
- Uses `KafkaTemplate<String, String>` with JSON string payloads
- Key is resource ID (Long.toString()), value is JSON with relevant fields
- Async send with `kafkaTemplate.send(topic, key, value)`

**Kafka Consumer Pattern** (from existing consumers like `IconFetchConsumer`):
- Uses `@KafkaListener(topics = "...", groupId = "...")`
- Deserializes JSON string to extract parameters
- Calls service layer to perform work

### External References

- [Spring Data Elasticsearch 6.0 Reference](https://docs.spring.io/spring-data/elasticsearch/reference/) -- current GA docs for the version managed by Boot 4.0.5
- [Spring Data ES 6.0.0 Source](https://github.com/spring-projects/spring-data-elasticsearch/tree/6.0.0) -- confirms elasticsearch-java 9.2.1 client dependency
- [Spring Boot 4.0.5 BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.5/spring-boot-dependencies-4.0.5.pom) -- manages elasticsearch-client 9.2.6, spring-data-bom 2025.1.4
- [Spring Data BOM 2025.1.4](https://repo1.maven.org/maven2/org/springframework/data/spring-data-bom/2025.1.4/spring-data-bom-2025.1.4.pom) -- manages spring-data-elasticsearch 6.0.4
- [Elasticsearch 9.x Java Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/9.0/index.html) -- new Rest5Client used by Spring Data ES 6.0

### Related Specs

- `.trellis/spec/backend/index.md` -- backend spec index
- `.trellis/spec/backend/database-guidelines.md` -- database patterns
- `docs/superpowers/specs/2026-04-16-hlaia-navigation-bar-design.md` -- full system design doc

## Caveats / Not Found

1. **ctx7 quota exceeded**: Could not fetch library docs via ctx7. All version data was obtained directly from Maven Central POM files and Spring API endpoints.
2. **ES 9.x server compatibility**: The ES Java Client 9.x is designed for ES server 9.x. Exact backward compatibility with ES 8.x is not guaranteed by the client library. An ES 9.x server instance must be deployed.
3. **Docker resource impact**: Elasticsearch 9.x is memory-intensive. The fnNAS deployment should be evaluated for available RAM before adding ES.
4. **Spring Data ES 6.0 migration docs**: The full "what's new" and "migration" pages at docs.spring.io returned 404 (likely Antora single-page app issue). The breaking change about Rest5Client was confirmed from source code.
5. **No existing search infrastructure**: The project currently has zero search-related code, config, or Docker services. Everything (ES server, Spring Data ES dependency, index definitions, sync logic) would be greenfield.
