# Be a Real Wizard — Fabric

Fabric loader wiring for [Be a Real Wizard](../../README.md).

## Contents (`com.theo.wizardreal.fabric`)

- `WizardRealFabric` — `ModInitializer`: registers the datapack spell reload listener, items (4 staves / scroll / tome), particles, sounds and the creative tab, then calls `WizardReal.init()`.
- `WizardRealFabricClient` — `ClientModInitializer`: registers S2C receivers (chant, magic sync, spell catalog) plus HUD/particle/item-property rendering and the client tick (PTT gesture + chant cancel). All gameplay and recognition stay server-side; the vocabulary push and recognition subscription live in the server-side `WizardReal.init()`.

## Run / build

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew :wizardreal-fabric:runClient     # dev run
.\gradlew :wizardreal-fabric:build         # produces build/libs/*.jar
```

Requires VoiceCast: `:voicecast-fabric` is wired into the dev run; in production ship both `wizardreal-fabric-*.jar` and `voicecast-fabric-*.jar`.

## Resources

- `fabric.mod.json` — main entrypoint; depends on Fabric Loader, Fabric API, and `voicecast`.
- `pack.mcmeta` — pack format 15.
