from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import FamilyRelation, Person, Photo, PhotoPerson
from ..schemas import FamilyRelationIn, PersonDetailOut, PersonProfileIn

router = APIRouter(prefix="/people", tags=["people"])


@router.get("")
def list_people(db: Session = Depends(get_db)):
    people = db.scalars(select(Person).order_by(Person.name.asc())).all()
    return [
        {
            "id": p.id,
            "name": p.name,
            "birth_year": p.birth_year,
            "death_year": p.death_year,
            "home_place": p.home_place,
            "notes": p.notes,
        }
        for p in people
    ]


@router.get("/{person_id}", response_model=PersonDetailOut)
def person_detail(person_id: int, db: Session = Depends(get_db)):
    person = db.get(Person, person_id)
    if not person:
        raise HTTPException(status_code=404, detail="Person not found")

    photo_rows = db.execute(
        select(Photo)
        .join(PhotoPerson, PhotoPerson.photo_id == Photo.id)
        .where(PhotoPerson.person_id == person_id)
        .order_by(Photo.created_at.desc())
    ).scalars().all()

    relative_edges = db.scalars(
        select(FamilyRelation).where((FamilyRelation.from_person_id == person_id) | (FamilyRelation.to_person_id == person_id))
    ).all()

    relatives = []
    for edge in relative_edges:
        other_id = edge.to_person_id if edge.from_person_id == person_id else edge.from_person_id
        other = db.get(Person, other_id)
        if other:
            relatives.append({"id": other.id, "name": other.name, "relation": edge.relation_type})

    places = db.execute(
        select(func.coalesce(Photo.confirmed_place, Photo.suggested_place, "Unknown Place"), func.count(Photo.id))
        .join(PhotoPerson, PhotoPerson.photo_id == Photo.id)
        .where(PhotoPerson.person_id == person_id)
        .group_by(func.coalesce(Photo.confirmed_place, Photo.suggested_place, "Unknown Place"))
    ).all()

    timeline = db.execute(
        select(func.coalesce(Photo.confirmed_year, Photo.suggested_year, "Unknown Year"), func.count(Photo.id))
        .join(PhotoPerson, PhotoPerson.photo_id == Photo.id)
        .where(PhotoPerson.person_id == person_id)
        .group_by(func.coalesce(Photo.confirmed_year, Photo.suggested_year, "Unknown Year"))
    ).all()

    return {
        "profile": {
            "id": person.id,
            "name": person.name,
            "birth_year": person.birth_year,
            "death_year": person.death_year,
            "home_place": person.home_place,
            "notes": person.notes,
        },
        "relatives": relatives,
        "photos": [
            {
                "id": p.id,
                "original_name": p.original_name,
                "media_url": f"/media/{p.filename}",
                "year": p.confirmed_year or p.suggested_year or "Unknown Year",
                "place": p.confirmed_place or p.suggested_place or "Unknown Place",
            }
            for p in photo_rows
        ],
        "places": [{"place": place, "count": count} for place, count in places],
        "timeline": [{"year": year, "count": count} for year, count in timeline],
    }


@router.post("")
def upsert_person(payload: PersonProfileIn, db: Session = Depends(get_db)):
    person = db.scalar(select(Person).where(Person.name == payload.name))
    if not person:
        person = Person(name=payload.name)
        db.add(person)
        db.flush()

    person.birth_year = payload.birth_year
    person.death_year = payload.death_year
    person.home_place = payload.home_place
    person.notes = payload.notes
    db.commit()
    return {"id": person.id, "name": person.name}


@router.post("/relations")
def add_relation(payload: FamilyRelationIn, db: Session = Depends(get_db)):
    left = db.get(Person, payload.from_person_id)
    right = db.get(Person, payload.to_person_id)
    if not left or not right:
        raise HTTPException(status_code=404, detail="Person not found")

    existing = db.scalar(
        select(FamilyRelation).where(
            FamilyRelation.from_person_id == payload.from_person_id,
            FamilyRelation.to_person_id == payload.to_person_id,
            FamilyRelation.relation_type == payload.relation_type,
        )
    )
    if existing:
        return {"status": "exists"}

    relation = FamilyRelation(
        from_person_id=payload.from_person_id,
        to_person_id=payload.to_person_id,
        relation_type=payload.relation_type,
    )
    db.add(relation)
    db.commit()
    return {"status": "saved"}


@router.get("/tree")
def family_tree(db: Session = Depends(get_db)):
    nodes = db.scalars(select(Person)).all()
    edges = db.scalars(select(FamilyRelation)).all()
    return {
        "nodes": [
            {
                "id": p.id,
                "label": p.name,
                "birth_year": p.birth_year,
                "death_year": p.death_year,
                "home_place": p.home_place,
            }
            for p in nodes
        ],
        "edges": [
            {
                "from": e.from_person_id,
                "to": e.to_person_id,
                "relation": e.relation_type,
            }
            for e in edges
        ],
    }
