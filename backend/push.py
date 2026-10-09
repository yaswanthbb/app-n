import base64
import json
import logging
import os
import threading
import time

import firebase_admin
from firebase_admin import credentials, messaging
from sqlalchemy import select

from database import PushRow

logger = logging.getLogger("newsapp.push")


class FirebaseSender:
    def __init__(self):
        raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
        encoded = os.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64", "").strip()
        self.app = None
        if not raw and not encoded:
            logger.warning("Firebase credentials are absent. Topic pushes remain in the database queue.")
            return
        try:
            if not raw:
                raw = base64.b64decode(encoded, validate=True).decode("utf-8")
            service_account = json.loads(raw)
            self.app = firebase_admin.initialize_app(credentials.Certificate(service_account), options={"httpTimeout": 15}, name="newsapp")
        except Exception as error:
            raise RuntimeError("Invalid Firebase service account configuration") from error

    @property
    def configured(self):
        return self.app is not None

    def send(self, row: PushRow):
        # Notification+data lets Android show background alerts automatically.
        # Its launcher extras contain `tab`; foreground messages use the service.
        return messaging.send(
            messaging.Message(
                topic=row.topic,
                notification=messaging.Notification(title=row.title, body=row.body),
                data={"tab": row.topic, "title": row.title, "url": row.url, "item_id": str(row.id)},
                android=messaging.AndroidConfig(
                    priority="high",
                    ttl=86400,
                    notification=messaging.AndroidNotification(channel_id="content_updates", icon="ic_notification", tag=f"item-{row.id}"),
                ),
            ),
            app=self.app,
        )

    def close(self):
        if self.app is not None:
            firebase_admin.delete_app(self.app)


class PushDispatcher:
    def __init__(self, sessions, sender):
        self.sessions = sessions
        self.sender = sender
        self.lock = threading.Lock()

    def flush(self):
        if not self.sender.configured:
            return
        # The configured Render process has one worker; serialize POST-triggered
        # and timer-triggered sends to avoid dispatching an outbox row twice.
        with self.lock, self.sessions() as session:
            pending = session.scalars(select(PushRow).where(PushRow.sent_at.is_(None), PushRow.next_attempt <= time.time()).order_by(PushRow.id).limit(100)).all()
            for row in pending:
                try:
                    self.sender.send(row)
                    row.sent_at = time.time()
                except Exception as error:
                    row.attempts += 1
                    row.next_attempt = time.time() + min(3600, 30 * 2 ** min(row.attempts, 7))
                    # Log only the exception type, never credentials or auth tokens.
                    logger.warning("Push %s queued after %s", row.id, type(error).__name__)
                session.commit()

    def status(self, item_id):
        with self.sessions() as session:
            row = session.get(PushRow, item_id)
            if row.sent_at is not None:
                return "sent"
            return "queued" if self.sender.configured else "not_configured"
