package com.zerog.neoessentials.teleportation.Misc;

import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials",
   bus = Bus.GAME
)
public class MiscTeleportManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(MiscTeleportManager.class);
   private final Map<UUID, TeleportLocation> backLocations = new ConcurrentHashMap<>();
   private final Map<UUID, TeleportLocation> deathLocations = new ConcurrentHashMap<>();
   private int maxBackHistory = 5;
   private int teleportDelay = 3;
   private boolean enableDeathBack = true;
   private boolean enableTeleportBack = true;

   public static MiscTeleportManager getInstance() {
      return MiscTeleportManager.SingletonHolder.INSTANCE;
   }

   private MiscTeleportManager() {
   }

   public void saveBackLocation(ServerPlayer player) {
      if (this.enableTeleportBack) {
         UUID playerId = player.getUUID();
         TeleportLocation backLocation = new TeleportLocation(player);
         this.backLocations.put(playerId, backLocation);
         LOGGER.debug("Saved back location for {}: {}", player.getName().getString(), backLocation);
      }
   }

   public void saveDeathLocation(ServerPlayer player) {
      if (this.enableDeathBack) {
         UUID playerId = player.getUUID();
         TeleportLocation deathLocation = new TeleportLocation(player);
         this.deathLocations.put(playerId, deathLocation);
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.misc.death_location_saved"));
         LOGGER.info("Saved death location for {}: {}", player.getName().getString(), deathLocation);
      }
   }

   public boolean teleportBack(ServerPlayer player) {
      UUID playerId = player.getUUID();
      TeleportLocation deathLocation = this.deathLocations.get(playerId);
      TeleportLocation backLocation = this.backLocations.get(playerId);
      TeleportLocation targetLocation = null;
      boolean usedDeath;
      if (deathLocation != null && !this.isPlayerAtLocation(player, deathLocation)) {
         targetLocation = deathLocation;
         usedDeath = true;
      } else if (backLocation != null) {
         targetLocation = backLocation;
         usedDeath = false;
      } else {
         usedDeath = false;
      }

      if (targetLocation == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.no_back_location"));
         return false;
      } else {
         TeleportLocation currentLocation = new TeleportLocation(player);
         TeleportLocation finalTargetLocation = targetLocation;
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, finalTargetLocation, delayTicks, true).thenAccept(result -> {
            if (result.isSuccess()) {
               this.backLocations.put(playerId, currentLocation);
               if (usedDeath) {
                  this.deathLocations.remove(playerId);
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.death_teleport_success"));
               } else {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.back_success"));
               }

               LOGGER.info("Player {} teleported back to {}", player.getName().getString(), finalTargetLocation);
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.back_failed", result.getMessage()));
               LOGGER.warn("Failed back teleport for {}: {}", player.getName().getString(), result.getMessage());
            }
         });
         return true;
      }
   }

   private boolean isPlayerAtLocation(ServerPlayer player, TeleportLocation location) {
      double dx = player.getX() - location.getX();
      double dy = player.getY() - location.getY();
      double dz = player.getZ() - location.getZ();
      return Math.abs(dx) < 0.5 && Math.abs(dy) < 1.0 && Math.abs(dz) < 0.5 && player.level().dimension().location().toString().equals(location.getWorldName());
   }

   public boolean teleportToDeathLocation(ServerPlayer player) {
      UUID playerId = player.getUUID();
      TeleportLocation deathLocation = this.deathLocations.get(playerId);
      if (deathLocation == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.no_death_location"));
         return false;
      } else {
         this.saveBackLocation(player);
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, deathLocation, delayTicks, true).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.death_teleport_success"));
               this.deathLocations.remove(playerId);
               LOGGER.info("Player {} teleported to death location: {}", player.getName().getString(), deathLocation);
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.death_teleport_failed", result.getMessage()));
               LOGGER.warn("Failed death teleport for {}: {}", player.getName().getString(), result.getMessage());
            }
         });
         return true;
      }
   }

   public void clearBackLocation(ServerPlayer player) {
      UUID playerId = player.getUUID();
      this.backLocations.remove(playerId);
      this.deathLocations.remove(playerId);
      LOGGER.debug("Cleared back locations for {}", player.getName().getString());
   }

   public boolean hasBackLocation(ServerPlayer player) {
      UUID playerId = player.getUUID();
      return this.backLocations.containsKey(playerId) || this.deathLocations.containsKey(playerId);
   }

   public String getBackLocationInfo(ServerPlayer player) {
      UUID playerId = player.getUUID();
      TeleportLocation backLocation = this.backLocations.get(playerId);
      if (backLocation != null) {
         return MessageUtil.localize(
            "commands.neoessentials.teleport.misc.back_info",
            backLocation.getWorldName(),
            String.format("%.1f %.1f %.1f", backLocation.getX(), backLocation.getY(), backLocation.getZ())
         );
      } else {
         TeleportLocation deathLocation = this.deathLocations.get(playerId);
         return deathLocation != null
            ? MessageUtil.localize(
               "commands.neoessentials.teleport.misc.death_info",
               deathLocation.getWorldName(),
               String.format("%.1f %.1f %.1f", deathLocation.getX(), deathLocation.getY(), deathLocation.getZ())
            )
            : MessageUtil.localize("commands.neoessentials.teleport.misc.no_back_location");
      }
   }

   public void onPlayerDisconnect(ServerPlayer player) {
      LOGGER.debug("Player {} disconnected, keeping back location data", player.getName().getString());
   }

   @SubscribeEvent(
      receiveCanceled = true
   )
   public static void onPlayerDeathEvent(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         LOGGER.info(
            "[MiscTeleportManager] Death event fired for {} at ({}, {}, {}) in {} — cancelled={}",
            new Object[]{
               player.getName().getString(),
               String.format("%.2f", player.getX()),
               String.format("%.2f", player.getY()),
               String.format("%.2f", player.getZ()),
               player.level().dimension().location(),
               event.isCanceled()
            }
         );
         getInstance().saveDeathLocation(player);
         LOGGER.info(
            "[MiscTeleportManager] Saved death location for {} at ({}, {}, {})",
            new Object[]{
               player.getName().getString(), String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ())
            }
         );
      }
   }

   public int getMaxBackHistory() {
      return this.maxBackHistory;
   }

   public void setMaxBackHistory(int max) {
      this.maxBackHistory = Math.max(1, max);
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public boolean isEnableDeathBack() {
      return this.enableDeathBack;
   }

   public void setEnableDeathBack(boolean enable) {
      this.enableDeathBack = enable;
   }

   public boolean isEnableTeleportBack() {
      return this.enableTeleportBack;
   }

   public void setEnableTeleportBack(boolean enable) {
      this.enableTeleportBack = enable;
   }

   public String getStatistics() {
      return String.format(
         "MiscTeleport Statistics: %d back locations, %d death locations, delay=%ds", this.backLocations.size(), this.deathLocations.size(), this.teleportDelay
      );
   }

   public boolean teleportToTop(ServerPlayer player) {
      this.saveBackLocation(player);
      int currentX = (int)player.getX();
      int currentZ = (int)player.getZ();
      int maxY = player.level().getMaxBuildHeight() - 1;

      for (int y = maxY; y >= player.level().getMinBuildHeight(); y--) {
         if (!player.level().getBlockState(new BlockPos(currentX, y, currentZ)).isAir()) {
            int targetY = y + 1;
            TeleportLocation topLocation = new TeleportLocation(
               player.level().dimension().location().toString(),
               (double)currentX + 0.5,
               (double)targetY,
               (double)currentZ + 0.5,
               player.getYRot(),
               player.getXRot(),
               "system"
            );
            int delayTicks = this.teleportDelay * 20;
            TeleportUtil.teleportPlayer(player, topLocation, delayTicks, true).thenAccept(result -> {
               if (result.isSuccess()) {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.top_success"));
                  LOGGER.info("Player {} teleported to top at Y={}", player.getName().getString(), targetY);
               } else {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.top_failed", result.getMessage()));
               }
            });
            return true;
         }
      }

      player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.no_solid_block"));
      return false;
   }

   public boolean teleportJump(ServerPlayer player) {
      this.saveBackLocation(player);
      Vec3 lookDirection = player.getLookAngle();
      Vec3 currentPos = player.position();

      for (int distance = 1; distance <= 20; distance++) {
         double newX = currentPos.x + lookDirection.x * (double)distance;
         double newY = currentPos.y + lookDirection.y * (double)distance;
         double newZ = currentPos.z + lookDirection.z * (double)distance;
         BlockPos pos = new BlockPos((int)newX, (int)newY, (int)newZ);
         BlockPos posAbove = pos.above();
         if (player.level().getBlockState(pos).isAir() && player.level().getBlockState(posAbove).isAir()) {
            int finalDistance = distance;
            TeleportLocation jumpLocation = new TeleportLocation(
               player.level().dimension().location().toString(), newX, newY, newZ, player.getYRot(), player.getXRot(), "system"
            );
            int delayTicks = this.teleportDelay * 20;
            TeleportUtil.teleportPlayer(player, jumpLocation, delayTicks, true).thenAccept(result -> {
               if (result.isSuccess()) {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.jump_success"));
                  LOGGER.info("Player {} jumped through walls to distance {}", player.getName().getString(), finalDistance);
               } else {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.jump_failed", result.getMessage()));
               }
            });
            return true;
         }
      }

      player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.no_open_space"));
      return false;
   }

   public boolean teleportToLookingAt(ServerPlayer player) {
      this.saveBackLocation(player);
      HitResult hitResult = player.pick(100.0, 1.0F, false);
      if (hitResult.getType() == Type.BLOCK) {
         BlockHitResult blockHitResult = (BlockHitResult)hitResult;
         BlockPos targetPos = blockHitResult.getBlockPos();
         TeleportLocation jumpToLocation = new TeleportLocation(
            player.level().dimension().location().toString(),
            (double)targetPos.getX() + 0.5,
            (double)targetPos.getY() + 1.0,
            (double)targetPos.getZ() + 0.5,
            player.getYRot(),
            player.getXRot(),
            "system"
         );
         int delayTicks = this.teleportDelay * 20;
         TeleportUtil.teleportPlayer(player, jumpToLocation, delayTicks, true).thenAccept(result -> {
            if (result.isSuccess()) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.jumpto_success"));
               LOGGER.info("Player {} teleported to looking at: {}", player.getName().getString(), targetPos);
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.jumpto_failed", result.getMessage()));
            }
         });
         return true;
      } else {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.no_block_in_sight"));
         return false;
      }
   }

   public boolean randomTeleport(ServerPlayer player) {
      this.saveBackLocation(player);
      int maxDistance = 1000;
      int attempts = 10;
      Random random = new Random();

      for (int attempt = 0; attempt < attempts; attempt++) {
         int randomX = (int)player.getX() + random.nextInt(maxDistance * 2) - maxDistance;
         int randomZ = (int)player.getZ() + random.nextInt(maxDistance * 2) - maxDistance;
         int maxY = player.level().getMaxBuildHeight() - 1;

         for (int y = maxY; y >= player.level().getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(randomX, y, randomZ);
            BlockPos posAbove = pos.above();
            BlockPos posAbove2 = posAbove.above();
            if (!player.level().getBlockState(pos).isAir() && player.level().getBlockState(posAbove).isAir() && player.level().getBlockState(posAbove2).isAir()
               )
             {
               int safeY = y + 1;
               TeleportLocation randomLocation = new TeleportLocation(
                  player.level().dimension().location().toString(),
                  (double)randomX + 0.5,
                  (double)safeY,
                  (double)randomZ + 0.5,
                  player.getYRot(),
                  player.getXRot(),
                  "system"
               );
               int delayTicks = this.teleportDelay * 20;
               TeleportUtil.teleportPlayer(player, randomLocation, delayTicks, true).thenAccept(result -> {
                  if (result.isSuccess()) {
                     player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.misc.tpr_success", randomX, safeY, randomZ));
                     LOGGER.info("Player {} randomly teleported to: {} {} {}", new Object[]{player.getName().getString(), randomX, safeY, randomZ});
                  } else {
                     player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_failed", result.getMessage()));
                  }
               });
               return true;
            }
         }
      }

      player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_no_safe_location"));
      return false;
   }

   public void clearAllData() {
      this.backLocations.clear();
      this.deathLocations.clear();
      LOGGER.info("Cleared all misc teleport data");
   }

   private static class SingletonHolder {
      private static final MiscTeleportManager INSTANCE = new MiscTeleportManager();
   }
}
