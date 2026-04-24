from __future__ import annotations

from pathlib import Path
from threading import Thread

from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer


class ImportHandler(FileSystemEventHandler):
    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    def on_created(self, event):
        if event.is_directory:
            return
        path = Path(event.src_path)
        if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".tif", ".tiff"}:
            self.callback(path)


class ImportWatcher:
    def __init__(self):
        self.observer: Observer | None = None

    def start(self, directory: Path, callback):
        if self.observer:
            return
        directory.mkdir(parents=True, exist_ok=True)
        handler = ImportHandler(callback)
        self.observer = Observer()
        self.observer.schedule(handler, str(directory), recursive=False)
        thread = Thread(target=self.observer.start, daemon=True)
        thread.start()

    def stop(self):
        if self.observer:
            self.observer.stop()
            self.observer.join(1)
            self.observer = None
