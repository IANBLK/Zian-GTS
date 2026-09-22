# Zian GTS V2 - Runtime integration gate

This gate separates the independently implemented transaction core from Minecraft/Cobblemon/economy runtime adapters.

## Core already required before runtime wiring

- Durable offer/proceeds persistence survives reopen.
- Checksummed append-only transaction journal replays incomplete operations and fails closed.
- Purchase hard-kill tests exercise the real TradeEngine with durable market, economy, Pokemon and journal test adapters.
- Reentrant mutation attempts from an external Pokemon callback are rejected.

## Runtime adapter rules

1. `TradeEngine` remains free of NeoForge, Minecraft, Cobblemon and AVECOINS classes.
2. Cobblemon integration implements only `PokemonPort`; AVECOINS integration implements only `EconomyPort`.
3. Adapter callbacks must translate known refusal into `Rejected`; exceptions or unknowable outcomes into `Uncertain`.
4. Never retry an `Uncertain` mutation automatically.
5. The same journal operation UUID is passed to every external mutation.
6. Runtime entry points execute mutations on the Minecraft server thread.
7. Startup must refuse trading when the V2 journal has unresolved evidence or persistence cannot be read.
8. Graceful shutdown is not considered proof of crash safety. A real Youer kill test is required.
9. Runtime integration must not depend on V1 market/service/listing implementation classes.
10. API usage for new adapters must be based on current public dependency APIs/documentation and compilation feedback, not copied V1 implementation.

## Required Youer test matrix

- publish, restart, withdraw;
- purchase with party space;
- purchase with party full / PC available;
- insufficient funds;
- own-offer rejection;
- duplicate/reentrant request;
- scheduled graceful restart;
- forced process kill before payment, after payment and after Pokemon delivery;
- claim proceeds and forced kill during claim;
- unresolved journal blocks new trading and remains blocked after restart;
- explicit administrative resolution restores trading only after external state is verified.

Passing unit tests is necessary but not sufficient for release.
