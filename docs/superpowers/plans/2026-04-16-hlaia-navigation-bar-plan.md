# HLAIANavigationBar V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete bookmark navigation system with Java Spring Boot backend, Vue 3 frontend, and Chromium extension.

**Architecture:** Monorepo with backend at root (Spring Boot), frontend/ and extension/ as sibling directories. Backend-first development: RESTful API with JWT auth, then frontend consuming the API, then extension for quick-save.

**Tech Stack:** Spring Boot 4.0.5 / Java 17 / MyBatis-Plus / MySQL 8 / Redis 7 / Kafka / Flyway / Knife4j / Vue 3 / Element Plus / Pinia / Manifest V3

**Spec:** `docs/superpowers/specs/2026-04-16-hlaia-navigation-bar-design.md`

---

## File Structure

### Backend (root level, existing Spring Boot project)

| File | Responsibility |
|---|---|
| `pom.xml` | Maven dependencies |
| `src/main/resources/application.yml` | Main config |
| `src/main/resources/application-dev.yml` | Dev profile overrides |
| `src/main/resources/db/migration/V1__init_schema.sql` | Flyway: create all tables |
| `src/main/resources/db/migration/V2__init_admin.sql` | Flyway: insert default admin |
| `src/main/java/com/hlaia/common/Result.java` | Unified API response wrapper |
| `src/main/java/com/hlaia/common/ErrorCode.java` | Error code enum |
| `src/main/java/com/hlaia/common/GlobalExceptionHandler.java` | Global exception handler |
| `src/main/java/com/hlaia/common/BusinessException.java` | Custom business exception |
| `src/main/java/com/hlaia/entity/User.java` | User entity |
| `src/main/java/com/hlaia/entity/Folder.java` | Folder entity |
| `src/main/java/com/hlaia/entity/Bookmark.java` | Bookmark entity |
| `src/main/java/com/hlaia/entity/StagingItem.java` | Staging item entity |
| `src/main/java/com/hlaia/mapper/UserMapper.java` | User MyBatis-Plus mapper |
| `src/main/java/com/hlaia/mapper/FolderMapper.java` | Folder mapper |
| `src/main/java/com/hlaia/mapper/BookmarkMapper.java` | Bookmark mapper |
| `src/main/java/com/hlaia/mapper/StagingItemMapper.java` | Staging item mapper |
| `src/main/java/com/hlaia/dto/request/LoginRequest.java` | Login request DTO |
| `src/main/java/com/hlaia/dto/request/RegisterRequest.java` | Register request DTO |
| `src/main/java/com/hlaia/dto/request/FolderCreateRequest.java` | Create folder DTO |
| `src/main/java/com/hlaia/dto/request/FolderSortRequest.java` | Sort folders DTO |
| `src/main/java/com/hlaia/dto/request/FolderMoveRequest.java` | Move folder DTO |
| `src/main/java/com/hlaia/dto/request/BookmarkCreateRequest.java` | Create bookmark DTO |
| `src/main/java/com/hlaia/dto/request/BookmarkSortRequest.java` | Sort bookmarks DTO |
| `src/main/java/com/hlaia/dto/request/BatchDeleteRequest.java` | Batch delete DTO |
| `src/main/java/com/hlaia/dto/request/BatchCopyRequest.java` | Batch copy DTO |
| `src/main/java/com/hlaia/dto/request/StagingCreateRequest.java` | Create staging item DTO |
| `src/main/java/com/hlaia/dto/request/StagingUpdateRequest.java` | Update staging expiry DTO |
| `src/main/java/com/hlaia/dto/request/MoveToFolderRequest.java` | Move staging to folder DTO |
| `src/main/java/com/hlaia/dto/response/AuthResponse.java` | Auth response (token) |
| `src/main/java/com/hlaia/dto/response/FolderTreeResponse.java` | Folder tree node DTO |
| `src/main/java/com/hlaia/dto/response/BookmarkResponse.java` | Bookmark response DTO |
| `src/main/java/com/hlaia/dto/response/StagingItemResponse.java` | Staging item response DTO |
| `src/main/java/com/hlaia/dto/response/UserResponse.java` | User list item for admin |
| `src/main/java/com/hlaia/security/JwtTokenProvider.java` | JWT sign/verify |
| `src/main/java/com/hlaia/security/JwtAuthFilter.java` | JWT filter for Security chain |
| `src/main/java/com/hlaia/security/UserDetailsServiceImpl.java` | Load user from DB |
| `src/main/java/com/hlaia/config/SecurityConfig.java` | Spring Security config |
| `src/main/java/com/hlaia/config/CorsConfig.java` | CORS (dev) |
| `src/main/java/com/hlaia/config/RedisConfig.java` | Redis serializer config |
| `src/main/java/com/hlaia/config/KafkaConfig.java` | Kafka producer/consumer config |
| `src/main/java/com/hlaia/config/SwaggerConfig.java` | Knife4j config |
| `src/main/java/com/hlaia/config/MyBatisPlusConfig.java` | MyBatis-Plus pagination config |
| `src/main/java/com/hlaia/annotation/RateLimit.java` | Rate limit annotation |
| `src/main/java/com/hlaia/aspect/RateLimitAspect.java` | Rate limit AOP aspect |
| `src/main/java/com/hlaia/aspect/OperationLogAspect.java` | Operation log AOP aspect |
| `src/main/java/com/hlaia/service/AuthService.java` | Auth business logic |
| `src/main/java/com/hlaia/service/FolderService.java` | Folder business logic |
| `src/main/java/com/hlaia/service/BookmarkService.java` | Bookmark business logic |
| `src/main/java/com/hlaia/service/StagingService.java` | Staging business logic |
| `src/main/java/com/hlaia/service/AdminService.java` | Admin business logic |
| `src/main/java/com/hlaia/controller/AuthController.java` | Auth endpoints |
| `src/main/java/com/hlaia/controller/FolderController.java` | Folder endpoints |
| `src/main/java/com/hlaia/controller/BookmarkController.java` | Bookmark endpoints |
| `src/main/java/com/hlaia/controller/StagingController.java` | Staging endpoints |
| `src/main/java/com/hlaia/controller/AdminController.java` | Admin endpoints |
| `src/main/java/com/hlaia/controller/ExtensionController.java` | Extension endpoints |
| `src/main/java/com/hlaia/kafka/IconFetchConsumer.java` | Kafka: fetch bookmark icon |
| `src/main/java/com/hlaia/kafka/StagingCleanupConsumer.java` | Kafka: clean expired staging |
| `src/main/java/com/hlaia/kafka/OperationLogConsumer.java` | Kafka: persist operation log |
| `src/main/java/com/hlaia/kafka/KafkaProducer.java` | Kafka message sender |
| `src/main/java/com/hlaia/entity/OperationLog.java` | Operation log entity |
| `src/main/java/com/hlaia/mapper/OperationLogMapper.java` | Operation log mapper |
| `src/main/java/com/hlaia/scheduled/StagingCleanupScheduler.java` | Cron: scan expiring items |
| `src/test/java/com/hlaia/service/AuthServiceTest.java` | Auth service unit tests |
| `src/test/java/com/hlaia/service/FolderServiceTest.java` | Folder service unit tests |
| `src/test/java/com/hlaia/service/BookmarkServiceTest.java` | Bookmark service unit tests |
| `src/test/java/com/hlaia/controller/AuthControllerTest.java` | Auth controller integration tests |
| `Dockerfile` | Backend Docker image |

### Frontend

| File | Responsibility |
|---|---|
| `frontend/package.json` | Dependencies |
| `frontend/vite.config.js` | Vite config with API proxy |
| `frontend/index.html` | Entry HTML |
| `frontend/src/main.js` | App bootstrap |
| `frontend/src/App.vue` | Root component |
| `frontend/src/router/index.js` | Route definitions |
| `frontend/src/utils/auth.js` | Token storage helpers |
| `frontend/src/api/request.js` | Axios instance + interceptors |
| `frontend/src/api/auth.js` | Auth API calls |
| `frontend/src/api/folder.js` | Folder API calls |
| `frontend/src/api/bookmark.js` | Bookmark API calls |
| `frontend/src/api/staging.js` | Staging API calls |
| `frontend/src/api/admin.js` | Admin API calls |
| `frontend/src/stores/auth.js` | Auth Pinia store |
| `frontend/src/stores/folder.js` | Folder Pinia store |
| `frontend/src/stores/bookmark.js` | Bookmark Pinia store |
| `frontend/src/views/LoginView.vue` | Login page |
| `frontend/src/views/RegisterView.vue` | Register page |
| `frontend/src/views/MainView.vue` | Main layout (left-right split) |
| `frontend/src/views/StagingView.vue` | Staging area page |
| `frontend/src/views/admin/UserListView.vue` | Admin user list |
| `frontend/src/views/admin/UserDetailView.vue` | Admin user detail |
| `frontend/src/components/FolderTree.vue` | Folder tree sidebar |
| `frontend/src/components/BookmarkGrid.vue` | Bookmark card grid |
| `frontend/src/components/BookmarkCard.vue` | Single bookmark card |
| `frontend/src/components/StagingList.vue` | Staging item list |
| `frontend/src/components/BatchToolbar.vue` | Batch action toolbar |
| `frontend/src/components/FolderBreadcrumb.vue` | Breadcrumb navigation |
| `frontend/Dockerfile` | Frontend Docker image |
| `frontend/nginx.conf` | Nginx reverse proxy config |

### Extension

| File | Responsibility |
|---|---|
| `extension/manifest.json` | Extension config (Manifest V3) |
| `extension/background.js` | Service Worker (context menus) |
| `extension/options/options.html` | Options page HTML |
| `extension/options/options.js` | Options page logic (login) |
| `extension/icons/icon16.png` | Extension icon 16x16 |
| `extension/icons/icon48.png` | Extension icon 48x48 |
| `extension/icons/icon128.png` | Extension icon 128x128 |

### Root

| File | Responsibility |
|---|---|
| `docker-compose.yml` | MySQL + Redis + Kafka + app containers |
| `.gitignore` | Updated gitignore |

---

## Phase 1: Infrastructure Setup

### Task 1: Docker Compose for Infrastructure Services

**Files:**
- Create: `docker-compose.yml`
- Modify: `.gitignore`

- [ ] **Step 1: Create docker-compose.yml with MySQL, Redis, Kafka**

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: hlaia-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: hlaia_nav
      MYSQL_USER: hlaia
      MYSQL_PASSWORD: hlaia123
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    command: --default-authentication-plugin=caching_sha2_password

  redis:
    image: redis:7-alpine
    container_name: hlaia-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: hlaia-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: hlaia-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

volumes:
  mysql-data:
  redis-data:
```

- [ ] **Step 2: Update .gitignore**

Append to existing `.gitignore`:
```
# Infrastructure
.superpowers/

# Frontend
frontend/node_modules/
frontend/dist/

# IDE
.idea/
*.iml

# Environment
.env
```

- [ ] **Step 3: Start infrastructure**

Run: `docker compose up -d`
Expected: mysql, redis, zookeeper, kafka containers running. Verify with `docker compose ps`.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml .gitignore
git commit -m "infra: add Docker Compose for MySQL, Redis, Kafka"
```

---

### Task 2: Backend Dependencies (pom.xml)

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update pom.xml with all required dependencies**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.5</version>
        <relativePath/>
    </parent>
    <groupId>com.hlaia</groupId>
    <artifactId>HLAIANavigationBar</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>HLAIANavigationBar</name>
    <description>HLAIANavigationBar</description>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.6</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.9</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Flyway -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Knife4j (Swagger) -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>4.5.0</version>
        </dependency>

        <!-- AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verify dependencies resolve**

Run: `./mvnw dependency:resolve -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add all backend dependencies"
```

---

### Task 3: Application Configuration

**Files:**
- Delete: `src/main/resources/application.properties`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`

- [ ] **Step 1: Delete old properties, create application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: HLAIANavigationBar
  profiles:
    active: dev
```

- [ ] **Step 2: Create application-dev.yml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hlaia_nav?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: hlaia
    password: hlaia123
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: localhost
      port: 6379

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: hlaia-nav
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest

  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

jwt:
  secret: hlaia-navigation-bar-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm
  access-token-expiration: 86400000
  refresh-token-expiration: 604800000

logging:
  level:
    com.hlaia: debug
```

- [ ] **Step 3: Verify app starts (will fail on DB - expected)**

Run: `./mvnw spring-boot:run`
Expected: Application starts, Flyway connects. May fail if MySQL not accessible — that's OK for now.

- [ ] **Step 4: Commit**

```bash
git rm src/main/resources/application.properties
git add src/main/resources/application.yml src/main/resources/application-dev.yml
git commit -m "config: add application YAML configs"
```

---

### Task 4: Flyway Database Migrations

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/resources/db/migration/V2__init_admin.sql`

- [ ] **Step 1: Create V1 — all tables**

```sql
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `password`   VARCHAR(255) NOT NULL,
    `email`      VARCHAR(100) DEFAULT NULL,
    `role`       ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=normal, 1=banned',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `folder` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `parent_id`  BIGINT       DEFAULT NULL COMMENT 'NULL means root level',
    `name`       VARCHAR(100) NOT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `icon`       VARCHAR(50)  DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`),
    CONSTRAINT `fk_folder_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_folder_parent` FOREIGN KEY (`parent_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `bookmark` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT        NOT NULL,
    `folder_id`   BIGINT        NOT NULL,
    `title`       VARCHAR(255)  NOT NULL,
    `url`         VARCHAR(2048) NOT NULL,
    `description` VARCHAR(500)  DEFAULT NULL,
    `icon_url`    VARCHAR(500)  DEFAULT NULL,
    `sort_order`  INT           NOT NULL DEFAULT 0,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_folder_id` (`folder_id`),
    CONSTRAINT `fk_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bookmark_folder` FOREIGN KEY (`folder_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `staging_item` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT        NOT NULL,
    `title`      VARCHAR(255)  NOT NULL,
    `url`        VARCHAR(2048) NOT NULL,
    `icon_url`   VARCHAR(500)  DEFAULT NULL,
    `expire_at`  DATETIME      NOT NULL,
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_at` (`expire_at`),
    CONSTRAINT `fk_staging_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       DEFAULT NULL,
    `action`     VARCHAR(50)  NOT NULL,
    `target`     VARCHAR(200) DEFAULT NULL,
    `detail`     TEXT         DEFAULT NULL,
    `ip`         VARCHAR(50)  DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Create V2 — placeholder (admin created by app)**

```sql
-- Admin user is created programmatically at application startup
-- to ensure the BCrypt hash is generated correctly.
```

We will create an `AdminInitializer.java` as a `CommandLineRunner` in Task 10 that creates the admin user if it doesn't exist, using the injected `PasswordEncoder`.

- [ ] **Step 3: Verify migration runs**

Run: `docker compose up -d` then `./mvnw spring-boot:run`
Expected: Flyway logs show `V1__init_schema.sql` and `V2__init_admin.sql` applied. App starts successfully.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/
git commit -m "db: add Flyway migration scripts for all tables"
```

---

## Phase 2: Backend Foundation

### Task 5: Common Module (Result, ErrorCode, Exceptions)

**Files:**
- Create: `src/main/java/com/hlaia/common/Result.java`
- Create: `src/main/java/com/hlaia/common/ErrorCode.java`
- Create: `src/main/java/com/hlaia/common/BusinessException.java`
- Create: `src/main/java/com/hlaia/common/GlobalExceptionHandler.java`

- [ ] **Step 1: Create Result.java — unified API response**

```java
package com.hlaia.common;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        return r;
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getMessage());
        return r;
    }
}
```

Note: Lombok dependency is already added in Task 2's pom.xml.

- [ ] **Step 1: Create ErrorCode.java**

```java
package com.hlaia.common;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // General
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error"),

    // Auth
    USER_EXISTS(1001, "Username already exists"),
    INVALID_CREDENTIALS(1002, "Invalid username or password"),
    TOKEN_EXPIRED(1003, "Token expired"),
    TOKEN_INVALID(1004, "Invalid token"),

    // Business
    FOLDER_NOT_FOUND(2001, "Folder not found"),
    BOOKMARK_NOT_FOUND(2002, "Bookmark not found"),
    STAGING_NOT_FOUND(2003, "Staging item not found"),
    USER_NOT_FOUND(2004, "User not found"),
    USER_BANNED(2005, "User is banned"),
    ACCESS_DENIED(2006, "Access denied"),
    RATE_LIMITED(2007, "Too many requests");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: Create BusinessException.java**

```java
package com.hlaia.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 3: Create Result.java**

```java
package com.hlaia.common;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}
```

- [ ] **Step 4: Create GlobalExceptionHandler.java**

```java
package com.hlaia.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return Result.error(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hlaia/common/
git commit -m "feat: add common module (Result, ErrorCode, BusinessException, GlobalExceptionHandler)"
```

---

### Task 6: Entity Classes

**Files:**
- Create: `src/main/java/com/hlaia/entity/User.java`
- Create: `src/main/java/com/hlaia/entity/Folder.java`
- Create: `src/main/java/com/hlaia/entity/Bookmark.java`
- Create: `src/main/java/com/hlaia/entity/StagingItem.java`
- Create: `src/main/java/com/hlaia/entity/OperationLog.java`

- [ ] **Step 1: Create User.java**

```java
package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create Folder.java**

```java
package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("folder")
public class Folder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private String icon;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create Bookmark.java**

```java
package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("bookmark")
public class Bookmark {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long folderId;
    private String title;
    private String url;
    private String description;
    private String iconUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Create StagingItem.java**

```java
package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("staging_item")
public class StagingItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String url;
    private String iconUrl;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Create OperationLog.java**

```java
package com.hlaia.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hlaia/entity/
git commit -m "feat: add entity classes (User, Folder, Bookmark, StagingItem, OperationLog)"
```

---

### Task 7: Mapper Interfaces + MyBatis-Plus Config

**Files:**
- Create: `src/main/java/com/hlaia/mapper/UserMapper.java`
- Create: `src/main/java/com/hlaia/mapper/FolderMapper.java`
- Create: `src/main/java/com/hlaia/mapper/BookmarkMapper.java`
- Create: `src/main/java/com/hlaia/mapper/StagingItemMapper.java`
- Create: `src/main/java/com/hlaia/mapper/OperationLogMapper.java`
- Create: `src/main/java/com/hlaia/config/MyBatisPlusConfig.java`

- [ ] **Step 1: Create all mapper interfaces**

`UserMapper.java`:
```java
package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

`FolderMapper.java`:
```java
package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.Folder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FolderMapper extends BaseMapper<Folder> {
}
```

`BookmarkMapper.java`:
```java
package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.Bookmark;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookmarkMapper extends BaseMapper<Bookmark> {
}
```

`StagingItemMapper.java`:
```java
package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.StagingItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StagingItemMapper extends BaseMapper<StagingItem> {
}
```

`OperationLogMapper.java`:
```java
package com.hlaia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hlaia.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
```

- [ ] **Step 2: Create MyBatisPlusConfig.java**

```java
package com.hlaia.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hlaia/mapper/ src/main/java/com/hlaia/config/MyBatisPlusConfig.java
git commit -m "feat: add MyBatis-Plus mapper interfaces and pagination config"
```

---

### Task 8: DTO Classes

**Files:**
- Create all DTOs in `src/main/java/com/hlaia/dto/request/` and `src/main/java/com/hlaia/dto/response/`

- [ ] **Step 1: Create request DTOs**

`LoginRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

`RegisterRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be 6-100 characters")
    private String password;
}
```

`FolderCreateRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FolderCreateRequest {
    private Long parentId;
    @NotBlank(message = "Folder name is required")
    @Size(max = 100, message = "Folder name must be under 100 characters")
    private String name;
    private String icon;
}
```

`FolderSortRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class FolderSortRequest {
    @NotNull(message = "Items are required")
    private List<SortItem> items;

    @Data
    public static class SortItem {
        @NotNull
        private Long id;
        @NotNull
        private Integer sortOrder;
    }
}
```

`FolderMoveRequest.java`:
```java
package com.hlaia.dto.request;

import lombok.Data;

@Data
public class FolderMoveRequest {
    private Long parentId;
}
```

`BookmarkCreateRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookmarkCreateRequest {
    @NotNull(message = "Folder ID is required")
    private Long folderId;
    @NotBlank(message = "Title is required")
    private String title;
    @NotBlank(message = "URL is required")
    private String url;
    private String description;
}
```

`BookmarkSortRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class BookmarkSortRequest {
    @NotNull(message = "Items are required")
    private List<SortItem> items;

    @Data
    public static class SortItem {
        @NotNull
        private Long id;
        @NotNull
        private Integer sortOrder;
    }
}
```

`BatchDeleteRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BatchDeleteRequest {
    @NotEmpty(message = "IDs are required")
    private List<Long> ids;
}
```

`BatchCopyRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BatchCopyRequest {
    @NotEmpty(message = "IDs are required")
    private List<Long> ids;
}
```

`StagingCreateRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StagingCreateRequest {
    @NotBlank(message = "Title is required")
    private String title;
    @NotBlank(message = "URL is required")
    private String url;
    private Integer expireMinutes;
}
```

`StagingUpdateRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StagingUpdateRequest {
    @NotNull(message = "Expire minutes is required")
    private Integer expireMinutes;
}
```

`MoveToFolderRequest.java`:
```java
package com.hlaia.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveToFolderRequest {
    @NotNull(message = "Folder ID is required")
    private Long folderId;
}
```

- [ ] **Step 2: Create response DTOs**

`AuthResponse.java`:
```java
package com.hlaia.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
}
```

`FolderTreeResponse.java`:
```java
package com.hlaia.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FolderTreeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private Integer sortOrder;
    private List<FolderTreeResponse> children;
    private Integer childFolderCount;
    private Integer bookmarkCount;
    private LocalDateTime createdAt;
}
```

`BookmarkResponse.java`:
```java
package com.hlaia.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookmarkResponse {
    private Long id;
    private Long folderId;
    private String title;
    private String url;
    private String description;
    private String iconUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`StagingItemResponse.java`:
```java
package com.hlaia.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StagingItemResponse {
    private Long id;
    private String title;
    private String url;
    private String iconUrl;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
```

`UserResponse.java`:
```java
package com.hlaia.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hlaia/dto/
git commit -m "feat: add all request and response DTOs"
```

---

## Phase 3: Security & Auth

### Task 9: Spring Security + JWT

**Files:**
- Create: `src/main/java/com/hlaia/security/JwtTokenProvider.java`
- Create: `src/main/java/com/hlaia/security/JwtAuthFilter.java`
- Create: `src/main/java/com/hlaia/security/UserDetailsServiceImpl.java`
- Create: `src/main/java/com/hlaia/config/SecurityConfig.java`
- Create: `src/main/java/com/hlaia/config/CorsConfig.java`
- Create: `src/main/java/com/hlaia/config/RedisConfig.java`

- [ ] **Step 1: Create JwtTokenProvider.java**

```java
package com.hlaia.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, accessTokenExpiration);
    }

    public String generateRefreshToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, refreshTokenExpiration);
    }

    private String buildToken(Long userId, String username, String role, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    public long getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 2: Create UserDetailsServiceImpl.java**

```java
package com.hlaia.security;

import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }

    public User getUserByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
```

- [ ] **Step 3: Create JwtAuthFilter.java**

```java
package com.hlaia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // Check if token is blacklisted
            String blacklistKey = "jwt:blacklist:" + token;
            Boolean blacklisted = redisTemplate.hasKey(blacklistKey);
            if (Boolean.TRUE.equals(blacklisted)) {
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId, null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 4: Create SecurityConfig.java**

```java
package com.hlaia.config;

import com.hlaia.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

- [ ] **Step 5: Create CorsConfig.java**

```java
package com.hlaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 6: Create RedisConfig.java**

```java
package com.hlaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

- [ ] **Step 7: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hlaia/security/ src/main/java/com/hlaia/config/SecurityConfig.java src/main/java/com/hlaia/config/CorsConfig.java src/main/java/com/hlaia/config/RedisConfig.java
git commit -m "feat: add Spring Security + JWT authentication system"
```

---

### Task 10: Auth Service + Controller

**Files:**
- Create: `src/main/java/com/hlaia/service/AuthService.java`
- Create: `src/main/java/com/hlaia/controller/AuthController.java`

- [ ] **Step 1: Create AuthService.java**

```java
package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.LoginRequest;
import com.hlaia.dto.request.RegisterRequest;
import com.hlaia.dto.response.AuthResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.UserMapper;
import com.hlaia.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    public AuthResponse register(RegisterRequest request) {
        // Check if username exists
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus(0);
        userMapper.insert(user);

        return generateAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        return generateAuthResponse(user);
    }

    public void logout(String token) {
        long expiration = jwtTokenProvider.getExpirationFromToken(token);
        if (expiration > 0) {
            redisTemplate.opsForValue().set(
                    "jwt:blacklist:" + token, "1", expiration, TimeUnit.MILLISECONDS);
        }
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        String blacklistKey = "jwt:blacklist:" + refreshToken;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Blacklist old refresh token
        long expiration = jwtTokenProvider.getExpirationFromToken(refreshToken);
        if (expiration > 0) {
            redisTemplate.opsForValue().set(blacklistKey, "1", expiration, TimeUnit.MILLISECONDS);
        }

        return generateAuthResponse(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
```

- [ ] **Step 2: Create AuthController.java**

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.LoginRequest;
import com.hlaia.dto.request.RegisterRequest;
import com.hlaia.dto.response.AuthResponse;
import com.hlaia.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT tokens")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and blacklist the token")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authService.logout(token);
        return Result.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public Result<AuthResponse> refresh(@RequestParam String refreshToken) {
        return Result.success(authService.refresh(refreshToken));
    }
}
```

- [ ] **Step 3: Test — verify app starts and auth endpoints exist**

Run: `./mvnw spring-boot:run`
Then in another terminal: `curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"testuser","password":"test123456"}'`
Expected: JSON response with access/refresh tokens

- [ ] **Step 4: Test login**

Run: `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"testuser","password":"test123456"}'`
Expected: JSON response with tokens

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hlaia/service/AuthService.java src/main/java/com/hlaia/controller/AuthController.java
git commit -m "feat: add authentication service and controller (register, login, logout, refresh)"
```

---

## Phase 4: Core Business Modules

### Task 11: Folder Service + Controller

**Files:**
- Create: `src/main/java/com/hlaia/service/FolderService.java`
- Create: `src/main/java/com/hlaia/controller/FolderController.java`

- [ ] **Step 1: Create FolderService.java**

```java
package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.FolderCreateRequest;
import com.hlaia.dto.request.FolderMoveRequest;
import com.hlaia.dto.request.FolderSortRequest;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderMapper folderMapper;
    private final BookmarkMapper bookmarkMapper;

    public List<FolderTreeResponse> getFolderTree(Long userId) {
        List<Folder> allFolders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .orderByAsc(Folder::getSortOrder));

        // Build tree
        Map<Long, FolderTreeResponse> map = allFolders.stream()
                .collect(Collectors.toMap(Folder::getId, this::toTreeResponse));

        // Count bookmarks per folder
        List<Bookmark> allBookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getUserId, userId));
        Map<Long, Long> bookmarkCounts = allBookmarks.stream()
                .collect(Collectors.groupingBy(Bookmark::getFolderId, Collectors.counting()));

        // Build tree structure
        List<FolderTreeResponse> roots = new ArrayList<>();
        for (Folder folder : allFolders) {
            FolderTreeResponse node = map.get(folder.getId());
            node.setBookmarkCount(bookmarkCounts.getOrDefault(folder.getId(), 0L).intValue());

            if (folder.getParentId() == null) {
                roots.add(node);
            } else {
                FolderTreeResponse parent = map.get(folder.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                }
            }
        }

        // Count child folders
        for (FolderTreeResponse node : map.values()) {
            if (node.getChildren() != null) {
                node.setChildFolderCount(node.getChildren().size());
            } else {
                node.setChildFolderCount(0);
            }
        }

        return roots;
    }

    @Transactional
    public FolderTreeResponse createFolder(Long userId, FolderCreateRequest request) {
        Folder folder = new Folder();
        folder.setUserId(userId);
        folder.setParentId(request.getParentId());
        folder.setName(request.getName());
        folder.setIcon(request.getIcon());

        // Set sort order to last
        Long count = folderMapper.selectCount(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .eq(request.getParentId() != null, Folder::getParentId, request.getParentId())
                        .isNull(request.getParentId() == null, Folder::getParentId));
        folder.setSortOrder(count.intValue());

        folderMapper.insert(folder);
        return toTreeResponse(folder);
    }

    @Transactional
    public FolderTreeResponse updateFolder(Long userId, Long folderId, FolderCreateRequest request) {
        Folder folder = getFolderForUser(userId, folderId);
        if (request.getName() != null) folder.setName(request.getName());
        if (request.getIcon() != null) folder.setIcon(request.getIcon());
        folderMapper.updateById(folder);
        return toTreeResponse(folder);
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        getFolderForUser(userId, folderId);
        // CASCADE in DB handles children and bookmarks
        folderMapper.deleteById(folderId);
    }

    @Transactional
    public void sortFolders(Long userId, FolderSortRequest request) {
        for (FolderSortRequest.SortItem item : request.getItems()) {
            Folder folder = getFolderForUser(userId, item.getId());
            folder.setSortOrder(item.getSortOrder());
            folderMapper.updateById(folder);
        }
    }

    @Transactional
    public void moveFolder(Long userId, Long folderId, FolderMoveRequest request) {
        Folder folder = getFolderForUser(userId, folderId);
        // Prevent moving into own descendant
        if (request.getParentId() != null) {
            if (isDescendant(folderId, request.getParentId(), userId)) {
                throw new BusinessException(400, "Cannot move folder into its own descendant");
            }
        }
        folder.setParentId(request.getParentId());
        folderMapper.updateById(folder);
    }

    private boolean isDescendant(Long ancestorId, Long candidateId, Long userId) {
        if (ancestorId.equals(candidateId)) return true;
        List<Folder> children = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getUserId, userId)
                        .eq(Folder::getParentId, ancestorId));
        for (Folder child : children) {
            if (isDescendant(child.getId(), candidateId, userId)) return true;
        }
        return false;
    }

    private Folder getFolderForUser(Long userId, Long folderId) {
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
        }
        return folder;
    }

    private FolderTreeResponse toTreeResponse(Folder folder) {
        FolderTreeResponse dto = new FolderTreeResponse();
        dto.setId(folder.getId());
        dto.setParentId(folder.getParentId());
        dto.setName(folder.getName());
        dto.setIcon(folder.getIcon());
        dto.setSortOrder(folder.getSortOrder());
        dto.setCreatedAt(folder.getCreatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: Create FolderController.java**

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.FolderCreateRequest;
import com.hlaia.dto.request.FolderMoveRequest;
import com.hlaia.dto.request.FolderSortRequest;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@Tag(name = "Folders", description = "Folder management APIs")
public class FolderController {

    private final FolderService folderService;

    @GetMapping("/tree")
    @Operation(summary = "Get current user's folder tree")
    public Result<List<FolderTreeResponse>> getTree(@AuthenticationPrincipal Long userId) {
        return Result.success(folderService.getFolderTree(userId));
    }

    @PostMapping
    @Operation(summary = "Create a folder")
    public Result<FolderTreeResponse> create(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody FolderCreateRequest request) {
        return Result.success(folderService.createFolder(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a folder")
    public Result<FolderTreeResponse> update(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody FolderCreateRequest request) {
        return Result.success(folderService.updateFolder(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a folder and all its children")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        folderService.deleteFolder(userId, id);
        return Result.success();
    }

    @PutMapping("/sort")
    @Operation(summary = "Batch update folder sort order")
    public Result<Void> sort(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody FolderSortRequest request) {
        folderService.sortFolders(userId, request);
        return Result.success();
    }

    @PutMapping("/{id}/move")
    @Operation(summary = "Move a folder to a new parent")
    public Result<Void> move(@AuthenticationPrincipal Long userId,
                              @PathVariable Long id,
                              @RequestBody FolderMoveRequest request) {
        folderService.moveFolder(userId, id, request);
        return Result.success();
    }
}
```

- [ ] **Step 3: Verify compilation and test**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

Then test: `curl -X GET http://localhost:8080/api/folders/tree -H "Authorization: Bearer <token>"`
Expected: `{"code":200,"message":"success","data":[]}`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hlaia/service/FolderService.java src/main/java/com/hlaia/controller/FolderController.java
git commit -m "feat: add folder service and controller (tree, CRUD, sort, move)"
```

---

### Task 12: Bookmark Service + Controller

**Files:**
- Create: `src/main/java/com/hlaia/service/BookmarkService.java`
- Create: `src/main/java/com/hlaia/controller/BookmarkController.java`

- [ ] **Step 1: Create BookmarkService.java**

```java
package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.*;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.kafka.KafkaProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final KafkaProducer kafkaProducer;

    public List<BookmarkResponse> getBookmarksByFolder(Long userId, Long folderId) {
        List<Bookmark> bookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, userId)
                        .eq(Bookmark::getFolderId, folderId)
                        .orderByAsc(Bookmark::getSortOrder));
        return bookmarks.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public BookmarkResponse createBookmark(Long userId, BookmarkCreateRequest request) {
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(request.getFolderId());
        bookmark.setTitle(request.getTitle());
        bookmark.setUrl(request.getUrl());
        bookmark.setDescription(request.getDescription());

        Long count = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getFolderId()));
        bookmark.setSortOrder(count.intValue());

        bookmarkMapper.insert(bookmark);

        // Send Kafka message for async icon fetch
        kafkaProducer.sendIconFetchTask(bookmark.getId(), bookmark.getUrl());

        return toResponse(bookmark);
    }

    @Transactional
    public BookmarkResponse updateBookmark(Long userId, Long bookmarkId, BookmarkCreateRequest request) {
        Bookmark bookmark = getBookmarkForUser(userId, bookmarkId);
        if (request.getTitle() != null) bookmark.setTitle(request.getTitle());
        if (request.getUrl() != null) bookmark.setUrl(request.getUrl());
        if (request.getDescription() != null) bookmark.setDescription(request.getDescription());
        bookmarkMapper.updateById(bookmark);
        return toResponse(bookmark);
    }

    @Transactional
    public void deleteBookmark(Long userId, Long bookmarkId) {
        getBookmarkForUser(userId, bookmarkId);
        bookmarkMapper.deleteById(bookmarkId);
    }

    @Transactional
    public void sortBookmarks(Long userId, BookmarkSortRequest request) {
        for (BookmarkSortRequest.SortItem item : request.getItems()) {
            Bookmark bookmark = getBookmarkForUser(userId, item.getId());
            bookmark.setSortOrder(item.getSortOrder());
            bookmarkMapper.updateById(bookmark);
        }
    }

    @Transactional
    public void batchDelete(Long userId, BatchDeleteRequest request) {
        for (Long id : request.getIds()) {
            getBookmarkForUser(userId, id);
        }
        bookmarkMapper.deleteBatchIds(request.getIds());
    }

    public List<String> batchCopyLinks(Long userId, BatchCopyRequest request) {
        return request.getIds().stream()
                .map(id -> {
                    Bookmark b = getBookmarkForUser(userId, id);
                    return b.getUrl();
                })
                .collect(Collectors.toList());
    }

    private Bookmark getBookmarkForUser(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
        if (bookmark == null || !bookmark.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND);
        }
        return bookmark;
    }

    private BookmarkResponse toResponse(Bookmark bookmark) {
        BookmarkResponse dto = new BookmarkResponse();
        dto.setId(bookmark.getId());
        dto.setFolderId(bookmark.getFolderId());
        dto.setTitle(bookmark.getTitle());
        dto.setUrl(bookmark.getUrl());
        dto.setDescription(bookmark.getDescription());
        dto.setIconUrl(bookmark.getIconUrl());
        dto.setSortOrder(bookmark.getSortOrder());
        dto.setCreatedAt(bookmark.getCreatedAt());
        dto.setUpdatedAt(bookmark.getUpdatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: Create BookmarkController.java**

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.*;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bookmarks", description = "Bookmark management APIs")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping("/folders/{folderId}/bookmarks")
    @Operation(summary = "Get bookmarks in a folder")
    public Result<List<BookmarkResponse>> list(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long folderId) {
        return Result.success(bookmarkService.getBookmarksByFolder(userId, folderId));
    }

    @PostMapping("/bookmarks")
    @Operation(summary = "Create a bookmark")
    public Result<BookmarkResponse> create(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.createBookmark(userId, request));
    }

    @PutMapping("/bookmarks/{id}")
    @Operation(summary = "Update a bookmark")
    public Result<BookmarkResponse> update(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long id,
                                            @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.updateBookmark(userId, id, request));
    }

    @DeleteMapping("/bookmarks/{id}")
    @Operation(summary = "Delete a bookmark")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        bookmarkService.deleteBookmark(userId, id);
        return Result.success();
    }

    @PutMapping("/bookmarks/sort")
    @Operation(summary = "Batch update bookmark sort order")
    public Result<Void> sort(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody BookmarkSortRequest request) {
        bookmarkService.sortBookmarks(userId, request);
        return Result.success();
    }

    @PostMapping("/bookmarks/batch-delete")
    @Operation(summary = "Batch delete bookmarks")
    public Result<Void> batchDelete(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody BatchDeleteRequest request) {
        bookmarkService.batchDelete(userId, request);
        return Result.success();
    }

    @PostMapping("/bookmarks/batch-copy")
    @Operation(summary = "Batch copy bookmark links")
    public Result<List<String>> batchCopy(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody BatchCopyRequest request) {
        return Result.success(bookmarkService.batchCopyLinks(userId, request));
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -q`
Expected: May fail because KafkaProducer doesn't exist yet. Create a stub first.

- [ ] **Step 4: Create KafkaProducer stub**

Create `src/main/java/com/hlaia/kafka/KafkaProducer.java`:
```java
package com.hlaia.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendIconFetchTask(Long bookmarkId, String url) {
        String message = "{\"bookmarkId\":" + bookmarkId + ",\"url\":\"" + url + "\"}";
        kafkaTemplate.send("bookmark-icon-fetch", bookmarkId.toString(), message);
        log.info("Sent icon fetch task for bookmark {}", bookmarkId);
    }

    public void sendStagingCleanup(Long stagingItemId, Long userId) {
        String message = "{\"stagingItemId\":" + stagingItemId + ",\"userId\":" + userId + "}";
        kafkaTemplate.send("staging-cleanup", stagingItemId.toString(), message);
        log.info("Sent staging cleanup task for item {}", stagingItemId);
    }

    public void sendOperationLog(Long userId, String action, String target) {
        String message = "{\"userId\":" + userId + ",\"action\":\"" + action
                + "\",\"target\":\"" + target + "\"}";
        kafkaTemplate.send("operation-log", userId.toString(), message);
        log.info("Sent operation log: {} {} by user {}", action, target, userId);
    }
}
```

- [ ] **Step 5: Verify compilation and commit**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/hlaia/service/BookmarkService.java src/main/java/com/hlaia/controller/BookmarkController.java src/main/java/com/hlaia/kafka/KafkaProducer.java
git commit -m "feat: add bookmark service, controller, and Kafka producer"
```

---

### Task 13: Staging Service + Controller

**Files:**
- Create: `src/main/java/com/hlaia/service/StagingService.java`
- Create: `src/main/java/com/hlaia/controller/StagingController.java`

- [ ] **Step 1: Create StagingService.java**

```java
package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.request.MoveToFolderRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.request.StagingUpdateRequest;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.StagingItem;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StagingService {

    private final StagingItemMapper stagingItemMapper;
    private final BookmarkMapper bookmarkMapper;

    public List<StagingItemResponse> getStagingItems(Long userId) {
        List<StagingItem> items = stagingItemMapper.selectList(
                new LambdaQueryWrapper<StagingItem>()
                        .eq(StagingItem::getUserId, userId)
                        .gt(StagingItem::getExpireAt, LocalDateTime.now())
                        .orderByDesc(StagingItem::getCreatedAt));
        return items.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public StagingItemResponse addToStaging(Long userId, StagingCreateRequest request) {
        StagingItem item = new StagingItem();
        item.setUserId(userId);
        item.setTitle(request.getTitle());
        item.setUrl(request.getUrl());

        int expireMinutes = request.getExpireMinutes() != null ? request.getExpireMinutes() : 1440;
        item.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));

        stagingItemMapper.insert(item);
        return toResponse(item);
    }

    @Transactional
    public StagingItemResponse updateExpiry(Long userId, Long itemId, StagingUpdateRequest request) {
        StagingItem item = getStagingItemForUser(userId, itemId);
        item.setExpireAt(LocalDateTime.now().plusMinutes(request.getExpireMinutes()));
        stagingItemMapper.updateById(item);
        return toResponse(item);
    }

    @Transactional
    public void deleteStagingItem(Long userId, Long itemId) {
        getStagingItemForUser(userId, itemId);
        stagingItemMapper.deleteById(itemId);
    }

    @Transactional
    public void moveToFolder(Long userId, Long itemId, MoveToFolderRequest request) {
        StagingItem item = getStagingItemForUser(userId, itemId);

        // Create bookmark from staging item
        Bookmark bookmark = new Bookmark();
        bookmark.setUserId(userId);
        bookmark.setFolderId(request.getFolderId());
        bookmark.setTitle(item.getTitle());
        bookmark.setUrl(item.getUrl());
        bookmark.setIconUrl(item.getIconUrl());

        Long count = bookmarkMapper.selectCount(
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getFolderId, request.getFolderId()));
        bookmark.setSortOrder(count.intValue());
        bookmarkMapper.insert(bookmark);

        // Remove from staging
        stagingItemMapper.deleteById(itemId);
    }

    private StagingItem getStagingItemForUser(Long userId, Long itemId) {
        StagingItem item = stagingItemMapper.selectById(itemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.STAGING_NOT_FOUND);
        }
        return item;
    }

    private StagingItemResponse toResponse(StagingItem item) {
        StagingItemResponse dto = new StagingItemResponse();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setUrl(item.getUrl());
        dto.setIconUrl(item.getIconUrl());
        dto.setExpireAt(item.getExpireAt());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: Create StagingController.java**

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.MoveToFolderRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.request.StagingUpdateRequest;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.service.StagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staging")
@RequiredArgsConstructor
@Tag(name = "Staging", description = "Staging area APIs")
public class StagingController {

    private final StagingService stagingService;

    @GetMapping
    @Operation(summary = "Get staging items")
    public Result<List<StagingItemResponse>> list(@AuthenticationPrincipal Long userId) {
        return Result.success(stagingService.getStagingItems(userId));
    }

    @PostMapping
    @Operation(summary = "Add to staging area")
    public Result<StagingItemResponse> add(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody StagingCreateRequest request) {
        return Result.success(stagingService.addToStaging(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update expiry time")
    public Result<StagingItemResponse> update(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody StagingUpdateRequest request) {
        return Result.success(stagingService.updateExpiry(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete staging item")
    public Result<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        stagingService.deleteStagingItem(userId, id);
        return Result.success();
    }

    @PostMapping("/{id}/move-to-folder")
    @Operation(summary = "Move staging item to a folder")
    public Result<Void> moveToFolder(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody MoveToFolderRequest request) {
        stagingService.moveToFolder(userId, id, request);
        return Result.success();
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/hlaia/service/StagingService.java src/main/java/com/hlaia/controller/StagingController.java
git commit -m "feat: add staging area service and controller"
```

---

## Phase 5: Admin & Extension APIs

### Task 14: Admin Service + Controller

**Files:**
- Create: `src/main/java/com/hlaia/service/AdminService.java`
- Create: `src/main/java/com/hlaia/controller/AdminController.java`

- [ ] **Step 1: Create AdminService.java**

```java
package com.hlaia.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.entity.User;
import com.hlaia.mapper.FolderMapper;
import com.hlaia.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final FolderMapper folderMapper;
    private final FolderService folderService;

    public Page<UserResponse> getUserList(int page, int size) {
        Page<User> userPage = userMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));

        Page<UserResponse> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(userPage.getRecords().stream().map(this::toUserResponse).collect(Collectors.toList()));
        return result;
    }

    public List<FolderTreeResponse> getUserFolderTree(Long userId) {
        return folderService.getFolderTree(userId);
    }

    @Transactional
    public void deleteUserFolder(Long folderId) {
        folderMapper.deleteById(folderId);
    }

    @Transactional
    public void banUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        user.setStatus(1);
        userMapper.updateById(user);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        user.setStatus(0);
        userMapper.updateById(user);
    }

    private UserResponse toUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: Create AdminController.java**

```java
package com.hlaia.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hlaia.common.Result;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.UserResponse;
import com.hlaia.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management APIs")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "Get paginated user list")
    public Result<Page<UserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.getUserList(page, size));
    }

    @GetMapping("/users/{userId}/folders/tree")
    @Operation(summary = "View a user's folder tree")
    public Result<List<FolderTreeResponse>> getUserFolders(@PathVariable Long userId) {
        return Result.success(adminService.getUserFolderTree(userId));
    }

    @DeleteMapping("/folders/{id}")
    @Operation(summary = "Delete any user's folder")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        adminService.deleteUserFolder(id);
        return Result.success();
    }

    @PutMapping("/users/{userId}/ban")
    @Operation(summary = "Ban a user")
    public Result<Void> banUser(@PathVariable Long userId) {
        adminService.banUser(userId);
        return Result.success();
    }

    @PutMapping("/users/{userId}/unban")
    @Operation(summary = "Unban a user")
    public Result<Void> unbanUser(@PathVariable Long userId) {
        adminService.unbanUser(userId);
        return Result.success();
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/hlaia/service/AdminService.java src/main/java/com/hlaia/controller/AdminController.java
git commit -m "feat: add admin service and controller"
```

---

### Task 15: Extension Controller

**Files:**
- Create: `src/main/java/com/hlaia/controller/ExtensionController.java`

- [ ] **Step 1: Create ExtensionController.java**

These endpoints are unauthenticated (extension authenticates via API key or token in body). For V1, we use the same auth flow — the extension stores the JWT and sends it in the header.

```java
package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.request.BookmarkCreateRequest;
import com.hlaia.dto.request.StagingCreateRequest;
import com.hlaia.dto.response.BookmarkResponse;
import com.hlaia.dto.response.FolderTreeResponse;
import com.hlaia.dto.response.StagingItemResponse;
import com.hlaia.service.BookmarkService;
import com.hlaia.service.FolderService;
import com.hlaia.service.StagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ext")
@RequiredArgsConstructor
@Tag(name = "Extension", description = "Browser extension APIs")
public class ExtensionController {

    private final FolderService folderService;
    private final BookmarkService bookmarkService;
    private final StagingService stagingService;

    @GetMapping("/folders/tree")
    @Operation(summary = "Get folder tree for extension context menu")
    public Result<List<FolderTreeResponse>> getFolderTree(@AuthenticationPrincipal Long userId) {
        return Result.success(folderService.getFolderTree(userId));
    }

    @PostMapping("/bookmarks")
    @Operation(summary = "Quick-add bookmark from extension")
    public Result<BookmarkResponse> addBookmark(@AuthenticationPrincipal Long userId,
                                                  @Valid @RequestBody BookmarkCreateRequest request) {
        return Result.success(bookmarkService.createBookmark(userId, request));
    }

    @PostMapping("/staging")
    @Operation(summary = "Add to staging from extension")
    public Result<StagingItemResponse> addStaging(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody StagingCreateRequest request) {
        return Result.success(stagingService.addToStaging(userId, request));
    }
}
```

- [ ] **Step 2: Verify and commit**

Run: `./mvnw compile -q`

```bash
git add src/main/java/com/hlaia/controller/ExtensionController.java src/main/java/com/hlaia/config/SecurityConfig.java
git commit -m "feat: add extension controller for browser extension APIs"
```

---

## Phase 6: Advanced Backend Features

### Task 16: Kafka Consumers + Scheduled Tasks

**Files:**
- Create: `src/main/java/com/hlaia/kafka/IconFetchConsumer.java`
- Create: `src/main/java/com/hlaia/kafka/StagingCleanupConsumer.java`
- Create: `src/main/java/com/hlaia/kafka/OperationLogConsumer.java`
- Create: `src/main/java/com/hlaia/scheduled/StagingCleanupScheduler.java`

- [ ] **Step 1: Create IconFetchConsumer.java**

```java
package com.hlaia.kafka;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.entity.Bookmark;
import com.hlaia.mapper.BookmarkMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IconFetchConsumer {

    private final BookmarkMapper bookmarkMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "bookmark-icon-fetch", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long bookmarkId = node.get("bookmarkId").asLong();
            String url = node.get("url").asText();

            String iconUrl = fetchFavicon(url);
            if (iconUrl != null) {
                Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
                if (bookmark != null) {
                    bookmark.setIconUrl(iconUrl);
                    bookmarkMapper.updateById(bookmark);
                    log.info("Updated icon for bookmark {}", bookmarkId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process icon fetch: {}", e.getMessage());
        }
    }

    private String fetchFavicon(String pageUrl) {
        try {
            URI uri = new URI(pageUrl);
            String domain = uri.getScheme() + "://" + uri.getHost();
            String faviconUrl = domain + "/favicon.ico";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(faviconUrl))
                    .timeout(Duration.ofSeconds(5))
                    .HEAD()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                return faviconUrl;
            }
        } catch (Exception e) {
            log.debug("Could not fetch favicon for {}: {}", pageUrl, e.getMessage());
        }
        return null;
    }
}
```

- [ ] **Step 2: Create StagingCleanupConsumer.java**

```java
package com.hlaia.kafka;

import com.hlaia.entity.StagingItem;
import com.hlaia.mapper.StagingItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StagingCleanupConsumer {

    private final StagingItemMapper stagingItemMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "staging-cleanup", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long stagingItemId = node.get("stagingItemId").asLong();
            stagingItemMapper.deleteById(stagingItemId);
            log.info("Cleaned up expired staging item {}", stagingItemId);
        } catch (Exception e) {
            log.error("Failed to cleanup staging item: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Create OperationLogConsumer.java**

```java
package com.hlaia.kafka;

import com.hlaia.entity.OperationLog;
import com.hlaia.mapper.OperationLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogConsumer {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "operation-log", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            OperationLog log = new OperationLog();
            log.setUserId(node.has("userId") ? node.get("userId").asLong() : null);
            log.setAction(node.get("action").asText());
            log.setTarget(node.has("target") ? node.get("target").asText() : null);
            log.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(log);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Create StagingCleanupScheduler.java**

```java
package com.hlaia.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hlaia.entity.StagingItem;
import com.hlaia.kafka.KafkaProducer;
import com.hlaia.mapper.StagingItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StagingCleanupScheduler {

    private final StagingItemMapper stagingItemMapper;
    private final KafkaProducer kafkaProducer;

    @Scheduled(fixedRate = 60000) // Every minute
    public void scanExpiredItems() {
        List<StagingItem> expired = stagingItemMapper.selectList(
                new LambdaQueryWrapper<StagingItem>()
                        .le(StagingItem::getExpireAt, LocalDateTime.now()));
        for (StagingItem item : expired) {
            kafkaProducer.sendStagingCleanup(item.getId(), item.getUserId());
        }
        if (!expired.isEmpty()) {
            log.info("Scheduled cleanup: {} expired staging items", expired.size());
        }
    }
}
```

- [ ] **Step 5: Add @EnableScheduling to main application class**

Add `@EnableScheduling` annotation to `HlaiaNavigationBarApplication.java`.

- [ ] **Step 6: Verify and commit**

Run: `./mvnw compile -q`

```bash
git add src/main/java/com/hlaia/kafka/ src/main/java/com/hlaia/scheduled/
git commit -m "feat: add Kafka consumers and staging cleanup scheduler"
```

---

### Task 17: AOP Rate Limiting + Operation Logging

**Files:**
- Create: `src/main/java/com/hlaia/annotation/RateLimit.java`
- Create: `src/main/java/com/hlaia/aspect/RateLimitAspect.java`
- Create: `src/main/java/com/hlaia/aspect/OperationLogAspect.java`

- [ ] **Step 1: Create RateLimit annotation**

```java
package com.hlaia.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int permits() default 10;
    int seconds() default 60;
}
```

- [ ] **Step 2: Create RateLimitAspect.java**

```java
package com.hlaia.aspect;

import com.hlaia.annotation.RateLimit;
import com.hlaia.common.BusinessException;
import com.hlaia.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(com.hlaia.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);

        String userId = getCurrentUserId();
        String api = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getRequestURI();

        String key = "ratelimit:" + userId + ":" + api;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, rateLimit.seconds(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.permits()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        return joinPoint.proceed();
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            return auth.getPrincipal().toString();
        }
        return "anonymous";
    }
}
```

- [ ] **Step 3: Create OperationLogAspect.java**

```java
package com.hlaia.aspect;

import com.hlaia.kafka.KafkaProducer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final KafkaProducer kafkaProducer;

    @Around("execution(* com.hlaia.controller.*.*(..)) && " +
            "!execution(* com.hlaia.controller.AuthController.*(..))")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String action = signature.getMethod().getName();
            String className = signature.getDeclaringType().getSimpleName();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (auth != null && auth.getPrincipal() instanceof Long) {
                userId = (Long) auth.getPrincipal();
            }

            String target = className + "." + action;
            kafkaProducer.sendOperationLog(userId, action, target);
        } catch (Exception e) {
            // Don't fail the request if logging fails
        }

        return result;
    }
}
```

- [ ] **Step 4: Verify and commit**

Run: `./mvnw compile -q`

```bash
git add src/main/java/com/hlaia/annotation/ src/main/java/com/hlaia/aspect/
git commit -m "feat: add AOP rate limiting and operation logging"
```

---

### Task 18: Knife4j API Documentation Config

**Files:**
- Create: `src/main/java/com/hlaia/config/SwaggerConfig.java`

- [ ] **Step 1: Create SwaggerConfig.java**

```java
package com.hlaia.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HLAIANavigationBar API")
                        .description("Navigation bar management system API documentation")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .schemaRequirement("Bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
```

- [ ] **Step 2: Verify by accessing API docs**

Run: `./mvnw spring-boot:run`
Open: `http://localhost:8080/doc.html`
Expected: Knife4j UI loads with all API endpoints listed.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hlaia/config/SwaggerConfig.java
git commit -m "feat: add Knife4j API documentation configuration"
```

---

## Phase 7: Frontend (Vue 3)

### Task 19: Initialize Vue 3 Project

**Files:**
- Create: `frontend/` (via npm create)

- [ ] **Step 1: Scaffold Vue 3 project with Vite**

Run:
```bash
cd "E:/Hello World/JAVA/HLAIANavigationBar"
npm create vite@latest frontend -- --template vue
cd frontend
npm install
npm install element-plus pinia vue-router@4 axios vue-draggable-plus
```

- [ ] **Step 2: Configure vite.config.js with API proxy**

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 3: Configure main.js**

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 4: Commit**

```bash
cd ..
git add frontend/
git commit -m "feat: initialize Vue 3 frontend with Element Plus, Pinia, Router"
```

---

### Task 20: Frontend API Layer + Auth

**Files:**
- Create: `frontend/src/utils/auth.js`
- Create: `frontend/src/api/request.js`
- Create: `frontend/src/api/auth.js`
- Create: `frontend/src/api/folder.js`
- Create: `frontend/src/api/bookmark.js`
- Create: `frontend/src/api/staging.js`
- Create: `frontend/src/api/admin.js`
- Create: `frontend/src/stores/auth.js`
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`

These files follow standard Vue 3 patterns. Key file: `request.js` sets up Axios with JWT interceptor.

- [ ] **Step 1: Create utils/auth.js — token storage**

```javascript
const TOKEN_KEY = 'hlaia_access_token'
const REFRESH_KEY = 'hlaia_refresh_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_KEY, token)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
```

- [ ] **Step 2: Create api/request.js — Axios instance**

```javascript
import axios from 'axios'
import { getToken, clearTokens } from '../utils/auth'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || 'Request failed')
      if (res.code === 401) {
        clearTokens()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    ElMessage.error(error.response?.data?.message || error.message)
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 3: Create api modules (auth, folder, bookmark, staging, admin)**

Each exports functions like:
```javascript
// api/auth.js
import request from './request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function logout() {
  return request.post('/auth/logout')
}
```

```javascript
// api/folder.js
import request from './request'

export function getFolderTree() {
  return request.get('/folders/tree')
}

export function createFolder(data) {
  return request.post('/folders', data)
}

export function updateFolder(id, data) {
  return request.put(`/folders/${id}`, data)
}

export function deleteFolder(id) {
  return request.delete(`/folders/${id}`)
}

export function sortFolders(data) {
  return request.put('/folders/sort', data)
}

export function moveFolder(id, data) {
  return request.put(`/folders/${id}/move`, data)
}
```

```javascript
// api/bookmark.js
import request from './request'

export function getBookmarks(folderId) {
  return request.get(`/folders/${folderId}/bookmarks`)
}

export function createBookmark(data) {
  return request.post('/bookmarks', data)
}

export function updateBookmark(id, data) {
  return request.put(`/bookmarks/${id}`, data)
}

export function deleteBookmark(id) {
  return request.delete(`/bookmarks/${id}`)
}

export function sortBookmarks(data) {
  return request.put('/bookmarks/sort', data)
}

export function batchDeleteBookmarks(ids) {
  return request.post('/bookmarks/batch-delete', { ids })
}

export function batchCopyLinks(ids) {
  return request.post('/bookmarks/batch-copy', { ids })
}
```

```javascript
// api/staging.js
import request from './request'

export function getStagingItems() {
  return request.get('/staging')
}

export function addStagingItem(data) {
  return request.post('/staging', data)
}

export function updateStagingExpiry(id, expireMinutes) {
  return request.put(`/staging/${id}`, { expireMinutes })
}

export function deleteStagingItem(id) {
  return request.delete(`/staging/${id}`)
}

export function moveToFolder(id, folderId) {
  return request.post(`/staging/${id}/move-to-folder`, { folderId })
}
```

```javascript
// api/admin.js
import request from './request'

export function getUsers(page = 1, size = 20) {
  return request.get('/admin/users', { params: { page, size } })
}

export function getUserFolderTree(userId) {
  return request.get(`/admin/users/${userId}/folders/tree`)
}

export function deleteUserFolder(folderId) {
  return request.delete(`/admin/folders/${folderId}`)
}

export function banUser(userId) {
  return request.put(`/admin/users/${userId}/ban`)
}

export function unbanUser(userId) {
  return request.put(`/admin/users/${userId}/unban`)
}
```

- [ ] **Step 4: Create stores/auth.js — Pinia auth store**

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout } from '../api/auth'
import { setToken, setRefreshToken, clearTokens, getToken } from '../utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(!!getToken())
  const username = ref('')
  const role = ref('')

  async function login(credentials) {
    const res = await apiLogin(credentials)
    setToken(res.data.accessToken)
    setRefreshToken(res.data.refreshToken)
    username.value = res.data.username
    role.value = res.data.role
    isLoggedIn.value = true
  }

  async function register(userData) {
    const res = await apiRegister(userData)
    setToken(res.data.accessToken)
    setRefreshToken(res.data.refreshToken)
    username.value = res.data.username
    role.value = res.data.role
    isLoggedIn.value = true
  }

  async function logout() {
    try { await apiLogout() } catch (e) { /* ignore */ }
    clearTokens()
    isLoggedIn.value = false
    username.value = ''
    role.value = ''
  }

  return { isLoggedIn, username, role, login, register, logout }
})
```

- [ ] **Step 5: Create router/index.js**

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../utils/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue') },
  { path: '/register', name: 'Register', component: () => import('../views/RegisterView.vue') },
  { path: '/', name: 'Main', component: () => import('../views/MainView.vue'), meta: { auth: true } },
  { path: '/staging', name: 'Staging', component: () => import('../views/StagingView.vue'), meta: { auth: true } },
  { path: '/admin/users', name: 'AdminUsers', component: () => import('../views/admin/UserListView.vue'), meta: { auth: true, admin: true } },
  { path: '/admin/users/:id', name: 'AdminUserDetail', component: () => import('../views/admin/UserDetailView.vue'), meta: { auth: true, admin: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  if (to.meta.auth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
```

- [ ] **Step 6: Create LoginView.vue and RegisterView.vue**

`LoginView.vue`: Element Plus form with username/password fields, calls `authStore.login()`.
`RegisterView.vue`: Same pattern, calls `authStore.register()`.

These are standard Vue + Element Plus login/register forms. The user can style them with the dark theme later.

- [ ] **Step 7: Verify frontend starts**

Run: `cd frontend && npm run dev`
Expected: `http://localhost:5173` loads with login page

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat: add frontend API layer, auth store, router, login/register views"
```

---

### Task 21: Main Layout + Folder Tree + Bookmark Grid

**Files:**
- Create: `frontend/src/views/MainView.vue`
- Create: `frontend/src/components/FolderTree.vue`
- Create: `frontend/src/components/BookmarkGrid.vue`
- Create: `frontend/src/components/BookmarkCard.vue`
- Create: `frontend/src/components/FolderBreadcrumb.vue`
- Create: `frontend/src/components/BatchToolbar.vue`
- Create: `frontend/src/stores/folder.js`
- Create: `frontend/src/stores/bookmark.js`

This is the core UI — the left-right split layout with folder tree on the left and bookmark card grid on the right. Each component encapsulates its own logic:

- **MainView.vue**: Two-column layout, manages which folder is selected
- **FolderTree.vue**: Recursive tree with drag-and-drop (vue-draggable-plus), click to select
- **BookmarkGrid.vue**: Grid of BookmarkCard components, supports multi-select and drag sort
- **BookmarkCard.vue**: Individual card showing title, URL, icon
- **BatchToolbar.vue**: Appears when items are selected, batch delete/copy actions
- **FolderBreadcrumb.vue**: Shows path to current folder

- [ ] **Step 1: Implement all components**

Write each component file. Key patterns:
- FolderTree uses `vue-draggable-plus` for drag-and-drop reordering
- BookmarkGrid uses CSS Grid (3 columns) with drag-and-drop
- Multi-select via Ctrl+Click, tracked in `selectedIds` ref
- Dark theme via Element Plus `dark` class on `<html>`

- [ ] **Step 2: Verify the main layout renders**

Run: `cd frontend && npm run dev`
Expected: Left sidebar with folder tree, right content area with bookmark cards

- [ ] **Step 3: Test end-to-end flow**

1. Login with testuser
2. Create folders
3. Add bookmarks
4. Drag to reorder
5. Multi-select and batch delete

- [ ] **Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: add main layout, folder tree, bookmark grid with drag-and-drop"
```

---

### Task 22: Staging View + Admin Views

**Files:**
- Create: `frontend/src/views/StagingView.vue`
- Create: `frontend/src/components/StagingList.vue`
- Create: `frontend/src/views/admin/UserListView.vue`
- Create: `frontend/src/views/admin/UserDetailView.vue`

- [ ] **Step 1: Implement StagingView.vue**

Shows list of staging items with countdown timers, "Move to Folder" and "Set Expiry" actions.

- [ ] **Step 2: Implement admin views**

UserListView: Paginated table of users with ban/unban actions.
UserDetailView: Shows selected user's folder tree and bookmarks, with delete ability.

- [ ] **Step 3: Verify and commit**

```bash
git add frontend/
git commit -m "feat: add staging view and admin management views"
```

---

## Phase 8: Browser Extension

### Task 23: Chromium Extension (Manifest V3)

**Files:**
- Create: `extension/manifest.json`
- Create: `extension/background.js`
- Create: `extension/options/options.html`
- Create: `extension/options/options.js`

- [ ] **Step 1: Create manifest.json**

```json
{
  "manifest_version": 3,
  "name": "HLAIA Navigation Bar",
  "version": "1.0.0",
  "description": "Quick-save web pages to your HLAIA navigation bar",
  "permissions": ["contextMenus", "storage", "activeTab"],
  "host_permissions": ["http://localhost:8080/*"],
  "background": {
    "service_worker": "background.js"
  },
  "options_ui": {
    "page": "options/options.html",
    "open_in_tab": true
  },
  "icons": {
    "16": "icons/icon16.png",
    "48": "icons/icon48.png",
    "128": "icons/icon128.png"
  }
}
```

- [ ] **Step 2: Create background.js**

```javascript
// Create context menu on install
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'hlaia-save',
    title: '收藏到 HLAIA 导航栏',
    contexts: ['page', 'link']
  })
})

// Handle context menu click
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId === 'hlaia-save') {
    const token = await getStoredToken()
    if (!token) {
      chrome.runtime.openOptionsPage()
      return
    }

    // Fetch folder tree for submenu
    const folders = await fetchFolderTree(token)
    // For V1: show a prompt to select folder or save to staging
    const choice = prompt(
      '选择操作:\n0 = 保存到暂存区\n' +
      folders.map((f, i) => `${i + 1} = ${f.name}`).join('\n')
    )

    const title = tab.title
    const url = info.linkUrl || tab.url

    if (choice === '0') {
      await saveToStaging(token, title, url)
      alert('已保存到暂存区')
    } else {
      const idx = parseInt(choice) - 1
      if (idx >= 0 && idx < folders.length) {
        await saveBookmark(token, folders[idx].id, title, url)
        alert(`已保存到 "${folders[idx].name}"`)
      }
    }
  }
})

async function getStoredToken() {
  return new Promise(resolve => {
    chrome.storage.local.get(['access_token'], result => {
      resolve(result.access_token)
    })
  })
}

async function fetchFolderTree(token) {
  const res = await fetch('http://localhost:8080/api/ext/folders/tree', {
    headers: { 'Authorization': `Bearer ${token}` }
  })
  const data = await res.json()
  return data.data || []
}

async function saveBookmark(token, folderId, title, url) {
  await fetch('http://localhost:8080/api/ext/bookmarks', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ folderId, title, url })
  })
}

async function saveToStaging(token, title, url) {
  await fetch('http://localhost:8080/api/ext/staging', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ title, url })
  })
}
```

- [ ] **Step 3: Create options page (login)**

`options/options.html`:
```html
<!DOCTYPE html>
<html>
<head><title>HLAIA Navigation Bar Settings</title></head>
<body style="font-family: sans-serif; max-width: 400px; margin: 40px auto;">
  <h2>HLAIA Navigation Bar</h2>
  <div id="logged-out">
    <input id="username" placeholder="Username" style="display:block;margin:8px 0;width:100%;padding:8px;">
    <input id="password" type="password" placeholder="Password" style="display:block;margin:8px 0;width:100%;padding:8px;">
    <button id="login-btn" style="padding:8px 16px;">Login</button>
    <p id="error" style="color:red;"></p>
  </div>
  <div id="logged-in" style="display:none;">
    <p>Logged in as <strong id="user-name"></strong></p>
    <button id="logout-btn" style="padding:8px 16px;">Logout</button>
  </div>
  <script src="options.js"></script>
</body>
</html>
```

`options/options.js`:
```javascript
const API_BASE = 'http://localhost:8080/api'

document.addEventListener('DOMContentLoaded', () => {
  checkLogin()

  document.getElementById('login-btn').addEventListener('click', async () => {
    const username = document.getElementById('username').value
    const password = document.getElementById('password').value
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      const data = await res.json()
      if (data.code === 200) {
        chrome.storage.local.set({
          access_token: data.data.accessToken,
          refresh_token: data.data.refreshToken,
          username: data.data.username
        }, checkLogin)
      } else {
        document.getElementById('error').textContent = data.message
      }
    } catch (e) {
      document.getElementById('error').textContent = 'Connection failed'
    }
  })

  document.getElementById('logout-btn').addEventListener('click', () => {
    chrome.storage.local.clear(checkLogin)
  })
})

function checkLogin() {
  chrome.storage.local.get(['access_token', 'username'], result => {
    if (result.access_token) {
      document.getElementById('logged-out').style.display = 'none'
      document.getElementById('logged-in').style.display = 'block'
      document.getElementById('user-name').textContent = result.username
    } else {
      document.getElementById('logged-out').style.display = 'block'
      document.getElementById('logged-in').style.display = 'none'
    }
  })
}
```

- [ ] **Step 4: Create placeholder icon files**

Create simple colored square PNGs for icons (16x16, 48x48, 128x128). Can use a placeholder or generate with a tool.

- [ ] **Step 5: Load extension in Chrome**

1. Open `chrome://extensions/`
2. Enable Developer mode
3. Click "Load unpacked"
4. Select the `extension/` directory
Expected: Extension appears, right-click shows "收藏到 HLAIA 导航栏"

- [ ] **Step 6: Commit**

```bash
git add extension/
git commit -m "feat: add Chromium browser extension (Manifest V3)"
```

---

## Phase 9: Docker Deployment

### Task 24: Backend Dockerfile + docker-compose.yml

**Files:**
- Create: `Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Modify: `docker-compose.yml` (add backend + frontend services)

- [ ] **Step 1: Create backend Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create frontend Dockerfile + nginx.conf**

`frontend/Dockerfile`:
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`frontend/nginx.conf`:
```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 3: Update docker-compose.yml with backend and frontend services**

Append to the existing docker-compose.yml:
```yaml
  backend:
    build: .
    container_name: hlaia-backend
    depends_on:
      - mysql
      - redis
      - kafka
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/hlaia_nav?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: hlaia
      SPRING_DATASOURCE_PASSWORD: hlaia123
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP-SERVERS: kafka:9092

  frontend:
    build: ./frontend
    container_name: hlaia-frontend
    depends_on:
      - backend
    ports:
      - "80:80"
```

- [ ] **Step 4: Test full stack with Docker**

Run: `docker compose up --build`
Expected: All services start. Open `http://localhost` to see the app.

- [ ] **Step 5: Final commit**

```bash
git add Dockerfile frontend/Dockerfile frontend/nginx.conf docker-compose.yml
git commit -m "deploy: add Docker configuration for full stack deployment"
```

---

## Summary

| Phase | Tasks | Estimated Files |
|---|---|---|
| Phase 1: Infrastructure | Tasks 1-4 | 6 files |
| Phase 2: Backend Foundation | Tasks 5-8 | 20 files |
| Phase 3: Security & Auth | Tasks 9-10 | 8 files |
| Phase 4: Core Business | Tasks 11-13 | 6 files |
| Phase 5: Admin & Extension | Tasks 14-15 | 4 files |
| Phase 6: Advanced Features | Tasks 16-18 | 8 files |
| Phase 7: Frontend | Tasks 19-22 | ~25 files |
| Phase 8: Extension | Task 23 | 6 files |
| Phase 9: Docker | Task 24 | 4 files |
| **Total** | **24 tasks** | **~87 files** |

Each task produces a working, testable increment with a clear commit point.

---

## Known Gaps & Deferred Items

These items from the spec are intentionally deferred to keep the plan focused. They should be addressed after the core system is working:

1. **Redis caching for staging items** (`user:staging:{userId}`) — Staging queries hit DB directly. Add `@Cacheable` to `StagingService` in a follow-up.
2. **Redis caching for user info** (`user:info:{userId}`) — User queries hit DB directly. Add `@Cacheable` to `UserDetailsServiceImpl` in a follow-up.
3. **Frontend component code** — Tasks 20-22 provide the architecture, API layer, and store logic with full code. The actual Vue component templates (MainView, FolderTree, BookmarkGrid, etc.) will be written during implementation. The patterns are established by the API layer and stores.
4. **Extension dynamic submenu** — V1 uses a `prompt()` dialog for folder selection. Dynamic Chrome context menu submenus (with folder list) require the `chrome.contextMenus.create` child API and will be added in a follow-up.
5. **Right-click context menu on frontend** — Custom context menu for folders/bookmarks (edit, delete, move) will be added as a polish pass after core functionality works.
6. **`application-prod.yml`** — Production profile config will be created when setting up Docker deployment (Task 24).
7. **Automated tests** — Backend service tests (`AuthServiceTest`, `FolderServiceTest`, etc.) should be added alongside each service. For a learning project, manual verification (curl, browser) during development is acceptable initially.
