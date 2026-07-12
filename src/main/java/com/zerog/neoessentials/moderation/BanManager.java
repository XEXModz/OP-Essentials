package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanManager {
   private final ScheduledExecutorService banCleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "BanManager-Cleanup");
      t.setDaemon(true);
      return t;
   });
   private ScheduledFuture<?> cleanupTaskFuture;
   private static volatile boolean isShuttingDown = false;
   private static final Logger LOGGER = LoggerFactory.getLogger(BanManager.class);
   private static BanManager instance;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final File banFile;
   private final File ipBanFile;
   private final Map<UUID, BanManager.BanEntry> playerBans = new ConcurrentHashMap<>();
   private final Map<String, BanManager.IPBanEntry> ipBans = new ConcurrentHashMap<>();

   private BanManager() {
      File moderationDir = new File("neoessentials/moderation");
      if (!moderationDir.exists() && !moderationDir.mkdirs()) {
         LOGGER.error("Failed to create moderation directory: {}", moderationDir.getAbsolutePath());
      }

      this.banFile = new File(moderationDir, "player_bans.json");
      this.ipBanFile = new File(moderationDir, "ip_bans.json");
      this.loadBans();
      if (!isShuttingDown) {
         int interval = ConfigManager.getInstance().getCheckExpiredBansInterval();
         if (interval > 0) {
            this.cleanupTaskFuture = this.banCleanupScheduler
               .scheduleAtFixedRate(this::cleanupExpiredTempBans, (long)interval, (long)interval, TimeUnit.SECONDS);
            LOGGER.info("Scheduled expired temp ban cleanup every {} seconds.", interval);
         } else {
            LOGGER.info("Expired temp ban cleanup scheduler is disabled (interval <= 0).");
         }
      } else {
         LOGGER.debug("BanManager created during shutdown - scheduler not started");
      }
   }

   public static BanManager getInstance() {
      if (instance == null) {
         instance = new BanManager();
      }

      return instance;
   }

   private void cleanupExpiredTempBans() {
      boolean removedAny = false;
      Iterator<Entry<UUID, BanManager.BanEntry>> iterator = this.playerBans.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, BanManager.BanEntry> entry = iterator.next();
         BanManager.BanEntry ban = entry.getValue();
         if (ban.isExpired() && ConfigManager.getInstance().isAutoExpireTempBansEnabled()) {
            iterator.remove();
            removedAny = true;
         }
      }

      if (removedAny) {
         this.saveBans();
         LOGGER.info("Expired temp bans cleaned up by scheduler.");
      }
   }

   public void shutdownScheduler() {
      isShuttingDown = true;
      if (this.cleanupTaskFuture != null) {
         this.cleanupTaskFuture.cancel(false);
      }

      this.banCleanupScheduler.shutdown();

      try {
         if (!this.banCleanupScheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.banCleanupScheduler.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.banCleanupScheduler.shutdownNow();
      }
   }

   public boolean banPlayer(String playerName, UUID playerId, String reason, String bannedBy) {
      if (this.isPlayerBanned(playerId)) {
         return false;
      } else if (!ConfigManager.getInstance().isPermanentBansEnabled()) {
         LOGGER.warn("Permanent bans are disabled in config. Cannot ban player {} permanently.", playerName);
         return false;
      } else {
         int maxReason = ConfigManager.getInstance().getMaxBanReasonLength();
         if (reason != null && reason.length() > maxReason) {
            LOGGER.warn("Ban reason too long ({} > {}). Cannot ban player {}.", new Object[]{reason.length(), maxReason, playerName});
            return false;
         } else {
            BanManager.BanEntry ban = new BanManager.BanEntry(playerName, playerId, reason, bannedBy);
            this.playerBans.put(playerId, ban);
            this.saveBans();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  String format = ConfigManager.getBanMessageFormat();
                  String duration = "Permanent";
                  String message = format.replace("{reason}", reason != null ? reason : "N/A")
                     .replace("{bannedBy}", bannedBy != null ? bannedBy : "Console")
                     .replace("{duration}", duration);
                  player.connection.disconnect(Component.literal(message));
               }

               if (ConfigManager.getInstance().isBroadcastBansEnabled()) {
                  String staffPerm = ConfigManager.getInstance().getStaffNotificationPermission();
                  String staffMsg = "[NeoEssentials] Player "
                     + playerName
                     + " was permanently banned by "
                     + bannedBy
                     + (reason != null && !reason.isEmpty() ? " for: " + reason : "");

                  for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                     if (staff.hasPermissions(2) || PermissionAPI.hasPermission(staff.getUUID(), staffPerm)) {
                        staff.sendSystemMessage(Component.literal(staffMsg));
                     }
                  }
               }
            }

            if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
               LOGGER.info("Player {} ({}) banned by {} for: {}", new Object[]{playerName, playerId, bannedBy, reason});
            }

            return true;
         }
      }
   }

   public boolean tempBanPlayer(String playerName, UUID playerId, String reason, String bannedBy, long durationMillis) {
      if (this.isPlayerBanned(playerId)) {
         return false;
      } else if (!ConfigManager.getInstance().isTempBansEnabled()) {
         LOGGER.warn("Temporary bans are disabled in config. Cannot temp-ban player {}.", playerName);
         return false;
      } else {
         int maxReason = ConfigManager.getInstance().getMaxBanReasonLength();
         if (reason != null && reason.length() > maxReason) {
            LOGGER.warn("Temp ban reason too long ({} > {}). Cannot temp-ban player {}.", new Object[]{reason.length(), maxReason, playerName});
            return false;
         } else {
            BanManager.BanEntry ban = new BanManager.BanEntry(playerName, playerId, reason, bannedBy);
            ban.expireTime = System.currentTimeMillis() + durationMillis;
            this.playerBans.put(playerId, ban);
            this.saveBans();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  String format = ConfigManager.getTempBanMessageFormat();
                  String duration = formatDuration(durationMillis);
                  String expiry = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                     .withZone(ZoneId.systemDefault())
                     .format(Instant.ofEpochMilli(System.currentTimeMillis() + durationMillis));
                  String message = format.replace("{reason}", reason != null ? reason : "N/A")
                     .replace("{bannedBy}", bannedBy != null ? bannedBy : "Console")
                     .replace("{duration}", duration)
                     .replace("{expiry}", expiry);
                  player.connection.disconnect(Component.literal(message));
               }

               if (ConfigManager.getInstance().isBroadcastBansEnabled()) {
                  String staffPerm = ConfigManager.getInstance().getStaffNotificationPermission();
                  String staffMsg = "[NeoEssentials] Player "
                     + playerName
                     + " was temporarily banned by "
                     + bannedBy
                     + " for "
                     + formatDuration(durationMillis)
                     + (reason != null && !reason.isEmpty() ? " - Reason: " + reason : "");

                  for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                     if (staff.hasPermissions(2) || PermissionAPI.hasPermission(staff.getUUID(), staffPerm)) {
                        staff.sendSystemMessage(Component.literal(staffMsg));
                     }
                  }
               }
            }

            if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
               LOGGER.info(
                  "Player {} ({}) temporarily banned by {} for {} - Reason: {}",
                  new Object[]{playerName, playerId, bannedBy, formatDuration(durationMillis), reason}
               );
            }

            return true;
         }
      }
   }

   public boolean banIP(String ipAddress, String reason, String bannedBy) {
      if (this.isIPBanned(ipAddress)) {
         return false;
      } else if (!ConfigManager.getInstance().isIPBansEnabled()) {
         LOGGER.warn("IP bans are disabled in config. Cannot ban IP {}.", ipAddress);
         return false;
      } else {
         BanManager.IPBanEntry ban = new BanManager.IPBanEntry(ipAddress, reason, bannedBy);
         this.ipBans.put(ipAddress, ban);
         this.saveIPBans();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            List<ServerPlayer> playersToKick = new ArrayList<>();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               if (this.getPlayerIP(player).equals(ipAddress)) {
                  playersToKick.add(player);
               }
            }

            for (ServerPlayer playerx : playersToKick) {
               String format = ConfigManager.getIPBanMessageFormat();
               String message = format.replace("{reason}", reason != null ? reason : "N/A").replace("{bannedBy}", bannedBy != null ? bannedBy : "Console");
               playerx.connection.disconnect(Component.literal(message));
            }
         }

         if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
            LOGGER.info("IP {} banned by {} for: {}", new Object[]{ipAddress, bannedBy, reason});
         }

         return true;
      }
   }

   public boolean tempBanIP(String ipAddress, String reason, String bannedBy, long durationMillis) {
      if (this.isIPBanned(ipAddress)) {
         return false;
      } else if (!ConfigManager.getInstance().isIPBansEnabled()) {
         LOGGER.warn("IP bans are disabled. Cannot temp-ban IP {}.", ipAddress);
         return false;
      } else {
         BanManager.IPBanEntry ban = new BanManager.IPBanEntry(ipAddress, reason, bannedBy);
         ban.expireTime = System.currentTimeMillis() + durationMillis;
         this.ipBans.put(ipAddress, ban);
         this.saveIPBans();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            for (ServerPlayer player : new ArrayList(server.getPlayerList().getPlayers())) {
               if (this.getPlayerIP(player).equals(ipAddress)) {
                  String msg = "You have been temporarily IP banned for "
                     + formatDuration(durationMillis)
                     + (reason != null && !reason.isEmpty() ? ": " + reason : "");
                  player.connection.disconnect(Component.literal(msg));
               }
            }
         }

         if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
            LOGGER.info("IP {} temporarily banned by {} for {} - Reason: {}", new Object[]{ipAddress, bannedBy, formatDuration(durationMillis), reason});
         }

         return true;
      }
   }

   public boolean unbanPlayer(UUID playerId) {
      BanManager.BanEntry removed = this.playerBans.remove(playerId);
      if (removed == null) {
         return false;
      } else {
         this.saveBans();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null && ConfigManager.getInstance().isBroadcastBansEnabled()) {
            String staffPerm = ConfigManager.getInstance().getStaffNotificationPermission();
            String staffMsg = "[NeoEssentials] Player " + removed.playerName + " was unbanned.";

            for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
               if (staff.hasPermissions(2) || PermissionAPI.hasPermission(staff.getUUID(), staffPerm)) {
                  staff.sendSystemMessage(Component.literal(staffMsg));
               }
            }
         }

         if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
            LOGGER.info("Player {} ({}) unbanned", removed.playerName, playerId);
         }

         return true;
      }
   }

   public boolean unbanIP(String ipAddress) {
      BanManager.IPBanEntry removed = this.ipBans.remove(ipAddress);
      if (removed != null) {
         this.saveIPBans();
         if (ConfigManager.getInstance().isLogBanActionsEnabled()) {
            LOGGER.info("IP {} unbanned", ipAddress);
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean isPlayerBanned(UUID playerId) {
      BanManager.BanEntry ban = this.playerBans.get(playerId);
      if (ban != null) {
         if (ban.isExpired()) {
            if (ConfigManager.getInstance().isAutoExpireTempBansEnabled()) {
               this.playerBans.remove(playerId);
               this.saveBans();
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean isIPBanned(String ipAddress) {
      return this.ipBans.containsKey(ipAddress);
   }

   public boolean canPlayerJoin(ServerPlayer player) {
      return !this.isPlayerBanned(player.getUUID()) && !this.isIPBanned(this.getPlayerIP(player));
   }

   public BanManager.BanEntry getBanEntry(UUID playerId) {
      BanManager.BanEntry ban = this.playerBans.get(playerId);
      if (ban != null && ban.isExpired()) {
         if (ConfigManager.getInstance().isAutoExpireTempBansEnabled()) {
            this.playerBans.remove(playerId);
            this.saveBans();
         }

         return null;
      } else {
         return ban;
      }
   }

   public BanManager.IPBanEntry getIPBanEntry(String ipAddress) {
      return this.ipBans.get(ipAddress);
   }

   public List<BanManager.BanEntry> getAllPlayerBans() {
      List<BanManager.BanEntry> activeBans = new ArrayList<>();
      Iterator<Entry<UUID, BanManager.BanEntry>> iterator = this.playerBans.entrySet().iterator();
      boolean removedAny = false;

      while (iterator.hasNext()) {
         Entry<UUID, BanManager.BanEntry> entry = iterator.next();
         BanManager.BanEntry ban = entry.getValue();
         if (ban.isExpired()) {
            if (ConfigManager.getInstance().isAutoExpireTempBansEnabled()) {
               iterator.remove();
               removedAny = true;
            }
         } else {
            activeBans.add(ban);
         }
      }

      if (removedAny) {
         this.saveBans();
      }

      return activeBans;
   }

   public List<BanManager.IPBanEntry> getAllIPBans() {
      return new ArrayList<>(this.ipBans.values());
   }

   public static long parseDuration(String duration) {
      if (duration != null && !duration.isEmpty()) {
         duration = duration.toLowerCase().trim();
         long totalMillis = 0L;

         try {
            String[] parts = duration.split("[\\s,]+");

            for (String part : parts) {
               if (!part.isEmpty()) {
                  String numberStr = part.replaceAll("[^0-9]", "");
                  String unit = part.replaceAll("[0-9]", "");
                  if (!numberStr.isEmpty()) {
                     long value = Long.parseLong(numberStr);
                     switch (unit) {
                        case "s":
                        case "sec":
                        case "second":
                        case "seconds":
                           totalMillis += value * 1000L;
                           break;
                        case "m":
                        case "min":
                        case "minute":
                        case "minutes":
                           totalMillis += value * 60L * 1000L;
                           break;
                        case "h":
                        case "hr":
                        case "hour":
                        case "hours":
                           totalMillis += value * 60L * 60L * 1000L;
                           break;
                        case "d":
                        case "day":
                        case "days":
                           totalMillis += value * 24L * 60L * 60L * 1000L;
                           break;
                        case "w":
                        case "week":
                        case "weeks":
                           totalMillis += value * 7L * 24L * 60L * 60L * 1000L;
                           break;
                        case "mo":
                        case "month":
                        case "months":
                           totalMillis += value * 30L * 24L * 60L * 60L * 1000L;
                           break;
                        case "y":
                        case "year":
                        case "years":
                           totalMillis += value * 365L * 24L * 60L * 60L * 1000L;
                           break;
                        default:
                           totalMillis += value * 60L * 1000L;
                     }
                  }
               }
            }

            return totalMillis;
         } catch (NumberFormatException var14) {
            LOGGER.warn("Invalid duration format: {}", duration);
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public static String formatDuration(long durationMillis) {
      if (durationMillis <= 0L) {
         return "0s";
      } else {
         long seconds = durationMillis / 1000L;
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

         if (seconds > 0L || sb.isEmpty()) {
            sb.append(seconds).append("s");
         }

         return sb.toString().trim();
      }
   }

   private String getPlayerIP(ServerPlayer player) {
      try {
         Field connectionField = player.connection.getClass().getDeclaredField("connection");
         connectionField.setAccessible(true);
         Object connection = connectionField.get(player.connection);
         Method getRemoteAddressMethod = connection.getClass().getMethod("getRemoteAddress");
         String fullAddress = getRemoteAddressMethod.invoke(connection).toString();
         return fullAddress.split(":")[0].substring(1);
      } catch (Exception var6) {
         LOGGER.debug("Failed to get IP for player {}: {}", player.getName().getString(), var6.getMessage());
         return "unknown";
      }
   }

   private static String formatTime(long timestamp) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
   }

   private void loadBans() {
      this.loadPlayerBans();
      this.loadIPBans();
   }

   private void loadPlayerBans() {
      if (this.banFile.exists()) {
         try (FileReader reader = new FileReader(this.banFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("bans")) {
               for (JsonElement element : root.getAsJsonArray("bans")) {
                  JsonObject banObj = element.getAsJsonObject();
                  BanManager.BanEntry ban = new BanManager.BanEntry(
                     banObj.get("playerName").getAsString(),
                     UUID.fromString(banObj.get("playerId").getAsString()),
                     banObj.get("reason").getAsString(),
                     banObj.get("bannedBy").getAsString()
                  );
                  ban.banTime = banObj.get("banTime").getAsLong();
                  ban.expireTime = banObj.has("expireTime") ? banObj.get("expireTime").getAsLong() : 0L;
                  if (!ban.isExpired()) {
                     this.playerBans.put(ban.playerId, ban);
                  }
               }
            }
         } catch (IOException var10) {
            LOGGER.error("Failed to load player bans", var10);
         }
      }
   }

   private void loadIPBans() {
      if (this.ipBanFile.exists()) {
         try (FileReader reader = new FileReader(this.ipBanFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("bans")) {
               for (JsonElement element : root.getAsJsonArray("bans")) {
                  JsonObject banObj = element.getAsJsonObject();
                  BanManager.IPBanEntry ban = new BanManager.IPBanEntry(
                     banObj.get("ipAddress").getAsString(), banObj.get("reason").getAsString(), banObj.get("bannedBy").getAsString()
                  );
                  ban.banTime = banObj.get("banTime").getAsLong();
                  this.ipBans.put(ban.ipAddress, ban);
               }
            }
         } catch (IOException var10) {
            LOGGER.error("Failed to load IP bans", var10);
         }
      }
   }

   private void saveBans() {
      this.savePlayerBans();
   }

   private void savePlayerBans() {
      try (FileWriter writer = new FileWriter(this.banFile)) {
         JsonObject root = new JsonObject();
         JsonArray bansArray = new JsonArray();

         for (BanManager.BanEntry ban : this.playerBans.values()) {
            JsonObject banObj = new JsonObject();
            banObj.addProperty("playerName", ban.playerName);
            banObj.addProperty("playerId", ban.playerId.toString());
            banObj.addProperty("reason", ban.reason);
            banObj.addProperty("bannedBy", ban.bannedBy);
            banObj.addProperty("banTime", ban.banTime);
            banObj.addProperty("expireTime", ban.expireTime);
            bansArray.add(banObj);
         }

         root.add("bans", bansArray);
         this.gson.toJson(root, writer);
      } catch (IOException var9) {
         LOGGER.error("Failed to save player bans", var9);
      }
   }

   private void saveIPBans() {
      try (FileWriter writer = new FileWriter(this.ipBanFile)) {
         JsonObject root = new JsonObject();
         JsonArray bansArray = new JsonArray();

         for (BanManager.IPBanEntry ban : this.ipBans.values()) {
            JsonObject banObj = new JsonObject();
            banObj.addProperty("ipAddress", ban.ipAddress);
            banObj.addProperty("reason", ban.reason);
            banObj.addProperty("bannedBy", ban.bannedBy);
            banObj.addProperty("banTime", ban.banTime);
            bansArray.add(banObj);
         }

         root.add("bans", bansArray);
         this.gson.toJson(root, writer);
      } catch (IOException var9) {
         LOGGER.error("Failed to save IP bans", var9);
      }
   }

   public static class BanEntry {
      public String playerName;
      public UUID playerId;
      public String reason;
      public String bannedBy;
      public long banTime;
      public long expireTime;

      public BanEntry(String playerName, UUID playerId, String reason, String bannedBy) {
         this.playerName = playerName;
         this.playerId = playerId;
         this.reason = reason;
         this.bannedBy = bannedBy;
         this.banTime = System.currentTimeMillis();
         this.expireTime = 0L;
      }

      public boolean isExpired() {
         return this.expireTime > 0L && System.currentTimeMillis() > this.expireTime;
      }

      public String getFormattedBanTime() {
         return BanManager.formatTime(this.banTime);
      }

      public String getFormattedExpireTime() {
         return this.expireTime > 0L ? BanManager.formatTime(this.expireTime) : "Never";
      }
   }

   public static class IPBanEntry {
      public String ipAddress;
      public String reason;
      public String bannedBy;
      public long banTime;
      public long expireTime;

      public IPBanEntry(String ipAddress, String reason, String bannedBy) {
         this.ipAddress = ipAddress;
         this.reason = reason;
         this.bannedBy = bannedBy;
         this.banTime = System.currentTimeMillis();
         this.expireTime = 0L;
      }

      public boolean isExpired() {
         return this.expireTime > 0L && System.currentTimeMillis() > this.expireTime;
      }

      public String getFormattedBanTime() {
         return BanManager.formatTime(this.banTime);
      }
   }
}
