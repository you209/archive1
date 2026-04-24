from __future__ import annotations

import shutil
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..database import SessionLocal, get_db
from ..models import AppSetting, Feedback, MergeAudit, Person, Photo, PhotoPerson, Suggestion
from ..schemas import (
    BatchReviewAnswer,
    ComparePeopleAnswer,
    ExportRequest,
    ExportResponse,
    ImportFolderIn,
    JobAccepted,
    MergePreview,
    PhotoOut,
    ReviewAnswer,
    SuggestionOut,
)
from ..services.exporter import apply_export_plan, build_export_plan
from ..services.importer import import_folder
from ..services.jobs import job_manager
from ..services.suggestions import average_hash, confidence_from_similarity, hamming_distance, infer_from_filename
from ..settings import settings

router = APIRouter(prefix="/photos", tags=["photos"])

settings.photos_dir.mkdir(parents=True, exist_ok=True)
settings.exports_dir.mkdir(parents=True, exist_ok=True)


DEFAULT_REVIEW_MIN_CONFIDENCE = 0.0
DEFAULT_IMPORT_QUEUE_MIN_CONFIDENCE = 0.5


def get_setting_float(db: Session, key: str, default: float) -> float:
    row = db.scalar(select(AppSetting).where(AppSetting.key == key))
    if not row:
        return default
    try:
        return float(row.value)
    except ValueError:
        return default


def enrich_with_profile_suggestions(photo: Photo, db: Session):
    people = db.scalars(select(Person)).all()
    lowered = photo.original_name.lower()
    for person in people:
        if person.name.lower().replace(" ", "-") in lowered or person.name.lower() in lowered:
            existing = db.scalar(
                select(PhotoPerson).where(PhotoPerson.photo_id == photo.id, PhotoPerson.person_id == person.id)
            )
            if not existing:
                db.add(PhotoPerson(photo_id=photo.id, person_id=person.id, confidence=0.62))
                db.add(
                    Suggestion(
                        photo_id=photo.id,
                        kind="person",
                        value=person.name,
                        confidence=0.62,
                        reason="Matched person name in original filename.",
                    )
                )


def serialize_photo(photo: Photo, db: Session) -> PhotoOut:
    suggestions = db.scalars(select(Suggestion).where(Suggestion.photo_id == photo.id)).all()
    return PhotoOut(
        id=photo.id,
        filename=photo.filename,
        media_url=f"/media/{photo.filename}",
        original_name=photo.original_name,
        suggested_year=photo.suggested_year,
        confirmed_year=photo.confirmed_year,
        suggested_event=photo.suggested_event,
        confirmed_event=photo.confirmed_event,
        suggested_place=photo.suggested_place,
        confirmed_place=photo.confirmed_place,
        notes=photo.notes,
        needs_review=photo.needs_review,
        people=[
            {"id": rel.person.id, "name": rel.person.name, "confidence": rel.confidence}
            for rel in photo.people
        ],
        suggestions=[SuggestionOut(kind=s.kind, value=s.value, confidence=s.confidence, reason=s.reason) for s in suggestions],
    )


def add_uploaded_file(upload_name: str, source_stream, db: Session):
    extension = Path(upload_name).suffix.lower()
    if extension not in {".jpg", ".jpeg", ".png", ".webp", ".tif", ".tiff"}:
        return None

    generated_name = f"{uuid.uuid4().hex}{extension}"
    save_path = settings.photos_dir / generated_name
    with save_path.open("wb") as buffer:
        shutil.copyfileobj(source_stream, buffer)

    inferred = infer_from_filename(upload_name)
    photo = Photo(
        filename=generated_name,
        original_name=upload_name,
        path=str(save_path),
        suggested_year=inferred["year"][0],
        suggested_event=inferred["event"][0],
        needs_review=True,
    )
    db.add(photo)
    db.flush()
    db.add(
        Suggestion(
            photo_id=photo.id,
            kind="year",
            value=inferred["year"][0],
            confidence=inferred["year"][1],
            reason="Detected a 4-digit year token in filename." if inferred["year"][0] != "Unknown Year" else "No clear year found in filename.",
        )
    )
    db.add(
        Suggestion(
            photo_id=photo.id,
            kind="event",
            value=inferred["event"][0],
            confidence=inferred["event"][1],
            reason="Matched known event keyword in filename." if inferred["event"][0] != "Unknown Event" else "No known event keyword matched in filename.",
        )
    )
    enrich_with_profile_suggestions(photo, db)
    return {"id": photo.id, "name": upload_name}


def should_include_for_threshold(photo: Photo, db: Session, threshold: float) -> bool:
    if threshold <= 0:
        return True
    suggestions = db.scalars(select(Suggestion).where(Suggestion.photo_id == photo.id)).all()
    if not suggestions:
        return True
    return max(s.confidence for s in suggestions) >= threshold


@router.post("/upload")
async def upload_photos(files: list[UploadFile] = File(...), db: Session = Depends(get_db)):
    uploaded = []
    for file in files:
        record = add_uploaded_file(file.filename, file.file, db)
        if record:
            uploaded.append(record)

    db.commit()
    return {"uploaded": uploaded, "count": len(uploaded)}


@router.post("/import-folder")
def import_from_folder(payload: ImportFolderIn, db: Session = Depends(get_db)):
    imported = import_folder(Path(payload.source_folder), settings.photos_dir)
    uploaded = []
    for item in imported:
        path = Path(item["path"])
        inferred = infer_from_filename(item["original_name"])
        photo = Photo(
            filename=item["stored_name"],
            original_name=item["original_name"],
            path=str(path),
            suggested_year=inferred["year"][0],
            suggested_event=inferred["event"][0],
            needs_review=True,
        )
        db.add(photo)
        db.flush()
        db.add(Suggestion(photo_id=photo.id, kind="year", value=inferred["year"][0], confidence=inferred["year"][1], reason="Detected from filename token parsing."))
        db.add(Suggestion(photo_id=photo.id, kind="event", value=inferred["event"][0], confidence=inferred["event"][1], reason="Detected from filename event keyword matching."))
        enrich_with_profile_suggestions(photo, db)
        uploaded.append({"id": photo.id, "name": item["original_name"]})

    db.commit()
    return {"imported": len(uploaded), "photos": uploaded}


@router.get("/review", response_model=list[PhotoOut])
def review_queue(min_confidence: float | None = Query(default=None), db: Session = Depends(get_db)):
    threshold = min_confidence
    if threshold is None:
        threshold = get_setting_float(db, "min_confidence_review", DEFAULT_REVIEW_MIN_CONFIDENCE)
    photos = db.scalars(select(Photo).where(Photo.needs_review.is_(True)).order_by(Photo.created_at.asc())).all()
    return [serialize_photo(photo, db) for photo in photos if should_include_for_threshold(photo, db, threshold)]


@router.get("/import-queue", response_model=list[PhotoOut])
def import_queue(min_confidence: float | None = Query(default=None), db: Session = Depends(get_db)):
    threshold = min_confidence
    if threshold is None:
        threshold = get_setting_float(db, "min_confidence_import_queue", DEFAULT_IMPORT_QUEUE_MIN_CONFIDENCE)
    photos = db.scalars(select(Photo).where(Photo.needs_review.is_(True)).order_by(Photo.created_at.asc())).all()
    return [serialize_photo(photo, db) for photo in photos if should_include_for_threshold(photo, db, threshold)]


@router.get("/all", response_model=list[PhotoOut])
def all_photos(
    person_id: int | None = Query(default=None),
    year: str | None = Query(default=None),
    db: Session = Depends(get_db),
):
    photos = db.scalars(select(Photo).order_by(Photo.created_at.desc())).all()
    filtered = []
    for photo in photos:
        if year and (photo.confirmed_year or photo.suggested_year) != year:
            continue
        if person_id and not any(link.person_id == person_id for link in photo.people):
            continue
        filtered.append(photo)
    return [serialize_photo(photo, db) for photo in filtered]


@router.post("/answer")
def answer_review(payload: ReviewAnswer, db: Session = Depends(get_db)):
    photo = db.get(Photo, payload.photo_id)
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    apply_review_answer(photo, payload, db)
    db.commit()
    return {"status": "saved", "photo_id": photo.id}


def apply_review_answer(photo: Photo, payload: ReviewAnswer, db: Session) -> None:
    if payload.year:
        photo.confirmed_year = payload.year
    if payload.event:
        photo.confirmed_event = payload.event
    if payload.place:
        photo.confirmed_place = payload.place
    if payload.notes is not None:
        photo.notes = payload.notes

    for person_name in payload.who:
        clean_name = person_name.strip()
        if not clean_name:
            continue
        person = db.scalar(select(Person).where(Person.name == clean_name))
        if not person:
            person = Person(name=clean_name)
            db.add(person)
            db.flush()

        existing = db.scalar(select(PhotoPerson).where(PhotoPerson.photo_id == photo.id, PhotoPerson.person_id == person.id))
        if not existing:
            db.add(PhotoPerson(photo_id=photo.id, person_id=person.id, confidence=0.95))

    db.add(Feedback(question_type="review", prompt="who/year/event/place", answer=str(payload.model_dump())))
    photo.needs_review = False


@router.post("/answer/batch")
def batch_answer_review(payload: BatchReviewAnswer, db: Session = Depends(get_db)):
    if not payload.photo_ids:
        raise HTTPException(status_code=400, detail="photo_ids is required")

    updated = 0
    for photo_id in payload.photo_ids:
        photo = db.get(Photo, photo_id)
        if not photo:
            continue
        apply_review_answer(
            photo,
            ReviewAnswer(
                photo_id=photo_id,
                who=payload.who,
                year=payload.year,
                event=payload.event,
                place=payload.place,
                notes=payload.notes,
            ),
            db,
        )
        updated += 1

    db.commit()
    return {"status": "saved", "updated": updated}


@router.post("/compare-people/preview", response_model=MergePreview)
def compare_people_preview(payload: ComparePeopleAnswer, db: Session = Depends(get_db)):
    person_a = db.scalar(select(Person).where(Person.name == payload.person_a))
    person_b = db.scalar(select(Person).where(Person.name == payload.person_b))
    if not person_a or not person_b:
        raise HTTPException(status_code=404, detail="Person not found")

    impacted = [r.photo_id for r in db.scalars(select(PhotoPerson).where(PhotoPerson.person_id == person_b.id)).all()]
    duplicates = [
        pid
        for pid in impacted
        if db.scalar(select(PhotoPerson).where(PhotoPerson.photo_id == pid, PhotoPerson.person_id == person_a.id))
    ]
    return MergePreview(
        canonical=person_a.name,
        merged=person_b.name,
        impacted_photo_ids=sorted(set(impacted)),
        duplicate_photo_ids=sorted(set(duplicates)),
    )


@router.post("/compare-people")
def compare_people(payload: ComparePeopleAnswer, db: Session = Depends(get_db)):
    person_a = db.scalar(select(Person).where(Person.name == payload.person_a))
    person_b = db.scalar(select(Person).where(Person.name == payload.person_b))
    if not person_a or not person_b:
        raise HTTPException(status_code=404, detail="Person not found")

    reassigned_photo_ids: list[int] = []
    merged = False
    if payload.same_person and person_a.id != person_b.id:
        for relation in db.scalars(select(PhotoPerson).where(PhotoPerson.person_id == person_b.id)).all():
            duplicate = db.scalar(select(PhotoPerson).where(PhotoPerson.photo_id == relation.photo_id, PhotoPerson.person_id == person_a.id))
            if duplicate:
                db.delete(relation)
            else:
                relation.person_id = person_a.id
                reassigned_photo_ids.append(relation.photo_id)
        db.add(MergeAudit(canonical_name=person_a.name, merged_name=person_b.name, reassigned_photo_ids=",".join(map(str, reassigned_photo_ids))))
        db.execute(delete(Person).where(Person.id == person_b.id))
        merged = True

    db.add(Feedback(question_type="identity", prompt=f"{payload.person_a}=={payload.person_b}", answer=str(payload.same_person)))
    db.commit()
    return {"status": "updated", "merged": merged, "canonical": person_a.name}


@router.post("/compare-people/undo")
def undo_last_merge(db: Session = Depends(get_db)):
    last = db.scalar(select(MergeAudit).order_by(MergeAudit.id.desc()))
    if not last:
        raise HTTPException(status_code=404, detail="No merge to undo")

    canonical = db.scalar(select(Person).where(Person.name == last.canonical_name))
    restored = db.scalar(select(Person).where(Person.name == last.merged_name))
    if not canonical:
        raise HTTPException(status_code=404, detail="Canonical missing")
    if not restored:
        restored = Person(name=last.merged_name)
        db.add(restored)
        db.flush()

    for pid in [int(v) for v in last.reassigned_photo_ids.split(",") if v.strip()]:
        relation = db.scalar(select(PhotoPerson).where(PhotoPerson.photo_id == pid, PhotoPerson.person_id == canonical.id))
        if relation:
            relation.person_id = restored.id

    db.delete(last)
    db.commit()
    return {"status": "undone", "restored": restored.name}


def compute_face_groups(db: Session):
    photos = db.scalars(select(Photo)).all()
    hashes = []
    for photo in photos:
        path = Path(photo.path)
        if path.exists():
            hashes.append((photo.id, average_hash(path)))

    groups: list[list[int]] = []
    for pid, image_hash in hashes:
        placed = False
        for group in groups:
            ref_hash = next(h for p, h in hashes if p == group[0])
            if hamming_distance(ref_hash, image_hash) <= 10:
                group.append(pid)
                placed = True
                break
        if not placed:
            groups.append([pid])

    output = []
    for group in groups:
        output.append({"photo_ids": group, "confidence": confidence_from_similarity([5] if len(group) > 1 else []), "requires_confirmation": True})
    return output


@router.get("/face-groups")
def face_groups(db: Session = Depends(get_db)):
    return compute_face_groups(db)


@router.post("/jobs/face-groups", response_model=JobAccepted)
def queue_face_group_job():
    job = job_manager.enqueue(lambda: compute_face_groups(SessionLocal()))
    return JobAccepted(job_id=job.id, status=job.status)


@router.post("/export", response_model=ExportResponse)
def export(payload: ExportRequest, db: Session = Depends(get_db)):
    photos = db.scalars(select(Photo)).all()
    source_items = [
        {
            "original_name": photo.original_name,
            "path": photo.path,
            "confirmed_year": photo.confirmed_year,
            "suggested_year": photo.suggested_year,
            "confirmed_event": photo.confirmed_event,
            "suggested_event": photo.suggested_event,
        }
        for photo in photos
        if Path(photo.path).exists()
    ]
    plan = build_export_plan(source_items, settings.exports_dir)
    if payload.commit:
        apply_export_plan(plan)
    return {"mode": "commit" if payload.commit else "dry-run", "root": str(settings.exports_dir), "items": plan}


@router.post("/jobs/export", response_model=JobAccepted)
def queue_export_job(payload: ExportRequest):
    def runner():
        with SessionLocal() as db:
            photos = db.scalars(select(Photo)).all()
            source_items = [
                {
                    "original_name": photo.original_name,
                    "path": photo.path,
                    "confirmed_year": photo.confirmed_year,
                    "suggested_year": photo.suggested_year,
                    "confirmed_event": photo.confirmed_event,
                    "suggested_event": photo.suggested_event,
                }
                for photo in photos
                if Path(photo.path).exists()
            ]
            plan = build_export_plan(source_items, settings.exports_dir)
            if payload.commit:
                apply_export_plan(plan)
            return {"mode": "commit" if payload.commit else "dry-run", "items": plan}

    job = job_manager.enqueue(runner)
    return JobAccepted(job_id=job.id, status=job.status)


@router.get("/jobs/{job_id}")
def get_job(job_id: str):
    job = job_manager.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return {"job_id": job.id, "status": job.status, "result": job.result, "error": job.error}
