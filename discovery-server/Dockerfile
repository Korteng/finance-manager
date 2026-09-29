# Этап сборки
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Этап запуска
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
