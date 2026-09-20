# Recovery diagnostics

The write-ahead journal now retains every market operation independently of SavedData.
See [RESTARTS_AND_JOURNAL.md](RESTARTS_AND_JOURNAL.md) for orderly restarts,
crash quarantine, journal resolution and limitations on Youer. It does not replay payments.

`/gts recovery` requires `ziangts.admin.recovery` (operator level 2 by default) and reports whether storage is blocked,
the incident count and the last ten failed transfers. The listing itself is read-only: it does
not refund money, deliver Pokémon or clear the block. Each incident line starts with its incident id.

Uncertain wallet saves and transfer exceptions block subsequent trading. Each
incident is retained in SavedData and copied to the world's `ziangts-recovery`
directory as an SNBT file, with the actor, operation and original snapshot.
The file contents are forced to disk before requesting a SavedData save. Failed
diagnostic writes are logged and never unblock trading.

Before manual recovery, back up the world and compare GTS storage, both players'
Pokémon storage, inventories and AVECOINS wallet records. Do not blindly retry an
uncertain payment or restore a Pokémon solely because an exception was reported.

## Resolving an incident

`/gts recovery resolve <incident-id>` prints the confirmation command; append `confirm`
to apply it. It requires `ziangts.admin.recovery.resolve` (operator level 2 by default)
in addition to `ziangts.admin.recovery`. Resolving only lifts the trading block caused by
that incident, after you have reconciled the stores manually: it never refunds money or
delivers a Pokémon. The original record moves to an audit log with `resolvedBy`, `resolvedById` (for an entity source) and
`resolvedAt` inside the `ziangts_listings` SavedData. Unreadable listings or payouts keep
blocking trading until they are repaired by hand. The command requests a SavedData save
before reporting success; this is not a crash-atomic commit across stores.

## Failures after delivery

If crediting the seller or appending the history record fails after the Pokémon and the
payment have already changed hands, the buyer is not told the purchase failed (a retry
would duplicate it). An incident of type `buy_credit` or `buy_history` is preserved
instead, including the complete intended transaction record (id, parties, payment and Pokémon snapshot), and trading is blocked until an administrator reconciles the seller proceeds or
the history record and resolves it.

The incident records below supplement the write-ahead journal. Abrupt
termination can still leave inconsistent external stores requiring manual reconciliation.
Automatic cross-store crash recovery and real multiplayer validation remain
pending. Keep production trading disabled until these are addressed.
