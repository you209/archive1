from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import AppSetting, Person, Photo
from ..schemas import ConfidenceSettings

router = APIRouter(prefix="/meta", tags=["meta"])


def _get_float_setting(db: Session, key: str, default: float) -> float:
    row = db.scalar(select(AppSetting).where(AppSetting.key == key))
    if not row:
        return default
    try:
        return float(row.value)
    except ValueError:
        return default


def _set_float_setting(db: Session, key: str, value: float) -> None:
    row = db.scalar(select(AppSetting).where(AppSetting.key == key))
    if not row:
        row = AppSetting(key=key, value=str(value))
        db.add(row)
    else:
        row.value = str(value)


@router.get("/settings/confidence", response_model=ConfidenceSettings)
def confidence_settings(db: Session = Depends(get_db)):
    return ConfidenceSettings(
        min_confidence_review=_get_float_setting(db, "min_confidence_review", 0.0),
        min_confidence_import_queue=_get_float_setting(db, "min_confidence_import_queue", 0.5),
    )


@router.post("/settings/confidence", response_model=ConfidenceSettings)
def save_confidence_settings(payload: ConfidenceSettings, db: Session = Depends(get_db)):
    _set_float_setting(db, "min_confidence_review", payload.min_confidence_review)
    _set_float_setting(db, "min_confidence_import_queue", payload.min_confidence_import_queue)
    db.commit()
    return payload


@router.get("/timeline")
def timeline(db: Session = Depends(get_db)):
    rows = db.execute(
        select(
            func.coalesce(Photo.confirmed_year, Photo.suggested_year, "Unknown Year"),
            func.count(Photo.id),
        ).group_by(func.coalesce(Photo.confirmed_year, Photo.suggested_year, "Unknown Year"))
    ).all()
    return [{"year": year, "count": count} for year, count in rows]


@router.get("/people")
def people(db: Session = Depends(get_db)):
    return [{"id": p.id, "name": p.name, "face_group": p.face_group} for p in db.scalars(select(Person)).all()]


@router.get("/events")
def events(db: Session = Depends(get_db)):
    rows = db.execute(
        select(func.coalesce(Photo.confirmed_event, Photo.suggested_event, "Unknown Event"), func.count(Photo.id)).group_by(
            func.coalesce(Photo.confirmed_event, Photo.suggested_event, "Unknown Event")
        )
    ).all()
    return [{"event": event, "count": count} for event, count in rows]


@router.get("/map")
def map_points(db: Session = Depends(get_db)):
    rows = db.execute(
        select(func.coalesce(Photo.confirmed_place, Photo.suggested_place, "Unknown Place"), func.count(Photo.id)).group_by(
            func.coalesce(Photo.confirmed_place, Photo.suggested_place, "Unknown Place")
        )
    ).all()
    return [{"place": place, "count": count} for place, count in rows]
