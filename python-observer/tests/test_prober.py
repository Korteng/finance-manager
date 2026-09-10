import asyncio

import pytest

from app.config import Target
from app.prober import PROBE_SUCCESS, Prober


async def _handle_health(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    """Bare-bones HTTP/1.1 server: always answers 200 on any request path,
    just enough for the prober's GET to succeed."""
    await reader.read(1024)
    body = b'{"status":"UP"}'
    response = (
        b"HTTP/1.1 200 OK\r\n"
        b"Content-Type: application/json\r\n"
        b"Content-Length: " + str(len(body)).encode() + b"\r\n"
        b"Connection: close\r\n\r\n" + body
    )
    writer.write(response)
    await writer.drain()
    writer.close()
    await writer.wait_closed()


@pytest.mark.asyncio
async def test_tcp_and_http_probe_success():
    server = await asyncio.start_server(_handle_health, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]
    async with server:
        target = Target(name="fake-svc", host="127.0.0.1", port=port, health_path="/actuator/health")
        prober = Prober([target])
        try:
            await prober._probe_target(target)
        finally:
            await prober.aclose()

        assert PROBE_SUCCESS.labels(target="fake-svc", protocol="tcp")._value.get() == 1
        assert PROBE_SUCCESS.labels(target="fake-svc", protocol="http")._value.get() == 1


@pytest.mark.asyncio
async def test_probe_marks_down_when_nothing_listening():
    # Port 1 is privileged/unassigned — nothing should ever be listening
    # here in a test sandbox, so the probe must fail cleanly, not hang or raise.
    target = Target(name="dead-svc", host="127.0.0.1", port=1, health_path="/actuator/health")
    prober = Prober([target])
    try:
        await asyncio.wait_for(prober._probe_target(target), timeout=5)
    finally:
        await prober.aclose()

    assert PROBE_SUCCESS.labels(target="dead-svc", protocol="tcp")._value.get() == 0
    assert PROBE_SUCCESS.labels(target="dead-svc", protocol="http")._value.get() == 0
