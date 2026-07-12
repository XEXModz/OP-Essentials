package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionSystem;
import com.zerog.neoessentials.permissions.PermissionUser;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanishManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(VanishManager.class);
   private static VanishManager instance;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final File vanishFile;
   private final Map<UUID, Integer> vanishedPlayers = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> viewerPriorities = new ConcurrentHashMap<>();

   public int getPlayerPriority(UUID playerId) {
      String group = null;

      try {
         PermissionManager manager = PermissionSystem.getManager();
         if (manager != null) {
            PermissionUser user = manager.getUser(playerId);
            if (user != null) {
               group = user.getGroup();
            }
         }
      } catch (Exception var5) {
      }

      if (group == null) {
         return 10;
      } else {
         String var6 = group.toLowerCase();
         switch (var6) {
            case "owner":
               return 0;
            case "admin":
               return 1;
            case "mod":
            case "moderator":
               return 2;
            case "helper":
               return 3;
            case "vip":
               return 5;
            case "default":
            case "member":
            default:
               return 10;
         }
      }
   }

   private VanishManager() {
      File moderationDir = new File("neoessentials/moderation");
      if (!moderationDir.exists() && !moderationDir.mkdirs()) {
         LOGGER.error("Failed to create moderation directory: {}", moderationDir.getAbsolutePath());
      }

      this.vanishFile = new File(moderationDir, "vanished_players.json");
      this.loadData();
   }


   // FEATURE 1.1.7 (Spock): vanish/unvanish broadcasts the EXACT vanilla
   // leave/join chat line, so /vanish is indistinguishable from logging off.
   // Players with see-vanished rights are excluded - they see reality.
   private void broadcastFakePresence(MinecraftServer server, ServerPlayer target, boolean join) {
      try {
         net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
               join ? "multiplayer.player.joined" : "multiplayer.player.left", target.getDisplayName()
            )
            .withStyle(net.minecraft.ChatFormatting.YELLOW);

         for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!viewer.getUUID().equals(target.getUUID()) && !this.canPlayerSeeVanished(viewer.getUUID())) {
               viewer.sendSystemMessage(msg);
            }
         }
      } catch (Exception var6) {
         LOGGER.error("Fake presence broadcast failed: {}", var6.getMessage());
      }
   }

   public static VanishManager getInstance() {
      if (instance == null) {
         instance = new VanishManager();
      }

      return instance;
   }

   public boolean vanishPlayer(UUID playerId, String playerName, String vanishedBy, boolean selfVanish) {
      if (this.isPlayerVanished(playerId)) {
         return false;
      } else {
         int vanishPriority = this.getPlayerPriority(playerId);
         this.vanishedPlayers.put(playerId, vanishPriority);
         this.saveData();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer vanishedPlayer = server.getPlayerList().getPlayer(playerId);
            if (vanishedPlayer != null) {
               this.hidePlayerFromOthers(vanishedPlayer);
               this.broadcastFakePresence(server, vanishedPlayer, false);
            }
         }

         if (ConfigManager.isLogVanishActionsEnabled()) {
            LOGGER.info("Player {} ({}) vanished by {}", new Object[]{playerName, playerId, vanishedBy});
         }

         return true;
      }
   }

   public boolean unvanishPlayer(UUID playerId) {
      if (this.vanishedPlayers.remove(playerId) == null) {
         return false;
      } else {
         this.saveData();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer unvanishedPlayer = server.getPlayerList().getPlayer(playerId);
            if (unvanishedPlayer != null) {
               this.showPlayerToOthers(unvanishedPlayer);
               this.broadcastFakePresence(server, unvanishedPlayer, true);
            }
         }

         if (ConfigManager.isLogVanishActionsEnabled()) {
            LOGGER.info("Player ({}) unvanished", playerId);
         }

         return true;
      }
   }

   public boolean toggleVanish(UUID playerId, String playerName, String toggledBy) {
      return this.isPlayerVanished(playerId) ? this.unvanishPlayer(playerId) : this.vanishPlayer(playerId, playerName, toggledBy, toggledBy.equals(playerName));
   }

   public void enableSeeVanished(UUID playerId) {
      int viewerPriority = this.getPlayerPriority(playerId);
      this.viewerPriorities.put(playerId, viewerPriority);
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         ServerPlayer observer = server.getPlayerList().getPlayer(playerId);
         if (observer != null) {
            for (UUID vanishedId : this.vanishedPlayers.keySet()) {
               ServerPlayer vanishedPlayer = server.getPlayerList().getPlayer(vanishedId);
               if (vanishedPlayer != null && !vanishedId.equals(playerId)) {
                  this.showPlayerToSpecific(vanishedPlayer, observer);
               }
            }
         }
      }

      if (ConfigManager.isLogVanishActionsEnabled()) {
         LOGGER.info("Player ({}) enabled see vanished", playerId);
      }
   }

   public void disableSeeVanished(UUID playerId) {
      this.viewerPriorities.remove(playerId);
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         ServerPlayer observer = server.getPlayerList().getPlayer(playerId);
         if (observer != null) {
            for (UUID vanishedId : this.vanishedPlayers.keySet()) {
               ServerPlayer vanishedPlayer = server.getPlayerList().getPlayer(vanishedId);
               if (vanishedPlayer != null && !vanishedId.equals(playerId)) {
                  this.hidePlayerFromSpecific(vanishedPlayer, observer);
               }
            }
         }
      }

      if (ConfigManager.isLogVanishActionsEnabled()) {
         LOGGER.info("Player ({}) disabled see vanished", playerId);
      }
   }

   public boolean toggleSeeVanished(UUID playerId) {
      if (this.viewerPriorities.containsKey(playerId)) {
         this.disableSeeVanished(playerId);
         return false;
      } else {
         this.enableSeeVanished(playerId);
         return true;
      }
   }

   public boolean isPlayerVanished(UUID playerId) {
      return this.vanishedPlayers.containsKey(playerId);
   }

   public boolean canPlayerSeeVanished(UUID playerId) {
      return this.viewerPriorities.containsKey(playerId);
   }

   public Set<UUID> getVanishedPlayers() {
      return new HashSet<>(this.vanishedPlayers.keySet());
   }

   public Set<UUID> getCanSeeVanished() {
      return new HashSet<>(this.viewerPriorities.keySet());
   }

   public void onPlayerJoin(ServerPlayer player) {
      UUID playerId = player.getUUID();
      if (this.isPlayerVanished(playerId)) {
         this.hidePlayerFromOthers(player);
         String message = MessageUtil.localize("neoessentials.moderation.vanish_reminder");
         player.sendSystemMessage(MessageUtil.info(message));
      }

      if (this.canPlayerSeeVanished(playerId)) {
         int viewerPriority = this.viewerPriorities.getOrDefault(playerId, 10);

         for (UUID vanishedId : this.vanishedPlayers.keySet()) {
            if (!vanishedId.equals(playerId)) {
               int vanishedPriority = this.vanishedPlayers.getOrDefault(vanishedId, 10);
               if (viewerPriority <= vanishedPriority) {
                  ServerPlayer vanishedPlayer = player.getServer().getPlayerList().getPlayer(vanishedId);
                  if (vanishedPlayer != null) {
                     this.showPlayerToSpecific(vanishedPlayer, player);
                  }
               }
            }
         }
      }

      // BUGFIX 1.1.6: re-hide already-vanished players FROM this new joiner.
      // The vanilla player-list sync sends the joining client ALL online players
      // (including vanished staff), and nothing previously re-hid them -> they
      // reappeared in tab (greyed but visible). We re-send the hide packets a few
      // ticks later so they land AFTER the vanilla ADD sync, not before it.
      MinecraftServer server = player.getServer();
      if (server != null && ConfigManager.isHideFromTabListEnabled()) {
         final int joinerPriority = this.viewerPriorities.getOrDefault(playerId, 10);
         server.tell(new TickTask(server.getTickCount() + 2, () -> {
            for (UUID vanishedId : this.vanishedPlayers.keySet()) {
               if (!vanishedId.equals(playerId)) {
                  int vanishedPriority = this.vanishedPlayers.getOrDefault(vanishedId, 10);
                  if (joinerPriority > vanishedPriority) {
                     ServerPlayer vanishedPlayer = server.getPlayerList().getPlayer(vanishedId);
                     if (vanishedPlayer != null) {
                        this.hidePlayerFromSpecific(vanishedPlayer, player);
                     }
                  }
               }
            }
         }));
      }
   }

   public void onPlayerLeave(ServerPlayer player) {
   }

   private void hidePlayerFromOthers(ServerPlayer vanishedPlayer) {
      boolean hideFromTabList = ConfigManager.isHideFromTabListEnabled();
      if (hideFromTabList) {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            UUID vanishedId = vanishedPlayer.getUUID();
            int vanishedPriority = this.vanishedPlayers.getOrDefault(vanishedId, 10);

            for (ServerPlayer otherPlayer : server.getPlayerList().getPlayers()) {
               if (otherPlayer != vanishedPlayer) {
                  int viewerPriority = this.viewerPriorities.getOrDefault(otherPlayer.getUUID(), 10);
                  if (viewerPriority > vanishedPriority) {
                     this.hidePlayerFromSpecific(vanishedPlayer, otherPlayer);
                  }
               }
            }
         }
      }
   }

   private void showPlayerToOthers(ServerPlayer unvanishedPlayer) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         for (ServerPlayer otherPlayer : server.getPlayerList().getPlayers()) {
            if (otherPlayer != unvanishedPlayer) {
               this.showPlayerToSpecific(unvanishedPlayer, otherPlayer);
            }
         }
      }
   }

   private void hidePlayerFromSpecific(ServerPlayer vanishedPlayer, ServerPlayer observer) {
      try {
         observer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(vanishedPlayer.getUUID())));
      } catch (Exception var4) {
         LOGGER.error("Failed to hide player {} from {}", new Object[]{vanishedPlayer.getName().getString(), observer.getName().getString(), var4});
      }
   }

   private void showPlayerToSpecific(ServerPlayer unvanishedPlayer, ServerPlayer observer) {
      try {
         observer.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED), List.of(unvanishedPlayer)));
         observer.connection.send(new ClientboundAddEntityPacket(unvanishedPlayer, 0, unvanishedPlayer.blockPosition()));
      } catch (Exception var4) {
         LOGGER.error("Failed to show player {} to {}", new Object[]{unvanishedPlayer.getName().getString(), observer.getName().getString(), var4});
      }
   }

   private static String formatTime(long timestamp) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
   }

   private void loadData() {
      if (this.vanishFile.exists()) {
         try (FileReader reader = new FileReader(this.vanishFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null) {
               if (root.has("vanished")) {
                  for (JsonElement element : root.getAsJsonArray("vanished")) {
                     JsonObject obj = element.getAsJsonObject();
                     UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                     int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 10;
                     this.vanishedPlayers.put(uuid, priority);
                  }
               }

               if (root.has("viewerPriorities")) {
                  for (JsonElement element : root.getAsJsonArray("viewerPriorities")) {
                     JsonObject obj = element.getAsJsonObject();
                     UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                     int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 10;
                     this.viewerPriorities.put(uuid, priority);
                  }
               }
            }
         } catch (IOException var11) {
            LOGGER.error("Failed to load vanish data", var11);
         }
      }
   }

   private void saveData() {
      try (FileWriter writer = new FileWriter(this.vanishFile)) {
         JsonObject root = new JsonObject();
         JsonArray vanishedArray = new JsonArray();

         for (Entry<UUID, Integer> entry : this.vanishedPlayers.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", entry.getKey().toString());
            obj.addProperty("priority", entry.getValue());
            vanishedArray.add(obj);
         }

         root.add("vanished", vanishedArray);
         JsonArray viewerArray = new JsonArray();

         for (Entry<UUID, Integer> entry : this.viewerPriorities.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", entry.getKey().toString());
            obj.addProperty("priority", entry.getValue());
            viewerArray.add(obj);
         }

         root.add("viewerPriorities", viewerArray);
         this.gson.toJson(root, writer);
      } catch (IOException var10) {
         LOGGER.error("Failed to save vanish data", var10);
      }
   }

   public static class VanishEntry {
      public String playerName;
      public UUID playerId;
      public String vanishedBy;
      public long vanishTime;
      public boolean selfVanish;

      public VanishEntry(String playerName, UUID playerId, String vanishedBy, boolean selfVanish) {
         this.playerName = playerName;
         this.playerId = playerId;
         this.vanishedBy = vanishedBy;
         this.selfVanish = selfVanish;
         this.vanishTime = System.currentTimeMillis();
      }

      public String getFormattedVanishTime() {
         return VanishManager.formatTime(this.vanishTime);
      }
   }
}
