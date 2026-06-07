#!/usr/bin/env python3
"""
Lightweight FTS5 search over talk summaries and metadata.
No dependencies beyond Python stdlib (sqlite3 has FTS5 built-in).

Usage: python3 search_lite.py "your query" [--limit N]
"""
import os
import sqlite3
import sys

# Resolve next to this script so the tool is portable (zip, clone, anywhere).
_HERE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("FBA_SEARCH_LITE_DB", os.path.join(_HERE, "search_lite.db"))


def make_fts_queries(query):
    """Build AND query (all terms required) and OR query (any term) for FTS5."""
    terms = query.strip().split()
    if len(terms) <= 1:
        return [query]
    and_query = " AND ".join(terms)
    or_query = " OR ".join(terms)
    return [and_query, or_query]


def search(query, limit=15):
    conn = sqlite3.connect(DB_PATH)
    queries = make_fts_queries(query)

    results = []
    seen = set()

    for q in queries:
        try:
            rows = conn.execute("""
                SELECT t.cat_num, t.title, t.speaker, t.year, t.summary, rank
                FROM talks_fts
                JOIN talks t ON t.rowid = talks_fts.rowid
                WHERE talks_fts MATCH ?
                ORDER BY rank
                LIMIT ?
            """, [q, limit]).fetchall()
        except Exception:
            continue

        for r in rows:
            if r[0] not in seen:
                seen.add(r[0])
                results.append({
                    "cat_num": r[0],
                    "title": r[1],
                    "speaker": r[2],
                    "year": r[3],
                    "summary": r[4],
                    "rank": r[5],
                    "url": f"https://www.freebuddhistaudio.com/audio/details?num={r[0]}",
                })
        if len(results) >= limit:
            break

    conn.close()
    return results[:limit]


def main():
    args = sys.argv[1:]
    limit = 15
    if "--limit" in args:
        idx = args.index("--limit")
        limit = int(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    query = " ".join(args)
    if not query:
        print("Usage: python3 search_lite.py \"your query\" [--limit N]")
        sys.exit(1)

    results = search(query, limit=limit)

    for i, r in enumerate(results, 1):
        title = r["title"] or r["cat_num"]
        speaker = f" — {r['speaker']}" if r["speaker"] else ""
        year = f" ({r['year']})" if r["year"] else ""
        print(f"{i}. [{r['cat_num']}] {title}{speaker}{year}")
        print(f"   {r['summary'][:150]}...")
        print(f"   {r['url']}")
        print()


if __name__ == "__main__":
    main()
