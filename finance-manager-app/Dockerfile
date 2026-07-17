# Этап сборки
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Скачиваем зависимости отдельно (кешируем этот слой)
RUN mvn dependency:go-offline
COPY src ./src
# Собираем JAR (тесты гоняются в CI до сборки образа)
RUN mvn package -DskipTests

# Этап запуска
FROM eclipse-temurin:17-jre
WORKDIR /app

# Запуск от непривилегированного пользователя
RUN useradd -r -u 1001 appuser
USER appuser

# Копируем JAR из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# JVM видит cgroup-лимиты контейнера, а не память хоста
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
