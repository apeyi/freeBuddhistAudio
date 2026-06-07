#!/usr/bin/env python3
"""
Builds a SQLite search database with:
- FTS5 full-text search on chunk text and summaries
- sqlite-vec vector search on chunk embeddings and summary embeddings
"""
import json
import os
import sqlite3
import struct
import sys
import numpy as np
import sqlite_vec

DB_PATH = "/workspace/scripts/search.db"
CHUNKS_DIR = "/workspace/scripts/chunks"
SUMMARY_TEXTS = "/workspace/scripts/summary_texts.json"
SUMMARY_CATNUMS = "/workspace/scripts/summary_catnums.json"
SUMMARY_EMBEDDINGS = "/workspace/scripts/summary_embeddings.npy"


def serialize_f32(vec):
    """Serialize a float32 numpy array to bytes for sqlite-vec."""
    return vec.astype(np.float32).tobytes()


def create_tables(conn):
    conn.execute("DROP TABLE IF EXISTS chunks")
    conn.execute("DROP TABLE IF EXISTS chunks_fts")
    conn.execute("DROP TABLE IF EXISTS summaries")
    conn.execute("DROP TABLE IF EXISTS summaries_fts")

    # Chunks table
    conn.execute("""
        CREATE TABLE chunks (
            id INTEGER PRIMARY KEY,
            cat_num TEXT NOT NULL,
            chunk_index INTEGER NOT NULL,
            text TEXT NOT NULL,
            token_count INTEGER,
            word_count INTEGER
        )
    """)
    conn.execute("CREATE INDEX idx_chunks_catnum ON chunks(cat_num)")

    # FTS5 on chunks
    conn.execute("""
        CREATE VIRTUAL TABLE chunks_fts USING fts5(
            text,
            content=chunks,
            content_rowid=id,
            tokenize='porter unicode61'
        )
    """)

    # Summaries table
    conn.execute("""
        CREATE TABLE summaries (
            cat_num TEXT PRIMARY KEY,
            text TEXT NOT NULL
        )
    """)

    # FTS5 on summaries
    conn.execute("""
        CREATE VIRTUAL TABLE summaries_fts USING fts5(
            text,
            content=summaries,
            content_rowid=rowid,
            tokenize='porter unicode61'
        )
    """)

    conn.commit()


def create_vec_tables(conn):
    """Create virtual tables for vector search."""
    conn.execute("DROP TABLE IF EXISTS chunks_vec")
    conn.execute("DROP TABLE IF EXISTS summaries_vec")

    conn.execute("""
        CREATE VIRTUAL TABLE chunks_vec USING vec0(
            embedding float[384]
        )
    """)
    conn.execute("""
        CREATE VIRTUAL TABLE summaries_vec USING vec0(
            embedding float[384]
        )
    """)
    conn.commit()


def load_chunks(conn):
    """Load all chunk JSON + numpy files into the database."""
    # Find all chunk files
    chunk_files = sorted(
        f for f in os.listdir(CHUNKS_DIR)
        if f.startswith("chunks_") and f.endswith(".json")
    )

    total_chunks = 0
    chunk_id = 0

    for cf in chunk_files:
        batch_num = cf.replace("chunks_", "").replace(".json", "")
        json_path = os.path.join(CHUNKS_DIR, cf)
        npy_path = os.path.join(CHUNKS_DIR, f"embeddings_{batch_num}.npy")

        with open(json_path) as f:
            chunks = json.load(f)

        embeddings = np.load(npy_path)
        assert len(chunks) == len(embeddings), f"Mismatch in {cf}: {len(chunks)} chunks vs {len(embeddings)} embeddings"

        # Batch insert chunks
        chunk_rows = []
        fts_rows = []
        vec_rows = []

        for i, chunk in enumerate(chunks):
            chunk_id += 1
            chunk_rows.append((
                chunk_id,
                chunk["catNum"],
                chunk["i"],
                chunk["t"],
                chunk["n"],
                chunk.get("w", 0),
            ))
            fts_rows.append((chunk_id, chunk["t"]))
            vec_rows.append((chunk_id, serialize_f32(embeddings[i])))

        conn.executemany(
            "INSERT INTO chunks (id, cat_num, chunk_index, text, token_count, word_count) VALUES (?, ?, ?, ?, ?, ?)",
            chunk_rows
        )
        conn.executemany(
            "INSERT INTO chunks_fts (rowid, text) VALUES (?, ?)",
            fts_rows
        )
        conn.executemany(
            "INSERT INTO chunks_vec (rowid, embedding) VALUES (?, ?)",
            vec_rows
        )
        conn.commit()

        total_chunks += len(chunks)
        print(f"  Loaded {cf}: {len(chunks)} chunks (total: {total_chunks})")

    return total_chunks


def load_summaries(conn):
    """Load summary texts and embeddings."""
    with open(SUMMARY_TEXTS) as f:
        summary_texts = json.load(f)
    with open(SUMMARY_CATNUMS) as f:
        summary_catnums = json.load(f)

    summary_embeddings = np.load(SUMMARY_EMBEDDINGS)
    assert len(summary_catnums) == len(summary_embeddings)

    # Insert summaries
    summary_rows = []
    fts_rows = []

    for cat_num in summary_catnums:
        text = summary_texts.get(cat_num, "")
        if text:
            summary_rows.append((cat_num, text))

    conn.executemany(
        "INSERT INTO summaries (cat_num, text) VALUES (?, ?)",
        summary_rows
    )
    conn.commit()

    # Build rowid mapping for FTS
    rows = conn.execute("SELECT rowid, cat_num FROM summaries").fetchall()
    rowid_map = {cat_num: rowid for rowid, cat_num in rows}

    fts_rows = [(rowid_map[cat_num], text) for cat_num, text in summary_rows if cat_num in rowid_map]
    conn.executemany(
        "INSERT INTO summaries_fts (rowid, text) VALUES (?, ?)",
        fts_rows
    )

    # Vector embeddings for summaries
    vec_rows = []
    for i, cat_num in enumerate(summary_catnums):
        if cat_num in rowid_map:
            vec_rows.append((rowid_map[cat_num], serialize_f32(summary_embeddings[i])))

    conn.executemany(
        "INSERT INTO summaries_vec (rowid, embedding) VALUES (?, ?)",
        vec_rows
    )
    conn.commit()

    return len(summary_rows)


def main():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    conn.enable_load_extension(True)
    sqlite_vec.load(conn)

    print("Creating tables...")
    create_tables(conn)
    create_vec_tables(conn)

    print("Loading chunks...")
    n_chunks = load_chunks(conn)
    print(f"Total chunks: {n_chunks}")

    print("Loading summaries...")
    n_summaries = load_summaries(conn)
    print(f"Total summaries: {n_summaries}")

    # Stats
    print("\n--- Database Stats ---")
    for table in ["chunks", "summaries", "chunks_vec", "summaries_vec"]:
        count = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        print(f"  {table}: {count} rows")

    db_size = os.path.getsize(DB_PATH) / (1024 * 1024)
    print(f"\n  Database size: {db_size:.1f} MB")

    conn.close()
    print(f"\nDone! Database saved to {DB_PATH}")


if __name__ == "__main__":
    main()
