#!/usr/bin/env python3
"""ipafill - auto-draft IPA templates for spell aliases (voice_overhaul SS2 tool A).

Pipeline per alias without an IPA template (coverage gap):
  alias -> G2P draft (espeak-ng: en default / cmn for zh / ja; syllable cache
  in out/g2p_cache.json doubles as the zh/ja mapping table) -> normalization
  (ports voicecast IpaText: NFD strip + vowel-class relaxation, so templates
  never contain tokens the CTC vocab silently drops) -> edge-tts one-shot WAV
  (16 kHz mono via ffmpeg, mirrors lab/gen_corpus.py) -> IpaBench (production
  ZipaPhonemeRecognizer from the voicecast jar) -> score.

  score >= threshold  -> out/ipafill_candidates.json  (overrides.json-shaped
                         {spellId: {"apply_to": "trigger", "ipa_add": [...]}}
                         merge-ready; NEVER writes spell JSONs directly - the
                         write-back goes through the lab porting process)
  score <  threshold  -> out/ipafill_manual.json      (human review queue)

Usage:
  python ipafill.py --spells-dir <.../data/wizardreal/voicecast/spells> \
      --model-dir <.../zipa-ipa> [--langs en,zh,ja] \
      [--threshold 0.85] [--limit N] [--out-dir out] [--fatjar <jar>]

Deps: espeak-ng (PATH or Program Files), ffmpeg, python -m edge_tts, pypinyin
(zh syllable cache), JDK (java on PATH) for IpaBench.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path

# ---------------------------------------------------------------- mirrors of lab/gen_corpus.py


def find_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    local = Path(os.environ.get("LOCALAPPDATA", ""))
    cand = [Path(la) / "Microsoft" / "WinGet" / "Links" / "ffmpeg.exe" for la in [local]]
    cand += sorted(local.glob("Microsoft/WinGet/Packages/Gyan.FFmpeg*/**/bin/ffmpeg.exe"))
    for c in cand:
        if c.exists():
            return str(c)
    raise FileNotFoundError("ffmpeg not found (PATH / WinGet)")


def edge_tts(text: str, voice: str, dest: Path) -> bool:
    cmd = [sys.executable, "-m", "edge_tts", "--voice", voice, "--text", text,
           "--write-media", str(dest)]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        return r.returncode == 0 and dest.exists()
    except subprocess.TimeoutExpired:
        return False


def convert(ffmpeg: str, src: Path, dest: Path) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    cmd = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(src),
           "-ar", "16000", "-ac", "1", "-sample_fmt", "s16", str(dest)]
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode == 0 and dest.exists()


# ---------------------------------------------------------------- G2P

VOICES = {
    "en": "en-US-GuyNeural",
    "zh": "zh-CN-XiaoxiaoNeural",
    "ja": "ja-JP-NanamiNeural",
}

ESPEAK_LANG = {"en": [], "zh": ["-v", "cmn"], "ja": ["-v", "ja"]}

# Mirrors voicecast IpaText.stripDiacritics/normalizeTokens: what the CTC vocab
# actually contains after espeak's IPA output is cleaned up. Anything the model
# vocab lacks is dropped here INSTEAD of silently killing the whole template at
# ZipaShared.mapTemplate (Round-1 lesson).
_DROP_CHARS = set("ˈˌː.ʰʲ˥˦˧˨˩ᵝɜ0123456789")
_VOWEL_MAP = {"ɪ": "i", "ʊ": "u", "ɛ": "e", "ʌ": "ə"}  # NOTE: ɡ (U+0261) stays — the model vocab uses the script g, not ASCII g


def normalize_ipa(text: str) -> str:
    s = unicodedata.normalize("NFD", text)
    out = []
    for ch in s:
        if unicodedata.category(ch) == "Mn" or ch in _DROP_CHARS:
            continue
        out.append(_VOWEL_MAP.get(ch, ch))
    s = "".join(out)
    s = re.sub(r"\s+", " ", s).strip()
    s = s.lower()
    # Production rule (ZipaShared.mapTemplate + empirical): templates
    # must be SPACE-SEPARATED phonemes - contiguous multi-phone strings score 0
    # in the production CTC scorer (the '|' word marker between phones matters).
    # Affricate pairs are kept as one part (matches IpaText.tokenize).
    aff = ("t\u0283", "d\u0292", "ts", "dz")
    out, i = [], 0
    while i < len(s):
        if s[i].isspace():
            i += 1
            continue
        if s[i:i + 2] in aff:
            out.append(s[i:i + 2])
            i += 2
            continue
        out.append(s[i])
        i += 1
    return " ".join(out)


class G2P:
    """espeak-ng drafts with a persistent syllable/word cache (out/g2p_cache.json)
    — the cache IS the zh/ja mapping table the plan calls for, grown per run."""

    def __init__(self, cache_path: Path, espeak: str):
        self.cache_path = cache_path
        self.espeak = espeak
        self.cache = {}
        if cache_path.exists():
            self.cache = json.loads(cache_path.read_text(encoding="utf-8"))

    def draft(self, text: str, lang: str) -> str | None:
        key = f"{lang}:{text}"
        if key in self.cache:
            return self.cache[key]
        cmd = [self.espeak, "-q", "--ipa"] + ESPEAK_LANG.get(lang, []) + [text]
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        except Exception as e:
            print(f"  g2p failed for '{text}' ({lang}): {e}")
            return None
        if r.returncode != 0 or not r.stdout.strip():
            return None
        norm = normalize_ipa(r.stdout)
        if not norm:
            return None
        self.cache[key] = norm
        return norm

    def save(self) -> None:
        self.cache_path.parent.mkdir(parents=True, exist_ok=True)
        self.cache_path.write_text(json.dumps(self.cache, ensure_ascii=False, indent=1),
                                   encoding="utf-8")


def find_espeak() -> str:
    exe = shutil.which("espeak-ng")
    if exe:
        return exe
    cand = Path("C:/Program Files/eSpeak NG/espeak-ng.exe")
    if cand.exists():
        return str(cand)
    raise FileNotFoundError("espeak-ng not found (PATH / C:/Program Files/eSpeak NG)")


# ---------------------------------------------------------------- spell scanning


def iter_aliases(spell: dict) -> list[tuple[str, str]]:
    """(lang, alias) pairs for every active trigger alias of a spell."""
    trigger = spell.get("trigger", {})
    out: list[tuple[str, str]] = []
    languages = trigger.get("languages")
    if isinstance(languages, dict) and languages:
        for lang, aliases in languages.items():
            for a in aliases or []:
                out.append((lang, a))
        return out
    for a in trigger.get("aliases", []) or []:
        out.append(("legacy", a))
    return out


def spell_has_ipa(spell: dict) -> bool:
    trigger = spell.get("trigger", {})
    return bool(trigger.get("ipa"))


# ---------------------------------------------------------------- main


def find_fatjar(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    m2 = Path.home() / ".m2" / "repository" / "com" / "theo" / "voicecast" / "voicecast-common-1.20.1"
    versions = sorted([d for d in m2.glob("*") if d.is_dir()], reverse=True)
    for v in versions:
        for jar in v.glob("voicecast-common-1.20.1-*.jar"):
            if "sources" not in jar.name and "javadoc" not in jar.name:
                return jar
    raise FileNotFoundError("voicecast fat jar not found in mavenLocal (pass --fatjar)")


def find_slf4j() -> str:
    """slf4j-api (+ any binding) — the fat jar expects slf4j from the MC runtime."""
    roots = [Path.home() / ".m2" / "repository" / "org" / "slf4j",
             Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "org.slf4j"]
    api, binding = None, None
    for root in roots:
        if not root.exists():
            continue
        jars = sorted(root.glob("**/*.jar"))
        for jar in jars:
            name = jar.name.lower()
            if "sources" in name or "javadoc" in name:
                continue
            if name.startswith("slf4j-api-"):
                api = api or jar
            elif name.startswith("slf4j-nop-") or name.startswith("slf4j-simple-"):
                binding = binding or jar
    parts = [str(j) for j in (api, binding) if j]
    if not parts:
        raise FileNotFoundError("slf4j-api jar not found (mavenLocal / gradle cache)")
    return ";".join(parts)


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:32] or "x"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--spells-dir", required=True, type=Path)
    ap.add_argument("--model-dir", required=True, type=Path)
    ap.add_argument("--out-dir", type=Path, default=Path(__file__).parent / "out")
    ap.add_argument("--langs", default="en,zh,ja")
    ap.add_argument("--voices", default="", help="e.g. en=en-US-GuyNeural,zh=zh-CN-XiaoxiaoNeural")
    ap.add_argument("--threshold", type=float, default=0.85)
    ap.add_argument("--limit", type=int, default=0, help="max candidates (0 = all)")
    ap.add_argument("--fatjar", default="")
    args = ap.parse_args()

    ffmpeg = find_ffmpeg()
    espeak = find_espeak()
    fatjar = find_fatjar(args.fatjar or None)
    slf4j = find_slf4j()
    for spec in (args.voices or "").split(","):
        if "=" in spec:
            k, v = spec.split("=", 1)
            VOICES[k.strip()] = v.strip()
    langs = [l.strip() for l in args.langs.split(",") if l.strip()]

    g2p = G2P(args.out_dir / "g2p_cache.json", espeak)
    wavs = args.out_dir / "wavs"
    wavs.mkdir(parents=True, exist_ok=True)

    # 1) collect coverage gaps
    items: list[dict] = []
    for file in sorted(Path(args.spells_dir).glob("*.json")):
        spell = json.loads(file.read_text(encoding="utf-8"))
        spell_id = spell.get("id", file.stem)
        if spell_has_ipa(spell):
            continue  # covered (retirement-complete batch)
        for lang, alias in iter_aliases(spell):
            if lang not in langs or lang not in VOICES:
                continue
            items.append({"id": f"{spell_id}::{lang}::{alias}", "spell": spell_id,
                          "lang": lang, "alias": alias})
    if args.limit > 0:
        items = items[: args.limit]
    print(f"coverage gaps: {len(items)} candidate alias(es) in {args.spells_dir}")

    # 2) draft + synthesize
    for it in items:
        draft = g2p.draft(it["alias"], it["lang"])
        if not draft:
            it["error"] = "g2p failed"
            continue
        it["ipa"] = draft
        mp3 = wavs / (slug(it["id"]) + ".mp3")
        wav = wavs / (slug(it["id"]) + ".wav")
        if not wav.exists():
            if not edge_tts(it["alias"], VOICES[it["lang"]], mp3):
                it["error"] = "edge-tts failed"
                continue
            if not convert(ffmpeg, mp3, wav):
                it["error"] = "ffmpeg convert failed"
                continue
        it["wav"] = str(wav)

    ready = [it for it in items if it.get("wav") and it.get("ipa")]
    print(f"synthesized: {len(ready)}/{len(items)}")

    # 3) bench via the production recognizer
    scores: dict[str, float] = {}
    if ready:
        # compile IpaBench against the fat jar once (javac from JAVA_HOME/PATH)
        cls = Path(__file__).parent / "IpaBench.class"
        if not cls.exists():
            javac = shutil.which("javac") or str(Path(shutil.which("java") or "").parent / "javac.exe")
            comp = subprocess.run([javac, "-cp", str(fatjar), str(Path(__file__).parent / "IpaBench.java")],
                                  capture_output=True, text=True)
            if not cls.exists():
                print(f"IpaBench compile failed: {comp.stderr[-500:]}")
        bench_in = args.out_dir / "bench_input.json"
        bench_in.write_text(json.dumps({"modelDir": str(args.model_dir),
                                        "vocabulary": [], "items": ready},
                                       ensure_ascii=False), encoding="utf-8")
        r = subprocess.run(["java", "-cp", f"{fatjar};{slf4j};{Path(__file__).parent}",
                            "IpaBench", str(bench_in)],
                           capture_output=True, text=True, encoding="utf-8",
                           timeout=1800)
        # IpaBench stdout: pretty-printed multi-line JSON objects (one per item)
        # interleaved with possible log noise — parse with raw_decode.
        dec = json.JSONDecoder()
        text = r.stdout
        idx = 0
        while True:
            brace = text.find("{", idx)
            if brace < 0:
                break
            try:
                obj, end = dec.raw_decode(text, brace)
                scores[obj.get("id", "")] = float(obj.get("score", 0.0))
                idx = end
            except json.JSONDecodeError:
                idx = brace + 1
        if r.returncode != 0:
            print(f"IpaBench stderr (tail): {r.stderr[-800:]}")

    # 4) split candidates vs manual queue
    candidates: dict[str, dict] = {}
    manual: list[dict] = []
    for it in items:
        entry = {"spell": it["spell"], "lang": it["lang"], "alias": it["alias"],
                 "ipa": it.get("ipa", ""), "score": scores.get(it["id"], 0.0)}
        if it.get("error") or not it.get("ipa"):
            entry["reason"] = it.get("error", "no draft")
            manual.append(entry)
        elif entry["score"] >= args.threshold:
            bucket = candidates.setdefault(it["spell"], {"apply_to": "trigger", "ipa_add": []})
            if entry["ipa"] not in bucket["ipa_add"]:
                bucket["ipa_add"].append(entry["ipa"])
        else:
            entry["reason"] = f"score {entry['score']:.2f} < {args.threshold}"
            manual.append(entry)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "ipafill_candidates.json").write_text(
        json.dumps({"_doc": "Merge-ready overrides.json fragments (review, then fold into lab/overrides.json or port to spell JSONs via the lab process).",
                    **candidates}, ensure_ascii=False, indent=1), encoding="utf-8")
    (args.out_dir / "ipafill_manual.json").write_text(
        json.dumps(manual, ensure_ascii=False, indent=1), encoding="utf-8")
    g2p.save()

    print(f"done: {sum(len(v['ipa_add']) for v in candidates.values())} auto-fill template(s) "
          f"across {len(candidates)} spell(s); {len(manual)} queued for manual review")
    print(f"outputs: {args.out_dir}/ipafill_candidates.json, ipafill_manual.json, g2p_cache.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
