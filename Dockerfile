# ============================================================
# 多阶段构建 - Stage 1: 构建阶段
# 使用 JDK 镜像编译 Spring Boot 项目
# ============================================================
FROM eclipse-temurin:25-jdk-alpine AS build

# 设置工作目录
WORKDIR /app

# 先复制 Maven Wrapper 和 pom.xml，利用 Docker 缓存层加速依赖下载
# 只要 pom.xml 不变，依赖层就不会重新构建
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 给 mvnw 添加可执行权限（Alpine Linux 默认不保留 Windows 的文件权限）
RUN chmod +x mvnw

# 下载所有依赖（利用 Docker 缓存，依赖不变时不会重复下载）
RUN ./mvnw dependency:go-offline -B

# 再复制源代码（源代码变更频率高，放在依赖下载之后）
COPY src src

# 编译打包，跳过测试（测试应在 CI 流水线中单独执行）
RUN ./mvnw package -DskipTests -B

# ============================================================
# 多阶段构建 - Stage 2: 运行阶段
# 使用更轻量的 JRE 镜像，减小最终镜像体积
# ============================================================
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# 从构建阶段复制打包好的 jar 文件
# 使用通配符匹配 jar 文件名，避免硬编码版本号
COPY --from=build /app/target/*.jar app.jar

# 暴露 Spring Boot 默认端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
