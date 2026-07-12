package com.zerog.neoessentials.inventory;

import javax.annotation.Nonnull;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PlayerInventoryContainerMenu extends AbstractContainerMenu {
   public PlayerInventoryContainerMenu(int id, Inventory ignored, ServerPlayer targetPlayer) {
      super(null, id);
      Container targetInventory = targetPlayer.getInventory();
      int slotCount = targetInventory.getContainerSize();
      int x = 8;
      int y = 18;

      for (int i = 0; i < slotCount; i++) {
         int row = i / 9;
         int col = i % 9;
         this.addSlot(new Slot(targetInventory, i, x + col * 18, y + row * 18));
      }
   }

   public boolean stillValid(@Nonnull Player player) {
      return true;
   }

   @Nonnull
   public ItemStack quickMoveStack(@Nonnull Player player, int index) {
      return ItemStack.EMPTY;
   }

   public static Component getTitle(ServerPlayer target) {
      return Component.literal(target.getName().getString() + "'s Inventory (Editable)");
   }
}
