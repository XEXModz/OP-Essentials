package com.zerog.neoessentials.economy.worth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(SellCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isEconomyEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                    "sell"
                                 )
                                 .requires(src -> {
                                    ServerPlayer p = src.getPlayer();
                                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sell");
                                 }))
                              .executes(ctx -> {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.sell.usage"));
                                 return 0;
                              }))
                           .then(
                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("hand").requires(src -> {
                                    ServerPlayer p = src.getPlayer();
                                    return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sell.hand");
                                 })).executes(ctx -> executeSellHand(ctx, 0)))
                                 .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                       .executes(ctx -> executeSellHand(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                                 )
                           ))
                        .then(((LiteralArgumentBuilder)Commands.literal("inventory").requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sell.bulk");
                        })).executes(SellCommand::executeSellAll)))
                     .then(((LiteralArgumentBuilder)Commands.literal("all").requires(src -> {
                        ServerPlayer p = src.getPlayer();
                        return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sell.bulk");
                     })).executes(SellCommand::executeSellAll)))
                  .then(((LiteralArgumentBuilder)Commands.literal("invent").requires(src -> {
                     ServerPlayer p = src.getPlayer();
                     return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sell.bulk");
                  })).executes(SellCommand::executeSellAll)))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> executeSellItem(ctx, StringArgumentType.getString(ctx, "item"), 0)))
                     .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1))
                           .executes(ctx -> executeSellItem(ctx, StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "amount")))
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("setworth").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.setworth");
               }))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word())
                        .then(Commands.literal("remove").executes(ctx -> executeRemoveWorth(ctx, StringArgumentType.getString(ctx, "item")))))
                     .then(
                        Commands.argument("price", DoubleArgumentType.doubleArg(0.0))
                           .executes(ctx -> executeSetWorth(ctx, StringArgumentType.getString(ctx, "item"), DoubleArgumentType.getDouble(ctx, "price")))
                     )
               )
         );
      }
   }

   private static int executeSellHand(CommandContext<CommandSourceStack> ctx, int amount) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.sell.no_item_in_hand"));
            return 0;
         } else {
            int qty = amount > 0 ? Math.min(amount, held.getCount()) : held.getCount();
            return doSell(source, player, held, qty);
         }
      }
   }

   private static int executeSellAll(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         WorthManager wm = WorthManager.getInstance();
         BigDecimal total = BigDecimal.ZERO;
         int typesSold = 0;
         List<String> skippedNamed = new ArrayList<>();
         Inventory inv = player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
               if (!wm.isAllowSellNamedItems() && s.has(DataComponents.CUSTOM_NAME)) {
                  skippedNamed.add(s.getDisplayName().getString());
               } else {
                  BigDecimal price = wm.getPrice(s);
                  if (price != null) {
                     BigDecimal earned = price.multiply(wm.getSellMultiplier()).multiply(BigDecimal.valueOf((long)s.getCount()));
                     total = total.add(earned);
                     typesSold++;
                     inv.setItem(i, ItemStack.EMPTY);
                  }
               }
            }
         }

         if (typesSold == 0 && skippedNamed.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.sell.nothing_to_sell"));
            return 0;
         } else {
            if (total.signum() > 0) {
               EconomyManager.getInstance().addBalance(player.getUUID(), total);
               LOGGER.info(
                  "Player {} sold inventory for {}{}", new Object[]{player.getName().getString(), WorthCommand.getCurrencySymbol(), WorthCommand.format(total)}
               );
            }

            String sym = WorthCommand.getCurrencySymbol();
            BigDecimal ft = total;
            int ft2 = typesSold;
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.sell.inventory_sold", ft2, sym + WorthCommand.format(ft)), false);
            if (!skippedNamed.isEmpty()) {
               source.sendSuccess(() -> MessageUtil.warning("commands.neoessentials.sell.named_items_skipped", skippedNamed.size()), false);
            }

            return 1;
         }
      }
   }

   private static int executeSellItem(CommandContext<CommandSourceStack> ctx, String itemId, int amount) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack template = WorthManager.resolveItem(itemId);
         if (template == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.worth.unknown_item", itemId));
            return 0;
         } else {
            int available = countInInventory(player, template);
            if (available == 0) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.sell.not_in_inventory", WorthManager.getItemId(template)));
               return 0;
            } else {
               int qty = amount > 0 ? Math.min(amount, available) : available;
               return doSell(source, player, template, qty);
            }
         }
      }
   }

   private static int doSell(CommandSourceStack source, ServerPlayer player, ItemStack template, int qty) {
      WorthManager wm = WorthManager.getInstance();
      if (!wm.isAllowSellNamedItems() && template.has(DataComponents.CUSTOM_NAME)) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.sell.cannot_sell_named"));
         return 0;
      } else {
         BigDecimal price = wm.getPrice(template);
         if (price == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.sell.no_price", WorthManager.getItemId(template)));
            return 0;
         } else {
            int available = countInInventory(player, template);
            int toSell = Math.min(qty, available);
            if (toSell <= 0) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.sell.not_enough_items"));
               return 0;
            } else {
               removeFromInventory(player, template, toSell);
               BigDecimal earned = price.multiply(wm.getSellMultiplier()).multiply(BigDecimal.valueOf((long)toSell));
               EconomyManager.getInstance().addBalance(player.getUUID(), earned);
               LOGGER.info(
                  "Player {} sold {}x {} for {}{}",
                  new Object[]{
                     player.getName().getString(), toSell, WorthManager.getItemId(template), WorthCommand.getCurrencySymbol(), WorthCommand.format(earned)
                  }
               );
               String sym = WorthCommand.getCurrencySymbol();
               source.sendSuccess(
                  () -> MessageUtil.success(
                        "commands.neoessentials.sell.item_sold",
                        toSell,
                        WorthManager.getItemId(template),
                        sym + WorthCommand.format(earned),
                        sym + WorthCommand.format(price)
                     ),
                  false
               );
               return 1;
            }
         }
      }
   }

   private static int executeSetWorth(CommandContext<CommandSourceStack> ctx, String itemId, double price) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ItemStack stack = resolveItemOrHand(source, itemId);
      if (stack == null) {
         return 0;
      } else {
         WorthManager.getInstance().setPrice(stack, price);
         String sym = WorthCommand.getCurrencySymbol();
         String id = WorthManager.getItemId(stack);
         source.sendSuccess(
            () -> MessageUtil.success("commands.neoessentials.sell.setworth_success", id, sym + WorthCommand.format(BigDecimal.valueOf(price))), true
         );
         return 1;
      }
   }

   private static int executeRemoveWorth(CommandContext<CommandSourceStack> ctx, String itemId) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ItemStack stack = resolveItemOrHand(source, itemId);
      if (stack == null) {
         return 0;
      } else {
         boolean removed = WorthManager.getInstance().removePrice(stack);
         if (removed) {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.sell.setworth_removed", WorthManager.getItemId(stack)), true);
            return 1;
         } else {
            source.sendFailure(MessageUtil.error("commands.neoessentials.worth.no_price", WorthManager.getItemId(stack)));
            return 0;
         }
      }
   }

   private static ItemStack resolveItemOrHand(CommandSourceStack source, String itemId) {
      if (itemId.equalsIgnoreCase("hand")) {
         ServerPlayer p = source.getPlayer();
         if (p == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return null;
         } else {
            ItemStack held = p.getMainHandItem();
            if (held.isEmpty()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.sell.no_item_in_hand"));
               return null;
            } else {
               return held;
            }
         }
      } else {
         ItemStack stack = WorthManager.resolveItem(itemId);
         if (stack == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.worth.unknown_item", itemId));
            return null;
         } else {
            return stack;
         }
      }
   }

   private static int countInInventory(ServerPlayer player, ItemStack template) {
      int count = 0;
      Inventory inv = player.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (!s.isEmpty() && s.getItem() == template.getItem()) {
            count += s.getCount();
         }
      }

      return count;
   }

   private static void removeFromInventory(ServerPlayer player, ItemStack template, int amount) {
      Inventory inv = player.getInventory();
      int remaining = amount;

      for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
         ItemStack s = inv.getItem(i);
         if (!s.isEmpty() && s.getItem() == template.getItem()) {
            int toRemove = Math.min(s.getCount(), remaining);
            s.shrink(toRemove);
            remaining -= toRemove;
            if (s.isEmpty()) {
               inv.setItem(i, ItemStack.EMPTY);
            }
         }
      }
   }
}
