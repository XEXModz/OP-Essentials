package com.zerog.neoessentials.api.permissions;

import com.zerog.neoessentials.permissions.LuckPermsAdapter;
import java.time.LocalDateTime;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionRegistry.class);
   private final Set<String> registeredPermissions = ConcurrentHashMap.newKeySet();
   private final Map<String, PermissionRegistry.PermissionInfo> permissionInfo = new ConcurrentHashMap<>();

   public static PermissionRegistry getInstance() {
      return PermissionRegistry.SingletonHolder.INSTANCE;
   }

   private PermissionRegistry() {
      this.registerAllPermissions();
      this.autoDiscoverPermissions();
   }

   public void register(String permission, String description, PermissionRegistry.PermissionCategory category, boolean defaultValue) {
      if (permission != null && !permission.trim().isEmpty()) {
         permission = permission.trim();
         if (!this.isValidPermission(permission)) {
            LOGGER.warn("Invalid permission format: {}", permission);
         } else {
            this.registeredPermissions.add(permission);
            this.permissionInfo.put(permission, new PermissionRegistry.PermissionInfo(permission, description, category, defaultValue));
            LOGGER.debug("Registered permission: {} ({})", permission, category.getKey());
         }
      } else {
         LOGGER.warn("Attempted to register empty or null permission");
      }
   }

   public void register(String permission, String description, PermissionRegistry.PermissionCategory category) {
      this.register(permission, description, category, false);
   }

   public void register(String permission) {
      this.register(permission, "Permission for " + permission, PermissionRegistry.PermissionCategory.MISC, false);
   }

   public Set<String> getAllPermissions() {
      return Collections.unmodifiableSet(this.registeredPermissions);
   }

   public Set<String> getPermissionsByCategory(PermissionRegistry.PermissionCategory category) {
      return this.permissionInfo
         .values()
         .stream()
         .filter(info -> info.getCategory() == category)
         .map(PermissionRegistry.PermissionInfo::getPermission)
         .collect(HashSet::new, HashSet::add, AbstractCollection::addAll);
   }

   public PermissionRegistry.PermissionInfo getPermissionInfo(String permission) {
      return this.permissionInfo.get(permission);
   }

   public boolean isRegistered(String permission) {
      return this.registeredPermissions.contains(permission);
   }

   public List<String> getPermissionsStartingWith(String prefix) {
      return this.registeredPermissions.stream().filter(perm -> perm.startsWith(prefix.toLowerCase())).sorted().toList();
   }

   public List<String> getNeoEssentialsPermissions() {
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allNeoEssentialsPermissions = new HashSet<>(this.getPermissionsStartingWith("neoessentials."));
      allNeoEssentialsPermissions.addAll(scanner.getDiscoveredPermissions().stream().filter(perm -> perm.startsWith("neoessentials.")).toList());
      return allNeoEssentialsPermissions.stream().sorted().toList();
   }

   private boolean isValidPermission(String permission) {
      return permission.matches("^[a-z0-9._-]+$") && permission.startsWith("neoessentials.");
   }

   private void registerAllPermissions() {
      LOGGER.info("Registering NeoEssentials permission nodes...");
      this.register("neoessentials.use", "Basic mod usage", PermissionRegistry.PermissionCategory.CORE, true);
      this.register("neoessentials.admin", "Administrative access", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.reload", "Reload configuration", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.economy.balance", "Check own balance", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.economy.balance.others", "Check others' balance", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.pay", "Send payments to online players", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.economy.pay.offline", "Send payments to offline players", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.pay.toggle", "Toggle payment acceptance", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.economy.baltop", "View balance leaderboard", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.economy.baltop.exempt", "Exclude self from baltop ranking", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.admin", "Economy administration", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.admin.give", "Give money to players", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.admin.take", "Take money from players", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.eco", "Run /eco admin commands (give/take/set/reset)", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.economy.admin.set", "Set player balance", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.worth", "Check the sell value of an item (/worth)", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.sell", "Use the /sell command", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.sell.hand", "Sell item in hand (/sell hand)", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.sell.bulk", "Sell entire inventory (/sell inventory|all)", PermissionRegistry.PermissionCategory.ECONOMY, true);
      this.register("neoessentials.setworth", "Set item sell prices (/setworth)", PermissionRegistry.PermissionCategory.ECONOMY, false);
      this.register("neoessentials.fly", "Toggle flight mode", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.fly.others", "Toggle flight for other players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.god", "Toggle god mode (invincibility)", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.god.others", "Toggle god mode for other players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.heal", "Restore own health and hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.heal.others", "Restore another player's health", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.feed", "Restore own hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.feed.others", "Restore another player's hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.speed", "Set walk/fly speed", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.speed.others", "Set another player's speed", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.ext", "Extinguish self", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.ext.others", "Extinguish another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.burn", "Set a player on fire", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.give", "Give items to players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.more", "Fill held stack to max", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.hat", "Wear held item as helmet", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp", "View XP info", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.exp.set", "Set own XP", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.set.others", "Set another player's XP", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.give", "Give XP to self", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.give.others", "Give XP to another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.sudo", "Run commands as another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.playtime", "View own playtime", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.playtime.others", "View another player's playtime", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.broadcast", "Broadcast a message to all players", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.time", "View current world time", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.time.set", "Set or add world time", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.weather", "Set world weather", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.kill", "Kill players", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.kill.exempt", "Exempt from being killed by /kill", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.kill.force", "Force kill even exempt players", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.gamemode", "Change own gamemode", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.gamemode.others", "Change another player's gamemode", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.teleport.tpo", "Teleport to player ignoring tptoggle", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.teleport.tpohere", "Bring player here ignoring tptoggle", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.teleport.tpoffline", "Teleport to offline player's last location", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.ptime", "Set own per-player time override", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.ptime.others", "Set another player's time override", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.pweather", "Set own per-player weather override", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.pweather.others", "Set another player's weather override", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.effect", "Apply potion effects to players", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.spawnmob", "Spawn entities at a player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.spawnmob.others", "Spawn entities at another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.unlimited", "Toggle unlimited item use", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.unlimited.others", "Toggle unlimited items for another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.condense", "Condense items to storage blocks", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.me", "Broadcast action messages (/me)", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.tptoggle", "Toggle teleport request acceptance", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.tptoggle.others", "Toggle tptoggle for another player", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.gc", "View server memory and TPS info", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.lightning", "Strike lightning at look target", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.lightning.others", "Strike lightning at a named player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.skull", "Get a player head item", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.itemname", "Rename held item", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.itemlore", "Edit held item lore", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.remove", "Remove entities in a radius", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.loom", "Open portable loom", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.cartography", "Open portable cartography table", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.renamehome", "Rename own homes", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.renamehome.others", "Rename another player's homes", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.warpinfo", "Show warp location info", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.world", "Teleport to a world/dimension", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.world.others", "Teleport another player to a world", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.spawner", "Change a mob spawner type", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.spawner.*", "Change spawner to any mob type", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.recipe", "Show/unlock crafting recipe for an item", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.tpauto", "Auto-accept all incoming teleport requests", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.firework", "Edit held firework rockets", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.nuke", "Rain TNT on a player (/nuke)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.beezooka", "Launch angry bees (/beezooka)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.itemdb", "Look up item registry information (/itemdb)", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.potion", "Edit potion effects on held potion item", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.info", "View server info/MOTD (/info)", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rest.others", "Reset another player's sleep timer", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.backup", "Trigger server world save and backup", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.tpauto.others", "Toggle tpauto for another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.fireball", "Shoot projectiles", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.fireball.*", "Shoot any projectile type", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.fireball.ride", "Ride a shot projectile", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.tree", "Grow a tree at look target", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.break", "Break the looked-at block instantly", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.break.bedrock", "Break bedrock blocks", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.ice", "Freeze self with ice", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.ice.others", "Freeze another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.bottom", "Teleport to world bottom at current XZ", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.tpaall", "Send tpa-here to all online players", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.tpaall.others", "Send tpaall on behalf of another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.broadcastworld", "Broadcast to players in your current world", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.seen", "View when a player was last online", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.near", "List nearby players", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.ping", "View your ping", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.ping.others", "View another player's ping", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.playtime", "View your total play time", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.playtime.others", "View another player's play time", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.whois", "View detailed player info", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.realname", "Look up real name from nickname", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.sudo", "Force a player to run a command", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.sudo.exempt", "Be immune to /sudo", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.suicide", "Kill yourself with /suicide", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.msgtoggle", "Toggle your incoming private messages", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.msgtoggle.others", "Toggle another player's messages", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.rtoggle", "Toggle reply-to-last-sender", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rtoggle.others", "Toggle rtoggle for another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.motd", "View the message of the day", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rules", "View server rules", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.teleport.admin", "Administrative teleportation", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.admin.tp", "Teleport players", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.admin.tphere", "Teleport players to you", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.admin.tpall", "Teleport all players", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.admin.tppos", "Teleport to coordinates", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.request.tpa", "Send teleport requests", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.request.tpahere", "Request players teleport to you", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.request.accept", "Accept teleport requests", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.request.deny", "Deny teleport requests", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.request.cancel", "Cancel sent teleport requests", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.home", "Use home system", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.home.set", "Set home locations", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.home.delete", "Delete home locations", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.home.list", "List home locations", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.home.others", "Access others' homes", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.warp", "Use warp system", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.warp.list", "List all available warps", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register(
         "neoessentials.teleport.warp.others", "Warp another player to a warp (/warp <name> <player>)", PermissionRegistry.PermissionCategory.TELEPORT, false
      );
      this.register("neoessentials.teleport.warp.create", "Create warps", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.warp.delete", "Delete warps", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.warps.*", "Access ALL warps regardless of per-warp permissions", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.warp.limit.unlimited", "Unlimited player warps", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.spawn", "Use spawn teleportation", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.spawn.set", "Set spawn location", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.spawn.info", "View spawn information", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.spawn.clear", "Clear spawn location", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.back", "Use back teleportation", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.death", "Teleport to death location", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.top", "Teleport to highest block", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.jump", "Teleport through walls", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.jumpto", "Teleport to looking at", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.tpr", "Random teleportation", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.admin.tpo", "Teleport other players to locations", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.kits.use", "Use kit system", PermissionRegistry.PermissionCategory.KITS, true);
      this.register("neoessentials.kits.list", "List available kits", PermissionRegistry.PermissionCategory.KITS, true);
      this.register("neoessentials.kits.nocooldown", "Bypass kit cooldowns", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kit.others", "Give a kit to another player (/kit <name> <player>)", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kitreset", "Reset own kit cooldown", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kitreset.others", "Reset another player's kit cooldown", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.admin", "Kit administration", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.admin.create", "Create kits", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.admin.delete", "Delete kits", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.admin.list", "List all kits (admin)", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.item.repair", "Repair items", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.enchant", "Enchant items", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.enchant.unsafe", "Unsafe enchanting", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.enchant.others", "Enchant others' items", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.powertool", "Use powertools", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.powertool.toggle", "Toggle powertools", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.dispose", "Use disposal system", PermissionRegistry.PermissionCategory.ITEMS, true);
      this.register("neoessentials.item.clearinventory", "Clear inventory", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.clearinventory.others", "Clear others' inventory", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.invsee", "View other players' inventories", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.invsee.edit", "Edit other players' inventories", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.enderchest", "View other players' ender chests", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.enderchest.edit", "Edit other players' ender chests", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.chat.msg", "Send private messages", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.reply", "Reply to messages", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.ignore", "Ignore players", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.unignore", "Unignore players", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.msgtoggle", "Toggle message acceptance", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.socialspy", "Use social spy", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.mute", "Mute players", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.unmute", "Unmute players", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.mutelist", "View mute list", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.exempt", "Exempt from muting", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.color", "Use basic color codes in chat (&0-9, &a-f)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.color.hex", "Use hex colors in chat (&#RRGGBB)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.format", "Use formatting codes in chat (&k-o, &r)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.channel.local", "Use local chat channel", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.channel.global", "Use global chat channel", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.staff", "Access to staff chat channel", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.mention", "Mention other players with @name", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.mention.all", "Mention everyone with @everyone", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.itemlink", "Show held item in chat with [item]", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.chat.caps.bypass", "Bypass caps filter", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.repeat.bypass", "Bypass repeat message filter", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.links.bypass", "Bypass link filter", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.spam.bypass", "Bypass spam rate limit", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.richtext", "Use rich text effects (gradients, rainbow)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.gradient", "Use gradient text effects", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.chat.rainbow", "Use rainbow text effects", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.afk", "Use AFK system", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.afk.exempt", "Exempt from AFK kick", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.anvil", "Open portable anvil", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.crafting", "Open portable crafting table", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.grindstone", "Open portable grindstone", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.smithing", "Open portable smithing table", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.stonecutting", "Open portable stonecutter", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.realname", "Find player by nickname", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.whois", "View player information", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.whois.detailed", "View detailed player information", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.seen", "Check when player was last seen", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.sign", "Edit sign text", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.sign.colors", "Use colors in signs", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.rules", "View server rules", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.rules.admin", "Manage server rules", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.suicide", "Use suicide command", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.ping", "Check own ping", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.ping.others", "Check others' ping", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.book", "Give yourself a writable book", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.book.unlock", "Unlock a written book for editing", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.book.title", "Set the title of a written book", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.book.author", "Set the author of a written book", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.depth", "View depth/Y-level information", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.depth.others", "View others' depth information", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.gamemode", "Change own gamemode", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.gamemode.others", "Change others' gamemode", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.helpop", "Send a help request to staff", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.helpop.receive", "Receive help-op requests", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.permissions.admin", "Permission system administration", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.reload", "Reload permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.list", "List permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.user", "User permission management", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group", "Group permission management", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.debug", "Debug mode access", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.info", "View mod information", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.moderation.ban", "Ban players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.banip", "Ban IP addresses", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.banlist", "View ban list", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.tempban", "Temporarily ban players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.unban", "Unban players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.unbanip", "Unban IP addresses", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.kick", "Kick players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.kickall", "Kick all players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.freeze", "Freeze players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.unfreeze", "Unfreeze players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.freezeall", "Freeze all players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.unfreezeall", "Unfreeze all players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.freezelist", "View frozen players list", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.jail", "Jail players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register(
         "neoessentials.moderation.jail.timed", "Jail players for a set duration (/jailfor)", PermissionRegistry.PermissionCategory.MODERATION, false
      );
      this.register("neoessentials.moderation.unjail", "Unjail players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.setjail", "Create jail locations", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.deljail", "Delete jail locations", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.jaillist", "View jailed players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.jailinfo", "View jail info", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.jail.allow-break", "Break blocks while jailed", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.jail.allow-place", "Place blocks while jailed", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.jail.allow-interact", "Interact with blocks/items while jailed", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.jail.allow-attack", "Attack entities while jailed", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.vanish", "Vanish self", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.vanish.others", "Vanish other players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.seevanished", "See vanished players", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.vanishlist", "View vanished players list", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.notify", "Receive moderation notifications", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.notifications", "Receive moderation event broadcasts", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.vanish.see", "See vanished players (alias)", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.list", "View online player list", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.near", "View nearby players", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.nick", "Change own nickname", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.nick.color", "Use colour codes in nickname", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.nick.others", "Change other players' nicknames", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.staff", "Access staff chat and staff features", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.motd", "View MOTD", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.motd.set", "Set MOTD", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.motd.broadcast", "Broadcast MOTD", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.motd.reload", "Reload MOTD", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.mail", "Use mail system (read, delete, status)", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.mail.send", "Send mail to players", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.mail.sendtemp", "Send timed/expiring mail to a player", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.mail.sendall", "Broadcast mail to all players", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.mail.sendtempall", "Broadcast timed mail to all players", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.mail.clear", "Clear own mail", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.mail.clear.others", "Clear another player's mail (admin)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.mail.clearall", "Wipe every player's mailbox (admin)", PermissionRegistry.PermissionCategory.CHAT, false);
      this.register("neoessentials.item.enchant.any", "Enchant any item (ignore restrictions)", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.item.spawn", "Use /spawnitem command", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.teleport.settpr", "Set random teleport centre", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.tp", "Teleport self (alias)", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.tphere", "Teleport others to self (alias)", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.tppos", "Teleport to coordinates (alias)", PermissionRegistry.PermissionCategory.TELEPORT, false);
      this.register("neoessentials.teleport.pwarp", "Use player warps", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.pwarp.create", "Create player warps", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.pwarp.delete", "Delete player warps", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.teleport.pwarp.list", "List player warps", PermissionRegistry.PermissionCategory.TELEPORT, true);
      this.register("neoessentials.kits.create", "Create kits via /createkit", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.delete", "Delete kits via /delkit", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.kits.override", "Override kit restrictions", PermissionRegistry.PermissionCategory.KITS, false);
      this.register("neoessentials.permissions.check", "Check a player's permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.search", "Search permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.list.groups", "List permission groups", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.list.users", "List permission users", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.info.user", "View user permission info", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.info.group", "View group permission info", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.user.permissions", "Manage user permission nodes", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.user.groups", "Manage user group membership", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.user.clear", "Clear all user permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.create", "Create permission groups", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.delete", "Delete permission groups", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.rename", "Rename permission groups", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.clone", "Clone permission groups", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.inherit", "Set group inheritance", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.permissions", "Manage group permission nodes", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.modify", "Modify group settings", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.permissions.group.clear", "Clear all group permissions", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.fly", "Toggle flight mode", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.fly.others", "Toggle flight for other players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.god", "Toggle god mode (invincibility)", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.god.others", "Toggle god mode for other players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.heal", "Restore own health and hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.heal.others", "Restore another player's health", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.feed", "Restore own hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.feed.others", "Restore another player's hunger", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.speed", "Set walk/fly speed", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.speed.others", "Set another player's speed", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.ext", "Extinguish self", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.ext.others", "Extinguish another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.burn", "Set a player on fire", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.give", "Give items to players", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.more", "Fill held stack to max", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.hat", "Wear held item as helmet", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp", "View XP info", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.exp.set", "Set own XP", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.set.others", "Set another player's XP", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.give", "Give XP to self", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.exp.give.others", "Give XP to another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.sudo", "Run commands as another player", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.sudo.exempt", "Cannot be sudo'd by non-console", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.playtime", "View own playtime", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.playtime.others", "View another player's playtime", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.dashboard.access", "Register and access the web dashboard", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.dashboard.view", "View-only dashboard access", PermissionRegistry.PermissionCategory.MISC, false);
      this.register("neoessentials.dashboard.manage", "Manage dashboard settings", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.dashboard.moderator", "Moderator dashboard access", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.dashboard.admin", "Full admin dashboard access", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.item", "Give yourself an item by name (/item)", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.rtoggle", "Toggle /r reply direction", PermissionRegistry.PermissionCategory.CHAT, true);
      this.register("neoessentials.rtoggle.others", "Toggle /r reply direction for another player", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.help", "View command help list", PermissionRegistry.PermissionCategory.MISC, true);
      this.register("neoessentials.moderation.tempbanip", "Temporarily ban an IP address", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.togglejail", "Toggle a player's jail state", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.moderation.jailinfo", "View jail location info", PermissionRegistry.PermissionCategory.MODERATION, false);
      this.register("neoessentials.powertooltoggle", "Toggle all powertools on/off globally", PermissionRegistry.PermissionCategory.ITEMS, true);
      this.register("neoessentials.tablist.admin", "Manage the custom tablist system", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.firework", "Edit held firework rockets", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.firework.fire", "Launch firework rockets with /firework fire", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.nuke", "Rain TNT on a player (/nuke)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.antioch", "Spawn lit TNT at look target (/antioch)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.kittycannon", "Launch exploding baby cat (/kittycannon)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.beezooka", "Launch angry bees (/beezooka)", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.itemdb", "Look up item registry information", PermissionRegistry.PermissionCategory.PLAYER, false);
      this.register("neoessentials.potion", "Edit potion effects on held potion item", PermissionRegistry.PermissionCategory.ITEMS, false);
      this.register("neoessentials.info", "View server info/MOTD (/info)", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rest", "Reset sleep timer to prevent phantom spawning", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.rest.others", "Reset another player's sleep timer", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.backup", "Trigger server world save and backup", PermissionRegistry.PermissionCategory.ADMIN, false);
      this.register("neoessentials.showkit", "Preview kit contents without claiming", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.powertoollist", "List all active powertool bindings", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.customtext", "Display custom server text pages", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.payconfirmtoggle", "Toggle payment confirmation prompts", PermissionRegistry.PermissionCategory.PLAYER, true);
      this.register("neoessentials.ciconfirmtoggle", "Toggle /clearinventory confirmation prompts", PermissionRegistry.PermissionCategory.PLAYER, true);
      LOGGER.info("Registered {} permission nodes", this.registeredPermissions.size());
   }

   public void registerKitPermission(String kitName) {
      if (kitName != null && !kitName.trim().isEmpty()) {
         String permission = "neoessentials.kits." + kitName.toLowerCase();
         String nocooldownPermission = permission + ".nocooldown";
         this.register(permission, "Use kit: " + kitName, PermissionRegistry.PermissionCategory.KITS, false);
         this.register(nocooldownPermission, "Bypass cooldown for kit: " + kitName, PermissionRegistry.PermissionCategory.KITS, false);
      }
   }

   public void unregisterKitPermission(String kitName) {
      if (kitName != null && !kitName.trim().isEmpty()) {
         String permission = "neoessentials.kits." + kitName.toLowerCase();
         String nocooldownPermission = permission + ".nocooldown";
         this.registeredPermissions.remove(permission);
         this.permissionInfo.remove(permission);
         this.registeredPermissions.remove(nocooldownPermission);
         this.permissionInfo.remove(nocooldownPermission);
         LOGGER.debug("Unregistered kit permissions: {} and {}", permission, nocooldownPermission);
      }
   }

   public Map<PermissionRegistry.PermissionCategory, Integer> getPermissionSummary() {
      Map<PermissionRegistry.PermissionCategory, Integer> summary = new EnumMap<>(PermissionRegistry.PermissionCategory.class);

      for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
         summary.put(category, this.getPermissionsByCategory(category).size());
      }

      return summary;
   }

   private void autoDiscoverPermissions() {
      LOGGER.info("Starting automatic permission discovery...");

      try {
         PermissionScanner scanner = PermissionScanner.getInstance();
         scanner.scanForPermissions();
         Set<String> discoveredPermissions = scanner.getDiscoveredPermissions();

         for (String permission : discoveredPermissions) {
            if (!this.isRegistered(permission)) {
               PermissionRegistry.PermissionCategory category = this.categorizePermission(permission);
               this.register(permission, "Auto-discovered permission", category, false);
            }
         }

         LOGGER.info(
            "Auto-discovery completed: {} permissions discovered, {} new permissions registered",
            discoveredPermissions.size(),
            discoveredPermissions.stream().mapToInt(p -> this.isRegistered(p) ? 0 : 1).sum()
         );
      } catch (Exception var6) {
         LOGGER.error("Error during automatic permission discovery", var6);
      }
   }

   private PermissionRegistry.PermissionCategory categorizePermission(String permission) {
      String[] parts = permission.split("\\.");
      if (parts.length >= 2) {
         String category = parts[1].toLowerCase();

         return switch (category) {
            case "economy", "eco", "balance", "pay", "money" -> PermissionRegistry.PermissionCategory.ECONOMY;
            case "teleport", "tp", "tpa", "home", "warp", "spawn" -> PermissionRegistry.PermissionCategory.TELEPORT;
            case "chat", "msg", "message", "reply", "socialspy", "mute", "ignore" -> PermissionRegistry.PermissionCategory.CHAT;
            case "kit", "kits" -> PermissionRegistry.PermissionCategory.KITS;
            case "item", "items", "give", "enchant", "repair" -> PermissionRegistry.PermissionCategory.ITEMS;
            case "moderation", "mod", "ban", "kick", "freeze", "jail", "vanish" -> PermissionRegistry.PermissionCategory.MODERATION;
            case "admin", "reload", "permissions", "debug" -> PermissionRegistry.PermissionCategory.ADMIN;
            case "fly", "god", "heal", "feed", "speed", "ext", "burn", "more", "hat", "exp", "sudo", "playtime" -> PermissionRegistry.PermissionCategory.PLAYER;
            default -> PermissionRegistry.PermissionCategory.MISC;
         };
      } else {
         return PermissionRegistry.PermissionCategory.CORE;
      }
   }

   public void refreshPermissions() {
      LOGGER.info("Refreshing permission registry...");
      int initialCount = this.registeredPermissions.size();
      this.autoDiscoverPermissions();
      int finalCount = this.registeredPermissions.size();
      LOGGER.info("Permission refresh completed: {} -> {} permissions (+" + (finalCount - initialCount) + " new)", initialCount, finalCount);
   }

   public Set<String> getAutoDiscoveredPermissions() {
      try {
         PermissionScanner scanner = PermissionScanner.getInstance();
         return scanner.getDiscoveredPermissions();
      } catch (Exception var2) {
         LOGGER.error("Error getting auto-discovered permissions", var2);
         return Collections.emptySet();
      }
   }

   public List<String> exportPermissions() {
      List<String> export = new ArrayList<>();
      export.add("# NeoEssentials Permission Nodes");
      export.add("# Total: " + this.registeredPermissions.size() + " permissions");
      export.add("");

      for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
         Set<String> categoryPerms = this.getPermissionsByCategory(category);
         if (!categoryPerms.isEmpty()) {
            export.add("## " + category.getDescription() + " (" + categoryPerms.size() + ")");
            export.add("");
            categoryPerms.stream().sorted().forEach(perm -> {
               PermissionRegistry.PermissionInfo info = this.permissionInfo.get(perm);
               export.add("- `" + perm + "` - " + info.getDescription() + " (default: " + info.getDefaultValue() + ")");
            });
            export.add("");
         }
      }

      return export;
   }

   public void syncWithLuckPerms() {
      try {
         if (PermissionAPI.getExternalAdapter() instanceof LuckPermsAdapter luckPermsAdapter) {
            LOGGER.info("Syncing {} permissions with LuckPerms...", this.registeredPermissions.size());
            luckPermsAdapter.registerPermissions(this.registeredPermissions);
            LOGGER.info("✓ Permissions synced with LuckPerms");
            LOGGER.info("  - Permissions will now appear in LuckPerms autocomplete");
            LOGGER.info("  - Use '/lp info' to see registered permissions");
            LOGGER.info("  - Web editor will show NeoEssentials permissions");
         } else {
            LOGGER.debug("LuckPerms not detected - skipping permission sync");
         }
      } catch (Exception var3) {
         LOGGER.warn("Could not sync permissions with LuckPerms: {}", var3.getMessage());
         LOGGER.debug("LuckPerms sync error details", var3);
      }
   }

   public String exportForLuckPerms() {
      StringBuilder yaml = new StringBuilder();
      yaml.append("# NeoEssentials Permissions for LuckPerms\n");
      yaml.append("# Generated on: ").append(LocalDateTime.now()).append("\n");
      yaml.append("# Total permissions: ").append(this.registeredPermissions.size()).append("\n");
      yaml.append("#\n");
      yaml.append("# To import: Save this file and run: /lp import <filename>\n");
      yaml.append("#\n\n");
      yaml.append("groups:\n");
      yaml.append("  default:\n");
      yaml.append("    permissions:\n");

      for (String permission : this.registeredPermissions) {
         PermissionRegistry.PermissionInfo info = this.permissionInfo.get(permission);
         if (info != null && info.getDefaultValue()) {
            yaml.append("      - ").append(permission).append("\n");
         }
      }

      yaml.append("\n");
      yaml.append("  admin:\n");
      yaml.append("    permissions:\n");
      yaml.append("      - neoessentials.*  # Grant all NeoEssentials permissions\n");
      return yaml.toString();
   }

   public static enum PermissionCategory {
      ADMIN("admin", "Administrative commands"),
      ECONOMY("economy", "Economy system"),
      TELEPORT("teleport", "Teleportation commands"),
      CHAT("chat", "Chat and messaging"),
      KITS("kits", "Kit system"),
      ITEMS("items", "Item management"),
      MODERATION("moderation", "Moderation commands"),
      PLAYER("player", "Player state commands"),
      MISC("misc", "Miscellaneous commands"),
      CORE("core", "Core functionality");

      private final String key;
      private final String description;

      private PermissionCategory(String key, String description) {
         this.key = key;
         this.description = description;
      }

      public String getKey() {
         return this.key;
      }

      public String getDescription() {
         return this.description;
      }
   }

   public static class PermissionInfo {
      private final String permission;
      private final String description;
      private final PermissionRegistry.PermissionCategory category;
      private final boolean defaultValue;

      public PermissionInfo(String permission, String description, PermissionRegistry.PermissionCategory category, boolean defaultValue) {
         this.permission = permission;
         this.description = description;
         this.category = category;
         this.defaultValue = defaultValue;
      }

      public String getPermission() {
         return this.permission;
      }

      public String getDescription() {
         return this.description;
      }

      public PermissionRegistry.PermissionCategory getCategory() {
         return this.category;
      }

      public boolean getDefaultValue() {
         return this.defaultValue;
      }
   }

   private static class SingletonHolder {
      private static final PermissionRegistry INSTANCE = new PermissionRegistry();
   }
}
