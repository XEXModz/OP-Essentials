package com.zerog.neoessentials.chat.handlers;

import com.zerog.neoessentials.chat.AfkManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class AfkMovementDetector {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkMovementDetector.class);
   private static final double MOVEMENT_THRESHOLD = 0.1;
   private static final Map<UUID, AfkMovementDetector.PlayerPosition> lastPositions = new HashMap<>();
   private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "AFK-MovementDetector");
      t.setDaemon(true);
      return t;
   });

   private static void checkAllPlayersMovement() {
      try {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server == null) {
            return;
         }

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            checkPlayerMovement(player);
         }
      } catch (Exception var3) {
         LOGGER.error("Error checking player movement", var3);
      }
   }

   private static void checkPlayerMovement(ServerPlayer player) {
      UUID playerId = player.getUUID();
      float yaw = player.getYRot();
      float pitch = player.getXRot();
      if (!Float.isNaN(yaw) && !Float.isInfinite(yaw) && !Float.isNaN(pitch) && !Float.isInfinite(pitch)) {
         AfkMovementDetector.PlayerPosition currentPos = new AfkMovementDetector.PlayerPosition(player.getX(), player.getY(), player.getZ(), yaw, pitch);
         AfkMovementDetector.PlayerPosition lastPos = lastPositions.get(playerId);
         if (lastPos != null) {
            double distanceMoved = currentPos.distanceTo(lastPos);
            double rotationChanged = currentPos.rotationDifference(lastPos);
            double rotationThreshold = AfkManager.getInstance().getRotationThreshold();
            if (distanceMoved > 0.1 || rotationChanged > rotationThreshold) {
               AfkManager.getInstance().updateActivity(playerId);
               LOGGER.debug(
                  "Movement activity tracked for {}: distance={}, rotation={} (threshold={})",
                  new Object[]{player.getName().getString(), distanceMoved, rotationChanged, rotationThreshold}
               );
            }
         }

         lastPositions.put(playerId, currentPos);
      } else {
         LOGGER.debug("Skipping movement check for {} due to invalid rotation (NaN/Infinite)", player.getName().getString());
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         lastPositions.remove(player.getUUID());
         LOGGER.debug("Cleaned up movement tracking for player: {}", player.getName().getString());
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         AfkMovementDetector.PlayerPosition currentPos = new AfkMovementDetector.PlayerPosition(
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
         );
         lastPositions.put(player.getUUID(), currentPos);
         LOGGER.debug("Initialized movement tracking for player: {}", player.getName().getString());
      }
   }

   public static void shutdown() {
      executor.shutdown();

      try {
         if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
            executor.shutdownNow();
         }
      } catch (InterruptedException var1) {
         executor.shutdownNow();
      }
   }

   static {
      executor.scheduleAtFixedRate(AfkMovementDetector::checkAllPlayersMovement, 5L, 5L, TimeUnit.SECONDS);
   }

   private static class PlayerPosition {
      private final double x;
      private final double y;
      private final double z;
      private final float yaw;
      private final float pitch;

      public PlayerPosition(double x, double y, double z, float yaw, float pitch) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.yaw = !Float.isNaN(yaw) && !Float.isInfinite(yaw) ? yaw : 0.0F;
         this.pitch = !Float.isNaN(pitch) && !Float.isInfinite(pitch) ? pitch : 0.0F;
      }

      public double distanceTo(AfkMovementDetector.PlayerPosition other) {
         double dx = this.x - other.x;
         double dy = this.y - other.y;
         double dz = this.z - other.z;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }

      public double rotationDifference(AfkMovementDetector.PlayerPosition other) {
         double yawDiff = (double)Math.abs(this.yaw - other.yaw);
         double pitchDiff = (double)Math.abs(this.pitch - other.pitch);
         if (yawDiff > 180.0) {
            yawDiff = 360.0 - yawDiff;
         }

         return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
      }
   }
}
