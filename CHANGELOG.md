# Changelog

## Unreleased (v0.2)
- Fixed `/tpo` never running the teleport override: it was registered twice
  (DirectTeleportCommands and ServerAdminCommands), Brigadier merged the nodes and
  the first-registered offline-location variant shadowed the online override.
  Removed the duplicate — `/tpo` is now the online teleport override only; use
  `/tpoffline` for last-known-location teleports (it already handled both cases).
  Note: `/tpo` now checks `neoessentials.teleport.tpo` (the merged node previously
  kept `neoessentials.teleport.admin.tpo` from the shadowing registration).

## v0.1-beta — the OP-Essentials rebrand (2026-07-12)
Identical code to lineage release `neoessentials-1.1.12-beta` — the version running
in production. From here the mod is its own project: **OP-Essentials**.

### Known issues (queued for v0.2)
- `/god` should be removed (collides with FTB Essentials' `/god` when both installed)
- Boot banner prints a stale hardcoded version string
- Full `neoessentials` → `opessentials` id/package rename pending

---

## Lineage (as NeoEssentials fork, private releases)

### 1.1.12-beta
- Staff ALWAYS see vanished staff — `isStaffViewer` (priority ≤ 3) applied across
  VanishManager; Owner/Co-Owner/Admin mutually visible while vanished, automatic.

### 1.1.11-beta
- Fixed co-owner vanish leak: `getPlayerPriority` rank table now covers custom staff
  group names (owner 0, co-owner 1, moderator/admin 2); unknown groups default to 10.

### 1.1.10-beta
- **True-invisibility vanish**: entity untracked client-side (no minimap dot, no sounds)
- Silent staff teleports: TP sounds/particles gated to non-staff
- Teleport broadcasts silenced while executor is vanished (`shouldBroadcast`)

### 1.1.9-beta
- Tablist joined the live-reload family (`neoe reload` system #11)
- Static header/footer supported — fixed the "dancing tab" frame-width jitter

### 1.1.8-beta
- Fixed remaining 17 `getConfig("chat")` mis-reads across 7 classes — chat pipeline,
  badges & rich text now actually honor `chat.json`. First fully clean boot.

### 1.1.7-beta
- First-join starter kits fixed (split-config settings block)
- Vanish fake leave/join broadcasts
- Tab footer balance reads external economy (EconomyCraft) via reflection
- Homes persist per-change (crash-proof)

### 1.1.6-beta
- Chat colors fixed: style-run walker re-emits legacy codes (`styleToLegacyCodes`)
- Vanish tab re-hide after other players relog (+2-tick scheduling)

### 1.1.5 and earlier
See `patches/CHANGES-1.1.4-to-1.1.5.diff` — Gson hardening (~40 files), atomic pay
cooldowns, FTB Ranks adapter hardening, admin command rework.
