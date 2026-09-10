import os

from app.config import load_targets


def test_load_targets_default(monkeypatch):
    monkeypatch.delenv("TARGETS", raising=False)
    targets = load_targets()
    assert [t.name for t in targets] == ["finance-manager-app", "notification-service"]
    assert targets[0].host == "app"
    assert targets[0].port == 8080
    assert targets[0].http_url == "http://app:8080/actuator/health"


def test_load_targets_custom_single(monkeypatch):
    monkeypatch.setenv("TARGETS", "my-svc=localhost:9000")
    targets = load_targets()
    assert len(targets) == 1
    assert targets[0] == targets[0]
    assert targets[0].name == "my-svc"
    assert targets[0].host == "localhost"
    assert targets[0].port == 9000
    assert targets[0].health_path == "/actuator/health"


def test_load_targets_custom_path(monkeypatch):
    monkeypatch.setenv("TARGETS", "svc=host:1234:/healthz")
    targets = load_targets()
    assert targets[0].health_path == "/healthz"
    assert targets[0].http_url == "http://host:1234/healthz"


def test_load_targets_multiple(monkeypatch):
    monkeypatch.setenv("TARGETS", "a=h1:1, b=h2:2:/p")
    targets = load_targets()
    assert [t.name for t in targets] == ["a", "b"]
