import os

from alembic import context
from database import Base, connect_database

config = context.config


def migrate(connection):
    context.configure(connection=connection, target_metadata=Base.metadata)
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    raise RuntimeError("These migrations inspect existing tables; use an online database connection")
elif config.attributes.get("connection") is not None:
    migrate(config.attributes["connection"])
else:
    # Direct Alembic commands use the same engine/lock path as migrate.py.
    from migrate import migration_connection
    engine, _ = connect_database(os.getenv("DATABASE_URL", "sqlite:///./newsapp.db"))
    try:
        with migration_connection(engine) as connection:
            migrate(connection)
    finally:
        engine.dispose()
