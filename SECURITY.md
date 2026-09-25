# Security Policy

## Supported versions

Zian GTS is currently in beta. Security fixes are provided for the latest published beta or stable release.

| Version | Supported |
| --- | --- |
| 1.0.0-beta.1 | Yes |
| Older development builds | No |

## Reporting a vulnerability

Please do not publish security-sensitive reports as public GitHub issues when they could enable duplication, unauthorized currency changes, Pokémon loss/theft, transaction replay, or recovery bypasses.

Report the issue privately to the project maintainer through GitHub's private vulnerability reporting feature when available. If private reporting is unavailable, contact the maintainer before disclosing exploit details publicly.

Include:

- Zian GTS version and JAR filename
- Minecraft, NeoForge/Youer, Cobblemon and AVECOINS versions
- server/client logs around the incident
- exact reproduction steps
- whether the server restarted or crashed
- relevant transaction/recovery identifiers
- whether Pokémon or currency were duplicated, lost, charged, credited, or delivered
- whether the issue reproduces on a clean test server

Do not repeatedly exploit a duplication or economy issue on a production server. Preserve logs and the Zian GTS data/journal files before attempting recovery.

## Transaction safety

Zian GTS coordinates state across Minecraft, Cobblemon and external economy storage. These systems do not provide one shared atomic transaction. The mod therefore uses durable journaling and conservative recovery behavior. An uncertain external mutation is not automatically repeated.

Administrators should investigate unresolved recovery entries before manually reconciling them.
