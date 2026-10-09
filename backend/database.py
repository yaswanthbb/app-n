from sqlalchemy import Float, Integer, JSON, String, Text, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker


class Base(DeclarativeBase):
    pass


class DealRow(Base):
    __tablename__ = "deals"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(Text)
    url: Mapped[str] = mapped_column(Text, unique=True)
    source: Mapped[str] = mapped_column(String(120))
    tag: Mapped[str] = mapped_column(String(30))
    expires: Mapped[str | None] = mapped_column(String(10), nullable=True)
    offer_details: Mapped[str | None] = mapped_column(Text, nullable=True)
    claim_steps: Mapped[list[str] | None] = mapped_column(JSON(none_as_null=True), nullable=True)
    created_at: Mapped[float] = mapped_column(Float)


class PickRow(Base):
    __tablename__ = "picks"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(Text)
    url: Mapped[str] = mapped_column(Text)
    date: Mapped[str] = mapped_column(String(10), unique=True)
    created_at: Mapped[float] = mapped_column(Float)


class PushRow(Base):
    __tablename__ = "push_outbox"
    id: Mapped[int] = mapped_column(primary_key=True)
    topic: Mapped[str] = mapped_column(String(10))
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(String(200))
    url: Mapped[str] = mapped_column(Text)
    sent_at: Mapped[float | None] = mapped_column(Float, nullable=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    next_attempt: Mapped[float] = mapped_column(Float, default=0)


def connect_database(url: str):
    # Render exports postgresql://, while SQLAlchemy needs the psycopg driver.
    if url.startswith("postgres://"):
        url = "postgresql+psycopg://" + url[len("postgres://"):]
    elif url.startswith("postgresql://"):
        url = "postgresql+psycopg://" + url[len("postgresql://"):]
    sqlite = url.startswith("sqlite:")
    engine = create_engine(url, pool_pre_ping=True, connect_args={"check_same_thread": False, "timeout": 30} if sqlite else {})
    if sqlite:
        @event.listens_for(engine, "connect")
        def configure_sqlite(connection, _):
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("PRAGMA busy_timeout=30000")
    return engine, sessionmaker(engine, expire_on_commit=False)
