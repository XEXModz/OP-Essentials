package com.zerog.neoessentials.teleportation;

import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeleportUtil {
   private static final Logger LOGGER = LoggerFactory.getLogger(TeleportUtil.class);
   public static final int INSTANT_TELEPORT = 0;
   public static final int SHORT_DELAY = 20;
   public static final int MEDIUM_DELAY = 60;
   public static final int LONG_DELAY = 100;

   public static CompletableFuture<TeleportUtil.TeleportResult> teleportPlayer(ServerPlayer player, TeleportLocation location) {
      return teleportPlayer(player, location, 0, true);
   }

   public static CompletableFuture<TeleportUtil.TeleportResult> teleportPlayer(ServerPlayer player, TeleportLocation location, int delayTicks, boolean findSafe) {
      CompletableFuture<TeleportUtil.TeleportResult> future = new CompletableFuture<>();
      ConfigManager configManager = ConfigManager.getInstance();
      boolean allowTeleportInCombat = configManager.isAllowTeleportInCombatEnabled();
      if (!allowTeleportInCombat && CombatTracker.isInCombat(player)) {
         int remainingTime = CombatTracker.getRemainingCombatTime(player);
         future.complete(TeleportUtil.TeleportResult.failure("You cannot teleport while in combat! Please wait " + remainingTime + " second(s)."));
         return future;
      } else {
         List<String> protectedAreas = ConfigManager.getProtectedAreas();
         if (protectedAreas != null && !protectedAreas.isEmpty()) {
            try {
               Class<?> yawpApiClass = Class.forName("net.yawp.api.YawpAPI");
               Object yawpApi = yawpApiClass.getMethod("getInstance").invoke(null);
               List<?> regions = (List<?>)yawpApiClass.getMethod("getRegionsAt", ServerLevel.class, double.class, double.class, double.class)
                  .invoke(yawpApi, location.getLevel(), location.getX(), location.getY(), location.getZ());
               if (regions != null) {
                  for (Object region : regions) {
                     String regionName = (String)region.getClass().getMethod("getName").invoke(region);
                     if (protectedAreas.contains(regionName)) {
                        future.complete(
                           TeleportUtil.TeleportResult.failure("Teleportation is blocked: target location is in a protected area (" + regionName + ")!")
                        );
                        return future;
                     }
                  }
               }
            } catch (ClassNotFoundException var14) {
            } catch (Exception var15) {
               LOGGER.error("Error checking YAWP protected areas: {}", var15.getMessage(), var15);
            }
         }

         int maxDistance = configManager.getMaxTeleportDistance();
         if (maxDistance > 0) {
            TeleportLocation fromLoc = new TeleportLocation(player);
            if (fromLoc.getWorldName().equals(location.getWorldName())) {
               double dist = fromLoc.distanceTo(location);
               if (dist > (double)maxDistance) {
                  future.complete(TeleportUtil.TeleportResult.failure("Teleport distance exceeds the maximum allowed by config (" + maxDistance + ")!"));
                  return future;
               }
            }
         }

         if (location == null) {
            future.complete(TeleportUtil.TeleportResult.failure("Invalid teleport location"));
            return future;
         } else {
            ServerLevel targetLevel = location.getLevel();
            if (targetLevel == null) {
               future.complete(TeleportUtil.TeleportResult.failure("Target world not found or not loaded"));
               return future;
            } else {
               TeleportLocation finalLocation = location;
               findSafe = false;
               if (findSafe && !location.isSafe()) {
                  finalLocation = location.findSafeLocation();
                  if (finalLocation == null) {
                     future.complete(TeleportUtil.TeleportResult.failure("No safe teleport location found"));
                     return future;
                  }
               }

               ChunkPos chunkPos = new ChunkPos(new BlockPos((int)finalLocation.getX(), (int)finalLocation.getY(), (int)finalLocation.getZ()));
               if (!targetLevel.isLoaded(chunkPos.getWorldPosition())) {
                  targetLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, chunkPos, 3, chunkPos.getWorldPosition());
               }

               TeleportLocation teleportTo = finalLocation;
               if (delayTicks > 0) {
                  player.getServer().execute(() -> scheduleDelayedTeleport(player, teleportTo, delayTicks, future));
               } else {
                  executeTeleport(player, teleportTo, future);
               }

               return future;
            }
         }
      }
   }

   private static void scheduleDelayedTeleport(
      ServerPlayer player, TeleportLocation location, int delayTicks, CompletableFuture<TeleportUtil.TeleportResult> future
   ) {
      Vec3 originalPos = player.position();
      ConfigManager configManager = ConfigManager.getInstance();
      boolean cancelOnMovement = ConfigManager.isCancelOnMovementEnabled();
      boolean cancelOnDamage = configManager.isCancelOnDamageEnabled();
      Runnable cancelAction = () -> future.complete(TeleportUtil.TeleportResult.failure("Teleport cancelled - you moved/took damage!"));
      if (cancelOnDamage) {
         TeleportDamageCancelHandler.registerPendingTeleport(player, cancelAction);
      }

      player.getServer().tell(new TickTask(delayTicks, () -> {
         if (cancelOnDamage) {
            TeleportDamageCancelHandler.unregisterPendingTeleport(player);
         }

         if (cancelOnMovement && player.position().distanceTo(originalPos) > 1.5) {
            double distance = player.position().distanceTo(originalPos);
            LOGGER.debug("Teleport cancelled for {} - moved {} blocks (threshold: 1.5)", player.getName().getString(), String.format("%.2f", distance));
            future.complete(TeleportUtil.TeleportResult.failure("Teleport cancelled - you moved!"));
         } else if (player.hasDisconnected()) {
            future.complete(TeleportUtil.TeleportResult.failure("Player disconnected"));
         } else {
            executeTeleport(player, location, future);
         }
      }));
   }

   private static void executeTeleport(ServerPlayer player, TeleportLocation location, CompletableFuture<TeleportUtil.TeleportResult> future) {
      try {
         ServerLevel targetLevel = location.getLevel();
         if (targetLevel == null) {
            future.complete(TeleportUtil.TeleportResult.failure("Target world no longer available"));
            return;
         }

         ConfigManager configManager = ConfigManager.getInstance();
         if (configManager.getEnableParticleEffects() && !player.hasPermissions(2) && player.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 50; i++) {
               double dx = player.getX() + (player.getRandom().nextDouble() - 0.5) * 1.0;
               double dy = player.getY() + 1.0 + player.getRandom().nextDouble();
               double dz = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 1.0;
               serverLevel.addParticle(ParticleTypes.PORTAL, dx, dy, dz, 0.0, 0.0, 0.0);
            }
         }

         if (ConfigManager.getEnableSoundEffects() && !player.hasPermissions(2) && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         if (player.level() != targetLevel) {
            float yaw = location.getYaw();
            float pitch = location.getPitch();
            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) {
               yaw = 0.0F;
               LOGGER.warn("Invalid yaw during cross-dimension teleport, using 0.0f");
            }

            if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
               pitch = 0.0F;
               LOGGER.warn("Invalid pitch during cross-dimension teleport, using 0.0f");
            }

            player.teleportTo(targetLevel, location.getX(), location.getY(), location.getZ(), yaw, pitch);
         } else {
            player.teleportTo(location.getX(), location.getY(), location.getZ());
            float yawx = location.getYaw();
            float pitchx = location.getPitch();
            if (Float.isNaN(yawx) || Float.isInfinite(yawx)) {
               yawx = 0.0F;
               LOGGER.warn("Invalid yaw during same-dimension teleport, using 0.0f");
            }

            if (Float.isNaN(pitchx) || Float.isInfinite(pitchx)) {
               pitchx = 0.0F;
               LOGGER.warn("Invalid pitch during same-dimension teleport, using 0.0f");
            }

            player.setYRot(yawx);
            player.setXRot(pitchx);
         }

         if (configManager.getEnableParticleEffects() && !player.hasPermissions(2)) {
            for (int i = 0; i < 50; i++) {
               double dx = location.getX() + (player.getRandom().nextDouble() - 0.5) * 1.0;
               double dy = location.getY() + 1.0 + player.getRandom().nextDouble();
               double dz = location.getZ() + (player.getRandom().nextDouble() - 0.5) * 1.0;
               targetLevel.addParticle(ParticleTypes.PORTAL, dx, dy, dz, 0.0, 0.0, 0.0);
            }
         }

         if (ConfigManager.getEnableSoundEffects() && !player.hasPermissions(2)) {
            targetLevel.playSound(null, location.getX(), location.getY(), location.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         LOGGER.debug("Teleported {} to {}", player.getName().getString(), location.getLocationString());
         future.complete(TeleportUtil.TeleportResult.success("Teleported to " + location.getLocationString()));
      } catch (Exception var13) {
         LOGGER.error("Failed to teleport player {}: {}", new Object[]{player.getName().getString(), var13.getMessage(), var13});
         future.complete(TeleportUtil.TeleportResult.failure("Teleport failed: " + var13.getMessage()));
      }
   }

   public static int getHighestSafeY(ServerLevel level, int x, int z) {
      for (int y = level.getMaxBuildHeight() - 2; y >= level.getMinBuildHeight() + 1; y--) {
         BlockPos testPos = new BlockPos(x, y, z);
         if (isSafeLocation(level, testPos)) {
            return y;
         }
      }

      return level.getSeaLevel();
   }

   public static BlockPos findNearestSafeLocation(ServerLevel level, BlockPos center, int maxRadius) {
      if (isSafeLocation(level, center)) {
         return center;
      } else {
         for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                     int safeY = getHighestSafeY(level, center.getX() + dx, center.getZ() + dz);
                     BlockPos safePos = new BlockPos(center.getX() + dx, safeY, center.getZ() + dz);
                     if (isSafeLocation(level, safePos)) {
                        return safePos;
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   public static boolean isSafeLocation(ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos)) {
         return false;
      } else {
         BlockPos ground = pos.below();
         BlockPos head = pos.above();
         BlockState groundState = level.getBlockState(ground);
         BlockState feetState = level.getBlockState(pos);
         BlockState headState = level.getBlockState(head);
         if (groundState.isAir() || groundState.getCollisionShape(level, ground).isEmpty()) {
            return false;
         } else if (!feetState.getCollisionShape(level, pos).isEmpty() && !feetState.isAir()) {
            return false;
         } else if (!headState.getCollisionShape(level, head).isEmpty() && !headState.isAir()) {
            return false;
         } else {
            return isDangerousBlock(groundState) ? false : !isDangerousBlock(feetState);
         }
      }
   }

   private static boolean isDangerousBlock(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.LAVA
         || block == Blocks.WATER
         || block == Blocks.FIRE
         || block == Blocks.SOUL_FIRE
         || block == Blocks.MAGMA_BLOCK
         || block == Blocks.CACTUS
         || block == Blocks.SWEET_BERRY_BUSH
         || block == Blocks.WITHER_ROSE
         || block == Blocks.NETHER_PORTAL
         || block == Blocks.CAMPFIRE
         || block == Blocks.SOUL_CAMPFIRE
         || block == Blocks.POWDER_SNOW;
   }

   public static void sendCountdownMessage(ServerPlayer player, int seconds) {
      if (seconds > 0) {
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.countdown", seconds));
      }
   }

   public static class TeleportResult {
      private final boolean success;
      private final String message;
      private final TeleportLocation location;

      private TeleportResult(boolean success, String message, TeleportLocation location) {
         this.success = success;
         this.message = message;
         this.location = location;
      }

      public static TeleportUtil.TeleportResult success(String message) {
         return new TeleportUtil.TeleportResult(true, message, null);
      }

      public static TeleportUtil.TeleportResult success(String message, TeleportLocation location) {
         return new TeleportUtil.TeleportResult(true, message, location);
      }

      public static TeleportUtil.TeleportResult failure(String message) {
         return new TeleportUtil.TeleportResult(false, message, null);
      }

      public boolean isSuccess() {
         return this.success;
      }

      public String getMessage() {
         return this.message;
      }

      public TeleportLocation getLocation() {
         return this.location;
      }
   }
}
