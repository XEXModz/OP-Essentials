# ⚡ OP-Essentials

![Version](https://img.shields.io/badge/version-0.1--beta-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-orange)
![Side](https://img.shields.io/badge/side-server--only-purple)
![Status](https://img.shields.io/badge/status-private%20beta-red)

**The all-in-one essentials mod for NeoForge servers — built for real SMPs, battle-tested on a live All The Mods 10 server.**

Every feature in this mod exists because a real server needed it. Vanish that actually
makes you invisible. Permission groups that actually apply. A tablist that doesn't dance.

---

## ✨ Features

### 👻 Vanish that actually works
- **True invisibility** — removed from tab, radar/minimap, entity tracking; no footsteps, no sounds
- **Staff always see vanished staff** — priority-based (Owner ⟷ Co-Owner ⟷ Admin), automatic, no toggles
- **Fake leave/join messages** on vanish/unvanish — players believe you logged off
- **Silent teleports while vanished** — no chat broadcast, no enderman sound, no particles
- Join-race protected: players who log in while you're vanished still can't see you

### 🧑‍🤝‍🧑 Permissions & groups
- Built-in permission engine — groups with inheritance, prefixes/suffixes, per-user overrides
- Dynamic permission nodes (`home.5` = 5 homes, highest node wins)
- Live reload: edit `permissions.json` → `/permissions reload` — no restart
- FTB Ranks adapter for pack-side integrations

### 🏠 Teleportation suite
- Homes (per-group limits), warps, `/spawn`, `/back`, `/rtp`, `/top`, `/jump`
- TPA requests (`/tpa`, `/tpahere`, accept/deny) + silent admin TPs (`/tpo`, `/tphere`, `/tpall`, `/tpoffline`)
- Teleport effects (sounds/particles) for players, silent for staff

### 🛡️ Moderation
- Ban / tempban (durations) / ban-ip / kick / kickall — with reasons
- Mute & unmute with reasons, mutelist
- **Freeze** individual players or `/freezeall` the whole server
- **Jail system** — `/setjail`, `/jail <player> [duration]`, `/jailinfo`, `/jaillist`
- `/invsee` + `/enderchest` — live inventory GUIs (view or edit)
- `/whois`, `/seen`, `/near`, `/realname`, `/sudo`

### 💬 Chat
- Group prefixes, colored/hex/rich-text chat (permission-gated), badges, channels
- `/msg`, `/reply`, `/ignore`, social spy, helpop
- AFK system: auto-detect, tab indicator, optional broadcasts & kick — all configurable
- Animated or static tablist header/footer with placeholders (TPS, ping, balance, world)

### 🧰 Utility & QoL
- Kits (create in-game, cooldowns, first-join starter kit)
- Virtual workbenches: `/craft`, `/anvil`, `/grindstone`, `/smithing`, `/stonecutter`
- `/hat`, `/repair`, `/heal`, `/feed`, `/ext`, `/speed`, `/ptime`, `/pweather`, `/nick`
- Mail (incl. offline delivery + `sendall`), MOTD, rules, item customisation
- Economy commands with external-economy bridging (EconomyCraft-aware tablist balance)

### ⚙️ Ops-friendly by design
- Split JSON configs (`config/neoessentials/*.json`) — one file per system
- `neoe reload` reloads every system live
- Per-change persistence — force-kills can't eat your homes

---

## 📦 Installation

Drop the jar in `mods/` on the **server**. That's it — server-side only,
vanilla clients connect fine. Configs generate under `config/neoessentials/` on first boot.

## 🔨 Building

This project is currently maintained via **surgical patching** — see [BUILDING.md](BUILDING.md).
`patches/` holds the per-version change history from the NeoEssentials lineage.

## 📜 Lineage & credits

OP-Essentials began as a fork of **NeoEssentials** (ZeroG Network) and has diverged
heavily — 1,300+ commits and 8 private patch releases of fixes and new systems
(see [CHANGELOG.md](CHANGELOG.md)). Internal ids still use `neoessentials`
namespaces; the full rename ships before any public release.

> ⚠️ **Before going public:** upstream license review required — portions of this
> codebase derive from the original NeoEssentials sources.

---

*Built with ❤️ (and a lot of 3am debugging) for the ATMOP Networks ATM10 server.*
