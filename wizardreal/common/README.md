# Be a Real Wizard — common

Platform-independent gameplay code. Recognition, matching and cast validation are all **server-authoritative**; the client only drives the push-to-talk gesture, renders HUDs/particles and receives S2C packets. The Fabric/Forge subprojects register loader content (items, particles, sounds, creative tab, datapack reload listeners) and call `WizardReal.init()`.

## Contents (`com.theo.wizardreal.*`)

### Bootstrap & API
| Class | Purpose |
|-------|---------|
| `WizardReal` | Common bootstrap `init()`: registers the effect primitives, runs the (empty) Java spell hook, wires server voice handling, the chant network receiver, the catalog service, magic-state hooks, mana manager, loot/kill-drop modifiers, magic sync, and server-lifecycle hooks (datapack spell load + recognizer vocabulary push). |
| `api/Spell` | Spell interface: namespaced id, schools, mana cost, cooldown, `pronunciation()`, optional per-spell matcher `threshold()`, `origin()`, `requiresLearning()`, ritual `chants()`, `cast(context)`. |
| `spell/AbstractSpell` | Base class holding the immutable spell metadata; subclasses implement `apply(CastContext)`. |
| `api/SpellRegistry` | Register/iterate spells by id; `replace()` lets datapack definitions override same-id entries. |
| `api/CastContext` | Server-side cast info (caster `ServerPlayer`, eye position, look vector, power, per-cast blackboard). |
| `api/School` | Spell schools (FIRE, LIGHTNING, WATER, EARTH, AIR, HOLY, NECROMANCY, ARCANE, NATURE, ILLUSION). |
| `api/Chant` / `api/ChantLine` | Ritual incantation variants/lines (display lang key + recognizer pronunciation). |
| `api/event/SpellCastEvent` | Cancellable event fired before execution; carries a translatable reason key. |
| `api/event/SpellEvents` | Tiny event bus (`postCast`). |
| `api/catalog/CatalogPayload` | Spell-catalog snapshot pushed to clients / wizardpedia. |

### Spells (`spell/`) — datapack-driven
Every shipped spell is defined as datapack JSON under `data/<ns>/voicecast/spells/*.json` (the jar ships 75 files in `data/wizardreal/voicecast/spells/`), parsed by the `SpellDefinition` codec into a `DataSpell`: an ordered list of composable `SpellEffect`s plus trigger/chant pronunciation metadata. `server/SpellDataLoader.registerAll` rebuilds the registry on server start and on `/reload`; same-id datapack definitions override Java-registered spells. `Spells.register()` is kept as an empty Java extension hook for future built-ins that need bespoke logic.

`effect/` provides 22 composable effect primitives (`BuiltinEffects`): `projectile`, `lightning`, `heal`, `status_effect`, `knockback`, `explosion`, `beam`, `sound`, `particles`, `hex`, `bind`, `pull`, `blink`, `surface`, `barrier`, `summon`, `excavate`, `harvest`, `smelt`, `visual`, `weather`, `light`. `EffectRegistry` maps type ids to their `MapCodec`s and dispatches JSON on the `"type"` field; addons register their own types through the same path.

### Networking (`net/`) — three Architectury `NetworkManager` channels
- `ChantNetwork` (`wizardreal:chant`) — ritual chant: S2C `START` (spell id + every chant variant's display keys), `PROGRESS` (locked variant / line index / error flash), `END` (finished, cancelled or timed out); C2S `CANCEL` (player-initiated left-click cancel). All chant state lives on the server; the client only renders what it receives.
- `MagicSyncNetwork` (`wizardreal:magic_sync`) — S2C full magic state (mana, max mana, remaining cooldown ticks), applied to `net/MagicClientState` for the HUD.
- `SpellCatalogNetwork` (`wizardreal:spell_catalog`) — S2C full spell-catalog snapshot (keys only); the client caches it and exports `<game-dir>/wizardreal/spell_catalog.json`.

### Server (`server/`) — all authoritative logic
- `ServerVoiceCast` — subscribes to VoiceCast's `ServerRecognitionFinalEvent` (marshaled onto the server main thread) and builds/pushes the recognizer vocabulary (a `SessionVocabulary`: trigger words + every chant line, language-routed) at server start and on spell reload, declaring the cast-mode routing (OPEN for free casting, CHANT_CONFIRM for the spell being chanted). Each recognized utterance requires a staff in the main hand; VoiceCast adjudicates the utterance into a semantic `Decision` (EXACT/NEAR/AMBIGUOUS/REJECTED + winning spell id) and the `ChantGate` maps that verdict onto gameplay — an in-progress chant is fed the line, otherwise the decision either starts a chant or casts instantly.
- `SpellCastHandler` — the validated cast path shared by every entry point; see the chain below.
- `ChantManager` — per-player chant state; maps `ChantEngine` results onto network packets, lockouts (3 s after completion, 1.2 s after cancel) and the validated cast.
- `ChantEngine` — pure, MC-free chant state machine (variant lock, 1.2 s line grace window, 90 s timeout), unit-tested in `ChantEngineTest`.
- `PlayerMagicState` — persistent per-player mana / known spells / cooldowns, stored at the world root as `wizardreal_player_magic.nbt` (auto-saved on server stop and every 5 minutes; defaults: 200 max mana, 2/s recovery).
- `ManaManager` — `SERVER_POST` tick: mana recovery, cooldown pruning (every 5 s), periodic autosave (5 min).
- `MagicSyncHandler` — pushes magic state on login and every 2 s.
- `SpellDataLoader` — datapack spell loading: `collect()` (from the loader reload listeners) → `apply()` on the server thread; then cancels active chants, re-pushes the vocabulary and republishes the catalog.
- `SpellCatalogService` / `SpellCatalogBuilder` / `WizardpediaPublisher` — build + publish the catalog (player join, tome learning, spell reload) to the client and to wizardpedia.
- `LootTableModifier` — injects staves/scrolls/tomes into vanilla structure chest loot tables; `SpellKillDrops` — tome drops when specific mobs die to spell damage.

### Cast validation chain (`server/SpellCastHandler.castValidated`)
Server main thread only; the client is never trusted for effects. Steps may be skipped by flags for alternative cast paths (scrolls):

1. Learning check — skipped by `SKIP_LEARNING` (scrolls teach by doing) or a bypass-all staff (dev staff).
2. Staff check — skipped by `SKIP_STAFF` (scrolls cast with raw mana/cooldown, no origin/school modifiers).
3. Origin restriction — the held staff must allow the spell's origin.
4. School penalty — if the staff supports none of the spell's schools, +50% mana penalty (warned on the action bar, not blocked).
5. Mana check — waived in creative mode.
6. Cooldown check — always enforced (creative does not skip it).
7. Cancellable `SpellCastEvent` → execute: consume mana, `spell.cast(ctx)`, start cooldown, sync state, action-bar feedback.

A non-spectator player and a resolvable spell id are prerequisites before the chain.

### Client (`client/`) — gesture input, HUD & rendering only
- `StaffCastHandler` — client tick: polls the main hand (VoiceCast is enabled while a staff is held) and the GLFW mouse buttons — right-click = push-to-talk, left-click = send the C2S chant cancel.
- `ChantClient` / `ChantState` / `ChantHud` — chant S2C receiver, client-side mirror state and the incantation HUD beside the crosshair (done lines checked, current line highlighted, error flash on a wrong line, fade-out after the end).
- `MagicHud` — mana bar above the XP bar plus active cooldown icons (reads `net/MagicClientState`).
- `SpellCatalogState` — caches the latest catalog payload and writes `spell_catalog.json`.
- `SchoolTintModels` — the `wizardreal:school` item model property for school-tinted tome/scroll textures.
- `HeldItemAmbience` — cosmetic wisp particles drifting off a held staff.
- `WizardSparkParticle` — spark/rune/wisp particle factory (registered by the loader clients).

### Match calibration (`match/`) — threshold data pushed to VoiceCast
Recognition matching/decision logic lives in VoiceCast (`com.theo.voicecast.match`, semantic contract v2); wizardreal supplies only push-time calibration data: `PerModeThresholdProvider` / `ResourceModeThresholdProvider` turn per-spell `threshold()` overrides and the shipped mode rows (`assets/voicecast/mode_thresholds.tsv`) into `ThresholdHint` values carried on the `SessionVocabulary` push.

### Items (`item/`)
`StaffItem` (hard origin gate + school mana/cooldown modifiers; `bypassAll` on the dev staff), `ScrollItem` (consumable instant cast via `SKIP_STAFF` + `SKIP_LEARNING`), `SpellTomeItem` (learning), `WizardRealItems` (supplier registry populated by the loader modules: apprentice/fire/lightning staves + dev `staff_sdevv`, blank scroll, spell tome).

## Resources

- `assets/wizardreal/lang/en_us.json` (+ `zh_cn.json`) — spell names, chant lines and `wizardreal.cast.*` messages.
- `data/wizardreal/voicecast/spells/*.json` — the 75 built-in datapack spells.
- `data/wizardreal/recipes/` and `assets/wizardreal/{sounds,particles,textures,models}` — crafting recipes, sounds and visuals.

## Note

Package root is `com.theo.wizardreal` (confirmed as the final package name; the earlier planned `com.theo.wizard.real` was dropped).
