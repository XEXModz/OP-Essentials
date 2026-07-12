package com.zerog.neoessentials.items.handlers;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.items.commands.PowertoolCommand;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickEmpty;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class ItemInteractionHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(ItemInteractionHandler.class);

   @SubscribeEvent
   public static void onRightClickItem(RightClickItem event) {
      handlePowertool(event);
   }

   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      handlePowertool(event);
   }

   @SubscribeEvent
   public static void onRightClickEmpty(RightClickEmpty event) {
      handlePowertool(event);
   }

   private static void handlePowertool(PlayerInteractEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            UUID playerUUID = player.getUUID();
            if (!PowertoolCommand.hasPowertoolData(playerUUID)) {
               return;
            }

            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.isEmpty()) {
               return;
            }

            ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
            String itemId = itemKey.toString();
            String command = PowertoolCommand.getPowertoolCommand(playerUUID, itemId);
            if (command == null || command.isBlank()) {
               return;
            }

            if (!PowertoolCommand.isPowertoolEnabled(playerUUID)) {
               return;
            }

            if (!PermissionAPI.hasPermission(playerUUID, "neoessentials.item.powertool")) {
               return;
            }

            if (event instanceof ICancellableEvent cancellable) {
               cancellable.setCanceled(true);
            }

            MinecraftServer server = player.getServer();
            if (server == null) {
               return;
            }

            try {
               server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command.startsWith("/") ? command.substring(1) : command);
               LOGGER.debug("Executed powertool '{}' for {}", command, player.getName().getString());
            } catch (Exception var9) {
               LOGGER.error("Failed to execute powertool command '{}' for player {}", new Object[]{command, player.getName().getString(), var9});
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.powertool.execution_failed"));
            }
         } catch (Exception var10) {
            LOGGER.error("Error in powertool interaction handler", var10);
         }
      }
   }
}
