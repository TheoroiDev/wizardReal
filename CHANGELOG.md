# Changelog — Be a Real Wizard (wizardreal)

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## Unreleased

### Features

- Catalog v3 (with wizardpedia's two-page compendium): entries now carry `entity` (mob entries get a live entity preview), school tags (right-rail filter), base + per-stage effect summaries (`wizardreal.effect.<type>` lang keys, all 22 built-in effect types, en+zh — future spells are covered automatically), nested per-language chant variants, and the chant-stage ladder (`chant_stages`: gate lines/mastery + mana/cooldown overrides), plus mastery %/mana/cost/difficulty scalars (wizardreal#27)

### Changes

- breaking: `wizardreal:spell_catalog` S2C bumps to formatVersion 3; `spell_catalog.json` export bumps to format 3 with `effects` and `chant_stages`
### Features

- Chant-stage ladder (magic_eco 03): spells can define `chant_stages` upgrade tiers — chanting deeper into a ritual swaps the spell to that tier's own effect list (and optional mana/cooldown overrides), gated by BOTH completed lines and the caster's mastery percent; casting deeper than your mastery resolves down to the highest unlocked tier, so using lower tiers trains the next one
- Spell ecosystem expansion (magic_eco 04): the shipped roster grows to 75 spell families (10 schools x 7+, plus the forbidden-chant ranks rift/domain/vincula/blade joining explosion as full 6-line rituals) with exploration/movement, combat, mining and farming coverage; every family gained 5-6 line per-language chants with two re-roll variants, and entry lines are mutually distinct (L1 conflict check clean)
- 13 new datapack effect primitives: `hex` (enemy status cone/sphere), `bind` (movement lock), `pull` (vortex/magnet), `blink` (safe-march teleport), `surface` (expiring ground cover: fire/ice/thorns), `barrier` (wall/ring/cage structures with restore-on-expiry), `summon` (temporary, owner-attributed allies), `excavate` (tunnel/area mining), `harvest` (crop harvest/replant/grow), `smelt` (touch-smelting), `visual` (choreographed particle shapes - ring/helix/pillar/burst/trail/cone/cross/orbit with colored dust - played back over time), `weather` and `light` (glow marking + night vision)
- Catalog v2 (voice overhaul SS8): the spell catalog now carries the language dimension — trigger words are grouped per language bucket and chant lines per language (legacy lines stay neutral lang keys); adds `difficulty`, `learning` (mastery %) and `skip_allowed` per spell, pushed to clients and wizardpedia (wizardreal#26)

### Changes

- Casts now resolve their chant stage before validation: mana cost and cooldown come from the resolved tier (staff modifiers still apply) and the executed effects are the stage's list
- breaking: `spell_catalog.json` export bumps to format 2 — `trigger.aliases`/`chants` are grouped per language (`""` = neutral legacy bucket), plus the new `difficulty`/`learning`/`skip_allowed` fields
- Wizardpedia entries render per language page: trigger words and chant lines are delivered per language so the compendium can show language sub-pages

### Features

- Learning system rework (voice overhaul D4/D-D2): every spell's mastery starts at 10% and grows only on successful casts (voice +2%, tome study up to the 75% cap, scrolls later); power scales with mastery (0.5x at 10% -> 1.0x at 100%, global 2.5x cap), `requires_learning` spells are blocked below 10% mastery, and "known" is derived from mastery (the knownSpells/forgottenSpells sets are gone - old saves migrate: forgotten spells pin to 0%, everything else starts at the baseline)
- `/wr (un)learn [player] <spell> [amount]`: bare number = +/- mastery points, `N%` = set absolute mastery (default +/-10%); `/wr known` and the spell catalog derive from mastery

### Changes

- breaking: spell matrix batch-0 rewrite (07 x3): the legacy 15-spell set is replaced by the language-keyed batch-0 matrix (10 projectile spells ignis/unda/fulmen/saxum/ventus/spina/sagitta/ossum/telum/falsum, 4 utility spells fulgur/semina/sanare/velum, and the forbidden chant explosion redone as a 6-line chant with per-language variants); removed ids (aegis/arcanum/gaia/ictus/mare/mortis/sanctus/tempest/umbra/vitae) return in later batches - learning counters reset with the new ids, ko variants lag one batch by design (validator WARNs)
- Wiki Server-FAQ updated for voicecast 0.3.2 (defer removal); docs/ref paths fixed
- Spell effects now scale with cast power (chant tiers x learning): damage/heal/range/blast linear, status durations sub-linear (sqrt) with amplifier capped at +2, projectile counts scale — power comes from the chant tier and mastery; cosmetic sound/particle effects stay unscaled
- Load-time spell validation report: near-identical first lines across ritual chains (L1 conflicts), forbidden-chant field group mistakes (difficulty >= 2 without requires_learning/skip_allowed=false), IPA coverage gaps, and a registered-count line in the reload log
- Chant HUD shows the spell's mastery percentage next to its name (synced via magic_sync)
- Chant entry reworked (voice overhaul D9): the first line (L1) of a ritual chant now enters the chant and locks the variant (the entry utterance counts as line 1); saying the trigger word / spell name while idle attempts a skip-cast instead, which requires chant mastery (denied until the learning system ships)
- Speaking the spell name mid-chant releases early at the completed-lines power tier (`chant_policy.skip_allowed`; forbidden chants refuse the jump)
- Damage mid-chant interrupts the chant (07 M1): 3+ line chants roll back one line, shorter chants fail; failed chants apply a stacking darkness penalty (config `[chant]`: timeout mode per-line (10s x lines, default) or fixed, darkness base/step/window)
- New `chant_policy` spell JSON block: power tiers per completed line, skip permission, interruptibility, optional pact; new language-keyed `chants` shape with per-language trigger/cast/body variants (legacy chant arrays keep parsing)

### Modding/API

- Machine-readable JSON Schema for the datapack spell format, validating spell JSONs offline: `schema/spell.schema.json` in the repo root (now covers `trigger.languages`, language-keyed `chants` and `chant_policy`)
- Spell gains a default `chantPolicy()`; `DataSpell` gained a `ChantPolicy` constructor overload (old arity keeps working)

### Infrastructure

- CI builds the voicecast dependency into mavenLocal as a bridge until a remote maven exists (voicecast#13)
- Dev-only testing mods moved out of gradle: release jars are pre-downloaded under workspace `resources/devmods/<loader>/` and wired from `manifest.txt` (fabric: hardlinked into the run mods folder; forge: file dependency so Loom remaps the SRG jar; Forge port of Carpet stays blocked, voicecast#38); voice-model fact source moved to `resources/models/`

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
