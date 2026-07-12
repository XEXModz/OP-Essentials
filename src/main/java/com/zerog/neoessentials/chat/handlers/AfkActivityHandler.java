package com.zerog.neoessentials.chat.handlers;

import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.util.DebugLogger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class AfkActivityHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkActivityHandler.class);
   private static final Map<UUID, AfkActivityHandler.ActivityPattern> activityPatterns = new ConcurrentHashMap<>();
   private static final int REPETITIVE_ACTION_THRESHOLD = 30;
   private static final long REPETITIVE_TIMEFRAME = 60000L;
   private static final int SUSPICIOUS_SCORE_THRESHOLD = 300;

   private static void recordActivity(ServerPlayer player, String activityType) {
      if (player != null) {
         UUID uuid = player.getUUID();
         AfkActivityHandler.ActivityPattern pattern = activityPatterns.computeIfAbsent(uuid, k -> new AfkActivityHandler.ActivityPattern());
         pattern.recordActivity(activityType);
         if (!pattern.isSuspicious()) {
            AfkManager.getInstance().updateActivity(uuid);
            DebugLogger.log(LOGGER, "Activity tracked for {}: {} (score: {})", player.getName().getString(), activityType, pattern.getSuspiciousScore());
         } else {
            DebugLogger.log(
               LOGGER, "Suspicious activity pattern detected for {}: {} (score: {})", player.getName().getString(), activityType, pattern.getSuspiciousScore()
            );
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerRightClickBlock(RightClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         recordActivity(player, "interact_block");
      }
   }

   @SubscribeEvent
   public static void onPlayerRightClickItem(RightClickItem event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         recordActivity(player, "interact_item");
      }
   }

   @SubscribeEvent
   public static void onPlayerLeftClickBlock(LeftClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         recordActivity(player, "interact_attack");
      }
   }

   @SubscribeEvent
   public static void onItemToss(ItemTossEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         recordActivity(player, "item_toss");
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UUID uuid = player.getUUID();
         activityPatterns.remove(uuid);
         AfkManager.getInstance().onPlayerLogout(uuid);
         DebugLogger.log(LOGGER, "AFK tracking cleanup for: {}", player.getName().getString());
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UUID uuid = player.getUUID();
         activityPatterns.put(uuid, new AfkActivityHandler.ActivityPattern());
         AfkManager.getInstance().updateActivity(uuid);
         DebugLogger.log(LOGGER, "AFK tracking initialized for: {}", player.getName().getString());
      }
   }

   public static AfkActivityHandler.ActivityPattern getActivityPattern(UUID playerUuid) {
      return activityPatterns.get(playerUuid);
   }

   public static boolean isSuspiciousActivity(UUID playerUuid) {
      AfkActivityHandler.ActivityPattern pattern = activityPatterns.get(playerUuid);
      return pattern != null && pattern.isSuspicious();
   }

   public static void clearPatterns() {
      activityPatterns.clear();
   }

   public static Map<UUID, AfkActivityHandler.ActivityPattern> getActivityPatterns() {
      return new ConcurrentHashMap<>(activityPatterns);
   }

   public static class ActivityPattern {
      private final Map<String, Integer> actionCounts = new ConcurrentHashMap<>();
      private final Map<String, Long> lastActionTime = new ConcurrentHashMap<>();
      private int suspiciousScore = 0;
      private long lastActivity = System.currentTimeMillis();

      public void recordActivity(String activityType) {
         long now = System.currentTimeMillis();
         this.lastActivity = now;
         Long lastTime = this.lastActionTime.get(activityType);
         if (lastTime != null && now - lastTime < 60000L) {
            int count = this.actionCounts.getOrDefault(activityType, 0) + 1;
            this.actionCounts.put(activityType, count);
            if (count > 30) {
               this.suspiciousScore += 10;
               DebugLogger.log(AfkActivityHandler.LOGGER, "Detected repetitive {} activity: {} times", activityType, count);
            }
         } else {
            this.actionCounts.put(activityType, 1);
         }

         this.lastActionTime.put(activityType, now);
         if (this.suspiciousScore > 0 && lastTime != null && now - lastTime > 300000L) {
            this.suspiciousScore = Math.max(0, this.suspiciousScore - 5);
         }
      }

      public boolean isSuspicious() {
         return this.suspiciousScore > 300;
      }

      public int getSuspiciousScore() {
         return this.suspiciousScore;
      }

      public long getLastActivity() {
         return this.lastActivity;
      }
   }
}
