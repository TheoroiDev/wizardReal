# Be a Real Wizard — Forge (MC 1.20.1 Forge 47.x)

Forge loader wiring for [Be a Real Wizard](../../README.md).

## Contents (`com.theo.wizardreal.forge`)

- `WizardRealForge` — `@Mod` entry point; common init (spells + network registration).
- `WizardRealForgeClient` — `@EventBusSubscriber(Dist.CLIENT, bus=MOD)`; `FMLClientSetupEvent` → `enqueueWork(WizardRealClient::init)` (spell vocabulary + recognition subscription).

## Run / build

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew :wizardreal-forge:runClient     # dev run
.\gradlew :wizardreal-forge:build         # produces build/libs/*.jar
```

Requires VoiceCast: `:voicecast-forge` is wired into the dev run; in production install `voicecast-forge-*.jar` alongside `wizardreal-forge-*.jar`.

## Resources

- `META-INF/mods.toml` — Forge mod metadata (`modId = wizardreal`).
- `pack.mcmeta` — pack format 15.
