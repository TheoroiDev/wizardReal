# [English](Ritual-Chants) | [中文](Ritual-Chants-zh)

# Ritual Chants

> [← Home](Home.md) · Previous: [Spells](Spells.md) · Next: [Troubleshooting & FAQ](Troubleshooting-FAQ.md)

Every spell in the grimoire is a ritual (see [Spells](Spells.md)): the trigger word puts you into a **chant**, and you must speak each line of the incantation before the spell fires. A **chant HUD** appears to the right of the crosshair.

## How it flows

1. **Trigger**: say the spell's trigger word (e.g. "explosion") — chanting begins;
2. **Line by line**: speak the highlighted (`►`) line. On a match it turns green (`✓`) and the next line becomes current;
3. **Complete**: after the final line (usually the trigger word — a few Chinese variants use a translated name) the spell casts immediately.

## The rules

| Rule | Detail |
|---|---|
| **First line locks the variant** | Each spell has chant variants (English/Chinese/Japanese, several per language on larger rituals); whichever variant your first line matches is used to the end |
| **Wrong line = retry** | A wrong line never resets progress — **repeat the current line**; finished lines stay green |
| **1.2 s grace window** | For 1.2 s after each line completes, recognition leftovers don't flash errors |
| **Timeout: 10 s per line** | A chant with no valid utterance for 10 s × its line count cancels (server-configurable to a fixed cap) |
| **3 s completion / 1.2 s cancel lockout** | Short ignore-window after the chant ends (the last line usually equals the trigger; recognition emits several finals per utterance — this prevents an instant re-trigger) |
| **Gates are checked up front** | Learning requirement, staff, origin and cooldown are checked **before** the first line is accepted — the action bar tells you exactly why a chant cannot start; an empty mana bar doesn't void a completed chant, the cast degrades to the tier your mana affords |
| **Left-click cancels** | While holding right-click during a chant, click **left** to cancel (no mana spent) |

## HUD legend

```
Explosion  42%             ← spell title (gold) + your mastery of it
✓ let the earth split wide    ← done (green strikethrough)
► let the heavens bend low    ← current (aqua; its pinyin/romaji reading sits above)
   sealed in ruin's name      ← upcoming (dimmed)
   unmake all that stands
   Explosion!                 ← the final line is usually the trigger
✗ Repeat this line            ← red flash on a wrong line
```

And when you are **not** chanting, a rejected utterance still answers you (a few seconds, then it fades):

- *"✦ The incantation dissipates on the wind"* — the server heard speech but nothing cleared the threshold;
- *"✦ You catch an almost-familiar whisper — closest: …"* — you were near another spell's trigger; its name is shown.

## Tips

- **Display follows the game language**: a zh_CN client shows the Chinese lines. Recognition matches aliases/phonemes, independent of display language — you can chant the Chinese variant from an English client as long as it was locked by your first line;
- Chinese lines carry **pinyin** and Japanese lines **romaji** aliases, so both the utterance and IPA engines understand them;
- The **current chant line shows its reading** (pinyin/romaji) above it while chanting — read it aloud to stay on track ([Configuration](Configuration.md) to turn it off or widen it);
- While chanting, **no instant spells fire**: every utterance feeds the current ritual only;
- Mana and cooldown settle **on completion** — cancelling costs nothing.

> [← Home](Home.md) · Previous: [Spells](Spells.md) · Next: [Troubleshooting & FAQ](Troubleshooting-FAQ.md)