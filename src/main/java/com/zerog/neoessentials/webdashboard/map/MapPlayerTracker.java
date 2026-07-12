package com.zerog.neoessentials.webdashboard.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class MapPlayerTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger(MapPlayerTracker.class);
   private static final int UPDATE_INTERVAL = 20;
   private static int tickCounter = 0;

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();
         tracker.updatePlayerLocation(player);
         LOGGER.debug("Map: Player joined - {}", player.getGameProfile().getName());
      }
   }

   @SubscribeEvent
   public static void onPlayerLeave(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();
         tracker.removePlayer(player.getUUID());
         LOGGER.debug("Map: Player left - {}", player.getGameProfile().getName());
      }
   }

   @SubscribeEvent
   public static void onServerTick(Post event) {
      tickCounter++;
      if (tickCounter >= 20) {
         tickCounter = 0;
         MinecraftServer server = event.getServer();
         if (server != null) {
            PlayerLocationTracker tracker = PlayerLocationTracker.getInstance();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               tracker.updatePlayerLocation(player);
            }
         }
      }
   }
}
