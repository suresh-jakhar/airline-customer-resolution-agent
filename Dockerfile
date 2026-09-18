# Stage 1: Build application with Maven & Eclipse Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight runtime environment with Eclipse Temurin JRE 21
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/customer-resolution-agent-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
