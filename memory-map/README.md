# Memory Map

Memory Map is a privacy-first family history web app and installable PWA for organizing scanned photos by year and event.

## Features

- Premium, family-focused interface built with React + Tailwind.
- FastAPI backend with SQLite metadata storage.
- Bulk photo upload and optional import-folder watcher.
- Review queue with large image preview and guided questions:
  - Who is in this photo?
  - What year is this?
  - What event is this?
  - Where was this taken?
  - Are these people the same person?
- Similar-face grouping with confidence-based suggestions.
- Timeline, people profiles, family tree, event, and map screens.
- Export into folder structures by year/event with dry-run preview before commit.
- Local-first storage (photos remain local by default).

## Project structure

- `frontend/` React + TypeScript + Tailwind + PWA shell.
- `backend/` FastAPI + SQLAlchemy + SQLite + watcher/export services.

## Quick start

### Backend

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

By default the frontend expects API at `http://localhost:8000`.

### Migrations

```bash
cd backend
alembic upgrade head
```


## Privacy model

Memory Map runs locally. Metadata is persisted in `backend/storage/memory_map.db` and images are kept in `backend/storage/photos/`.

All AI suggestions include confidence values and require user confirmation before metadata is finalized or exported.


## Recent upgrades

- Review screen now shows the actual photo while answering prompts.
- People screen includes an explicit same-person confirmation/merge flow.
- Export endpoint supports dry-run planning and explicit commit mode.


## Phase 1 hardening

- Added typed app settings (`MEMORY_MAP_*`) for storage/cors configuration.
- Added background job endpoints for face grouping and export workloads.
- Added merge preview and undo support for people identity operations.

- Family tree relationships (parent/spouse/sibling/child) with profile metadata.

- Folder-dump importer for bulk ingestion and profile-aware suggestions.

- Import queue screen with confidence cards and reasons for each AI suggestion.
- Review keyboard shortcuts: S / Ctrl+Enter to confirm, K to skip.
- Family tree graph view for visual relationship mapping.

Run migrations again after pulling updates to include genealogy and provenance changes (`0003_provenance_and_settings`).


## New in this iteration

- Suggestion provenance reasons are now returned and shown in Review/Import Queue.
- Confidence thresholds are configurable in Settings and persisted in the backend DB.
- Batch review endpoint and keyboard flow (`B`) to apply common tags to many photos at once.
- Person detail page with linked photos, timeline, places, and relatives.
