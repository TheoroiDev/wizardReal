# [English](Spellcasting) | [中文](Spellcasting-zh)

# Spellcasting

> [← Home](Home.md) · Previous: [Getting Started](Getting-Started.md) · Next: [Spells](Spells.md)

## What you know

A fresh character knows 5 basic spells: **Ignis**, **Fulmen**, **Vitae**, **Aegis**, **Ictus**. Everything else must be learned from a **Spell Tome**. The block-breaking ritual **Explosion** additionally requires learning before it can be chanted.

## Mana & cooldowns

- **Max mana 200**, regenerating at **0.1 per tick (2 per second)**;
- Casting consumes mana and starts that spell's individual cooldown;
- **Creative mode** skips mana (but not cooldowns); spectators can never cast;
- The **mana bar** renders right above the XP bar (dark/light blue); active cooldowns appear as small icons above it, labeled with remaining seconds.

> Mana data lives in `wizardreal_player_magic.nbt` in the world folder and is **auto-saved every 5 minutes** and written **on clean server stop** — a crash loses at most the last 5 minutes of progress.

## Staffs & schools

Every spell belongs to one or more **schools** (fire, lightning, water, earth, air, holy, necromancy, arcane, nature, illusion). Each staff favors some of them:

| Staff | Favored schools | Mana cost | Cooldown |
|---|---|---|---|
| Apprentice Staff | all | ×1.0 | ×1.0 |
| Fire Staff | fire, arcane | ×0.9 | ×1.0 |
| Lightning Staff | lightning, air | ×0.85 | ×0.9 |
| [DEV] Sdevv Staff | — | bypasses all checks (development only, no recipe) | |

- Casting a spell the staff does **not** favor is allowed but costs **+50% mana**;
- All built-in spells share the origin `wizardreal:wizardry`, so every non-dev staff can channel them.

### Staff recipes

- **Apprentice Staff**: gold ingot / stick / diamond in a diagonal shape;
- **Fire Staff**: blaze powder over the apprentice staff over a redstone block;
- **Lightning Staff**: prismarine crystals over the apprentice staff over a lapis block.

## Learning: tomes & scrolls

| Item | Purpose |
|---|---|
| **Spell Tome** | Right-click to **learn** its spell (tome is consumed); blank/corrupt tomes are refused |
| **Blank Scroll** | Crafting material (paper + feather + ink sac → 2) |
| **Bound Scroll** (Scroll of X) | Right-click to **cast** the bound spell: **no staff, no learning required**, but full mana/cooldown apply and the scroll is consumed |

Scroll-binding recipes currently exist for three spells (bound scrolls for the others come from the creative tab only):

| Scroll | Recipe |
|---|---|
| Scroll of Ignis | blank scroll + blaze powder + lapis lazuli |
| Scroll of Fulmen | blank scroll + prismarine shard + lapis lazuli |
| Scroll of Vitae | blank scroll + glistering melon slice + lapis lazuli |

> Scrolls are a great way to lend a spell to someone who hasn't learned it.

## Didn't match?

- The HUD shows `Heard: "…" — no spell matched` when nothing fits;
- Speak clearly; every alias of a spell works (see [Spells](Spells.md));
- The `vosk-text` engine only understands English pronunciation — for Chinese/Japanese incantations switch to the CJK native engines (`/voicecast engine vosk-cn` / `vosk-jp`, Korean `vosk-kr`); the IPA engine (`/voicecast engine ipa`) matches by phoneme and is more forgiving of accents — an alternative, not the only way to chant in Chinese;
- Chant-based spells behave differently — see [Ritual Chants](Ritual-Chants.md).
