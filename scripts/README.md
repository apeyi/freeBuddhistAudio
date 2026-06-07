# FBAudio Transcription & Search Pipeline

Tooling that transcribes the Free Buddhist Audio talk archive with Whisper and
builds a searchable index over the transcripts. **Code is version-controlled
here; the generated data (transcripts, databases, embeddings) is not** — see
"Where the data lives" below.

## Where the data lives

| Artifact | Size | Location |
|----------|------|----------|
| Transcripts (~6,600 `.txt`) | ~236 MB | GitHub **release** `transcripts-round2` (asset bundle), not in the repo |
| Full search DB (`search.db`) | ~631 MB | GitHub **release** `search-full-db` (built artifact) |
| Lite search DB (`search_lite.db`) | ~7.6 MB | Built locally / shared as a zip |
| Chunk embeddings (`*.npy`), summaries | — | Local only (regenerable) |

> To rebuild the full database you first need the transcripts. Download them
> from the `transcripts-round2` release and extract into `scripts/transcripts/`
> (or pass `--input` to the chunker). They are stored as release assets rather
> than in git because of their size.

## Pipeline overview

```
FBA website ──scrape──> all_talks.json ──batch──> batches/batch_*.json
     │                                                   │
     │                                          transcribe (Whisper on Vast.ai GPUs)
     ▼                                                   ▼
 audio URLs                                    transcripts/*.txt  ──> GitHub release
                                                         │
                                          chunk + embed + summarise
                                                         ▼
                              search.db (FTS5 + sqlite-vec)  /  search_lite.db (FTS5 only)
```

### Scripts

| Script | Purpose |
|--------|---------|
| `scrape_talk_urls.py` | Scrape all catNums + audio URLs from the FBA site → `all_talks.json` |
| `transcribe_orchestrator.py` | Split into batches, launch Vast.ai GPU workers, collect results |
| `launch_transcription.py` | Provision Vast.ai workers (cheapest GPU), upload results to a GitHub release |
| `transcribe_batch.py` | Per-worker: download audio, run Whisper, upload transcripts |
| `check_transcription.py` | Monitor workers, download completed transcripts |
| `build_search_db.py` | Build the **full** hybrid DB: FTS5 + sqlite-vec 384-dim embeddings |
| `build_search_lite.py` | Build the **lite** DB: FTS5 over talk summaries + metadata only |
| `search.py` | Query the full DB — hybrid FTS5 + vector search fused with RRF |
| `search_lite.py` | Query the lite DB — keyword search, stdlib `sqlite3` only |

`batches/` holds the per-batch catNum lists used by the transcription workers.

## Search: two tiers

### Lite (recommended starting point)
- **7.6 MB**, **zero dependencies** (`sqlite3` FTS5 is built into Python)
- Keyword search over per-talk summaries + title + speaker
```bash
python3 search_lite.py "impermanence and letting go"
```

### Full (hybrid semantic)
- **631 MB**, needs `numpy`, `sentence-transformers`, `sqlite-vec`
- FTS5 keyword **plus** vector semantic search over full-transcript chunks,
  merged with Reciprocal Rank Fusion; understands intent, not just keywords
```bash
pip install numpy sentence-transformers sqlite-vec
python3 search.py "what is the difference between men and women"
```

The full DB embeds with `all-MiniLM-L6-v2` (384-dim). The query is encoded with
the same model at search time, so the first run downloads ~90 MB of model
weights.

## Rebuilding from scratch

1. Scrape: `python3 scrape_talk_urls.py --output all_talks.json`
2. Transcribe (GPU, costs money): `VASTAI_API_KEY=… python3 transcribe_orchestrator.py`
   — or just download existing transcripts from the `transcripts-round2` release.
3. Build lite: `python3 build_search_lite.py` → `search_lite.db`
4. Build full: chunk + embed transcripts, then `python3 build_search_db.py` → `search.db`
