# Zian GTS independent implementation specification

Status: normative implementation specification for the v2 rewrite.

## Provenance rule

This rewrite is based on Zian GTS product requirements and public APIs. Do not
consult or copy decompiled Cobble GTS implementation details while implementing
v2. Existing provenance-sensitive v1 classes are migration inputs only at the
data boundary and will be removed from the release tree.

Because earlier Zian development inspected a reference binary, this work is
described as an **independent rewrite**, not as a strict two-team clean-room
implementation.

## Product behavior

A player can:
- publish one trade offer containing one owned, tradeable Pokémon;
- browse current offers;
- filter by Shiny, Alpha, legendary and legendary+Shiny;
- sort by creation time, price or Pokémon level;
- buy another player's current offer;
- withdraw their own offer, including an expired offer;
- collect seller proceeds;
- inspect their own offers and remaining expiry time.

Administrators can inspect transaction history and recovery incidents.

## Server authority

The client never decides ownership, price, currency, expiry, Pokémon contents,
wallet balance or transaction outcome. Client requests contain intent and
identifiers only. Every mutation is validated and executed on the server.

## New domain model

v2 uses these concepts:

- `OfferId`: opaque UUID.
- `OfferOwner`: seller UUID and display name captured at publication.
- `PaymentSpec`: economy adapter id, currency id and positive amount.
- `PokemonEnvelope`: serialized Pokémon payload plus display/search metadata.
- `TradeOffer`: immutable offer aggregate.
- `OfferState`: CURRENT or EXPIRED, derived from the expiry timestamp.
- `OfferBook`: persistence boundary, not a Minecraft SavedData implementation.
- `TradeEngine`: application service for publish/purchase/withdraw/claim.
- `MarketQuery`: paging/filter/sort request.
- `MarketView`: DTO returned to networking/UI.

This vocabulary intentionally differs from the reconstruction baseline.

## Transaction invariants

1. A Pokémon UUID can exist in at most one current offer.
2. A seller cannot buy their own offer.
3. Expired offers cannot be purchased.
4. Expiry never destroys a Pokémon. The owner can withdraw it.
5. A purchase is never automatically retried after an uncertain external
   storage/economy outcome.
6. Payment and Pokémon delivery transitions are journaled durably.
7. Seller proceeds retain the economy adapter and currency used by the offer.
8. Corrupt/unreadable records are preserved for recovery rather than silently
   discarded.
9. All mutations are serialized on the Minecraft server thread.
10. The client cannot supply authoritative transaction values.

## Persistence

The v2 release will use a new SavedData key/version and an explicit migration
reader for Zian v1 data. Migration is one-way and creates a backup/evidence
record before committing converted data.

The durable Zian transaction journal remains a Zian-owned subsystem and is
adapted to v2 operations.

## Network protocol

v2 gets new payload identifiers and a protocol version. Requests use explicit
action types instead of numeric action magic where the NeoForge codec permits.
All paging/filter/sort values are bounded server-side.

## UI

The v2 UI is a Zian design:
- offers list;
- Pokémon preview;
- seller and price;
- level, gender, Shiny, Alpha, nature and ability;
- IV radar;
- filter/sort toolbar;
- buy confirmation;
- owner withdrawal action;
- claim proceeds.

No third-party textures, assets or copied layout code are required.

## Release gate

The public v2 release must not compile or package provenance-sensitive v1
implementation classes. Compatibility migration code may understand Zian v1
serialized data formats, but must not copy third-party implementation.

MIT applies to original Zian code. Third-party dependencies keep their own
licenses and notices.
