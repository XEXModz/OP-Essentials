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
import net.minecraft.world.inventory.CraftingMenu;

public class CraftingCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("crafting")) {
         registerCraftingCommand(dispatcher, "crafting");
         registerCraftingCommand(dispatcher, "craft");
         registerCraftingCommand(dispatcher, "workbench");
      }
   }

   private static void registerCraftingCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal(commandName)
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.crafting.player_only");
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.crafting"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        openCraftingGui(player);
                        player.sendSystemMessage(MessageUtil.success("commands.neoessentials.crafting.opened"));
                        return 1;
                     }
                  }
               }
            )
      );
   }

   private static void openCraftingGui(ServerPlayer player) {
      MenuProvider menuProvider = new MenuProvider() {
         public Component getDisplayName() {
            return MessageUtil.info("commands.neoessentials.crafting.title");
         }

         public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new CraftingMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
               public boolean stillValid(Player player) {
                  return true;
               }

               public void removed(Player player) {
                  super.removed(player);
               }
            };
         }
      };
      player.openMenu(menuProvider);
   }
}
