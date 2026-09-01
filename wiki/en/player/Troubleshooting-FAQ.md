# [English](../../en/player/Troubleshooting-FAQ.md) | [中文](../../zh/player/Troubleshooting-FAQ.md)

# Troubleshooting & FAQ

> [← Home](../Home.md) · Previous: [Ritual Chants](Ritual-Chants.md)

## No waveform HUD

1. You must **hold a staff in your main hand** (the HUD only shows while a staff is held);
2. You must be **inside a world**;
3. Both mods installed: wizardreal (gameplay) + voicecast (speech).

## Waveform shows red static

Red = the model isn't ready. Read the **status line** above the waveform:

- **Gold** "preparing/downloading model": first download, wait a moment (Vosk ~40 MB / IPA ~230 MB);
- **Red** "engine failed / model missing": check `logs/latest.log`. If the server set `autoDownload = false`, an admin must place the model files manually;
- **Red** "Microphone unavailable": next section.

## "Microphone unavailable" / nothing gets recognized

1. Check the OS **microphone privacy permission** for Java/Minecraft;
2. Verify the system's default recording device (VoiceCast uses the default input device);
3. Exclusive-mode mic access by other apps (Discord, OBS) can conflict — disable exclusive mode (Windows: device properties → Advanced);
4. Coexisting with Simple Voice Chat / Plasmo Voice: the microphone **opens only while holding a staff and right-click**, and is released immediately after — normally no interference with voice mods.

## My spell doesn't match

- Slow down and pronounce clearly, especially the trigger ("ignis", not "ign");
- **Vosk only understands English pronunciation** — switch to `/voicecast engine ipa` to chant pinyin/Chinese;
- The IPA engine matches by phonemes and is forgiving to non-native accents (tolerates tense/lax vowel shifts, dropped syllable-final consonants);
- Each spell has several aliases (see [Spells](Spells.md)) — try another;
- The gray text under the crosshair is the live recognition — if it's far from any trigger word, first confirm the model finished loading (red status line gone).

## Switching engines

```
/voicecast engine vosk    # word recognition (English words)
/voicecast engine ipa     # phoneme recognition (pronunciation-based, supports zh/ja)
/voicecast settings       # picker UI
```

Applies immediately; remembered in `config/voicecast/voicecast.toml` under `[client] engine`. The server may restrict available engines ("engine not allowed" → ask the admin).

## Does it conflict with voice chat?

Recognition runs **server-side** and the mic is open only during staff + right-click — Simple Voice Chat keeps working normally. Note: your incantation is audible to other players through the voice mod (you *are* chanting out loud, after all).

## Advanced diagnostics (devs/admins)

- Start with `-Dvoicecast.verbose=true` (dev: `gradlew runClient -PvoicecastVerbose=true`) to log the recognition pipeline: `[Mic]`, `[Vosk]`, `[IPA DEBUG]`;
- Logs: `logs/latest.log`; crashes: `crash-reports/`;
- A client-side debug WAV recording (`VoiceCastConfig.saveDebugWav` source constant, off by default) proves whether capture works — separate from whether recognition works.

## Other questions

- **No mana bar**: mana/cooldowns sync on change — cast once after joining;
- **Spells cost no mana**: creative mode skips mana (not cooldowns);
- **Last chant line doesn't complete**: make sure you're reading the *highlighted* line; after completion there's a 3 s lockout before the next ritual can start;
- Anything else: open a GitHub issue with `logs/latest.log` attached.
