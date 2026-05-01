from pathlib import Path
import shutil


def slugify(value: str) -> str:
    return "-".join(part for part in value.replace("/", " ").split() if part)


def build_export_plan(photos: list[dict], export_root: Path) -> list[dict[str, str]]:
    counters: dict[tuple[str, str], int] = {}
    plan: list[dict[str, str]] = []

    for photo in photos:
        year = photo.get("confirmed_year") or photo.get("suggested_year") or "Unknown Year"
        event = photo.get("confirmed_event") or photo.get("suggested_event") or "Unknown Event"

        key = (year, event)
        counters[key] = counters.get(key, 0) + 1
        idx = counters[key]

        output_name = f"{slugify(year)}-{slugify(event)}-{idx:03d}{Path(photo['original_name']).suffix or '.jpg'}"
        destination = export_root / year / event / output_name
        plan.append({"source": photo["path"], "destination": str(destination)})

    return plan


def apply_export_plan(plan: list[dict[str, str]]) -> list[str]:
    created = []
    for item in plan:
        destination = Path(item["destination"])
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item["source"], destination)
        created.append(str(destination))
    return created
