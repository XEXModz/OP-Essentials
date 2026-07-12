package com.zerog.neoessentials.chat.handlers;

import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.util.DebugLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class AfkTablistHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkTablistHandler.class);

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         updatePlayerTablistName(player);
      }
   }

   public static void updatePlayerTablistName(ServerPlayer player) {
      try {
         AfkManager afkManager = AfkManager.getInstance();
         if (!afkManager.isEnableTablistIndicator()) {
            return;
         }

         String originalName = player.getName().getString();
         String displayName;
         if (afkManager.isAfk(player.getUUID())) {
            String prefix = afkManager.getTablistAfkPrefix();
            String suffix = afkManager.getTablistAfkSuffix();
            displayName = prefix + originalName + suffix;
            DebugLogger.log(LOGGER, "Setting AFK tablist name for {}: {}", originalName, displayName);
         } else {
            displayName = originalName;
            DebugLogger.log(LOGGER, "Setting normal tablist name for {}: {}", originalName, originalName);
         }

         LOGGER.debug("Tablist display would be: {}", displayName);
      } catch (Exception var6) {
         LOGGER.error("Failed to update tablist name for player {}: {}", new Object[]{player.getName().getString(), var6.getMessage(), var6});
      }
   }

   public static void updateAllPlayersTablistNames() {
      try {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               updatePlayerTablistName(player);
            }

            LOGGER.debug("Updated tablist names for all online players");
         }
      } catch (Exception var3) {
         LOGGER.error("Failed to update tablist names for all players: {}", var3.getMessage(), var3);
      }
   }

   public static void onPlayerAfk(ServerPlayer player) {
      updatePlayerTablistName(player);
      LOGGER.debug("Updated tablist for AFK player: {}", player.getName().getString());
   }

   public static void onPlayerReturnFromAfk(ServerPlayer player) {
      updatePlayerTablistName(player);
      LOGGER.debug("Updated tablist for returning player: {}", player.getName().getString());
   }
}
