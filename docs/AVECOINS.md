# AVECOINS integration

Zian GTS V2 integrates with AVECOINS 2.3 through its runtime wallet interface.

AVECOINS is installed separately and is not bundled with Zian GTS.

## Supported runtime

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228+
- AVECOINS 2.3

The V2 adapter validates supported currencies and performs balance mutations on the server.

## Transaction behavior

AVECOINS, Cobblemon storage and Zian GTS persistence are independent systems. A purchase therefore cannot be represented as one atomic transaction across all three stores.

Zian GTS journals sensitive transitions. If an external result is uncertain, the operation is not blindly retried and trading can be blocked for manual recovery.

See `RECOVERY.md` for the recovery procedure.

## Safety

Do not bypass an ownership, tradeability, balance or recovery check to make an edge case succeed. Administrative/generated Pokémon that Cobblemon itself marks as non-tradeable should remain rejected by the GTS.
