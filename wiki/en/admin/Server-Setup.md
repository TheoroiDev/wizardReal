# [English](../../en/admin/Server-Setup.md) | [中文](../../zh/admin/Server-Setup.md)

# Server Setup

> [← Admin Home](../Home.md) · Next: [Configuration](Configuration.md)

## How it works (read this first)

Recognition runs **entirely on the server**:

- The client only captures the mic → Opus-compresses (about **3 KB/s** per speaking player) → sends it over the **vanilla Minecraft connection**;
- The server runs Vosk / ONNX inference, matches the spell and casts;
- **No extra ports**, no extra firewall rules; players never download models or run inference.

## Install

Drop `voicecast-forge-*.jar` (or the fabric build) and `wizardreal-forge-*.jar` into the server's `mods/`. Both mods belong on the server; wizardreal hard-depends on voicecast.

## Model download

- The server **pre-warms the default engine** at start (`[server] defaultEngine`, Vosk English ~40 MB by default); other engines download on first selection and are **shared server-wide**;
- Downloads go over HTTPS with built-in mirror probing (**hf-mirror.com first**, huggingface.co fallback) and sha256 verification;
- **No internet / slow link**:
  - Proxy via JVM flags: `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808` (Java ignores the `HTTPS_PROXY` env var, but the downloader detects and applies it);
  - Or set `[server] autoDownload = false` and **place models manually** into `config/voicecast/models/<modelId>/` (extracted Vosk needs `am/ conf/ graph/` subdirectories);
- Model catalog and checksums: [Configuration → models.json](Configuration.md).

## Memory & hardware

| Scale | Recommendation |
|---|---|
| ≤20 online (3–5 speaking) | 4 cores / 8 GB |
| ~50 online (~10 speaking) | 16 cores / 16 GB |
| 100 online | 32 cores / 32 GB, plus idle-recognizer recycling (see [Performance](Performance.md)) |

Shared model layer: Vosk English ~150–250 MB; all four Vosk languages ~0.8–1 GB; IPA (q4 weights + ONNX runtime) ~400–600 MB. Each speaking player adds 30–80 MB of session memory. Details in [Performance](Performance.md).

## Access control (quick start)

- `[server] enabled = false` disables voice server-wide (no model warm-up);
- `[players] whitelist` with UUIDs restricts who may stream (empty = everyone);
- `[engines] allowed` limits which engines players may select, preventing big-model downloads;
- Details: [Access Control](Access-Control.md).

## Verify

1. The log shows `Server voice engine ready: vosk-text`;
2. A client in-world holds a staff, holds right-click and the waveform turns green;
3. `/voicecast engine` shows the player's current engine.

## Notes

- **Dedicated-server safe**: the voicecast server code never references client/LWJGL classes; all-platform natives for Vosk/ONNX are bundled (Linux x64/arm, macOS work);
- Mana/learning data lives in `wizardreal_player_magic.nbt` in the world folder, written **on normal server stop** (a crash can lose the last stretch of progress);
- Upgrading: the config schema migrates automatically (versioned `config/voicecast/voicecast.toml`); legacy `server.properties`/`client.properties` are imported once and deleted.
