# Zian GTS 1.0.3 — Updating and recovery

## Installation, updating and recovery

Use Minecraft **1.21.1**, **Java 21** and **NeoForge**, with the separate required libraries listed in the README. Stop the server and back up the entire world, configuration and mod list before replacing a version. Install the same current JAR on the server and every client. Remove the previous JAR, keep the required libraries on both sides, start and inspect the log, then verify your existing configuration and progress. For rollback, stop and restore the complete matching backup and mod versions together. Do not edit schema numbers to force migration.

Youer has been tested; this does not guarantee every hybrid server/plugin combination. Command-whitelist plugins may separately block public commands. Allow the needed public roots while keeping administrative operations restricted. LuckPerms is optional; administrator configuration remains protected.

Keep world data and configuration together. Optional SQLite maintenance is explicit, not required automatically by an update. Never edit a live database or delete a journal to clear an error. For an uncertain payment/delivery, keep its reference, reconcile the recorded outcome with inventory/PC/wallet state and use the administrator review flow. Confirmation records a verified result; it must never be used to guess or generate a second reward.

Report the mod/library versions, reproduction steps and sanitized logs through this repository's Issues. Do not upload credentials, tokens, player databases or private world backups.

## Pokémon recovery files (journal failure)

If the transaction journal cannot be written during a purchase or withdrawal, GTS puts the offer back when nothing was charged. If the buyer was already charged (stage `PAYMENT_APPLIED` or `BEFORE_POKEMON_DELIVERY`), or the offer cannot be restored, the Pokémon is **not** delivered and is saved to `<world>/ziangts-v2/recovery/<ticket>.json` (ticket quarantined, `ERROR` in the log with ticket and UUIDs). The file holds the ticket, failed stage, offer id, buyer and seller UUIDs, price, timestamp, error and the full Pokémon (`pokemon.serialized` is the stored NBT text). If the file cannot be written, the same JSON is printed in the server log.

To restore by hand: stop the server, check the buyer's wallet and PC/party, then either give the Pokémon back from `pokemon.serialized` (to the buyer if the payment stands, to the seller if you refund the buyer), or refund the buyer. Never do both. Then resolve the quarantined ticket with the administrator review flow and keep the file as evidence.
