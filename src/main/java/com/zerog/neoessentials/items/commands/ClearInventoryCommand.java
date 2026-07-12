package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearInventoryCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(ClearInventoryCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("clearinventory")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("clearinventory").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
                     if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.clearinventory")) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
                        return 0;
                     } else {
                        int[] cleared = clear(player);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.clearinventory.detailed_success", cleared[0], cleared[1], cleared[2]), false
                           );
                        return 1;
                     }
                  }
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ci").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
                     if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.clearinventory")) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
                        return 0;
                     } else {
                        int[] cleared = clear(player);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.clearinventory.detailed_success", cleared[0], cleared[1], cleared[2]), false
                           );
                        return 1;
                     }
                  }
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("clearinv").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
                     if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.clearinventory")) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
                        return 0;
                     } else {
                        int[] cleared = clear(player);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.clearinventory.detailed_success", cleared[0], cleared[1], cleared[2]), false
                           );
                        return 1;
                     }
                  }
               )
         );
      }
   }

   public static int[] clear(ServerPlayer player) {
      int mainCleared = 0;
      int armorCleared = 0;
      int offhandCleared = 0;

      for (int i = 0; i < player.getInventory().items.size(); i++) {
         if (!((ItemStack)player.getInventory().items.get(i)).isEmpty()) {
            mainCleared++;
         }
      }

      player.getInventory().clearContent();

      for (int ix = 0; ix < player.getInventory().armor.size(); ix++) {
         if (!((ItemStack)player.getInventory().armor.get(ix)).isEmpty()) {
            armorCleared++;
         }
      }

      player.getInventory().armor.clear();

      for (int ixx = 0; ixx < player.getInventory().offhand.size(); ixx++) {
         if (!((ItemStack)player.getInventory().offhand.get(ixx)).isEmpty()) {
            offhandCleared++;
         }
      }

      player.getInventory().offhand.clear();
      LOGGER.info(
         "Player {} cleared inventory: {} main items, {} armor pieces, {} offhand items (total: {})",
         new Object[]{player.getName().getString(), mainCleared, armorCleared, offhandCleared, mainCleared + armorCleared + offhandCleared}
      );
      return new int[]{mainCleared, armorCleared, offhandCleared};
   }
}
