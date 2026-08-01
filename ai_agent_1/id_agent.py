#!/usr/bin/env python3
"""ID-document intake agent.

Watches an inbox folder for photos of identity documents (passport, national
ID, driving licence, ...). Each NEW image is sent once to an OpenAI vision
model, the extracted data points are written as a CSV into an output folder
named after the person on the document, and the source image is archived next
to the CSV.

Guardrails
----------
1. Only new files are sent: a SHA-256 ledger (.processed_ledger.json) records
   every file ever processed; duplicates (even renamed copies) are skipped.
2. No assumed values: the extraction schema makes every field nullable and the
   prompt forbids guessing. Fields the model cannot read come back as null and
   are written as empty CSV cells.
3. Non-ID images are not extracted; they are moved to rejected/.
"""

import argparse
import base64
import csv
import hashlib
import json
import logging
import mimetypes
import os
import re
import shutil
import sys
import time
from pathlib import Path
from typing import Dict, Optional

from dotenv import load_dotenv
from openai import OpenAI
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer

log = logging.getLogger("id_agent")

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tiff", ".tif"}
LEDGER_NAME = ".processed_ledger.json"
MAX_ATTEMPTS = 3
RESCAN_INTERVAL_S = 60

# Every field nullable on purpose: the model must return null for anything it
# cannot read directly off the document.
EXTRACTION_FIELDS = [
    "document_type",
    "issuing_country",
    "issuing_authority",
    "full_name",
    "surname",
    "given_names",
    "document_number",
    "personal_number",
    "nationality",
    "date_of_birth",
    "place_of_birth",
    "sex",
    "date_of_issue",
    "date_of_expiry",
    "address",
    "mrz_line_1",
    "mrz_line_2",
]

EXTRACTION_SCHEMA = {
    "type": "object",
    "properties": dict(
        {"is_identity_document": {
            "type": "boolean",
            "description": "True only if the image clearly shows an identity document.",
        }},
        **{f: {"type": ["string", "null"]} for f in EXTRACTION_FIELDS},
    ),
    "required": ["is_identity_document"] + EXTRACTION_FIELDS,
    "additionalProperties": False,
}

SYSTEM_PROMPT = (
    "You are a meticulous data-entry operator for identity documents. "
    "Transcribe ONLY text that is clearly legible in the image, exactly as "
    "printed (keep original date formats and spelling). If a field is "
    "missing, obscured, ambiguous, or you are not certain, return null for "
    "it. Never infer, normalise, translate, or guess any value. If the image "
    "is not an identity document, set is_identity_document to false and all "
    "other fields to null."
)


class Ledger:
    """Hash ledger of already-processed files (guardrail: only send new files)."""

    def __init__(self, path: Path):
        self.path = path
        self.entries: Dict[str, dict] = {}
        if path.exists():
            try:
                self.entries = json.loads(path.read_text())
            except (json.JSONDecodeError, OSError):
                log.warning("Ledger %s unreadable, starting fresh", path)

    @staticmethod
    def digest(file_path: Path) -> str:
        h = hashlib.sha256()
        with file_path.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        return h.hexdigest()

    def seen(self, digest: str) -> bool:
        return digest in self.entries

    def record(self, digest: str, file_path: Path, status: str, detail: str = "") -> None:
        self.entries[digest] = {
            "file": file_path.name,
            "status": status,
            "detail": detail,
            "processed_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.entries, indent=2))
        tmp.replace(self.path)


def wait_until_stable(file_path: Path, checks: int = 2, interval: float = 1.0) -> bool:
    """Return True once the file size stops changing (upload/copy finished)."""
    last = -1
    stable = 0
    for _ in range(60):
        try:
            size = file_path.stat().st_size
        except OSError:
            return False
        if size == last and size > 0:
            stable += 1
            if stable >= checks:
                return True
        else:
            stable = 0
        last = size
        time.sleep(interval)
    return False


def extract_document(client: OpenAI, model: str, file_path: Path) -> dict:
    mime = mimetypes.guess_type(file_path.name)[0] or "image/jpeg"
    encoded = base64.b64encode(file_path.read_bytes()).decode("ascii")
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Extract the data points from this identity document.",
                    },
                    {
                        "type": "image_url",
                        "image_url": {"url": "data:{};base64,{}".format(mime, encoded)},
                    },
                ],
            },
        ],
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "id_document_extraction",
                "schema": EXTRACTION_SCHEMA,
                "strict": True,
            },
        },
    )
    return json.loads(response.choices[0].message.content)


def folder_name_from(data: dict) -> str:
    name = data.get("full_name") or " ".join(
        part for part in (data.get("given_names"), data.get("surname")) if part
    )
    if not name or not name.strip():
        return "UNKNOWN_NAME"
    cleaned = re.sub(r"[^\w \-']", "", name).strip()  # \w keeps Unicode letters
    return re.sub(r"\s+", "_", cleaned).upper() or "UNKNOWN_NAME"


def write_csv(data: dict, person_dir: Path, source: Path) -> Path:
    person_dir.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    csv_path = person_dir / "{}_{}.csv".format(source.stem, stamp)
    with csv_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["source_file"] + EXTRACTION_FIELDS)
        writer.writerow(
            [source.name] + [data.get(f) if data.get(f) is not None else "" for f in EXTRACTION_FIELDS]
        )
    return csv_path


class IdAgent:
    def __init__(self, inbox: Path, output: Path, rejected: Path, model: str):
        self.inbox = inbox
        self.output = output
        self.rejected = rejected
        self.model = model
        self.ledger = Ledger(output / LEDGER_NAME)
        self.client = OpenAI()
        self.attempts: Dict[str, int] = {}

    def process(self, file_path: Path) -> None:
        if file_path.suffix.lower() not in IMAGE_EXTENSIONS or file_path.name.startswith("."):
            return
        if not wait_until_stable(file_path):
            log.warning("Skipping %s: never became stable/readable", file_path.name)
            return

        digest = Ledger.digest(file_path)
        if self.ledger.seen(digest):
            log.info("Skipping %s: already processed (duplicate content)", file_path.name)
            return
        if self.attempts.get(digest, 0) >= MAX_ATTEMPTS:
            return
        self.attempts[digest] = self.attempts.get(digest, 0) + 1

        log.info("Processing %s (attempt %d)", file_path.name, self.attempts[digest])
        try:
            data = extract_document(self.client, self.model, file_path)
        except Exception as exc:  # noqa: BLE001 - retried on next rescan
            log.error("Extraction failed for %s: %s", file_path.name, exc)
            return

        if not data.get("is_identity_document"):
            log.warning("%s is not an identity document, moving to rejected/", file_path.name)
            self.rejected.mkdir(parents=True, exist_ok=True)
            shutil.move(str(file_path), str(self.rejected / file_path.name))
            self.ledger.record(digest, file_path, "rejected", "not an identity document")
            return

        person_dir = self.output / folder_name_from(data)
        csv_path = write_csv(data, person_dir, file_path)
        shutil.move(str(file_path), str(person_dir / file_path.name))
        self.ledger.record(digest, file_path, "ok", str(csv_path))
        missing = [f for f in EXTRACTION_FIELDS if data.get(f) is None]
        log.info("Saved %s (%d fields empty: %s)", csv_path,
                 len(missing), ", ".join(missing) or "none")

    def scan_inbox(self) -> None:
        for entry in sorted(self.inbox.iterdir()):
            if entry.is_file():
                self.process(entry)

    def seed_existing(self) -> None:
        """Mark files already in the inbox as processed without sending them."""
        for entry in sorted(self.inbox.iterdir()):
            if entry.is_file() and entry.suffix.lower() in IMAGE_EXTENSIONS:
                digest = Ledger.digest(entry)
                if not self.ledger.seen(digest):
                    self.ledger.record(digest, entry, "seeded", "present before startup")
                    log.info("Seeded existing file %s (will not be sent)", entry.name)


class InboxHandler(FileSystemEventHandler):
    def __init__(self, agent: IdAgent):
        self.agent = agent

    def on_created(self, event):
        if not event.is_directory:
            self.agent.process(Path(event.src_path))

    def on_moved(self, event):
        if not event.is_directory:
            self.agent.process(Path(event.dest_path))


def main(argv: Optional[list] = None) -> int:
    load_dotenv(Path(__file__).resolve().parent / ".env")
    default_model = os.environ.get("OPENAI_MODEL", "gpt-4o")

    parser = argparse.ArgumentParser(description="Monitor a folder for ID photos and extract data to CSV.")
    parser.add_argument("--inbox", default="incoming", help="folder to watch (default: incoming)")
    parser.add_argument("--output", default="extracted", help="output root (default: extracted)")
    parser.add_argument("--rejected", default="rejected", help="folder for non-ID images (default: rejected)")
    parser.add_argument("--model", default=default_model,
                        help="OpenAI vision model (default: %(default)s, set via OPENAI_MODEL in .env)")
    parser.add_argument("--skip-existing", action="store_true",
                        help="do not process files already in the inbox at startup, only future arrivals")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    api_key = os.environ.get("OPENAI_API_KEY", "")
    if not api_key or "REPLACE_ME" in api_key:
        log.error("OPENAI_API_KEY is not set. Put it in %s (see .env.example).",
                  Path(__file__).resolve().parent / ".env")
        return 1

    inbox, output, rejected = Path(args.inbox), Path(args.output), Path(args.rejected)
    inbox.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)

    agent = IdAgent(inbox, output, rejected, args.model)
    if args.skip_existing:
        agent.seed_existing()
    else:
        agent.scan_inbox()

    observer = Observer()
    observer.schedule(InboxHandler(agent), str(inbox), recursive=False)
    observer.start()
    log.info("Watching %s -> %s (model: %s). Ctrl-C to stop.", inbox, output, args.model)
    try:
        while True:
            time.sleep(RESCAN_INTERVAL_S)
            agent.scan_inbox()  # fallback for events missed (e.g. network shares)
    except KeyboardInterrupt:
        log.info("Stopping.")
    finally:
        observer.stop()
        observer.join()
    return 0


if __name__ == "__main__":
    sys.exit(main())
