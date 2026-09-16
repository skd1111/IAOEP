FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 复制本地 SDK (Phase 1 PR 4 产物)
COPY ../../sdk/java /sdk-java

# 构建 demo
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/spring-ai-customer-service-demo-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
