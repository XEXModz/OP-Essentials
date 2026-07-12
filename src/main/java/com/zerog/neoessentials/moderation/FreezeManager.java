package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.InputValidator;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreezeManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(FreezeManager.class);
   private static FreezeManager instance;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final File freezeFile;
   private final Map<UUID, FreezeManager.FreezeEntry> frozenPlayers = new ConcurrentHashMap<>();

   private FreezeManager() {
      File moderationDir = new File("neoessentials/moderation");
      if (!moderationDir.exists() && !moderationDir.mkdirs()) {
         LOGGER.error("Failed to create moderation directory: {}", moderationDir.getAbsolutePath());
      }

      this.freezeFile = new File(moderationDir, "frozen_players.json");
      this.loadData();
   }

   public static FreezeManager getInstance() {
      if (instance == null) {
         instance = new FreezeManager();
      }

      return instance;
   }

   public boolean freezePlayer(String playerName, UUID playerId, String reason, String frozenBy) {
      if (this.isPlayerFrozen(playerId)) {
         return false;
      } else {
         InputValidator.ValidationResult reasonResult = InputValidator.validateReason(reason);
         if (!reasonResult.isValid()) {
            LOGGER.warn("Freeze failed for {}: invalid reason: {}", playerName, reasonResult.getErrorMessage());
            return false;
         } else {
            reason = (String)reasonResult.getValue();
            FreezeManager.FreezeEntry freeze = new FreezeManager.FreezeEntry(playerName, playerId, reason, frozenBy);
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  freeze.frozenPosition = player.blockPosition();
                  String template = ConfigManager.getFreezeMessage();
                  String message;
                  if (template.equals("neoessentials.moderation.frozen_message")) {
                     message = MessageUtil.localize(template, reason, frozenBy);
                  } else {
                     message = template.replace("{reason}", reason != null ? reason : "").replace("{freezer}", frozenBy != null ? frozenBy : "");
                  }

                  player.sendSystemMessage(MessageUtil.warning(message));
               }
            }

            this.frozenPlayers.put(playerId, freeze);
            this.saveData();
            LOGGER.info("Player {} ({}) frozen by {} for: {}", new Object[]{playerName, playerId, frozenBy, reason});
            return true;
         }
      }
   }

   public boolean unfreezePlayer(UUID playerId) {
      FreezeManager.FreezeEntry removed = this.frozenPlayers.remove(playerId);
      if (removed != null) {
         this.saveData();
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
               String template = ConfigManager.getUnfreezeMessage();
               String message;
               if (template.equals("neoessentials.moderation.unfrozen_message")) {
                  message = MessageUtil.localize(template);
               } else {
                  message = template.replace("{unfreezer}", "Staff");
               }

               player.sendSystemMessage(MessageUtil.success(message));
            }
         }

         LOGGER.info("Player {} ({}) unfrozen", removed.playerName, playerId);
         return true;
      } else {
         return false;
      }
   }

   public int freezeAllPlayers(String reason, String frozenBy) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return 0;
      } else {
         int count = 0;

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (this.freezePlayer(player.getName().getString(), player.getUUID(), reason, frozenBy)) {
               count++;
            }
         }

         LOGGER.info("Froze {} players by {}", count, frozenBy);
         return count;
      }
   }

   public int unfreezeAllPlayers() {
      int count = this.frozenPlayers.size();
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         for (UUID playerId : this.frozenPlayers.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
               String message = MessageUtil.localize("neoessentials.moderation.unfrozen_message");
               player.sendSystemMessage(MessageUtil.success(message));
            }
         }
      }

      this.frozenPlayers.clear();
      this.saveData();
      LOGGER.info("Unfroze {} players", count);
      return count;
   }

   public boolean isPlayerFrozen(UUID playerId) {
      return this.frozenPlayers.containsKey(playerId);
   }

   public FreezeManager.FreezeEntry getFreezeEntry(UUID playerId) {
      return this.frozenPlayers.get(playerId);
   }

   public List<FreezeManager.FreezeEntry> getAllFrozenPlayers() {
      return new ArrayList<>(this.frozenPlayers.values());
   }

   public boolean canPlayerMove(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerInteract(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerAttack(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerBreakBlocks(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerPlaceBlocks(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerPickupItems(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public boolean canPlayerDropItems(ServerPlayer player) {
      return !this.isPlayerFrozen(player.getUUID());
   }

   public void enforceFreezePosition(ServerPlayer player) {
      UUID playerId = player.getUUID();
      FreezeManager.FreezeEntry freeze = this.getFreezeEntry(playerId);
      if (freeze != null && freeze.frozenPosition != null) {
         BlockPos currentPos = player.blockPosition();
         if (currentPos.distSqr(freeze.frozenPosition) > 2.0) {
            player.teleportTo((double)freeze.frozenPosition.getX() + 0.5, (double)freeze.frozenPosition.getY(), (double)freeze.frozenPosition.getZ() + 0.5);
            String message = MessageUtil.localize("neoessentials.moderation.freeze_movement_blocked");
            player.sendSystemMessage(MessageUtil.warning(message));
         }
      }
   }

   public void onPlayerJoin(ServerPlayer player) {
      UUID playerId = player.getUUID();
      FreezeManager.FreezeEntry freeze = this.getFreezeEntry(playerId);
      if (freeze != null) {
         if (freeze.frozenPosition == null) {
            freeze.frozenPosition = player.blockPosition();
            this.saveData();
         }

         String template = ConfigManager.getFreezeReminder();
         String message;
         if (template.equals("neoessentials.moderation.freeze_reminder")) {
            message = MessageUtil.localize(template, freeze.reason);
         } else {
            message = template.replace("{reason}", freeze.reason != null ? freeze.reason : "");
         }

         player.sendSystemMessage(MessageUtil.warning(message));
      }
   }

   private static String formatTime(long timestamp) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
   }

   private void loadData() {
      if (this.freezeFile.exists()) {
         try (FileReader reader = new FileReader(this.freezeFile)) {
            JsonObject root = (JsonObject)this.gson.fromJson(reader, JsonObject.class);
            if (root != null && root.has("frozen")) {
               for (JsonElement element : root.getAsJsonArray("frozen")) {
                  JsonObject freezeObj = element.getAsJsonObject();
                  FreezeManager.FreezeEntry freeze = new FreezeManager.FreezeEntry(
                     freezeObj.get("playerName").getAsString(),
                     UUID.fromString(freezeObj.get("playerId").getAsString()),
                     freezeObj.get("reason").getAsString(),
                     freezeObj.get("frozenBy").getAsString()
                  );
                  freeze.freezeTime = freezeObj.get("freezeTime").getAsLong();
                  if (freezeObj.has("frozenPosition")) {
                     JsonObject posObj = freezeObj.getAsJsonObject("frozenPosition");
                     freeze.frozenPosition = new BlockPos(posObj.get("x").getAsInt(), posObj.get("y").getAsInt(), posObj.get("z").getAsInt());
                  }

                  this.frozenPlayers.put(freeze.playerId, freeze);
               }
            }
         } catch (IOException var11) {
            LOGGER.error("Failed to load freeze data", var11);
         }
      }
   }

   private void saveData() {
      try (FileWriter writer = new FileWriter(this.freezeFile)) {
         JsonObject root = new JsonObject();
         JsonArray frozenArray = new JsonArray();

         for (FreezeManager.FreezeEntry freeze : this.frozenPlayers.values()) {
            JsonObject freezeObj = new JsonObject();
            freezeObj.addProperty("playerName", freeze.playerName);
            freezeObj.addProperty("playerId", freeze.playerId.toString());
            freezeObj.addProperty("reason", freeze.reason);
            freezeObj.addProperty("frozenBy", freeze.frozenBy);
            freezeObj.addProperty("freezeTime", freeze.freezeTime);
            if (freeze.frozenPosition != null) {
               JsonObject posObj = new JsonObject();
               posObj.addProperty("x", freeze.frozenPosition.getX());
               posObj.addProperty("y", freeze.frozenPosition.getY());
               posObj.addProperty("z", freeze.frozenPosition.getZ());
               freezeObj.add("frozenPosition", posObj);
            }

            frozenArray.add(freezeObj);
         }

         root.add("frozen", frozenArray);
         this.gson.toJson(root, writer);
      } catch (IOException var10) {
         LOGGER.error("Failed to save freeze data", var10);
      }
   }

   public static class FreezeEntry {
      public String playerName;
      public UUID playerId;
      public String reason;
      public String frozenBy;
      public long freezeTime;
      public BlockPos frozenPosition;

      public FreezeEntry(String playerName, UUID playerId, String reason, String frozenBy) {
         this.playerName = playerName;
         this.playerId = playerId;
         this.reason = reason;
         this.frozenBy = frozenBy;
         this.freezeTime = System.currentTimeMillis();
      }

      public String getFormattedFreezeTime() {
         return FreezeManager.formatTime(this.freezeTime);
      }
   }
}
