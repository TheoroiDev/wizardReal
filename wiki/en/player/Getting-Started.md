# [English](../../en/player/Getting-Started.md) | [中文](../../zh/player/Getting-Started.md)

# Getting Started

> [← Home](../Home.md) · Previous: none · Next: [Spellcasting](Spellcasting.md)

Be a Real Wizard (`wizardreal`) is a Minecraft 1.20.1 mod for **casting spells with your voice**: hold a staff, hold right-click and speak the incantation. Speech recognition runs **server-side** through the companion **VoiceCast** (`voicecast`) mod — the client only records audio, so players never download the big models.

## Installation

| Platform | Install | Requires |
|---|---|---|
| Fabric | `voicecast-fabric-*.jar` + `wizardreal-fabric-*.jar` | Fabric Loader, Fabric API, Architectury API |
| Forge | `voicecast-forge-*.jar` + `wizardreal-forge-*.jar` | Forge 47.x, Architectury API |

> Both mods are **required together**: wizardreal is the gameplay (staffs/spells/mana), voicecast is the speech engine. Install on client and server.

## First launch: pick a recognition engine

In-world (or via the Mod Menu / Mods-list **config button**, or the `/voicecast settings` command) open the engine picker:

| Engine | Size | Best for |
|---|---|---|
| **Word recognition (Vosk)** `vosk-text` | ~40 MB | Speaking English trigger words directly (ignis, fulmen…) |
| **Phoneme recognition (IPA)** `ipa-phonemes` | ~230 MB | Pronouncing Latin/English/Chinese/Japanese incantations (matched by phonemes, not exact words) |

Models download **once, on first use** (hf-mirror.com first, huggingface.co fallback) into `config/voicecast/models/`. The server loads and shares them for everyone — players' PCs never run inference.

Switch any time (applies immediately and is remembered):

```
/voicecast engine vosk    # word recognition
/voicecast engine ipa     # phoneme recognition
```

## Get your first staff

- **Apprentice Staff**: craft it (gold ingot / stick / diamond diagonal);
- **Fire / Lightning Staff**: upgrades of the apprentice staff (recipes in [Spellcasting](Spellcasting.md));
- Or explore: desert pyramids, jungle temples, woodland mansions, strongholds, mineshafts and dungeons have a small chance of loot — a finished staff (3%), **blank scrolls** (15%) and **spell tomes** (8%).

## Cast by voice (PTT)

1. **Hold any staff in your main hand** (the waveform HUD appears above the crosshair);
2. **Hold right-click** and speak — the green waveform follows your voice;
3. Say a spell's **trigger word** (e.g. "ignis"; ritual spells: say the trigger first, then each chant line — see [Ritual Chants](Ritual-Chants.md));
4. Release the key (or pause 0.7 s) — the utterance finalizes and the spell casts.

> Waveform legend: **red static** = model downloading/loading (a gold status line above shows progress); **green waveform** = ready, audio-reactive; **gray italic** text below the crosshair is the live recognition; the final result appears in **white quotes** and fades after 3.5 s; a **red** status line means an error (mic busy, model missing…).

## Next steps

- [Spellcasting](Spellcasting.md): mana, cooldowns, learning, scrolls;
- [Spells](Spells.md): all 15 built-in spells with numbers and trigger words;
- [Ritual Chants](Ritual-Chants.md): long incantations line by line;
- Problems? See [Troubleshooting & FAQ](Troubleshooting-FAQ.md).
