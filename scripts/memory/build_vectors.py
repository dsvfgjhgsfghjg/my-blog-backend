#!/usr/bin/env python3
"""Build the local, sanitized MyBlog memory vector index."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import sqlite3
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DB = Path.home() / ".local/share/myblog-memory/knowledge.sqlite3"
DIMENSIONS = 2048
MAX_CHUNK_CHARS = 1000
MIN_CHUNK_CHARS = 100
ASCII_TOKEN_RE = re.compile(r"[a-z0-9_./:\\-]{2,}")
CJK_CHAR_RE = re.compile(r"[\u3400-\u9fff]")
HEADING_RE = re.compile(r"(?m)^(#{1,6})\s+(.+?)\s*$")
FILES = (
    ("memory", "*.md"),
    ("docs/engineering", "*.md"),
    ("docs/app-handoffs", "*.md"),
)


def normalize(text: str) -> str:
    return unicodedata.normalize("NFKC", text).lower()


def tokens_for(text: str) -> list[str]:
    normalized = normalize(text)
    ascii_tokens = ASCII_TOKEN_RE.findall(normalized)
    cjk_chars = CJK_CHAR_RE.findall(normalized)
    cjk_bigrams = [
        "".join(cjk_chars[index : index + 2])
        for index in range(max(0, len(cjk_chars) - 1))
    ]
    ascii_bigrams = [
        f"{ascii_tokens[index]}::{ascii_tokens[index + 1]}"
        for index in range(max(0, len(ascii_tokens) - 1))
    ]
    return ascii_tokens + cjk_chars + cjk_bigrams + ascii_bigrams


def embed_text(text: str) -> np.ndarray:
    vector = np.zeros(DIMENSIONS, dtype=np.float32)
    for token, count in Counter(tokens_for(text)).items():
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
        index = int.from_bytes(digest[:4], "little") % DIMENSIONS
        sign = 1.0 if digest[4] & 1 else -1.0
        vector[index] += sign * (1.0 + math.log1p(count))
    norm = float(np.linalg.norm(vector))
    if norm:
        vector /= norm
    return vector


def split_markdown(path: Path, text: str) -> list[tuple[str, str]]:
    sections = re.split(r"(?m)(?=^#{1,6}\s+)", text)
    chunks: list[tuple[str, str]] = []
    for section in sections:
        section = section.strip()
        if not section:
            continue
        match = HEADING_RE.search(section)
        title = match.group(2).strip() if match else path.stem
        paragraphs = [
            part.strip()
            for part in re.split(r"\n\s*\n", section)
            if part.strip()
        ]
        current: list[str] = []
        size = 0
        for paragraph in paragraphs:
            if current and size + len(paragraph) + 2 > MAX_CHUNK_CHARS:
                chunks.append((title, "\n\n".join(current).strip()))
                current = []
                size = 0
            current.append(paragraph)
            size += len(paragraph) + 2
        if current:
            chunks.append((title, "\n\n".join(current).strip()))

    filtered: list[tuple[str, str]] = []
    for title, content in chunks:
        if len(content) < MIN_CHUNK_CHARS and filtered:
            previous_title, previous = filtered[-1]
            filtered[-1] = (previous_title, f"{previous}\n\n{content}")
        else:
            filtered.append((title, content))
    return filtered


def collect_documents(root: Path) -> list[Path]:
    documents = {root / "AGENTS.md"}
    for relative, pattern in FILES:
        documents.update((root / relative).glob(pattern))
    return sorted(path for path in documents if path.is_file())


def build_index(root: Path, db_path: Path) -> dict[str, object]:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(db_path.parent, 0o700)
    rows: list[tuple[str, str, str, int, str, bytes]] = []
    documents = collect_documents(root)

    for document in documents:
        relative = str(document.relative_to(root))
        text = document.read_text(encoding="utf-8")
        for ordinal, (title, content) in enumerate(split_markdown(document, text)):
            chunk_id = hashlib.sha256(
                f"{relative}:{ordinal}:{content}".encode("utf-8")
            ).hexdigest()[:24]
            vector = embed_text(f"{title}\n{content}")
            rows.append(
                (
                    chunk_id,
                    relative,
                    title,
                    ordinal,
                    content,
                    vector.astype("<f4").tobytes(),
                )
            )

    if db_path.exists():
        backup = Path(f"{db_path}.bak")
        shutil.copy2(db_path, backup)
        os.chmod(backup, 0o600)
        db_path.unlink()

    with sqlite3.connect(db_path) as connection:
        connection.execute(
            """
            CREATE TABLE chunks (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                title TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                content TEXT NOT NULL,
                vector BLOB NOT NULL
            )
            """
        )
        connection.execute(
            "CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
        connection.executemany("INSERT INTO chunks VALUES (?,?,?,?,?,?)", rows)
        metadata = {
            "format": "sqlite-numpy",
            "embedding": "hashed-md-cjk-ngram-v1",
            "dimensions": str(DIMENSIONS),
            "source_scope": "sanitized-memory-engineering-app-handoffs",
            "documents": str(len(documents)),
            "chunks": str(len(rows)),
            "generated_at": datetime.now(timezone.utc).isoformat(),
        }
        connection.executemany(
            "INSERT INTO metadata VALUES (?,?)", metadata.items()
        )
    os.chmod(db_path, 0o600)
    return {
        "documents": len(documents),
        "chunks": len(rows),
        "db_path": str(db_path),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    args = parser.parse_args()
    result = build_index(args.root.resolve(), args.db.expanduser().resolve())
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
