# [English](Spell-Alias-Guidelines) | [中文](Spell-Alias-Guidelines-zh)

# Spell Alias Guidelines

> [← Home](Home.md) · See also: [Spells](Spells.md) · [Ritual Chants](Ritual-Chants.md) · [Datapack Spells](Datapack-Spells.md)

Why some spell trigger words changed in the **2026-09 rename batch**, the rules every spell name follows, and how to say spells so they land every time. This is the player-facing summary; the full design spec (machine-executable rules, evidence links) lives in the repo at `docs/spell_alias_guidelines.md`.

## Why spells got renamed

Spells are recognized by **sound**. When two spells sound almost alike — "幻弹" vs "魂灯" differ by a single vowel — the recognizer sometimes casts the wrong one. The 2026-09 batch renamed a handful of Chinese trigger words to pull the confusable pairs apart. **Old words are gone on purpose**; each renamed spell keeps or gains a clear replacement.

| Spell | Old word | New word |
|---|---|---|
| Falsum (phantom bolt) | ~~幻弹~~ | **虚影弹** (kept) |
| Vestibulum (fire wall) | ~~火墙~~ | **燎墙** |
| Silva Voco (awaken grove) | ~~唤林~~ | **唤森** |
| Volatus (wind step) | ~~疾风步~~ | **迅风步** |
| Aurae Levitas (light step) | ~~轻风步~~ | **踏云步** |
| Sagitta (holy arrow) | ~~圣光矢~~ | **圣光箭** |
| Lumen (holy beam) | ~~圣光~~ (bare short alias) | **圣光束** (kept) |

Renamed words appear in the changelog of the release that ships them.

## The naming rules (quick reference)

Every spell name — built-in or datapack-added — must pass five checks:

| # | Rule | In plain words |
|---|---|---|
| 1 | **Distinct sounds** | Any two spells must sound clearly different overall — not one vowel apart |
| 2 | **No tricky pairs** | Names may not differ *only* by n/l, front vs back nasal (-n/-ng), flat vs retroflex sibilants (s/sh, z/zh, c/ch), or f/h — these are the sounds voices mix up most |
| 3 | **No swallowing** | A short trigger word must not appear inside another spell's chant line (e.g. a spell aliased "圣光" inside "圣光矢初现" would misfire the short one) |
| 4 | **Series spacing** | Names sharing a first character ("唤X") must pull their endings far apart; names sharing a last character ("X墙") must pull their beginnings apart |
| 5 | **Length** | Chinese trigger words should be **2+ syllables** — single syllables are structurally hard to recognize |

Datapack authors: run the lint before shipping custom wordings:

```
python ipa/check_alias_distinctness.py --spells <your-spells-dir>
```

Exit code 0 = all clear. (Full tool docs in the repo, `ipa/check_alias_distinctness.py`.)

## FAQ

**My old trigger word stopped working.**
Intended. Renamed words are removed outright (no hidden legacy aliases) — use the new word from the table above or the in-game tome/chant HUD, which always shows the current wording.

**Can I still call a spell by another name?**
Most spells keep several aliases (e.g. Falsum answers to 虚影弹, and its English name). The chant HUD's last line and the spell catalog always list working names.

**How do I make recognition more reliable?**
Say the **full name**, prefer the 2-3 syllable Chinese or English aliases over improvising, and don't rely on tiny sound differences (-n vs -ng, s vs sh) — the naming rules already keep spells apart, and clear speech does the rest.

**I'm adding datapack spells and one triggers another.**
Your wording likely violates rule 1-4 against a built-in spell. Rename the offending alias and re-run the lint above.

*2026-09 rename batch — rules and evidence: `docs/spell_alias_guidelines.md` in the repo; player-facing changes are announced per release.*
