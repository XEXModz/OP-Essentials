package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JailManager {
   private static boolean jailSystemEnabledCache = true;
   private static final Logger LOGGER = LoggerFactory.getLogger(JailManager.class);
   private static JailManager instance;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final File jailFile;
   private final File jailLocationFile;
   private final Map<UUID, Integer> jailCounts = new ConcurrentHashMap<>();
   private final Map<UUID, JailManager.JailEntry> jailedPlayers = new ConcurrentHashMap<>();
   private final Map<String, JailManager.JailLocation> jailLocations = new ConcurrentHashMap<>();

   private JailManager() {
      jailSystemEnabledCache = ConfigManager.isJailSystemEnabled();
      if (!jailSystemEnabledCache) {
         LOGGER.info("Jail system is disabled via config. All jail features will be inactive.");
      }

      File moderationDir = new File("neoessentials/moderation");
      if (!moderationDir.exists() && !moderationDir.mkdirs()) {
         LOGGER.error("Failed to create moderation directory: {}", moderationDir.getAbsolutePath());
      }

      this.jailFile = new File(moderationDir, "jailed_players.json");
      this.jailLocationFile = new File(moderationDir, "jail_locations.json");
      this.loadData();
   }

   public static boolean isJailSystemEnabled() {
      return jailSystemEnabledCache;
   }

   public static JailManager getInstance() {
      if (instance == null) {
         instance = new JailManager();
      }

      return instance;
   }

   public boolean jailPlayer(String playerName, UUID playerId, String reason, String jailedBy, String jailName) {
      return this.jailPlayer(playerName, playerId, reason, jailedBy, jailName, 0L);
   }

   public boolean jailPlayer(String playerName, UUID playerId, String reason, String jailedBy, String jailName, long durationMillis) {
      if (this.jailedPlayers.putIfAbsent(playerId, null) != null) {
         return false;
      } else {
         JailManager.JailLocation jailLoc = this.jailLocations.get(jailName);
         if (jailLoc == null) {
            this.jailedPlayers.remove(playerId, null);
            return false;
         } else {
            int jailCount = this.jailCounts.compute(playerId, (id, count) -> (count == null ? 0 : count) + 1);
            int tempBanThreshold = ConfigManager.getMaxJailsBeforeTempBan();
            int permBanThreshold = ConfigManager.getMaxJailsBeforePermBan();
            int tempBanDuration = ConfigManager.getTempBanDurationMinutes();
            if (jailCount >= permBanThreshold) {
               this.jailedPlayers.remove(playerId, null);
               BanManager banManager = BanManager.getInstance();
               banManager.banPlayer(playerName, playerId, "Exceeded maximum jailings (permanent ban)", "System");
               this.jailCounts.put(playerId, 0);
               if (ConfigManager.getInstance().isLogJailActionsEnabled()) {
                  LOGGER.info("Player {} ({}) permanently banned after {} jailings.", new Object[]{playerName, playerId, jailCount});
               }

               return false;
            } else if (jailCount >= tempBanThreshold) {
               this.jailedPlayers.remove(playerId, null);
               BanManager banManager = BanManager.getInstance();
               banManager.tempBanPlayer(playerName, playerId, "Exceeded maximum jailings (temporary ban)", "System", (long)(tempBanDuration * 60) * 1000L);
               if (ConfigManager.getInstance().isLogJailActionsEnabled()) {
                  LOGGER.info("Player {} ({}) temp-banned for {} minutes after {} jailings.", new Object[]{playerName, playerId, tempBanDuration, jailCount});
               }

               return false;
            } else {
               JailManager.JailEntry jail = new JailManager.JailEntry(playerName, playerId, reason, jailedBy, jailName);
               if (durationMillis > 0L) {
                  jail.expireAt = System.currentTimeMillis() + durationMillis;
               }

               MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
               if (server != null) {
                  ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                  if (player != null) {
                     jail.originalLocation = player.blockPosition();
                     jail.originalDimension = player.level().dimension().location().toString();
                     this.jailedPlayers.put(playerId, jail);
                     this.saveJailedPlayers();
                     this.teleportToJail(player, jailLoc);
                     String message = MessageUtil.localize("neoessentials.moderation.jailed_message", reason, jailedBy);
                     player.sendSystemMessage(MessageUtil.warning(message));
                     if (ConfigManager.getInstance().isLogJailActionsEnabled()) {
                        LOGGER.info("Player {} ({}) jailed by {} in {} for: {}", new Object[]{playerName, playerId, jailedBy, jailName, reason});
                     }

                     return true;
                  }
               }

               this.jailedPlayers.put(playerId, jail);
               this.saveJailedPlayers();
               if (ConfigManager.getInstance().isLogJailActionsEnabled()) {
                  LOGGER.info("Player {} ({}) jailed while offline by {} in {} for: {}", new Object[]{playerName, playerId, jailedBy, jailName, reason});
               }

               return true;
            }
         }
      }
   }

   public boolean unjailPlayer(UUID playerId) {
      JailManager.JailEntry jail = this.jailedPlayers.remove(playerId);
      if (jail != null) {
         this.saveJailedPlayers();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
               if (jail.originalLocation != null && jail.originalDimension != null) {
                  this.teleportToOriginalLocation(player, jail);
               }

               String message = MessageUtil.localize("neoessentials.moderation.unjailed_message");
               player.sendSystemMessage(MessageUtil.success(message));
            }
         }

         if (ConfigManager.getInstance().isLogJailActionsEnabled()) {
            LOGGER.info("Player {} ({}) unjailed", jail.playerName, playerId);
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean setJailLocation(String jailName, BlockPos position, String dimension, String createdBy) {
      JailManager.JailLocation jail = new JailManager.JailLocation(jailName, position, dimension, createdBy);
      this.jailLocations.put(jailName, jail);
      this.saveJailLocations();
      LOGGER.info("Jail location '{}' set at {} in {} by {}", new Object[]{jailName, position, dimension, createdBy});
      return true;
   }

   public boolean removeJailLocation(String jailName) {
      JailManager.JailLocation removed = this.jailLocations.remove(jailName);
      if (removed != null) {
         this.saveJailLocations();
         LOGGER.info("Jail location '{}' removed", jailName);
         return true;
      } else {
         return false;
      }
   }

   public boolean isPlayerJailed(UUID playerId) {
      return this.jailedPlayers.containsKey(playerId);
   }

   public JailManager.JailEntry getJailEntry(UUID playerId) {
      return this.jailedPlayers.get(playerId);
   }

   public JailManager.JailLocation getJailLocation(String jailName) {
      return this.jailLocations.get(jailName);
   }

   public List<JailManager.JailEntry> getAllJailedPlayers() {
      return new ArrayList<>(this.jailedPlayers.values());
   }

   public List<JailManager.JailLocation> getAllJailLocations() {
      return new ArrayList<>(this.jailLocations.values());
   }

   public boolean canPlayerMove(ServerPlayer player, BlockPos newPos) {
      UUID playerId = player.getUUID();
      if (!this.isPlayerJailed(playerId)) {
         return true;
      } else {
         JailManager.JailEntry jail = this.getJailEntry(playerId);
         if (jail == null) {
            return true;
         } else {
            JailManager.JailLocation jailLoc = this.getJailLocation(jail.jailName);
            if (jailLoc == null) {
               return true;
            } else {
               double distance = newPos.distSqr(jailLoc.position);
               return distance <= 100.0;
            }
         }
      }
   }

   public void onPlayerJoin(ServerPlayer player) {
      UUID playerId = player.getUUID();
      if (this.isPlayerJailed(playerId)) {
         JailManager.JailEntry jail = this.getJailEntry(playerId);
         if (jail != null) {
            JailManager.JailLocation jailLoc = this.getJailLocation(jail.jailName);
            if (jailLoc == null) {
               this.unjailPlayer(playerId);
            } else {
               boolean teleportOnLogin = ConfigManager.getInstance().isJailTeleportOnLoginEnabled();
               if (teleportOnLogin) {
                  this.teleportToJail(player, jailLoc);
                  String message = MessageUtil.localize("neoessentials.moderation.jail_reminder", jail.reason);
                  player.sendSystemMessage(MessageUtil.warning(message));
               }
            }
         }
      }
   }

   public boolean checkJailTimeout(UUID playerId) {
      JailManager.JailEntry jail = this.jailedPlayers.get(playerId);
      if (jail == null) {
         return false;
      } else if (!jail.isExpired()) {
         return false;
      } else {
         LOGGER.info("Timed jail expired for player {} ({}). Auto-releasing.", jail.playerName, playerId);
         this.unjailPlayer(playerId);
         return true;
      }
   }

   public static String formatDuration(long millis) {
      if (millis <= 0L) {
         return "0s";
      } else {
         long seconds = millis / 1000L;
         long minutes = seconds / 60L;
         long hours = minutes / 60L;
         long days = hours / 24L;
         seconds %= 60L;
         minutes %= 60L;
         hours %= 24L;
         StringBuilder sb = new StringBuilder();
         if (days > 0L) {
            sb.append(days).append("d ");
         }

         if (hours > 0L) {
            sb.append(hours).append("h ");
         }

         if (minutes > 0L) {
            sb.append(minutes).append("m ");
         }

         if (seconds > 0L || sb.length() == 0) {
            sb.append(seconds).append("s");
         }

         return sb.toString().trim();
      }
   }

   private void teleportToJail(ServerPlayer player, JailManager.JailLocation jailLoc) {
      try {
         MinecraftServer server = player.getServer();
         if (server == null) {
            return;
         }

         ResourceKey<Level> dimensionKey = Level.OVERWORLD;
         if (jailLoc.dimension != null && !jailLoc.dimension.isEmpty()) {
            try {
               ResourceLocation dimensionId = ResourceLocation.tryParse(jailLoc.dimension);
               if (dimensionId != null) {
                  dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
               }
            } catch (Exception var6) {
               LOGGER.warn("Failed to parse jail dimension '{}', defaulting to overworld", jailLoc.dimension);
            }
         }

         ServerLevel dimension = server.getLevel(dimensionKey);
         if (dimension != null) {
            player.teleportTo(
               dimension,
               (double)jailLoc.position.getX() + 0.5,
               (double)(jailLoc.position.getY() + 1),
               (double)jailLoc.position.getZ() + 0.5,
               player.getYRot(),
               player.getXRot()
            );
         }
      } catch (Exception var7) {
         LOGGER.error("Failed to teleport player {} to jail {}", new Object[]{player.getName().getString(), jailLoc.name, var7});
      }
   }

   private void teleportToOriginalLocation(ServerPlayer player, JailManager.JailEntry jail) {
      try {
         MinecraftServer server = player.getServer();
         if (server == null || jail.originalLocation == null) {
            return;
         }

         ResourceKey<Level> dimensionKey = Level.OVERWORLD;
         if (jail.originalDimension != null && !jail.originalDimension.isEmpty()) {
            try {
               ResourceLocation dimensionId = ResourceLocation.tryParse(jail.originalDimension);
               if (dimensionId != null) {
                  dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
               }
            } catch (Exception var6) {
               LOGGER.warn("Failed to parse original dimension '{}', defaulting to overworld", jail.originalDimension);
            }
         }

         ServerLevel dimension = server.getLevel(dimensionKey);
         if (dimension != null) {
            player.teleportTo(
               dimension,
               (double)jail.originalLocation.getX() + 0.5,
               (double)(jail.originalLocation.getY() + 1),
               (double)jail.originalLocation.getZ() + 0.5,
               player.getYRot(),
               player.getXRot()
            );
         }
      } catch (Exception var7) {
         LOGGER.error("Failed to teleport player {} back to original location", player.getName().getString(), var7);
      }
   }

   private static String formatTime(long timestamp) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
   }

   private void loadData() {
      this.loadJailedPlayers();
      this.loadJailLocations();
   }

   private void loadJailedPlayers() {
      if (this.jailFile.exists()) {
         try (FileReader reader = new FileReader(this.jailFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("jailed")) {
               for (JsonElement element : root.getAsJsonArray("jailed")) {
                  JsonObject jailObj = element.getAsJsonObject();
                  JailManager.JailEntry jail = new JailManager.JailEntry(
                     jailObj.get("playerName").getAsString(),
                     UUID.fromString(jailObj.get("playerId").getAsString()),
                     jailObj.get("reason").getAsString(),
                     jailObj.get("jailedBy").getAsString(),
                     jailObj.get("jailName").getAsString()
                  );
                  jail.jailTime = jailObj.get("jailTime").getAsLong();
                  jail.expireAt = jailObj.has("expireAt") ? jailObj.get("expireAt").getAsLong() : 0L;
                  if (jailObj.has("originalLocation")) {
                     JsonObject locObj = jailObj.getAsJsonObject("originalLocation");
                     jail.originalLocation = new BlockPos(locObj.get("x").getAsInt(), locObj.get("y").getAsInt(), locObj.get("z").getAsInt());
                  }

                  if (jailObj.has("originalDimension")) {
                     jail.originalDimension = jailObj.get("originalDimension").getAsString();
                  }

                  this.jailedPlayers.put(jail.playerId, jail);
               }
            }
         } catch (IOException var11) {
            LOGGER.error("Failed to load jailed players", var11);
         }
      }
   }

   private void loadJailLocations() {
      if (this.jailLocationFile.exists()) {
         try (FileReader reader = new FileReader(this.jailLocationFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("jails")) {
               for (JsonElement element : root.getAsJsonArray("jails")) {
                  JsonObject jailObj = element.getAsJsonObject();
                  JsonObject posObj = jailObj.getAsJsonObject("position");
                  BlockPos position = new BlockPos(posObj.get("x").getAsInt(), posObj.get("y").getAsInt(), posObj.get("z").getAsInt());
                  JailManager.JailLocation jail = new JailManager.JailLocation(
                     jailObj.get("name").getAsString(), position, jailObj.get("dimension").getAsString(), jailObj.get("createdBy").getAsString()
                  );
                  jail.createdTime = jailObj.get("createdTime").getAsLong();
                  this.jailLocations.put(jail.name, jail);
               }
            }
         } catch (IOException var12) {
            LOGGER.error("Failed to load jail locations", var12);
         }
      }
   }

   private void saveJailedPlayers() {
      try (FileWriter writer = new FileWriter(this.jailFile)) {
         JsonObject root = new JsonObject();
         JsonArray jailedArray = new JsonArray();

         for (JailManager.JailEntry jail : this.jailedPlayers.values()) {
            JsonObject jailObj = new JsonObject();
            jailObj.addProperty("playerName", jail.playerName);
            jailObj.addProperty("playerId", jail.playerId.toString());
            jailObj.addProperty("reason", jail.reason);
            jailObj.addProperty("jailedBy", jail.jailedBy);
            jailObj.addProperty("jailName", jail.jailName);
            jailObj.addProperty("jailTime", jail.jailTime);
            jailObj.addProperty("expireAt", jail.expireAt);
            if (jail.originalLocation != null) {
               JsonObject locObj = new JsonObject();
               locObj.addProperty("x", jail.originalLocation.getX());
               locObj.addProperty("y", jail.originalLocation.getY());
               locObj.addProperty("z", jail.originalLocation.getZ());
               jailObj.add("originalLocation", locObj);
            }

            if (jail.originalDimension != null) {
               jailObj.addProperty("originalDimension", jail.originalDimension);
            }

            jailedArray.add(jailObj);
         }

         root.add("jailed", jailedArray);
         this.gson.toJson(root, writer);
      } catch (IOException var10) {
         LOGGER.error("Failed to save jailed players", var10);
      }
   }

   private void saveJailLocations() {
      try (FileWriter writer = new FileWriter(this.jailLocationFile)) {
         JsonObject root = new JsonObject();
         JsonArray jailsArray = new JsonArray();

         for (JailManager.JailLocation jail : this.jailLocations.values()) {
            JsonObject jailObj = new JsonObject();
            jailObj.addProperty("name", jail.name);
            jailObj.addProperty("dimension", jail.dimension);
            jailObj.addProperty("createdBy", jail.createdBy);
            jailObj.addProperty("createdTime", jail.createdTime);
            JsonObject posObj = new JsonObject();
            posObj.addProperty("x", jail.position.getX());
            posObj.addProperty("y", jail.position.getY());
            posObj.addProperty("z", jail.position.getZ());
            jailObj.add("position", posObj);
            jailsArray.add(jailObj);
         }

         root.add("jails", jailsArray);
         this.gson.toJson(root, writer);
      } catch (IOException var10) {
         LOGGER.error("Failed to save jail locations", var10);
      }
   }

   public void reload() {
      LOGGER.info("Reloading jail system...");
      this.jailedPlayers.clear();
      this.jailLocations.clear();
      this.jailCounts.clear();
      this.loadJailedPlayers();
      this.loadJailLocations();
      LOGGER.info("Jail system reloaded: {} jailed players, {} jail locations", this.jailedPlayers.size(), this.jailLocations.size());
   }

   public static class JailEntry {
      public String playerName;
      public UUID playerId;
      public String reason;
      public String jailedBy;
      public long jailTime;
      public long expireAt;
      public String jailName;
      public BlockPos originalLocation;
      public String originalDimension;

      public JailEntry(String playerName, UUID playerId, String reason, String jailedBy, String jailName) {
         this.playerName = playerName;
         this.playerId = playerId;
         this.reason = reason;
         this.jailedBy = jailedBy;
         this.jailName = jailName;
         this.jailTime = System.currentTimeMillis();
         this.expireAt = 0L;
      }

      public boolean isExpired() {
         return this.expireAt > 0L && System.currentTimeMillis() >= this.expireAt;
      }

      public String getFormattedRemaining() {
         if (this.expireAt <= 0L) {
            return "indefinite";
         } else {
            long remaining = this.expireAt - System.currentTimeMillis();
            return remaining <= 0L ? "expired" : JailManager.formatDuration(remaining);
         }
      }

      public String getFormattedJailTime() {
         return JailManager.formatTime(this.jailTime);
      }
   }

   public static class JailLocation {
      public String name;
      public BlockPos position;
      public String dimension;
      public String createdBy;
      public long createdTime;

      public JailLocation(String name, BlockPos position, String dimension, String createdBy) {
         this.name = name;
         this.position = position;
         this.dimension = dimension;
         this.createdBy = createdBy;
         this.createdTime = System.currentTimeMillis();
      }

      public String getFormattedCreatedTime() {
         return JailManager.formatTime(this.createdTime);
      }
   }
}
