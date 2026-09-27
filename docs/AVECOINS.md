# AVECOINS integration

Zian GTS V2 integrates with AVECOINS 2.3 and 2.4 through its runtime wallet interface.

AVECOINS is installed separately and is not bundled with Zian GTS.

## Supported runtime

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228+
- AVECOINS 2.3 or 2.4

The V2 adapter validates the version, wallet method signatures, managed currencies, and wallet capacity before opening the market. An uninspected later AVECOINS version leaves the Minecraft server running but keeps GTS trading unavailable until its wallet contract is reviewed. The NeoForge dependency metadata therefore imposes no version ceiling; the runtime check remains the safety boundary.

The standalone wallet contract probe passed against the separately supplied 2.3 and 2.4 JARs. Live server purchase, seller proceeds, and restart checks remain necessary for 2.4.

## Transaction behavior

AVECOINS, Cobblemon storage and Zian GTS persistence are independent systems. A purchase therefore cannot be represented as one atomic transaction across all three stores.

Zian GTS journals sensitive transitions. If an external result is uncertain, the operation is not blindly retried and trading can be blocked for manual recovery.

See `RECOVERY.md` for the recovery procedure.

## Safety

Do not bypass an ownership, tradeability, balance or recovery check to make an edge case succeed. Administrative/generated Pokémon that Cobblemon itself marks as non-tradeable should remain rejected by the GTS.

