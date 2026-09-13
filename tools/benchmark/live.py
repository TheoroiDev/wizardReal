#!/usr/bin/env python3
"""live.py — launcher for LiveBench.java (interactive real-time backtest).

Compiles LiveBench against the voicecast fat jar (+ icu4j for the phonetic
matcher) and runs it. Default mode REPLAYS existing takes from the audio
store (debug harness, no microphone); --mic switches to real push-to-talk.

Usage:
  python live.py                          # replay, all langs, default engine
  python live.py --langs en,zh --auto 20  # 20 automated replay rounds
  python live.py --mic --langs zh         # real microphone PTT
  python live.py --engines sensevoice --denoise

All unknown args are forwarded to LiveBench (see its header for the full
list: --spells-dir --catalog --models-root --audio-dir --g2p-cache --seed
--n --game-dir).
"""
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from ipafill import find_fatjar, find_slf4j  # noqa: E402
from engbench import find_icu4j  # noqa: E402

HERE = Path(__file__).parent


def main() -> int:
    src = HERE / "LiveBench.java"
    cls = HERE / "LiveBench.class"
    fat = find_fatjar(None)
    slf4j = find_slf4j()
    icu = find_icu4j()
    if not fat:
        print("voicecast fat jar not found (build voicecast / publishToMavenLocal)")
        return 1

    def compile_needed() -> bool:
        if not cls.exists():
            return True
        # recompile when LiveBench.java or the fat jar is newer
        src_t = src.stat().st_mtime
        jar_t = Path(fat).stat().st_mtime
        return src_t > cls.stat().st_mtime or jar_t > cls.stat().st_mtime

    if compile_needed():
        javac = shutil.which("javac") or str(Path(shutil.which("java") or "").parent / "javac.exe")
        cp = ";".join(str(p) for p in (fat, icu) if p)
        print("compiling LiveBench ...")
        r = subprocess.run([javac, "-encoding", "UTF-8", "-cp", cp, str(src)],
                           capture_output=True, text=True)
        if not cls.exists():
            print(r.stderr[-3000:])
            return 1

    cp = ";".join(str(p) for p in (fat, icu, slf4j, str(HERE)) if p)
    args = list(sys.argv[1:])
    # defaults mirroring engbench.py: repo-relative run-dir catalog + models
    repo = HERE.parents[1]      # tools/benchmark -> tools -> <repo>
    workspace = HERE.parents[2]  # -> workspace root (D:\...\Minecraft)
    defaults = {
        "--catalog": repo / "wizardreal/fabric/run/config/voicecast/models.json",
        "--models-root": workspace / "resources" / "models",
        "--spells-dir": repo / "wizardreal/common/src/main/resources/data/wizardreal/voicecast/spells",
    }
    for flag, dflt in defaults.items():
        if flag not in args and dflt.exists():
            args += [flag, str(dflt)]
    cmd = ["java", "-Dfile.encoding=UTF-8", "-cp", cp, "LiveBench"] + args
    try:
        r = subprocess.run(cmd)
        return r.returncode
    except KeyboardInterrupt:
        print("interrupted")
        return 130


if __name__ == "__main__":
    sys.exit(main())
