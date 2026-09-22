#!/usr/bin/env python3
"""engbench - full-preset-engine backtest for the wizardreal benchmark harness.

Drives every engine declared in the voicecast models.json v2 catalog
(type in offline|ipa — the offline family is the Qwen3-ASR engine; `denoiser` models are auxiliary and skipped)
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
      [--audio-dir audio] [--pick random|all] [--seed N]

Audio lives in a persistent store (--audio-dir, default
tools/benchmark/audio/ with clean/<backend>/ and noisy/<tag>/<backend>/
subdirs) OUTSIDE the per-run report dir: runs only synthesize MISSING
files (append) and reuse everything on disk. --pick random (default)
tests each alias x condition against one randomly chosen backend take
(--seed for reproducibility); --pick all enumerates all takes. Manual
takes dropped into the store join the random pool automatically.

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
        if etype not in ("offline", "ipa"):
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

def find_icu4j():
    """icu4j jar from the gradle cache (MC runtime lib; Any-Latin transliteration
    for the phonetic matcher). Excludes -sources/-javadoc artifacts."""
    import glob as _glob
    cands = [j for j in _glob.glob(str(Path.home() /
             ".gradle/caches/modules-2/files-2.1/com.ibm.icu/icu4j/*/*/icu4j-*.jar"))
             if "sources" not in j and "javadoc" not in j]
    return max(cands) if cands else ""



# ja kanji→kana readings: espeak-ng "-v ja" has NO kanji dictionary — kanji
# aliases render empty/broken IPA templates (score 0.0). Phrase-level readings
# for the matrix's ja aliases; longest-match substitution before espeak.
JA_READINGS = {
    "紅蓮の雷を目覚めさせよ": "ぐれんのらいをめざめさせよ",
    "慈悲なき炎よ流れよ": "じひなきほのおよながれよ",
    "灰は目覚め空は燃える": "はいはめざめそらはもえる",
    "破滅の名のもとに封ぜよ": "はめつのなのもとにふうぜよ",
    "闇をもって天を覆え": "やみをもっててんをおおえ",
    "大地よ割れ開け": "だいちよわれあけ",
    "高き壁よひざせ": "たかきかべよひざせ",
    "天よかしこめ": "てんよかしこめ",
    "厄の鐘が鳴る": "やくのかねがなる",
    "剣の領域": "けんのりょういき",
    "火の領域": "ひのりょういき",
    "降雨領域": "こううりょういき",
    "天罰領域": "てんばつりょういき",
    "豪雨領域": "ごううりょういき",
    "麻痺領域": "まひりょういき",
    "炎のブレス": "ほのおのブレス",
    "潮のブレス": "しおのブレス",
    "根のブレス": "ねのブレス",
    "混沌のブレス": "こんとんのブレス",
    "聖光のブレス": "せいこうのブレス",
    "落石のブレス": "らくせきのブレス",
    "霧のブレス": "きりのブレス",
    "吸収のブレス": "きゅうしゅうのブレス",
    "千の剣": "せんのけん",
    "天の鎖": "てんのくさり",
    "束縛蔓": "そくばくかずら",
    "鎧溶かし": "よろいとかし",
    "骸骨召喚": "がいこつしょうかん",
    "静止の域": "せいしのいき",
    "萎れの域": "しおれのいき",
    "剣雨領域": "けんうりょういき",
    "渦潮": "うずしお",
    "石の壁": "いしのかべ",
    "虚空の裂け目": "こくうのさけめ",
    "竜巻": "たつまき",
    "突風": "とっぷう",
    "疾風歩": "しっぷうほ",
    "地割れ": "じわれ",
    "変身": "へんしん",
    "恐怖": "きょうふ",
    "催眠": "さいみん",
    "裁き": "さばき",
    "連鎖雷": "れんさらい",
}

def apply_ja_readings(text: str) -> str:
    if not any(chr(0x4E00) <= ch <= chr(0x9FFF) for ch in text):
        return text
    for phrase in sorted(JA_READINGS, key=len, reverse=True):
        if phrase in text:
            text = text.replace(phrase, JA_READINGS[phrase])
    return text


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
    ap.add_argument("--audio-dir", type=Path,
                    default=Path(__file__).parent / "audio",
                    help="persistent reusable wav store (clean/ and noisy/ subdirs); "
                         "runs APPEND missing files and reuse existing ones — drop "
                         "extra takes in here and they join the candidate pool")
    ap.add_argument("--pick", choices=("random", "all"), default="random",
                    help="random = each alias x condition tests ONE randomly "
                         "chosen backend take (seeded); all = exhaustive "
                         "backend x condition matrix (legacy)")
    ap.add_argument("--seed", type=int, default=0, help="rng seed for --pick random")
    ap.add_argument("--denoise", action="store_true",
                    help="route every wav through the GTCRN denoiser before "
                         "recognition (mirrors production noiseSuppression=true)")
    args = ap.parse_args()

    if not args.catalog or not args.catalog.exists():
        ap.error("--catalog not found (pass the run-dir models.json explicitly)")
    ffmpeg = find_ffmpeg()
    fatjar = find_fatjar(args.fatjar or None)
    slf4j = find_slf4j()
    # icu4j (Any-Latin transliteration for the phonetic matcher) comes from the
    # Minecraft runtime, not the voicecast fat jar — locate it in the gradle cache
    icu = find_icu4j()
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
    run_dir.mkdir(parents=True, exist_ok=True)
    # Persistent reusable audio store — lives OUTSIDE the per-run report dir so
    # synthesized takes accumulate across runs (追加): only missing files are
    # synthesized, everything already on disk is reused as-is.
    audio_dir = args.audio_dir
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

    # Chant lines (long incantations, e.g. the forbidden explosion): each
    # line's alias variants are separate items — production streams them
    # sentence by sentence and matches per line (M1 interrupt points).
    chant_items: list[dict] = []
    for file in sorted(Path(args.spells_dir).glob("*.json")):
        spell = json.loads(file.read_text(encoding="utf-8"))
        spell_id = spell.get("id", file.stem)
        for lang, groups in spell.get("chants", {}).get("languages", {}).items():
            if lang not in langs:
                continue
            # trigger/cast: {"aliases": [...]}
            for role in ("trigger", "cast"):
                for alias in groups.get(role, {}).get("aliases", []):
                    if alias:
                        chant_items.append({"spell": spell_id, "lang": lang,
                                            "alias": alias, "kind": f"chant_{role}"})
            # body: [[{"aliases": [...]}, ...], ...] — variant list per line
            for li, line in enumerate(groups.get("body", []), start=1):
                for var in line:
                    for alias in var.get("aliases", []):
                        if alias:
                            chant_items.append({"spell": spell_id, "lang": lang,
                                                "alias": alias, "kind": f"chant_body{li}"})
    # drop chant items whose (spell, lang, alias) already exists as a trigger
    # alias — same spoken text, same wav, same test
    seen_alias = {(it["spell"], it["lang"], it["alias"]) for it in limited}
    chant_items = [it for it in chant_items
                   if (it["spell"], it["lang"], it["alias"]) not in seen_alias]
    limited = limited + chant_items
    print(f"aliases: {len(base_items)} trigger + {len(chant_items)} chant lines")

    import hashlib
    import random
    rng = random.Random(args.seed)
    items: list[dict] = []
    made = 0
    for it in limited:
        # IPA template: only the ipa engine consumes it (espeak G2P draft)
        g2p_text = apply_ja_readings(it["alias"]) if it["lang"] == "ja" else it["alias"]
        it["ipa"] = g2p.draft(g2p_text, it["lang"]) or ""
        stems: dict[str, str] = {}
        for backend in backends:
            stem = slug(f"{it['spell']}::{it['lang']}::{it['alias']}::{backend}")
            if not it["alias"].isascii():
                # slug() strips CJK, so every non-latin alias of one
                # (spell, lang) collapsed to the same stem and shared ONE wav;
                # a stable hash disambiguates them (ASCII wavs keep caching)
                stem += "-" + hashlib.md5(it["alias"].encode("utf-8")).hexdigest()[:8]
            clean = audio_dir / "clean" / backend / f"{stem}.wav"
            clean.parent.mkdir(parents=True, exist_ok=True)
            if not clean.exists():
                if not synthesize(it["alias"], it["lang"], backend, clean, ffmpeg, libs_dir):
                    print(f"  tts failed: {it['alias']} ({it['lang']}/{backend})")
                    continue
                made += 1
            stems[backend] = stem

            for kind, snr in conds:
                if kind == "clean":
                    continue
                tag = condition_tag(kind, snr)
                noisy = audio_dir / "noisy" / tag / backend / f"{stem}.wav"
                if not noisy.exists():
                    if not add_noise(clean, noisy, kind, float(snr)):
                        continue
                    made += 1

        if not stems:
            continue

        # rows: one entry per (alias, condition); the wav is resolved from the
        # store — --pick random selects a random available backend take
        # (seeded), --pick all enumerates every backend (legacy exhaustive).
        for kind, snr in conds:
            tag = condition_tag(kind, snr)
            available = [b for b, stem in stems.items()
                         if (audio_dir / ("clean" if kind == "clean"
                                          else f"noisy/{tag}") / b / f"{stems[b]}.wav").exists()]
            if not available:
                continue
            chosen = [rng.choice(available)] if args.pick == "random" else available
            for backend in chosen:
                stem = stems[backend]
                wav = (audio_dir / ("clean" if kind == "clean"
                                    else f"noisy/{tag}") / backend / f"{stem}.wav")
                items.append(dict(it, backend=backend, condition=tag,
                                  id=f"{it['spell']}::{it['lang']}::{it['alias']}::{backend}::{tag}",
                                  wav=str(wav)))
    print(f"audio store: {audio_dir} ({made} newly rendered)")
    print(f"bench items: {len(items)}")
    g2p.save()

    if not items:
        print("nothing synthesized — aborting")
        return 1

    # 2) run EngineBench
    cls = Path(__file__).parent / "EngineBench.class"
    if not cls.exists():
        javac = shutil.which("javac") or str(Path(shutil.which("java") or "").parent / "javac.exe")
        comp = subprocess.run([javac, "-cp", f"{fatjar};{icu}".rstrip(";"),
                               str(Path(__file__).parent / "EngineBench.java")],
                              capture_output=True, text=True)
        if not cls.exists():
            print(f"EngineBench compile failed: {comp.stderr[-1500:]}")
            return 1
    bench_in = run_dir / "bench_input.json"
    # gameDir: production-style config/voicecast root (models.json + model
    # store) so EngineBench can run the GTCRN denoiser in-loop (--denoise)
    game_dir = Path(__file__).resolve().parents[2] / "wizardreal/fabric/run"
    bench_in.write_text(json.dumps({"ipaThreshold": args.threshold_ipa,
                                    "denoise": bool(args.denoise),
                                    "gameDir": str(game_dir),
                                    "engines": engines, "items": items},
                                   ensure_ascii=False), encoding="utf-8")
    r = subprocess.run(["java", "-cp", f"{fatjar};{slf4j};{icu};{Path(__file__).parent}",
                        "EngineBench", str(bench_in)],
                       capture_output=True, text=True, encoding="utf-8", timeout=10800)  # denoise-in-loop + ja pin roughly triple the runtime
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
