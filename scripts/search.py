#!/usr/bin/env python3
"""
Hybrid search: combines FTS5 keyword search + vector semantic search
using Reciprocal Rank Fusion (RRF) to merge results.

Usage: python3 search.py "your query here" [--limit N]
"""
import json
import os
import sqlite3
import sys
from collections import defaultdict

import numpy as np
import sqlite_vec
from sentence_transformers import SentenceTransformer

DB_PATH = "/workspace/scripts/search.db"
METADATA_PATH = "/workspace/scripts/talk_metadata.json"
RRF_K = 60  # RRF constant (standard value)
SPEAKER_BOOST = 0.03  # bonus score when speaker name matches query terms

_model = None
_metadata = None


def get_metadata():
    global _metadata
    if _metadata is None:
        _metadata = {}
        if os.path.exists(METADATA_PATH):
            with open(METADATA_PATH) as f:
                _metadata = json.load(f)
    return _metadata

def get_model():
    global _model
    if _model is None:
        _model = SentenceTransformer("all-MiniLM-L6-v2")
    return _model


def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.enable_load_extension(True)
    sqlite_vec.load(conn)
    return conn


def make_fts_queries(query):
    """Build AND query (all terms required) and OR query (any term) for FTS5."""
    terms = query.strip().split()
    if len(terms) <= 1:
        return [query]
    and_query = " AND ".join(terms)
    or_query = " OR ".join(terms)
    return [and_query, or_query]


def fts_chunk_search(conn, query, limit=50):
    """FTS5 keyword search on chunks. Runs AND query first, then OR to fill."""
    queries = make_fts_queries(query)
    results = []
    seen = set()
    for q in queries:
        try:
            rows = conn.execute("""
                SELECT c.cat_num, c.chunk_index, c.text, rank
                FROM chunks_fts
                JOIN chunks c ON c.id = chunks_fts.rowid
                WHERE chunks_fts MATCH ?
                ORDER BY rank
                LIMIT ?
            """, [q, limit]).fetchall()
        except Exception:
            continue
        for r in rows:
            key = (r[0], r[1])
            if key not in seen:
                seen.add(key)
                results.append((r[0], r[1], r[2], r[3]))
        if len(results) >= limit:
            break
    return results[:limit]


def fts_summary_search(conn, query, limit=50):
    """FTS5 keyword search on summaries. Runs AND query first, then OR to fill."""
    queries = make_fts_queries(query)
    results = []
    seen = set()
    for q in queries:
        try:
            rows = conn.execute("""
                SELECT s.cat_num, s.text, rank
                FROM summaries_fts
                JOIN summaries s ON s.rowid = summaries_fts.rowid
                WHERE summaries_fts MATCH ?
                ORDER BY rank
                LIMIT ?
            """, [q, limit]).fetchall()
        except Exception:
            continue
        for r in rows:
            if r[0] not in seen:
                seen.add(r[0])
                results.append((r[0], r[1], r[2]))
        if len(results) >= limit:
            break
    return results[:limit]


def vec_chunk_search(conn, query_embedding, limit=50):
    """Vector search on chunk embeddings."""
    qvec = query_embedding.astype(np.float32).tobytes()
    rows = conn.execute("""
        SELECT c.cat_num, c.chunk_index, c.text, v.distance
        FROM chunks_vec v
        JOIN chunks c ON c.id = v.rowid
        WHERE v.embedding MATCH ? AND k = ?
        ORDER BY v.distance
    """, [qvec, limit]).fetchall()
    return [(r[0], r[1], r[2], r[3]) for r in rows]


def vec_summary_search(conn, query_embedding, limit=50):
    """Vector search on summary embeddings."""
    qvec = query_embedding.astype(np.float32).tobytes()
    rows = conn.execute("""
        SELECT s.cat_num, s.text, v.distance
        FROM summaries_vec v
        JOIN summaries s ON s.rowid = v.rowid
        WHERE v.embedding MATCH ? AND k = ?
        ORDER BY v.distance
    """, [qvec, limit]).fetchall()
    return [(r[0], r[1], r[2]) for r in rows]


def rrf_score(rank):
    """Reciprocal Rank Fusion score."""
    return 1.0 / (RRF_K + rank)


def hybrid_search(query, limit=15):
    """
    Run all four searches, group results by cat_num,
    combine with RRF, and return ranked talks.
    """
    conn = get_conn()
    model = get_model()
    query_embedding = model.encode(query)

    # Run all searches
    fts_chunks = fts_chunk_search(conn, query)
    fts_summaries = fts_summary_search(conn, query)
    vec_chunks = vec_chunk_search(conn, query_embedding)
    vec_summaries = vec_summary_search(conn, query_embedding)

    # Aggregate scores by cat_num
    scores = defaultdict(float)
    best_snippet = {}  # cat_num -> best matching text snippet

    # FTS chunk results: group by cat_num, take best rank per talk
    seen_fts_chunk = {}
    for rank_pos, (cat_num, chunk_idx, text, fts_rank) in enumerate(fts_chunks):
        if cat_num not in seen_fts_chunk:
            seen_fts_chunk[cat_num] = rank_pos
            scores[cat_num] += rrf_score(rank_pos + 1)
            if cat_num not in best_snippet:
                best_snippet[cat_num] = text[:200]

    # FTS summary results
    for rank_pos, (cat_num, text, fts_rank) in enumerate(fts_summaries):
        scores[cat_num] += rrf_score(rank_pos + 1)
        if cat_num not in best_snippet:
            best_snippet[cat_num] = text[:200]

    # Vector chunk results: group by cat_num
    seen_vec_chunk = {}
    for rank_pos, (cat_num, chunk_idx, text, distance) in enumerate(vec_chunks):
        if cat_num not in seen_vec_chunk:
            seen_vec_chunk[cat_num] = rank_pos
            scores[cat_num] += rrf_score(rank_pos + 1)
            if cat_num not in best_snippet:
                best_snippet[cat_num] = text[:200]

    # Vector summary results
    for rank_pos, (cat_num, text, distance) in enumerate(vec_summaries):
        scores[cat_num] += rrf_score(rank_pos + 1)
        if cat_num not in best_snippet:
            best_snippet[cat_num] = text[:200]

    conn.close()

    # Boost talks whose speaker matches query terms
    metadata = get_metadata()
    query_terms = [t.lower() for t in query.strip().split()]
    for cat_num in scores:
        speaker = metadata.get(cat_num, {}).get("speaker", "").lower()
        if speaker and any(term in speaker for term in query_terms):
            scores[cat_num] += SPEAKER_BOOST

    # Sort by combined RRF score
    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:limit]

    results = []
    for cat_num, score in ranked:
        meta = metadata.get(cat_num, {})
        results.append({
            "cat_num": cat_num,
            "score": score,
            "title": meta.get("title", ""),
            "speaker": meta.get("speaker", ""),
            "year": meta.get("year", ""),
            "snippet": best_snippet.get(cat_num, ""),
            "url": f"https://www.freebuddhistaudio.com/audio/details?num={cat_num}",
        })

    return results


def main():
    args = sys.argv[1:]
    limit = 15
    if "--limit" in args:
        idx = args.index("--limit")
        limit = int(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    query = " ".join(args)
    if not query:
        print("Usage: python3 search.py \"your query\" [--limit N]")
        sys.exit(1)

    results = hybrid_search(query, limit=limit)

    for i, r in enumerate(results, 1):
        title = r["title"] or r["cat_num"]
        speaker = f" — {r['speaker']}" if r["speaker"] else ""
        year = f" ({r['year']})" if r["year"] else ""
        print(f"{i}. [{r['cat_num']}] {title}{speaker}{year}")
        print(f"   Score: {r['score']:.4f}")
        print(f"   {r['snippet'][:150]}...")
        print(f"   {r['url']}")
        print()


if __name__ == "__main__":
    main()
