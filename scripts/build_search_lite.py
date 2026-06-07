#!/usr/bin/env python3
"""
Builds a lightweight (<20 MB) FTS5 search database from summaries + metadata.
No vector embeddings, no transcript chunks — just keyword search.

Usage: python3 build_search_lite.py [--output search_lite.db]
"""
import json
import os
import sqlite3
import sys

SUMMARY_TEXTS = "/workspace/scripts/summary_texts.json"
METADATA_PATH = "/workspace/scripts/talk_metadata.json"


def build(db_path):
    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)

    # Talks table: metadata for every talk
    conn.execute("""
        CREATE TABLE talks (
            cat_num TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            speaker TEXT NOT NULL DEFAULT '',
            year INTEGER NOT NULL DEFAULT 0,
            summary TEXT NOT NULL DEFAULT ''
        )
    """)

    # FTS5 index over title + speaker + summary
    conn.execute("""
        CREATE VIRTUAL TABLE talks_fts USING fts5(
            title,
            speaker,
            summary,
            content=talks,
            content_rowid=rowid,
            tokenize='porter unicode61'
        )
    """)

    # Load data
    metadata = {}
    if os.path.exists(METADATA_PATH):
        with open(METADATA_PATH) as f:
            metadata = json.load(f)

    summaries = {}
    if os.path.exists(SUMMARY_TEXTS):
        with open(SUMMARY_TEXTS) as f:
            summaries = json.load(f)

    # Merge: all talks from metadata, attach summaries where available
    all_cat_nums = set(metadata.keys()) | set(summaries.keys())

    talk_rows = []
    for cat_num in sorted(all_cat_nums):
        meta = metadata.get(cat_num, {})
        title = meta.get("title", "")
        speaker = meta.get("speaker", "")
        year = meta.get("year", 0)
        summary = summaries.get(cat_num, "")
        talk_rows.append((cat_num, title, speaker, year, summary))

    conn.executemany(
        "INSERT INTO talks (cat_num, title, speaker, year, summary) VALUES (?, ?, ?, ?, ?)",
        talk_rows,
    )
    conn.commit()

    # Populate FTS index
    rows = conn.execute("SELECT rowid, title, speaker, summary FROM talks").fetchall()
    conn.executemany(
        "INSERT INTO talks_fts (rowid, title, speaker, summary) VALUES (?, ?, ?, ?)",
        rows,
    )
    conn.commit()

    # Stats
    count = conn.execute("SELECT COUNT(*) FROM talks").fetchone()[0]
    with_summary = conn.execute("SELECT COUNT(*) FROM talks WHERE summary != ''").fetchone()[0]
    conn.close()

    size_mb = os.path.getsize(db_path) / (1024 * 1024)
    print(f"Talks: {count} ({with_summary} with summaries)")
    print(f"Database: {size_mb:.1f} MB")
    print(f"Saved to: {db_path}")


def main():
    db_path = "/workspace/scripts/search_lite.db"
    if "--output" in sys.argv:
        idx = sys.argv.index("--output")
        db_path = sys.argv[idx + 1]
    build(db_path)


if __name__ == "__main__":
    main()
