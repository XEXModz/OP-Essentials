# OP-Essentials

![Version](https://img.shields.io/badge/version-0.1--beta-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-orange)
![Side](https://img.shields.io/badge/side-server--only-purple)

Essentials-style server utilities for NeoForge: vanish, permission groups, homes,
warps, teleports, moderation, chat formatting, kits and more. Server-side only —
players connect with a completely vanilla client.

Everything in here exists because our own server needed it. The mod runs in
production on a public All The Mods 10 server, which is where most of the bug
fixes in the changelog come from.

## Features

### Vanish
- Removes you from the tab list, entity tracking and minimaps — no footsteps,
  no sounds, no radar dot
- Staff can always see other vanished staff (priority-based, no toggles needed)
- Optional fake leave/join messages on vanish/unvanish
- Teleports while vanished are silent: no chat broadcast, no sound, no particles
- Handles the login race, so players who join while you are vanished still
  cannot see you

### Permissions and groups
- Built-in permission engine: groups with inheritance, prefixes/suffixes,
  per-user overrides
- Dynamic nodes, e.g. `neoessentials.home.5` grants 5 homes (highest node wins)
- Live reload: edit `permissions.json`, run `/permissions reload`, done
- FTB Ranks adapter for modpack integrations

### Teleportation
- Homes with per-group limits, warps, `/spawn`, `/back`, `/rtp`, `/top`, `/jump`
- TPA requests with accept/deny, plus silent admin teleports
  (`/tpo`, `/tphere`, `/tpall`, `/tpoffline`)
- Teleport sounds and particles for players; staff move silently

### Moderation
- Ban, tempban with durations, IP bans, kick, kickall — all with reasons
- Mute/unmute, mute list
- Freeze single players or the whole server (`/freezeall`)
- Jails: `/setjail`, `/jail <player> [duration]`, `/jailinfo`, `/jaillist`
- `/invsee` and `/enderchest` inventory GUIs (view or edit)
- `/whois`, `/seen`, `/near`, `/realname`, `/sudo`

### Chat
- Group prefixes, colored/hex/rich-text chat behind permissions, badges, channels
- Private messages, replies, ignore, social spy, helpop
- AFK detection with tab indicator; broadcasts and auto-kick are optional and
  fully configurable
- Tablist header/footer with placeholders (TPS, ping, balance, world), static
  or animated

### Utility
- Kits with cooldowns and a first-join starter kit, created in-game
- Virtual workbenches: `/craft`, `/anvil`, `/grindstone`, `/smithing`, `/stonecutter`
- `/hat`, `/repair`, `/heal`, `/feed`, `/ext`, `/speed`, `/ptime`, `/pweather`, `/nick`
- Mail with offline delivery and send-to-all, MOTD, rules, item customisation
- Economy commands, with bridging to external economy mods where present

### Operations
- Split JSON configs under `config/neoessentials/`, one file per system
- `neoe reload` reloads every system live
- Player data persists per-change, so a hard kill cannot lose homes or settings

## Installation

Put the jar in the server's `mods/` folder. There is no client component.
Configs generate under `config/neoessentials/` on first start.

## Building

The project is currently maintained by surgical patching rather than a full
gradle build — see [BUILDING.md](BUILDING.md). The `patches/` directory contains
the per-version change history.

## Lineage and credits

OP-Essentials started as a fork of NeoEssentials by ZeroG Network and has since
diverged heavily (see [CHANGELOG.md](CHANGELOG.md)). Internal ids still use the
`neoessentials` namespace; the full rename will land before any public release.

Note: before this repository goes public, the upstream license needs to be
reviewed, since parts of the codebase derive from the original NeoEssentials
sources.
