"""python-observer — a small SRE sidecar that probes the other services in
this repo (finance-manager-app, notification-service) over HTTP and raw TCP,
and exposes Prometheus metrics for it.

Run standalone:
    TARGETS="finance-manager-app=localhost:8080:/actuator/health" \
        uvicorn app.main:app --port 9100

Or via docker-compose (see ../docker-compose.observer.yml).
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from .config import POLL_INTERVAL_SECONDS, REDIS_HOST, REDIS_PORT, load_targets
from .prober import Prober, RedisProber

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s"
)
logger = logging.getLogger("observer")

prober = Prober(load_targets())
redis_prober = RedisProber(REDIS_HOST, REDIS_PORT) if REDIS_HOST else None


async def _poll_loop() -> None:
    while True:
        try:
            tasks = [prober.probe_all()]
            if redis_prober is not None:
                tasks.append(redis_prober.probe())
            await asyncio.gather(*tasks)
        except Exception:  # noqa: BLE001 — the loop must survive a bad cycle
            logger.exception("probe cycle failed")
        await asyncio.sleep(POLL_INTERVAL_SECONDS)


@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(_poll_loop())
    logger.info(
        "observer started: targets=%s redis=%s interval=%ss",
        [t.name for t in prober.targets],
        bool(redis_prober),
        POLL_INTERVAL_SECONDS,
    )
    yield
    task.cancel()
    await prober.aclose()


app = FastAPI(title="python-observer", lifespan=lifespan)


@app.get("/metrics")
async def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "targets": [t.name for t in prober.targets],
        "redis_probe_enabled": redis_prober is not None,
    }
