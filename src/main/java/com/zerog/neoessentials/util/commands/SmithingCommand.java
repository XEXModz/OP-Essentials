package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;

public class SmithingCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("smithing")) {
         dispatcher.register(
            (LiteralArgumentBuilder)Commands.literal("smithing")
               .executes(
                  ctx -> {
                     ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.smithing.player_only");
                     if (player == null) {
                        return 0;
                     } else {
                        PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                           (CommandSourceStack)ctx.getSource(), "neoessentials.smithing"
                        );
                        if (!permResult.hasPermission()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                           return 0;
                        } else {
                           openSmithingGui(player);
                           player.sendSystemMessage(MessageUtil.success("commands.neoessentials.smithing.opened"));
                           return 1;
                        }
                     }
                  }
               )
         );
      }
   }

   private static void openSmithingGui(ServerPlayer player) {
      MenuProvider menuProvider = new MenuProvider() {
         public Component getDisplayName() {
            return MessageUtil.info("commands.neoessentials.smithing.title");
         }

         public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new SmithingMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
               public boolean stillValid(Player player) {
                  return true;
               }
            };
         }
      };
      player.openMenu(menuProvider);
   }
}
