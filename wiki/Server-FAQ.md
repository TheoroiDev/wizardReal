# [English](Server-FAQ) | [中文](Server-FAQ-zh)

# Server FAQ

> [← Admin Home](Home.md) · Previous: [Datapack Spells](Datapack-Spells.md)

## Players report "not allowed to use voice"

Check in order:

1. `config/voicecast/voicecast.toml` → is `[server] enabled` set to `false`;
2. Is `[players] whitelist` non-empty and missing that player's UUID (empty = everyone);
3. "engine not allowed" is a different thing — that's the `[engines] allowed` whitelist.

## Model download fails / server has no internet

- Add a JVM proxy: `-Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>`; the downloader also detects the `HTTPS_PROXY` / `HTTP_PROXY` environment variables;
- Change mirrors / self-host: edit `config/voicecast/models.json` and point `urls` at your own HTTP endpoints (multi-mirror probing and fallback apply);
- Fully offline: `[server] autoDownload = false`, then place models in `config/voicecast/models/<modelId>/` (extracted Vosk needs `am/ conf/ graph/`);
- Downloads are sha256/minBytes verified; failures automatically retry the next mirror.

## Players say "engine failed to load"

Search the log for `Server voice engine failed to start: <engine>`. Common causes: missing model (above), low disk space, incomplete q4 download (IPA needs a ≥150 MB `model_q4.onnx`). After fixing, players just re-run `/voicecast engine <name>` to rebuild their session.

## Can I run voicecast without wizardreal?

Yes. voicecast is a standalone voice library (without wizardreal there are no spell triggers, but the engine SPI remains available for other mods). The other way around wizardreal **requires** voicecast (hard dependency).

## Do I need to open extra ports?

No. All voice traffic rides the vanilla Minecraft connection (small client→server packet stream).

## Does it conflict with Simple Voice Chat / Plasmo Voice?

No. The microphone opens only for "staff held + right-click held" and is released immediately after; server-side recognition is invisible to voice mods. Players' normal voice chat keeps working while they chant.

There is a **first-class integration** with Simple Voice Chat (M7b): coexistence is automatic — VoiceCast and SVC share the microphone with no setup required. `[compat] svcCoexistence` in `voicecast.toml` is a **client-local setting** (each player's own config; the server neither reads nor syncs it). The old `defer` mode was removed in voicecast 0.3.2; existing configs fall back to `share`. See `docs/ref/compatibility.md`.

## How do I disable voice but keep the spells?

`[server] enabled = false`. No models load; wizardreal scroll-casting and datapack spells keep working.

## Where is data stored, can it be lost?

- `wizardreal_player_magic.nbt` in the world folder: per-player mana, known spells, cooldowns;
- **Auto-saved every 5 minutes** and written on clean server stop — a crash loses at most the last 5 minutes (schedule your own snapshots/backups);
- Models and configs live in `config/voicecast/`, are world-independent and survive world changes.

## Anything to watch when upgrading?

- The config schema is versioned; missing keys get defaults and the file is rewritten; legacy `server.properties`/`client.properties` are imported once and deleted;
- The model directory is reusable across versions — no re-download needed;
- Datapack spell schema changes show up as strict-validation errors in the log; fix the JSON and `/reload`.

## Where are errors logged?

- Server: `logs/latest.log` (search for `VoiceCast`, `voice engine`, `Denied`, `ERROR`);
- Crashes: `crash-reports/`;
- Client recognition issues: reproduce with `-Dvoicecast.verbose=true` and attach the log.
