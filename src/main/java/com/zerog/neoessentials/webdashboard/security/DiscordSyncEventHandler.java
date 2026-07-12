package com.zerog.neoessentials.webdashboard.security;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class DiscordSyncEventHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(DiscordSyncEventHandler.class);

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         new Thread(() -> syncPlayerPermissions(player), "DiscordPermissionSync-" + player.getName().getString()).start();
      }
   }

   private static void syncPlayerPermissions(ServerPlayer player) {
      try {
         Thread.sleep(1000L);
         DiscordAuthConfig config = DiscordAuthConfig.load();
         if (!config.isPermissionSyncEnabled() || !config.isSyncOnJoin()) {
            return;
         }

         DiscordPermissionSync syncService = DiscordPermissionSync.getInstance();
         if (!syncService.isEnabled()) {
            return;
         }

         String playerName = player.getName().getString();
         LOGGER.debug("Starting Discord permission sync for player '{}'", playerName);
         DiscordPermissionSync.SyncResult result = syncService.syncPlayerPermissions(player);
         if (result.isSuccess() && result.getPermissionsGranted() > 0) {
            player.sendSystemMessage(
               Component.literal("✓ ")
                  .withStyle(ChatFormatting.GREEN)
                  .append(Component.literal("Your permissions have been synced from Discord.").withStyle(ChatFormatting.GRAY))
            );
            LOGGER.info("Discord permission sync completed for '{}': {}", playerName, result.getMessage());
         } else if (!result.isSuccess()) {
            LOGGER.debug("Discord permission sync skipped for '{}': {}", playerName, result.getMessage());
         }
      } catch (InterruptedException var5) {
         Thread.currentThread().interrupt();
         LOGGER.warn("Discord permission sync interrupted for player '{}'", player.getName().getString());
      } catch (Exception var6) {
         LOGGER.error("Error during Discord permission sync for player '{}': {}", new Object[]{player.getName().getString(), var6.getMessage(), var6});
      }
   }
}
