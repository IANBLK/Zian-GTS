# Zian GTS independent implementation specification

Status: normative implementation specification for the v2 rewrite.

## Implementation scope

Zian GTS V2 is implemented from the project's product requirements and the public APIs of its supported dependencies. The public release tree contains only the active V2 implementation and release documentation.

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

The public V2 release packages only the active Zian GTS implementation and required resources. Third-party dependencies are installed separately and keep their own licenses.
