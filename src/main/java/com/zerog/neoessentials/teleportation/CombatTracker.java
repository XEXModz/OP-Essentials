package com.zerog.neoessentials.teleportation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombatTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger(CombatTracker.class);
   private static final long COMBAT_TIMEOUT_MS = 5000L;
   private static final Map<UUID, Long> combatEndTimestamps = new ConcurrentHashMap<>();

   public static void markInCombat(ServerPlayer player) {
      long endTime = System.currentTimeMillis() + 5000L;
      combatEndTimestamps.put(player.getUUID(), endTime);
      LOGGER.debug("Player {} marked in combat until {}", player.getName().getString(), endTime);
   }

   public static boolean isInCombat(ServerPlayer player) {
      Long end = combatEndTimestamps.get(player.getUUID());
      boolean inCombat = end != null && end > System.currentTimeMillis();
      if (end != null && !inCombat) {
         combatEndTimestamps.remove(player.getUUID());
      }

      return inCombat;
   }

   public static int getRemainingCombatTime(ServerPlayer player) {
      Long end = combatEndTimestamps.get(player.getUUID());
      if (end == null) {
         return 0;
      } else {
         long remaining = end - System.currentTimeMillis();
         if (remaining <= 0L) {
            combatEndTimestamps.remove(player.getUUID());
            return 0;
         } else {
            return (int)Math.ceil((double)remaining / 1000.0);
         }
      }
   }

   public static void clearCombat(ServerPlayer player) {
      combatEndTimestamps.remove(player.getUUID());
      LOGGER.debug("Cleared combat status for player {}", player.getName().getString());
   }

   public static void clearAll() {
      combatEndTimestamps.clear();
   }
}
