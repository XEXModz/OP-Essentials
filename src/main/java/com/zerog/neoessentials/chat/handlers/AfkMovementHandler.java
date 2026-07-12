package com.zerog.neoessentials.chat.handlers;

import com.zerog.neoessentials.chat.AfkManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class AfkMovementHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkMovementHandler.class);
   private static final Map<UUID, AfkMovementHandler.PlayerPosition> lastPositions = new ConcurrentHashMap<>();
   private static final double POSITION_THRESHOLD = 0.1;
   private static final double ROTATION_THRESHOLD = 5.0;
   private static final int CHECK_INTERVAL_TICKS = 10;
   private static final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UUID var14 = player.getUUID();
         int tickCount = tickCounters.getOrDefault(var14, 0) + 1;
         if (tickCount < 10) {
            tickCounters.put(var14, tickCount);
         } else {
            tickCounters.put(var14, 0);
            Vec3 currentPosition = player.position();
            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            if (!Float.isNaN(currentYaw) && !Float.isInfinite(currentYaw) && !Float.isNaN(currentPitch) && !Float.isInfinite(currentPitch)) {
               AfkMovementHandler.PlayerPosition lastPosition = lastPositions.get(var14);
               if (lastPosition == null) {
                  lastPositions.put(var14, new AfkMovementHandler.PlayerPosition(currentPosition, currentYaw, currentPitch));
               } else {
                  double distanceMoved = lastPosition.distanceTo(currentPosition);
                  double rotationDiff = lastPosition.rotationDifference(currentYaw, currentPitch);
                  boolean positionChanged = distanceMoved > 0.1;
                  boolean rotationChanged = rotationDiff > 5.0;
                  if (positionChanged || rotationChanged) {
                     AfkManager.getInstance().updateActivity(var14);
                     if (positionChanged) {
                        LOGGER.debug("Activity tracked for {}: moved {:.2f} blocks", player.getName().getString(), distanceMoved);
                     }

                     if (rotationChanged) {
                        LOGGER.debug("Activity tracked for {}: rotated {:.1f} degrees", player.getName().getString(), rotationDiff);
                     }

                     lastPositions.put(var14, new AfkMovementHandler.PlayerPosition(currentPosition, currentYaw, currentPitch));
                  }
               }
            } else {
               LOGGER.debug("Skipping movement check for {} due to invalid rotation (NaN/Infinite)", player.getName().getString());
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UUID uuid = player.getUUID();
         lastPositions.remove(uuid);
         tickCounters.remove(uuid);
         LOGGER.debug("Movement tracking cleanup for: {}", player.getName().getString());
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UUID uuid = player.getUUID();
         Vec3 position = player.position();
         float yaw = player.getYRot();
         float pitch = player.getXRot();
         lastPositions.put(uuid, new AfkMovementHandler.PlayerPosition(position, yaw, pitch));
         tickCounters.put(uuid, 0);
         LOGGER.debug("Movement tracking initialized for: {}", player.getName().getString());
      }
   }

   public static double getPositionThreshold() {
      return 0.1;
   }

   public static double getRotationThreshold() {
      return 5.0;
   }

   private static class PlayerPosition {
      final Vec3 position;
      final float yaw;
      final float pitch;
      final long timestamp;

      PlayerPosition(Vec3 position, float yaw, float pitch) {
         this.position = position;
         this.yaw = !Float.isNaN(yaw) && !Float.isInfinite(yaw) ? yaw : 0.0F;
         this.pitch = !Float.isNaN(pitch) && !Float.isInfinite(pitch) ? pitch : 0.0F;
         this.timestamp = System.currentTimeMillis();
      }

      double distanceTo(Vec3 newPosition) {
         return this.position.distanceTo(newPosition);
      }

      double rotationDifference(float newYaw, float newPitch) {
         float yawDiff = this.normalizeAngle(newYaw - this.yaw);
         float pitchDiff = this.normalizeAngle(newPitch - this.pitch);
         return Math.sqrt((double)(yawDiff * yawDiff + pitchDiff * pitchDiff));
      }

      private float normalizeAngle(float angle) {
         while (angle > 180.0F) {
            angle -= 360.0F;
         }

         while (angle < -180.0F) {
            angle += 360.0F;
         }

         return angle;
      }
   }
}
