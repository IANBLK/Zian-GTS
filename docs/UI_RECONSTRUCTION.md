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

For Zian GTS, the requested central-detail cleanup is explicit:

**Remove only Nature and Ability from the central tooltip/details area.**

Keep seller, price, level, gender, Shiny, statistics and the rest of the GTS
functionality. The new implementation should use translation components rather
than hard-coded English strings.

This document exists to prevent a later UI rewrite from accidentally deleting
more information than requested.
