# Профилирование под нагрузкой

Разовый прогон приложения через JDK Flight Recorder (JFR) под синтетической нагрузкой - цель: посмотреть, куда реально уходит CPU-время на живом (не книжном) стенде, и найти узкие места, если они есть.

## Стенд

- Инфраструктура (Postgres, Kafka, Redis, discovery-server, camunda-service, notification-service, python-observer, api-gateway, Prometheus/Grafana) - в Docker Compose, как обычно.
- `finance-manager-app` - запущен локально (`mvn spring-boot:run`), не в контейнере: JFR/профайлеры цепляются к JVM-процессу по PID, с контейнеризованной JVM это ощутимо сложнее (нужен `--pid=host`/`SYS_ADMIN` или профайлер внутри образа), не оправдано ради разового замера.
- Инструмент: встроенный в JDK **JFR** (Java Flight Recorder), без сторонних агентов - работает одинаково на Linux/macOS/Windows. Управление записью - через `jcmd`, чтобы точно синхронизировать окно записи с окном нагрузки.
- Конвертация `.jfr` -> flame graph - через `jfr-converter.jar` (async-profiler tooling).

## Как гонялось

```bash
# найти PID запущенного процесса
jps

# стартовать 60-секундную JFR-запись
jcmd <PID> JFR.start duration=60s filename=profile.jfr

# синтетическая нагрузка на /actuator/health (60 параллельных curl-запросов в цикле)
for i in $(seq 1 3000); do curl -s -o /dev/null http://localhost:8080/actuator/health & done
wait

# конвертация в интерактивный flame graph
java -jar jfr-converter.jar profile.jfr flame.html
```

## Находка

`GET /actuator/health` - не дешёвый liveness-пробник, а полный агрегированный readiness-чек: на каждый вызов эндпоинт последовательно опрашивает все подключённые зависимости (Postgres, Redis, диск, Eureka) через соответствующие `HealthIndicator`.

Самая заметная статья расходов CPU под нагрузкой - проверка соединения с Postgres:

```
HealthEndpointSupport.getHealth
  -> getAggregateContribution -> getContribution
  -> DataSourceHealthIndicator.doHealthCheck -> isConnectionValid
  -> HikariProxyConnection.isValid -> PgConnection.isValid
  -> PGStream.receiveChar / ensureBytes (реальный socket round-trip до БД)
```

**227 из 1651 семпла (13.75%)** всего профиля ушли именно в этот путь - `Connection.isValid()` реально стучится в БД по сети на каждый вызов `/health`, это не кэшированная проверка.

## Вывод

Для сценария, где `/actuator/health` дёргается часто и извне (например, Kubernetes liveness/readiness пробы с нескольких реплик каждые несколько секунд) - такая цена на один health-чек ощутима и постоянна. Практическое решение: разделить `liveness` (дешёвый `ping`, без похода в зависимости) и `readiness` (полная проверка, но реже/с кэшем) через `management.endpoint.health.group.*` конфигурацию, а не бить одним тяжёлым агрегированным эндпоинтом.

Сырой профиль: [`flame3.html`](./flame3.html) - открывается в браузере, интерактивный (клик - зум в стек, Ctrl+F - поиск по функциям).
