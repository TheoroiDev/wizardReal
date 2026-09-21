# Changelog — Be a Real Wizard (wizardreal)

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## Unreleased

### Features

- G2P pipeline completion: 769 generated IPA drafts fill the previously-empty ipa fields across all 75 spell JSONs (zh chant lines fully covered; ja limited to kana-only lines pending a morphological analyzer), plus an opt-in `[voice] g2pDrafts` runtime fill (default off — 2026-09 backtest shows no CTC gain until posterior scoring is calibrated) so servers can template custom words without touching datapacks
- In-game G2P (grapheme-to-phoneme) drafts for unknown/custom spell words: hanzi->pinyin (embedded MIT-licensed data table, 26.7k chars) with espeak-style initial/final composition, kana (sokuon/chōonpu/palatalization) and Hangul decomposition; hand-curated spell templates always win, drafts stay strict (any unconvertible segment produces no template) and only reach the recognizer through the ipa-backtest quality gate
- Voice false-trigger controls: `[voice] languages` config trims the recognizer vocabulary and matcher candidates to the enabled language buckets (legacy bucket-less pronunciations always pass), and a CTC-authoritative rejection level (`wizardreal.voice.rejectLevel`, default 0 = legacy) can suppress the snap-to-nearest fallback tiers for precision-focused servers
- Catalog v3 (with wizardpedia's two-page compendium): entries now carry `entity` (mob entries get a live entity preview), school tags (right-rail filter), base + per-stage effect summaries (`wizardreal.effect.<type>` lang keys, all 22 built-in effect types, en+zh — future spells are covered automatically), nested per-language chant variants, and the chant-stage ladder (`chant_stages`: gate lines/mastery + mana/cooldown overrides), plus mastery %/mana/cost/difficulty scalars (wizardreal#27)

### Changes

- Chinese voice incantations reworked to stop cross-fires (rename batch backtested on the lab bench: the six worst confusion pairs drop from 14-64% false-match to 0-3% on the new words): 幻弹 is retired in favor of 虚影弹 (falsum), 火墙→燎墙 (vestibulum), 唤林→唤森 (silva_voco), 疾风步→迅风步 (volatus), 轻风步→踏云步 (aurae_levitas), 圣光矢→圣光箭 (sagitta); the bare short alias 圣光 is retired (圣光束 stays) because it was contained inside other spells' chant lines and kept firing them. Chant lines were updated to the new wording and old aliases no longer resolve — naming rules for custom words live in the wiki ("Spell-Alias-Guidelines")
- breaking: `wizardreal:spell_catalog` S2C bumps to formatVersion 3; `spell_catalog.json` export bumps to format 3 with `effects` and `chant_stages`

### Bugfixes

- Quitting mid-chant no longer re-declares the OPEN cast mode from the quit handler: VoiceCast's own quit cleanup already clears the player's session and cast-mode declaration, and racing it could re-insert a stale declaration keyed by the departed player's UUID

### Modding/API

- Voice matchers ship the lab-calibrated S6 working point (wizardreal#29): the IPA phoneme matcher replaces the flat Levenshtein with a data-driven weighted edit distance — an 872-pair confusion cost table ships as a jar asset (`assets/wizardreal/phoneme_costs.tsv`; in-table costs are `clamp(raw x 2.0, 0.1, 1)` at match time, out-of-table substitutions stay a flat 1.0, insertions/deletions cost 0.6 in both directions, and the former free target-phoneme skip is gone — a swallowed phoneme now costs 0.6). Accented-speech recall improves markedly on the lab bench (positives 74.3% vs 60.7%, negative false-fires 8/300); a missing cost asset is a hard error — there is no equal-weight fallback
- CTC templateScores now reject ambiguous wins: when the top-two posterior candidates finish within 0.02 of each other, the recognizer (VoiceCast-side margin rule) zeroes the whole score set for that utterance, so borderline utterances fall through instead of firing a possibly-wrong spell; `FORWARD_MATCH_THRESHOLD` (0.10) and per-spell threshold overrides still apply unchanged on top
- Text alias matching breaks exact score ties by LONGEST alias first (most specific match), then by smallest spell id — a short alias contained inside another spell's longer alias no longer steals the win by candidate order
- New `PerModeThresholdProvider` mechanism interface (match package) exposing the three matcher-tier thresholds per mode, with a `fullVocabulary()` default that returns the shipped constants; per-mode recalibration wiring lands with the four-mode casting-time router (#30)
- Four-mode casting-time vocabulary routing (issue #30, D-15 user-adjudicated) with the VoiceCast dependency at 0.5.0:
  - free casting runs in the OPEN cast mode (declared for every player at join) — per the supervisor ruling on the P30 re-verification, OPEN keeps the full word list ∩ engine language buckets, identical candidates to the pre-#30 default: the trigger+release refinement was reverted because the SpellMatcher's Phonetics layer re-shuffles rather than removes false triggers under a narrowed OPEN set, so free-casting behavior is unchanged
  - ladder chants declare CHANT_CONFIRM for the current spell the moment the chant starts (mid-chant the session hears only that spell's rows) and fall back to OPEN on completion, early release, failure, cancel, timeout, quit and registry reload
  - per-mode forward thresholds ship in voicecast's `assets/voicecast/mode_thresholds.tsv` (first batch mirrors the shipped 0.10 constant — behavior-neutral); `ResourceModeThresholdProvider` (match package) reads them and stays unwired until recalibrated values deviate from the constants
  - practice surfaces (B crystal / E guide / mentor NPC) integrate later via `CastMode.PRACTICE_CONFIRM` (M4 — the interface ships now, no consumer yet)
- breaking: the voice interaction contract is re-keyed onto VoiceCast's semantic v2 result (engine-swap C1b, VoiceCast dependency stays 0.5.0 with the C1b api): recognition finals now carry the adjudicated `Decision` (EXACT / NEAR / AMBIGUOUS / REJECTED) + `spellId`/`pronId`/`score`/`alternatives`, and `ChantGate` is a pure semantic gate (Decision -> ENTER / INSTANT / SKIP / NONE). The consumer-side matcher chain (templateScores reading, phoneme/text matchers, the `FORWARD_MATCH_THRESHOLD` constant and the ctcPresent gating) is gone — decision authority lives in voicecast, WizardReal only maps the verdict onto gameplay; player-visible behavior is pinned equivalent by the shared equivalence vectors (`c1b_vectors.json`, voicecast + wizardreal tests; contract doc `docs/ref/voicecast-recognition-contract.md`)
- breaking: the matcher machinery moved to voicecast (`PhonemeMatcher`, `SpellMatcher`, `Phonetics` and the confusion-cost asset `assets/wizardreal/phoneme_costs.tsv` were removed from this repo); `com.theo.wizardreal.api.Pronunciation` is WizardReal's own content type now (the voicecast push type is `SessionVocabulary`); `PerModeThresholdProvider`/`ResourceModeThresholdProvider` produce `ThresholdHint` data instead of matcher constants
- reject levels are Decision-keyed: `EXACT` is accepted at every level, `NEAR` only at level 0, `AMBIGUOUS`/`REJECTED` never cast; the old ctcPresent-conditional surface suppression (level >= 1 kills the trigger matcher surface, level >= 2 the lenient first-line surface) travels as push-time threshold hints (data over the boundary) — the 首行即门 first-line priority and the chant progress gate are unchanged

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
