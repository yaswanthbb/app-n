"""Allow distinct picks on one day while rejecting exact double publication."""
from alembic import op
import sqlalchemy as sa

revision = "0003_multiple_picks"
down_revision = "0002_deal_details"
branch_labels = None
depends_on = None

COMPOSITE = "uq_picks_title_url_date"
NAMING = {"uq": "uq_%(table_name)s_%(column_0_name)s"}


def upgrade():
    inspector = sa.inspect(op.get_bind())
    constraints = inspector.get_unique_constraints("picks")
    # Inspect names instead of assuming Postgres' generated picks_date_key.
    date_constraints = [c for c in constraints if c["column_names"] == ["date"]]
    date_indexes = [i for i in inspector.get_indexes("picks")
                    if i.get("unique") and i["column_names"] == ["date"] and not i.get("duplicates_constraint")]
    has_composite = any(set(c["column_names"]) == {"title", "url", "date"} for c in constraints)
    # Postgres uses ALTER TABLE only. SQLite batch mode preserves every column
    # and row while rebuilding its table to remove an unnamed UNIQUE constraint.
    with op.batch_alter_table("picks", naming_convention=NAMING) as batch:
        for constraint in date_constraints:
            batch.drop_constraint(constraint["name"] or "uq_picks_date", type_="unique")
        for index in date_indexes:
            batch.drop_index(index["name"])
        if not has_composite:
            batch.create_unique_constraint(COMPOSITE, ["title", "url", "date"])


def downgrade():
    # Restoring one-pick-per-day must never silently discard newer notes.
    duplicate_day = op.get_bind().execute(sa.text("SELECT date FROM picks GROUP BY date HAVING COUNT(*) > 1 LIMIT 1")).first()
    if duplicate_day is not None:
        raise RuntimeError("Cannot restore date uniqueness while multiple picks share a date; no rows were removed")
    with op.batch_alter_table("picks", naming_convention=NAMING) as batch:
        batch.drop_constraint(COMPOSITE, type_="unique")
        batch.create_unique_constraint("uq_picks_date", ["date"])
