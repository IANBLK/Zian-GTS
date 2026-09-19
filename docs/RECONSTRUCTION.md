# Zian GTS - Reconstruction notes

## Reference binary
Analyzed: `cobblegts-neoforge-1.7.0-1.5.2.jar`

The reference binary is MIT licensed and identifies the original project as Cobble GTS by xpointfive. Zian GTS will preserve required license/attribution notices while developing its own maintained source tree.

## Confirmed reference requirements
- Minecraft 1.21.1
- NeoForge >= 21.1.182
- Kotlin for Forge loader >= 4
- Cobblemon >= 1.7.1+1.21.1
- Java 21 runtime target

Zian GTS target: Cobblemon 1.8+.

## Confirmed architecture
Reference classes include:
- GtsCommand / WondertradeCommand
- Listing / ListingsData / WondertradeData
- GtsScreen / GtsStorageWidget / GtsStorageSlot
- PokemonTooltip
- FilterButton / SortButton / NavigationButton
- PurchaseButton / PurchaseConfirmationButton
- OpenListingsPacket / PurchaseListingPacket
- NeoForgeNetworkManager

The listing model stores listing UUID, seller UUID/name, price, currency, creation/expiration timestamps and the Pokemon.

## Important localization finding
The reference JAR contains user-visible English strings directly in compiled code (for example Nature, Ability, Purchase, Show All and command responses). It does not ship a normal lang resource for those strings. Translation therefore requires source-level replacement/translatable components; adding only es_es.json is insufficient.

## Planned Zian changes
1. Establish a reproducible NeoForge 1.21.1 project.
2. Reconstruct baseline behavior before feature changes.
3. Replace visible hard-coded strings with translation keys; Spanish (Spain) is primary and the term "Shiny" remains unchanged.
4. Preserve the requested GTS UI while removing only Nature and Ability from the specified central detail area.
5. Add AVECOINS integration behind an economy abstraction.
6. Add persistent transaction history with transaction ID, buyer, seller, Pokemon snapshot/identifier, amount/currency and timestamp.
7. Add permission-gated history search/deletion with confirmation and audit-safe behavior.
8. Build/test before producing release JARs.

## Safety rule
Do not overwrite the original reference binary. Development happens on branches and releases are built from source.
