package com.zerog.neoessentials.teleportation;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PlayerDataMigration;
import com.zerog.neoessentials.util.PlayerDataStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomeManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(HomeManager.class);
   private static final String HOMES_FILE = "homes.json";
   private final PlayerDataStore playerDataStore;
   private final Map<UUID, Map<String, TeleportLocation>> playerHomes = new ConcurrentHashMap<>();
   private int maxHomesPerPlayer = 5;
   private int homeSetCooldownSeconds = 0;
   private int homeTeleportCooldownSeconds = 0;
   private int homeDeleteCooldownSeconds = 0;
   private final Map<UUID, Long> lastHomeSetTimestamps = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastHomeTeleportTimestamps = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastHomeDeleteTimestamps = new ConcurrentHashMap<>();
   private boolean allowOverworldOnly = false;
   private boolean allowCrossDimensionHomes = true;
   private boolean requireSafeLocations = true;
   private int teleportDelay = 3;

   public static HomeManager getInstance() {
      return HomeManager.SingletonHolder.INSTANCE;
   }

   public int getMaxHomesForPlayer(ServerPlayer player) {
      int configMax = this.maxHomesPerPlayer;
      int permMax = -1;

      for (int i = 100; i >= 1; i--) {
         String perm = "neoessentials.home." + i;
         if (PermissionAPI.hasPermission(player.getUUID(), perm)) {
            permMax = i;
            break;
         }
      }

      return Math.max(permMax, configMax);
   }

   private HomeManager() {
      this.playerDataStore = new PlayerDataStore("homes");
      if (PlayerDataMigration.needsMigration("homes.json")) {
         LOGGER.info("Migrating homes from old storage format...");
         PlayerDataMigration.migrateToPlayerData("homes.json", "homes");
      }

      this.loadConfig();
   }

   private void loadConfig() {
      try {
         ConfigManager configManager = ConfigManager.getInstance();
         boolean safe = true;
         int maxHomes = 5;
         int setCooldown = 0;
         int tpCooldown = 0;
         int delCooldown = 0;
         if (configManager != null) {
            JsonObject config = configManager.getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("homeSettings")) {
                  JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
                  if (homeSettings.has("enableHomeTeleportSafety")) {
                     safe = homeSettings.get("enableHomeTeleportSafety").getAsBoolean();
                  }

                  if (homeSettings.has("maxHomes")) {
                     try {
                        maxHomes = homeSettings.get("maxHomes").getAsInt();
                     } catch (Exception var15) {
                     }
                  }

                  if (homeSettings.has("allowCrossDimensionHomes")) {
                     try {
                        this.allowCrossDimensionHomes = homeSettings.get("allowCrossDimensionHomes").getAsBoolean();
                     } catch (Exception var14) {
                     }
                  }

                  if (homeSettings.has("homeSetCooldown")) {
                     try {
                        setCooldown = homeSettings.get("homeSetCooldown").getAsInt();
                     } catch (Exception var13) {
                     }
                  }

                  if (homeSettings.has("homeTeleportCooldown")) {
                     try {
                        tpCooldown = homeSettings.get("homeTeleportCooldown").getAsInt();
                     } catch (Exception var12) {
                     }
                  }

                  if (homeSettings.has("homeDeleteCooldown")) {
                     try {
                        delCooldown = homeSettings.get("homeDeleteCooldown").getAsInt();
                     } catch (Exception var11) {
                     }
                  }
               }
            }
         }

         this.setRequireSafeLocations(safe);
         this.setMaxHomesPerPlayer(maxHomes);
         this.setHomeSetCooldownSeconds(setCooldown);
         this.setHomeTeleportCooldownSeconds(tpCooldown);
         this.setHomeDeleteCooldownSeconds(delCooldown);
      } catch (Exception var16) {
         LOGGER.warn("Failed to load home config, using defaults: {}", var16.getMessage());
      }
   }

   public boolean setHome(ServerPlayer player, String homeName) {
      return this.setHome(player, homeName, null);
   }

   public boolean setHome(ServerPlayer player, String homeName, TeleportLocation customLocation) {
      UUID playerId = player.getUUID();
      boolean requireSafe = ConfigManager.getInstance().isHomeTeleportSafetyEnabled();
      boolean debug = ConfigManager.isDebugModeEnabled();
      if (debug) {
         LOGGER.info("[DEBUG] Home set safety: {} (from config)", requireSafe);
      }

      if (this.homeSetCooldownSeconds > 0) {
         long now = System.currentTimeMillis();
         Long lastSet = this.lastHomeSetTimestamps.putIfAbsent(playerId, now);
         if (lastSet != null) {
            long elapsed = (now - lastSet) / 1000L;
            if (elapsed < (long)this.homeSetCooldownSeconds) {
               long wait = (long)this.homeSetCooldownSeconds - elapsed;
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.cooldown", wait));
               return false;
            }

            this.lastHomeSetTimestamps.put(playerId, now);
         }
      }

      if (!this.isValidHomeName(homeName)) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.invalid_name", homeName));
         return false;
      } else {
         TeleportLocation location = customLocation != null ? customLocation : new TeleportLocation(player);
         if (!this.allowCrossDimensionHomes && !this.isOverworld(location)) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.overworld_only"));
            return false;
         } else {
            if (requireSafe && !location.isSafe()) {
               TeleportLocation safeLocation = location.findSafeLocation();
               if (safeLocation == null) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.unsafe_location"));
                  if (debug) {
                     LOGGER.info("[DEBUG] Unsafe sethome location for '{}', set blocked.", homeName);
                  }

                  return false;
               }

               location = safeLocation;
               if (debug) {
                  LOGGER.info("[DEBUG] Sethome '{}' moved to safe location.", homeName);
               }
            }

            int allowedHomes = this.getMaxHomesForPlayer(player);
            TeleportLocation finalLocation = location;
            boolean[] result = new boolean[2];
            this.playerHomes.compute(playerId, (id, homes) -> {
               if (homes == null) {
                  homes = new ConcurrentHashMap<>();
               }

               boolean isNewx = !homes.containsKey(homeName);
               if (isNewx && homes.size() >= allowedHomes) {
                  return homes;
               } else {
                  homes.put(homeName, finalLocation);
                  result[0] = true;
                  result[1] = isNewx;
                  return homes;
               }
            });
            if (!result[0]) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.limit_reached", allowedHomes));
               return false;
            } else {
               boolean isNew = result[1];
               this.savePlayerHomes(playerId);
               if (isNew) {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.set", homeName, location.getLocationString()));
               } else {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.updated", homeName, location.getLocationString()));
               }

               if (ConfigManager.getInstance().isLogHomeActionsEnabled()) {
                  LOGGER.info(
                     "Player {} {} home '{}' at {}",
                     new Object[]{player.getName().getString(), isNew ? "set" : "updated", homeName, location.getLocationString()}
                  );
               }

               return true;
            }
         }
      }
   }

   public boolean deleteHome(ServerPlayer player, String homeName) {
      UUID playerId = player.getUUID();
      if (this.homeDeleteCooldownSeconds > 0) {
         long now = System.currentTimeMillis();
         Long lastDelete = this.lastHomeDeleteTimestamps.putIfAbsent(playerId, now);
         if (lastDelete != null) {
            long elapsed = (now - lastDelete) / 1000L;
            if (elapsed < (long)this.homeDeleteCooldownSeconds) {
               long wait = (long)this.homeDeleteCooldownSeconds - elapsed;
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.delete_cooldown", wait));
               return false;
            }

            this.lastHomeDeleteTimestamps.put(playerId, now);
         }
      }

      boolean[] deleted = new boolean[]{false};
      this.playerHomes.computeIfPresent(playerId, (id, homes) -> {
         if (homes.remove(homeName) != null) {
            deleted[0] = true;
            return homes.isEmpty() ? null : homes;
         } else {
            return homes;
         }
      });
      if (!deleted[0]) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.not_found", homeName));
         return false;
      } else {
         this.savePlayerHomes(playerId);
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.deleted", homeName));
         if (ConfigManager.getInstance().isLogHomeActionsEnabled()) {
            LOGGER.info("Player {} deleted home '{}'", player.getName().getString(), homeName);
         }

         return true;
      }
   }

   public int getHomeDeleteCooldownSeconds() {
      return this.homeDeleteCooldownSeconds;
   }

   public void setHomeDeleteCooldownSeconds(int seconds) {
      this.homeDeleteCooldownSeconds = Math.max(0, seconds);
   }

   public boolean renameHome(ServerPlayer player, String oldName, String newName) {
      UUID playerId = player.getUUID();
      if (!this.isValidHomeName(newName)) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.invalid_name", newName));
         return false;
      } else {
         boolean[] result = new boolean[]{false};
         this.playerHomes.computeIfPresent(playerId, (id, homes) -> {
            TeleportLocation loc = homes.get(oldName);
            if (loc == null) {
               return homes;
            } else if (homes.containsKey(newName)) {
               return homes;
            } else {
               homes.remove(oldName);
               homes.put(newName, loc);
               result[0] = true;
               return homes;
            }
         });
         if (!result[0]) {
            boolean exists = this.getOrLoadPlayerHomes(playerId).containsKey(oldName);
            if (!exists) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.not_found", oldName));
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.name_taken", newName));
            }

            return false;
         } else {
            this.savePlayerHomes(playerId);
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.renamed", oldName, newName));
            return true;
         }
      }
   }

   private Map<String, TeleportLocation> getOrLoadPlayerHomes(UUID playerId) {
      return this.playerHomes.computeIfAbsent(playerId, this::loadPlayerHomes);
   }

   public TeleportLocation getHome(ServerPlayer player, String homeName) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> homes = this.getOrLoadPlayerHomes(playerId);
      return homes.get(homeName);
   }

   public Map<String, TeleportLocation> getPlayerHomes(ServerPlayer player) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> homes = this.getOrLoadPlayerHomes(playerId);
      return new HashMap<>(homes);
   }

   public List<String> getHomeNames(ServerPlayer player) {
      Map<String, TeleportLocation> homes = this.getPlayerHomes(player);
      return new ArrayList<>(homes.keySet());
   }

   public void teleportToHome(ServerPlayer player, String homeName) {
      TeleportLocation home = this.getHome(player, homeName);
      UUID playerId = player.getUUID();
      boolean requireSafe = ConfigManager.getInstance().isHomeTeleportSafetyEnabled();
      boolean debug = ConfigManager.isDebugModeEnabled();
      if (debug) {
         LOGGER.info("[DEBUG] Home teleport safety: {} (from config)", requireSafe);
      }

      if (home == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.not_found", homeName));
      } else {
         if (requireSafe) {
            if (!home.isSafe()) {
               TeleportLocation safeLocation = home.findSafeLocation();
               if (safeLocation == null) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.unsafe", homeName));
                  if (debug) {
                     LOGGER.info("[DEBUG] Unsafe home location for '{}', teleport blocked.", homeName);
                  }

                  return;
               }

               this.playerHomes.computeIfPresent(playerId, (id, homes) -> {
                  homes.put(homeName, safeLocation);
                  return homes;
               });
               this.savePlayerHomes(playerId);
               home = safeLocation;
               player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.home.moved_to_safety", homeName));
               if (debug) {
                  LOGGER.info("[DEBUG] Home '{}' moved to safe location.", homeName);
               }
            }
         } else if (debug) {
            LOGGER.info("[DEBUG] Home teleport safety is disabled. Teleporting to potentially unsafe location for '{}'.", homeName);
         }

         MiscTeleportManager.getInstance().saveBackLocation(player);
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, home, delayTicks, false).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.success", homeName));
               if (ConfigManager.getInstance().isLogHomeActionsEnabled()) {
                  LOGGER.info("Player {} teleported to home '{}'", player.getName().getString(), homeName);
               }
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.failed", homeName, result.getMessage()));
               LOGGER.warn("Failed to teleport player {} to home '{}': {}", new Object[]{player.getName().getString(), homeName, result.getMessage()});
            }
         });
      }
   }

   public void teleportToDefaultHome(ServerPlayer player) {
      Map<String, TeleportLocation> homes = this.getPlayerHomes(player);
      if (homes.isEmpty()) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.none_set"));
      } else {
         String homeName = homes.containsKey("home") ? "home" : homes.keySet().iterator().next();
         this.teleportToHome(player, homeName);
      }
   }

   public String getFormattedHomesList(ServerPlayer player) {
      Map<String, TeleportLocation> homes = this.getPlayerHomes(player);
      if (homes.isEmpty()) {
         return MessageUtil.localize("commands.neoessentials.teleport.home.list_empty");
      } else {
         StringBuilder builder = new StringBuilder();
         int allowedHomes = this.getMaxHomesForPlayer(player);
         builder.append(MessageUtil.localize("commands.neoessentials.teleport.home.list_header", homes.size(), allowedHomes));
         List<String> sortedNames = new ArrayList<>(homes.keySet());
         Collections.sort(sortedNames);

         for (String homeName : sortedNames) {
            TeleportLocation location = homes.get(homeName);
            builder.append("\n  §e").append(homeName).append("§r: ").append(location.getLocationString());
         }

         return builder.toString();
      }
   }

   public boolean hasHomes(ServerPlayer player) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> homes = this.getOrLoadPlayerHomes(playerId);
      return !homes.isEmpty();
   }

   public int getHomeCount(ServerPlayer player) {
      Map<String, TeleportLocation> homes = this.getPlayerHomes(player);
      return homes.size();
   }

   private boolean isValidHomeName(String name) {
      if (name != null && !name.trim().isEmpty()) {
         return name.length() > 20 ? false : name.matches("^[a-zA-Z0-9_-]+$");
      } else {
         return false;
      }
   }

   private boolean isOverworld(TeleportLocation location) {
      return location.getWorldName().contains("overworld");
   }

   private void loadHomes() {
      LOGGER.debug("Home loading is now on-demand per player");
   }

   private Map<String, TeleportLocation> loadPlayerHomes(UUID playerId) {
      try {
         JsonObject data = this.playerDataStore.load(playerId);
         Map<String, TeleportLocation> homes = new HashMap<>();
         if (data.keySet().isEmpty()) {
            LOGGER.debug("No homes found for player {}", playerId);
            return homes;
         } else {
            for (String homeName : data.keySet()) {
               try {
                  JsonObject homeJson = data.getAsJsonObject(homeName);
                  TeleportLocation location = TeleportLocation.fromJson(homeJson);
                  if (location != null) {
                     homes.put(homeName, location);
                  }
               } catch (Exception var8) {
                  LOGGER.warn("Failed to load home '{}' for player {}: {}", new Object[]{homeName, playerId, var8.getMessage()});
               }
            }

            LOGGER.debug("Loaded {} homes for player {}", homes.size(), playerId);
            return homes;
         }
      } catch (Exception var9) {
         LOGGER.error("Failed to load homes for player {}: {}", new Object[]{playerId, var9.getMessage(), var9});
         return new HashMap<>();
      }
   }

   private void saveHomes() {
      for (UUID playerId : this.playerHomes.keySet()) {
         this.savePlayerHomes(playerId);
      }
   }

   private void savePlayerHomes(UUID playerId) {
      try {
         Map<String, TeleportLocation> homes = this.playerHomes.get(playerId);
         if (homes == null || homes.isEmpty()) {
            this.playerDataStore.save(playerId, new JsonObject());
            return;
         }

         JsonObject data = new JsonObject();

         for (Entry<String, TeleportLocation> entry : homes.entrySet()) {
            data.add(entry.getKey(), entry.getValue().toJson());
         }

         this.playerDataStore.save(playerId, data);
         LOGGER.debug("Saved {} homes for player {}", homes.size(), playerId);
      } catch (Exception var6) {
         LOGGER.error("Failed to save homes for player {}: {}", new Object[]{playerId, var6.getMessage(), var6});
      }
   }

   public int getMaxHomesPerPlayer() {
      return this.maxHomesPerPlayer;
   }

   public void setMaxHomesPerPlayer(int max) {
      this.maxHomesPerPlayer = Math.max(1, max);
   }

   public boolean isAllowOverworldOnly() {
      return this.allowOverworldOnly;
   }

   public void setAllowOverworldOnly(boolean allow) {
      this.allowOverworldOnly = allow;
   }

   public boolean isAllowCrossDimensionHomes() {
      return this.allowCrossDimensionHomes;
   }

   public void setAllowCrossDimensionHomes(boolean allow) {
      this.allowCrossDimensionHomes = allow;
   }

   public boolean isRequireSafeLocations() {
      return this.requireSafeLocations;
   }

   public void setRequireSafeLocations(boolean require) {
      this.requireSafeLocations = require;
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public int getHomeSetCooldownSeconds() {
      return this.homeSetCooldownSeconds;
   }

   public void setHomeSetCooldownSeconds(int seconds) {
      this.homeSetCooldownSeconds = Math.max(0, seconds);
   }

   public int getHomeTeleportCooldownSeconds() {
      return this.homeTeleportCooldownSeconds;
   }

   public void setHomeTeleportCooldownSeconds(int seconds) {
      this.homeTeleportCooldownSeconds = Math.max(0, seconds);
   }

   public void clearAllHomes() {
      this.playerHomes.clear();
      this.playerDataStore.clearAll();
      LOGGER.info("Cleared all player homes");
   }

   public int getTotalHomesCount() {
      return this.playerHomes.values().stream().mapToInt(Map::size).sum();
   }

   public String getStatistics() {
      int totalPlayers = this.playerHomes.size();
      int totalHomes = this.getTotalHomesCount();
      double avgHomesPerPlayer = totalPlayers > 0 ? (double)totalHomes / (double)totalPlayers : 0.0;
      return String.format("Homes Statistics: %d players, %d total homes, %.1f avg homes per player", totalPlayers, totalHomes, avgHomesPerPlayer);
   }

   public void reload() {
      LOGGER.info("Reloading home system...");
      this.loadConfig();
      this.playerDataStore.flushAll();
      this.playerHomes.clear();
      LOGGER.info("Home system reloaded - {} players in storage, homes will load on-demand", this.playerDataStore.getTotalPlayers());
   }

   private static class SingletonHolder {
      private static final HomeManager INSTANCE = new HomeManager();
   }
}
