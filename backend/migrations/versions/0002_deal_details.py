"""Add nullable deal details without changing existing rows or identifiers."""
from alembic import op
import sqlalchemy as sa

revision = "0002_deal_details"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade():
    columns = {column["name"]: column for column in sa.inspect(op.get_bind()).get_columns("deals")}
    for name, expected_type in [("offer_details", sa.Text), ("claim_steps", sa.JSON)]:
        if name in columns and (not columns[name]["nullable"] or not isinstance(columns[name]["type"], expected_type)):
            raise RuntimeError(f"Existing deals.{name} has an incompatible type or nullability; inspect it before migrating")
    present = set(columns)
    if "offer_details" not in present:
        op.add_column("deals", sa.Column("offer_details", sa.Text(), nullable=True))
    if "claim_steps" not in present:
        op.add_column("deals", sa.Column("claim_steps", sa.JSON(none_as_null=True), nullable=True))


def downgrade():
    with op.batch_alter_table("deals") as batch:
        batch.drop_column("claim_steps")
        batch.drop_column("offer_details")
