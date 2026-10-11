**🌐 ZIAN GTS — A MARKETPLACE FOR YOUR POKÉMON**

Give your Cobblemon server a dedicated marketplace where players can list Pokémon from their party, browse offers, buy Pokémon, withdraw their own listings, and claim sale proceeds through an in-game interface.

Built for **Minecraft 1.21.1 and NeoForge**, with testing on **Youer 1.21.1**.

**🔎 BROWSE BEFORE YOU BUY**

View Pokémon models and inspect their gender, nature, ability, moves, and IVs. A visual IV radar helps compare their stats.

Use filters for **Shiny, Alpha, Legendary, and your own listings**, together with sorting and pagination, to find the offers you want.

**💰 AVECOINS ECONOMY**

Zian GTS integrates with **AVECOINS 2.3 and 2.4**. The server checks the wallet contract, balances, listing ownership, and purchase conditions.

Seller proceeds are persisted and can be claimed through the interface. A listing is reserved during a purchase to prevent two players from buying the same offer simultaneously.

An unsupported economy version or an unresolved transaction can leave the market unavailable while the server remains running.

**📜 COMMANDS**

**/ZianGTS** — Open the marketplace.

**/ZianGTS open** — Open the marketplace.

**/ZianGTS status** — Administrative status and readiness information.

**/ZianGTS recovery** — Inspect unresolved transaction records.

**/ZianGTS recovery resolve <operation-uuid>** — Show the confirmation required to record a manually reconciled operation.

**/ZianGTS recovery resolve <operation-uuid> confirm** — Confirm that manual reconciliation has been completed.

The command name is **/ZianGTS**, including its capitalization. The old **/gtsv2** command has been removed. Update scripts and menu buttons that still use it.

**🔐 LUCKPERMS SUPPORT**

LuckPerms is optional and can be installed as a **NeoForge mod** or as a **Bukkit plugin on Youer**.

**ziangts.use** — General access to the commands and interface.

**ziangts.list** — Browse public listings and filters.

**ziangts.mine** — View personal listings, pending proceeds, and personal history.

**ziangts.sell** — Publish Pokémon.

**ziangts.buy** — Purchase Pokémon.

**ziangts.cancel** — Withdraw your own listings.

**ziangts.claim** — Claim sale proceeds.

**ziangts.admin.recovery** — Inspect administrative status and recovery information.

**ziangts.admin.recovery.resolve** — Resolve manually reconciled journal operations.

Every action also requires **ziangts.use**. Recovery resolution additionally requires **ziangts.admin.recovery**. Explicit denials apply to operators too. If LuckPerms is installed but cannot respond, access is denied.

Without LuckPerms, player actions are enabled by default and administrative actions require operator level 2. Permissions never bypass ownership, balance, or market safety checks.

See the [permission guide](https://github.com/IANBLK/Zian-GTS/blob/fix/command-and-permission-review/docs/PERMISSIONS.md) for group configuration.

**💾 PERSISTENCE AND RECOVERY**

Market data, transaction history, pending proceeds, and transaction evidence are persisted on the server.

The market, Cobblemon storage, and AVECOINS are separate systems. If an interrupted operation has an uncertain outcome, trading is blocked for review rather than assuming success or retrying an external mutation.

Recovery commands **do not automatically refund money or deliver Pokémon**. Administrators must verify the wallet, Pokémon ownership, market state, and pending proceeds before resolving an operation. Restart the server after all unresolved operations have been reconciled.

Read the [recovery guide](https://github.com/IANBLK/Zian-GTS/blob/fix/command-and-permission-review/docs/RECOVERY.md).

**📦 INSTALLATION**

Requires **Minecraft 1.21.1**, **Java 21**, **NeoForge 21.1.228+**, **Kotlin for Forge 5.10+**, and **Cobblemon 1.8+** for Minecraft 1.21.1.

Install **AVECOINS 2.3 or 2.4** for the integrated economy. Install the same Zian GTS version on both the server and clients, together with the required dependencies.

**🚧 BETA — ACTIVE DEVELOPMENT**

Zian GTS is in active development. The core has automated coverage for persistence, concurrent purchases, rollback, journaling, and recovery. Live-server testing remains necessary for each release and dependency combination.

Back up your world before updating. Report issues with your mod versions, server platform, relevant logs, and reproduction steps through [GitHub Issues](https://github.com/IANBLK/Zian-GTS/issues).

**Created by IANBLK • Minecraft 1.21.1 • NeoForge • Youer • MIT License**
