package com.zerog.neoessentials.util.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class SeenCommand {
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final Map<UUID, SeenCommand.PlayerActivity> PLAYER_ACTIVITY = new ConcurrentHashMap<>();
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Path SEEN_DATA_FILE = Paths.get("config", "neoessentials", "seen_data.json");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("seen")) {
         loadSeenData();
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("seen")
                  .then(
                     Commands.argument("player", StringArgumentType.word())
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.seen"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 String playerName = StringArgumentType.getString(ctx, "player");
                                 return showPlayerActivity((CommandSourceStack)ctx.getSource(), playerName);
                              }
                           }
                        )
                  ))
               .executes(ctx -> {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.info("commands.neoessentials.seen.usage"));
                  return 0;
               })
         );
      }
   }

   private static int showPlayerActivity(CommandSourceStack source, String playerName) {
      SeenCommand.PlayerActivity foundActivity = null;
      UUID playerId = null;
      ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
      if (onlinePlayer != null) {
         playerId = onlinePlayer.getUUID();
         foundActivity = PLAYER_ACTIVITY.get(playerId);
         if (foundActivity == null) {
            foundActivity = new SeenCommand.PlayerActivity(onlinePlayer.getName().getString());
            foundActivity.isOnline = true;
            foundActivity.lastLoginTime = LocalDateTime.now().format(TIME_FORMAT);
            PLAYER_ACTIVITY.put(playerId, foundActivity);
            saveSeenData();
         }
      } else {
         for (Entry<UUID, SeenCommand.PlayerActivity> entry : PLAYER_ACTIVITY.entrySet()) {
            if (entry.getValue().playerName.equalsIgnoreCase(playerName)) {
               playerId = entry.getKey();
               foundActivity = entry.getValue();
               break;
            }
         }
      }

      if (foundActivity == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.seen.player_not_found", playerName));
         return 0;
      } else {
         String displayName = foundActivity.playerName;
         if (foundActivity.isOnline) {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.seen.online", displayName), false);
            if (foundActivity.lastLoginTime != null) {
               String loginTime = foundActivity.lastLoginTime;
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.login_time", loginTime), false);
            }

            if (onlinePlayer != null) {
               String world = onlinePlayer.level().toString();
               String coords = String.format("%.1f, %.1f, %.1f", onlinePlayer.getX(), onlinePlayer.getY(), onlinePlayer.getZ());
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.current_location", world, coords), false);
            }
         } else {
            String timeSince = getTimeSince(foundActivity.lastSeen);
            String lastSeen = foundActivity.lastSeen;
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.offline", displayName, timeSince), false);
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.last_seen", lastSeen), false);
         }

         if (foundActivity.firstSeen != null) {
            String firstSeen = foundActivity.firstSeen;
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.first_seen", firstSeen), false);
         }

         if (foundActivity.totalPlayTime > 0L) {
            String playTimeStr = formatPlayTime(foundActivity.totalPlayTime);
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.play_time", playTimeStr), false);
         }

         return 1;
      }
   }

   private static String getTimeSince(String timestamp) {
      try {
         LocalDateTime lastTime = LocalDateTime.parse(timestamp, TIME_FORMAT);
         LocalDateTime now = LocalDateTime.now();
         long days = ChronoUnit.DAYS.between(lastTime, now);
         long hours = ChronoUnit.HOURS.between(lastTime, now) % 24L;
         long minutes = ChronoUnit.MINUTES.between(lastTime, now) % 60L;
         if (days > 0L) {
            return String.format("%d days, %d hours ago", days, hours);
         } else {
            return hours > 0L ? String.format("%d hours, %d minutes ago", hours, minutes) : String.format("%d minutes ago", minutes);
         }
      } catch (Exception var9) {
         return "unknown";
      }
   }

   private static String formatPlayTime(long minutes) {
      long hours = minutes / 60L;
      long remainingMinutes = minutes % 60L;
      long days = hours / 24L;
      long remainingHours = hours % 24L;
      if (days > 0L) {
         return String.format("%d days, %d hours, %d minutes", days, remainingHours, remainingMinutes);
      } else {
         return hours > 0L ? String.format("%d hours, %d minutes", hours, remainingMinutes) : String.format("%d minutes", minutes);
      }
   }

   public static void onPlayerJoin(ServerPlayer player) {
      UUID playerId = player.getUUID();
      SeenCommand.PlayerActivity activity = PLAYER_ACTIVITY.computeIfAbsent(playerId, k -> new SeenCommand.PlayerActivity(player.getName().getString()));
      activity.isOnline = true;
      activity.lastLoginTime = LocalDateTime.now().format(TIME_FORMAT);
      activity.playerName = player.getName().getString();
      saveSeenData();
   }

   public static void onPlayerLeave(ServerPlayer player) {
      UUID playerId = player.getUUID();
      SeenCommand.PlayerActivity activity = PLAYER_ACTIVITY.get(playerId);
      if (activity != null) {
         activity.isOnline = false;
         String logoutTime = LocalDateTime.now().format(TIME_FORMAT);
         activity.lastSeen = logoutTime;
         activity.lastLogoutTime = logoutTime;
         if (activity.lastLoginTime != null) {
            try {
               LocalDateTime loginTime = LocalDateTime.parse(activity.lastLoginTime, TIME_FORMAT);
               LocalDateTime logoutDateTime = LocalDateTime.parse(logoutTime, TIME_FORMAT);
               long sessionMinutes = ChronoUnit.MINUTES.between(loginTime, logoutDateTime);
               activity.totalPlayTime += sessionMinutes;
            } catch (Exception var8) {
            }
         }

         saveSeenData();
      }
   }

   private static void loadSeenData() {
      try {
         if (!Files.exists(SEEN_DATA_FILE)) {
            return;
         }

         String content = Files.readString(SEEN_DATA_FILE);
         JsonObject data = (JsonObject)GSON.fromJson(content, JsonObject.class);

         for (String uuidStr : data.keySet()) {
            try {
               UUID uuid = UUID.fromString(uuidStr);
               JsonObject activityObj = data.getAsJsonObject(uuidStr);
               SeenCommand.PlayerActivity activity = new SeenCommand.PlayerActivity(activityObj.get("playerName").getAsString());
               if (activityObj.has("lastSeen")) {
                  activity.lastSeen = activityObj.get("lastSeen").getAsString();
               }

               if (activityObj.has("firstSeen")) {
                  activity.firstSeen = activityObj.get("firstSeen").getAsString();
               }

               if (activityObj.has("isOnline")) {
                  activity.isOnline = activityObj.get("isOnline").getAsBoolean();
               }

               if (activityObj.has("lastLoginTime")) {
                  activity.lastLoginTime = activityObj.get("lastLoginTime").getAsString();
               }

               if (activityObj.has("lastLogoutTime")) {
                  activity.lastLogoutTime = activityObj.get("lastLogoutTime").getAsString();
               }

               if (activityObj.has("totalPlayTime")) {
                  activity.totalPlayTime = activityObj.get("totalPlayTime").getAsLong();
               }

               PLAYER_ACTIVITY.put(uuid, activity);
            } catch (Exception var7) {
               System.err.println("Failed to load player activity for UUID: " + uuidStr);
            }
         }
      } catch (Exception var8) {
         System.err.println("Failed to load seen data: " + var8.getMessage());
      }
   }

   private static void saveSeenData() {
      try {
         JsonObject data = new JsonObject();

         for (Entry<UUID, SeenCommand.PlayerActivity> entry : PLAYER_ACTIVITY.entrySet()) {
            JsonObject activityObj = new JsonObject();
            SeenCommand.PlayerActivity activity = entry.getValue();
            activityObj.addProperty("playerName", activity.playerName);
            activityObj.addProperty("lastSeen", activity.lastSeen);
            activityObj.addProperty("firstSeen", activity.firstSeen);
            activityObj.addProperty("isOnline", activity.isOnline);
            activityObj.addProperty("lastLoginTime", activity.lastLoginTime);
            activityObj.addProperty("lastLogoutTime", activity.lastLogoutTime);
            activityObj.addProperty("totalPlayTime", activity.totalPlayTime);
            data.add(entry.getKey().toString(), activityObj);
         }

         Files.createDirectories(SEEN_DATA_FILE.getParent());
         Files.writeString(SEEN_DATA_FILE, GSON.toJson(data));
      } catch (Exception var5) {
         System.err.println("Failed to save seen data: " + var5.getMessage());
      }
   }

   private static class PlayerActivity {
      String playerName;
      String lastSeen;
      String firstSeen;
      boolean isOnline;
      String lastLoginTime;
      String lastLogoutTime;
      long totalPlayTime;

      PlayerActivity(String playerName) {
         this.playerName = playerName;
         this.isOnline = false;
         this.totalPlayTime = 0L;
         String now = LocalDateTime.now().format(SeenCommand.TIME_FORMAT);
         this.firstSeen = now;
         this.lastSeen = now;
      }
   }
}
