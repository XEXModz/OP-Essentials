package com.zerog.neoessentials.tablist;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class TablistEventHandler {
   @SubscribeEvent
   public static void onServerTick(Post event) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         TablistManager.getInstance().onTick(server);
      }
   }

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         MinecraftServer server = player.getServer();
         if (server != null) {
            TablistManager.getInstance().onPlayerJoin(player, server);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerQuit(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         MinecraftServer server = player.getServer();
         if (server != null) {
            TablistManager.getInstance().cleanupPlayerTeam(player);
            TablistManager.getInstance().clearCustomName(player.getUUID());
            TablistManager.getInstance().onPlayerQuit(server);
         }
      }
   }
}
