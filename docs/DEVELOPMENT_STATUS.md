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
- `/gts claim` claims pending item or AVECOINS wallet proceeds, partially if inventory space is limited.
- `/gts history [player-uuid] [page]` requires `ziangts.admin.history` (OP level 2 by default).
- `/gts history delete <transaction-uuid> confirm` archives the record, retaining
  its original contents, actor and timestamp in SavedData. It requires `ziangts.admin.history.archive` (OP level 2 by default).
- `/gts recovery` lists quarantined incidents with their ids; `/gts recovery resolve <incident-id> confirm`
  (`ziangts.admin.recovery.resolve`, OP level 2 by default) marks one as resolved after manual reconciliation and keeps an audit copy.
- UI: six entries per page, all/Shiny/Alpha/legendary/legendary Shiny/own filters,
  date/price/level sorting, confirmation, claim and withdrawal buttons, Pokémon
  preview on wider screens. Central details show seller, price, level, gender,
  Shiny, Alpha, Nature, Ability, stats and IVs; EVs are not shown.
- Networking uses bounded requests, a per-player request limit and server-side
  lookups. No client-supplied Pokémon, ownership, price or balance is accepted.
- Corrupt listings/payouts are retained verbatim and block trading. Corrupt history
  records are also retained. Expiration never silently destroys an escrowed Pokémon.
- LICENSE and THIRD_PARTY_NOTICES are packaged into the JAR.

## Test-world gate and remaining work

This remains a development build. `tradingEnabled` defaults to false in the
world's `serverconfig/ziangts-server.toml`. Enable it only in a backed-up test world.
Browsing is available independently. Currency, listing limit and expiry are also
world-specific settings. The default is `avecoins:coppercoin`; new offers and
purchases accept only the supported AVECOINS coin and ticket catalog.

The current item transaction sequence is serialized on the server thread with
reentrant mutation protection. It is NOT a crash-atomic transaction across
Minecraft player inventory, Cobblemon asynchronous storage and GTS SavedData.
The append-only recovery journal now records intent before each market mutation and
quarantines sessions interrupted without an orderly shutdown marker. It preserves
evidence; it does not replay money or Pokémon automatically. See
[RESTARTS_AND_JOURNAL.md](RESTARTS_AND_JOURNAL.md). Before enabling this on a real
server, validate interruption at each actual mod ownership/payment boundary. Exceptions from Cobblemon
removal/delivery callbacks quarantine the pre-operation listing snapshot in
`failedTransfers` and block subsequent trading for manual recovery. This is not
a substitute for verified cross-store durability. Test these callback failures too. Do not claim crash-safe trading yet.

Required in-game validation: dedicated-server startup; simultaneous purchases of
the same offer; last Pokémon / untradeable / active battle / active trade rejection;
full party and PC; full inventory with partial claims; seller offline; expiry and
reclaim; disconnect and restart; multiple dimensions; packet spam; small GUI scales;
preview models and custom forms. Unit tests cover proceeds persistence, overflow,
overdraft, and retention/quarantine of malformed and duplicate records.

AVECOINS 2.3 is connected through an optional reflection adapter using its public
wallet methods. Physical AVECOINS coins also work as item currency. See
[AVECOINS.md](AVECOINS.md) for configuration, supported denominations, wallet
capacity, live-test requirements and the cross-store recovery limitation.
Minecraft stays 1.21.1; NeoForge is now 21.1.228 to satisfy the supplied AVECOINS
artifact. The external JAR and its decompiled source are not redistributed.

Visual parity with the reference binary, trade evolution/event behavior, live LuckPerms
validation, and crash recovery remain pending. Shiny stays Shiny.
