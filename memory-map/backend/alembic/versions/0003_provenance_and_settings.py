"""provenance settings and constraints

Revision ID: 0003_provenance_and_settings
Revises: 0002_genealogy_extensions
Create Date: 2026-04-24
"""

from alembic import op
import sqlalchemy as sa


revision = "0003_provenance_and_settings"
down_revision = "0002_genealogy_extensions"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("suggestions", sa.Column("reason", sa.String(), nullable=True))

    op.create_table(
        "app_settings",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("key", sa.String(), nullable=False),
        sa.Column("value", sa.String(), nullable=False),
    )
    op.create_index("ix_app_settings_key", "app_settings", ["key"], unique=True)

    op.create_unique_constraint("uq_photo_people_pair", "photo_people", ["photo_id", "person_id"])
    op.create_unique_constraint("uq_family_relations_link", "family_relations", ["from_person_id", "to_person_id", "relation_type"])


def downgrade() -> None:
    op.drop_constraint("uq_family_relations_link", "family_relations", type_="unique")
    op.drop_constraint("uq_photo_people_pair", "photo_people", type_="unique")

    op.drop_index("ix_app_settings_key", table_name="app_settings")
    op.drop_table("app_settings")

    op.drop_column("suggestions", "reason")
