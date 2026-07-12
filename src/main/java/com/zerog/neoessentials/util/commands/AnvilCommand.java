package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
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
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class AnvilCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("anvil")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("anvil").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.anvil"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        ServerPlayer player = (ServerPlayer)((CommandSourceStack)ctx.getSource()).getEntity();
                        openAnvilGui(player);
                        player.sendSystemMessage(MessageUtil.success("commands.neoessentials.anvil.opened"));
                        return 1;
                     }
                  }
               )
         );
      }
   }

   private static void openAnvilGui(ServerPlayer player) {
      MenuProvider menuProvider = new MenuProvider() {
         public Component getDisplayName() {
            return MessageUtil.info("commands.neoessentials.anvil.title");
         }

         public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new AnvilMenu(containerId, playerInventory, ContainerLevelAccess.create(player.level(), player.blockPosition())) {
               public boolean stillValid(Player player) {
                  return true;
               }

               protected boolean mayPickup(Player player, boolean hasStack) {
                  return hasStack && player.experienceLevel >= this.repairItemCountCost;
               }

               protected void onTake(Player player, ItemStack stack) {
                  if (!player.getAbilities().instabuild) {
                     player.giveExperienceLevels(-this.repairItemCountCost);
                  }

                  this.inputSlots.setItem(0, ItemStack.EMPTY);
                  this.inputSlots.setItem(1, ItemStack.EMPTY);
                  this.repairItemCountCost = 0;
                  this.access.execute((level, pos) -> level.levelEvent(1030, pos, 0));
               }
            };
         }
      };
      player.openMenu(menuProvider);
   }
}
