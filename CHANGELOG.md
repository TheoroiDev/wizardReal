# Changelog — Be a Real Wizard (wizardreal)

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## Unreleased

### Changes

- Wiki Server-FAQ updated for voicecast 0.3.2 (defer removal); docs/ref paths fixed

### Modding/API

- Machine-readable JSON Schema for the datapack spell format, validating spell JSONs offline: `schema/spell.schema.json` in the repo root

### Infrastructure

- CI builds the voicecast dependency into mavenLocal as a bridge until a remote maven exists (voicecast#13)
- Fabric dev runs bundle a Carpet testing mod (Forge port blocked; voicecast#38)

## 0.3.2 — 2026-09-02

### Features

- Blockbench 3D item models for staffs, spell tome and scroll, with school-tinted scroll/tome variants; staff wisp ambience; school item-model property
- Wizardpedia integration: the spell catalog self-exports and is pushed to wizardpedia with zero dependencies (`wizardpedia:catalog` provider side)
- Dev command set `/wr learn|unlearn|cast` plus `/spellinfo` (wizardreal#18)
- Loot-table tomes now bind a random learnable spell

### Bugfixes

- `PlayerMagicState` autosaves every 5 minutes (wizardreal#7)
- Creative tab, school tint and item names fall back to the synced spell catalog
- Tome/scroll covers render in the GUI; origin lang keys use dots (E2E verified)
- Dev `runServer`/`runClient` run directories split — concurrent runs on Windows
- Voice models auto-seeded into run directories as hard links

### Modding/API

- `spell_catalog.json` self-export: DTOs + builder + S2C (catalog wire v1 provider side)

### Infrastructure

- Docs-vs-code audit 001 remediation; wiki restructured to GitHub-wiki layout; GitHub Actions build workflow; issue templates; add-to-project workflow

## 0.3.1 — 2026-09-01

### Changes

- M7b compatibility hardening; depends on voicecast 0.3.1

### Modding/API

- wizardreal-local `ModDetection` dropped (voicecast provides `compat/ModDetection`)

### Infrastructure

- Simple Voice Chat added as dev mod for M7b coexistence testing

## 0.3.0 — 2026-09-01 (workspace split baseline)

### Features

- Gameplay baseline (M4–M7a): 15 datapack-driven spells across 10 schools, mana cost & cooldowns, staff/scroll/tome items, ritual chants with first-lock variants, spell-cast HUD
- Server-authoritative casting: recognition results are validated server-side (cooldown/mana/origin)

### Modding/API

- Datapack spell schema (docs/spells/spell_json.md)
