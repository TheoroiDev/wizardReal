#!/usr/bin/env python3
"""engbench - full-preset-engine backtest for the wizardreal benchmark harness.

Drives every engine declared in the voicecast models.json v2 catalog
(type in stream|offline|ipa; `denoiser` models are auxiliary and skipped)
over spell-trigger aliases rendered by pluggable TTS backends, optionally
degraded by synthetic noise, and produces a per-engine per-language report.

Pipeline:
  spells dir -> (lang, alias) buckets            [reuses ipafill scanning]
  alias x backend -> 16 kHz mono s16 wav          [edge-tts / SAPI / Piper]
  wav x condition -> noise variants               [numpy, SNR-mixed]
  engines x items -> EngineBench (voicecast fat jar, production recognizers)
  raw JSONL -> report.md / report.json            [matrix + per-engine detail]

Usage:
  python engbench.py --spells-dir <.../data/wizardreal/voicecast/spells> \
      [--catalog <run-dir>/config/voicecast/models.json] \
      [--models-root <workspace>/resources/models] \
      [--langs en,zh,ja,ko] [--backends edge,sapi] [--limit N] \
      [--conditions clean,pink@5,white@0] [--engines id1,id2] \
      [--threshold-ipa 0.85] [--out-dir out]

Deps: ffmpeg, JDK (javac/java), numpy (noise mix); edge-tts (cloud backend),
PowerShell System.Speech (SAPI, built into Windows), piper (optional local).
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from ipafill import (  # noqa: E402  (reuse the SS2 tool-A helpers)
    G2P, ESPEAK_LANG, VOICES, convert, edge_tts, find_espeak, find_ffmpeg,
    find_fatjar, find_slf4j, iter_aliases, slug,
)

# ---------------------------------------------------------------- TTS backends

# Piper has no ja/ko official voices (rhasspy/piper-voices) — those pairs are
# skipped with a warning instead of silently missing from the matrix.
PIPER_VOICES = {
    "en": "en_US-lessac-medium",
    "zh": "zh_CN-huayan-medium",
}
PIPER_URL = ("https://huggingface.co/rhasspy/piper-voices/resolve/main/"
             "{part}/{name}.onnx?download=true")
PIPER_JSON_URL = ("https://huggingface.co/rhasspy/piper-voices/resolve/main/"
                  "{part}/{name}.onnx.json?download=true")
# voice name -> HF path parts under rhasspy/piper-voices/main
PIPER_PARTS = {
    "en_US-lessac-medium": "en/en_US/lessac/medium",
    "zh_CN-huayan-medium": "zh/zh_CN/huayan/medium",
}

EDGE_VOICES = dict(VOICES)
EDGE_VOICES.setdefault("ko", "ko-KR-SunHiNeural")  # verified at first use

_SAPI_VOICES: dict[str, str] | None = None


def _ps_run(script: str, timeout: int = 120) -> subprocess.CompletedProcess:
    """Run a PowerShell script via -EncodedCommand (UTF-16LE) so CJK alias
    text survives the command line without codepage games."""
    import base64
    enc = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
    return subprocess.run(["powershell", "-NoProfile", "-EncodedCommand", enc],
                          capture_output=True, text=True, timeout=timeout)


def sapi_voices() -> dict[str, str]:
    """lang prefix -> installed SAPI voice name (enumerated once)."""
    global _SAPI_VOICES
    if _SAPI_VOICES is not None:
        return _SAPI_VOICES
    ps = ("Add-Type -AssemblyName System.Speech;"
          "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
          "$s.GetInstalledVoices() | ForEach-Object { "
          "$_.VoiceInfo.Culture.Parent.Name + '|' + $_.VoiceInfo.Name }")
    r = _ps_run(ps, timeout=60)
    out: dict[str, str] = {}
    if r.returncode == 0:
        for line in r.stdout.splitlines():
            if "|" in line:
                cult, name = line.strip().split("|", 1)
                out.setdefault(cult.split("-")[0].lower(), name)
    _SAPI_VOICES = out
    return out


def sapi_tts(text: str, lang: str, dest: Path) -> bool:
    voice = sapi_voices().get(lang)
    if not voice:
        print(f"  sapi: no voice for '{lang}', skipping pair")
        return False
    ps = f"""
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SetOutputToWaveFile('{dest.as_posix()}',
  (New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,
     [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
     [System.Speech.AudioFormat.AudioChannel]::Mono)))
$s.SelectVoice('{voice}')
$s.Speak('{text.replace("'", "''")}')
$s.Dispose()
"""
    try:
        r = _ps_run(ps)
        return r.returncode == 0 and dest.exists() and dest.stat().st_size > 44
    except subprocess.TimeoutExpired:
        return False


def piper_model(lang: str, libs_dir: Path) -> Path | None:
    """Ensure the piper onnx voice for `lang` exists locally (download once)."""
    name = PIPER_VOICES.get(lang)
    if not name:
        print(f"  piper: no official voice for '{lang}', skipping pair")
        return None
    part = PIPER_PARTS[name]
    model = libs_dir / "piper-voices" / f"{name}.onnx"
    if not model.exists():
        model.parent.mkdir(parents=True, exist_ok=True)
        for url, dest in ((PIPER_URL.format(part=part, name=name), model),
                          (PIPER_JSON_URL.format(part=part, name=name),
                           model.with_suffix(".onnx.json"))):
            print(f"  piper: downloading {name} ...")
            r = subprocess.run(["curl", "-L", "--fail", "-o", str(dest), url],
                               capture_output=True, text=True, timeout=600)
            if r.returncode != 0 or not dest.exists():
                print(f"  piper: download failed ({url})")
                return None
    return model


def piper_tts(text: str, lang: str, libs_dir: Path, dest: Path) -> bool:
    model = piper_model(lang, libs_dir)
    if not model:
        return False
    exe = shutil.which("piper")
    if not exe:
        print("  piper: executable not on PATH, skipping backend")
        return False
    try:
        r = subprocess.run([exe, "--model", str(model), "--output_file", str(dest)],
                           input=text, capture_output=True, text=True, timeout=120)
        return r.returncode == 0 and dest.exists()
    except subprocess.TimeoutExpired:
        return False


def synthesize(text: str, lang: str, backend: str, dest: Path,
               ffmpeg: str, libs_dir: Path) -> bool:
    """backend -> raw audio -> 16 kHz mono s16 wav at `dest`."""
    ext = ".mp3" if backend == "edge" else ".wav"
    raw = dest.with_name(dest.stem + ".raw" + ext)
    ok = False
    if backend == "edge":
        ok = edge_tts(text, EDGE_VOICES.get(lang, ""), raw)
    elif backend == "sapi":
        ok = sapi_tts(text, lang, raw)
    elif backend == "piper":
        ok = piper_tts(text, lang, libs_dir, raw)
    else:
        raise ValueError(f"unknown backend {backend}")
    if not ok:
        return False
    if raw == dest:
        return True
    converted = convert(ffmpeg, raw, dest)
    raw.unlink(missing_ok=True)
    return converted


# ---------------------------------------------------------------- noise

def _pink_noise(n: int, rng) -> "list[float]":
    """Voss-McCartney-ish pink noise via 1/f filtering of white noise."""
    import numpy as np
    white = rng.standard_normal(n)
    spectrum = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n)
    freqs[0] = freqs[1]  # avoid div by zero; DC folded into first band
    spectrum = spectrum / np.sqrt(freqs)
    pink = np.fft.irfft(spectrum, n)
    return pink


def add_noise(src: Path, dest: Path, kind: str, snr_db: float) -> bool:
    """Mix `kind` noise at `snr_db` into a 16 kHz mono s16 wav (numpy)."""
    try:
        import numpy as np
    except ImportError:
        print("noise conditions need numpy (pip install numpy); skipping noise")
        return False
    import wave
    with wave.open(str(src), "rb") as w:
        assert w.getframerate() == 16000 and w.getnchannels() == 1 \
            and w.getsampwidth() == 2, f"unexpected wav format: {src}"
        samples = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16)
    sig = samples.astype(np.float64)
    sig_pow = float(np.mean(sig ** 2)) or 1e-9
    rng = np.random.default_rng()
    noise = (_pink_noise(len(sig), rng) if kind == "pink" else rng.standard_normal(len(sig)))
    noise = noise / (np.sqrt(np.mean(noise ** 2)) + 1e-12)
    noise *= np.sqrt(sig_pow / (10 ** (snr_db / 10)))
    mixed = np.clip(sig + noise, -32768, 32767).astype(np.int16)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(dest), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(mixed.tobytes())
    return True


def parse_conditions(spec: str) -> list[tuple[str, str]]:
    """'clean,pink@5,white@0' -> [('clean',''), ('pink','5'), ...]"""
    out = []
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "@" in part:
            kind, snr = part.split("@", 1)
            out.append((kind.strip(), snr.strip()))
        else:
            out.append((part, ""))
    return out


def condition_tag(kind: str, snr: str) -> str:
    return kind if not snr else f"{kind}{snr}"


# ---------------------------------------------------------------- catalog

def load_catalog(catalog: Path, models_root: Path,
                 engine_filter: set[str]) -> list[dict]:
    """models.json v2 -> engine entries for EngineBench (denoiser skipped)."""
    cat = json.loads(catalog.read_text(encoding="utf-8"))
    engines = []
    for name, entry in cat.get("models", {}).items():
        props = entry.get("properties", {})
        etype = props.get("type", "")
        if etype not in ("stream", "offline", "ipa"):
            continue
        if engine_filter and name not in engine_filter:
            continue
        model_dir = models_root / name
        if not model_dir.is_dir():
            print(f"  catalog: model dir missing, skipping engine '{name}': {model_dir}")
            continue
        options = [{"k": k, "v": str(v)} for k, v in props.items()
                   if k not in ("type", "lang")]
        engines.append({
            "id": name, "type": etype, "modelDir": str(model_dir),
            "languages": [str(l) for l in props.get("lang", [])],
            "options": options,
        })
    return engines


# ---------------------------------------------------------------- report

def build_report(rows: list[dict], conditions: list[str],
                 backends: list[str]) -> tuple[str, dict]:
    def rget(r, k, default=""):
        return r.get(k, default)

    def agg(sel):
        total = [r for r in rows if sel(r)]
        if not total:
            return None
        passed = sum(1 for r in total if rget(r, "verdict") == "pass")
        errs = sum(1 for r in total if rget(r, "verdict") == "error")
        lat = [rget(r, "latencyMs", 0) for r in total if isinstance(rget(r, "latencyMs", 0), (int, float))]
        return {
            "total": len(total), "pass": passed, "error": errs,
            "hitRate": round(passed / len(total), 4),
            "meanLatencyMs": round(sum(lat) / len(lat)) if lat else None,
        }

    engines = sorted({rget(r, "engine") for r in rows})
    langs = sorted({rget(r, "lang") for r in rows})

    report: dict = {"summary": {}, "byCondition": {}, "rows": len(rows)}
    md: list[str] = ["# Engine backtest report", "",
                     f"- conditions: {', '.join(conditions)}",
                     f"- backends: {', '.join(backends)}", ""]

    # engine x language matrix (all conditions pooled)
    md += ["## Hit-rate matrix (engine x language, all conditions)", "",
           "| engine | " + " | ".join(langs) + " | overall |",
           "|---|" + "---|" * (len(langs) + 1)]
    for eng in engines:
        cells = []
        for lang in langs:
            a = agg(lambda r, e=eng, l=lang: rget(r, "engine") == e and rget(r, "lang") == l)
            cells.append(f"{a['pass']}/{a['total']} ({a['hitRate']:.0%})" if a else "-")
            if a:
                report["summary"].setdefault(eng, {})[lang] = a
        a = agg(lambda r, e=eng: rget(r, "engine") == e)
        report["summary"].setdefault(eng, {})["overall"] = a
        cells.append(f"{a['pass']}/{a['total']} ({a['hitRate']:.0%})" if a else "-")
        md.append("| " + eng + " | " + " | ".join(cells) + " |")

    # engine x language x condition breakdown
    md += ["", "## By noise condition (engine x language x condition)", ""]
    for eng in engines:
        md.append(f"### {eng}")
        md += ["| lang | condition | pass/total | hitRate | meanLatencyMs |",
               "|---|---|---|---|---|"]
        for lang in langs:
            for cond in conditions:
                a = agg(lambda r, e=eng, l=lang, c=cond:
                        rget(r, "engine") == e and rget(r, "lang") == l
                        and rget(r, "condition") == c)
                if a:
                    md.append(f"| {lang} | {cond} | {a['pass']}/{a['total']} "
                              f"| {a['hitRate']:.0%} | {a['meanLatencyMs']} |")
                    report["byCondition"].setdefault(eng, {}).setdefault(lang, {})[cond] = a
        md.append("")

    # per-engine per-language detail
    md += ["## Per-item detail", ""]
    for eng in engines:
        for lang in langs:
            sel = [r for r in rows if rget(r, "engine") == eng and rget(r, "lang") == lang]
            if not sel:
                continue
            md.append(f"### {eng} / {lang}")
            md += ["| spell | alias | backend | condition | verdict | score | text | latencyMs |",
                   "|---|---|---|---|---|---|---|---|"]
            for r in sorted(sel, key=lambda x: (rget(x, "spell"), rget(x, "condition"),
                                                rget(x, "backend"))):
                score = rget(r, "matchScore", rget(r, "score", ""))
                if isinstance(score, float):
                    score = f"{score:.4f}"
                text = str(rget(r, "text", "")).replace("|", "\\|")
                md.append(f"| {rget(r, 'spell')} | {rget(r, 'alias')} | {rget(r, 'backend')} "
                          f"| {rget(r, 'condition')} | {rget(r, 'verdict')} | {score} "
                          f"| {text} | {rget(r, 'latencyMs', '')} |")
            md.append("")

    return "\n".join(md), report


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--spells-dir", required=True, type=Path)
    # tools/benchmark/engbench.py -> parents: [benchmark, tools, <repo>, <workspace>]
    repo = Path(__file__).resolve().parents[2]
    workspace = Path(__file__).resolve().parents[3]
    default_catalog = repo / "wizardreal/fabric/run/config/voicecast/models.json"
    ap.add_argument("--catalog", type=Path,
                    default=default_catalog if default_catalog.exists() else None)
    ap.add_argument("--models-root", type=Path,
                    default=workspace / "resources" / "models")
    ap.add_argument("--langs", default="en,zh,ja,ko")
    ap.add_argument("--backends", default="edge,sapi")
    ap.add_argument("--conditions", default="clean,pink@5,white@0")
    ap.add_argument("--engines", default="", help="comma filter; default = all non-denoiser")
    ap.add_argument("--limit", type=int, default=0, help="max aliases per language (0 = all)")
    ap.add_argument("--threshold-ipa", type=float, default=0.85)
    ap.add_argument("--out-dir", type=Path, default=Path(__file__).parent / "out")
    ap.add_argument("--fatjar", default="")
    args = ap.parse_args()

    if not args.catalog or not args.catalog.exists():
        ap.error("--catalog not found (pass the run-dir models.json explicitly)")
    ffmpeg = find_ffmpeg()
    fatjar = find_fatjar(args.fatjar or None)
    slf4j = find_slf4j()
    langs = [l.strip() for l in args.langs.split(",") if l.strip()]
    backends = [b.strip() for b in args.backends.split(",") if b.strip()]
    conds = parse_conditions(args.conditions)
    cond_tags = [condition_tag(k, s) for k, s in conds]
    engine_filter = {e.strip() for e in args.engines.split(",") if e.strip()}

    engines = load_catalog(args.catalog, args.models_root, engine_filter)
    if not engines:
        print("no engines resolved from catalog — nothing to do")
        return 1
    print(f"engines: {[e['id'] for e in engines]}")

    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    run_dir = args.out_dir / "engbench" / ts
    wavs = run_dir / "wavs"
    wavs.mkdir(parents=True, exist_ok=True)
    libs_dir = Path(__file__).parent / "libs"
    espeak = find_espeak()
    ESPEAK_LANG.setdefault("ko", ["-v", "ko"])  # espeak-ng has a Korean voice
    g2p = G2P(args.out_dir / "g2p_cache.json", espeak)

    # 1) collect (spell, lang, alias) and render audio per backend/condition
    base_items: list[dict] = []
    for file in sorted(Path(args.spells_dir).glob("*.json")):
        spell = json.loads(file.read_text(encoding="utf-8"))
        spell_id = spell.get("id", file.stem)
        for lang, alias in iter_aliases(spell):
            if lang not in langs:
                continue
            base_items.append({"spell": spell_id, "lang": lang, "alias": alias})
    per_lang: dict[str, int] = {}
    limited: list[dict] = []
    for it in base_items:
        n = per_lang.get(it["lang"], 0)
        if args.limit > 0 and n >= args.limit:
            continue
        per_lang[it["lang"]] = n + 1
        limited.append(it)
    print(f"aliases: {len(limited)} (per lang {per_lang})")

    items: list[dict] = []
    for it in limited:
        # IPA template: only the ipa engine consumes it (espeak G2P draft)
        it["ipa"] = g2p.draft(it["alias"], it["lang"]) or ""
        for backend in backends:
            stem = slug(f"{it['spell']}::{it['lang']}::{it['alias']}::{backend}")
            wav = wavs / f"{stem}.wav"
            if not wav.exists():
                if not synthesize(it["alias"], it["lang"], backend, wav, ffmpeg, libs_dir):
                    print(f"  tts failed: {it['alias']} ({it['lang']}/{backend})")
                    continue
            for kind, snr in conds:
                tag = condition_tag(kind, snr)
                entry = dict(it, backend=backend, condition=tag,
                             id=f"{it['spell']}::{it['lang']}::{it['alias']}::{backend}::{tag}",
                             wav=str(wav))
                if kind != "clean":
                    noisy = wavs / "noisy" / f"{stem}__{tag}.wav"
                    if not noisy.exists():
                        if not add_noise(wav, noisy, kind, float(snr)):
                            continue
                    entry["wav"] = str(noisy)
                items.append(entry)
    print(f"bench items: {len(items)}")
    g2p.save()

    if not items:
        print("nothing synthesized — aborting")
        return 1

    # 2) run EngineBench
    cls = Path(__file__).parent / "EngineBench.class"
    if not cls.exists():
        javac = shutil.which("javac") or str(Path(shutil.which("java") or "").parent / "javac.exe")
        comp = subprocess.run([javac, "-cp", str(fatjar),
                               str(Path(__file__).parent / "EngineBench.java")],
                              capture_output=True, text=True)
        if not cls.exists():
            print(f"EngineBench compile failed: {comp.stderr[-1500:]}")
            return 1
    bench_in = run_dir / "bench_input.json"
    bench_in.write_text(json.dumps({"ipaThreshold": args.threshold_ipa,
                                    "engines": engines, "items": items},
                                   ensure_ascii=False), encoding="utf-8")
    r = subprocess.run(["java", "-cp", f"{fatjar};{slf4j};{Path(__file__).parent}",
                        "EngineBench", str(bench_in)],
                       capture_output=True, text=True, encoding="utf-8", timeout=3600)
    (run_dir / "engbench.stderr.log").write_text(r.stderr or "", encoding="utf-8")
    if r.returncode != 0:
        print(f"EngineBench failed (exit {r.returncode}); stderr tail:\n{r.stderr[-800:]}")

    # parse one-JSON-per-line-ish stdout (tolerates interleaved log noise)
    dec = json.JSONDecoder()
    rows: list[dict] = []
    idx = 0
    while True:
        brace = r.stdout.find("{", idx)
        if brace < 0:
            break
        try:
            obj, end = dec.raw_decode(r.stdout, brace)
            if "engine" in obj and "verdict" in obj:
                rows.append(obj)
            idx = end
        except json.JSONDecodeError:
            idx = brace + 1
    (run_dir / "raw.jsonl").write_text(
        "\n".join(json.dumps(x, ensure_ascii=False) for x in rows), encoding="utf-8")

    # 3) report
    md, report = build_report(rows, cond_tags, backends)
    (run_dir / "report.md").write_text(md, encoding="utf-8")
    (run_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")

    total = len(rows)
    passed = sum(1 for x in rows if x.get("verdict") == "pass")
    engines_seen = len({x.get("engine") for x in rows})
    print(f"done: {passed}/{total} pass across {engines_seen} engine(s)")
    print(f"outputs: {run_dir}/report.md, report.json, raw.jsonl")
    return 0


if __name__ == "__main__":
    sys.exit(main())
