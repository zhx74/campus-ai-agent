# ============================================
# 阶段1: 构建 — 用 Maven 编译打包
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom 文件，利用 Docker 缓存层加速
COPY pom.xml .
COPY campus-common/pom.xml campus-common/
COPY campus-pojo/pom.xml campus-pojo/
COPY campus-ai/pom.xml campus-ai/
COPY campus-server/pom.xml campus-server/

# 下载依赖（改代码不改 pom 时不会重复下载）
RUN mvn dependency:go-offline -B

# 复制源码
COPY campus-common/src campus-common/src
COPY campus-pojo/src campus-pojo/src
COPY campus-ai/src campus-ai/src
COPY campus-server/src campus-server/src

# 编译打包（跳过测试，因为没有测试文件）
RUN mvn package -DskipTests -B

# ============================================
# 阶段2: 运行 — 只保留 JAR，镜像尽量小
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/campus-server/target/campus-server-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
