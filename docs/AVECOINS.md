# AVECOINS 2.3 integration

Inspected user-supplied artifact: AVECOINS 2.3, author SundGGs, Minecraft 1.21.1,
NeoForge >= 21.1.228, SHA-256
`008e115448d1156c4117f2030f9f3da3d03615324aff1317da96b27f2332c1a2`.
The dependency is proprietary and is not copied into this repository or bundled.

The adapter calls public `WalletStore.get/save` and `WalletData.copy`, `balance`,
`balances`, `debit` and `credit` methods using reflection. No private access,
command dispatch, direct editing of AVECOINS files or duplicated wallet database
is used. The known version and supported currencies are checked before use.
Unknown versions fail closed. This contract was inspected in the supplied JAR;
a live integration test with the actual mod is still required.

## Modes

Configure the world's `serverconfig/ziangts-server.toml`:

```toml
# Keep false on production until cross-store crash recovery is implemented.
tradingEnabled = false
# Physical AVECOINS coins or tickets in the player inventory:
economyProvider = "vanilla_item"
currency = "avecoins:coppercoin"
```

For the AVECOINS wallet instead:

```toml
economyProvider = "avecoins_wallet"
currency = "avecoins:goldcoin"
```

Supported denominations: `coppercoin`, `ironcoin`, `goldcoin`, `diamondcoin`,
`netheritecoin`, `goldticket`, `diamondticket`, `netheriteticket`, all in the
`avecoins` namespace. The configured denomination is the unit of the price. New
offers reject every currency outside this catalog; `avecoins:coppercoin` is the default.
There is no automatic conversion or mixing inventory and wallet funds in this
adapter. This is explicit in the market payment method.

Existing listings, pending payouts and transaction records retain their original
provider and denomination. Changing the config affects new offers only. Legacy
records without a provider are interpreted as physical item currency. Legacy
offers with a non-AVECOINS currency remain visible to their owner for withdrawal,
but are hidden from buyers and cannot be purchased.

## Wallet behavior and limits

- Exact denominations; the supplied wallet has 27 slots of 64 items.
- Other denominations consume slots using whole-stack rounding.
- Offers using the wallet cannot exceed its 1728-unit per-currency balance limit.
- `/gts claim` deposits what fits and keeps the rest pending, including when the
  seller was offline during a purchase.
- `/gts` closes an existing container before opening the market. Direct payment
  and claim commands require containers to be closed. In particular,
  AVECOINS's wallet menu holds a snapshot and saves on close; mutating the backing
  balance while that menu is open could overwrite a payment.
- Mutations work on a copy and check WalletStore.save's boolean result. An
  exceptional/uncertain debit, credit, refund or Pokémon callback quarantines
  the operation snapshot and blocks further market mutations for manual review.

This does not make the combined wallet + Cobblemon + GTS operation crash-atomic.
AVECOINS's exposed methods do not provide external transaction identifiers or
an idempotency lookup. A cross-store recovery protocol remains necessary before
production trading is enabled. Never treat an exception as proof that no debit
occurred, and never blindly retry an uncertain payment.

## Repeat the isolated wallet contract check

With Java 21 and the separately installed AVECOINS 2.3 JAR:

```sh
java --class-path /path/to/avecoins-2.3.jar tools/VerifyAvecoinsWallet.java
```

This check passed against the artifact fingerprint above. It verifies copy
isolation, exact debit, overdraft rejection, defensive balance maps and the
aggregate 27-slot limit. It does not initialize a Minecraft server or exercise
WalletStore disk persistence, network menus or a live Pokémon transfer.
