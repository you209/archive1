from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


class Photo(Base):
    __tablename__ = "photos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    filename: Mapped[str] = mapped_column(String, unique=True, index=True)
    original_name: Mapped[str] = mapped_column(String)
    path: Mapped[str] = mapped_column(String)
    suggested_year: Mapped[str | None] = mapped_column(String, nullable=True)
    confirmed_year: Mapped[str | None] = mapped_column(String, nullable=True)
    suggested_event: Mapped[str | None] = mapped_column(String, nullable=True)
    confirmed_event: Mapped[str | None] = mapped_column(String, nullable=True)
    suggested_place: Mapped[str | None] = mapped_column(String, nullable=True)
    confirmed_place: Mapped[str | None] = mapped_column(String, nullable=True)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    needs_review: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    people = relationship("PhotoPerson", back_populates="photo", cascade="all, delete-orphan")


class Person(Base):
    __tablename__ = "people"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String, unique=True)
    face_group: Mapped[str | None] = mapped_column(String, nullable=True)
    birth_year: Mapped[str | None] = mapped_column(String, nullable=True)
    death_year: Mapped[str | None] = mapped_column(String, nullable=True)
    home_place: Mapped[str | None] = mapped_column(String, nullable=True)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)


class FamilyRelation(Base):
    __tablename__ = "family_relations"
    __table_args__ = (UniqueConstraint("from_person_id", "to_person_id", "relation_type", name="uq_family_relations_link"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    from_person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), index=True)
    to_person_id: Mapped[int] = mapped_column(ForeignKey("people.id"), index=True)
    relation_type: Mapped[str] = mapped_column(String)  # parent, spouse, sibling, child


class PhotoPerson(Base):
    __tablename__ = "photo_people"
    __table_args__ = (UniqueConstraint("photo_id", "person_id", name="uq_photo_people_pair"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    photo_id: Mapped[int] = mapped_column(ForeignKey("photos.id"))
    person_id: Mapped[int] = mapped_column(ForeignKey("people.id"))
    confidence: Mapped[float] = mapped_column(Float, default=0.0)

    photo = relationship("Photo", back_populates="people")
    person = relationship("Person")


class Suggestion(Base):
    __tablename__ = "suggestions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    photo_id: Mapped[int] = mapped_column(ForeignKey("photos.id"), index=True)
    kind: Mapped[str] = mapped_column(String)
    value: Mapped[str] = mapped_column(String)
    confidence: Mapped[float] = mapped_column(Float)
    reason: Mapped[str | None] = mapped_column(String, nullable=True)


class Feedback(Base):
    __tablename__ = "feedback"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    question_type: Mapped[str] = mapped_column(String)
    prompt: Mapped[str] = mapped_column(String)
    answer: Mapped[str] = mapped_column(String)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class MergeAudit(Base):
    __tablename__ = "merge_audit"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    canonical_name: Mapped[str] = mapped_column(String)
    merged_name: Mapped[str] = mapped_column(String)
    reassigned_photo_ids: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class AppSetting(Base):
    __tablename__ = "app_settings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    key: Mapped[str] = mapped_column(String, unique=True, index=True)
    value: Mapped[str] = mapped_column(String)
