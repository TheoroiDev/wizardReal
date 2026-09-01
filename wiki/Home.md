# [English](Home.md) | [中文](zh/Home.md)

# Be a Real Wizard — Wiki

A Minecraft 1.20.1 multi-loader (Fabric / Forge) mod for **casting spells with your voice**. Hold a staff, hold right-click and chant — recognition runs server-side, powered by the VoiceCast engine.

## Quick start in 10 seconds

1. Install **both** mods: `voicecast` + `wizardreal`;
2. In-world, pick a recognition engine (try the ~40 MB **Vosk** first; choose **IPA** to chant in Chinese);
3. Craft or loot a **staff** and hold it;
4. **Hold right-click and speak**: say "ignis" → fireball away.

## Player docs

| Page | Contents |
|---|---|
| [Getting Started](en/player/Getting-Started.md) | Installation, engine choice, model download, staffs, PTT, HUD legend |
| [Spellcasting](en/player/Spellcasting.md) | Mana/cooldowns, staffs & schools, tomes & scrolls |
| [Spells](en/player/Spells.md) | All 15 built-in spells: triggers, numbers, effects |
| [Ritual Chants](en/player/Ritual-Chants.md) | Line-by-line incantations: variants, retries, timeout, cancel |
| [Troubleshooting & FAQ](en/player/Troubleshooting-FAQ.md) | Microphone, red waveform, no match, engine switching |

## Server admin docs

| Page | Contents |
|---|---|
| [Server Setup](en/admin/Server-Setup.md) | How it works (no extra ports), model download & proxies, memory sizing |
| [Configuration](en/admin/Configuration.md) | Full `voicecast.toml` keys + `models.json`/mirrors/self-hosting |
| [Access Control](en/admin/Access-Control.md) | Master switch, UUID whitelist, engine whitelist, permission hook |
| [Performance](en/admin/Performance.md) | Capacity table, memory/CPU profile, load-reducing knobs |
| [Datapack Spells](en/admin/Datapack-Spells.md) | Add/override spells with JSON, `/reload` |
| [Server FAQ](en/admin/Server-FAQ.md) | Permissions / downloads / upgrades / data persistence |

## Related

- [CREDITS](../../CREDITS.md) — third-party components & licenses (a NOTICE also ships inside every jar)
- Developer-facing: [AGENTS.md](../../AGENTS-wizardreal.md) (build conventions), [spell JSON schema](../../docs/spell_json.md), [multi-engine capacity analysis](../../docs/multi_engine.md), [wizardpedia plan](../../docs/wizardpedia.md)
