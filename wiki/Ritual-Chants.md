# [English](Ritual-Chants) | [中文](Ritual-Chants-zh)

# Ritual Chants

> [← Home](Home.md) · Previous: [Spells](Spells.md) · Next: [Troubleshooting & FAQ](Troubleshooting-FAQ.md)

Ritual spells (Explosion, Tempest and 8 more — see [Spells](Spells.md)) don't cast on the trigger alone: the trigger word puts you into a **chant**, and you must speak each line of the incantation before the spell fires. A chant HUD appears to the right of the crosshair.

## How it flows

1. **Trigger**: say the spell's trigger word (e.g. "explosion") — chanting begins;
2. **Line by line**: speak the highlighted (`►`) line. On a match it turns green (`✓`) and the next line becomes current;
3. **Complete**: after the final line (usually the trigger word — a few Chinese variants use a translated name) the spell casts immediately.

## The rules

| Rule | Detail |
|---|---|
| **First line locks the variant** | Each spell has 3 chant variants (English/Chinese/Japanese); whichever variant your first line matches is used to the end |
| **Wrong line = retry** | A wrong line never resets progress — **repeat the current line**; finished lines stay green |
| **1.2 s grace window** | For 1.2 s after each line completes, recognition leftovers don't flash errors |
| **90 s timeout** | 90 s without a valid utterance cancels the chant |
| **3 s completion / 1.2 s cancel lockout** | Short ignore-window after the chant ends (the last line usually equals the trigger; recognition emits several finals per utterance — this prevents an instant re-trigger) |
| **Left-click cancels** | While holding right-click during a chant, click **left** to cancel (no mana spent) |

## HUD legend

```
Explosion               ← spell title (gold)
✓ Cloak the sky in black   ← done (green strikethrough)
► Wake the crimson thunder ← current (aqua)
   Explosion!              ← upcoming (dimmed)
✗ Repeat this line         ← red flash on a wrong line
```

## Tips

- **Display follows the game language**: a zh_CN client shows the Chinese lines. Recognition matches aliases/phonemes, independent of display language — you can chant the Chinese variant from an English client as long as it was locked by your first line;
- Chinese lines carry **pinyin** and Japanese lines **romaji** aliases, so both the Vosk and IPA engines understand them;
- While chanting, **no instant spells fire**: every utterance feeds the current ritual only;
- Mana and cooldown settle **on completion** — cancelling costs nothing.
