package com.zerog.neoessentials.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerog.neoessentials.chat.handlers.AfkTablistHandler;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.util.DebugLogger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AfkManager {
   private boolean autoSave = true;
   private int saveIntervalSeconds = 60;
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final Map<UUID, AfkManager.PlayerAfkData> playerData = new ConcurrentHashMap<>();
   private final ScheduledExecutorService afkCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "AfkManager-Check");
      t.setDaemon(true);
      return t;
   });
   private volatile boolean isShuttingDown = false;
   private final File afkDataFile = new File("neoessentials/afk_data.json");
   private long afkTimeoutMs = 300000L;
   private boolean autoAfkEnabled = true;
   private boolean broadcastAfkMessages = true;
   private boolean broadcastReturnMessages = true;
   private boolean kickAfkPlayers = false;
   private long afkKickTimeoutMs = 1800000L;
   private String afkMessage = "{player} is now AFK";
   private String returnMessage = "{player} is no longer AFK";
   private String afkKickMessage = null;
   private boolean ignoreAfkInSleep = true;
   private boolean enableTablistIndicator = true;
   private String tablistAfkPrefix = "[AFK] ";
   private String tablistAfkSuffix = "";
   private boolean enableActivityTracking = true;
   private boolean trackMovement = true;
   private boolean trackChat = true;
   private boolean trackCommands = true;
   private boolean trackInteractions = true;
   private double rotationThreshold = 5.0;
   private Set<String> excludedCommands = new HashSet<>(Arrays.asList("afk", "list", "who", "tps", "ping", "help", "?"));

   public static AfkManager getInstance() {
      return AfkManager.SingletonHelper.INSTANCE;
   }

   private AfkManager() {
      this.loadAfkData();
      this.startAfkCheckTask();
      this.startAutoSaveTask();
   }

   public void updateActivity(UUID playerUuid) {
      AfkManager.PlayerAfkData data = this.playerData.computeIfAbsent(playerUuid, k -> new AfkManager.PlayerAfkData());
      data.setLastActivity(System.currentTimeMillis());
      if (data.isAfk()) {
         this.setAfkStatus(playerUuid, false, null);
      }
   }

   public boolean isAfk(UUID playerUuid) {
      AfkManager.PlayerAfkData data = this.playerData.get(playerUuid);
      return data != null && data.isAfk();
   }

   public boolean isAfk(ServerPlayer player) {
      return this.isAfk(player.getUUID());
   }

   public void toggleAfk(ServerPlayer player, String reason) {
      UUID uuid = player.getUUID();
      AfkManager.PlayerAfkData data = this.playerData.computeIfAbsent(uuid, k -> new AfkManager.PlayerAfkData());
      this.setAfkStatus(uuid, !data.isAfk(), reason);
   }

   public void setAfkStatus(UUID playerUuid, boolean afk, String reason) {
      AfkManager.PlayerAfkData data = this.playerData.computeIfAbsent(playerUuid, k -> new AfkManager.PlayerAfkData());
      boolean wasAfk = data.isAfk();
      data.setAfk(afk);
      data.setAfkReason(reason);
      if (afk && !wasAfk) {
         data.setAfkStartTime(System.currentTimeMillis());
         this.onPlayerGoAfk(playerUuid, reason);

         try {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
               ServerPlayer p = srv.getPlayerList().getPlayer(playerUuid);
               if (p != null) {
                  ChatIntegrationManager.broadcastAfkEvent(p, true, reason);
               }
            }
         } catch (Exception var9) {
         }
      } else if (!afk && wasAfk) {
         data.setAfkStartTime(0L);
         data.setLastActivity(System.currentTimeMillis());
         this.onPlayerReturnFromAfk(playerUuid);

         try {
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
               ServerPlayer p = srv.getPlayerList().getPlayer(playerUuid);
               if (p != null) {
                  ChatIntegrationManager.broadcastAfkEvent(p, false, null);
               }
            }
         } catch (Exception var8) {
         }
      }

      this.queueSaveAfkData();
   }

   public String getAfkReason(UUID playerUuid) {
      AfkManager.PlayerAfkData data = this.playerData.get(playerUuid);
      return data != null ? data.getAfkReason() : null;
   }

   public long getAfkDuration(UUID playerUuid) {
      AfkManager.PlayerAfkData data = this.playerData.get(playerUuid);
      return data != null && data.isAfk() ? System.currentTimeMillis() - data.getAfkStartTime() : 0L;
   }

   private void onPlayerGoAfk(UUID playerUuid, String reason) {
      if (this.broadcastAfkMessages) {
         try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
               return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
               return;
            }

            String message = this.afkMessage.replace("{player}", player.getName().getString());
            if (reason != null && !reason.trim().isEmpty() && !reason.equals("Inactive")) {
               message = message + " (" + reason + ")";
            }

            Component afkComponent = Component.literal("§e" + message);
            server.getPlayerList().broadcastSystemMessage(afkComponent, false);
            server.sendSystemMessage(afkComponent);
            if ("Inactive".equals(reason)) {
               player.sendSystemMessage(Component.literal("§eYou are now AFK due to inactivity."));
            }

            LOGGER.info("Player {} went AFK{}", player.getName().getString(), reason != null && !reason.equals("Inactive") ? " (" + reason + ")" : "");
            AfkTablistHandler.onPlayerAfk(player);
         } catch (Exception var7) {
            LOGGER.error("Error broadcasting AFK message", var7);
         }
      }
   }

   private void onPlayerReturnFromAfk(UUID playerUuid) {
      if (this.broadcastReturnMessages) {
         try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
               return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
               return;
            }

            String message = this.returnMessage.replace("{player}", player.getName().getString());
            Component returnComponent = Component.literal("§e" + message);
            server.getPlayerList().broadcastSystemMessage(returnComponent, false);
            server.sendSystemMessage(returnComponent);
            LOGGER.info("Player {} returned from AFK", player.getName().getString());
            AfkTablistHandler.onPlayerReturnFromAfk(player);
         } catch (Exception var6) {
            LOGGER.error("Error broadcasting return message", var6);
         }
      }
   }

   private void startAfkCheckTask() {
      if (this.autoAfkEnabled) {
         this.afkCheckExecutor.scheduleAtFixedRate(() -> {
            try {
               this.checkForAfkPlayers();
            } catch (Exception var2) {
               LOGGER.error("Error in AFK check task", var2);
            }
         }, 30L, 30L, TimeUnit.SECONDS);
      }
   }

   private void checkForAfkPlayers() {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         long currentTime = System.currentTimeMillis();

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            AfkManager.PlayerAfkData data = this.playerData.get(uuid);
            if (data != null) {
               if (!data.isAfk() && currentTime - data.getLastActivity() > this.afkTimeoutMs) {
                  this.setAfkStatus(uuid, true, "Inactive");
               }

               if (this.kickAfkPlayers && this.afkKickTimeoutMs > 0L && data.isAfk()) {
                  long afkDuration = currentTime - data.getAfkStartTime();
                  if (afkDuration > this.afkKickTimeoutMs - 60000L && afkDuration < this.afkKickTimeoutMs) {
                     long secondsUntilKick = (this.afkKickTimeoutMs - afkDuration) / 1000L;
                     if (secondsUntilKick > 0L && secondsUntilKick <= 60L) {
                        player.sendSystemMessage(
                           Component.literal(
                              String.format("§c§lWARNING: §eYou will be kicked for AFK in %d seconds! Move to stay connected.", secondsUntilKick)
                           )
                        );
                        LOGGER.warn("Player {} will be kicked for AFK in {} seconds", player.getName().getString(), secondsUntilKick);
                     }
                  }

                  if (afkDuration > this.afkKickTimeoutMs) {
                     try {
                        String kickMsg = this.afkKickMessage != null && !this.afkKickMessage.isEmpty()
                           ? this.afkKickMessage
                           : "§cKicked for being AFK too long.\n§7You were inactive for " + afkDuration / 60000L + " minutes.";
                        player.connection.disconnect(Component.literal(kickMsg));
                        LOGGER.info("Kicked player {} for being AFK too long (AFK for {} minutes)", player.getName().getString(), afkDuration / 60000L);
                     } catch (Exception var12) {
                        LOGGER.error("Error kicking AFK player {}", player.getName().getString(), var12);
                     }
                  }
               }
            }
         }
      }
   }

   public void loadConfiguration(JsonObject afkConfig) {
      if (afkConfig.has("autoSave")) {
         this.autoSave = afkConfig.get("autoSave").getAsBoolean();
      } else {
         this.autoSave = true;
      }

      if (afkConfig.has("saveInterval")) {
         try {
            this.saveIntervalSeconds = Math.max(10, afkConfig.get("saveInterval").getAsInt());
         } catch (Exception var6) {
            this.saveIntervalSeconds = 60;
            LOGGER.warn("Invalid value for saveInterval in config, using default 60s");
         }
      } else {
         this.saveIntervalSeconds = 60;
      }

      if (afkConfig.has("timeout")) {
         long timeoutSeconds = afkConfig.get("timeout").getAsLong();
         this.afkTimeoutMs = timeoutSeconds > 0L ? timeoutSeconds * 1000L : 300000L;
      } else if (afkConfig.has("timeoutMinutes")) {
         long timeoutMinutes = afkConfig.get("timeoutMinutes").getAsLong();
         this.afkTimeoutMs = timeoutMinutes > 0L ? timeoutMinutes * 60000L : 300000L;
      } else {
         this.afkTimeoutMs = 300000L;
      }

      this.autoAfkEnabled = !afkConfig.has("autoAfkEnabled") || afkConfig.get("autoAfkEnabled").getAsBoolean();
      if (afkConfig.has("broadcastOnAfk")) {
         this.broadcastAfkMessages = afkConfig.get("broadcastOnAfk").getAsBoolean();
      } else if (afkConfig.has("enableafkBroadcasts")) {
         this.broadcastAfkMessages = afkConfig.get("enableafkBroadcasts").getAsBoolean();
      } else {
         this.broadcastAfkMessages = !afkConfig.has("broadcastMessages") || afkConfig.get("broadcastMessages").getAsBoolean();
      }

      if (afkConfig.has("broadcastOnReturn")) {
         this.broadcastReturnMessages = afkConfig.get("broadcastOnReturn").getAsBoolean();
      } else {
         this.broadcastReturnMessages = this.broadcastAfkMessages;
      }

      this.kickAfkPlayers = afkConfig.has("kickAfkPlayers") && afkConfig.get("kickAfkPlayers").getAsBoolean();
      LOGGER.info("AFK kick feature: {}", this.kickAfkPlayers ? "ENABLED" : "DISABLED");
      if (afkConfig.has("kickTimeout")) {
         long kickTimeoutSeconds = afkConfig.get("kickTimeout").getAsLong();
         this.afkKickTimeoutMs = kickTimeoutSeconds > 0L ? kickTimeoutSeconds * 1000L : 0L;
         if (this.kickAfkPlayers && kickTimeoutSeconds > 0L) {
            LOGGER.info("AFK kick timeout: {} seconds ({} minutes)", kickTimeoutSeconds, kickTimeoutSeconds / 60L);
         }
      } else if (afkConfig.has("kickTimeoutMinutes")) {
         long kickTimeoutMinutes = afkConfig.get("kickTimeoutMinutes").getAsLong();
         this.afkKickTimeoutMs = kickTimeoutMinutes > 0L ? kickTimeoutMinutes * 60000L : 0L;
         if (this.kickAfkPlayers && kickTimeoutMinutes > 0L) {
            LOGGER.info("AFK kick timeout: {} minutes", kickTimeoutMinutes);
         }
      } else {
         this.afkKickTimeoutMs = 1800000L;
      }

      if (this.kickAfkPlayers && this.afkKickTimeoutMs == 0L) {
         LOGGER.error("AFK kick is ENABLED but timeout is 0! This would kick players immediately. Setting to 30 minutes default.");
         this.afkKickTimeoutMs = 1800000L;
      }

      this.afkMessage = afkConfig.has("afkMessage") ? afkConfig.get("afkMessage").getAsString() : "{player} is now AFK";
      this.returnMessage = afkConfig.has("returnMessage") ? afkConfig.get("returnMessage").getAsString() : "{player} is no longer AFK";
      this.afkKickMessage = afkConfig.has("afkkickMessage") ? afkConfig.get("afkkickMessage").getAsString() : null;
      if (afkConfig.has("ignoreAfkInSleep")) {
         this.ignoreAfkInSleep = afkConfig.get("ignoreAfkInSleep").getAsBoolean();
      }

      if (afkConfig.has("enableActivityTracking")) {
         this.enableActivityTracking = afkConfig.get("enableActivityTracking").getAsBoolean();
      }

      if (afkConfig.has("trackMovement")) {
         this.trackMovement = afkConfig.get("trackMovement").getAsBoolean();
      }

      if (afkConfig.has("trackChat")) {
         this.trackChat = afkConfig.get("trackChat").getAsBoolean();
      }

      if (afkConfig.has("trackCommands")) {
         this.trackCommands = afkConfig.get("trackCommands").getAsBoolean();
      }

      if (afkConfig.has("trackInteractions")) {
         this.trackInteractions = afkConfig.get("trackInteractions").getAsBoolean();
      }

      if (afkConfig.has("rotationThreshold")) {
         try {
            this.rotationThreshold = afkConfig.get("rotationThreshold").getAsDouble();
         } catch (Exception var5) {
            this.rotationThreshold = 5.0;
            LOGGER.warn("Invalid value for rotationThreshold in config, using default 5.0");
         }
      } else {
         this.rotationThreshold = 5.0;
      }

      if (afkConfig.has("excludedCommands") && afkConfig.get("excludedCommands").isJsonArray()) {
         try {
            Set<String> newSet = new HashSet<>();

            for (JsonElement el : afkConfig.get("excludedCommands").getAsJsonArray()) {
               newSet.add(el.getAsString().toLowerCase());
            }

            if (!newSet.isEmpty()) {
               this.excludedCommands = newSet;
            }
         } catch (Exception var7) {
            LOGGER.warn("Invalid value for excludedCommands in config, using default list");
         }
      }

      LOGGER.info(
         "AFK configuration loaded: timeout={}min, autoAfk={}, broadcast={}, kick={}",
         new Object[]{this.afkTimeoutMs / 60000L, this.autoAfkEnabled, this.broadcastAfkMessages, this.kickAfkPlayers}
      );
   }

   public void onPlayerLogout(UUID playerUuid) {
      this.playerData.remove(playerUuid);
      this.queueSaveAfkData();
   }

   private void loadAfkData() {
      if (this.afkDataFile.exists()) {
         try (FileReader reader = new FileReader(this.afkDataFile)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();

            for (Entry<String, JsonElement> entry : data.entrySet()) {
               try {
                  UUID uuid = UUID.fromString(entry.getKey());
                  JsonObject playerJson = entry.getValue().getAsJsonObject();
                  AfkManager.PlayerAfkData playerAfkData = new AfkManager.PlayerAfkData();
                  playerAfkData.setLastActivity(playerJson.get("lastActivity").getAsLong());
                  this.playerData.put(uuid, playerAfkData);
               } catch (Exception var9) {
                  LOGGER.warn("Failed to load AFK data for entry: {}", entry.getKey());
               }
            }

            LOGGER.info("Loaded AFK data for {} players", this.playerData.size());
         } catch (Exception var11) {
            LOGGER.error("Failed to load AFK data", var11);
         }
      }
   }

   private void queueSaveAfkData() {
      if (!this.isShuttingDown && !this.afkCheckExecutor.isShutdown()) {
         try {
            this.afkCheckExecutor.execute(this::saveAfkData);
         } catch (RejectedExecutionException var2) {
            LOGGER.debug("Cannot queue AFK data save - executor is shutting down");
         }
      }
   }

   private void saveAfkData() {
      try {
         File parentDir = this.afkDataFile.getParentFile();
         if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            LOGGER.error("Failed to create AFK data directory: {}", parentDir.getAbsolutePath());
            return;
         }

         JsonObject data = new JsonObject();

         for (Entry<UUID, AfkManager.PlayerAfkData> entry : this.playerData.entrySet()) {
            JsonObject playerJson = new JsonObject();
            AfkManager.PlayerAfkData playerData = entry.getValue();
            playerJson.addProperty("lastActivity", playerData.getLastActivity());
            data.add(entry.getKey().toString(), playerJson);
         }

         File tempFile = new File(this.afkDataFile.getAbsolutePath() + ".tmp");

         try (FileWriter writer = new FileWriter(tempFile)) {
            GSON.toJson(data, writer);
         }

         Files.move(tempFile.toPath(), this.afkDataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception var9) {
         LOGGER.error("Failed to save AFK data", var9);
      }
   }

   public boolean isIgnoreAfkInSleep() {
      return this.ignoreAfkInSleep;
   }

   public boolean isEnableTablistIndicator() {
      return this.enableTablistIndicator;
   }

   public String getTablistAfkPrefix() {
      return this.tablistAfkPrefix;
   }

   public String getTablistAfkSuffix() {
      return this.tablistAfkSuffix;
   }

   public boolean isEnableActivityTracking() {
      return this.enableActivityTracking;
   }

   public boolean isTrackMovement() {
      return this.trackMovement;
   }

   public boolean isTrackChat() {
      return this.trackChat;
   }

   public boolean isTrackCommands() {
      return this.trackCommands;
   }

   public boolean isTrackInteractions() {
      return this.trackInteractions;
   }

   public double getRotationThreshold() {
      return this.rotationThreshold;
   }

   public boolean isAutoSave() {
      return this.autoSave;
   }

   public int getSaveIntervalSeconds() {
      return this.saveIntervalSeconds;
   }

   public Set<String> getExcludedCommands() {
      return this.excludedCommands;
   }

   public void shutdown() {
      LOGGER.info("Shutting down AFK Manager...");
      this.isShuttingDown = true;

      try {
         this.saveAfkData();
         this.afkCheckExecutor.shutdown();
         if (!this.afkCheckExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
            LOGGER.warn("AFK Manager executor did not terminate gracefully, forcing shutdown...");
            this.afkCheckExecutor.shutdownNow();
         }

         LOGGER.info("AFK Manager shutdown complete");
      } catch (InterruptedException var2) {
         LOGGER.warn("Interrupted while waiting for AFK Manager executor shutdown");
         this.afkCheckExecutor.shutdownNow();
         Thread.currentThread().interrupt();
      } catch (Exception var3) {
         LOGGER.error("Error during AFK Manager shutdown", var3);
      }
   }

   private void startAutoSaveTask() {
      this.afkCheckExecutor.scheduleAtFixedRate(() -> {
         if (this.autoSave) {
            try {
               this.saveAfkData();
               DebugLogger.log(LOGGER, "AFK data auto-saved");
            } catch (Exception var2) {
               LOGGER.error("Error during AFK data auto-save", var2);
            }
         }
      }, (long)this.saveIntervalSeconds, (long)this.saveIntervalSeconds, TimeUnit.SECONDS);
   }

   public void reload() {
      LOGGER.info("Reloading AFK system...");
      LOGGER.info("AFK system reloaded");
   }

   public static class PlayerAfkData {
      private boolean isAfk = false;
      private long lastActivity = System.currentTimeMillis();
      private String afkReason = null;
      private long afkStartTime = 0L;

      public boolean isAfk() {
         return this.isAfk;
      }

      public void setAfk(boolean afk) {
         this.isAfk = afk;
      }

      public long getLastActivity() {
         return this.lastActivity;
      }

      public void setLastActivity(long time) {
         this.lastActivity = time;
      }

      public String getAfkReason() {
         return this.afkReason;
      }

      public void setAfkReason(String reason) {
         this.afkReason = reason;
      }

      public long getAfkStartTime() {
         return this.afkStartTime;
      }

      public void setAfkStartTime(long time) {
         this.afkStartTime = time;
      }
   }

   private static class SingletonHelper {
      private static final AfkManager INSTANCE = new AfkManager();
   }
}
