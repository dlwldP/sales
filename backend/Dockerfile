# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 의존성 레이어를 먼저 캐싱한다
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/quote-automation-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
