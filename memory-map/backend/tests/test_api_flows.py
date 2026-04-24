from io import BytesIO

from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.database import SessionLocal
from app.main import app
from app.models import AppSetting, FamilyRelation, Feedback, MergeAudit, Person, Photo, PhotoPerson, Suggestion

client = TestClient(app)

PNG_1X1 = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde"
    b"\x00\x00\x00\x0cIDAT\x08\xd7c\xf8\xff\xff?\x00\x05\xfe\x02\xfeA\xdd\x8d\xb1\x00\x00\x00\x00IEND\xaeB`\x82"
)


def reset_db():
    with SessionLocal() as db:
        db.execute(delete(PhotoPerson))
        db.execute(delete(Suggestion))
        db.execute(delete(FamilyRelation))
        db.execute(delete(Feedback))
        db.execute(delete(MergeAudit))
        db.execute(delete(AppSetting))
        db.execute(delete(Person))
        db.execute(delete(Photo))
        db.commit()


def test_upload_review_answer_and_export_flow():
    reset_db()
    files = [("files", ("1998-christmas.jpg", BytesIO(PNG_1X1), "image/png"))]
    upload = client.post("/photos/upload", files=files)
    assert upload.status_code == 200
    assert upload.json()["count"] == 1

    review = client.get("/photos/review")
    assert review.status_code == 200
    photo = review.json()[0]
    assert any(s.get("reason") for s in photo["suggestions"])

    answer = client.post(
        "/photos/answer",
        json={
            "photo_id": photo["id"],
            "who": ["Grandma"],
            "year": "1998",
            "event": "Christmas",
            "place": "Chicago",
        },
    )
    assert answer.status_code == 200

    export_preview = client.post("/photos/export", json={"commit": False})
    assert export_preview.status_code == 200
    assert export_preview.json()["mode"] == "dry-run"
    assert len(export_preview.json()["items"]) >= 1


def test_merge_preview_merge_and_undo_flow():
    reset_db()
    with SessionLocal() as db:
        alice = Person(name="Alice")
        alice_alt = Person(name="Alice Smith")
        db.add_all([alice, alice_alt])
        db.flush()
        photo = Photo(filename="x.jpg", original_name="x.jpg", path="/tmp/x.jpg", needs_review=False)
        db.add(photo)
        db.flush()
        db.add(PhotoPerson(photo_id=photo.id, person_id=alice_alt.id, confidence=0.91))
        db.commit()

    preview = client.post(
        "/photos/compare-people/preview",
        json={"person_a": "Alice", "person_b": "Alice Smith", "same_person": True},
    )
    assert preview.status_code == 200
    assert preview.json()["impacted_photo_ids"]

    merge = client.post(
        "/photos/compare-people",
        json={"person_a": "Alice", "person_b": "Alice Smith", "same_person": True},
    )
    assert merge.status_code == 200
    assert merge.json()["merged"] is True

    undo = client.post("/photos/compare-people/undo")
    assert undo.status_code == 200
    assert undo.json()["status"] == "undone"


def test_confidence_settings_batch_review_and_person_detail():
    reset_db()

    settings_save = client.post(
        "/meta/settings/confidence",
        json={"min_confidence_review": 0.7, "min_confidence_import_queue": 0.8},
    )
    assert settings_save.status_code == 200

    files = [
        ("files", ("1998-christmas.jpg", BytesIO(PNG_1X1), "image/png")),
        ("files", ("unknown-photo.jpg", BytesIO(PNG_1X1), "image/png")),
    ]
    upload = client.post("/photos/upload", files=files)
    assert upload.status_code == 200

    review = client.get("/photos/review")
    assert review.status_code == 200
    assert len(review.json()) >= 1

    photo_ids = [item["id"] for item in review.json()]
    batch = client.post(
        "/photos/answer/batch",
        json={"photo_ids": photo_ids, "year": "2004", "event": "School Photos", "who": ["Dad"]},
    )
    assert batch.status_code == 200

    people = client.get("/people")
    dad = next(item for item in people.json() if item["name"] == "Dad")
    detail = client.get(f"/people/{dad['id']}")
    assert detail.status_code == 200
    assert detail.json()["photos"]
    assert detail.json()["timeline"]
