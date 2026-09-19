# Build status

## Verified target versions
- Minecraft 1.21.1
- NeoForge 21.1.182
- Cobblemon 1.8.0+1.21.1
- Kotlin for Forge 5.10.0
- Java 21

The Cobblemon 1.8 version is corroborated by active NeoForge/Fabric addon
projects targeting the same 1.21.1 stack. The build uses Modrinth Maven for
Cobblemon and keeps dependency versions in gradle.properties.

## Current milestone
The repository now has:
- NeoForge/Kotlin project skeleton
- Cobblemon 1.8 dependency declaration
- base mod entrypoint
- listing domain model
- economy abstraction for AVECOINS
- immutable transaction record model
- transaction history abstraction
- Spanish resource bundle

## Not yet claimed
No successful Gradle build or Minecraft runtime test is claimed yet. A Gradle
wrapper and CI build must be established and the Cobblemon dependency resolved
before the baseline can be called compilable.
