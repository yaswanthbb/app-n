"""Adopt the original publisher schema without replacing existing tables."""
from alembic import op
import sqlalchemy as sa

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def ensure_table(name, columns):
    inspector = sa.inspect(op.get_bind())
    if not inspector.has_table(name):
        op.create_table(name, *columns)
    else:
        present = {column["name"] for column in inspector.get_columns(name)}
        missing = {column.name for column in columns} - present
        if missing:
            raise RuntimeError(f"Existing {name} table is incompatible; missing columns: {sorted(missing)}")


def upgrade():
    ensure_table("deals", [
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column("url", sa.Text(), nullable=False, unique=True),
        sa.Column("source", sa.String(120), nullable=False),
        sa.Column("tag", sa.String(30), nullable=False),
        sa.Column("expires", sa.String(10), nullable=True),
        sa.Column("created_at", sa.Float(), nullable=False),
    ])
    ensure_table("picks", [
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("body", sa.Text(), nullable=False),
        sa.Column("url", sa.Text(), nullable=False),
        sa.Column("date", sa.String(10), nullable=False, unique=True),
        sa.Column("created_at", sa.Float(), nullable=False),
    ])
    ensure_table("push_outbox", [
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("topic", sa.String(10), nullable=False),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("body", sa.String(200), nullable=False),
        sa.Column("url", sa.Text(), nullable=False),
        sa.Column("sent_at", sa.Float(), nullable=True),
        sa.Column("attempts", sa.Integer(), nullable=False),
        sa.Column("next_attempt", sa.Float(), nullable=False),
    ])


def downgrade():
    raise RuntimeError("Removing the baseline would destroy publisher data; restore a backup explicitly instead")
