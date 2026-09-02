# Be a Real Wizard

Minecraft 1.20.1 mod: **speak incantations to cast spells**. Built with [Architectury](https://github.com/architectury) MultiLoader for **Fabric and Forge**.

📖 **Full documentation**: [wiki (English)](https://github.com/TheoroiDev/wizardReal/wiki) · [中文文档](https://github.com/TheoroiDev/wizardReal/wiki/Home-zh) — player guides and server-admin docs.

> This repository is one of the project's companion repos: the speech engine **[VoiceCast](https://github.com/TheoroiDev/voiceCast)** is a separate library mod; wizardreal consumes it via maven coordinates (`com.theo.voicecast:voicecast-*-1.20.1`).

## What it does

- **Staff-gated push-to-talk**: hold a staff in your main hand and hold **right-click** to speak — the mic opens only during that gesture and is released immediately after.
- **Server-authoritative casting**: the client only streams Opus audio; recognition, matching, mana/cooldown checks and effects all run on the server.
- **15 built-in spells** (5 instant + 10 ritual chants with English/Chinese/Japanese variants), each with multiple trigger aliases for text and IPA matching.
- **Mana + cooldowns** with a HUD bar; learnable via spell tomes, castable without learning via bound scrolls; staves modify mana cost by school affinity.
- **Data-driven spells**: `data/<namespace>/voicecast/spells/*.json` — add or override spells with a datapack (`/reload` applies), including datapack-defined rituals.
- **Localized**: English + 简体中文 (spell names, HUD, chant lines); recognition is display-language independent.

## Subprojects

- [`common/`](wizardreal/common/) — spell registry, cast validation, networking, mana, effects, chant state machine.
- [`fabric/`](wizardreal/fabric/) — Fabric entrypoint.
- [`forge/`](wizardreal/forge/) — Forge entrypoint.

## Build & dev

Prerequisite: the **VoiceCast** artifacts must be available — either `gradlew publishToMavenLocal` in the voicecast repo, or a configured CI maven repository.

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"   # Gradle runs on JDK 21
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew build                              # build everything (incl. unit tests)
.\gradlew :wizardreal-fabric:runClient       # launch on Fabric
.\gradlew :wizardreal-forge:runClient        # launch on Forge
```

## License

MIT — see the [LICENSE](LICENSE); a NOTICE also ships inside the jars (`META-INF/legal/NOTICE`). Third-party attribution for the paired VoiceCast mod lives in the voicecast repository.
