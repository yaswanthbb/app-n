import os
from pathlib import Path
import uuid

from alembic import command
from alembic.config import Config
from alembic.migration import MigrationContext
import pytest
from sqlalchemy import inspect, text
from sqlalchemy.engine import make_url

from database import DealRow, connect_database
from sqlalchemy.orm import Session
from migrate import migration_connection, require_current_schema, upgrade_database


@pytest.fixture(params=["sqlite", "postgresql"])
def database(request, tmp_path):
    if request.param == "sqlite":
        engine, _ = connect_database(f"sqlite:///{tmp_path / 'legacy.db'}")
        yield engine
        engine.dispose()
    else:
        url = os.getenv("TEST_POSTGRES_URL")
        if not url:
            pytest.skip("Set TEST_POSTGRES_URL to run isolated Postgres schema migration tests")
        admin, _ = connect_database(url)
        schema = "newsapp_test_" + uuid.uuid4().hex
        with admin.begin() as connection:
            connection.exec_driver_sql(f'CREATE SCHEMA "{schema}"')
        parsed = make_url(url)
        engine, _ = connect_database(parsed.update_query_dict({"options": f"-csearch_path={schema}"}).render_as_string(hide_password=False))
        try:
            yield engine
        finally:
            engine.dispose()
            with admin.begin() as connection:
                connection.exec_driver_sql(f'DROP SCHEMA "{schema}" CASCADE')
            admin.dispose()


def legacy_schema(engine):
    root = Path(__file__).resolve().parents[1]
    config = Config(str(root / "alembic.ini"))
    config.set_main_option("script_location", str(root / "migrations"))
    with migration_connection(engine) as connection:
        config.attributes["connection"] = connection
        command.upgrade(config, "0001_initial")
        # The live original create_all schema had no Alembic version table.
        connection.execute(text("DROP TABLE alembic_version"))
        connection.execute(text("INSERT INTO deals (id,title,description,url,source,tag,expires,created_at) VALUES (37,'Legacy free tool','Free access','https://example.com/old','Example','AI',NULL,1700000000)"))
        connection.execute(text("INSERT INTO picks (id,title,body,url,date,created_at) VALUES (19,'Legacy pick','A note','https://example.com/pick','2026-10-08',1700000001)"))
        connection.execute(text("INSERT INTO push_outbox (id,topic,title,body,url,sent_at,attempts,next_attempt) VALUES (8,'deals','Legacy free tool','Ready','https://example.com/old',NULL,2,1700000002)"))


def test_live_legacy_upgrade_preserves_data_and_is_repeatable(database):
    legacy_schema(database)
    with pytest.raises(RuntimeError, match="migration required"):
        require_current_schema(database)
    assert "offer_details" not in {column["name"] for column in inspect(database).get_columns("deals")}
    upgrade_database(database)
    require_current_schema(database)
    upgrade_database(database)
    columns = {column["name"]: column for column in inspect(database).get_columns("deals")}
    assert columns["offer_details"]["nullable"] is True
    assert columns["claim_steps"]["nullable"] is True
    with database.connect() as connection:
        row = connection.execute(text("SELECT * FROM deals WHERE id=37")).mappings().one()
        assert row["title"] == "Legacy free tool"
        assert row["created_at"] == 1700000000
        assert row["offer_details"] is None and row["claim_steps"] is None
        assert connection.execute(text("SELECT id FROM picks")).scalar_one() == 19
        assert connection.execute(text("SELECT attempts FROM push_outbox WHERE id=8")).scalar_one() == 2
        assert MigrationContext.configure(connection).get_current_revision() == "0002_deal_details"


def test_new_database_migrates_without_create_all(database):
    upgrade_database(database)
    require_current_schema(database)
    assert {"deals", "picks", "push_outbox", "alembic_version"} <= set(inspect(database).get_table_names())


def test_incompatible_schema_fails_without_partial_ddl(database):
    with database.begin() as connection:
        connection.execute(text("CREATE TABLE deals (id INTEGER PRIMARY KEY, title TEXT)"))
        connection.execute(text("INSERT INTO deals VALUES (42,'Keep me')"))
    with pytest.raises(RuntimeError, match="incompatible"):
        upgrade_database(database)
    with database.connect() as connection:
        assert connection.execute(text("SELECT title FROM deals WHERE id=42")).scalar_one() == "Keep me"
    assert set(inspect(database).get_table_names()) == {"deals"}


def test_preexisting_wrong_detail_type_is_not_silently_stamped(database):
    legacy_schema(database)
    with database.begin() as connection:
        connection.execute(text("ALTER TABLE deals ADD COLUMN claim_steps TEXT"))
    with pytest.raises(RuntimeError, match="incompatible type"):
        upgrade_database(database)
    assert "offer_details" not in {column["name"] for column in inspect(database).get_columns("deals")}
    with database.connect() as connection:
        assert connection.execute(text("SELECT title FROM deals WHERE id=37")).scalar_one() == "Legacy free tool"


def test_json_steps_round_trip_on_real_database(database):
    upgrade_database(database)
    with Session(database) as session:
        row = DealRow(title="Free tool", description="Free access", url="https://example.com/free", source="Example", tag="AI", created_at=1700000000, offer_details="Free usage", claim_steps=["Open the site", "Claim"])
        session.add(row)
        session.commit()
        session.refresh(row)
        assert row.claim_steps == ["Open the site", "Claim"]
        assert row.offer_details == "Free usage"
