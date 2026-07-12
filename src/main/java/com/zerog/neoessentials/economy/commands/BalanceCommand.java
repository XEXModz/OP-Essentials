package com.zerog.neoessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.economy.EconomyPlayerUtil;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public class BalanceCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("balance").executes(ctx -> execute(ctx)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                     .requires(
                        src -> src.hasPermission(2)
                              || src.getPlayer() != null && PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.economy.balance.others")
                     ))
                  .suggests(
                     (ctx, builder) -> SharedSuggestionProvider.suggest(
                           ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                           builder
                        )
                  )
                  .executes(ctx -> executeOther(ctx))
            )
      );
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("bal").executes(ctx -> execute(ctx)));
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("money").executes(ctx -> execute(ctx)));
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) {
      if (!EconomyManager.getInstance().isEnabled()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.eco.disabled"));
         return 0;
      } else {
         ServerPlayer player;
         try {
            player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         } catch (CommandSyntaxException var5) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.balance.player_not_found"));
            return 0;
         }

         if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.economy.balance")) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
            return 0;
         } else {
            UUID uuid = player.getUUID();
            BigDecimal balance = EconomyManager.getInstance().getBalance(uuid);
            String currency = EconomyManager.getInstance().getCurrencySymbol();
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.balance", balance, currency), false);
            return 1;
         }
      }
   }

   private static int executeOther(CommandContext<CommandSourceStack> ctx) {
      if (!EconomyManager.getInstance().isEnabled()) {
         return 0;
      } else {
         if (((CommandSourceStack)ctx.getSource()).getPlayer() != null) {
            ServerPlayer sender = ((CommandSourceStack)ctx.getSource()).getPlayer();
            if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.economy.balance.others")) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
               return 0;
            }
         }

         String playerName = StringArgumentType.getString(ctx, "player");
         Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(((CommandSourceStack)ctx.getSource()).getServer(), playerName);
         if (uuidOpt.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.balance.player_not_found"));
            return 0;
         } else {
            BigDecimal balance = EconomyManager.getInstance().getBalance(uuidOpt.get());
            String currency = EconomyManager.getInstance().getCurrencySymbol();
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.balance", balance, currency), false);
            return 1;
         }
      }
   }
}
