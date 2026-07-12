package com.zerog.neoessentials.shop.model;

import java.math.BigDecimal;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public class ShopData {
   public static final int NAME_LINE = 0;
   public static final int QUANTITY_LINE = 1;
   public static final int PRICE_LINE = 2;
   public static final int ITEM_LINE = 3;
   public static final String ADMIN_SHOP_NAME = "Admin Shop";
   public UUID ownerUUID;
   public String ownerName;
   public int quantity;
   public BigDecimal buyPrice;
   public BigDecimal sellPrice;
   public String itemId;
   public String signDimension;
   public int signX;
   public int signY;
   public int signZ;
   public String chestDimension;
   public int chestX;
   public int chestY;
   public int chestZ;
   public boolean hasChest;
   public boolean itemPending = false;

   public BlockPos getSignPos() {
      return new BlockPos(this.signX, this.signY, this.signZ);
   }

   public BlockPos getChestPos() {
      return this.hasChest ? new BlockPos(this.chestX, this.chestY, this.chestZ) : null;
   }

   public boolean isAdminShop() {
      return this.ownerUUID == null || "Admin Shop".equalsIgnoreCase(this.ownerName != null ? this.ownerName.trim() : "");
   }

   public boolean canBuy() {
      return this.buyPrice != null;
   }

   public boolean canSell() {
      return this.sellPrice != null;
   }

   public String toKey() {
      return this.signDimension + "@" + this.signX + "," + this.signY + "," + this.signZ;
   }

   @Override
   public String toString() {
      return String.format(
         "ShopData{owner=%s, qty=%d, buy=%s, sell=%s, item=%s, pos=%s}",
         this.ownerName,
         this.quantity,
         this.buyPrice != null ? this.buyPrice.toPlainString() : "—",
         this.sellPrice != null ? this.sellPrice.toPlainString() : "—",
         this.itemId,
         this.toKey()
      );
   }
}
