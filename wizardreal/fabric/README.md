# Be a Real Wizard — Fabric

Fabric loader wiring for [Be a Real Wizard](../../README.md).

## Contents (`com.theo.wizardreal.fabric`)

- `WizardRealFabric` — `ModInitializer`: common init (spells + network registration).
- `WizardRealFabricClient` — `ClientModInitializer`: calls `WizardRealClient.init()` (pushes spell vocabulary to VoiceCast, subscribes recognition finals).

## Run / build

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew :wizardreal-fabric:runClient     # dev run
.\gradlew :wizardreal-fabric:build         # produces build/libs/*.jar
```

Requires VoiceCast: `:voicecast-fabric` is wired into the dev run; in production ship both `wizardreal-fabric-*.jar` and `voicecast-fabric-*.jar`.

## Resources

- `fabric.mod.json` — main entrypoint; depends on Fabric Loader, Fabric API, and (in production) `voicecast`.
- `pack.mcmeta` — pack format 15.
