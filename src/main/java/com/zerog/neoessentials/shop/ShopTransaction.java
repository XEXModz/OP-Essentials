package com.zerog.neoessentials.shop;

import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.worth.WorthManager;
import com.zerog.neoessentials.shop.model.ShopData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShopTransaction {
   private static final Logger LOGGER = LoggerFactory.getLogger(ShopTransaction.class);

   private ShopTransaction() {
   }

   private static ShopTransaction.TransactionResult ok(BigDecimal price, int qty) {
      return new ShopTransaction.TransactionResult(ShopTransaction.ResultType.SUCCESS, null, price, qty);
   }

   private static ShopTransaction.TransactionResult fail(ShopTransaction.ResultType type) {
      return new ShopTransaction.TransactionResult(type, type.name(), BigDecimal.ZERO, 0);
   }

   public static ShopTransaction.TransactionResult executeBuy(ServerPlayer buyer, ShopData shop, ServerLevel level) {
      if (!shop.canBuy()) {
         return fail(ShopTransaction.ResultType.SHOP_DISABLED);
      } else {
         EconomyManager eco = EconomyManager.getInstance();
         if (eco == null) {
            return fail(ShopTransaction.ResultType.ERROR);
         } else {
            BigDecimal price = shop.buyPrice.setScale(2, RoundingMode.HALF_UP);
            ItemStack template = resolveItem(shop.itemId);
            if (template.isEmpty()) {
               return fail(ShopTransaction.ResultType.ERROR);
            } else {
               ItemStack item = template.copyWithCount(shop.quantity);
               BigDecimal buyerBalance = eco.getBalance(buyer.getUUID());
               if (buyerBalance.compareTo(price) < 0) {
                  return fail(ShopTransaction.ResultType.NOT_ENOUGH_MONEY);
               } else {
                  if (!shop.isAdminShop()) {
                     ChestBlockEntity chest = getChest(shop, level);
                     if (chest == null) {
                        return fail(ShopTransaction.ResultType.NO_CHEST);
                     }

                     int available = countItems(chest, template);
                     if (available < shop.quantity) {
                        return fail(ShopTransaction.ResultType.NOT_ENOUGH_STOCK);
                     }
                  }

                  if (!hasSpace(buyer.getInventory(), item)) {
                     return fail(ShopTransaction.ResultType.NO_SPACE);
                  } else {
                     boolean deducted = eco.subtractBalance(buyer.getUUID(), price);
                     if (!deducted) {
                        return fail(ShopTransaction.ResultType.NOT_ENOUGH_MONEY);
                     } else {
                        if (!shop.isAdminShop()) {
                           ChestBlockEntity chestx = getChest(shop, level);
                           if (chestx == null) {
                              eco.addBalance(buyer.getUUID(), price);
                              return fail(ShopTransaction.ResultType.NO_CHEST);
                           }

                           if (!removeItems(chestx, template, shop.quantity)) {
                              eco.addBalance(buyer.getUUID(), price);
                              return fail(ShopTransaction.ResultType.NOT_ENOUGH_STOCK);
                           }
                        }

                        giveItems(buyer, item);
                        if (!shop.isAdminShop() && shop.ownerUUID != null) {
                           eco.addBalance(shop.ownerUUID, price);
                        }

                        LOGGER.debug(
                           "[ChestShop] BUY: {} bought {}x {} for {} from {}",
                           new Object[]{buyer.getName().getString(), shop.quantity, shop.itemId, price, shop.ownerName}
                        );
                        return ok(price, shop.quantity);
                     }
                  }
               }
            }
         }
      }
   }

   public static ShopTransaction.TransactionResult executeSell(ServerPlayer seller, ShopData shop, ServerLevel level) {
      if (!shop.canSell()) {
         return fail(ShopTransaction.ResultType.SHOP_DISABLED);
      } else {
         EconomyManager eco = EconomyManager.getInstance();
         if (eco == null) {
            return fail(ShopTransaction.ResultType.ERROR);
         } else {
            BigDecimal price = shop.sellPrice.setScale(2, RoundingMode.HALF_UP);
            ItemStack template = resolveItem(shop.itemId);
            if (template.isEmpty()) {
               return fail(ShopTransaction.ResultType.ERROR);
            } else {
               ItemStack item = template.copyWithCount(shop.quantity);
               int available = countItems(seller.getInventory(), template);
               if (available < shop.quantity) {
                  return fail(ShopTransaction.ResultType.NOT_ENOUGH_STOCK);
               } else {
                  if (!shop.isAdminShop() && shop.ownerUUID != null) {
                     BigDecimal ownerBalance = eco.getBalance(shop.ownerUUID);
                     if (ownerBalance.compareTo(price) < 0) {
                        return fail(ShopTransaction.ResultType.NOT_ENOUGH_MONEY);
                     }
                  }

                  if (!shop.isAdminShop()) {
                     ChestBlockEntity chest = getChest(shop, level);
                     if (chest == null) {
                        return fail(ShopTransaction.ResultType.NO_CHEST);
                     }

                     if (!hasSpaceInContainer(chest, template, shop.quantity)) {
                        return fail(ShopTransaction.ResultType.NO_SPACE);
                     }
                  }

                  if (!removeItemsFromPlayer(seller, template, shop.quantity)) {
                     return fail(ShopTransaction.ResultType.NOT_ENOUGH_STOCK);
                  } else {
                     if (!shop.isAdminShop() && shop.ownerUUID != null) {
                        boolean deducted = eco.subtractBalance(shop.ownerUUID, price);
                        if (!deducted) {
                           giveItems(seller, item);
                           return fail(ShopTransaction.ResultType.NOT_ENOUGH_MONEY);
                        }
                     }

                     if (!shop.isAdminShop()) {
                        ChestBlockEntity chestx = getChest(shop, level);
                        if (chestx != null) {
                           addItems(chestx, template, shop.quantity);
                        }
                     }

                     eco.addBalance(seller.getUUID(), price);
                     LOGGER.debug(
                        "[ChestShop] SELL: {} sold {}x {} for {} to {}",
                        new Object[]{seller.getName().getString(), shop.quantity, shop.itemId, price, shop.ownerName}
                     );
                     return ok(price, shop.quantity);
                  }
               }
            }
         }
      }
   }

   private static ItemStack resolveItem(String itemId) {
      try {
         ItemStack result = WorthManager.resolveItem(itemId);
         if (result != null && !result.isEmpty()) {
            return result;
         }
      } catch (Exception var2) {
      }

      return ItemStack.EMPTY;
   }

   private static ChestBlockEntity getChest(ShopData shop, ServerLevel level) {
      if (!shop.hasChest) {
         return null;
      } else {
         BlockPos pos = shop.getChestPos();
         return level.getBlockEntity(pos) instanceof ChestBlockEntity c ? c : null;
      }
   }

   private static int countItems(Container container, ItemStack target) {
      int count = 0;

      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack slot = container.getItem(i);
         if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
            count += slot.getCount();
         }
      }

      return count;
   }

   private static boolean removeItems(Container container, ItemStack target, int amount) {
      int toRemove = amount;

      for (int i = 0; i < container.getContainerSize() && toRemove > 0; i++) {
         ItemStack slot = container.getItem(i);
         if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
            int take = Math.min(slot.getCount(), toRemove);
            slot.shrink(take);
            toRemove -= take;
            container.setItem(i, slot.isEmpty() ? ItemStack.EMPTY : slot);
         }
      }

      if (container instanceof BlockEntity be) {
         be.setChanged();
      }

      return toRemove == 0;
   }

   private static boolean removeItemsFromPlayer(ServerPlayer player, ItemStack target, int amount) {
      return removeItems(player.getInventory(), target, amount);
   }

   private static void giveItems(ServerPlayer player, ItemStack item) {
      ItemStack copy = item.copy();
      if (!player.getInventory().add(copy)) {
         player.drop(copy, false);
      }
   }

   private static void addItems(Container container, ItemStack target, int amount) {
      int toAdd = amount;

      for (int i = 0; i < container.getContainerSize() && toAdd > 0; i++) {
         ItemStack slot = container.getItem(i);
         if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, target)) {
            int space = slot.getMaxStackSize() - slot.getCount();
            int add = Math.min(space, toAdd);
            slot.grow(add);
            toAdd -= add;
            container.setItem(i, slot);
         }
      }

      for (int ix = 0; ix < container.getContainerSize() && toAdd > 0; ix++) {
         if (container.getItem(ix).isEmpty()) {
            int stackAmt = Math.min(toAdd, target.getMaxStackSize());
            container.setItem(ix, target.copyWithCount(stackAmt));
            toAdd -= stackAmt;
         }
      }

      if (container instanceof BlockEntity be) {
         be.setChanged();
      }
   }

   private static boolean hasSpaceInContainer(Container container, ItemStack target, int amount) {
      int canFit = 0;

      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack slot = container.getItem(i);
         if (slot.isEmpty()) {
            canFit += target.getMaxStackSize();
         } else if (ItemStack.isSameItemSameComponents(slot, target)) {
            canFit += slot.getMaxStackSize() - slot.getCount();
         }

         if (canFit >= amount) {
            return true;
         }
      }

      return false;
   }

   private static boolean hasSpace(Inventory inv, ItemStack item) {
      return hasSpaceInContainer(inv, item, item.getCount());
   }

   public static enum ResultType {
      SUCCESS,
      NOT_ENOUGH_MONEY,
      NOT_ENOUGH_STOCK,
      NO_SPACE,
      NO_CHEST,
      NO_ECONOMY_ACCOUNT,
      SHOP_DISABLED,
      ERROR;
   }

   public static class TransactionResult {
      public final ShopTransaction.ResultType type;
      public final String message;
      public final BigDecimal price;
      public final int quantity;

      TransactionResult(ShopTransaction.ResultType type, String message, BigDecimal price, int quantity) {
         this.type = type;
         this.message = message;
         this.price = price;
         this.quantity = quantity;
      }

      public boolean isSuccess() {
         return this.type == ShopTransaction.ResultType.SUCCESS;
      }
   }
}
