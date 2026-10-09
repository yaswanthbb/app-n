import pytest
from sqlalchemy import select

from database import PushRow
from test_api import AUTH, DEAL, PICK, setup


@pytest.mark.parametrize("topic,item", [("deals", DEAL), ("picks", PICK)])
def test_delete_auth_confirmation_and_missing_id(setup, topic, item):
    client, sender, _ = setup
    published = client.post(f"/api/{topic}", json=item, headers=AUTH).json()["item"]
    path = f"/api/{topic}/{published['id']}"
    for headers in ({}, {"X-Publish-Token": "wrong"}):
        assert client.delete(path, headers=headers).status_code == 401
        assert len(client.get(f"/api/{topic}").json()) == 1
    response = client.delete(path, headers=AUTH)
    assert response.status_code == 204
    assert response.content == b""
    assert client.get(f"/api/{topic}").json() == []
    assert client.delete(path, headers=AUTH).status_code == 404
    assert len(sender.sent) == 1  # Deletion does not publish an extra push.


def test_full_deal_details_round_trip(setup):
    client, _, _ = setup
    content = {**DEAL, "offer_details": "Free access to the model and examples.", "claim_steps": ["Open the site", "Create a free account", "Start using the model"]}
    response = client.post("/api/deals", json=content, headers=AUTH)
    assert response.status_code == 201
    item = response.json()["item"]
    assert item["offer_details"] == content["offer_details"]
    assert item["claim_steps"] == content["claim_steps"]
    assert client.get("/api/deals").json() == [item]


@pytest.mark.parametrize("steps", [None, []])
def test_nullable_and_empty_details(setup, steps):
    client, _, _ = setup
    response = client.post("/api/deals", json={**DEAL, "offer_details": None, "claim_steps": steps}, headers=AUTH)
    assert response.status_code == 201
    assert response.json()["item"]["claim_steps"] == steps


@pytest.mark.parametrize("steps", ["one step", [1], [None], [" "], ["x" * 2001]])
def test_invalid_steps_rejected(setup, steps):
    client, _, _ = setup
    assert client.post("/api/deals", json={**DEAL, "claim_steps": steps}, headers=AUTH).status_code == 422


def test_delete_cancels_unsent_push(setup):
    client, sender, app = setup
    sender.configured = False
    item = client.post("/api/deals", json=DEAL, headers=AUTH).json()["item"]
    assert client.delete(f"/api/deals/{item['id']}", headers=AUTH).status_code == 204
    with app.state.dispatcher.sessions() as session:
        assert session.scalar(select(PushRow)) is None
    sender.configured = True
    app.state.dispatcher.flush()
    assert sender.sent == []
