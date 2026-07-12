# OP-Essentials — Command Reference

Every command registered by OP-Essentials 0.1-beta, grouped by category.
Generated from the command registry of the production build.

Notes:
- Arguments in `<angle brackets>` are required, `[square brackets]` optional
- Aliases are listed in the Aliases column
- Most player commands are permission-gated (`neoessentials.<command>` style
  nodes); admin commands additionally require the matching group or op level
- For the live list on your server, run `/neoe commands`

## Homes

| Command | Aliases | What it does |
|---|---|---|
| `/home [name]` | | Teleport to your home (or a named one) |
| `/sethome <name>` | `/createhome` | Set a home where you stand |
| `/delhome <name>` | `/deletehome`, `/removehome` | Delete a home |
| `/homes` | `/homelist`, `/listhomes` | List your homes |
| `/renamehome <old> <new>` | `/rhome` | Rename a home |

Home limits come from permission nodes (`neoessentials.home.<N>`), so groups
can have different caps.

## Warps and spawn

| Command | Aliases | What it does |
|---|---|---|
| `/warp <name>` | | Teleport to a warp |
| `/warps` | `/warplist`, `/listwarps` | List all warps |
| `/setwarp <name> [pos]` | `/addwarp`, `/createwarp` | Create a warp (admin) |
| `/delwarp <name>` | `/deletewarp`, `/removewarp`, `/rwarp` | Delete a warp (admin) |
| `/warpinfo <name>` | | Show a warp's location info |
| `/pwarp <name>` | | Teleport to a player warp |
| `/pwarps` | | List player warps |
| `/setpwarp <name>` / `/delpwarp <name>` | | Manage your own player warps |
| `/spawn` | `/hub` | Teleport to spawn |
| `/setspawn [pos]` | | Set the server spawn (admin) |
| `/spawninfo` | `/spawni` | Show spawn info |

## Teleportation

| Command | Aliases | What it does |
|---|---|---|
| `/tpa <player>` | | Request to teleport to a player |
| `/tpahere <player>` | | Request a player to teleport to you |
| `/tpaccept` / `/tpdeny` | | Accept / deny a pending request |
| `/tpcancel` | | Cancel your outgoing request |
| `/tpauto [on\|off]` | | Auto-accept incoming requests |
| `/back` | | Return to your previous location |
| `/rtp` | `/randomteleport`, `/randomtp`, `/tpr` | Random teleport into the wild |
| `/settpr` | | Configure the random teleport (admin) |
| `/top` | | Teleport to the surface above you |
| `/jump` | `/jumpto` | Teleport to the block you are looking at |
| `/tp <player>` | | Teleport directly (admin) |
| `/tpo <player>` | | Teleport directly, silent override (admin) |
| `/tphere <player>` | | Pull a player to you (admin) |
| `/tppos <x> <y> <z> [world]` | | Teleport to coordinates (admin) |
| `/tpall` | | Teleport everyone to you (admin) |
| `/tpoffline <player>` / `/tpohere` | | Teleport to/bring an offline player's last position (admin) |

Admin teleports are silent while the executor is vanished: no chat broadcast,
no sounds, no particles.

## Economy

| Command | Aliases | What it does |
|---|---|---|
| `/bal [player]` | `/balance`, `/money` | Show a balance |
| `/baltop [page]` | `/balancetop`, `/btop` | Richest players |
| `/pay <player> <amount>` | | Send money |
| `/paytoggle` | `/pt` | Block/allow incoming payments |
| `/eco give\|take\|set\|reset <player> <amount>` | `/economy` | Admin money management |
| `/eco history <player>` | | Transaction history (admin) |
| `/sell hand\|all\|inventory` | | Sell items for their configured worth |
| `/worth [hand\|item]` | | Show an item's sell value |
| `/vault` | | Vault access / conversion utilities |

## Kits

| Command | Aliases | What it does |
|---|---|---|
| `/kit <name> [player]` | | Claim a kit (or give one, admin) |
| `/listkits` | `/kits` | List available kits |
| `/createkit <name> [cooldown]` | `/addkit`, `/makekit` | Create a kit from your inventory (admin) |
| `/delkit <name>` | `/deletekit`, `/removekit`, `/rkit` | Delete a kit (admin) |
| `/kitreset <name> [player]` | | Reset a kit cooldown (admin) |

A first-join starter kit can be configured in `kits.json`.

## Moderation

| Command | Aliases | What it does |
|---|---|---|
| `/ban <player> [reason]` | | Permanent ban |
| `/tempban <player> <duration> [reason]` | | Timed ban (e.g. `1d`, `12h`) |
| `/banip <player\|ip>` / `/tempbanip` | | IP bans |
| `/unban <player>` / `/unbanip <ip>` | | Lift bans |
| `/banlist [ips]` | | List bans |
| `/kick <player> [reason]` | | Kick a player |
| `/kickall [reason]` | | Kick everyone (admin) |
| `/mute <player> [reason]` | `/silence` | Mute chat |
| `/unmute <player>` | | Unmute |
| `/mutelist` | | List muted players |
| `/freeze <player> [reason]` / `/unfreeze` | | Stop a player from moving/interacting |
| `/freezeall [reason]` / `/unfreezeall` | | Freeze / release the whole server |
| `/freezelist` | | List frozen players |
| `/jail <player> [duration] [reason]` | `/jailfor` | Send a player to jail |
| `/unjail <player>` | | Release from jail |
| `/setjail <name>` / `/deljail <name>` | | Create / remove a jail location |
| `/jails` | `/jaillist` | List jails |
| `/jailinfo <player>` | | Why/how long someone is jailed |
| `/togglejail <player>` | | Toggle jail state |
| `/vanish` | `/v` | Toggle vanish (see README for what vanish covers) |
| `/unvanish` | | Explicit unvanish |
| `/vanishlist` | | List vanished players |
| `/invsee <player>` | `/inv` | View a player's inventory (GUI) |
| `/invseeedit <player>` | | Edit a player's inventory (GUI) |
| `/enderchest <player>` | `/ec` | View a player's ender chest |
| `/ecedit <player>` | `/enderchestedit` | Edit a player's ender chest |
| `/clearinventory <player>` | `/ci`, `/clearinv` | Wipe a player's inventory |
| `/whois <player>` | | Full player report |
| `/seen <player>` | | Last seen / first joined info |
| `/near [radius]` | `/nearby` | Players near you |
| `/realname <nick>` | | Resolve a nickname to the account name |
| `/sudo <player> <command>` | | Run a command as another player |

## Chat and social

| Command | Aliases | What it does |
|---|---|---|
| `/msg <player> <message>` | `/tell`, `/pm` | Private message |
| `/reply <message>` | `/r` | Reply to the last message |
| `/msgtoggle` | `/mt`, `/togglemsg` | Block/allow private messages |
| `/ignore <player>` | `/block` | Ignore a player |
| `/unignore <player>` | `/unblock` | Stop ignoring |
| `/socialspy` | `/spy`, `/ss` | See private messages (admin) |
| `/helpop <message>` | `/adminhelp`, `/request` | Message online staff |
| `/mail read\|send\|clear` | | Player mail, works offline |
| `/mail sendall <message>` | | Mail every player (admin) |
| `/nick <name>` | `/nickname`, `/setnick` | Set a nickname (color perms apply) |
| `/afk [message]` | `/away` | Toggle AFK status |
| `/motd` | | Show the message of the day |
| `/rules [page]` | | Show the server rules |
| `/ping [player]` | | Show latency |
| `/playtime [player]` | | Show play time |
| `/suicide` | `/die` | The easy way out |

## Player state and utility

| Command | Aliases | What it does |
|---|---|---|
| `/heal [player]` | | Restore health |
| `/feed [player]` | | Restore hunger |
| `/fly [player] [on\|off]` | | Toggle flight |
| `/god [player]` | | Toggle invulnerability |
| `/speed <walk\|fly> <amount> [player]` | | Change movement speed |
| `/ext [player]` | `/extinguish` | Stop burning |
| `/burn <player> <seconds>` | | Set a player on fire (admin) |
| `/hat` | | Wear the item you are holding |
| `/repair [hand\|all]` | `/fix` | Repair items |
| `/more` | | Fill the held stack to max |
| `/give <player> <item> [amount]` | | Give items (admin) |
| `/exp give\|set\|show` | `/xp` | Manage experience (admin) |
| `/enchant <enchantment> <level>` | `/ench` | Enchant the held item |
| `/ptime <day\|night\|reset>` | | Personal time — only you see it |
| `/pweather <clear\|reset>` | | Personal weather — only you see it |
| `/getpos [player]` | `/coords`, `/whereami`, `/pos` | Show exact coordinates |
| `/depth` | | Depth relative to sea level |
| `/compass` | `/bearing`, `/direction` | Show facing direction |
| `/dispose` | `/trash` | Open a disposal bin |
| `/condense` | | Compact items into blocks |
| `/powertool <command>` | `/ptool`, `/ptt` | Bind a command to the held item |
| `/powertoollist` | `/ptlist` | List powertool bindings |
| `/itemname <name>` | `/rename` | Rename the held item |
| `/itemlore <line> <text>` | | Edit held item lore |
| `/skull [player]` | | Get a player head |
| `/sign <line> <text>` | | Edit a sign you are looking at |
| `/book <title\|author\|unlock>` | | Edit book meta |

## Virtual workbenches

| Command | Aliases | What it does |
|---|---|---|
| `/craft` | `/crafting`, `/workbench` | Open a crafting table |
| `/anvil` | | Open an anvil |
| `/grindstone` | | Open a grindstone |
| `/smithing` | | Open a smithing table |
| `/stonecutter` | `/stonecut`, `/stonecutting` | Open a stonecutter |

## World and admin

| Command | Aliases | What it does |
|---|---|---|
| `/broadcast <message>` | `/bc`, `/announce` | Server-wide announcement |
| `/broadcastworld <message>` | `/bcastworld` | Announcement in one world |
| `/time <day\|noon\|night\|midnight\|...>` | | Named time presets (also sunrise, dusk, morning...) |
| `/weather <sun\|rain\|storm>` | | Weather control |
| `/gamemode <mode> [player]` | `/gms`, `/gmc`, `/gma`, `/gmsp` | Change gamemode |
| `/kill <target>` | | Kill a player/entity (admin) |
| `/clear <player>` | | Clear inventory (admin) |
| `/spawnmob <mob> [amount]` | | Spawn mobs (admin) |
| `/spawner <mob>` | | Change the spawner you are looking at (admin) |
| `/effect <effect> [duration] [amplifier]` | | Apply status effects (admin) |
| `/list` | `/online`, `/who` | Who is online |
| `/tree <type>` | | Grow a tree where you look |
| `/ride <entity>` | | Ride the entity you are looking at |
| `/lightning [player]` | `/smite` | Strike lightning |
| `/expbottle <amount>` | | Throw XP bottles |

## Fun

| Command | What it does |
|---|---|
| `/nuke [player]` | Classic Essentials chaos, use responsibly |
| `/kittycannon` | Launches an exploding kitten. Yes, really |
| `/antioch` | Holy hand grenade |
| `/beezooka` | Launches an angry bee |
| `/firework <options>` | Build and launch custom fireworks |

## System

| Command | What it does |
|---|---|
| `/neoe reload` | Reload every system live (configs, perms, tablist, kits...) |
| `/neoe commands` | List all registered commands |
| `/permissions ...` | `/pex` — full group/user management: create, delete, setgroup, setprefix, setsuffix, inherit, check, search, reload |
| `/tablist reload\|preview\|enable\|disable` | Manage the tab list |
| `/language <lang>` | Localisation management |
| `/dashboard start\|stop\|status` | Web dashboard control |
| `/dashboardregister` | Register a dashboard account |

---

Some niche aliases and subcommands may be missing here — the registry is big.
`/neoe commands` in-game is always the authoritative list.
