package com.zerog.neoessentials.teleportation.Warp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarpManager {
   private final Map<UUID, Long> lastWarpSetTimestamps = new ConcurrentHashMap<>();
   private int warpSetCooldown = 0;
   private final Map<UUID, Map<String, TeleportLocation>> playerWarps = new ConcurrentHashMap<>();
   private int maxPlayerWarps = 3;
   private boolean allowPlayerWarps = false;
   private static final Logger LOGGER = LoggerFactory.getLogger(WarpManager.class);
   private static final String WARPS_FILE = "warps.json";
   private final Map<String, TeleportLocation> warps = new ConcurrentHashMap<>();
   private final Gson gson = new Gson();
   private int teleportDelay = 0;
   private boolean requireSafeLocations = true;
   private boolean allowOverworldOnly = false;
   private int maxWarps = 50;
   private boolean caseSensitiveNames = false;
   private boolean allowCrossDimensionWarps = true;
   private static final String PLAYER_WARPS_FILE = "run/playerwarps.json";

   public static WarpManager getInstance() {
      return WarpManager.SingletonHolder.INSTANCE;
   }

   private WarpManager() {
      this.loadConfig();
      this.loadWarps();
      this.loadPlayerWarps();
   }

   private void loadConfig() {
      try {
         ConfigManager configManager = ConfigManager.getInstance();
         if (configManager != null) {
            JsonObject config = configManager.getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("warpSettings")) {
                  JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                  if (warpSettings.has("enableWarpSafety")) {
                     this.requireSafeLocations = warpSettings.get("enableWarpSafety").getAsBoolean();
                  }

                  if (warpSettings.has("allowPlayerWarps")) {
                     this.allowPlayerWarps = warpSettings.get("allowPlayerWarps").getAsBoolean();
                  }

                  if (warpSettings.has("maxPlayerWarps")) {
                     try {
                        this.maxPlayerWarps = warpSettings.get("maxPlayerWarps").getAsInt();
                     } catch (Exception var9) {
                     }
                  }

                  if (warpSettings.has("warpSetCooldown")) {
                     try {
                        this.warpSetCooldown = warpSettings.get("warpSetCooldown").getAsInt();
                     } catch (Exception var8) {
                     }
                  }

                  if (warpSettings.has("allowCrossDimensionWarps")) {
                     try {
                        this.allowCrossDimensionWarps = warpSettings.get("allowCrossDimensionWarps").getAsBoolean();
                     } catch (Exception var7) {
                     }
                  }
               }

               if (tp.has("generalSettings")) {
                  JsonObject generalSettings = tp.getAsJsonObject("generalSettings");
                  if (generalSettings.has("teleportDelay")) {
                     try {
                        this.teleportDelay = generalSettings.get("teleportDelay").getAsInt();
                     } catch (Exception var6) {
                     }
                  }
               }
            }
         }

         LOGGER.debug("Warp config loaded: requireSafe={}, maxWarps={}, delay={}", new Object[]{this.requireSafeLocations, this.maxWarps, this.teleportDelay});
      } catch (Exception var10) {
         LOGGER.warn("Failed to load warp config, using defaults: {}", var10.getMessage());
      }
   }

   public boolean isPlayerWarpsEnabled() {
      return this.allowPlayerWarps;
   }

   public int getMaxPlayerWarps() {
      return this.maxPlayerWarps;
   }

   public int getMaxPlayerWarpsForPlayer(ServerPlayer player) {
      if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.warp.limit.unlimited")) {
         return -1;
      } else {
         int configMax = this.maxPlayerWarps;
         int permMax = -1;

         for (int i = 100; i >= 1; i--) {
            String perm = "neoessentials.warp.limit." + i;
            if (PermissionAPI.hasPermission(player.getUUID(), perm)) {
               permMax = i;
               break;
            }
         }

         return Math.max(permMax, configMax);
      }
   }

   public boolean createPlayerWarp(ServerPlayer player, String warpName, TeleportLocation location) {
      if (!this.allowPlayerWarps) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.playerwarps_disabled"));
         return false;
      } else if (!this.allowCrossDimensionWarps && !this.isOverworld(location)) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.cross_dimension_disabled"));
         return false;
      } else {
         if (this.warpSetCooldown > 0) {
            long now = System.currentTimeMillis();
            UUID playerId = player.getUUID();
            Long lastSet = this.lastWarpSetTimestamps.putIfAbsent(playerId, now);
            if (lastSet != null && now - lastSet < (long)this.warpSetCooldown * 1000L) {
               long secondsLeft = (long)this.warpSetCooldown - (now - lastSet) / 1000L;
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.set_cooldown", secondsLeft));
               return false;
            }

            if (lastSet != null) {
               this.lastWarpSetTimestamps.put(playerId, now);
            }
         }

         UUID playerIdx = player.getUUID();
         String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
         if (!this.isValidWarpName(warpName)) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.invalid_name", warpName));
            return false;
         } else {
            Map<String, TeleportLocation> warps = this.playerWarps.computeIfAbsent(playerIdx, k -> new ConcurrentHashMap<>());
            if (warps.containsKey(normalizedName)) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.already_exists", warpName));
               return false;
            } else {
               int maxWarpsForPlayer = this.getMaxPlayerWarpsForPlayer(player);
               if (maxWarpsForPlayer >= 0 && warps.size() >= maxWarpsForPlayer) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.playerwarps_limit", maxWarpsForPlayer));
                  return false;
               } else {
                  TeleportLocation existing = warps.putIfAbsent(normalizedName, location);
                  if (existing != null) {
                     player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.already_exists", warpName));
                     return false;
                  } else {
                     this.savePlayerWarps();
                     player.sendSystemMessage(
                        MessageUtil.success("commands.neoessentials.teleport.warp.playerwarp_created", warpName, location.getLocationString())
                     );
                     LOGGER.info("Player {} created player warp '{}' at {}", new Object[]{player.getName().getString(), warpName, location.getLocationString()});
                     return true;
                  }
               }
            }
         }
      }
   }

   public boolean deletePlayerWarp(ServerPlayer player, String warpName) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> warps = this.playerWarps.get(playerId);
      if (warps == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
         return false;
      } else {
         String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
         TeleportLocation removed = warps.remove(normalizedName);
         if (removed == null) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
            return false;
         } else {
            this.savePlayerWarps();
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.warp.playerwarp_deleted", warpName));
            LOGGER.info("Player {} deleted player warp '{}'", player.getName().getString(), warpName);
            return true;
         }
      }
   }

   public TeleportLocation getPlayerWarp(ServerPlayer player, String warpName) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> warps = this.playerWarps.get(playerId);
      if (warps == null) {
         return null;
      } else {
         String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
         return warps.get(normalizedName);
      }
   }

   public List<String> getPlayerWarpNames(ServerPlayer player) {
      UUID playerId = player.getUUID();
      Map<String, TeleportLocation> warps = this.playerWarps.get(playerId);
      return (List<String>)(warps == null ? Collections.emptyList() : new ArrayList<>(warps.keySet()));
   }

   public boolean teleportToPlayerWarp(ServerPlayer admin, UUID targetPlayerId, String warpName) {
      if (!this.isAdmin(admin)) {
         admin.sendSystemMessage(MessageUtil.error("You do not have permission to access other players' warps."));
         return false;
      } else {
         Map<String, TeleportLocation> warps = this.playerWarps.get(targetPlayerId);
         if (warps == null) {
            admin.sendSystemMessage(MessageUtil.error("Target player has no warps."));
            return false;
         } else {
            TeleportLocation location = warps.get(this.caseSensitiveNames ? warpName : warpName.toLowerCase());
            if (location == null) {
               admin.sendSystemMessage(MessageUtil.error("Warp not found for target player."));
               return false;
            } else {
               TeleportUtil.teleportPlayer(admin, location);
               admin.sendSystemMessage(MessageUtil.success("Teleported to target player's warp."));
               return true;
            }
         }
      }
   }

   public List<String> listPlayerWarps(UUID targetPlayerId, ServerPlayer admin) {
      if (!this.isAdmin(admin)) {
         admin.sendSystemMessage(MessageUtil.error("You do not have permission to list other players' warps."));
         return List.of();
      } else {
         Map<String, TeleportLocation> warps = this.playerWarps.get(targetPlayerId);
         return (List<String>)(warps == null ? List.of() : new ArrayList<>(warps.keySet()));
      }
   }

   private boolean isAdmin(ServerPlayer player) {
      return player.hasPermissions(4);
   }

   private void savePlayerWarps() {
      try {
         Map<String, Map<String, TeleportLocation>> serializable = new HashMap<>();

         for (Entry<UUID, Map<String, TeleportLocation>> entry : this.playerWarps.entrySet()) {
            serializable.put(entry.getKey().toString(), entry.getValue());
         }

         String json = new GsonBuilder().setPrettyPrinting().create().toJson(serializable);
         Files.writeString(Path.of("run/playerwarps.json"), json);
      } catch (Exception var4) {
         System.err.println("[WarpManager] Failed to save player warps: " + var4);
      }
   }

   private void loadPlayerWarps() {
      try {
         Path path = Path.of("run/playerwarps.json");
         if (!Files.exists(path)) {
            return;
         }

         String json = Files.readString(path);
         Type type = (new TypeToken<Map<String, Map<String, TeleportLocation>>>() {
         }).getType();
         Map<String, Map<String, TeleportLocation>> loaded = (Map<String, Map<String, TeleportLocation>>)new Gson().fromJson(json, type);
         this.playerWarps.clear();

         for (Entry<String, Map<String, TeleportLocation>> entry : loaded.entrySet()) {
            this.playerWarps.put(UUID.fromString(entry.getKey()), entry.getValue());
         }
      } catch (Exception var7) {
         System.err.println("[WarpManager] Failed to load player warps: " + var7);
      }
   }

   public boolean createWarp(ServerPlayer creator, String warpName, TeleportLocation location) {
      if (this.warpSetCooldown > 0) {
         long now = System.currentTimeMillis();
         UUID playerId = creator.getUUID();
         Long lastSet = this.lastWarpSetTimestamps.putIfAbsent(playerId, now);
         if (lastSet != null && now - lastSet < (long)this.warpSetCooldown * 1000L) {
            long secondsLeft = (long)this.warpSetCooldown - (now - lastSet) / 1000L;
            creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.set_cooldown", secondsLeft));
            return false;
         }

         if (lastSet != null) {
            this.lastWarpSetTimestamps.put(playerId, now);
         }
      }

      if (!this.allowCrossDimensionWarps && !this.isOverworld(location)) {
         creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.cross_dimension_disabled"));
         return false;
      } else {
         String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
         if (!this.isValidWarpName(warpName)) {
            creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.invalid_name", warpName));
            return false;
         } else if (this.warps.size() >= this.maxWarps) {
            creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.limit_reached", this.maxWarps));
            return false;
         } else if (this.allowOverworldOnly && !this.isOverworld(location)) {
            creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.overworld_only"));
            return false;
         } else {
            boolean requireSafe = true;

            try {
               JsonObject config = ConfigManager.getInstance().getConfig("config.json");
               if (config.has("teleportation")) {
                  JsonObject tp = config.getAsJsonObject("teleportation");
                  if (tp.has("warpSettings")) {
                     JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                     if (warpSettings.has("enableWarpSafety")) {
                        requireSafe = warpSettings.get("enableWarpSafety").getAsBoolean();
                     }
                  }
               }
            } catch (Exception var10) {
               LOGGER.warn("Failed to read warp safety config, defaulting to enabled: {}", var10.getMessage());
            }

            if (requireSafe && !location.isSafe()) {
               TeleportLocation safeLocation = location.findSafeLocation();
               if (safeLocation == null) {
                  creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.unsafe_location"));
                  return false;
               }

               location = safeLocation;
               creator.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.warp.moved_to_safety"));
            }

            TeleportLocation existing = this.warps.putIfAbsent(normalizedName, location);
            if (existing != null) {
               creator.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.already_exists", warpName));
               return false;
            } else {
               this.saveWarps();
               creator.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.warp.created", warpName, location.getLocationString()));
               LOGGER.info("Player {} created warp '{}' at {}", new Object[]{creator.getName().getString(), warpName, location.getLocationString()});
               return true;
            }
         }
      }
   }

   public boolean createWarp(ServerPlayer creator, String warpName) {
      TeleportLocation location = new TeleportLocation(creator);
      return this.createWarp(creator, warpName, location);
   }

   public boolean createWarp(ServerPlayer creator, String warpName, ServerLevel level, BlockPos pos) {
      TeleportLocation location = new TeleportLocation(level, pos, 0.0F, 0.0F, creator.getName().getString());
      return this.createWarp(creator, warpName, location);
   }

   public boolean hasWarp(String warpName) {
      String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
      return this.warps.containsKey(normalizedName);
   }

   public TeleportLocation getWarp(String warpName) {
      String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
      return this.warps.get(normalizedName);
   }

   public List<String> getWarpNames() {
      return new ArrayList<>(this.warps.keySet());
   }

   public boolean deleteWarpByAdmin(String warpName, String deletedBy) {
      String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
      TeleportLocation removed = this.warps.remove(normalizedName);
      if (removed == null) {
         return false;
      } else {
         this.saveWarps();
         if (ConfigManager.getInstance().isLogWarpActionsEnabled()) {
            LOGGER.info("Warp '{}' deleted by {}", warpName, deletedBy);
         }

         return true;
      }
   }

   public boolean deleteWarp(ServerPlayer player, String warpName) {
      String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
      TeleportLocation removed = this.warps.remove(normalizedName);
      if (removed == null) {
         return false;
      } else {
         this.saveWarps();
         if (ConfigManager.getInstance().isLogWarpActionsEnabled()) {
            LOGGER.info("Player {} deleted warp '{}'", player.getName().getString(), warpName);
         }

         return true;
      }
   }

   public void teleportToWarp(ServerPlayer player, String warpName) {
      TeleportLocation warp = this.getWarp(warpName);
      if (warp == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
      } else {
         int maxDistance = ConfigManager.getInstance().getMaxTeleportDistance();
         if (maxDistance > 0) {
            TeleportLocation fromLoc = new TeleportLocation(player);
            if (fromLoc.getWorldName().equals(warp.getWorldName())) {
               double dist = fromLoc.distanceTo(warp);
               if (dist > (double)maxDistance) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.distance_exceeded", maxDistance));
                  return;
               }
            }
         }

         boolean requireSafe = true;

         try {
            JsonObject config = ConfigManager.getInstance().getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("warpSettings")) {
                  JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
                  if (warpSettings.has("enableWarpSafety")) {
                     requireSafe = warpSettings.get("enableWarpSafety").getAsBoolean();
                  }
               }
            }
         } catch (Exception var9) {
            LOGGER.warn("Failed to read warp safety config, defaulting to enabled: {}", var9.getMessage());
         }

         if (requireSafe && !warp.isSafe()) {
            TeleportLocation safeLocation = warp.findSafeLocation();
            if (safeLocation == null) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.unsafe", warpName));
               return;
            }

            String normalizedName = this.caseSensitiveNames ? warpName : warpName.toLowerCase();
            this.warps.put(normalizedName, safeLocation);
            this.saveWarps();
            warp = safeLocation;
            player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.warp.moved_to_safety", warpName));
         }

         MiscTeleportManager.getInstance().saveBackLocation(player);
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, warp, delayTicks, false).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.warp.success", warpName));
               LOGGER.info("Player {} teleported to warp '{}'", player.getName().getString(), warpName);
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.warp.failed", warpName, result.getMessage()));
               LOGGER.warn("Failed to teleport player {} to warp '{}': {}", new Object[]{player.getName().getString(), warpName, result.getMessage()});
            }
         });
      }
   }

   public String getFormattedWarpsList() {
      if (this.warps.isEmpty()) {
         return MessageUtil.localize("commands.neoessentials.teleport.warp.list_empty");
      } else {
         StringBuilder builder = new StringBuilder();
         builder.append(MessageUtil.localize("commands.neoessentials.teleport.warp.list_header", this.warps.size(), this.maxWarps));
         List<String> sortedNames = new ArrayList<>(this.warps.keySet());
         Collections.sort(sortedNames);

         for (String warpName : sortedNames) {
            TeleportLocation location = this.warps.get(warpName);
            builder.append("\n")
               .append(MessageUtil.localize("commands.neoessentials.teleport.warp.list_entry", warpName, location.getLocationString(), location.getCreatedBy()));
         }

         return builder.toString();
      }
   }

   public int getWarpCount() {
      return this.warps.size();
   }

   private boolean isValidWarpName(String name) {
      if (name != null && !name.trim().isEmpty()) {
         return name.length() > 32 ? false : name.matches("^[a-zA-Z0-9_-]+$");
      } else {
         return false;
      }
   }

   private boolean isOverworld(TeleportLocation location) {
      return location.getWorldName().contains("overworld");
   }

   private void loadWarps() {
      try {
         File file = ResourceUtil.getDataFile("warps.json");
         if (!file.exists()) {
            LOGGER.info("No warps file found, starting with empty warps");
            return;
         }

         String content = Files.readString(file.toPath());
         if (content.trim().isEmpty()) {
            return;
         }

         JsonObject root = JsonParser.parseString(content).getAsJsonObject();
         if (root.has("warps")) {
            JsonObject warpsJson = root.getAsJsonObject("warps");

            for (String warpName : warpsJson.keySet()) {
               try {
                  JsonObject warpJson = warpsJson.getAsJsonObject(warpName);
                  TeleportLocation location = TeleportLocation.fromJson(warpJson);
                  if (location != null) {
                     this.warps.put(warpName, location);
                  }
               } catch (Exception var9) {
                  LOGGER.warn("Failed to load warp '{}': {}", warpName, var9.getMessage());
               }
            }
         }

         if (root.has("config")) {
            JsonObject config = root.getAsJsonObject("config");
            if (config.has("teleportDelay")) {
               this.teleportDelay = config.get("teleportDelay").getAsInt();
            }

            if (config.has("requireSafeLocations")) {
               this.requireSafeLocations = config.get("requireSafeLocations").getAsBoolean();
            }

            if (config.has("allowOverworldOnly")) {
               this.allowOverworldOnly = config.get("allowOverworldOnly").getAsBoolean();
            }

            if (config.has("maxWarps")) {
               this.maxWarps = config.get("maxWarps").getAsInt();
            }

            if (config.has("caseSensitiveNames")) {
               this.caseSensitiveNames = config.get("caseSensitiveNames").getAsBoolean();
            }
         }

         LOGGER.info("Loaded {} warps", this.warps.size());
      } catch (Exception var10) {
         LOGGER.error("Failed to load warps from file", var10);
      }
   }

   private void saveWarps() {
      try {
         JsonObject root = new JsonObject();
         JsonObject warpsJson = new JsonObject();

         for (Entry<String, TeleportLocation> entry : this.warps.entrySet()) {
            warpsJson.add(entry.getKey(), entry.getValue().toJson());
         }

         root.add("warps", warpsJson);
         JsonObject config = new JsonObject();
         config.addProperty("teleportDelay", this.teleportDelay);
         config.addProperty("requireSafeLocations", this.requireSafeLocations);
         config.addProperty("allowOverworldOnly", this.allowOverworldOnly);
         config.addProperty("maxWarps", this.maxWarps);
         config.addProperty("caseSensitiveNames", this.caseSensitiveNames);
         root.add("config", config);
         ResourceUtil.ensureDataDirectory();
         File file = ResourceUtil.getDataFile("warps.json");
         Files.writeString(file.toPath(), this.gson.toJson(root));
      } catch (Exception var5) {
         LOGGER.error("Failed to save warps to file", var5);
      }
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public boolean isRequireSafeLocations() {
      return this.requireSafeLocations;
   }

   public void setRequireSafeLocations(boolean require) {
      this.requireSafeLocations = require;
   }

   public boolean isAllowOverworldOnly() {
      return this.allowOverworldOnly;
   }

   public void setAllowOverworldOnly(boolean allow) {
      this.allowOverworldOnly = allow;
   }

   public int getMaxWarps() {
      return this.maxWarps;
   }

   public void setMaxWarps(int max) {
      this.maxWarps = Math.max(1, max);
   }

   public boolean isCaseSensitiveNames() {
      return this.caseSensitiveNames;
   }

   public void setCaseSensitiveNames(boolean caseSensitive) {
      this.caseSensitiveNames = caseSensitive;
   }

   public void clearAllWarps() {
      this.warps.clear();
      this.saveWarps();
      LOGGER.info("Cleared all warps");
   }

   public String getStatistics() {
      return MessageUtil.localize(
         "commands.neoessentials.teleport.warp.list_statistics", this.warps.size(), this.maxWarps, (double)this.warps.size() * 100.0 / (double)this.maxWarps
      );
   }

   public void reload() {
      LOGGER.info("Reloading warp system...");
      this.loadConfig();
      this.warps.clear();
      this.playerWarps.clear();
      this.loadWarps();
      this.loadPlayerWarps();
      LOGGER.info("Warp system reloaded: {} warps, {} player warps loaded", this.warps.size(), this.playerWarps.values().stream().mapToInt(Map::size).sum());
   }

   private static class SingletonHolder {
      private static final WarpManager INSTANCE = new WarpManager();
   }
}
