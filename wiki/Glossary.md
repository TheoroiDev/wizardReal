# [English](Glossary) | [中文](Glossary-zh)

# Glossary — canonical terms

> Naming canon for contributors: datapack authors, wiki editors, and anyone adding content. When a new term is canonized, add it here in **both** languages. This page is the start of the glossary, not the whole of it.

## Realm names — two-tier rule

Realm names follow a **two-tier system**: the Chinese name is **canonical** (lore-authoritative); the English name is a **working name** chosen to avoid collisions with vanilla mobs/biomes and is allowed to diverge from a literal translation of the canonical name. Do not "fix" one language to match the other — each tier has its own job.

| Canonical (zh) | Working name (en) |
|---|---|
| 缄默圣域 | the Silent Reach |
| 龙嗣之境 | the Dragon Elegy |
| 深脉之底 | the Deepvein |
| 四季王庭 | the Four Seasons Court |
| 星陨裂隙 | the Starfall Rift |
| 沉没和声之城 | the Sunken Chorus |

## Spell naming conventions

- Voice aliases are **phonetic-first**: aliases must survive mishearing (see the wiki page *Spell-Alias-Guidelines*); a rename batch in 0.6.0 re-worded the six worst confusion pairs.
- Display names are **unique per language** and locked by a unit test (`SpellLangNameLockTest`): every spell has a namespaced `spell.<id>.name` key in both languages, no dot-style legacy keys, no duplicates.
- One IP-free namespace: spell/subtitle/audio names must not carry third-party proper nouns (see the banned list below).

## Banned terms

| Banned | Why | Use instead |
|---|---|---|
| Excalibur / 誓约胜利之剑 | third-party IP (Fate) | blade（千剑刃） |
| Dragon Slave / 龙破斩 | third-party IP (Slayers) | rift（虚空裂隙） |
| 幻弹 · 火墙 · 唤林 · 疾风步 · 轻风步 · 圣光矢 · 裸「圣光」 | retired aliases (0.6.0 rename batch — cross-fire sources) | 虚影弹 · 燎墙 · 唤森 · 迅风步 · 踏云步 · 圣光箭 · 圣光束 |
| "sequel" (as a noun for 「续篇」) | translationese — in English a *sequel* is a follow-up work, not a story's continuation | "the tale, continued" / "continuation" |
