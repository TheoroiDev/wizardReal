# Be a Real Wizard — common

Platform-independent gameplay code. Spells, the cast pipeline, networking and client-side voice matching all live here; the Fabric/Forge subprojects only wire loader-specific entrypoints.

## Contents (`com.theo.wizardreal.*`)

### API
| Class | Purpose |
|-------|---------|
| `WizardReal` | Common bootstrap: registers spells + network channel (`init()`). |
| `api/Spell` | Spell interface: id, school, mana cost, cooldown, `pronunciation()`, `cast(context)`. |
| `spell/AbstractSpell` | Base class holding id/school/mana/cooldown/`Pronunciation`; subclasses implement `apply(CastContext)`. |
| `api/SpellRegistry` | Register/iterate spells by id (`Spells.register()` bootstraps the built-ins). |
| `api/CastContext` | Server-side cast info (caster `ServerPlayerEntity`, eye position, look vector, power). |
| `api/School` | Spell schools (FIRE, LIGHTNING, WATER, EARTH, AIR, HOLY, NECROMANCY, ARCANE, NATURE, ILLUSION). |
| `api/event/SpellCastEvent` | Cancellable event fired before execution; carries a translatable reason key. |
| `api/event/SpellEvents` | Tiny event bus (`onCast` / `postCast`). |
| `compat/ModDetection` | `isLoaded(id)` for soft compatibility (Simple Voice Chat, Curios/Trinkets, Patchouli...). |

### Spells (`spell/`)
`Spells` + `IgnisSpell` (fireball), `FulmenSpell` (lightning at look target), `VitaeSpell` (self heal), `AegisSpell` (resistance + fire resistance), `IctusSpell` (cone knockback). Each defines its `Pronunciation` (aliases + placeholder IPA) used as the VoiceCast grammar.

### Networking (`net/`)
- `WizardRealNetwork` — registers the C2S channel `wizardreal:cast_spell` via Architectury `NetworkManager`; `sendCast(spellId, confidence)` client helper.
- `C2SCastSpell(spellId, confidence)` — packet codec. Decoded on the net thread, then `ctx.queue(...)` onto the server main thread.

### Server (`server/SpellCastHandler`)
Validation chain (server main thread only): non-spectator → spell exists → per-player/per-spell cooldown (keyed by world time) → **creative-mode gate (M4 temporary)** → cancelable `SpellCastEvent` → `spell.cast(context)` → action-bar feedback. The client is never trusted for effects.

### Client (`client/`)
- `WizardRealClient` — client init: pushes all spell pronunciations to VoiceCast (`VoiceCastClient.setVocabulary`) and subscribes to `RecognitionFinalEvent` (marshaled to the client thread); sends cast packets with 250 ms same-spell dedupe.
- `SpellMatcher` — normalizes heard text and scores aliases: exact match 1.0, multi-word contains 0.95, whole-word contains 0.9, Levenshtein similarity otherwise; threshold 0.8.

## Resources

- `assets/wizardreal/lang/en_us.json` — spell names and `wizardreal.cast.*` / `wizardreal.heard.*` messages.

## Planned (M5/M6)

- Mana data attachment (Architectury) with client sync; enforce `manaCost`.
- Staff item + blank scroll replacing the creative-mode gate.
- Mana bar / cooldown HUD.
- M6: JSON data-driven spells (`data/<ns>/voicecast/spells/*.json`) and public addon API.

## Note

Package root is `com.theo.wizardreal` (confirmed as the final package name; the earlier planned `com.theo.wizard.real` was dropped).
