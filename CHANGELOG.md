# Changelog — Be a Real Wizard (wizardreal)

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## Unreleased

### Features

- Chinese chant readings shipped as in-game sounds (wizardReal#45, zh line): 132 CosyVoice3-synthesized readings across 69 spells are bundled under `wizardreal.reading.<spell>.zh` sound events — the corpus passed the production recognition chain at parity with the TTS baseline (zh cast 89% vs 93% baseline, zipa chant_idx0 93% vs 89%), so the reference audio IS a verified correct pronunciation.
- Japanese chant readings shipped as in-game sounds (wizardReal#45, ja line): 106 CosyVoice3 readings across 75 spells bundled under `wizardreal.reading.<spell>.ja` — regenerated with native-Japanese prompt voices after the tuning round (lab backtest cast rate 26% → 67% with the native prompts, closing most of the gap to the zh 77% baseline). en line awaits the prompt-audio sourcing decision. Playback wiring (compendium reading-audio button) lands with the wizardpedia play-slot slice — the events are registered and subtitle-ready today
- School particle vocabulary (wizardReal#44): ten palette-locked spark particles (one per school, `spark_<school>` in spell effect JSON) — procedurally painted glow orbs whose colors come straight from the school palette, so what you cast matches what the HUD and icons call the school
- School palette as a single source of truth (wizardReal#44): the ten school colors (accent / glow / dark, extracted from the spell dust colors every player sees) now live in one place — the chant HUD title takes its school's color, icon recoloring derives its hues from the same file, and a generator validates every spell effect's particle color against it (currently 23 dust colors, 0 off-palette). A follow-up batch adds the animated textures (mcmeta frame animation)
- First Lesson onboarding (wizardReal#49): every new player receives the fixed "First Lesson" tome on first join — one use teaches the three entry spells (ignis / celere / velum, the shortest chants), replacing the random tome loot that never guaranteed a beginner's book. A five-step advancement tree teaches the rules as you go: craft the apprentice staff → First Whisper (first voice cast) → The Full Ritual (a 3+ line chant) → Forbidden Words (one of the five forbidden spells) → Thousand Times Refined (one spell at 100% mastery, rewards +20 max mana — the first max-mana acquisition source, capped at 300)
- Practice loop (`/wrpractice <spell>` — wizardReal#43): rehearse any spell with zero mana, zero cooldown and no effect. The chant HUD highlights the current line **word by word** as you speak, grading uses the strict band set, and each completed rehearsal settles +1% mastery (half a real cast, capped per day — real casts keep the full +2%). Failures in practice never blind and never stack. Ops can also enter via `/wr practice <spell>`
- Chant score bands & resonance (wizardReal#43, consuming the voiceCast#48 W3 graded line scores): every completed line is scored into perfect (≥ 0.95) / excellent (≥ 0.85) / pass (≥ 0.70, the success line). A **perfect** chant *resonates* — +10% power for that one cast. The bands never change success/failure and never add mastery (mastery follows success, score follows performance); the cut points ship as the `ScoreBands` constants; `PerModeThresholdProvider#scoreBands` is the addon recalibration seam (per-mode wiring lands with the four-mode router)
- Rejected incantations now answer you (wizardReal#41): an utterance the semantic gate rules out while holding a staff is no longer swallowed silently — a rejected reading surfaces as "the incantation dissipates on the wind", and a near-miss shows what it almost was ("an almost-familiar whisper — closest: …" with the candidate spell's name) for a few seconds at the chant HUD anchor
- Chant annotation layer (chant reading annotations): the server derives a `readings` map per chant line once at catalog build time — zh lines get toned citation pinyin (textbook mark placement, tone digits from the embedded table; no sandhi in v1), ja lines get Hepburn romaji (new pure-JVM KanaRomaji table: sokuon doubling with a word-final apostrophe, macron long vowels, mora-spaced output, foreign morae ティ/ファ/ウィ and ん=n). Readings ride the catalog as a fixed key set (`pinyin`/`romaji`/`ipa`); derivation is fail-closed (any unconvertible letter drops the key — no invented readings) while punctuation/whitespace are dropped separators so the reading stays same-source with the hand-curated IPA templates; hand-curated IPA always wins over the G2P draft. A lab lint (`check_readings.py`) cross-checks derived vs hand-curated IPA and doubles as the Stage-A absorption list (first full run: 687/689 hand-ipa chant lines converge exactly)
- Chant HUD readings (D4, on by default): while chanting, the current line shows a small gray reading row above it, fed by the catalog's derived readings; language policy matches the book page (`[chantReadings] languagePolicy`: auto = non-display languages only / off / selected bucket set / all, plus `languages` for the selected set). New `[chantReadings] pinyinStyle` picks the pinyin display style — tone marks (`zhēn`, default) or digits (`zhen1`, always renderable) — applied when the catalog is derived, so a change takes effect at the next catalog rebuild (login / datapack reload)
- G2P pipeline completion: 769 generated IPA drafts fill the previously-empty ipa fields across all 75 spell JSONs (zh chant lines fully covered; ja limited to kana-only lines pending a morphological analyzer), plus the `[voice] g2pDrafts` runtime fill (now **on by default**, see Changes) so servers can template custom words without touching datapacks
- In-game G2P (grapheme-to-phoneme) drafts for unknown/custom spell words: hanzi->pinyin (embedded MIT-licensed data table, 26.7k chars) with espeak-style initial/final composition, kana (sokuon/chōonpu/palatalization) and Hangul decomposition; hand-curated spell templates always win, drafts stay strict (any unconvertible segment produces no template) and only reach the recognizer through the ipa-backtest quality gate
- Voice false-trigger controls: `[voice] languages` config trims the recognizer vocabulary and matcher candidates to the enabled language buckets (legacy bucket-less pronunciations always pass), and a CTC-authoritative rejection level (`wizardreal.voice.rejectLevel`, default 0 = legacy) can suppress the snap-to-nearest fallback tiers for precision-focused servers
- Catalog v3 (with wizardpedia's two-page compendium): entries now carry `entity` (mob entries get a live entity preview), school tags (right-rail filter), base + per-stage effect summaries (`wizardreal.effect.<type>` lang keys, all 22 built-in effect types, en+zh — future spells are covered automatically), nested per-language chant variants, and the chant-stage ladder (`chant_stages`: gate lines/mastery + mana/cooldown overrides), plus mastery %/mana/cost/difficulty scalars (wizardreal#27)

### Changes

- 失败庇护 (voiceCast#52): after 3 consecutive failed chants of the same spell, your in-chant line matching relaxes — an utterance whose verdict named the line but landed AMBIGUOUS (margin-suppressed near-hit) completes it at/above a relaxed floor (success line − 5 points). Idle triggering never relaxes, a successful chant resets the streak, and practice is unaffected (zero stakes already)
- Chant progression now runs the production adjudicator-backed line matcher (voiceCast#48 W2, wired as part of #43): line matching weighs the recognizer's graded verdict per line and falls back to the lenient boolean for out-of-vocabulary lines — previously chants silently ran the pre-#48 boolean matcher (every matched line graded a flat 1.0/0.0), which would have made the score bands vacuous
- Failed chants no longer blind you by default: `[chant] failBlindness` now defaults to **off** (was on) — the stacking darkness punished misrecognition (accents, background noise) as hard as wrong recitation, so a failed chant now just fizzles (wizardReal#41). Servers that want the old ritual stakes set `failBlindness = true` in the config
- A chant can no longer be wasted on a locked gate: the learning requirement, staff-in-hand, staff origin and cooldown are checked BEFORE the first line is accepted (the action bar tells you exactly why), instead of only after the whole recital; and an empty mana bar no longer voids a completed chant — the cast degrades to the highest stage your current mana affords (空转咏唱, wizardReal#41)
- breaking: `[voice] g2pDrafts` now defaults to **on** (was off): vocabulary entries without hand-curated IPA get production G2P draft templates at push time (voiceCast#47 — the 0.5.0 engine swap left en/ja chant lines without any zipa CTC template, and the runtime fill restores template coverage for every language the G2P chain supports: zh/ja/ko; en has no Tier-2 yet and stays fail-closed). The 0.6.0 CTC posterior calibration (R3/R5 port) removed the old "no CTC gain until scoring is calibrated" caveat that kept the fill off. Drafts are strict per alias and never override curated templates — to restore the old behavior set `g2pDrafts = false` in the config
- Chinese voice incantations reworked to stop cross-fires (rename batch backtested on the lab bench: the six worst confusion pairs drop from 14-64% false-match to 0-3% on the new words): 幻弹 is retired in favor of 虚影弹 (falsum), 火墙→燎墙 (vestibulum), 唤林→唤森 (silva_voco), 疾风步→迅风步 (volatus), 轻风步→踏云步 (aurae_levitas), 圣光矢→圣光箭 (sagitta); the bare short alias 圣光 is retired (圣光束 stays) because it was contained inside other spells' chant lines and kept firing them. Chant lines were updated to the new wording and old aliases no longer resolve — naming rules for custom words live in the wiki ("Spell-Alias-Guidelines")
- breaking: `wizardreal:spell_catalog` S2C bumps to formatVersion 3; `spell_catalog.json` export bumps to format 3 with `effects` and `chant_stages`

### Bugfixes

- G2P draft lookups no longer hand out another language's reading on multi-alias spell words: the internal Tier-0 dictionary bound the FIRST curated IPA template to EVERY alias of a surface, so e.g. the English alias "radix" drafted as the zh 缠根 reading and ja aliases drafted with zh templates (found by a lab readings audit). Only single-alias surfaces register now; every other alias falls to the per-script draft chain, which also unblocked ja kana/kanji drafts that the wrong binding had masked (2026-09-26)

- CJK voice chants no longer stall silently: the chant engine's noise gate stripped transcripts with an ASCII-only regex, so zh/ja text-lane chant lines (qwen3) were swallowed whole — the chant never advanced, never errored, and always ended in the timeout (found by the chant-sequence bench: zh/ja line progression was exactly 0%). The gate now counts all Unicode letters/digits (\p{L}\p{N}); en behavior unchanged

- Quitting mid-chant no longer re-declares the OPEN cast mode from the quit handler: VoiceCast's own quit cleanup already clears the player's session and cast-mode declaration, and racing it could re-insert a stale declaration keyed by the departed player's UUID

### Modding/API

- Two custom advancement triggers for datapack authors (wizardReal#49): `wizardreal:voice_cast` (optional `lines` minimum and `spells` filter — fired only by validated voice casts) and `wizardreal:mastery` (optional `spells` filter — fired when a spell's mastery crosses 100%)
- `PerModeThresholdProvider` gains `failSafeBonus(String mode)` (default 0.05) — the per-mode relaxation for the #52 fail-safe; the new `FailSafeLineMatcher` + `FailStreaks` (`match` package) carry the per-player relaxation as an overridable matcher binding
- `PerModeThresholdProvider` gains `scoreBands(String mode)` and the new `ScoreBands` record (`match` package): the perfect/excellent/pass cut points for graded chant scores as DATA with the `0.95/0.85/0.70` defaults — addons can recalibrate per mode; `ScoreBands.STRICT` (0.98/0.92/0.85) is the practice grading set
- breaking: CatalogPayload v4 — chant lines are structured `CatalogLine(text, readings)` records instead of plain strings; the `wizardreal:spell_catalog` S2C channel bumps to formatVersion 4 (per line: text ≤160 plus a readings map, keys ≤8 / values ≤128 UTF-16 units — oversized values are truncated, never rejected); the `spell_catalog.json` export bumps to format 4 with each chant line carrying `key`/`text`/`readings`
- Voice matchers ship the lab-calibrated S6 working point (wizardreal#29): the IPA phoneme matcher replaces the flat Levenshtein with a data-driven weighted edit distance — an 872-pair confusion cost table ships as a jar asset (`assets/wizardreal/phoneme_costs.tsv`; in-table costs are `clamp(raw x 2.0, 0.1, 1)` at match time, out-of-table substitutions stay a flat 1.0, insertions/deletions cost 0.6 in both directions, and the former free target-phoneme skip is gone — a swallowed phoneme now costs 0.6). Accented-speech recall improves markedly on the lab bench (positives 74.3% vs 60.7%, negative false-fires 8/300); a missing cost asset is a hard error — there is no equal-weight fallback
- CTC margin rejection carries over into the semantic v2 chain (VoiceCast-side rule): when the top-two template posteriors finish within 0.02 of each other, the whole CTC score set is zeroed for that utterance — the voicecast adjudicator sees the pre-margin gap and rules `AMBIGUOUS` when the suppressed top1 would have passed its forward threshold; per-spell thresholds travel as push-time `ThresholdHint` data (v2, below)
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

### Infrastructure

- The common subproject now publishes to Maven (`gradlew publishToMavenLocal` -> `com.theo.wizardreal:wizardreal-common-1.20.1`, mirroring the voicecast-common block): the plain unclassified jar + sources lets non-MC consumers (the lab/java-harness recognition harness, future tooling) resolve the pure-JVM subset (G2P, Pronunciation/SpellRegistry api) by coordinate. Build-infra only — zero logic change

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
- Damage mid-chant interrupts the chant (07 M1): 3+ line chants roll back one line, shorter chants fail; failed chants apply a stacking darkness penalty (config `[chant]`: timeout mode per-line (10s x lines, default) or fixed, darkness base/step/window) — **default off since 0.6.0** (see Unreleased)
- New `chant_policy` spell JSON block: power tiers per completed line, skip permission, interruptibility, optional pact; new language-keyed `chants` shape with per-language trigger/cast/body variants (legacy chant arrays keep parsing)

### Modding/API

- Machine-readable JSON Schema for the datapack spell format, validating spell JSONs offline: `schema/spell.schema.json` in the repo root (now covers `trigger.languages`, language-keyed `chants` and `chant_policy`)
- Spell gains a default `chantPolicy()`; `DataSpell` gained a `ChantPolicy` constructor overload (old arity keeps working)

### Infrastructure

- CI builds the voicecast dependency into mavenLocal as a bridge until a remote maven exists (voicecast#13)
- Dev-only testing mods moved out of gradle: release jars are pre-downloaded under workspace `resources/devmods/<loader>/` and wired from `manifest.txt` (fabric: hardlinked into the run mods folder; forge: file dependency so Loom remaps the SRG jar; Forge port of Carpet stays blocked, voicecast#38); voice-model fact source moved to `resources/models/`
- The backtest tools (`tools/benchmark`: `IpaBench`/`EngineBench`/`LiveBench` + `ipafill.py`/`engbench.py`) are ported to the 0.5.0 contract — the ipa arm drives `ZipaPhonemeRecognizer` through `SessionVocabulary` and reads `RecognitionDiagnostics.templateScores()`, the text arm is the Qwen3-ASR offline engine, compiled against the published voicecast jar; the removed streaming/SenseVoice engine branches are gone

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
