# [English](Configuration) | [中文](Configuration-zh)

# Configuration

**Chant readings** put a small pronunciation row — pinyin, romaji or IPA — above chant lines you don't speak, in the chant HUD and on spell tome / spellbook pages. Gameplay overview: [Getting Started](Getting-Started).

## Be a Real Wizard — `config/wizardreal/wizardreal.toml`

The `[chantReadings]` section:

| Key | Default | Meaning |
|---|---|---|
| `hud` | `true` | Show the reading row above the current chant line in the chant HUD |
| `languagePolicy` | `"auto"` | Which lines get a reading: `auto` = only lines **not** in your game language (a Chinese line is annotated on an English client, not on a Chinese one); `off` = nowhere; `selected` = only the language buckets in `languages`; `all` = every line that has a reading |
| `languages` | `""` | With `selected`: comma-separated language buckets, e.g. `"ja,zh"` |
| `pinyinStyle` | `"marks"` | Pinyin tone display: `marks` (zhēn) or `numbers` (zhen1) |

- The chant HUD always prefers **pinyin over romaji over IPA** — it has no per-method toggles;
- `hud` / `languagePolicy` / `languages` are **client-side display** choices and are read once at game start — restart the client after editing;
- `pinyinStyle` is **server-side**: readings are derived once when the spell catalog is built, with the server's style baked in. On a dedicated server every client sees the server's style — editing the client copy has no effect there. Changes apply at the next catalog rebuild (re-join, learn a spell from a tome, or `/reload` the spell datapack).

## Spellbook pages — `config/wizardpedia/client.json`

The wizardpedia book page renders the same readings with its own policy copy (`chantLanguagePolicy` / `chantReadLanguages`, same semantics as above) plus per-method toggles: `methodPinyin` / `methodRomaji` (default on) and `methodIpa` (default off). The file is re-read each time the spellbook opens. The two files are independent — tune each surface separately.

> [← Home](Home)
