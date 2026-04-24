from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .database import Base, engine
from .routers import meta, people, photos
from .settings import settings

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Memory Map API", version="0.3.0")

origins = [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins or ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(photos.router)
app.include_router(meta.router)
app.include_router(people.router)

settings.photos_dir.mkdir(parents=True, exist_ok=True)
app.mount("/media", StaticFiles(directory=settings.photos_dir), name="media")


@app.get("/")
def health():
    return {"message": "Memory Map API is running"}
