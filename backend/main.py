import asyncio
from contextlib import asynccontextmanager, suppress
from datetime import datetime, timezone
import hmac
import logging
import os
import time
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Response, status
from sqlalchemy import delete, func, select, text
from sqlalchemy.exc import IntegrityError
from starlette.concurrency import run_in_threadpool

from database import DealRow, PickRow, PushRow, connect_database
from migrate import require_current_schema
from push import FirebaseSender, PushDispatcher
from schemas import DealInput, PickInput


def serialize_deal(row):
    return {**{key: getattr(row, key) for key in ("id", "title", "description", "url", "source", "tag", "expires", "offer_details", "claim_steps")}, "created_at": publish_timestamp(row.created_at)}


def serialize_pick(row):
    return {**{key: getattr(row, key) for key in ("id", "title", "body", "url", "date")}, "created_at": publish_timestamp(row.created_at)}


def publish_timestamp(seconds):
    return datetime.fromtimestamp(seconds, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def create_app(database_url=None, publish_token=None, sender=None, retry_seconds=60):
    url = database_url or os.getenv("DATABASE_URL", "sqlite:///./newsapp.db")
    token = publish_token if publish_token is not None else os.getenv("PUBLISH_TOKEN", "")
    engine, sessions = connect_database(url)

    @asynccontextmanager
    async def lifespan(app):
        if not token.strip():
            raise RuntimeError("Set PUBLISH_TOKEN before starting the server")
        if os.getenv("RENDER") and url.startswith("sqlite:"):
            raise RuntimeError("Render's free disk is ephemeral. Set DATABASE_URL to persistent Postgres.")
        # The publisher runs migrations explicitly before deploying this version.
        # Startup verifies the schema but never mutates the live database schema.
        await run_in_threadpool(require_current_schema, engine)
        fcm = sender if sender is not None else FirebaseSender()
        dispatcher = PushDispatcher(sessions, fcm)
        app.state.dispatcher = dispatcher

        async def retry_outbox():
            while True:
                try:
                    await run_in_threadpool(dispatcher.flush)
                except Exception as error:
                    logging.getLogger("newsapp.push").warning("Outbox retry deferred after %s", type(error).__name__)
                await asyncio.sleep(retry_seconds)

        task = asyncio.create_task(retry_outbox())
        try:
            yield
        finally:
            task.cancel()
            with suppress(asyncio.CancelledError):
                await task
            if sender is None:
                fcm.close()
            engine.dispose()

    app = FastAPI(title="News App Publisher", version="1.2.0", lifespan=lifespan)

    def require_token(x_publish_token: Annotated[str | None, Header(alias="X-Publish-Token")] = None):
        if x_publish_token is None or not hmac.compare_digest(x_publish_token.encode(), token.encode()):
            raise HTTPException(status_code=401, detail="Invalid publish token")

    @app.get("/health")
    def health():
        with sessions() as session:
            session.execute(text("SELECT 1"))
        return {"status": "ok"}

    @app.get("/api/deals")
    def get_deals():
        with sessions() as session:
            return [serialize_deal(row) for row in session.scalars(select(DealRow).order_by(DealRow.created_at.desc(), DealRow.id.desc()))]

    @app.get("/api/picks")
    def get_picks():
        with sessions() as session:
            return [serialize_pick(row) for row in session.scalars(select(PickRow).order_by(PickRow.date.desc(), PickRow.id.desc()))]

    def publish(model, fields, topic, serializer):
        row = model(**fields, created_at=time.time())
        push = PushRow(topic=topic, title=fields["title"], url=fields["url"], body="Machine's daily pick is ready." if topic == "picks" else "A new free software offer is ready to claim.")
        with sessions() as session:
            session.add_all([row, push])
            try:
                session.commit()
            except IntegrityError:
                session.rollback()
                duplicate = select(model.id).where(model.url == fields["url"])
                if topic == "picks":
                    duplicate = duplicate.where(model.title == fields["title"], model.date == fields["date"])
                if session.scalar(duplicate.limit(1)) is None:
                    raise  # Unrelated integrity failures are not duplicate publications.
                detail = "This title, URL, and date have already been published" if topic == "picks" else "A deal already exists for this URL"
                raise HTTPException(status_code=409, detail=detail)
            item, push_id = serializer(row), push.id
        # Persist the content and push in one transaction before contacting FCM.
        # A transport failure is accepted, visible as `queued`, and retried.
        app.state.dispatcher.flush()
        return {"item": item, "push": app.state.dispatcher.status(push_id)}

    @app.post("/api/deals", status_code=status.HTTP_201_CREATED, dependencies=[Depends(require_token)])
    def post_deal(item: DealInput):
        return publish(DealRow, item.model_dump(mode="json"), "deals", serialize_deal)

    @app.post("/api/picks", status_code=status.HTTP_201_CREATED, dependencies=[Depends(require_token)])
    def post_pick(item: PickInput):
        return publish(PickRow, item.model_dump(mode="json"), "picks", serialize_pick)

    def remove(model, item_id, topic):
        # Match the dispatcher's lock so an unsent outbox item cannot race deletion.
        with app.state.dispatcher.lock, sessions() as session:
            row = session.get(model, item_id)
            if row is None:
                raise HTTPException(status_code=404, detail="Item not found")
            # Old outbox rows identify content by its unique (topic, URL, title).
            # Remove only pending pushes; delivered notifications can't be recalled.
            matching_items = session.scalar(select(func.count()).select_from(model).where(model.url == row.url, model.title == row.title))
            if matching_items == 1:
                session.execute(delete(PushRow).where(PushRow.topic == topic, PushRow.url == row.url, PushRow.title == row.title, PushRow.sent_at.is_(None)))
            session.delete(row)
            session.commit()
        return Response(status_code=204)

    @app.delete("/api/deals/{id}", status_code=204, dependencies=[Depends(require_token)])
    def delete_deal(id: int):
        return remove(DealRow, id, "deals")

    @app.delete("/api/picks/{id}", status_code=204, dependencies=[Depends(require_token)])
    def delete_pick(id: int):
        return remove(PickRow, id, "picks")

    return app


app = create_app()
