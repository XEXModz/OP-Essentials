package com.zerog.neoessentials.util.handlers;

import com.zerog.neoessentials.util.commands.PlayerStateCommands;
import com.zerog.neoessentials.util.commands.UtilityCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class GodModeEventHandler {
   @SubscribeEvent
   public static void onLivingDamagePre(Pre event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (PlayerStateCommands.isGodMode(player.getUUID())) {
            event.setNewDamage(0.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         PlayerStateCommands.onPlayerJoin(player.getUUID());
         UtilityCommands.onPlayerJoin(player);
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         PlayerStateCommands.onPlayerQuit(player.getUUID());
         UtilityCommands.onPlayerQuit(player.getUUID());
      }
   }
}
