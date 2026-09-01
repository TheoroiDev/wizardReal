# [English](Datapack-Spells-zh) | [中文](Datapack-Spells-zh)

# Datapack Spells

> [← Admin Home](Home.md) · Previous: [Performance(see the VoiceCast wiki: Performance) · Next: [Server FAQ](Server-FAQ.md)

Spells are **datapack-driven**: add JSON to a datapack to add or override spells — no Java code. The full schema and effect parameter tables ship with the mod's internal documentation; this page covers the admin workflow.
# Datapack Spells

> [← Admin Home](Home.md) · Previous: [Performance(see the VoiceCast wiki: Performance) · Next: [Server FAQ](Server-FAQ.md)

Spells are **datapack-driven**: add JSON to a datapack to add or override spells — no Java code. The full schema and effect parameter tables live in the mod's internal spell-JSON schema documentation; this page is the minimal admin flow.

## Where to put files

```
<world or datapack>/data/<your-namespace>/voicecast/spells/<any-name>.json
```

Server-side datapacks (`world/datapacks/<name>/data/...`) or singleplayer `datapacks/`.

## Minimal example: an instant spell

```json
{
  "id": "myspells:petra",
  "name": "Petra",
  "mana_cost": 15,
  "cooldown_ticks": 100,
  "schools": ["earth"],
  "aliases": ["petra", "stone", "岩石"],
  "ipa": ["ˈpɛtɹə", "ʂan˥˩ tʂɤ"],
  "effects": [
    { "type": "sound", "sound": "minecraft:block.stone.break", "volume": 1.0, "pitch": 0.8 },
    { "type": "knockback", "range": 4.0, "angle_cos": 0.5, "power": 0.8 }
  ]
}
```

- `id`: namespaced id (**same id overrides built-in spells**, including builtin datapack entries);
- `aliases`: the **trigger words**; `ipa` are pronunciation templates for the IPA engine — for Chinese triggers also supply pinyin templates;
- Defaults: `mana_cost` 10, `cooldown_ticks` 40, `requires_learning` false, `origin` `wizardreal:wizardry`;
- `effects` run in order; types: `projectile` / `lightning` / `heal` / `status_effect` / `knockback` / `explosion` / `beam` / `sound` / `particles` (parameters in spell_json.md).

## Ritual spells (line-by-line chanting)

Add `chants` to make a spell ritual (each line has a display key and pronunciation templates):

```json
"chants": [
  [
    { "display_key": "myspells.chant.petra.zh.l1", "aliases": ["岩石低语"], "ipa": ["jaŋ ʂɤ ti ju˥"] },
    { "display_key": "myspells.chant.petra.zh.l2", "aliases": ["大地回应"], "ipa": ["ta ti xuɪ ɪŋ"] },
    { "display_key": "myspells.chant.petra.zh.l3", "aliases": ["petra", "岩石"], "ipa": ["ˈpɛtɹə"] }
  ]
]
```

- Multiple `chants` groups = variants; the **first spoken line locks** which variant is used;
- `display_key` needs a translation in your language files (`assets/<ns>/lang/zh_cn.json` etc.); the HUD renders it in each player's game language;
- Make the last line the trigger word (matches the built-in spell experience).

## Applying & validation

1. Drop the datapack in and run **`/reload`**;
2. The loader rebuilds the spell registry (built-ins first, same-id overrides), cancels stale in-progress chants, and pushes the new trigger words / chant lines into the recognizer vocabulary;
3. **Strict validation**: if a file references an unregistered effect or registry entry, that file is **skipped entirely** (no half-working spells) — the log states why;
4. New spells automatically appear in the creative tab (pre-bound tomes/scrolls) and follow the same learning/mana/cooldown rules as built-ins.
