from pydantic import BaseModel, Field


class PersonOut(BaseModel):
    id: int
    name: str
    confidence: float


class SuggestionOut(BaseModel):
    kind: str
    value: str
    confidence: float
    reason: str | None = None


class PhotoOut(BaseModel):
    id: int
    filename: str
    media_url: str
    original_name: str
    suggested_year: str | None
    confirmed_year: str | None
    suggested_event: str | None
    confirmed_event: str | None
    suggested_place: str | None
    confirmed_place: str | None
    notes: str | None
    needs_review: bool
    people: list[PersonOut]
    suggestions: list[SuggestionOut]


class ReviewAnswer(BaseModel):
    photo_id: int
    who: list[str] = Field(default_factory=list)
    year: str | None = None
    event: str | None = None
    place: str | None = None
    notes: str | None = None


class BatchReviewAnswer(BaseModel):
    photo_ids: list[int] = Field(default_factory=list)
    year: str | None = None
    event: str | None = None
    place: str | None = None
    who: list[str] = Field(default_factory=list)
    notes: str | None = None


class ComparePeopleAnswer(BaseModel):
    person_a: str
    person_b: str
    same_person: bool


class ExportRequest(BaseModel):
    commit: bool = False


class ExportItem(BaseModel):
    source: str
    destination: str


class ExportResponse(BaseModel):
    mode: str
    root: str
    items: list[ExportItem]


class MergePreview(BaseModel):
    canonical: str
    merged: str
    impacted_photo_ids: list[int]
    duplicate_photo_ids: list[int]


class JobAccepted(BaseModel):
    job_id: str
    status: str


class PersonProfileIn(BaseModel):
    name: str
    birth_year: str | None = None
    death_year: str | None = None
    home_place: str | None = None
    notes: str | None = None


class FamilyRelationIn(BaseModel):
    from_person_id: int
    to_person_id: int
    relation_type: str


class ImportFolderIn(BaseModel):
    source_folder: str


class ConfidenceSettings(BaseModel):
    min_confidence_review: float = 0.0
    min_confidence_import_queue: float = 0.0


class PersonDetailOut(BaseModel):
    profile: dict
    relatives: list[dict]
    photos: list[dict]
    places: list[dict]
    timeline: list[dict]
