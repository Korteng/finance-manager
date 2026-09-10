"""Active probing: TCP-level (L4) and HTTP-level (L7) checks per target,
plus an optional Redis PING probe.

Metric names deliberately mirror Prometheus blackbox_exporter's own
convention (probe_success / probe_duration_seconds) rather than inventing
new ones — anyone who's read a blackbox_exporter dashboard before can read
this one without a legend. Percentiles (p50/p95/p99) are *not* computed in
Python: latencies go into Histogram buckets and p99 is derived at query
time with histogram_quantile() in PromQL, which is how you'd actually do
this in production — a Python-side "average of the last N" would just be
wrong under concurrent scrape/reset semantics.
"""

from __future__ import annotations

import asyncio
import logging
import time

import httpx
from prometheus_client import Counter, Gauge, Histogram

from .config import HTTP_TIMEOUT_SECONDS, TCP_TIMEOUT_SECONDS, Target

logger = logging.getLogger("observer.prober")

# Bucket boundaries in seconds. HTTP buckets are wider (app-level round trip,
# includes DB/Kafka work behind /actuator/health); TCP buckets are tight
# since a bare connect() on a local network should be sub-10ms.
PROBE_SUCCESS = Gauge(
    "probe_success",
    "Whether the last probe succeeded (1) or not (0)",
    ["target", "protocol"],
)
PROBE_DURATION = Histogram(
    "probe_duration_seconds",
    "End-to-end HTTP health-check latency (L7)",
    ["target"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5),
)
TCP_CONNECT_DURATION = Histogram(
    "probe_tcp_connect_duration_seconds",
    "Raw TCP connect latency (L4) — isolates network/socket time from app-level work",
    ["target"],
    buckets=(0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1),
)
PROBE_ERRORS = Counter(
    "probe_errors_total",
    "Number of failed probes",
    ["target", "protocol", "reason"],
)


class Prober:
    def __init__(self, targets: list[Target]):
        self.targets = targets
        self._client = httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS)

    async def probe_all(self) -> None:
        await asyncio.gather(*(self._probe_target(t) for t in self.targets))

    async def _probe_target(self, target: Target) -> None:
        # TCP and HTTP checks run concurrently — they answer different
        # questions ("is the port open" vs "is the app healthy") and one
        # shouldn't wait on the other.
        await asyncio.gather(
            self._tcp_probe(target),
            self._http_probe(target),
        )

    async def _tcp_probe(self, target: Target) -> None:
        start = time.perf_counter()
        try:
            reader, writer = await asyncio.wait_for(
                asyncio.open_connection(target.host, target.port),
                timeout=TCP_TIMEOUT_SECONDS,
            )
            writer.close()
            await writer.wait_closed()
            elapsed = time.perf_counter() - start
            TCP_CONNECT_DURATION.labels(target=target.name).observe(elapsed)
            PROBE_SUCCESS.labels(target=target.name, protocol="tcp").set(1)
        except Exception as exc:  # noqa: BLE001 — a probe must never raise
            PROBE_SUCCESS.labels(target=target.name, protocol="tcp").set(0)
            PROBE_ERRORS.labels(
                target=target.name, protocol="tcp", reason=type(exc).__name__
            ).inc()
            logger.warning("tcp probe failed for %s: %s", target.name, exc)

    async def _http_probe(self, target: Target) -> None:
        start = time.perf_counter()
        try:
            resp = await self._client.get(target.http_url)
            elapsed = time.perf_counter() - start
            PROBE_DURATION.labels(target=target.name).observe(elapsed)
            ok = resp.status_code == 200
            PROBE_SUCCESS.labels(target=target.name, protocol="http").set(1 if ok else 0)
            if not ok:
                PROBE_ERRORS.labels(
                    target=target.name, protocol="http", reason=f"status_{resp.status_code}"
                ).inc()
        except Exception as exc:  # noqa: BLE001
            PROBE_SUCCESS.labels(target=target.name, protocol="http").set(0)
            PROBE_ERRORS.labels(
                target=target.name, protocol="http", reason=type(exc).__name__
            ).inc()
            logger.warning("http probe failed for %s: %s", target.name, exc)

    async def aclose(self) -> None:
        await self._client.aclose()


class RedisProber:
    """Separate from Prober because a PING is a protocol-level check, not
    just a TCP connect — same distinction the JD draws between "network
    interaction" and just knowing a language."""

    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port
        self._redis = None

    def _client(self):
        if self._redis is None:
            import redis.asyncio as redis  # local import: optional dependency path

            self._redis = redis.Redis(host=self.host, port=self.port, socket_timeout=2)
        return self._redis

    async def probe(self) -> None:
        start = time.perf_counter()
        try:
            client = self._client()
            pong = await client.ping()
            elapsed = time.perf_counter() - start
            PROBE_DURATION.labels(target="redis").observe(elapsed)
            PROBE_SUCCESS.labels(target="redis", protocol="redis").set(1 if pong else 0)
        except Exception as exc:  # noqa: BLE001
            PROBE_SUCCESS.labels(target="redis", protocol="redis").set(0)
            PROBE_ERRORS.labels(target="redis", protocol="redis", reason=type(exc).__name__).inc()
            logger.warning("redis probe failed: %s", exc)
