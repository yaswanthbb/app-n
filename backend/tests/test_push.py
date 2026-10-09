import json

import pytest
from firebase_admin import _messaging_encoder, messaging

from database import PushRow
from push import FirebaseSender


@pytest.mark.parametrize("topic", ["deals", "picks"])
def test_real_admin_sdk_payload_has_title_and_tab(monkeypatch, topic):
    payloads = []
    firebase_app = object()

    def capture_send(message, app):
        assert app is firebase_app
        # Exercise the SDK's real serializer/validators without a network call.
        payloads.append(json.loads(_messaging_encoder.MessageEncoder().encode(message)))
        return "accepted-message-id"

    monkeypatch.setattr(messaging, "send", capture_send)
    sender = FirebaseSender.__new__(FirebaseSender)
    sender.app = firebase_app
    row = PushRow(id=42, topic=topic, title="Today's useful find", body="Open the app", url="https://example.com/item")
    assert sender.send(row) == "accepted-message-id"
    payload = payloads[0]
    assert payload["topic"] == topic
    assert payload["notification"]["title"] == row.title
    assert payload["data"]["tab"] == topic
    assert payload["data"]["item_id"] == "42"
    assert payload["android"]["priority"] == "high"
    assert payload["android"]["ttl"] == "86400s"
    assert payload["android"]["notification"]["channel_id"] == "content_updates"
    assert payload["android"]["notification"]["tag"] == "item-42"
