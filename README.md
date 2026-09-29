# Finance Manager

Персональное приложение для учёта финансов с событийно-ориентированной микросервисной архитектурой: создание транзакции публикует событие в Kafka, которое асинхронно обрабатывает отдельный сервис уведомлений. Backend на Spring Boot, фронтенд на React.

## Архитектура

```mermaid
flowchart LR
    CLIENT["frontend / клиент"] --> GW["api-gateway<br/>(Spring Cloud Gateway)"]
    GW -- "lb://" --> APP["finance-manager-app<br/>(REST API, JWT)"]
    GW -- "lb://" --> NOTIF["notification-service<br/>(Kafka consumer)"]

    GRAF["Grafana"] --> PROM["Prometheus"]
    PROM -- "scrape" --> OBS["python-observer<br/>(FastAPI)"]
    PROM -- "scrape" --> APP
    PROM -- "scrape" --> NOTIF

    FE["frontend<br/>(React + Vite)"] -- "REST" --> APP

    OBS -. "probe" .-> APP
    OBS -. "probe" .-> NOTIF
    OBS -. "PING опционально" .-> REDIS[("Redis<br/>JWT blacklist")]

    APP --> DB1[("finance_db<br/>(Postgres)")]
    APP -- "logout" --> REDIS
    APP --> KAFKA{{"Kafka<br/>transaction-events"}}
    KAFKA --> NOTIF
    NOTIF --> DB2[("notification_db<br/>(Postgres)")]

    APP -. "register" .-> DISC["discovery-server<br/>(Eureka)"]
    NOTIF -. "register" .-> DISC
    GW -. "register" .-> DISC
    GW -. "discover" .-> DISC

    CAMUNDA["camunda-service<br/>(Camunda 7 BPM, вне Eureka)"] --> DB3[("camunda_db<br/>(Postgres)")]
```

**Принцип разделения:** `finance-manager-app` отвечает за бизнес-логику транзакций и не знает, кто и как обрабатывает события - публикует их в Kafka и продолжает работу. `notification-service` независимо читает поток событий, ведёт собственный лог уведомлений в отдельной БД и может быть остановлен/обновлён без влияния на основной сервис. `python-observer` не входит в путь запроса ни одного из сервисов - это внешний наблюдатель, который опрашивает их снаружи и может быть остановлен без какого-либо влияния на работу приложения. `frontend` - обычный клиент REST API, не участвует в событийном обмене. Оба прикладных сервиса регистрируются в `discovery-server` (Eureka) - единый реестр живых инстансов используется `api-gateway` (Spring Cloud Gateway) для роутинга по логическому имени сервиса (`lb://FINANCE-MANAGER-APP`, `lb://NOTIFICATION-SERVICE`), без хардкода адресов и портов. Клиент обращается только к `api-gateway` - какой сервис и сколько его инстансов реально поднято, от него скрыто.
```
## Стек технологий

**Backend**
- **Java 17**, Spring Boot 3.5.16
- **Spring Security + JWT** - аутентификация без сессий (stateless), секрет подписи берётся из переменной окружения `APP_JWT_SECRET`, а не хранится в коде
- **Spring Data JPA + PostgreSQL** - отдельная БД на каждый сервис, сущности и DTO разнесены по разным пакетам, наружу через API отдаются только DTO
- **Flyway** - версионирование схемы БД
- **Apache Kafka** (KRaft mode, без Zookeeper) - асинхронный обмен событиями между сервисами
- **Redis** - blacklist для отозванных JWT-токенов (logout)
- **Docker Compose** - оркестрация всех сервисов для локальной разработки
- **Kubernetes** - манифесты Deployment/Service/ConfigMap/Secret/HPA/Ingress для оркестрации в кластере, подробности в [`K8S.md`](K8S.md)
- **Spring Cloud Netflix Eureka** - service discovery, `finance-manager-app` и `notification-service` регистрируются как клиенты в `discovery-server`, дашборд доступен на `:8761`
- **Spring Cloud Gateway** - единая точка входа (`:8090`), роутинг по Eureka service discovery (`/api/notifications/**` -> notification-service, остальное `/api/**` -> finance-manager-app)
- **Camunda 7 (BPM engine)** - процесс `transaction-approval` (одобрение крупной транзакции), один Java service-task делегат + один User Task, Cockpit/Tasklist UI на `:8082`, детали в разделе ниже
- **gRPC** - один unary-эндпоинт (`GetTransaction`) поверх той же бизнес-логики, что и REST `GET /api/transactions/{id}` - proto-контракт в [`finance-manager-app/src/main/proto/transaction.proto`](finance-manager-app/src/main/proto/transaction.proto)
- **Prometheus + Grafana** - мониторинг JVM-метрик, HTTP-запросов, GC, Kafka producer/consumer
- **JUnit 5, Mockito, Testcontainers** - юнит-тесты на сервисном слое (включая проверку бюджетной логики через моки Kafka) и интеграционные тесты с реальным Postgres
- **GitHub Actions + CodeQL** - CI и статический анализ безопасности

**Frontend**
- **React 19 + Vite** - тёмная тема, две вкладки (Транзакции, Бюджеты)
- Токен JWT живёт только в памяти вкладки, без localStorage - разлогин при обновлении страницы, для пет-проекта это осознанный компромисс в пользу простоты

## Быстрый старт

Backend и инфраструктура:

```bash
git clone https://github.com/Korteng/finance-manager.git
cd finance-manager
cp .env.example .env
# отредактируй .env - задай реальные пароли для БД и свой APP_JWT_SECRET
docker compose up --build
```

После запуска доступны:

| Сервис | URL |
|---|---|
| finance-manager-app | http://localhost:8080 |
| notification-service | http://localhost:8081 |
| discovery-server (Eureka) | http://localhost:8761 |
| api-gateway | http://localhost:8090 |
| camunda-service (Cockpit/Tasklist) | http://localhost:8082 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Kafka | localhost:9092 |
| python-observer | http://localhost:9100/metrics |

Frontend (отдельно, в соседней консоли):

```bash
cd frontend
npm install
npm run dev
```

По умолчанию фронтенд ходит на `http://localhost:8080`, переопределяется переменной `VITE_API_BASE_URL`. Приложение доступно на `http://localhost:5173`.

## API

### Аутентификация (finance-manager-app)

```
POST /api/auth/register   { "username": "...", "password": "..." }  → { "token": "..." }
POST /api/auth/login      { "username": "...", "password": "..." }  → { "token": "..." }
POST /api/auth/logout     Authorization: Bearer <token>  → 200 OK
```

Токен `logout` кладётся в Redis-blacklist с TTL, равным оставшемуся сроку его действия - дальше `JwtAuthFilter` отклоняет его при каждом запросе, даже если подпись валидна и срок жизни токена не истёк.

### Категории (finance-manager-app, требует JWT)

```
GET /api/categories
GET /api/categories/{id}
```

### Транзакции (finance-manager-app, требует JWT в заголовке `Authorization: Bearer <token>`)

```
POST /api/transactions   { "amount": 500.00, "currency": "RUB", "categoryId": 1, "description": "..." }
GET  /api/transactions/{id}
GET  /api/transactions?categoryId=&from=&to=
```

При успешном создании транзакции в Kafka-топик `transaction-events` публикуется событие `TRANSACTION_CREATED`. Если для категории и текущего периода задан бюджет и сумма трат по нему превышена, дополнительно публикуется `BUDGET_EXCEEDED`.

### Бюджеты (finance-manager-app, требует JWT)

```
POST /api/budgets   { "categoryId": 1, "period": "2026-09", "limitAmount": 20000.00 }
GET  /api/budgets?period=2026-09
```

`GET` без параметра `period` возвращает бюджеты за текущий месяц, для каждого - лимит, фактические траты по категории за период и флаг `exceeded`.

### gRPC (finance-manager-app, порт 9091)

Тот же `GET /api/transactions/{id}` доступен и как unary gRPC-вызов - оба маршрута (REST и gRPC) вызывают один и тот же `TransactionService`, так что проверка владельца транзакции работает одинаково в обоих случаях. Контракт - [`transaction.proto`](finance-manager-app/src/main/proto/transaction.proto).

```
service TransactionGrpcService {
  rpc GetTransaction (GetTransactionRequest) returns (TransactionGrpcResponse);
}
```

Проверка через [grpcurl](https://github.com/fullstorydev/grpcurl) (сервис пока без reflection, схему передаём явно):

```bash
grpcurl -plaintext -import-path finance-manager-app/src/main/proto -proto transaction.proto \
  -d '{"id": 1, "user_id": 1}' \
  localhost:9091 ru.korteng.financemanager.grpc.TransactionGrpcService/GetTransaction
```

### Уведомления (notification-service)

```
GET /api/notifications?userId={id}&page=0&size=20
```

Возвращает лог событий, обработанных сервисом уведомлений для указанного пользователя.

### Мониторинг (оба сервиса)

```
GET /actuator/health              - статус приложения
GET /actuator/health/liveness     - liveness probe
GET /actuator/health/readiness    - readiness probe
GET /actuator/prometheus          - метрики в формате Prometheus
GET /actuator/info                - версия и метаданные приложения
```

## python-observer - SRE-sidecar

Отдельный Python-сервис (FastAPI + `prometheus_client`), который активно опрашивает `finance-manager-app` и `notification-service` - не изнутри JVM, а снаружи, как это делал бы `blackbox_exporter`. На каждый таргет два независимых чек:

- **TCP connect (L4)** - открыт ли порт вообще, изолированно от логики приложения
- **HTTP health-check (L7)** - отвечает ли `/actuator/health`, с полным замером round-trip

Опционально проверяет и сам Redis настоящим `PING` (не просто TCP-коннект) - `finance-manager-app` зависит от него для JWT-blacklist, так что это закрывает цепочку мониторинга целиком.

Метрики - `Histogram`, не усреднённые/перцентильные значения из Python: p50/p95/p99 считаются в момент запроса через PromQL, как это принято в проде:

```promql
histogram_quantile(0.99, sum(rate(probe_duration_seconds_bucket[5m])) by (le, target))
```

Уже вписан в корневой `docker-compose.yml` и в scrape-конфиг Prometheus. Метрики: `http://localhost:9100/metrics`. Liveness: `http://localhost:9100/health`.

Подробности и локальный запуск без compose - в [`python-observer/README.md`](python-observer/README.md).

## Тестирование

```bash
cd finance-manager-app
./mvnw test
```

Юнит-тесты сервисного слоя (Mockito, без поднятия контекста) и интеграционные тесты (`contextLoads`), поднимающие реальный Postgres через Testcontainers - требуется запущенный Docker.

## Frontend: скриншот

![Frontend](docs/screenshots/frontend.png)

## Мониторинг: скриншоты

Дашборд Grafana (JVM Micrometer) под нагрузкой - 50 транзакций подряд, виден отклик CPU/GC/Threads/Heap:

![JVM Overview](docs/screenshots/grafana-jvm-overview.jpg)

![GC & Memory Pools](docs/screenshots/grafana-gc-memory.jpg)

Дашборд Eureka (`discovery-server`) - оба прикладных сервиса зарегистрированы и в статусе UP:

![Eureka Dashboard](docs/screenshots/eureka-dashboard.png)

Camunda Cockpit - диаграмма процесса `transaction-approval` с реальными ветками (авто-выполнение / ручное одобрение):

![Camunda Cockpit](docs/screenshots/camunda-cockpit-process.png)

## Kubernetes

Три прикладных сервиса (finance-manager-app, notification-service, python-observer) деплоятся в кластер через Deployment + Service, внешняя инфраструктура (Kafka/Postgres/Redis) остаётся в Docker Compose. Настроено горизонтальное автомасштабирование (HPA) с демонстрацией под реальной нагрузкой, а также разбор проблем, с которыми столкнулись при развёртывании (конфликт портов, медленный старт JVM под пробами, локальные образы без registry).

Подробности, манифесты и скриншоты - в [`K8S.md`](K8S.md).

## Camunda BPM - одобрение крупной транзакции

Отдельный сервис `camunda-service` (Camunda 7, embedded engine) - демонстрация BPMN-процесса поверх бизнес-логики Finance Manager, не встроена в основной event-flow (Kafka), запускается по явному REST-вызову.

Процесс `transaction-approval` ([`camunda-service/src/main/resources/processes/transaction-approval.bpmn`](camunda-service/src/main/resources/processes/transaction-approval.bpmn)):

1. **Новая транзакция** (start event) - на вход приходят `transactionId` и `amount`.
2. **Проверка суммы** (service task, `AmountCheckDelegate` - единственный Java-делегат) - сравнивает сумму с лимитом (50 000), выставляет переменную `requiresApproval`.
3. **Требуется одобрение?** (exclusive gateway) - если сумма в пределах лимита, транзакция считается выполненной автоматически; если превышен - процесс уходит в очередь на ручное одобрение.
4. **Одобрение крупной транзакции** (user task, `candidateGroups=finance-approvers`) - открытая задача видна в Tasklist (`:8082`) и через REST.

```bash
# Запустить процесс
curl -X POST http://localhost:8082/api/camunda/transactions/approval-process \
  -H "Content-Type: application/json" \
  -d '{"transactionId": "tx-123", "amount": 75000}'

# Посмотреть открытые задачи (появится, если сумма выше лимита)
curl http://localhost:8082/api/camunda/tasks

# Одобрить задачу
curl -X POST "http://localhost:8082/api/camunda/tasks/{taskId}/approve?comment=ok"
```

Cockpit (мониторинг запущенных процессов) и Tasklist (список задач) доступны на `http://localhost:8082` - логин/пароль admin/`${CAMUNDA_ADMIN_PASSWORD}` из `.env`.

`camunda-service` намеренно не регистрируется в Eureka (`eureka.client.enabled: false`) - Spring Cloud Netflix Eureka client конфликтует на classpath с Jersey, который приносит Camunda REST (`camunda-bpm-spring-boot-starter-rest`): известная связка багов в автоконфигурации (`EurekaServiceRegistry.maybeInitializeClient` падает в NPE, `getEurekaClient()` возвращает null). Пробовал явно добавлять `httpclient5` - не помогло. Поскольку camunda-service никто не находит через service discovery (обращение напрямую по REST), решил не тратить время на дальнейший дебаг этой конкретной связки библиотек и просто отключил регистрацию - осознанный компромисс, а не недоделка.

Camunda 7 Community Edition как отдельный дистрибутив достиг end-of-life (7.24.0, октябрь 2025 - последний релиз на Maven Central), но остаётся частью многих действующих enterprise-систем; концепция BPMN/Cockpit/Tasklist от этого не меняется.

## Структура репозитория

```
finance-manager/
├── finance-manager-app/      - основной REST API, JWT-авторизация, Kafka producer
├── notification-service/     - Kafka consumer, независимая БД
├── discovery-server/         - Eureka-сервер service discovery, регистрация обоих прикладных сервисов
├── api-gateway/              - Spring Cloud Gateway, единая точка входа, роутинг по Eureka
├── camunda-service/          - Camunda 7 BPM, процесс transaction-approval
├── frontend/                  - React + Vite клиент REST API
├── python-observer/          - Python/FastAPI-сайдкар: TCP+HTTP пробы обоих сервисов, опционально Redis PING
├── prometheus/
│   └── prometheus.yml        - конфигурация scrape для finance-manager-app, notification-service, python-observer
├── k8s/                       - манифесты Kubernetes (Deployment/Service/ConfigMap/Secret/HPA/Ingress)
├── K8S.md                     - гайд по развёртыванию в Kubernetes и демонстрация HPA
├── docker-compose.yml        - оркестрация: 2×Postgres, Redis, Kafka, оба сервиса, python-observer, Prometheus, Grafana
└── .env.example               - шаблон переменных окружения
```

## Roadmap

- [ ] Реальная доставка уведомлений (email/webhook) вместо только записи в БД
- [ ] Отдельный Grafana-дашборд под Kafka producer/consumer lag
- [ ] Provisioning Grafana-дашбордов через конфиг (без ручного импорта)
