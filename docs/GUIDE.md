# Zian GTS 1.0.3 — English guide

**Language notice:** The current in-game interface, preset dialogues and administrator messages are primarily Spanish. English localization is incomplete. The English guide explains the controls and includes their Spanish button names. Changing Minecraft to English does not translate administrator-written NPC names, dialogues or configuration text.

## Players: browse and purchase

1. Open `/ZianGTS` or `/ZianGTS open`; `/gts` is an alias when available. Choose **Mercado** (Market).
2. **Filtro** changes filtering; **Orden** changes sorting. Enter species or seller and choose **Buscar** (Search). Search happens before pagination.
3. Inspect the Pokémon card, level, IVs, shiny/alpha status, price, seller and expiration. Hover for details, then choose **Comprar** (Buy), inspect the confirmation and confirm the intended offer. A supported wallet and enough funds are required.

## Players: sell, withdraw and collect

1. Put the intended Pokémon in your party. Choose **Publicar** (Publish), select it, set currency/price and confirm.
2. **Mis anuncios** (My listings) shows owned offers. **Retirar** (Withdraw) requires confirmation and returns the owned Pokémon through the recorded delivery flow.
3. **Ganancias** (Proceeds) collects sale earnings; **Historial** (History) shows your transactions.
4. Expired offers are hidden from the public market but remain recoverable by their owner. **Expira 0m** is not a permissions error. Withdraw and relist to start a new listing period.

The server checks ownership, listing availability, payment and storage. Public access cannot withdraw another player's listing. Do not retry an uncertain transaction by deleting market data.

## Permissions and economy

Player defaults allow `ziangts.use`, `.list`, `.mine`, `.sell`, `.buy`, `.cancel` and `.claim`. LuckPerms is optional; explicit denials affect OP too. Recovery needs `ziangts.admin.recovery`, and resolution additionally needs `ziangts.admin.recovery.resolve`, or the supported fallback without a provider.

AVECOINS 2.3/2.4/2.5 wallets have been inspected. Later versions require the reviewed classes/fingerprints and reflection contract to match; arbitrary future compatibility is not promised. Trading is unavailable without a supported wallet. Cobblemon and Kotlin for Forge are separate required libraries.

## Administrator recovery

Inspect `/ZianGTS status` and `/ZianGTS recovery`. Reconcile both players' wallet balances, Pokémon location/owner, listing state and seller proceeds before resolution. `/ZianGTS recovery resolve <operation-uuid>` prints the confirmation form; adding `confirm` records completed manual reconciliation, without refunding or generating a Pokémon. Restart after unresolved entries are properly reconciled before trading resumes. There is no shared atomic transaction between Pokémon storage, market and wallet; uncertain evidence deliberately remains blocked. Protocol: **v3-market**.

## Installation, updating and recovery

Use Minecraft **1.21.1**, **Java 21** and **NeoForge**, with the separate required libraries listed in the README. Stop the server and back up the entire world, configuration and mod list before replacing a version. Install the same current JAR on the server and every client. Remove the previous JAR, keep the required libraries on both sides, start and inspect the log, then verify your existing configuration and progress. For rollback, stop and restore the complete matching backup and mod versions together. Do not edit schema numbers to force migration.

Youer has been tested; this does not guarantee every hybrid server/plugin combination. Command-whitelist plugins may separately block public commands. Allow the needed public roots while keeping administrative operations restricted. LuckPerms is optional; administrator configuration remains protected.

Keep world data and configuration together. Optional SQLite maintenance is explicit, not required automatically by an update. Never edit a live database or delete a journal to clear an error. For an uncertain payment/delivery, keep its reference, reconcile the recorded outcome with inventory/PC/wallet state and use the administrator review flow. Confirmation records a verified result; it must never be used to guess or generate a second reward.

Report the mod/library versions, reproduction steps and sanitized logs through this repository's Issues. Do not upload credentials, tokens, player databases or private world backups.
