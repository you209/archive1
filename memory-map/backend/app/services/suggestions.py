from __future__ import annotations

from collections import Counter
import hashlib
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - optional dependency in constrained envs
    Image = None


def average_hash(path: Path, size: int = 8) -> str:
    if Image is None:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        return bin(int(digest[:16], 16))[2:].zfill(64)

    with Image.open(path) as img:
        img = img.convert("L").resize((size, size))
        pixels = list(img.getdata())
    avg = sum(pixels) / len(pixels)
    return "".join("1" if p > avg else "0" for p in pixels)


def hamming_distance(hash_a: str, hash_b: str) -> int:
    return sum(a != b for a, b in zip(hash_a, hash_b))


def infer_from_filename(filename: str) -> dict[str, tuple[str, float]]:
    lowered = filename.lower()
    tokens = [token.strip("_-") for token in lowered.replace(".", "-").split("-")]
    year = next((token for token in tokens if token.isdigit() and len(token) == 4), None)
    event_keywords = ["christmas", "wedding", "birthday", "school", "vacation", "easter", "thanksgiving"]
    event = next((keyword for keyword in event_keywords if keyword in lowered), None)

    return {
        "year": (year or "Unknown Year", 0.74 if year else 0.35),
        "event": ((event.title() if event else "Unknown Event"), 0.68 if event else 0.30),
    }


def confidence_from_similarity(distances: list[int]) -> float:
    if not distances:
        return 0.2
    normalized = max(0.0, 1 - (sum(distances) / len(distances) / 64))
    return round(0.5 + (normalized * 0.45), 2)


def propose_people_name(name_votes: list[str]) -> tuple[str, float]:
    if not name_votes:
        return ("Unknown Person", 0.22)
    counter = Counter(name_votes)
    top_name, count = counter.most_common(1)[0]
    confidence = min(0.95, 0.55 + (count / max(1, len(name_votes))) * 0.4)
    return (top_name, round(confidence, 2))
