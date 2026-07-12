package com.zerog.neoessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.EconomyPlayerUtil;
import com.zerog.neoessentials.economy.EconomyTransactionLogger;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.managers.TransactionHistoryManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class EcoCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerEcoCommand(dispatcher, "eco");
      registerEcoCommand(dispatcher, "economy");
   }

   private static void registerEcoCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              commandName
                           )
                           .requires(
                              src -> src.hasPermission(2)
                                    || PermissionAPI.hasPermission(src.getPlayer() != null ? src.getPlayer().getUUID() : null, "neoessentials.economy.eco")
                           ))
                        .then(
                           Commands.literal("give")
                              .then(
                                 Commands.argument("player", StringArgumentType.word())
                                    .suggests(
                                       (ctx, builder) -> SharedSuggestionProvider.suggest(
                                             ((CommandSourceStack)ctx.getSource())
                                                .getServer()
                                                .getPlayerList()
                                                .getPlayers()
                                                .stream()
                                                .map(p -> p.getGameProfile().getName()),
                                             builder
                                          )
                                    )
                                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(ctx -> ecoAdminAction(ctx, "give")))
                              )
                        ))
                     .then(
                        Commands.literal("take")
                           .then(
                              Commands.argument("player", StringArgumentType.word())
                                 .suggests(
                                    (ctx, builder) -> SharedSuggestionProvider.suggest(
                                          ((CommandSourceStack)ctx.getSource())
                                             .getServer()
                                             .getPlayerList()
                                             .getPlayers()
                                             .stream()
                                             .map(p -> p.getGameProfile().getName()),
                                          builder
                                       )
                                 )
                                 .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(ctx -> ecoAdminAction(ctx, "take")))
                           )
                     ))
                  .then(
                     Commands.literal("set")
                        .then(
                           Commands.argument("player", StringArgumentType.word())
                              .suggests(
                                 (ctx, builder) -> SharedSuggestionProvider.suggest(
                                       ((CommandSourceStack)ctx.getSource())
                                          .getServer()
                                          .getPlayerList()
                                          .getPlayers()
                                          .stream()
                                          .map(p -> p.getGameProfile().getName()),
                                       builder
                                    )
                              )
                              .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(ctx -> ecoAdminAction(ctx, "set")))
                        )
                  ))
               .then(
                  Commands.literal("reset")
                     .then(
                        Commands.argument("player", StringArgumentType.word())
                           .suggests(
                              (ctx, builder) -> SharedSuggestionProvider.suggest(
                                    ((CommandSourceStack)ctx.getSource())
                                       .getServer()
                                       .getPlayerList()
                                       .getPlayers()
                                       .stream()
                                       .map(p -> p.getGameProfile().getName()),
                                    builder
                                 )
                           )
                           .executes(ctx -> ecoAdminAction(ctx, "reset"))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("history")
                     .executes(ctx -> showHistory(ctx, ((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word()).requires(src -> src.hasPermission(2)))
                        .suggests(
                           (ctx, builder) -> SharedSuggestionProvider.suggest(
                                 ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                                 builder
                              )
                        )
                        .executes(ctx -> showOtherHistory(ctx))
                  )
            )
      );
   }

   private static int ecoAdminAction(CommandContext<CommandSourceStack> ctx, String action) {
      if (!EconomyManager.getInstance().isEnabled()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.economy.disabled"));
         return 0;
      } else {
         PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
            (CommandSourceStack)ctx.getSource(), "neoessentials.economy.eco"
         );
         if (!permResult.hasPermission()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
         } else {
            String playerName = StringArgumentType.getString(ctx, "player");
            InputValidator.ValidationResult nameValidation = InputValidator.validatePlayerName(playerName);
            if (!nameValidation.isValid()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(nameValidation.getErrorMessage()));
               return 0;
            } else {
               String validPlayerName = nameValidation.getValue(String.class);
               MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
               Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, validPlayerName);
               if (uuidOpt.isEmpty()) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.eco.player_not_found"));
                  return 0;
               } else {
                  UUID uuid = uuidOpt.get();
                  if ("reset".equals(action)) {
                     BigDecimal startBal = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
                     EconomyManager.getInstance().setBalance(uuid, startBal);
                     String adminName = ((CommandSourceStack)ctx.getSource()).getTextName();
                     ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(() -> MessageUtil.success("commands.neoessentials.eco.reset", validPlayerName, startBal), false);
                     EconomyTransactionLogger.log("ADMIN_RESET", adminName, validPlayerName, startBal.toPlainString(), "Reset to starting balance");
                     TransactionHistoryManager.getInstance()
                        .addTransaction(uuid, MessageUtil.localize("commands.neoessentials.transaction.admin_reset", startBal));
                     BaltopCommand.invalidateCache();
                     ServerPlayer onlineTarget = server.getPlayerList().getPlayer(uuid);
                     if (onlineTarget != null) {
                        onlineTarget.sendSystemMessage(
                           MessageUtil.info("commands.neoessentials.eco.reset_notify", startBal, EconomyManager.getInstance().getCurrencySymbol())
                        );
                     }

                     return 1;
                  } else {
                     double amountRaw = DoubleArgumentType.getDouble(ctx, "amount");
                     boolean isPercent = false;

                     try {
                        String rawAmountStr = (String)ctx.getArgument("amount", String.class);
                        isPercent = rawAmountStr != null && rawAmountStr.endsWith("%");
                     } catch (Exception var19) {
                     }

                     BigDecimal amount;
                     if ("set".equals(action)) {
                        if (amountRaw < 0.0 || Double.isNaN(amountRaw) || Double.isInfinite(amountRaw)) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Invalid amount: must be non-negative"));
                           return 0;
                        }

                        amount = BigDecimal.valueOf(amountRaw);
                     } else {
                        InputValidator.ValidationResult amountValidation = InputValidator.validateEconomyAmount(amountRaw);
                        if (!amountValidation.isValid()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(amountValidation.getErrorMessage()));
                           return 0;
                        }

                        amount = amountValidation.getValue(BigDecimal.class);
                     }

                     if (isPercent) {
                        BigDecimal current = EconomyManager.getInstance().getBalance(uuid);
                        amount = current.multiply(amount).scaleByPowerOfTen(-2);
                     }

                     EconomyManager manager = EconomyManager.getInstance();
                     String adminName = ((CommandSourceStack)ctx.getSource()).getTextName();
                     BigDecimal finalAmount = amount;
                     switch (action) {
                        case "give":
                           manager.addBalance(uuid, finalAmount);
                           ((CommandSourceStack)ctx.getSource())
                              .sendSuccess(() -> MessageUtil.success("commands.neoessentials.eco.give", finalAmount, validPlayerName), false);
                           EconomyTransactionLogger.log("ADMIN_GIVE", adminName, validPlayerName, finalAmount.toPlainString(), "Admin give");
                           TransactionHistoryManager.getInstance()
                              .addTransaction(uuid, MessageUtil.localize("commands.neoessentials.transaction.admin_gave", finalAmount));
                           ServerPlayer t1 = server.getPlayerList().getPlayer(uuid);
                           if (t1 != null) {
                              t1.sendSystemMessage(MessageUtil.info("commands.neoessentials.eco.received_give", finalAmount, manager.getCurrencySymbol()));
                           }
                           break;
                        case "take":
                           manager.subtractBalance(uuid, finalAmount);
                           ((CommandSourceStack)ctx.getSource())
                              .sendSuccess(() -> MessageUtil.success("commands.neoessentials.eco.take", finalAmount, validPlayerName), false);
                           EconomyTransactionLogger.log("ADMIN_TAKE", adminName, validPlayerName, finalAmount.toPlainString(), "Admin take");
                           TransactionHistoryManager.getInstance()
                              .addTransaction(uuid, MessageUtil.localize("commands.neoessentials.transaction.admin_took", finalAmount));
                           break;
                        case "set":
                           manager.setBalance(uuid, finalAmount);
                           ((CommandSourceStack)ctx.getSource())
                              .sendSuccess(() -> MessageUtil.success("commands.neoessentials.eco.set", validPlayerName, finalAmount), false);
                           EconomyTransactionLogger.log("ADMIN_SET", adminName, validPlayerName, finalAmount.toPlainString(), "Admin set");
                           TransactionHistoryManager.getInstance()
                              .addTransaction(uuid, MessageUtil.localize("commands.neoessentials.transaction.admin_set", finalAmount));
                           ServerPlayer t2 = server.getPlayerList().getPlayer(uuid);
                           if (t2 != null) {
                              t2.sendSystemMessage(MessageUtil.info("commands.neoessentials.eco.set_notify", finalAmount, manager.getCurrencySymbol()));
                           }
                     }

                     BaltopCommand.invalidateCache();
                     return 1;
                  }
               }
            }
         }
      }
   }

   private static int showHistory(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
      UUID uuid = player.getUUID();
      List<String> history = TransactionHistoryManager.getInstance().getHistory(uuid);
      if (history.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.empty"), false);
      } else {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.header"), false);

         for (String entry : history) {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.entry", entry), false);
         }
      }

      return 1;
   }

   private static int showOtherHistory(CommandContext<CommandSourceStack> ctx) {
      String playerName = StringArgumentType.getString(ctx, "player");
      MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
      Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
      if (uuidOpt.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.eco.player_not_found"));
         return 0;
      } else {
         List<String> history = TransactionHistoryManager.getInstance().getHistory(uuidOpt.get());
         if (history.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.empty"), false);
         } else {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.header"), false);

            for (String entry : history) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.history.entry", entry), false);
            }
         }

         return 1;
      }
   }
}
