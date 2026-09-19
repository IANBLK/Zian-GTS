# Development status

## Build fixes

- Kotlin compiler 2.2.20 matches the Kotlin 2.2 metadata shipped by Cobblemon
  1.8.0 and Kotlin for Forge 5.10.0.
- The mod descriptor uses `${version}`, which is the property exported by
  processResources; `${file.jarVersion}` was being interpreted by Gradle.
- Listing UUID serialization explicitly uses `this@Listing.id`, avoiding the
  `CompoundTag.id` (NBT type byte) receiver inside `apply`.
- ListingsData captures the overworld RegistryAccess required by Cobblemon,
  rather than passing SavedData's more general HolderLookup.Provider.

## Reconstructed development features

- `/gts` opens the market; `/gts list [page]` provides a text alternative.
- `/gts sell <party-slot 1..6> <price>` escrows a Pokémon.
- `/gts buy <listing-uuid>` shows the price; append `confirm` to purchase.
- `/gts mine` includes expired offers; `/gts cancel <listing-uuid>` returns one.
- `/gts claim` claims pending item proceeds, partially if inventory space is limited.
- `/gts history [player-uuid] [page]` requires vanilla operator level 2.
- `/gts history delete <transaction-uuid> confirm` archives the record, retaining
  its original contents, actor and timestamp in SavedData. It requires level 2.
- UI: six entries per page, all/Shiny/Alpha/legendary/legendary Shiny/own filters,
  date/price/level sorting, confirmation, claim and withdrawal buttons, Pokémon
  preview on wider screens. Central details retain seller, price, level, gender,
  Shiny, stats, IVs and EVs. Nature and Ability are omitted only from this panel.
- Networking uses bounded requests, a per-player request limit and server-side
  lookups. No client-supplied Pokémon, ownership, price or balance is accepted.
- Corrupt listings/payouts are retained verbatim and block trading. Corrupt history
  records are also retained. Expiration never silently destroys an escrowed Pokémon.
- LICENSE and THIRD_PARTY_NOTICES are packaged into the JAR.

## Test-world gate and remaining work

This remains a development build. `tradingEnabled` defaults to false in the
world's `serverconfig/ziangts-server.toml`. Enable it only in a backed-up test world.
Browsing is available independently. Currency, listing limit and expiry are also
world-specific settings. Default currency remains unmodified vanilla diamonds;
named or component-modified diamonds are deliberately not counted as money.

The current item transaction sequence is serialized on the server thread with
reentrant mutation protection. It is NOT a crash-atomic transaction across
Minecraft player inventory, Cobblemon asynchronous storage and GTS SavedData.
Before enabling this on a real server, add a persistent recovery journal and test
interruption at each ownership/payment boundary. Also test callbacks from other
mods that throw during removal or delivery. Do not claim crash-safe trading yet.

Required in-game validation: dedicated-server startup; simultaneous purchases of
the same offer; last Pokémon / untradeable / active battle / active trade rejection;
full party and PC; full inventory with partial claims; seller offline; expiry and
reclaim; disconnect and restart; multiple dimensions; packet spam; small GUI scales;
preview models and custom forms. Unit tests cover proceeds persistence, overflow,
overdraft, and retention/quarantine of malformed and duplicate records.

AVECOINS is not wired up: its JAR/repository/API must be supplied. The existing
EconomyProvider interface alone does not guarantee idempotency or atomic transfers.
Integration needs documented failure semantics, offline-player support and stable
operation IDs before external currency is debited. Item payments are explicitly
isolated rather than pretending to support an unknown AVECOINS API.

Visual parity with the reference binary, trade evolution/event behavior, custom
permission-node integrations, and crash recovery remain pending. Shiny stays Shiny.
