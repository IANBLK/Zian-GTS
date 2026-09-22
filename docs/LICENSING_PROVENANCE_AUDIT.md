# Licensing and provenance audit

Date: 2026-09-22
Audited branch: `feat/durable-journal`
Audit branch: `audit/licensing-provenance`

## Executive conclusion

Zian GTS contains a substantial body of new Zian-specific engineering, but the current tree should **not yet be represented as a wholly original, independently created mod**.

The reconstruction history explicitly records bytecode-level inspection of `cobblegts-neoforge-1.7.0-1.5.2.jar`, including its architecture, listing model and UI fields. The public CurseForge project currently labels cobble-gts by xpointfive as **All Rights Reserved**, while the local reconstruction notes state that the inspected binary declared MIT. Those two signals conflict.

Until that conflict is resolved by an authoritative license grant or the affected baseline is independently reimplemented, public release should be treated as a provenance blocker rather than solved by renaming packages or choosing a new license.

## Desired Zian license

For code that is original to Zian GTS, MIT is the recommended project license. It allows use, modification, forks, redistribution, sublicensing and commercial use while requiring preservation of the copyright/license notice.

The repository already uses MIT. This audit does not attempt to relicense any third-party code.

## Evidence reviewed

- Complete current repository tree on `feat/durable-journal`.
- Git history from bootstrap through commit `85118726838b05908397b8f71804cc1c547d9e6c`.
- `LICENSE`, `THIRD_PARTY_NOTICES.md`, `README.md`.
- `docs/RECONSTRUCTION.md` and `docs/UI_RECONSTRUCTION.md`.
- All production Kotlin packages: bootstrap, client UI, config, data, economy, history, networking and server transaction/recovery code.
- Existing tests and crash-journal work.
- Public CurseForge metadata for project 1324991 / file 7342604.

## License conflict found

Repository documentation currently says the inspected Cobble GTS binary declares MIT.

However, the current public CurseForge project metadata for cobble-gts identifies its license as All Rights Reserved. CurseForge's own submission guidance requires forks/remakes to comply with the original project's license and to credit/link the original when a fork is permitted.

This audit cannot determine from repository evidence alone whether:
1. the distributed 1.5.2 binary contained a legally effective MIT grant that applies to the reconstructed portions;
2. CurseForge's ARR setting was changed later;
3. the embedded declaration and platform license conflict; or
4. separate permission exists.

Therefore the upstream permission status is **unresolved**.

## Provenance classification

### Category A — strong evidence of Zian-original implementation

These components were introduced for Zian-specific requirements and/or have no corresponding component listed in the recorded upstream architecture:

- `server/TransactionJournal.kt`
- `server/GtsJournal.kt`
- durable WAL, checksums, hard-crash probes and recovery commands
- `server/PurchaseFinalization.kt`
- `data/TransactionRecord.kt`
- `history/TransactionHistory.kt`
- `history/TransactionHistoryData.kt`
- AVECOINS abstraction/integration:
  - `economy/AvecoinsCatalog.kt`
  - `economy/AvecoinsWalletProvider.kt`
  - `economy/Economies.kt`
  - `economy/EconomyKey.kt`
  - `economy/EconomyProvider.kt`
- recovery incident preservation and administrator resolution/audit
- LuckPerms permission integration
- crash/restart tests added during durable-journal development
- Zian-specific translations added for new features
- `/gts mylistings`, expiry countdown and owned-listing autocomplete as later Zian additions
- current IV radar implementation and later AVECOINS-inspired visual work, subject to the separate AVECOINS note below.

These files/features may still call public Minecraft/Cobblemon/NeoForge APIs; that is dependency interoperability, not evidence that their implementation came from Cobble GTS.

### Category B — provenance-sensitive baseline, reimplement or obtain permission

The reconstruction documents explicitly say the reference binary was inspected for these concepts/classes. They should not be marketed as independently created until source-level similarity is ruled out or permission is established:

- `data/Listing.kt`
- baseline portions of `data/ListingsData.kt`
- baseline `server/GtsCommands.kt`
- baseline `network/GtsNetwork.kt`
- baseline `client/GtsScreen.kt`
- baseline sell/buy/cancel/claim flow in `server/GtsService.kt`
- baseline configuration defaults/shape in `config/GtsConfig.kt` and `config/GtsSettings.kt`
- user-facing tooltip/detail field selection reconstructed from upstream bytecode
- command vocabulary and baseline market behavior reconstructed from the reference.

The fact that these files have since received substantial Zian changes does not erase provenance of any upstream-derived expression that may remain.

### Category C — generic/project infrastructure

Generally low provenance concern, but still subject to dependency licenses and trademarks:

- Gradle wrapper/build configuration
- GitHub Actions workflow
- NeoForge mod metadata
- pack metadata
- bootstrap registration in `ZianGts.kt`
- tests written specifically for Zian behavior
- documentation authored for this repository.

### Category D — third-party interoperability

#### Cobblemon
Zian GTS depends on Cobblemon and uses its public/runtime APIs and types. Cobblemon itself is not redistributed as Zian-owned code by this repository.

#### NeoForge / KotlinForForge / Gson / JUnit / LuckPerms
Normal build/runtime/test dependencies. Their licenses remain their own.

#### AVECOINS
AVECOINS 2.3 is separately installed and is not bundled. Zian uses reflective calls to its runtime classes. The current UI also documents that it reproduces AVECOINS palette values locally. Before public release, review AVECOINS branding/trade-dress/assets separately and avoid implying that AVECOINS code or assets are part of Zian GTS.

## What the audit does NOT prove

This is a provenance audit of the repository and its recorded reconstruction process. It is not a byte-for-byte or AST comparison against the original Cobble GTS implementation because the authoritative upstream source is not present in this repository and the inspected artifact was a compiled binary.

Accordingly, this audit cannot certify that Category B contains zero protectable expression from the reference binary.

## Publication decision

### Safe today
- Continue private development and testing.
- License clearly original Zian contributions under MIT.
- Keep attribution/provenance records.
- Continue using third-party dependencies according to their licenses.

### Not recommended today
- Claim that the entire current tree is 100% original Zian code.
- Remove xpointfive/Cobble GTS provenance records merely to make the project look independent.
- Upload the current tree to CurseForge as an independently created derivative without resolving the upstream license conflict.

## Two valid release paths

### Path 1 — permission / license confirmation
Obtain an authoritative permission or license statement from xpointfive covering modification, reconstruction, redistribution and relicensing requirements for the relevant Cobble GTS version. Preserve attribution/notices required by that grant. This is the shortest path if permission is available.

### Path 2 — clean-room replacement
Keep the requirements and externally observable behavior, but replace Category B through an independent implementation that does not consult decompiled/bytecode implementation details while writing the replacement. Retain Zian-original Category A components. Have the replacement reviewed for structural/code similarity before release.

For the strongest provenance record, one person can write a behavior/specification document from public behavior and existing Zian requirements, while the implementation is produced from that specification without consulting the reference bytecode.

## Recommended release posture

Target license: **MIT** for original Zian GTS code.

Public description should be factual. If the final release still contains legitimately licensed upstream-derived portions, identify it as a fork/continuation and credit the upstream project as required. If Category B is clean-room replaced, describe Zian GTS as an independent GTS implementation and keep historical provenance documentation internally/publicly as appropriate; do not claim authorship of third-party work.

## Release gate

Do not mark the provenance audit PASS until one of these is true:

- [ ] Authoritative upstream permission/license is documented and compatible with distribution; or
- [ ] Every Category B component has been independently replaced and reviewed.

Then additionally require:

- [ ] MIT scope and copyright notices are accurate.
- [ ] THIRD_PARTY_NOTICES matches verified facts.
- [ ] No upstream assets are packaged.
- [ ] No misleading upstream/AVECOINS branding.
- [ ] CurseForge description is original and credits any remaining licensed upstream relationship.
- [ ] Release JAR contains the intended license/notices.
- [ ] Build/tests pass from the release commit.

Current audit status: **HOLD FOR PUBLIC RELEASE — provenance/license conflict unresolved.**
