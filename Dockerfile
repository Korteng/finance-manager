# Этап сборки
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Скачиваем зависимости отдельно (кешируем этот слой)
RUN mvn dependency:go-offline
COPY src ./src
# Собираем JAR (пропускаем тесты для ускорения)
RUN mvn package -DskipTests

# Этап запуска
FROM eclipse-temurin:21-jre
WORKDIR /app
# Копируем JAR из этапа сборки
COPY --from=build /app/target/*.jar app.jar
# Точка входа
ENTRYPOINT ["java", "-jar", "app.jar"]