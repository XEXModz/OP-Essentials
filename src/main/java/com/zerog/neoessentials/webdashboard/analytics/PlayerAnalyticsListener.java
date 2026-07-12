package com.zerog.neoessentials.webdashboard.analytics;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class PlayerAnalyticsListener {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAnalyticsListener.class);

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      try {
         Player player = event.getEntity();
         PlayerSessionTracker tracker = PlayerSessionTracker.getInstance();
         tracker.trackPlayerJoin(player.getUUID(), player.getGameProfile().getName());
         LOGGER.debug("Analytics: Player joined - {} ({})", player.getGameProfile().getName(), player.getUUID());
      } catch (Exception var3) {
         LOGGER.error("Error tracking player join event", var3);
      }
   }

   @SubscribeEvent
   public static void onPlayerLeave(PlayerLoggedOutEvent event) {
      try {
         Player player = event.getEntity();
         PlayerSessionTracker tracker = PlayerSessionTracker.getInstance();
         tracker.trackPlayerLeave(player.getUUID());
         LOGGER.debug("Analytics: Player left - {} ({})", player.getGameProfile().getName(), player.getUUID());
      } catch (Exception var3) {
         LOGGER.error("Error tracking player leave event", var3);
      }
   }
}
