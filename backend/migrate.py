"""Manual versioned migrations and read-only startup schema verification."""
import argparse
from contextlib import contextmanager
import os
from pathlib import Path

from alembic import command
from alembic.config import Config
from alembic.migration import MigrationContext
from alembic.script import ScriptDirectory
from sqlalchemy import text
from sqlalchemy import inspect

from database import connect_database


@contextmanager
def migration_connection(engine):
    with engine.begin() as connection:
        if engine.dialect.name == "postgresql":
            # Serialize concurrent migration commands on the same database.
            connection.execute(text("SELECT pg_advisory_xact_lock(726394182)"))
        elif engine.dialect.name == "sqlite":
            # sqlite3 otherwise defers BEGIN, so DDL may escape a transaction.
            connection.exec_driver_sql("BEGIN IMMEDIATE")
        yield connection


def upgrade_database(engine):
    root = Path(__file__).resolve().parent
    config = Config(str(root / "alembic.ini"))
    config.set_main_option("script_location", str(root / "migrations"))
    with migration_connection(engine) as connection:
        config.attributes["connection"] = connection
        command.upgrade(config, "head")


def require_current_schema(engine):
    root = Path(__file__).resolve().parent
    config = Config(str(root / "alembic.ini"))
    config.set_main_option("script_location", str(root / "migrations"))
    head = ScriptDirectory.from_config(config).get_current_head()
    with engine.connect() as connection:
        current = MigrationContext.configure(connection).get_current_revision()
    if current != head:
        raise RuntimeError("Database migration required. Run python migrate.py with this database's DATABASE_URL before starting the updated backend.")
    if not {"offer_details", "claim_steps"} <= {column["name"] for column in inspect(engine).get_columns("deals")}:
        raise RuntimeError("The migration revision is set but deal detail columns are missing; inspect the schema rather than stamping head.")
    inspector = inspect(engine)
    constraints = inspector.get_unique_constraints("picks")
    date_unique = any(c["column_names"] == ["date"] for c in constraints) or any(i.get("unique") and i["column_names"] == ["date"] for i in inspector.get_indexes("picks"))
    composite_unique = any(set(c["column_names"]) == {"title", "url", "date"} for c in constraints)
    if date_unique or not composite_unique:
        raise RuntimeError("Pick uniqueness does not match migration 0003; inspect the schema rather than stamping head.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-postgres", action="store_true", help="Refuse a local SQLite fallback when migrating Neon")
    args = parser.parse_args()
    # Optional local private configuration; explicit environment values win.
    from dotenv import load_dotenv
    load_dotenv(Path(__file__).with_name(".env"))
    url = os.getenv("DATABASE_URL", "sqlite:///./newsapp.db")
    engine, _ = connect_database(url)
    try:
        if args.require_postgres and engine.dialect.name != "postgresql":
            raise RuntimeError("Set DATABASE_URL to the intended Neon Postgres connection before migrating")
        upgrade_database(engine)
        with engine.connect() as connection:
            revision = MigrationContext.configure(connection).get_current_revision()
        print(f"Migration complete: {revision} ({engine.dialect.name})")
    finally:
        engine.dispose()
