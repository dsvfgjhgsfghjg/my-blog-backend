#!/usr/bin/env python3
"""Search the local sanitized MyBlog memory vector index."""

from __future__ import annotations

import argparse
import sqlite3
from pathlib import Path

import numpy as np

from build_vectors import DEFAULT_DB, embed_text


def shorten(text: str, limit: int = 360) -> str:
    compact = " ".join(text.split())
    return compact if len(compact) <= limit else compact[: limit - 1] + "..."


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("query")
    parser.add_argument("-k", "--limit", type=int, default=5)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    args = parser.parse_args()
    db_path = args.db.expanduser().resolve()
    if not db_path.exists():
        raise SystemExit(f"Vector database not found: {db_path}")

    with sqlite3.connect(db_path) as connection:
        rows = connection.execute(
            "SELECT id, source, title, content, vector FROM chunks"
        ).fetchall()
    if not rows:
        raise SystemExit("Vector database is empty")

    matrix = np.vstack(
        [np.frombuffer(row[4], dtype="<f4") for row in rows]
    )
    scores = matrix @ embed_text(args.query)
    indexes = np.argsort(scores)[::-1][: args.limit]
    for rank, index in enumerate(indexes, start=1):
        chunk_id, source, title, content, _ = rows[int(index)]
        print(f"{rank}. score={scores[int(index)]:.4f}  {title}")
        print(f"   source: {source}  id: {chunk_id}")
        print(f"   {shorten(content)}")
        print()


if __name__ == "__main__":
    main()
