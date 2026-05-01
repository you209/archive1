from __future__ import annotations

import shutil
import uuid
from pathlib import Path


def import_folder(source: Path, destination_dir: Path) -> list[dict[str, str]]:
    imported: list[dict[str, str]] = []
    if not source.exists() or not source.is_dir():
        return imported

    for path in sorted(source.iterdir()):
        if not path.is_file():
            continue
        if path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp", ".tif", ".tiff"}:
            continue

        generated = f"{uuid.uuid4().hex}{path.suffix.lower()}"
        destination = destination_dir / generated
        shutil.copy2(path, destination)
        imported.append({"original_name": path.name, "stored_name": generated, "path": str(destination)})

    return imported
