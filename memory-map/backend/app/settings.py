from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="MEMORY_MAP_", env_file=".env", extra="ignore")

    storage_root: Path = Field(default=Path(__file__).resolve().parent.parent / "storage")
    database_name: str = "memory_map.db"
    cors_origins: str = "*"

    @property
    def database_path(self) -> Path:
        return self.storage_root / self.database_name

    @property
    def photos_dir(self) -> Path:
        return self.storage_root / "photos"

    @property
    def exports_dir(self) -> Path:
        return self.storage_root / "exports"


settings = Settings()
