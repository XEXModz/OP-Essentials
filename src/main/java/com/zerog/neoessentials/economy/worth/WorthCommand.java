package com.zerog.neoessentials.economy.worth;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class WorthCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isEconomyEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("worth").requires(src -> {
                     ServerPlayer p = src.getPlayer();
                     return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.worth");
                  })).executes(ctx -> executeWorthHand(ctx, 0)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("hand").executes(ctx -> executeWorthHand(ctx, 0)))
                        .then(
                           Commands.argument("amount", IntegerArgumentType.integer(1))
                              .executes(ctx -> executeWorthHand(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                        )
                  ))
               .then(Commands.argument("item", StringArgumentType.greedyString()).executes(ctx -> {
                  String raw = StringArgumentType.getString(ctx, "item").trim();
                  String[] parts = raw.split("\\s+");
                  if (parts.length >= 2) {
                     try {
                        int amt = Integer.parseInt(parts[parts.length - 1]);
                        String itemPart = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
                        return executeWorthItem(ctx, itemPart, amt);
                     } catch (NumberFormatException var5) {
                     }
                  }

                  return executeWorthItem(ctx, raw, 1);
               }))
         );
      }
   }

   private static int executeWorthHand(CommandContext<CommandSourceStack> ctx, int amount) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.worth.no_item_in_hand"));
            return 0;
         } else {
            int qty = amount > 0 ? amount : held.getCount();
            return showWorth(source, held, qty);
         }
      }
   }

   private static int executeWorthItem(CommandContext<CommandSourceStack> ctx, String itemId, int amount) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String normalized = itemId.trim().toLowerCase().replace(" ", "_");
      ItemStack stack = WorthManager.resolveItem(normalized);
      if (stack != null && !stack.isEmpty()) {
         int qty = Math.max(1, amount);
         return showWorth(source, stack, qty);
      } else {
         source.sendFailure(MessageUtil.error("commands.neoessentials.worth.unknown_item", itemId));
         return 0;
      }
   }

   private static int showWorth(CommandSourceStack source, ItemStack stack, int amount) {
      WorthManager wm = WorthManager.getInstance();
      BigDecimal price = wm.getPrice(stack);
      if (price == null) {
         String itemId = WorthManager.getItemId(stack);
         source.sendFailure(MessageUtil.error("commands.neoessentials.worth.no_price", itemId));
         return 0;
      } else {
         BigDecimal total = price.multiply(BigDecimal.valueOf((long)amount));
         String itemId = WorthManager.getItemId(stack);
         String symbol = getCurrencySymbol();
         source.sendSuccess(
            () -> MessageUtil.info("commands.neoessentials.worth.result", amount, itemId, symbol + format(total), symbol + format(price)), false
         );
         return 1;
      }
   }

   static String getCurrencySymbol() {
      try {
         JsonObject cfg = ConfigManager.getInstance().getConfig("config.json");
         if (cfg.has("economy")) {
            JsonObject eco = cfg.getAsJsonObject("economy");
            if (eco.has("currencySymbol")) {
               return eco.get("currencySymbol").getAsString();
            }
         }
      } catch (Exception var2) {
      }

      return "$";
   }

   static String format(BigDecimal value) {
      return value.stripTrailingZeros().toPlainString();
   }
}
