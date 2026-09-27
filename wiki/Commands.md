# [English](Commands) | [中文](Commands-zh)

# Commands

All cheat/debug commands live under **`/wizardreal`** (alias **`/wr`**) and require **permission level 2** (ops / server console). Spell ids are namespaced, e.g. `wizardreal:ignis`; pressing Tab completes ids from the registry.

The practice loop (wizardReal#43) is a **player feature** and lives on its own, permission-free root:

| Command | Description |
|---|---|
| `/wrpractice <spell>` | Start a practice rehearsal: no mana, no cooldown, no effects — strict scoring, per-word HUD feedback, mastery +1% per completed rehearsal (capped per day) |
| `/wrpractice` / `/wrpractice stop` | Leave practice (no penalty) |

Ops also get the same executors under `/wr practice <spell>`.

| Command | Description |
|---|---|
| `/wr learn <target> <spell>` · `/wr learn <spell> [amount]` | Teach a spell. Spell-first form targets the executor; target-first form is required from console |
| `/wr unlearn <target> <spell>` · `/wr unlearn <spell> [amount]` | Forget a spell — works for the 5 default spells too (mastery resets to 0%; re-learning starts from scratch) |
| `/wr cast <spell> [confidence]` | Force-cast as yourself: no staff, no learning required, but mana (skipped in creative) and cooldown still apply. `confidence` (0..1, default 1.0) lets you test thresholds without voice input |
| `/wr spells [filter]` | List registered spells: name, id, mana, cooldown, requires-learning, known flag |
| `/wr spellinfo <spell>` | Full definition dump: schools, origin, mana/cooldown, learning flag, threshold, trigger aliases, chant lines, effects |
| `/wr known [target]` | List a player's known spells |
| `/wr mana get [target]` · `/wr mana set <amount> [target]` · `/wr mana bonus <amount> [target]` · `/wr mana reset [target]` | Inspect or manipulate mana (set/bonus/reset resync the HUD; `bonus` is the advancement-reward path granting permanent max mana up to the cap) |
| `/wr cooldown clear all [target]` · `/wr cooldown clear <spell> [target]` · `/wr cooldown set <spell> <seconds> [target]` | Cooldowns are never waived by cast paths, so clear/set them for repeated-cast testing |
| `/wr sync [target]` | Force-resend mana/cooldown sync + the spell catalog (debug HUD/catalog desync) |
| `/wr state save` | Write `wizardreal_player_magic.nbt` immediately (autosave otherwise runs every 5 min) |
| `/wr state dump [target]` | Raw state dump: mana, known spells, active cooldowns |

## Notes

- `learn`/`unlearn` publish the spell catalog to the target immediately (the HUD/compendium learned flags update without relog) and force a state save;
- From the **server console** every player-targeting command needs an explicit target, e.g. `/wr learn Dev wizardreal:explosion`;
- `cast` always targets the executing player — a spell needs a position and look vector, so it cannot run from the console.

> [← Home](Home.md)
