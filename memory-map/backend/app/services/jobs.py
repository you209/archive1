from __future__ import annotations

from dataclasses import dataclass, field
from threading import Lock, Thread
from typing import Any, Callable
from uuid import uuid4


@dataclass
class Job:
    id: str
    status: str = "queued"
    result: Any = None
    error: str | None = None


class JobManager:
    def __init__(self):
        self._jobs: dict[str, Job] = {}
        self._lock = Lock()

    def enqueue(self, fn: Callable[[], Any]) -> Job:
        job = Job(id=uuid4().hex)
        with self._lock:
            self._jobs[job.id] = job

        def run():
            job.status = "running"
            try:
                job.result = fn()
                job.status = "done"
            except Exception as exc:  # noqa: BLE001
                job.error = str(exc)
                job.status = "failed"

        Thread(target=run, daemon=True).start()
        return job

    def get(self, job_id: str) -> Job | None:
        with self._lock:
            return self._jobs.get(job_id)


job_manager = JobManager()
