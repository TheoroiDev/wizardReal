# -*- coding: utf-8 -*-
"""Regenerates docs/spells/spell-triggers.md from the batch-0 spell JSONs
(0.4.0 language-keyed format). Run from the wizardreal repo root after any
spell content change (AGENTS §6 hard requirement)."""
import json
from pathlib import Path

SRC = Path("wizardreal/wizardreal/common/src/main/resources/data/wizardreal/voicecast/spells")
OUT = Path("docs/spells/spell-triggers.md")


def aliases_of(trigger):
    langs = trigger.get("languages")
    if isinstance(langs, dict) and langs:
        return {lang: list(aliases) for lang, aliases in langs.items()}
    return {"legacy": list(trigger.get("aliases", []))}


def main():
    spells = []
    for f in sorted(SRC.glob("*.json")):
        spells.append(json.loads(f.read_text(encoding="utf-8")))
    lines = [
        "# 全法术触发语一览 / Spell Trigger Reference",
        "",
        "> 由 data/wizardreal/voicecast/spells/*.json 提取（0.4.0 语言 keyed 格式，",
        "> 生成器 tools/gen_spell_triggers.py——改 JSON 后重跑）。识别按别名/IPA 匹配，",
        "> 念任意别名均可；仪式法术由 **L1（首行）** 进入（D9 首行即门），念完咒名（末行）",
        "> 或逐行念全施放；禁咒 skip_allowed=false。ko 变体允许滞后（legacy/其他语言兜底）。",
        "",
    ]
    for s in spells:
        sid = s["id"]
        lines.append(f"## {sid}")
        lines.append("")
        trig = s.get("trigger", {})
        for lang, aliases in aliases_of(trig).items():
            if not aliases:
                continue
            lines.append(f"- 触发词[{lang}]: " + " · ".join(aliases))
        ipa = trig.get("ipa") or []
        if ipa:
            lines.append("- IPA: " + " · ".join(ipa))
        else:
            lines.append("- IPA: （待 ipafill 填充/批次退役窗口内）")
        chants = s.get("chants", {})
        if isinstance(chants, dict) and chants.get("languages"):
            for lang, chant in sorted(chants["languages"].items()):
                body_variants = chant.get("body", [])
                lines.append(f"- 咒文[{lang}]: {len(body_variants)} 变体 × {2 + sum(len(v) for v in body_variants[:1])} 句"
                             if body_variants else f"- 咒文[{lang}]: trigger+cast")
                for vi, variant in enumerate(body_variants, 1):
                    seq = [chant["trigger"]["aliases"][0]] + [ln["aliases"][0] for ln in variant] + \
                          [chant["cast"]["aliases"][0]]
                    lines.append(f"  - 变体 {vi}: " + " / ".join(seq))
        elif isinstance(chants, list) and chants:
            lines.append("- 咒文: legacy 数组格式")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(spells)} spells)")


if __name__ == "__main__":
    main()
