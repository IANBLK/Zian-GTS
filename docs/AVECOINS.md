# AVECOINS integration

Zian GTS V2 integrates with AVECOINS 2.3, 2.4 and 2.5 through its runtime wallet interface.

AVECOINS is installed separately and is not bundled with Zian GTS.

## Supported runtime

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228+
- AVECOINS 2.3, 2.4 or 2.5; later versions require the inspected class fingerprints to match

The V2 adapter validates the version, wallet method signatures, managed currencies, and wallet capacity before opening the market. A later version whose inspected class fingerprints differ leaves the Minecraft server running but keeps GTS trading unavailable until its wallet contract is reviewed. The NeoForge dependency metadata therefore imposes no version ceiling; the runtime check remains the safety boundary.

The inspected 2.5 wallet classes match the reviewed 2.4 class fingerprints. The adapter and version tests passed, and GTS 1.0.1 reached ready state on the supplied Youer/Cobblemon 1.8.2/AVECOINS 2.5 server. This does not certify arbitrary future wallet changes.

## Transaction behavior

AVECOINS, Cobblemon storage and Zian GTS persistence are independent systems. A purchase therefore cannot be represented as one atomic transaction across all three stores.

Zian GTS journals sensitive transitions. If an external result is uncertain, the operation is not blindly retried and trading can be blocked for manual recovery.

See `RECOVERY.md` for the recovery procedure.

## Safety

Do not bypass an ownership, tradeability, balance or recovery check to make an edge case succeed. Administrative/generated Pokémon that Cobblemon itself marks as non-tradeable should remain rejected by the GTS.

## AVECOINS 2.5 and future version numbers

The supplied 2.5 WalletStore, WalletData and CraftingConfig classes are byte-identical to inspected 2.4 classes. The real wallet contract probe passes. Version 2.5 is explicitly reviewed; later version numbers are accepted only if all three class resources match the reviewed SHA-256 fingerprints. Reflection checks remain active. Changed classes are rejected until inspection, even if their public signatures appear unchanged. No unreviewed API change is automatically allowed.
