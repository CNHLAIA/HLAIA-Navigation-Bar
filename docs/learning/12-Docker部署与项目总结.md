# 阶段 12：Docker 部署与项目总结

> 恭喜你走到了最后一个阶段！在前面的 11 个阶段中，你已经学完了从项目骨架搭建到业务逻辑实现的全部内容。这个阶段，我们将把应用"打包上架"——用 Docker 容器化部署，并回顾整个项目的架构。

---

## 目录

1. [前置知识：虚拟机、容器与 Linux](#1-前置知识虚拟机容器与-linux)
2. [概念讲解：Docker 核心概念](#2-概念讲解docker-核心概念)
3. [代码逐行解读：Dockerfile](#3-代码逐行解读dockerfile)
4. [代码逐行解读：nginx.conf](#4-代码逐行解读nginxconf)
5. [代码逐行解读：docker-compose.yml](#5-代码逐行解读docker-composeyml)
6. [环境配置分离：dev vs prod](#6-环境配置分离dev-vs-prod)
7. [项目整体架构回顾](#7-项目整体架构回顾)
8. [动手练习建议](#8-动手练习建议)

---

## 1. 前置知识：虚拟机、容器与 Linux

### 1.1 什么是 Linux？

我们日常使用 Windows 或 macOS，但服务器（也就是运行我们应用的远程电脑）绝大多数运行的是 **Linux 操作系统**。Linux 有很多发行版，比如 Ubuntu、CentOS、Debian，以及后面会提到的 **Alpine**（一个极其轻量的 Linux 发行版）。

你不需要精通 Linux 才能学 Docker，但需要知道几个基本概念：

| 概念 | 说明 |
|------|------|
| **Shell** | 命令行界面，类似 Windows 的 CMD 或 PowerShell |
| **文件系统** | Linux 没有C盘D盘，所有文件从根目录 `/` 开始 |
| **权限** | 每个文件有读(r)、写(w)、执行(x) 权限 |
| **包管理器** | 类似应用商店，用来安装软件。Alpine 用 `apk`，Ubuntu 用 `apt` |

### 1.2 虚拟机 vs 容器

在 Docker 出现之前，如果要在一台服务器上同时运行多个应用，通常使用**虚拟机（Virtual Machine）**：

```
+---------------------------------------------------+
|                   物理服务器                        |
|  +----------------+  +----------------+            |
|  |   虚拟机 1      |  |   虚拟机 2      |            |
|  | +------------+ |  | +------------+ |            |
|  | |  App A     | |  | |  App B     | |            |
|  | +------------+ |  | +------------+ |            |
|  | | Guest OS   | |  | | Guest OS   | |            |
|  | +------------+ |  | +------------+ |            |
|  +----------------+  +----------------+            |
|  +-----------------------------------------+       |
|  |            Hypervisor（虚拟化层）          |       |
|  +-----------------------------------------+       |
|  +-----------------------------------------+       |
|  |            宿主机操作系统（Host OS）       |       |
|  +-----------------------------------------+       |
+---------------------------------------------------+
```

虚拟机的问题：
- 每个 Guest OS 都要占几 GB 磁盘空间
- 每个 Guest OS 都要占几百 MB 内存
- 启动慢（要等整个操作系统启动完）

**容器（Container）** 是一种更轻量的方案：

```
+---------------------------------------------------+
|                   物理服务器                        |
|  +----------+  +----------+  +----------+         |
|  | 容器 A   |  | 容器 B   |  | 容器 C   |         |
|  |  App A   |  |  App B   |  |  App C   |         |
|  +----------+  +----------+  +----------+         |
|  +-----------------------------------------+       |
|  |            Docker Engine                  |       |
|  +-----------------------------------------+       |
|  +-----------------------------------------+       |
|  |            宿主机操作系统（Host OS）       |       |
|  +-----------------------------------------+       |
+---------------------------------------------------+
```

核心区别：**容器共享宿主机的操作系统内核**，不需要为每个应用安装完整的操作系统。

| 对比项 | 虚拟机 | 容器 |
|--------|--------|------|
| 启动速度 | 分钟级 | 秒级 |
| 磁盘占用 | GB 级 | MB 级 |
| 内存占用 | 每个需要完整 OS | 共享内核，只需应用自身 |
| 隔离性 | 完全隔离 | 进程级隔离（够用） |

**类比**：如果虚拟机是"买一栋独立的房子"（带地基、带围墙），那容器就是"租一间公寓"——共享大楼的基础设施（电梯、水管），但有自己独立的空间。

---

## 2. 概念讲解：Docker 核心概念

### 2.1 Docker 是什么？

Docker 是一个**容器化平台**，它把应用和其所有依赖打包成一个标准化单元，叫做**容器**。

**类比——"集装箱"**：

在集装箱发明之前，货物运输非常混乱—— barrels（木桶）、bags（袋子）、crates（板条箱）各不相同，每到一个港口都要重新装卸。集装箱标准化后，不管里面装的是什么，外面都是统一尺寸的钢铁箱子，船、火车、卡车都能运输。

Docker 就是软件世界的"集装箱"：
- 不管你的应用是 Java、Python 还是 Node.js
- 不管运行在开发机、测试服务器还是生产服务器
- 打包成 Docker 镜像后，到处都能运行

**"Build once, run anywhere"（构建一次，到处运行）**

### 2.2 镜像（Image） vs 容器（Container）

这是 Docker 中最重要的两个概念：

| 概念 | 说明 | 类比 |
|------|------|------|
| **镜像（Image）** | 一个只读的模板，包含运行应用所需的一切 | 面向对象中的"类"（Class） |
| **容器（Container）** | 镜像的运行实例 | 面向对象中的"对象"（Object） |

```java
// 类比 Java 代码
Image image = new Image("hlaia-nav:latest");  // 镜像 = 类
Container c1 = image.run();                    // 容器1 = 对象1
Container c2 = image.run();                    // 容器2 = 对象2
// 同一个镜像可以创建多个容器，就像同一个类可以 new 多个对象
```

### 2.3 Dockerfile：构建镜像的"菜谱"

Dockerfile 是一个文本文件，里面写着"如何构建镜像"的步骤。你可以把它理解为一道菜的**菜谱**——原材料、步骤、火候都写清楚，照着做就能得到一样的结果。

常见的 Dockerfile 指令：

| 指令 | 含义 | 类比 |
|------|------|------|
| `FROM` | 基础镜像（从哪个系统开始） | "准备一个干净的厨房" |
| `WORKDIR` | 设置工作目录 | "站在灶台前" |
| `COPY` | 复制文件到镜像中 | "把食材放到灶台上" |
| `RUN` | 执行命令（安装依赖、编译等） | "开始做菜" |
| `EXPOSE` | 声明端口 | "标注这道菜需要用什么盘子装" |
| `ENTRYPOINT` | 容器启动时执行的命令 | "上菜" |
| `CMD` | 容器启动时的默认命令 | 和 ENTRYPOINT 类似，但可以被覆盖 |

### 2.4 多阶段构建（Multi-stage Build）

这是 Docker 中一个非常重要的优化技巧。

**问题**：构建 Java 项目需要 JDK（Java Development Kit，开发工具包），但运行 Java 项目只需要 JRE（Java Runtime Environment，运行环境）。JDK 比 JRE 大很多——如果把 JDK 也打包进最终镜像，就太浪费了。

**解决方案**：多阶段构建——把构建和运行分成两个阶段：

```
Stage 1（构建阶段）          Stage 2（运行阶段）
+-------------------+       +-------------------+
| JDK 17 Alpine     |       | JRE 17 Alpine     |
| + 源代码          |  -->  | + app.jar         |
| + Maven 依赖      |       |                   |
| = 编译打包        |       | = 直接运行         |
| 体积：~500MB      |       | 体积：~170MB       |
+-------------------+       +-------------------+
```

类比：**建房子 vs 住房子**——建房子需要挖掘机、搅拌机、脚手架；但建好之后，你只需要房子本身，不需要把挖掘机也留在客厅里。多阶段构建就是"建完房子就把施工设备运走"。

### 2.5 Docker Compose：编排多个容器

一个真实的 Web 项目通常由多个服务组成：

```
我们的项目需要：
├── 前端（Nginx）        -- 提供网页和反向代理
├── 后端（Spring Boot）   -- 处理业务逻辑
├── 数据库（MySQL）       -- 存储数据
├── 缓存（Redis）         -- 缓存和 Token 黑名单
└── 消息队列（Kafka）     -- 异步消息处理
```

如果用 `docker run` 命令逐个启动，要写很长的命令，还要注意启动顺序。**Docker Compose** 用一个 YAML 文件定义所有服务，一条命令就能全部启动。

```bash
# 没有 Docker Compose 时：
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=xxx mysql:8
docker run -d --name redis redis:7
docker run -d --name kafka ... kafka:latest
docker run -d --name app ... hlaia-nav:latest
docker run -d --name frontend -p 13566:80 hlaia-nav-frontend:latest

# 有 Docker Compose 时：
docker compose up -d    # 一条命令搞定！
```

Docker Compose 中的关键概念：

| 关键字 | 含义 |
|--------|------|
| `services` | 定义各个容器服务 |
| `build` | 指定 Dockerfile 所在目录 |
| `image` | 指定使用的镜像名 |
| `ports` | 端口映射（宿主机端口:容器端口） |
| `depends_on` | 服务依赖（先启动被依赖的服务） |
| `networks` | 服务加入的网络（同一网络内的容器可以互相通信） |
| `environment` | 环境变量注入 |
| `restart` | 重启策略 |

### 2.6 Nginx 反向代理

**正向代理 vs 反向代理**：

- **正向代理**：客户端知道要访问的目标，但通过代理去访问。比如 VPN——你知道你要访问 Google，但你通过 VPN 服务器去访问。
- **反向代理**：客户端不知道后端是谁，只和代理服务器打交道。比如你访问 `www.taobao.com`，背后可能有成千上万台服务器，但你只需要和一个入口打交道。

**类比——"前台接线员"**：

```
你（客户端） --> 前台接线员（Nginx） --> 转接给技术部（后端 Spring Boot）
               --> 转接给行政部门（前端静态文件）
```

你不需要知道技术部的分机号是多少，只需要告诉前台你要什么，前台帮你转接。

**为什么需要反向代理？**

1. **解决跨域问题**：前端和后端在同一个域名下，不存在跨域
2. **统一入口**：用户只需要访问一个地址（13566），不需要记两个端口
3. **安全性**：后端不直接暴露给外部，只有 Nginx 对外可见

在我们的项目中，Nginx 的角色：

```
浏览器请求 http://192.168.8.6:13566/api/xxx
    --> Nginx 判断路径以 /api 开头
    --> 转发给后端 Spring Boot (hlaia-nav:8080)

浏览器请求 http://192.168.8.6:13566/xxx
    --> Nginx 判断路径不以 /api 开头
    --> 返回前端 Vue 页面
```

---

## 3. 代码逐行解读：Dockerfile

### 3.1 后端 Dockerfile

文件位置：`Dockerfile`（项目根目录）

```dockerfile
# ============================================================
# 多阶段构建 - Stage 1: 构建阶段
# 使用 JDK 镜像编译 Spring Boot 项目
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS build
```

- `FROM eclipse-temurin:17-jdk-alpine`：基于 Eclipse Temurin 的 JDK 17 Alpine 版本镜像。
  - `eclipse-temurin` 是 Adoptium 项目提供的开源 JDK 发行版，类似于 Oracle JDK 但免费
  - `alpine` 表示基于 Alpine Linux，一个只有 5MB 大小的 Linux 发行版，非常适合做 Docker 镜像
  - `AS build` 给这个阶段取名为 `build`，后面 Stage 2 可以通过名字引用

```dockerfile
# 设置工作目录
WORKDIR /app
```

- `WORKDIR /app`：在容器内创建 `/app` 目录并切换到该目录。后续所有操作都在这个目录下进行。类似 `cd /app`。

```dockerfile
# 先复制 Maven Wrapper 和 pom.xml，利用 Docker 缓存层加速依赖下载
# 只要 pom.xml 不变，依赖层就不会重新构建
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
```

- `COPY`：把宿主机（你的电脑）上的文件复制到容器内。
- **为什么要分两步复制？** 这是 Docker 缓存优化技巧：
  - Docker 构建镜像时，每条指令会生成一个"层（Layer）"。如果某层没有变化，Docker 会直接使用缓存，不会重新执行。
  - 先复制 `pom.xml` 并下载依赖（下一步）。只要 `pom.xml` 不变，即使源代码改了，依赖层也不会重新下载。
  - 如果把所有文件一次性 COPY 进去，改一行代码就会导致依赖重新下载——非常慢。

```dockerfile
# 给 mvnw 添加可执行权限（Alpine Linux 默认不保留 Windows 的文件权限）
RUN chmod +x mvnw
```

- `RUN`：在容器内执行命令。
- `chmod +x mvnw`：给 Maven Wrapper 脚本添加可执行权限。
  - 在 Windows 上，Git 不保留 Linux 的可执行权限位。当你在 Windows 上 clone 项目后，`mvnw` 在 Linux 容器里默认没有执行权限。所以需要手动加上。

```dockerfile
# 下载所有依赖（利用 Docker 缓存，依赖不变时不会重复下载）
RUN ./mvnw dependency:go-offline -B
```

- `./mvnw dependency:go-offline`：使用 Maven Wrapper 下载所有依赖到本地仓库。
  - `go-offline` 表示"把所有依赖都下载好，之后可以离线构建"
  - `-B` 表示 Batch 模式（非交互模式），适合在 CI/CD 环境中使用
- **Docker 缓存原理**：这一步会生成一个镜像层。下次构建时，如果前面的层（pom.xml 等）没变，Docker 直接用缓存，跳过这一步。Maven 依赖可能有好几百 MB，这个优化非常重要。

```dockerfile
# 再复制源代码（源代码变更频率高，放在依赖下载之后）
COPY src src
```

- 把 `src` 目录复制到容器的 `/app/src`。此时 `/app` 下已经有 `pom.xml` 和下载好的依赖了。

```dockerfile
# 编译打包，跳过测试（测试应在 CI 流水线中单独执行）
RUN ./mvnw package -DskipTests -B
```

- `./mvnw package`：编译打包，生成 jar 文件到 `target/` 目录。
- `-DskipTests`：跳过测试。为什么？在 Docker 构建中跑测试会增加构建时间，而且数据库等依赖不一定可用。测试应该在 CI 流水线中单独执行。
- 打包完成后，`/app/target/` 下会有一个类似 `HLAIANavigationBar-0.0.1-SNAPSHOT.jar` 的文件。

```dockerfile
# ============================================================
# 多阶段构建 - Stage 2: 运行阶段
# 使用更轻量的 JRE 镜像，减小最终镜像体积
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
```

- `FROM eclipse-temurin:17-jre-alpine`：第二个阶段，使用 JRE（只有运行环境，没有编译器）。
  - JDK 包含编译工具（javac 等），体积约 300+ MB
  - JRE 只包含运行环境，体积约 170 MB
  - 最终镜像只需要运行 jar，不需要编译工具

```dockerfile
# 从构建阶段复制打包好的 jar 文件
# 使用通配符匹配 jar 文件名，避免硬编码版本号
COPY --from=build /app/target/*.jar app.jar
```

- `COPY --from=build`：从名为 `build` 的阶段（Stage 1）复制文件。这是多阶段构建的核心语法。
- `*.jar`：使用通配符匹配 jar 文件名。这样即使 `pom.xml` 中的版本号变了，也不需要修改 Dockerfile。
- 复制到 `app.jar`：重命名为 `app.jar`，简洁明了。

```dockerfile
# 暴露 Spring Boot 默认端口
EXPOSE 8080
```

- `EXPOSE 8080`：声明这个容器会使用 8080 端口。
  - 注意：这只是**声明**，并不会自动发布端口。真正让外部能访问到，需要在 `docker run` 时用 `-p` 参数或 docker-compose 中的 `ports` 来映射。
  - 这行的作用主要是**文档性质**——告诉使用者"这个容器内应用监听的是 8080 端口"。

```dockerfile
# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- `ENTRYPOINT`：容器启动时执行的命令。
- `["java", "-jar", "app.jar"]`：等价于在命令行运行 `java -jar app.jar`。
- 使用 JSON 数组格式（exec 格式）而不是字符串格式，这样 Java 进程会是 PID 1，能正确接收信号（如 SIGTERM 用于优雅关闭）。

**整个后端 Dockerfile 的构建流程总结**：

```
Stage 1 (build):
  eclipse-temurin:17-jdk-alpine
    --> 复制 pom.xml + mvnw
    --> 下载 Maven 依赖
    --> 复制源代码
    --> 编译打包生成 .jar 文件

Stage 2 (最终镜像):
  eclipse-temurin:17-jre-alpine
    --> 从 Stage 1 复制 .jar 文件
    --> 运行 java -jar app.jar
```

### 3.2 前端 Dockerfile

文件位置：`frontend/Dockerfile`

```dockerfile
# ============================================================
# 多阶段构建 - Stage 1: 构建阶段
# 使用 Node.js 镜像编译 Vue 3 前端项目
# ============================================================
FROM node:20-alpine AS build
```

- `node:20-alpine`：Node.js 20 的 Alpine 版本镜像。
  - Node.js 是 JavaScript 的运行时环境，用来编译 Vue 项目
  - 为什么需要 Node.js？因为 Vue 的源代码不能直接在浏览器中运行，需要用 Vite 等构建工具把 `.vue` 文件编译成普通的 HTML/CSS/JS

```dockerfile
WORKDIR /app

# 先复制 package.json 和 package-lock.json，利用 Docker 缓存层
# 只要依赖不变，node_modules 层就不会重新构建
COPY package*.json ./
```

- `package*.json`：通配符，同时匹配 `package.json` 和 `package-lock.json`。
- 和后端的 pom.xml 优化思路一样——先复制依赖声明文件，再安装依赖，利用 Docker 缓存。

```dockerfile
# 安装依赖（npm ci 比 npm install 更适合 CI/CD 环境，严格按照 lock 文件安装）
RUN npm ci
```

- `npm ci`：安装依赖。
  - `ci` 代表 Clean Install（干净安装）
  - 和 `npm install` 的区别：`npm ci` 会严格按照 `package-lock.json` 安装，速度更快，结果更可预测
  - 在 Docker/CI 环境中推荐使用 `npm ci`，因为它不会修改 `package-lock.json`

```dockerfile
# 复制源代码
COPY . .

# 执行生产环境构建，输出到 dist 目录
RUN npm run build
```

- `COPY . .`：复制所有源代码。注意这里被 `.dockerignore` 过滤了 `node_modules` 等目录。
- `npm run build`：执行 Vite 构建，把 Vue 源代码编译成 HTML/CSS/JS，输出到 `dist/` 目录。

```dockerfile
# ============================================================
# 多阶段构建 - Stage 2: 运行阶段
# 使用 Nginx 作为静态文件服务器和反向代理
# ============================================================
FROM nginx:alpine
```

- `nginx:alpine`：Nginx 的 Alpine 版本镜像。
  - 前端构建完成后，只需要一个 Web 服务器来提供静态文件
  - Nginx 是全球使用最广泛的 Web 服务器之一，高性能、低内存占用
  - 不需要 Node.js 运行时！因为 Vue 已经被编译成了纯 HTML/CSS/JS

```dockerfile
# 从构建阶段复制打包好的静态文件到 Nginx 默认的 HTML 目录
COPY --from=build /app/dist /usr/share/nginx/html
```

- `COPY --from=build`：从 Stage 1（名为 `build`）复制文件。
- `/app/dist`：Vue 构建产物的目录。
- `/usr/share/nginx/html`：Nginx 默认的静态文件目录。把文件放这里，Nginx 就会自动对外提供。

```dockerfile
# 复制自定义 Nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

- 把我们自定义的 `nginx.conf` 复制到 Nginx 的配置目录，覆盖默认配置。

```dockerfile
# 暴露 Nginx 默认端口
EXPOSE 80

# 使用 Nginx 默认的前台运行命令
CMD ["nginx", "-g", "daemon off;"]
```

- `EXPOSE 80`：声明使用 80 端口。
- `daemon off;`：让 Nginx 在前台运行。Docker 容器需要前台有一个持续运行的进程，否则容器会立即退出。默认 Nginx 会以守护进程（后台）方式运行，加 `daemon off;` 让它留在前台。

### 3.3 .dockerignore 文件

文件位置：`.dockerignore`（项目根目录）

```
# 版本控制
.git
.gitignore

# IDE 配置
.idea
*.iml
.vscode

# 前端依赖和构建产物（前端在 Docker 中独立构建）
frontend/node_modules
frontend/dist

# 后端构建产物
target

# 环境变量文件（包含敏感信息，不应进入镜像）
.env
.env.example

# 文档和设计文件
docs

# 浏览器扩展插件（独立打包，不进入后端镜像）
extension

# Docker 相关文件（避免递归）
docker-compose.yml

# 项目说明文件
CLAUDE.md
*.md
```

`.dockerignore` 类似 `.gitignore`，但作用不同：
- `.gitignore`：告诉 Git 哪些文件不要提交到版本控制
- `.dockerignore`：告诉 Docker 哪些文件不要复制到镜像中

为什么要排除这些文件？

| 排除的文件 | 原因 |
|------------|------|
| `.git` | 版本控制历史，镜像不需要 |
| `.idea` | IDE 配置，每个开发者不同 |
| `frontend/node_modules` | 在 Docker 构建中通过 `npm ci` 重新安装 |
| `target` | 后端构建产物，在 Docker 中重新编译 |
| `.env` | 可能包含密码等敏感信息，不应打包进镜像 |
| `docs` | 文档不参与应用运行，减小镜像体积 |

还有一个前端专属的 `.dockerignore`（`frontend/.dockerignore`）：

```
# 前端依赖（在 Docker 构建中通过 npm ci 重新安装）
node_modules

# 前端构建产物（在 Docker 构建中通过 npm run build 重新生成）
dist

# 版本控制
.git
```

这个文件只排除前端相关的文件，因为 `COPY . .` 的上下文是 `frontend/` 目录。

---

## 4. 代码逐行解读：nginx.conf

文件位置：`frontend/nginx.conf`

```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;
```

- `server { }`：定义一个虚拟服务器。
- `listen 80`：监听 80 端口（HTTP 默认端口）。
- `server_name localhost`：匹配域名为 localhost 的请求。在生产环境中可以改为实际域名。
- `root /usr/share/nginx/html`：静态文件的根目录——和 Dockerfile 中 COPY 的目标路径一致。
- `index index.html`：默认首页文件。

```nginx
    # API 请求反向代理到后端 Spring Boot 容器
    # hlaia-nav 是 docker-compose 中后端服务的容器名
    # Docker 内部网络通过容器名进行 DNS 解析
    location /api {
        proxy_pass http://hlaia-nav:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
```

这段是**反向代理的核心配置**：

- `location /api`：匹配所有以 `/api` 开头的请求路径。
  - 例如 `/api/auth/login`、`/api/folders` 都会被匹配

- `proxy_pass http://hlaia-nav:8080`：把请求转发到 `hlaia-nav` 容器的 8080 端口。
  - `hlaia-nav` 是 docker-compose 中后端服务的 `container_name`
  - Docker 的自定义网络（app-network）提供了内置的 DNS 服务，容器之间可以通过容器名互相访问
  - 你不需要知道后端容器的 IP 地址，直接用容器名就行

- `proxy_set_header` 系列：在转发请求时，添加/修改 HTTP 请求头。

| 请求头 | 含义 |
|--------|------|
| `Host $host` | 保留原始的 Host 头（浏览器请求的域名），让后端知道用户访问的是什么域名 |
| `X-Real-IP $remote_addr` | 添加客户端的真实 IP 地址。没有这个，后端看到的 IP 永远是 Nginx 容器的 IP |
| `X-Forwarded-For $proxy_add_x_forwarded_for` | 记录请求经过的所有代理 IP 链。如果有多级代理，这个头会把所有 IP 都记录下来 |
| `X-Forwarded-Proto $scheme` | 记录原始请求使用的协议（http 还是 https）。如果将来加了 HTTPS，后端需要知道原始协议 |

**请求流转示例**：

```
浏览器请求: GET http://192.168.8.6:13566/api/auth/login

Nginx 收到请求，匹配 /api 前缀：
  --> 转发到 http://hlaia-nav:8080/api/auth/login
  --> 添加 X-Real-IP: 192.168.8.x（你的电脑 IP）
  --> 添加 X-Forwarded-For: 192.168.8.x

Spring Boot 收到请求: GET /api/auth/login
  --> 正常处理并返回响应

Nginx 把响应返回给浏览器
```

```nginx
    # Vue Router 使用 history 模式，所有前端路由都返回 index.html
    # 由前端 JavaScript 负责解析路由并渲染对应组件
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- `location /`：匹配所有其他请求（不以 `/api` 开头的）。
- `try_files $uri $uri/ /index.html`：按顺序尝试查找文件：
  1. `$uri`：尝试匹配请求路径对应的文件，比如 `/assets/logo.png` --> 查找 `/usr/share/nginx/html/assets/logo.png`
  2. `$uri/`：尝试匹配路径对应的目录
  3. `/index.html`：如果都不存在，返回 `index.html`

**为什么需要 `try_files`？**

Vue Router 使用 **history 模式**，URL 看起来像正常路径：
```
http://192.168.8.6:13566/login
http://192.168.8.6:13566/bookmarks
http://192.168.8.6:13566/folders
```

这些 URL 并不是服务器上的真实文件。如果用户直接访问 `/login`，Nginx 会去找 `/usr/share/nginx/html/login` 这个文件——找不到就返回 404。

`try_files` 的解决方案：如果找不到对应文件，就返回 `index.html`。然后 Vue Router 的 JavaScript 会在浏览器端解析 URL `/login`，渲染对应的组件。

**完整的请求分发逻辑**：

```
收到请求
  ├── 路径以 /api 开头？
  │     YES --> proxy_pass 转发给后端 Spring Boot
  │
  └── 其他路径
        ├── 能找到对应的静态文件？
        │     YES --> 返回该文件（CSS、JS、图片等）
        │
        └── 找不到？
              YES --> 返回 index.html（交给 Vue Router 处理）
```

---

## 5. 代码逐行解读：docker-compose.yml

文件位置：`docker-compose.yml`

```yaml
version: '3.8'
```

- `version: '3.8'`：Docker Compose 文件格式版本。3.8 是较新的版本，支持大部分常用功能。

```yaml
services:
  # 后端 Spring Boot 服务
  # 不对外暴露端口，仅通过 app-network 内部网络与前端通信
  hlaia-nav:
    build: .
    image: hlaia-navigation-bar:latest
    container_name: hlaia-nav
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      TZ: Asia/Shanghai
    networks:
      - app-network
```

逐项解释：

| 配置 | 含义 |
|------|------|
| `hlaia-nav:` | 服务名称。Docker Compose 内部通过这个名字引用服务。 |
| `build: .` | Dockerfile 在当前目录（`.`）。`docker compose build` 时会自动构建。 |
| `image: hlaia-navigation-bar:latest` | 构建后的镜像名称和标签。 |
| `container_name: hlaia-nav` | 容器名称。Nginx 配置中的 `proxy_pass http://hlaia-nav:8080` 就是这个名字。 |
| `restart: unless-stopped` | 除非手动停止，否则自动重启。服务器重启后应用也会自动启动。 |
| `SPRING_PROFILES_ACTIVE: prod` | 激活 Spring Boot 的 `prod` Profile，使用 `application-prod.yml` 配置。 |
| `TZ: Asia/Shanghai` | 设置容器时区为中国上海时区。 |
| `networks: - app-network` | 加入 `app-network` 网络。 |

**注意：后端没有 `ports` 配置！** 这意味着后端的 8080 端口不对外暴露。外部只能通过前端的 Nginx 反向代理来访问后端。这是一种安全措施——后端不直接暴露在公网上。

```yaml
  # 前端 Nginx 服务
  # 对外暴露 13566 端口，同时提供静态文件和 API 反向代理
  hlaia-nav-frontend:
    build: ./frontend
    container_name: hlaia-nav-frontend
    restart: unless-stopped
    ports:
      - "13566:80"
    environment:
      TZ: Asia/Shanghai
    depends_on:
      - hlaia-nav
    networks:
      - app-network
```

| 配置 | 含义 |
|------|------|
| `build: ./frontend` | Dockerfile 在 `frontend/` 子目录。 |
| `ports: "13566:80"` | **端口映射**：宿主机的 13566 端口映射到容器的 80 端口。访问 `http://NAS_IP:13566` 就能到达 Nginx。 |
| `depends_on: - hlaia-nav` | 依赖后端服务。Docker Compose 会先启动后端，再启动前端。 |

**端口映射详解**：

```
外部访问                  宿主机                容器
http://NAS:13566  -->  13566 端口  -->  容器的 80 端口 (Nginx)
```

- `13566`：宿主机（NAS）上的端口。你可以理解为"门牌号"——外部世界通过这个门牌号找到你的应用。
- `80`：容器内部 Nginx 监听的端口。
- 为什么选 13566？为了避免和其他服务的端口冲突，选了一个不太常用的端口号。

```yaml
networks:
  app-network:
    external: true
```

- `app-network`：自定义网络名称。
- `external: true`：表示这个网络是**外部已存在的**，不需要 Docker Compose 创建。
  - 在飞牛 NAS 上，MySQL、Redis、Kafka 等基础设施已经部署好了，它们也在 `app-network` 这个网络中。
  - 用 `external: true` 让我们的应用加入已有的网络，和基础设施互相通信。

**Docker 网络示意**：

```
app-network（Docker 自定义网络）
┌─────────────────────────────────────────────────────┐
│                                                       │
│  ┌──────────────┐  ┌──────────────┐                  │
│  │ hlaia-nav    │  │ hlaia-nav-   │                  │
│  │ (后端:8080)  │  │ frontend     │                  │
│  │              │  │ (Nginx:80)   │                  │
│  └──────┬───────┘  └──────┬───────┘                  │
│         │                 │                          │
│  ┌──────┴───────┐         │  ← 端口映射 13566:80    │
│  │ mysql        │         │                          │
│  │ (3306)       │         │                          │
│  └──────┬───────┘         │                          │
│         │                 │                          │
│  ┌──────┴───────┐  ┌──────┴───────┐                  │
│  │ redis        │  │ kafka        │                  │
│  │ (6379)       │  │ (9092)       │                  │
│  └──────────────┘  └──────────────┘                  │
│                                                       │
│  所有容器通过容器名互相访问（Docker 内置 DNS）         │
│                                                       │
└─────────────────────────────────────────────────────┘
          │
          │  端口映射 13566:80
          v
    外部用户通过 http://192.168.8.6:13566 访问
```

---

## 6. 环境配置分离：dev vs prod

### 6.1 为什么要分离配置？

开发和生产环境的配置是不同的：

| 配置项 | 开发环境 (dev) | 生产环境 (prod) |
|--------|---------------|----------------|
| MySQL 地址 | `192.168.8.6:3306` | `mysql:3306`（容器名） |
| Redis 地址 | `192.168.8.6:6379` | `redis:6379`（容器名） |
| Kafka 地址 | `192.168.8.6:9092` | `kafka:9092`（容器名） |
| 日志级别 | `debug`（详细日志） | `info`（精简日志） |
| JWT Secret | 硬编码的测试值 | 从环境变量读取 `${JWT_SECRET:...}` |

Spring Boot 使用 **Profile** 机制来管理不同环境的配置：

```
application.yml           <-- 公共配置（端口、应用名）
application-dev.yml       <-- 开发环境配置
application-prod.yml      <-- 生产环境配置
```

### 6.2 如何激活不同 Profile？

**方式一：配置文件中指定**（`application.yml`）

```yaml
spring:
  profiles:
    active: dev    # 默认使用开发环境
```

**方式二：环境变量覆盖**（docker-compose.yml 中使用）

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod    # 覆盖为生产环境
```

**方式三：命令行参数**

```bash
java -jar app.jar --spring.profiles.active=prod
```

### 6.3 生产环境配置中的 `${}` 语法

```yaml
# application-prod.yml
jwt:
  secret: ${JWT_SECRET:hlaia-navigation-bar-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm}
```

这是 Spring Boot 的**属性占位符**语法：
- `${JWT_SECRET}`：尝试从环境变量中读取 `JWT_SECRET` 的值
- `:` 后面的内容是**默认值**：如果环境变量不存在，就使用这个默认值

这样，敏感信息（密码、密钥）不需要写死在配置文件中，而是通过环境变量注入，更加安全。

---

## 7. 项目整体架构回顾

经过 12 个阶段的学习，你已经完整实现了一个前后端分离的 Web 应用。让我们站在更高处回顾整个项目。

### 7.1 技术架构总览

```
                          ┌─────────────────────────────────┐
                          │          用户浏览器              │
                          │    (Chrome 扩展 / 直接访问)      │
                          └──────────────┬──────────────────┘
                                         │ HTTP
                                         v
                    ┌────────────────────────────────────────┐
                    │       Nginx (反向代理 + 静态文件)        │
                    │         端口: 80 (对外 13566)           │
                    │                                        │
                    │   /api/*  ──proxy_pass──> 后端:8080    │
                    │   /*      ──try_files──> Vue SPA       │
                    └──────────────┬─────────────────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              v                                         v
    ┌──────────────────┐                    ┌──────────────────┐
    │  Vue 3 前端 SPA  │                    │  Spring Boot 后端 │
    │  Element Plus    │                    │  端口: 8080       │
    │  Pinia 状态管理  │                    │                   │
    │  vue-draggable   │                    │  ┌──────────────┐ │
    │  Axios HTTP 请求 │                    │  │ Security     │ │
    └──────────────────┘                    │  │ JwtAuthFilter│ │
                                            │  └──────┬───────┘ │
                                            │         │         │
                                            │  ┌──────v───────┐ │
                                            │  │ Controller   │ │
                                            │  │ REST API     │ │
                                            │  └──────┬───────┘ │
                                            │         │         │
                                            │  ┌──────v───────┐ │
                                            │  │ Service      │ │
                                            │  │ 业务逻辑     │ │
                                            │  └──────┬───────┘ │
                                            │         │         │
                                            │  ┌──────v───────┐ │
                                            │  │ Mapper       │ │
                                            │  │ MyBatis-Plus │ │
                                            │  └──────┬───────┘ │
                                            └─────────┼─────────┘
                                                      │
                    ┌─────────────────────────────────┼────────────────┐
                    v                                 v                v
          ┌──────────────┐                  ┌──────────────┐  ┌──────────────┐
          │   MySQL 8    │                  │   Redis 7    │  │    Kafka     │
          │   数据持久化  │                  │   缓存/TOKEN │  │  异步消息    │
          │              │                  │   黑名单     │  │  操作日志    │
          │ - user       │                  │              │  │              │
          │ - folder     │                  │              │  │              │
          │ - bookmark   │                  │              │  │              │
          │ - staging    │                  │              │  │              │
          │ - op_log     │                  │              │  │              │
          └──────────────┘                  └──────────────┘  └──────────────┘
```

### 7.2 安全链路回顾

你在阶段 03-05 学到的安全机制，在部署后仍然完整运作：

```
1. 用户登录
   POST /api/auth/login { username, password }
       │
       v
   AuthController.login()
       │
       v
   AuthenticationManager.authenticate()  ── Spring Security 验证密码
       │
       v
   JwtTokenProvider.generateToken()      ── 生成 JWT Token
       │
       v
   返回 { accessToken, refreshToken }

2. 后续请求携带 Token
   GET /api/folders
   Header: Authorization: Bearer <token>
       │
       v
   JwtAuthFilter.doFilterInternal()      ── 每个请求都经过这个过滤器
       │
       ├── 验证 Token 签名和有效期
       ├── 检查 Token 是否在 Redis 黑名单中
       ├── 解析用户信息
       └── 设置 SecurityContext
       │
       v
   到达 Controller，通过 @AuthenticationPrincipal 获取当前用户

3. 用户登出
   POST /api/auth/logout
       │
       v
   将 Token 加入 Redis 黑名单（设置过期时间 = Token 剩余有效期）
       │
       v
   即使 Token 还没过期，也无法再使用
```

### 7.3 数据流转回顾

```
浏览器                     后端                         数据库
  │                         │                             │
  │  POST /api/bookmarks    │                             │
  │  { name, url, folderId }│                             │
  │ ──────────────────────> │                             │
  │                         │  BookmarkController         │
  │                         │    .createBookmark()        │
  │                         │         │                   │
  │                         │         v                   │
  │                         │  BookmarkService            │
  │                         │    .createBookmark()        │
  │                         │    - 校验参数               │
  │                         │    - 检查文件夹是否存在     │
  │                         │    - 检查是否有权限         │
  │                         │         │                   │
  │                         │         v                   │
  │                         │  BookmarkMapper             │
  │                         │    .insert()                │
  │                         │         │                   │
  │                         │         v                   │
  │                         │  ──── INSERT INTO bookmark ─>│
  │                         │  <─── 返回生成的主键 ID ─────│
  │                         │         │                   │
  │                         │         v                   │
  │                         │  发送 Kafka 消息（异步日志） │
  │                         │         │                   │
  │  { id, name, url, ... } │         │                   │
  │ <────────────────────── │                             │
```

### 7.4 你学过的核心知识点

| 阶段 | 主题 | 核心技术 |
|------|------|----------|
| 01 | 项目骨架 | Spring Boot 项目结构、Maven、application.yml |
| 02 | 用户注册 | Controller → Service → Mapper 三层架构、DTO、参数校验 |
| 03 | 用户登录与 JWT | Spring Security、JWT Token 生成与解析、BCrypt 密码加密 |
| 04 | Security 过滤器链 | FilterChain、JwtAuthFilter、SecurityContext、白名单路径 |
| 05 | Token 黑名单与 Redis | Redis String 操作、Token 登出机制、Refresh Token |
| 06 | 文件夹树形结构 | 邻接表模型、递归查询、树形结构构建算法 |
| 07 | 书签管理与分页 | MyBatis-Plus 分页插件、CRUD、条件构造器 |
| 08 | 暂存区与定时任务 | Spring @Scheduled、Cron 表达式、定时清理过期数据 |
| 09 | AOP 与操作日志 | @Aspect 切面、自定义注解 @OperationLog、AOP 通知类型 |
| 10 | 管理员功能 | CommandLineRunner 初始化、角色权限控制、用户管理 |
| 11 | Kafka 消息队列 | 异步消息处理、Producer/Consumer、消息序列化 |
| **12** | **Docker 部署** | **Dockerfile、多阶段构建、Nginx 反向代理、Docker Compose** |

### 7.5 部署命令速查

```bash
# 构建并启动所有服务
docker compose up -d --build

# 查看运行状态
docker compose ps

# 查看后端日志
docker compose logs -f hlaia-nav

# 查看前端日志
docker compose logs -f hlaia-nav-frontend

# 停止所有服务
docker compose down

# 重新构建（代码更新后）
docker compose up -d --build
```

---

## 8. 动手练习建议

### 练习 1：本地体验 Docker 构建

如果你已经安装了 Docker Desktop：

```bash
# 在项目根目录下执行
docker compose up -d --build

# 等待构建完成后，访问
# http://localhost:13566
```

观察构建过程中的日志输出，理解多阶段构建的每一步。

### 练习 2：理解端口映射

尝试修改 `docker-compose.yml` 中的端口映射：

```yaml
ports:
  - "18080:80"    # 改为 18080
```

重新启动后，访问 `http://localhost:18080`，体会"宿主机端口:容器端口"的含义。

### 练习 3：理解 Nginx 路由

在 `nginx.conf` 中添加一个新的 location 块：

```nginx
# 在 server 块内添加
location /health {
    return 200 'OK';
    add_header Content-Type text/plain;
}
```

重新构建前端镜像后，访问 `http://localhost:13566/health`，观察返回结果。

### 练习 4：查看镜像大小对比

```bash
# 查看构建好的镜像
docker images | grep hlaia

# 你会看到类似：
# hlaia-navigation-bar   latest   xxx MB
# hlaia-nav-frontend     latest   xxx MB（应该很小，因为用了 Alpine + 多阶段构建）
```

### 练习 5：总结你学到的知识

尝试不看文档，用自己的话回答以下问题：

1. Docker 镜像和容器的关系是什么？（提示：类比 Java 中的类和对象）
2. 为什么要用多阶段构建？如果不用，镜像会变成什么样？
3. Nginx 反向代理解决了什么问题？
4. `application-dev.yml` 和 `application-prod.yml` 中最大的区别是什么？为什么要有两个配置文件？
5. 从浏览器发出一个请求到返回数据，经过了哪些环节？

---

## 结语

至此，你完成了全部 12 个阶段的学习！

你从一个空的 Spring Boot 项目开始，逐步构建了一个包含用户认证、权限控制、数据管理、消息队列、容器化部署的完整 Web 应用。这些知识涵盖了 Java 后端开发的核心技能：

- **分层架构**：Controller → Service → Mapper
- **安全认证**：Spring Security + JWT
- **数据访问**：MyBatis-Plus + Flyway
- **缓存**：Redis
- **消息队列**：Kafka
- **AOP 切面**：日志、限流
- **容器化部署**：Docker + Nginx

这些技能可以迁移到绝大多数 Java 后端项目中。祝你在 Java 后端开发的道路上继续进步！
