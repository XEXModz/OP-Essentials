package com.zerog.neoessentials.shop.handlers;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.worth.WorthManager;
import com.zerog.neoessentials.shop.ShopManager;
import com.zerog.neoessentials.shop.ShopParser;
import com.zerog.neoessentials.shop.ShopTransaction;
import com.zerog.neoessentials.shop.model.ShopData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class ShopInteractHandler {
   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onRightClick(RightClickBlock event) {
      if (ConfigManager.isChestShopEnabled()) {
         if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
               ServerLevel level = player.serverLevel();
               BlockPos pos = event.getPos();
               BlockEntity be = level.getBlockEntity(pos);
               if (be instanceof SignBlockEntity) {
                  String dimension = level.dimension().location().toString();
                  ShopData shop = ShopManager.getInstance().getShopBySign(dimension, pos);
                  if (shop != null) {
                     event.setCanceled(true);
                     if (!shop.itemPending) {
                        if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
                           sendShopInfo(player, shop);
                        } else if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.shop.use")) {
                           player.sendSystemMessage(Component.literal("§cYou don't have permission to use shops."));
                        } else if (!shop.canBuy()) {
                           player.sendSystemMessage(Component.literal("§cThis shop does not sell items."));
                        } else {
                           ShopTransaction.TransactionResult result = ShopTransaction.executeBuy(player, shop, level);
                           sendTransactionResult(player, result, shop, true);
                        }
                     } else {
                        if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
                           ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
                           if (held.isEmpty()) {
                              player.sendSystemMessage(Component.literal("§eHold the item you want this shop to trade, then right-click the sign."));
                           } else {
                              shop.itemId = WorthManager.getItemId(held);
                              shop.itemPending = false;
                              ShopManager.getInstance().registerShop(shop);
                              ShopSignHandler.writeSignLines(level, pos, ShopParser.formatSignLines(shop));
                              String currency = EconomyManager.getInstance().getCurrencySymbol();
                              player.sendSystemMessage(Component.literal("§aItem set to §f" + ShopParser.buildItemDisplayName(shop.itemId) + "§a!"));
                              if (shop.buyPrice != null) {
                                 player.sendSystemMessage(Component.literal("§eBuy price:  §f" + currency + shop.buyPrice.toPlainString()));
                              }

                              if (shop.sellPrice != null) {
                                 player.sendSystemMessage(Component.literal("§eSell price: §f" + currency + shop.sellPrice.toPlainString()));
                              }

                              player.sendSystemMessage(Component.literal("§aShop is now active."));
                           }
                        } else {
                           player.sendSystemMessage(Component.literal("§cThis shop is not yet ready."));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onLeftClick(LeftClickBlock event) {
      if (ConfigManager.isChestShopEnabled()) {
         if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel var8 = player.serverLevel();
            BlockPos pos = event.getPos();
            BlockEntity be = var8.getBlockEntity(pos);
            if (be instanceof SignBlockEntity) {
               String dimension = var8.dimension().location().toString();
               ShopData shop = ShopManager.getInstance().getShopBySign(dimension, pos);
               if (shop != null) {
                  if (shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID())) {
                     event.setCanceled(true);
                     sendShopInfo(player, shop);
                  } else if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.shop.use")) {
                     event.setCanceled(true);
                     player.sendSystemMessage(Component.literal("§cYou don't have permission to use shops."));
                  } else if (!shop.canSell()) {
                     event.setCanceled(true);
                     player.sendSystemMessage(Component.literal("§cThis shop does not buy items."));
                  } else {
                     event.setCanceled(true);
                     ShopTransaction.TransactionResult result = ShopTransaction.executeSell(player, shop, var8);
                     sendTransactionResult(player, result, shop, false);
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onBlockBreak(BreakEvent event) {
      if (ConfigManager.isChestShopEnabled()) {
         if (event.getPlayer() instanceof ServerPlayer player) {
            if (event.getLevel() instanceof ServerLevel level) {
               BlockPos var9 = event.getPos();
               String dimension = level.dimension().location().toString();
               ShopData shop = ShopManager.getInstance().getShopBySign(dimension, var9);
               if (shop == null) {
                  shop = ShopManager.getInstance().getShopByChest(dimension, var9);
               }

               if (shop != null) {
                  boolean isOwner = shop.ownerUUID != null && shop.ownerUUID.equals(player.getUUID());
                  boolean isAdmin = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.shop.admin.remove");
                  if (!isOwner && !isAdmin) {
                     event.setCanceled(true);
                     player.sendSystemMessage(Component.literal("§cYou cannot break someone else's shop."));
                  } else {
                     ShopManager.getInstance().removeShop(dimension, shop.getSignPos());
                     player.sendSystemMessage(Component.literal("§aShop removed."));
                  }
               }
            }
         }
      }
   }

   private static void sendTransactionResult(ServerPlayer player, ShopTransaction.TransactionResult result, ShopData shop, boolean buying) {
      String currency = EconomyManager.getInstance().getCurrencySymbol();
      String itemDisplay = ShopParser.buildItemDisplayName(shop.itemId);
      switch (result.type) {
         case SUCCESS:
            if (buying) {
               player.sendSystemMessage(
                  Component.literal(
                     String.format(
                        "§aYou bought §f%dx %s §afor §f%s%s§a from §f%s§a.",
                        result.quantity,
                        itemDisplay,
                        currency,
                        result.price.toPlainString(),
                        shop.ownerName
                     )
                  )
               );
            } else {
               player.sendSystemMessage(
                  Component.literal(String.format("§aYou sold §f%dx %s §afor §f%s%s§a.", result.quantity, itemDisplay, currency, result.price.toPlainString()))
               );
            }
            break;
         case NOT_ENOUGH_MONEY:
            player.sendSystemMessage(Component.literal(buying ? "§cYou don't have enough money to buy that." : "§cThe shop owner can't afford to buy that."));
            break;
         case NOT_ENOUGH_STOCK:
            player.sendSystemMessage(Component.literal(buying ? "§cThis shop is out of stock." : "§cYou don't have enough of that item."));
            break;
         case NO_SPACE:
            player.sendSystemMessage(Component.literal(buying ? "§cYour inventory is full." : "§cThe shop's chest is full."));
            break;
         case NO_CHEST:
            player.sendSystemMessage(Component.literal("§cShop has no linked chest."));
            break;
         case SHOP_DISABLED:
            player.sendSystemMessage(Component.literal(buying ? "§cThis shop doesn't sell items." : "§cThis shop doesn't buy items."));
            break;
         default:
            player.sendSystemMessage(Component.literal("§cTransaction failed (internal error)."));
      }
   }

   private static void sendShopInfo(ServerPlayer player, ShopData shop) {
      String currency = EconomyManager.getInstance().getCurrencySymbol();
      String itemDisplay = ShopParser.buildItemDisplayName(shop.itemId);
      player.sendSystemMessage(Component.literal("§6§l--- Shop Info ---"));
      player.sendSystemMessage(Component.literal("§eOwner: §f" + shop.ownerName));
      player.sendSystemMessage(Component.literal("§eItem:  §f" + shop.quantity + "x " + itemDisplay));
      if (shop.buyPrice != null) {
         player.sendSystemMessage(Component.literal("§eBuy:   §f" + currency + shop.buyPrice.toPlainString()));
      }

      if (shop.sellPrice != null) {
         player.sendSystemMessage(Component.literal("§eSell:  §f" + currency + shop.sellPrice.toPlainString()));
      }

      if (!shop.isAdminShop() && shop.hasChest) {
         player.sendSystemMessage(Component.literal("§eChest: §f" + shop.chestX + ", " + shop.chestY + ", " + shop.chestZ));
      }
   }
}
