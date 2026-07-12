package com.zerog.neoessentials.teleportation.Spawn;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpawnManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(SpawnManager.class);
   private static final String SPAWN_FILE = "spawn.json";
   private TeleportLocation spawnLocation;
   private int teleportDelay = 0;
   private boolean requireSafeLocation = true;
   private boolean allowSetSpawnInNether = false;
   private boolean allowSetSpawnInEnd = false;

   public static SpawnManager getInstance() {
      return SpawnManager.SingletonHolder.INSTANCE;
   }

   private SpawnManager() {
      this.loadConfig();
      this.loadSpawn();
   }

   private void loadConfig() {
      try {
         ConfigManager configManager = ConfigManager.getInstance();
         boolean safe = true;
         if (configManager != null) {
            JsonObject config = configManager.getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("spawnSettings")) {
                  JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                  if (spawnSettings.has("enableSpawnSafety")) {
                     safe = spawnSettings.get("enableSpawnSafety").getAsBoolean();
                  }
               }
            }
         }

         this.requireSafeLocation = safe;
      } catch (Exception var6) {
         LOGGER.warn("Failed to load spawn safety config, defaulting to safe: {}", var6.getMessage());
      }
   }

   public boolean setSpawn(ServerPlayer setter, TeleportLocation location) {
      if (location == null) {
         setter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.invalid_location"));
         return false;
      } else {
         String worldName = location.getWorldName();
         if (!this.allowSetSpawnInNether && worldName.contains("nether")) {
            setter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.no_nether"));
            return false;
         } else if (!this.allowSetSpawnInEnd && worldName.contains("end")) {
            setter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.no_end"));
            return false;
         } else {
            if (this.requireSafeLocation && !location.isSafe()) {
               TeleportLocation safeLocation = location.findSafeLocation();
               if (safeLocation == null) {
                  setter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.unsafe_location"));
                  return false;
               }

               location = safeLocation;
               setter.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.spawn.moved_to_safety"));
            }

            this.spawnLocation = location;
            this.saveSpawn();
            setter.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.spawn.set", location.getLocationString()));
            if (ConfigManager.getInstance().isLogSpawnActionsEnabled()) {
               LOGGER.info("Player {} set server spawn to {}", setter.getName().getString(), location.getLocationString());
            }

            return true;
         }
      }
   }

   public boolean setSpawn(ServerPlayer setter) {
      TeleportLocation location = new TeleportLocation(setter);
      return this.setSpawn(setter, location);
   }

   public boolean setSpawn(ServerPlayer setter, ServerLevel level, BlockPos pos) {
      TeleportLocation location = new TeleportLocation(level, pos, 0.0F, 0.0F, setter.getName().getString());
      return this.setSpawn(setter, location);
   }

   public TeleportLocation getSpawn() {
      return this.spawnLocation;
   }

   public boolean hasSpawn() {
      return this.spawnLocation != null;
   }

   public void teleportToSpawn(ServerPlayer player) {
      if (this.spawnLocation == null) {
         this.teleportToWorldSpawn(player);
      } else {
         int maxDistance = ConfigManager.getInstance().getMaxTeleportDistance();
         if (maxDistance > 0 && this.spawnLocation != null) {
            TeleportLocation fromLoc = new TeleportLocation(player);
            if (fromLoc.getWorldName().equals(this.spawnLocation.getWorldName())) {
               double dist = fromLoc.distanceTo(this.spawnLocation);
               if (dist > (double)maxDistance) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.distance_exceeded", maxDistance));
                  return;
               }
            }
         }

         if (this.spawnLocation != null && this.requireSafeLocation && !this.spawnLocation.isSafe()) {
            TeleportLocation safeLocation = this.spawnLocation.findSafeLocation();
            if (safeLocation == null) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.unsafe"));
               this.teleportToWorldSpawn(player);
               return;
            }

            this.spawnLocation = safeLocation;
            this.saveSpawn();
            player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.spawn.moved_to_safety"));
         }

         MiscTeleportManager.getInstance().saveBackLocation(player);
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, this.spawnLocation, delayTicks, this.requireSafeLocation).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.spawn.success"));
               if (ConfigManager.getInstance().isLogSpawnActionsEnabled()) {
                  LOGGER.info("Player {} teleported to spawn", player.getName().getString());
               }
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.failed", result.getMessage()));
               LOGGER.warn("Failed to teleport player {} to spawn: {}", player.getName().getString(), result.getMessage());
               this.teleportToWorldSpawn(player);
            }
         });
      }
   }

   private void teleportToWorldSpawn(ServerPlayer player) {
      try {
         MinecraftServer server = player.getServer();
         if (server == null) {
            LOGGER.error("Cannot teleport to world spawn - server is null");
            return;
         }

         ServerLevel overworld = server.overworld();
         BlockPos worldSpawn = overworld.getSharedSpawnPos();
         TeleportLocation fallbackLocation = new TeleportLocation(overworld, worldSpawn, 0.0F, 0.0F, "world");
         TeleportUtil.teleportPlayer(player, fallbackLocation, 0, true).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.spawn.fallback_success"));
               if (ConfigManager.getInstance().isLogSpawnActionsEnabled()) {
                  LOGGER.info("Player {} teleported to world spawn fallback", player.getName().getString());
               }
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.fallback_failed", result.getMessage()));
               LOGGER.error("Failed to teleport player {} to world spawn fallback: {}", player.getName().getString(), result.getMessage());
            }
         });
      } catch (Exception var6) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.critical_failure"));
         LOGGER.error("Critical failure in spawn teleportation for player {}: {}", new Object[]{player.getName().getString(), var6.getMessage(), var6});
      }
   }

   public String getSpawnInfo() {
      return this.spawnLocation == null
         ? MessageUtil.localize("commands.neoessentials.teleport.spawn.info_not_set")
         : MessageUtil.localize("commands.neoessentials.teleport.spawn.info", this.spawnLocation.getLocationString(), this.spawnLocation.getCreatedBy());
   }

   public boolean clearSpawn(ServerPlayer clearer) {
      this.spawnLocation = null;
      this.saveSpawn();
      clearer.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.spawn.cleared"));
      if (ConfigManager.getInstance().isLogSpawnActionsEnabled()) {
         LOGGER.info("Player {} cleared server spawn", clearer.getName().getString());
      }

      return true;
   }

   private void loadSpawn() {
      try {
         File file = ResourceUtil.getDataFile("spawn.json");
         if (!file.exists()) {
            LOGGER.info("No spawn file found, using world spawn");
            return;
         }

         String content = Files.readString(file.toPath());
         if (content.trim().isEmpty()) {
            return;
         }

         JsonObject root = JsonParser.parseString(content).getAsJsonObject();
         if (root.has("spawn")) {
            JsonObject spawnJson = root.getAsJsonObject("spawn");
            this.spawnLocation = TeleportLocation.fromJson(spawnJson);
            if (this.spawnLocation != null) {
               LOGGER.info("Loaded spawn location: {}", this.spawnLocation.getLocationString());
            } else {
               LOGGER.warn("Failed to parse spawn location from file");
            }
         }

         if (root.has("config")) {
            JsonObject config = root.getAsJsonObject("config");
            if (config.has("teleportDelay")) {
               this.teleportDelay = config.get("teleportDelay").getAsInt();
            }

            if (config.has("requireSafeLocation")) {
               this.requireSafeLocation = config.get("requireSafeLocation").getAsBoolean();
            }

            if (config.has("allowSetSpawnInNether")) {
               this.allowSetSpawnInNether = config.get("allowSetSpawnInNether").getAsBoolean();
            }

            if (config.has("allowSetSpawnInEnd")) {
               this.allowSetSpawnInEnd = config.get("allowSetSpawnInEnd").getAsBoolean();
            }
         }
      } catch (Exception var5) {
         LOGGER.error("Failed to load spawn from file", var5);
      }
   }

   private void saveSpawn() {
      try {
         JsonObject root = new JsonObject();
         if (this.spawnLocation != null) {
            root.add("spawn", this.spawnLocation.toJson());
         }

         JsonObject config = new JsonObject();
         config.addProperty("teleportDelay", this.teleportDelay);
         config.addProperty("requireSafeLocation", this.requireSafeLocation);
         config.addProperty("allowSetSpawnInNether", this.allowSetSpawnInNether);
         config.addProperty("allowSetSpawnInEnd", this.allowSetSpawnInEnd);
         root.add("config", config);
         ResourceUtil.ensureDataDirectory();
         File file = ResourceUtil.getDataFile("spawn.json");
         Files.writeString(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
      } catch (Exception var4) {
         LOGGER.error("Failed to save spawn to file", var4);
      }
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public boolean isRequireSafeLocation() {
      return this.requireSafeLocation;
   }

   public void setRequireSafeLocation(boolean require) {
      this.requireSafeLocation = require;
   }

   public boolean isAllowSetSpawnInNether() {
      return this.allowSetSpawnInNether;
   }

   public void setAllowSetSpawnInNether(boolean allow) {
      this.allowSetSpawnInNether = allow;
   }

   public boolean isAllowSetSpawnInEnd() {
      return this.allowSetSpawnInEnd;
   }

   public void setAllowSetSpawnInEnd(boolean allow) {
      this.allowSetSpawnInEnd = allow;
   }

   public String getStatistics() {
      return String.format(
         "Spawn Statistics: %s, Safe location required: %s, Teleport delay: %ds",
         this.hasSpawn() ? "Set at " + this.spawnLocation.getLocationString() : "Not set",
         this.requireSafeLocation,
         this.teleportDelay
      );
   }

   public void reload() {
      LOGGER.info("Reloading spawn system...");
      this.loadConfig();
      this.spawnLocation = null;
      this.loadSpawn();
      LOGGER.info("Spawn system reloaded: {}", this.hasSpawn() ? "Spawn loaded" : "No spawn set");
   }

   private static class SingletonHolder {
      private static final SpawnManager INSTANCE = new SpawnManager();
   }
}
