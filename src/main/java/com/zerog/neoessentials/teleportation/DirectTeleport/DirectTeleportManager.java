package com.zerog.neoessentials.teleportation.DirectTeleport;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.zerog.neoessentials.NeoEssentialsManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectTeleportManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DirectTeleportManager.class);
   private int teleportDelay = 0;
   private boolean bypassSafetyChecks = true;

   public static DirectTeleportManager getInstance() {
      return DirectTeleportManager.SingletonHolder.INSTANCE;
   }

   private DirectTeleportManager() {
      try {
         ConfigManager configManager = ConfigManager.getInstance();
         boolean bypass = true;
         if (configManager != null) {
            JsonObject config = configManager.getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("generalSettings")) {
                  JsonObject generalSettings = tp.getAsJsonObject("generalSettings");
                  if (generalSettings.has("enableTeleportSafety")) {
                     bypass = !generalSettings.get("enableTeleportSafety").getAsBoolean();
                  }
               }
            }
         }

         this.bypassSafetyChecks = bypass;
      } catch (Exception var6) {
         LOGGER.warn("Failed to load direct teleport safety config, defaulting to bypass: {}", var6.getMessage());
      }
   }

   public CompletableFuture<TeleportUtil.TeleportResult> teleportPlayerToPlayer(ServerPlayer executor, ServerPlayer player, ServerPlayer target) {
      MiscTeleportManager.getInstance().saveBackLocation(player);
      TeleportLocation targetLocation = new TeleportLocation(target);
      return TeleportUtil.teleportPlayer(player, targetLocation, this.teleportDelay * 20, !this.bypassSafetyChecks)
         .thenApply(
            result -> {
               if (result.isSuccess()) {
                  if (!executor.getUUID().equals(player.getUUID())) {
                     executor.sendSystemMessage(
                        MessageUtil.success(
                           "commands.neoessentials.teleport.admin.teleported_player", player.getName().getString(), target.getName().getString()
                        )
                     );
                  }

                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.admin.teleported_to", target.getName().getString()));
                  if (!target.getUUID().equals(executor.getUUID())) {
                     target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.admin.player_teleported_to_you", player.getName().getString()));
                  }

                  LOGGER.info(
                     "Admin {} teleported {} to {}", new Object[]{executor.getName().getString(), player.getName().getString(), target.getName().getString()}
                  );
               } else {
                  executor.sendSystemMessage(
                     MessageUtil.error("commands.neoessentials.teleport.admin.failed", player.getName().getString(), result.getMessage())
                  );
                  LOGGER.warn(
                     "Failed admin teleport by {}: {} to {} - {}",
                     new Object[]{executor.getName().getString(), player.getName().getString(), target.getName().getString(), result.getMessage()}
                  );
               }

               return (TeleportUtil.TeleportResult)result;
            }
         );
   }

   public CompletableFuture<TeleportUtil.TeleportResult> teleportPlayerToCoordinates(ServerPlayer executor, ServerPlayer player, double x, double y, double z) {
      MiscTeleportManager.getInstance().saveBackLocation(player);
      TeleportLocation targetLocation = new TeleportLocation(
         player.serverLevel().dimension().location().toString(), x, y, z, 0.0F, 0.0F, executor.getName().getString()
      );
      return TeleportUtil.teleportPlayer(player, targetLocation, this.teleportDelay * 20, !this.bypassSafetyChecks)
         .thenApply(
            result -> {
               if (result.isSuccess()) {
                  if (!executor.getUUID().equals(player.getUUID())) {
                     executor.sendSystemMessage(
                        MessageUtil.success("commands.neoessentials.teleport.admin.teleported_player_coords", player.getName().getString(), x, y, z)
                     );
                  }

                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.admin.teleported_to_coords", x, y, z));
                  LOGGER.info(
                     "Admin {} teleported {} to coordinates {}, {}, {}", new Object[]{executor.getName().getString(), player.getName().getString(), x, y, z}
                  );
               } else {
                  executor.sendSystemMessage(
                     MessageUtil.error("commands.neoessentials.teleport.admin.failed_coords", player.getName().getString(), result.getMessage())
                  );
                  LOGGER.warn(
                     "Failed admin coordinate teleport by {}: {} to {}, {}, {} - {}",
                     new Object[]{executor.getName().getString(), player.getName().getString(), x, y, z, result.getMessage()}
                  );
               }

               return (TeleportUtil.TeleportResult)result;
            }
         );
   }

   public CompletableFuture<TeleportUtil.TeleportResult> teleportPlayerHere(ServerPlayer executor, ServerPlayer player) {
      return this.teleportPlayerToPlayer(executor, player, executor);
   }

   public void teleportAllPlayers(ServerPlayer executor, TeleportLocation targetLocation) {
      Collection<ServerPlayer> players = executor.getServer().getPlayerList().getPlayers();
      int totalPlayers = players.size();
      int excludingSelf = executor != null ? totalPlayers - 1 : totalPlayers;
      if (excludingSelf == 0) {
         executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.tpall.no_players"));
      } else {
         executor.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.admin.tpall.starting", excludingSelf));
         int[] successCount = new int[]{0};
         int[] failureCount = new int[]{0};
         CompletableFuture<Void>[] futures = players.stream().filter(player -> !player.getUUID().equals(executor.getUUID())).map(player -> {
            MiscTeleportManager.getInstance().saveBackLocation(player);
            return TeleportUtil.teleportPlayer(player, targetLocation, this.teleportDelay * 20, !this.bypassSafetyChecks).thenAccept(result -> {
               if (result.isSuccess()) {
                  successCount[0]++;
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.admin.tpall.teleported"));
               } else {
                  failureCount[0]++;
                  LOGGER.warn("Failed to teleport {} during tpall: {}", player.getName().getString(), result.getMessage());
               }
            });
         }).toArray(CompletableFuture[]::new);
         CompletableFuture.allOf(futures).thenRun(() -> {
            executor.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.admin.tpall.completed", successCount[0], failureCount[0]));
            LOGGER.info("Admin {} completed tpall: {} successful, {} failed", new Object[]{executor.getName().getString(), successCount[0], failureCount[0]});
         });
      }
   }

   public void teleportAllPlayersHere(ServerPlayer executor) {
      TeleportLocation executorLocation = new TeleportLocation(executor);
      this.teleportAllPlayers(executor, executorLocation);
   }

   public void teleportAllPlayersToCoordinates(ServerPlayer executor, double x, double y, double z) {
      TeleportLocation targetLocation = new TeleportLocation(
         executor.serverLevel().dimension().location().toString(), x, y, z, 0.0F, 0.0F, executor.getName().getString()
      );
      this.teleportAllPlayers(executor, targetLocation);
   }

   public void teleportAllPlayersToTarget(ServerPlayer executor, ServerPlayer target) {
      TeleportLocation targetLocation = new TeleportLocation(target);
      this.teleportAllPlayers(executor, targetLocation);
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public boolean isBypassSafetyChecks() {
      return this.bypassSafetyChecks;
   }

   public void setBypassSafetyChecks(boolean bypass) {
      this.bypassSafetyChecks = bypass;
   }

   public boolean teleportToOfflinePlayer(ServerPlayer executor, String playerName) {
      try {
         MinecraftServer server = executor.getServer();
         GameProfile profile = (GameProfile)server.getProfileCache().get(playerName).orElse(null);
         if (profile == null) {
            executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.offline_player_not_found", playerName));
            return false;
         } else {
            NeoEssentialsManager manager = NeoEssentialsManager.getInstance();
            NeoEssentialsManager.PlayerData playerData = manager.getPlayerData(profile.getId());
            String lastLocationString = playerData.getLastLocation();
            if (lastLocationString != null && !lastLocationString.isEmpty()) {
               TeleportLocation offlineLocation = TeleportLocation.fromLocationString(lastLocationString);
               if (offlineLocation == null) {
                  executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.offline_failed", lastLocationString));
                  return false;
               } else {
                  MiscTeleportManager.getInstance().saveBackLocation(executor);
                  TeleportUtil.teleportPlayer(executor, offlineLocation, this.teleportDelay * 20, !this.bypassSafetyChecks)
                     .thenAccept(
                        result -> {
                           if (result.isSuccess()) {
                              executor.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.admin.offline_teleported", playerName));
                              LOGGER.info("Admin {} teleported to offline player {}'s location", executor.getName().getString(), playerName);
                           } else {
                              executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.offline_failed", result.getMessage()));
                              LOGGER.warn(
                                 "Failed to teleport {} to offline player {}: {}",
                                 new Object[]{executor.getName().getString(), playerName, result.getMessage()}
                              );
                           }
                        }
                     );
                  return true;
               }
            } else {
               executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.offline_player_not_found", playerName));
               return false;
            }
         }
      } catch (Exception var9) {
         executor.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.admin.offline_failed", var9.getMessage()));
         LOGGER.error("Error during offline teleport to {}: {}", new Object[]{playerName, var9.getMessage(), var9});
         return false;
      }
   }

   public String getStatistics() {
      return String.format("DirectTeleport Statistics: delay=%ds, bypassSafety=%s", this.teleportDelay, this.bypassSafetyChecks);
   }

   private static class SingletonHolder {
      private static final DirectTeleportManager INSTANCE = new DirectTeleportManager();
   }
}
