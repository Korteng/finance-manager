"""Runtime configuration for the observer sidecar.

Everything is env-driven on purpose — this is meant to run as a container
next to whatever services it's watching (finance-manager-app,
notification-service, or anything else with an HTTP health endpoint and a
TCP port), so the target list can't be hardcoded.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Target:
    name: str
    host: str
    port: int
    health_path: str = "/actuator/health"

    @property
    def http_url(self) -> str:
        return f"http://{self.host}:{self.port}{self.health_path}"


def load_targets() -> list[Target]:
    """Parse TARGETS env var: "name=host:port[:path],name2=host2:port2[:path2]".

    Defaults to the two finance-manager services (as reachable from inside
    the docker-compose network) so the sidecar works out of the box in this
    repo without extra config.
    """
    raw = os.environ.get(
        "TARGETS",
        "finance-manager-app=app:8080:/actuator/health,"
        "notification-service=notification-service:8081:/actuator/health",
    )
    targets: list[Target] = []
    for entry in raw.split(","):
        entry = entry.strip()
        if not entry:
            continue
        name, rest = entry.split("=", 1)
        parts = rest.split(":")
        host, port = parts[0], int(parts[1])
        path = parts[2] if len(parts) > 2 else "/actuator/health"
        targets.append(Target(name=name, host=host, port=port, health_path=path))
    return targets


POLL_INTERVAL_SECONDS = float(os.environ.get("POLL_INTERVAL_SECONDS", "5"))
TCP_TIMEOUT_SECONDS = float(os.environ.get("TCP_TIMEOUT_SECONDS", "2"))
HTTP_TIMEOUT_SECONDS = float(os.environ.get("HTTP_TIMEOUT_SECONDS", "3"))

# Optional: also probe Redis with a real PING (not just a TCP connect),
# since finance-manager-app already depends on Redis for JWT-blacklist
# lookups. Unset REDIS_HOST to skip this probe entirely.
REDIS_HOST = os.environ.get("REDIS_HOST")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))
