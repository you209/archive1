from pathlib import Path

from app.services.exporter import build_export_plan


def test_build_export_plan_groups_and_names(tmp_path: Path):
    export_root = tmp_path / "exports"
    photos = [
        {
            "original_name": "img1.jpg",
            "path": "/tmp/img1.jpg",
            "confirmed_year": "1998",
            "suggested_year": None,
            "confirmed_event": "Christmas",
            "suggested_event": None,
        },
        {
            "original_name": "img2.jpg",
            "path": "/tmp/img2.jpg",
            "confirmed_year": "1998",
            "suggested_year": None,
            "confirmed_event": "Christmas",
            "suggested_event": None,
        },
    ]

    plan = build_export_plan(photos, export_root)
    assert len(plan) == 2
    assert plan[0]["destination"].endswith("1998/Christmas/1998-Christmas-001.jpg")
    assert plan[1]["destination"].endswith("1998/Christmas/1998-Christmas-002.jpg")
