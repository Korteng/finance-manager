# python-observer

A small SRE/observability sidecar written in Python (FastAPI + `prometheus_client`) that actively probes the other services in this repo — `finance-manager-app` and `notification-service` — over both raw TCP (L4) and HTTP (L7), and exposes the results as Prometheus metrics.

## Why this exists

The rest of this repo is Java. This service isn't here to duplicate that — it's here to demonstrate a specific, separate skill: writing a small Python network-facing service and reasoning about service reliability at the network layer, not just the application layer.

It deliberately does two different kinds of check per target, because they answer different questions:

- **TCP connect (L4)** — is the port even open? Isolates raw network/socket latency from anything the application is doing.
- **HTTP health check (L7)** — is the *application* healthy, not just the port? Hits `/actuator/health` and measures the full round trip.

Metric names mirror Prometheus's own `blackbox_exporter` convention (`probe_success`, `probe_duration_seconds`) rather than inventing new ones, so the output is legible to anyone who's used blackbox_exporter before.

## Why percentiles aren't computed in Python

Latencies are recorded into `Histogram` buckets, not averaged or percentiled in application code. p50/p95/p99 are derived at query time in PromQL:

```promql
histogram_quantile(0.99, sum(rate(probe_duration_seconds_bucket[5m])) by (le, target))
```

Computing "p99 of the last N samples" in Python would be wrong under concurrent scraping and doesn't survive a restart — bucketed histograms are how this is actually done in production.

## Running it

Standalone, against anything with a TCP port and (optionally) an HTTP health endpoint:

```bash
TARGETS="my-service=localhost:8080:/actuator/health" \
    uvicorn app.main:app --port 9100
```

`TARGETS` format: `name=host:port[:path]`, comma-separated. `path` defaults to `/actuator/health`. Set `REDIS_HOST` (and optionally `REDIS_PORT`, default 6379) to also probe Redis with a real `PING`, not just a TCP connect — `finance-manager-app` already depends on Redis for its JWT blacklist, so this closes the loop on that.

Via docker-compose (already wired into the repo's root `docker-compose.yml` and scraped by the existing Prometheus):

```bash
docker-compose up --build observer
```

Metrics: `http://localhost:9100/metrics`. Liveness: `http://localhost:9100/health`.

## Tests

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest tests/ -v
```

The probe tests spin up a real local TCP/HTTP listener and assert against it — not mocked — so a broken probe implementation actually fails the suite.
