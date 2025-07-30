Finance Manager (v1.0.2)  
Spring Boot приложение для учета личных финансов  

Текущее состояние  
Release v1.0.2 — Рабочее ядро с базовым API  

Что реализовано  
1. Инфраструктура  
- Сборка: Maven + Docker (`Dockerfile`, `docker-compose.yml`)  
- База данных: PostgreSQL 15 + Flyway (миграции)  
- Ветки Git: `main`, `dev`, `feature/*`  

2. Функционал  
- API для транзакций:  
  - `POST /api/transactions` — Создание транзакции  
  - `GET /api/transactions/{id}` — Получение транзакции  
- Иерархия категорий:  
  - Родительские/дочерние связи (`Еда → Кафе`, `Транспорт → Такси`)  
- Документация:  
  - Swagger UI: `http://localhost:8080/swagger-ui.html`  

3. Тестирование  
- Юнит-тесты (H2)  
- Интеграционные тесты (Testcontainers + PostgreSQL)  

Ограничения  
- Нет авторизации (Security отключен)  
- Docker-образ не оптимизирован

Инструкция 

1. Локальный запуск (без Docker)  
bash
mvn spring-boot:run 
2. Сборка и запуск через Docker
bash
docker-compose up --build  # PostgreSQL + приложение
3. API-документация
После запуска:

Swagger: http://localhost:8080/swagger-ui.html

Health-check: http://localhost:8080/actuator/health
