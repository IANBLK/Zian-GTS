# Recovery diagnostics

`/gts recovery` requires operator level 2 and reports whether storage is blocked,
the incident count and the last ten failed transfers. It is read-only: it does
not refund money, deliver Pokémon or clear the block.

Uncertain wallet saves and transfer exceptions block subsequent trading. Each
incident is retained in SavedData and copied to the world's `ziangts-recovery`
directory as an SNBT file, with the actor, operation and original snapshot.
The file contents are forced to disk before requesting a SavedData save. Failed
diagnostic writes are logged and never unblock trading.

Before manual recovery, back up the world and compare GTS storage, both players'
Pokémon storage, inventories and AVECOINS wallet records. Do not blindly retry an
uncertain payment or restore a Pokémon solely because an exception was reported.

This is incident preservation, not a write-ahead transaction journal. Abrupt
termination before an exception is handled can still leave inconsistent stores.
Automatic cross-store crash recovery and real multiplayer validation remain
pending. Keep production trading disabled until these are addressed.
