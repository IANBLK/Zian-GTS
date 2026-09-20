# UI reconstruction notes

The reference `PokemonTooltip` was inspected at bytecode level.

Confirmed visible fields in the reference tooltip:
- Seller
- Price
- Nature
- Ability
- Level
- Gender
- Shiny
- Stats / IV values

The current Zian GTS detail panel includes Nature and Ability again, following
the later UI requirement. Their values use Cobblemon's translation keys. Seller,
price, level, gender, Shiny, Alpha, statistics and IV values remain visible; EVs
are intentionally omitted.

This document exists to prevent a later UI rewrite from accidentally deleting
more information than requested.
