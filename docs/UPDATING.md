# Zian GTS 1.0.3 — Updating and recovery

## Installation, updating and recovery

Use Minecraft **1.21.1**, **Java 21** and **NeoForge**, with the separate required libraries listed in the README. Stop the server and back up the entire world, configuration and mod list before replacing a version. Install the same current JAR on the server and every client. Remove the previous JAR, keep the required libraries on both sides, start and inspect the log, then verify your existing configuration and progress. For rollback, stop and restore the complete matching backup and mod versions together. Do not edit schema numbers to force migration.

Youer has been tested; this does not guarantee every hybrid server/plugin combination. Command-whitelist plugins may separately block public commands. Allow the needed public roots while keeping administrative operations restricted. LuckPerms is optional; administrator configuration remains protected.

Keep world data and configuration together. Optional SQLite maintenance is explicit, not required automatically by an update. Never edit a live database or delete a journal to clear an error. For an uncertain payment/delivery, keep its reference, reconcile the recorded outcome with inventory/PC/wallet state and use the administrator review flow. Confirmation records a verified result; it must never be used to guess or generate a second reward.

Report the mod/library versions, reproduction steps and sanitized logs through this repository's Issues. Do not upload credentials, tokens, player databases or private world backups.
