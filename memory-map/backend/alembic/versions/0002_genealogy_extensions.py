"""genealogy extensions

Revision ID: 0002_genealogy_extensions
Revises: 0001_initial
Create Date: 2026-04-24
"""

from alembic import op
import sqlalchemy as sa


revision = "0002_genealogy_extensions"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("people", sa.Column("birth_year", sa.String(), nullable=True))
    op.add_column("people", sa.Column("death_year", sa.String(), nullable=True))
    op.add_column("people", sa.Column("home_place", sa.String(), nullable=True))
    op.add_column("people", sa.Column("notes", sa.Text(), nullable=True))

    op.create_table(
        "family_relations",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("from_person_id", sa.Integer(), sa.ForeignKey("people.id"), nullable=False),
        sa.Column("to_person_id", sa.Integer(), sa.ForeignKey("people.id"), nullable=False),
        sa.Column("relation_type", sa.String(), nullable=False),
    )
    op.create_index("ix_family_relations_from_person_id", "family_relations", ["from_person_id"])
    op.create_index("ix_family_relations_to_person_id", "family_relations", ["to_person_id"])


def downgrade() -> None:
    op.drop_index("ix_family_relations_to_person_id", table_name="family_relations")
    op.drop_index("ix_family_relations_from_person_id", table_name="family_relations")
    op.drop_table("family_relations")

    op.drop_column("people", "notes")
    op.drop_column("people", "home_place")
    op.drop_column("people", "death_year")
    op.drop_column("people", "birth_year")
