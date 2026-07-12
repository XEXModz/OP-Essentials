package com.zerog.neoessentials.teleportation;

import com.zerog.neoessentials.config.ConfigManager;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class TeleportDamageCancelHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(TeleportDamageCancelHandler.class);
   private static final ConcurrentHashMap<UUID, Runnable> pendingTeleports = new ConcurrentHashMap<>();

   public static void registerPendingTeleport(ServerPlayer player, Runnable cancelAction) {
      pendingTeleports.put(player.getUUID(), cancelAction);
   }

   public static void unregisterPendingTeleport(ServerPlayer player) {
      pendingTeleports.remove(player.getUUID());
   }

   @SubscribeEvent
   public static void onPlayerHurt(Pre event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (ConfigManager.getInstance().isCancelOnDamageEnabled()) {
            Runnable cancelAction = pendingTeleports.get(player.getUUID());
            if (cancelAction != null) {
               LOGGER.debug("Cancelling teleport for {} due to damage taken.", player.getName().getString());
               cancelAction.run();
               pendingTeleports.remove(player.getUUID());
            }
         }
      }
   }
}
