# [English](Spells) | [中文](Spells-zh)

# Spells

> [← Home](Home.md) · Previous: [Spellcasting](Spellcasting.md) · Next: [Ritual Chants](Ritual-Chants.md)

Be a Real Wizard ships a growing grimoire of voice-cast spells — every one of them a **ritual**: its trigger word puts you into a chant, and the spell fires when the chant completes. The roster grows with content updates, so this page no longer duplicates counts or tables that would drift; the authoritative list lives in the **in-game compendium**.

## Where the full data lives

- **In-game compendium** (wizardpedia book): every spell with its trigger words, schools, mana, cooldown, learning requirement, chant stages and effects — in your client language, always in sync with the loaded datapack;
- **`/wr spells` / `/wr spellinfo`** — quick lookups from chat;
- **`spell_catalog.json`** in your game directory — the same data exported as a file (see [Spell Catalog](Spell-Catalog)).

## What every spell entry tells you

| Field | Meaning |
|---|---|
| **Trigger** | the word that starts the chant — per-language aliases (en/zh/ja); matching is phonetic, not verbatim |
| **School** | fire, water, arcane, … — staff origin and school penalties apply ([Spellcasting](Spellcasting.md)) |
| **Mana · Cooldown** | paid when the chant completes; staff modifiers apply |
| **Learning** | some spells must be studied before they will cast (mastery t ≥ 10%) |
| **Chant stages** | optional power ladder: complete more lines → stronger tier (mana/cooldown may scale with the tier) |

## Casting, in short

1. Hold a staff and say the **trigger** — the chant HUD appears;
2. recite the highlighted (`►`) line — it turns green (`✓`) when matched;
3. the spell casts on the final line; mana and cooldown settle then.

Rules, timeout and the HUD legend: [Ritual Chants](Ritual-Chants.md).

> The Chinese/Japanese chant lines carry **pinyin/romaji aliases** — with the **IPA engine** you can simply speak Mandarin/Japanese; with the **utterance engine** the romanized aliases are matched too. The chant HUD also shows the current line's reading while chanting (see [Getting Started](Getting-Started.md)).

## Filler words are fine

The matcher does whole-word containment: "cast ignis now" also matches (an exact match scores higher, but both clear the match threshold and the spell casts identically — multi-word triggers like "explosion magic" score 0.95 when contained). Fuzzy matching fills the gaps; anything below the threshold never misfires — and a rejected attempt now tells you so (see [Ritual Chants](Ritual-Chants.md)).

## Custom spells

Server admins can add or override spells with a **datapack** — no code required. See [Datapack Spells](Datapack-Spells) in the admin docs.

> [← Home](Home.md) · Previous: [Spellcasting](Spellcasting.md) · Next: [Ritual Chants](Ritual-Chants.md)
