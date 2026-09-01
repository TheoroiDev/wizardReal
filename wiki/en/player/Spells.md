# [English](../../en/player/Spells.md) | [中文](../../zh/player/Spells.md)

# Spells

> [← Home](../Home.md) · Previous: [Spellcasting](Spellcasting.md) · Next: [Ritual Chants](Ritual-Chants.md)

There are **15 built-in spells**: 5 instant + 10 ritual. All are voice-triggered; values below are base values with the Apprentice Staff.

## Instant spells (speak the trigger, it casts)

| Spell | Trigger words | School | Mana | Cooldown | Effect |
|---|---|---|---|---|---|
| **Ignis** | ignis · fire · flame · fireball | fire | 5 | 2 s | small fireball projectile + sparks |
| **Fulmen** | fulmen · lightning · thunder · thunderbolt | lightning | 20 | 5 s | lightning strike within 48 blocks |
| **Vitae** | vitae · heal · vitality · cure | holy | 10 | 4 s | heal 8 ❤ |
| **Aegis** | aegis · shield · protect · protection · ward | arcane | 12 | 6 s | Resistance II + Fire Resistance for 10 s |
| **Ictus** | ictus · gust · wind · push · shove · strike | air | 3 | 1.5 s | cone knockback within 6 blocks |

## Ritual spells (trigger word enters a chant, cast on completion)

Each ritual spell has **3 chant variants (English/Chinese/Japanese) × 3 lines**; the first line you speak locks the variant used, and the final line is always the spell's trigger. See [Ritual Chants](Ritual-Chants.md).

| Spell | Trigger words | School | Mana | Cooldown | Learn req. | Effect |
|---|---|---|---|---|---|---|
| **Explosion** | explosion · explode · burst · 爆裂 | fire | 50 | 10 s | ✅ | lightning within 32 blocks + power-6 explosion (**breaks blocks & sets fire**) |
| **Arcanum** | arcanum · arcane · 奥术 | arcane | 35 | 12 s | — | Resistance II + Absorption III for 20 s |
| **Gaia** | gaia · 大地 | earth | 35 | 12 s | — | power-3 explosion within 20 blocks (no block damage) |
| **Mare** | mare · tide · 沧海 | water | 30 | 12 s | — | strong cone knockback within 5.5 blocks |
| **Mortis** | mortis · wither · 亡灵 | necromancy | 25 | 12 s | — | Wither II + Slowness II for 10 s |
| **Sanctus** | sanctus · holy · 圣光 | holy | 40 | 14 s | — | heal 10 ❤ + Regeneration II 10 s + Absorption II 20 s |
| **Semina** | semina · nature · 自然 | nature | 30 | 12 s | — | heal 6 ❤ + Regeneration II 15 s |
| **Tempest** | tempest · storm · 风暴 | lightning | 30 | 12 s | — | lightning strike within 48 blocks |
| **Umbra** | umbra · veil · 幻影 | illusion | 30 | 12 s | — | Invisibility + Speed II for 20 s |
| **Ventus** | ventus · gale · 狂风 | air | 25 | 10 s | — | strong cone knockback within 8 blocks |

> The Chinese/Japanese chant lines carry **pinyin/romaji aliases** — with the **IPA engine** you can simply speak Mandarin/Japanese; with **Vosk** the romanized aliases are matched too.

## Filler words are fine

The matcher does whole-word containment: "cast ignis now" scores the same as "ignis" (multi-word triggers like "explosion magic" score 0.95 when contained). Fuzzy matching fills the gaps; anything below the threshold never misfires.

## Custom spells

Server admins can add or override spells with a **datapack** — no code required. See [Datapack Spells](../admin/Datapack-Spells.md) in the admin docs.
