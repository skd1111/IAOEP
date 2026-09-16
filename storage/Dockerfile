FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 缓存依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 构建
COPY src ./src
RUN mvn package -DskipTests -B

# 运行镜像
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/iaoep-storage-*.jar app.jar

EXPOSE 8081 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
