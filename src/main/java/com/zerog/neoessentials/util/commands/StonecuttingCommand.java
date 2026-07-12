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
import net.minecraft.world.inventory.StonecutterMenu;

public class StonecuttingCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("stonecutting")) {
         registerStonecuttingCommand(dispatcher, "stonecutting");
         registerStonecuttingCommand(dispatcher, "stonecutter");
         registerStonecuttingCommand(dispatcher, "stonecut");
      }
   }

   private static void registerStonecuttingCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal(commandName)
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer(
                     (CommandSourceStack)ctx.getSource(), "commands.neoessentials.stonecutting.player_only"
                  );
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.stonecutting"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        openStonecuttingGui(player);
                        player.sendSystemMessage(MessageUtil.success("commands.neoessentials.stonecutting.opened"));
                        return 1;
                     }
                  }
               }
            )
      );
   }

   private static void openStonecuttingGui(ServerPlayer player) {
      MenuProvider menuProvider = new MenuProvider() {
         public Component getDisplayName() {
            return MessageUtil.info("commands.neoessentials.stonecutting.title");
         }

         public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new StonecutterMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
               public boolean stillValid(Player player) {
                  return true;
               }
            };
         }
      };
      player.openMenu(menuProvider);
   }
}
