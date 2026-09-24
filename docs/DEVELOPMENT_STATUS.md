# Zian GTS V2 — development status

## Current candidate

Version: **1.0.0 Beta 1 candidate**

The active production implementation is Zian GTS V2.

## Implemented

- Native market UI.
- Party publication flow.
- Purchase and owner withdrawal.
- Seller proceeds and claim.
- Transaction history.
- Filters, sorting and pagination.
- Pokémon details and IV radar.
- AVECOINS integration.
- Durable market, history and transaction journal.
- Server-authoritative validation.
- Request sequencing with requestId.
- Administrative status and recovery commands.

## Commands

Players:

- `/gtsv2`
- `/gtsv2 open`

Operators:

- `/gtsv2 status`
- `/gtsv2 recovery`
- `/gtsv2 recovery resolve <operation-uuid>`
- `/gtsv2 recovery resolve <operation-uuid> confirm`

## Transaction safety

Zian GTS coordinates its own durable state with external Pokémon and economy systems. Those systems do not share one atomic transaction.

For that reason, uncertain external outcomes are not blindly retried. The transaction journal retains evidence and the runtime can block trading until an administrator reconciles the external stores.

## Validation completed

Automated coverage includes persistence, concurrency, rollback, journal failures, reentrancy, reservation races, hard-kill probes and runtime lifecycle.

Real multiplayer testing has covered publishing, buying, withdrawing, claim, history, filters, pagination, simultaneous buyers, buy-vs-withdraw races, restart persistence and abuse/duplication attempts.

## Release gate

The remaining gate for Beta 1 is a smoke test of the exact release-candidate JAR on the target Youer server, followed by one final artifact audit.
