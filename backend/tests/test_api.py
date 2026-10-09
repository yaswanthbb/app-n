import time

from fastapi.testclient import TestClient
import pytest
from sqlalchemy import select

from database import PushRow
from main import create_app

TOKEN = "test-publish-token"
AUTH = {"X-Publish-Token": TOKEN}
DEAL = {"title": "A free AI model", "description": "A free tier for small projects.", "url": "https://example.com/model", "source": "Example", "tag": "AI", "expires": None}
PICK = {"title": "Learn something useful", "body": "A small resource worth your time.", "url": "https://example.com/learn", "date": "2026-10-09"}


class FakeSender:
    configured = True
    fail = False

    def __init__(self):
        self.sent = []

    def send(self, row):
        if self.fail:
            raise ConnectionError("test outage")
        self.sent.append((row.topic, row.title))


@pytest.fixture
def setup(tmp_path):
    sender = FakeSender()
    app = create_app(f"sqlite:///{tmp_path / 'feeds.db'}", TOKEN, sender, retry_seconds=3600)
    with TestClient(app) as client:
        yield client, sender, app


def test_health_and_empty_feeds(setup):
    client, _, _ = setup
    assert client.get("/health").json() == {"status": "ok"}
    assert client.get("/api/deals").json() == []
    assert client.get("/api/picks").json() == []


@pytest.mark.parametrize("path,item", [("deals", DEAL), ("picks", PICK)])
def test_publishing_requires_token(setup, path, item):
    client, sender, _ = setup
    for headers in ({}, {"X-Publish-Token": "wrong"}):
        assert client.post(f"/api/{path}", json=item, headers=headers).status_code == 401
    assert client.get(f"/api/{path}").json() == []
    assert sender.sent == []


def test_publish_shapes_and_topics(setup):
    client, sender, _ = setup
    for topic, item in [("deals", DEAL), ("picks", PICK)]:
        response = client.post(f"/api/{topic}", json=item, headers=AUTH)
        assert response.status_code == 201
        assert response.json() == {"item": item, "push": "sent"}
        assert client.get(f"/api/{topic}").json() == [item]
    assert sender.sent == [("deals", DEAL["title"]), ("picks", PICK["title"])]


def test_one_pick_per_day_and_newest_first(setup):
    client, sender, _ = setup
    yesterday = {**PICK, "date": "2026-10-08", "title": "Yesterday's resource"}
    assert client.post("/api/picks", json=yesterday, headers=AUTH).status_code == 201
    assert client.post("/api/picks", json=PICK, headers=AUTH).status_code == 201
    assert client.post("/api/picks", json={**PICK, "title": "A second pick"}, headers=AUTH).status_code == 409
    assert client.get("/api/picks").json() == [PICK, yesterday]
    assert len(sender.sent) == 2


def test_duplicate_deal_does_not_push_again(setup):
    client, sender, _ = setup
    assert client.post("/api/deals", json=DEAL, headers=AUTH).status_code == 201
    assert client.post("/api/deals", json=DEAL, headers=AUTH).status_code == 409
    assert len(sender.sent) == 1


@pytest.mark.parametrize("changes", [
    {"tag": "Telco"}, {"title": "A free SIM card"}, {"description": "Free prepaid recharge"},
    {"title": "Premium AI", "description": "$20/month"}, {"url": "javascript:alert(1)"},
    {"expires": "tomorrow"}, {"expires": "null"}, {"expires": 123}, {"unexpected": True}, {"title": " "},
])
def test_invalid_or_nonsoftware_deals_rejected(setup, changes):
    client, sender, _ = setup
    assert client.post("/api/deals", json={**DEAL, **changes}, headers=AUTH).status_code == 422
    assert sender.sent == []


@pytest.mark.parametrize("changes", [{"date": "2026-02-30"}, {"date": "2026-1-1"}, {"body": ""}, {"url": "file:///etc/passwd"}])
def test_invalid_picks_rejected(setup, changes):
    client, _, _ = setup
    assert client.post("/api/picks", json={**PICK, **changes}, headers=AUTH).status_code == 422


def test_push_failure_keeps_content_and_retries(setup):
    client, sender, app = setup
    sender.fail = True
    response = client.post("/api/deals", json=DEAL, headers=AUTH)
    assert response.status_code == 201
    assert response.json()["push"] == "queued"
    assert client.get("/api/deals").json() == [DEAL]
    dispatcher = app.state.dispatcher
    with dispatcher.sessions() as session:
        row = session.scalar(select(PushRow))
        assert row.attempts == 1
        row.next_attempt = time.time() - 1
        session.commit()
    sender.fail = False
    dispatcher.flush()
    dispatcher.flush()
    assert sender.sent == [("deals", DEAL["title"])]


def test_no_firebase_is_accepted_and_persisted(setup):
    client, sender, app = setup
    sender.configured = False
    assert client.post("/api/picks", json=PICK, headers=AUTH).json()["push"] == "not_configured"
    assert client.get("/api/picks").json() == [PICK]
    sender.configured = True
    app.state.dispatcher.flush()
    assert sender.sent == [("picks", PICK["title"])]


def test_content_survives_process_restart(tmp_path):
    url = f"sqlite:///{tmp_path / 'persistent.db'}"
    with TestClient(create_app(url, TOKEN, FakeSender())) as client:
        assert client.post("/api/deals", json=DEAL, headers=AUTH).status_code == 201
    with TestClient(create_app(url, TOKEN, FakeSender())) as client:
        assert client.get("/api/deals").json() == [DEAL]


def test_missing_token_fails_closed(tmp_path):
    with pytest.raises(RuntimeError, match="PUBLISH_TOKEN"):
        with TestClient(create_app(f"sqlite:///{tmp_path / 'bad.db'}", "", FakeSender())):
            pass
