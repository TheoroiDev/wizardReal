# wizardreal/tools/benchmark

Backtest harness for voicecast speech engines (voice_overhaul SS2 tools;
re-exported here so reports always reflect the production recognition chain
from the voicecast fat jar — no lab-side replicas). Not shipped in the mod
jar. Same locality policy as the workspace-root `ipa/` lab: sources and
fixtures are tracked; `corpus/`, `out/`, `libs/`, `build/` stay local
(anchored in the repo `.gitignore`).

## Tools

| Tool | Purpose |
|---|---|
| `ipafill.py` + `IpaBench.java` | SS2 tool A — auto-draft IPA templates for aliases missing `trigger.ipa` (espeak G2P -> edge-tts -> IpaPhonemeRecognizer score). See its `--help`. |
| `engbench.py` + `EngineBench.java` | Full-preset-engine backtest: every non-denoiser engine in the models.json v2 catalog x languages x TTS backends x noise conditions, with a per-engine per-language report. |
| `testdata/spells/` | Tracked deterministic fixtures (e.g. `bench_sample.json`). |

## engbench usage

```
python engbench.py --spells-dir <.../data/wizardreal/voicecast/spells> \
    [--catalog <run-dir>/config/voicecast/models.json]     # default: fabric run dir
    [--models-root <workspace>/resources/models]           # default: workspace root
    [--langs en,zh,ja,ko] [--backends edge,sapi,piper]
    [--conditions clean,pink@5,white@0] [--engines id1,id2]
    [--limit N] [--threshold-ipa 0.85] [--fatjar <jar>]
    [--audio-dir audio] [--pick random|all] [--seed N]
```

- **Engines** come from the v2 catalog (`models.<name>.properties.type` in
  `stream|offline|ipa`; `denoiser` is auxiliary and never benched). A model
  whose directory is missing under `--models-root` is skipped with a warning.
- **TTS backends**: `edge` (edge-tts, cloud), `sapi` (Windows System.Speech,
  zero install), `piper` (local neural; needs the `piper` executable, voice
  models download once into `libs/piper-voices/`; no official ja/ko voices —
  those pairs are skipped with a warning). A backend failing for a pair never
  aborts the run.
- **Noise conditions**: `clean`, or `<kind>@<snr_db>` with kind `white|pink`
  (numpy-mixed at the given SNR). Each alias is benched once per condition.
- **Verdicts**: text engines are graded by a mirror of wizardreal's
  `SpellMatcher` (normalize + whole-word containment + Levenshtein
  similarity, threshold 0.8 — ported into `EngineBench.java`, keep in sync);
  the IPA engine keeps its CTC-template score vs `--threshold-ipa`.

- **Audio store** (`--audio-dir`, default `tools/benchmark/audio/`): all
  renders live OUTSIDE the per-run report dir, in `clean/<backend>/` and
  `noisy/<tag>/<backend>/`. Runs are APPEND-only — only missing files are
  synthesized, so growing the spell matrix costs just the new aliases, and
  manual takes dropped into the store join the candidate pool automatically.
- **`--pick random|all`** (default `random`, seed via `--seed`): `random`
  tests each alias x condition against ONE randomly chosen available take;
  `all` enumerates every take (legacy exhaustive matrix).

Outputs land in `out/engbench/<timestamp>/`: `report.md` (engine x language
hit-rate matrix + engine x language x condition breakdown + per-item
detail), `report.json` (aggregates), `raw.jsonl` (one line per engine x
item). The synthesized corpus is NOT inside the report dir — it stays in
the audio store for reuse.

Typical session:

```
# smoke: one engine, cloud TTS, no noise
python engbench.py --spells-dir <spells> --limit 3 \
    --engines wav2vec2-espeak-ipa --backends edge --conditions clean

# full matrix, all engines, both local + cloud TTS
python engbench.py --spells-dir <spells>

# exhaustive backend matrix (legacy), reproducible random pick
python engbench.py --spells-dir <spells> --pick all
python engbench.py --spells-dir <spells> --seed 42
```

Deps: ffmpeg, JDK (`javac`/`java` on PATH), numpy (noise conditions),
espeak-ng (IPA templates), edge-tts (`pip install edge-tts`).
