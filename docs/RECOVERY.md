# Zian GTS V2 recovery

Zian GTS uses a durable transaction journal for operations that cross the GTS market, Pokémon storage and economy systems.

If the runtime finds unresolved transaction evidence, trading remains blocked instead of guessing whether an external mutation succeeded.

## Inspect status

Operators can use:

`/gtsv2 status`

and:

`/gtsv2 recovery`

Recovery output lists unresolved operations and their current stage.

## Resolve an operation

Before resolving anything, manually reconcile:

1. The buyer and seller AVECOINS state.
2. The Pokémon location and ownership.
3. The GTS market state.
4. Any pending seller proceeds.

Then run:

`/gtsv2 recovery resolve <operation-uuid>`

The command prints the destructive confirmation form:

`/gtsv2 recovery resolve <operation-uuid> confirm`

Resolution only records that manual reconciliation was completed. It does not automatically refund money or deliver a Pokémon.

After all unresolved operations are resolved, restart the server before normal V2 trading resumes.

## Important limitation

The GTS market, Cobblemon storage and AVECOINS do not expose one shared atomic transaction. The journal therefore preserves evidence and fails closed on uncertain outcomes rather than claiming perfect crash atomicity.
