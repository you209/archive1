"""initial schema

Revision ID: 0001_initial
Revises: 
Create Date: 2026-04-24
"""

from alembic import op
import sqlalchemy as sa


revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "photos",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("filename", sa.String(), nullable=False),
        sa.Column("original_name", sa.String(), nullable=False),
        sa.Column("path", sa.String(), nullable=False),
        sa.Column("suggested_year", sa.String(), nullable=True),
        sa.Column("confirmed_year", sa.String(), nullable=True),
        sa.Column("suggested_event", sa.String(), nullable=True),
        sa.Column("confirmed_event", sa.String(), nullable=True),
        sa.Column("suggested_place", sa.String(), nullable=True),
        sa.Column("confirmed_place", sa.String(), nullable=True),
        sa.Column("notes", sa.Text(), nullable=True),
        sa.Column("needs_review", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_photos_id", "photos", ["id"])
    op.create_index("ix_photos_filename", "photos", ["filename"], unique=True)

    op.create_table("people", sa.Column("id", sa.Integer(), primary_key=True), sa.Column("name", sa.String(), nullable=False), sa.Column("face_group", sa.String(), nullable=True))
    op.create_index("ix_people_id", "people", ["id"])

    op.create_table(
        "photo_people",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("photo_id", sa.Integer(), sa.ForeignKey("photos.id"), nullable=False),
        sa.Column("person_id", sa.Integer(), sa.ForeignKey("people.id"), nullable=False),
        sa.Column("confidence", sa.Float(), nullable=False),
    )

    op.create_table(
        "suggestions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("photo_id", sa.Integer(), sa.ForeignKey("photos.id"), nullable=False),
        sa.Column("kind", sa.String(), nullable=False),
        sa.Column("value", sa.String(), nullable=False),
        sa.Column("confidence", sa.Float(), nullable=False),
    )
    op.create_index("ix_suggestions_photo_id", "suggestions", ["photo_id"])

    op.create_table(
        "feedback",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("question_type", sa.String(), nullable=False),
        sa.Column("prompt", sa.String(), nullable=False),
        sa.Column("answer", sa.String(), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )

    op.create_table(
        "merge_audit",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("canonical_name", sa.String(), nullable=False),
        sa.Column("merged_name", sa.String(), nullable=False),
        sa.Column("reassigned_photo_ids", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("merge_audit")
    op.drop_table("feedback")
    op.drop_index("ix_suggestions_photo_id", table_name="suggestions")
    op.drop_table("suggestions")
    op.drop_table("photo_people")
    op.drop_index("ix_people_id", table_name="people")
    op.drop_table("people")
    op.drop_index("ix_photos_filename", table_name="photos")
    op.drop_index("ix_photos_id", table_name="photos")
    op.drop_table("photos")
