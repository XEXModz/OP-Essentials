package com.zerog.neoessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.EconomyAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.IgnoreManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.EconomyPlayerUtil;
import com.zerog.neoessentials.economy.events.EconomyTransactionEvent;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.managers.PayToggleManager;
import com.zerog.neoessentials.economy.managers.TransactionHistoryManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

public class PayCommand {
   private static final Map<UUID, Long> payCooldowns = new ConcurrentHashMap<>();

   private static long getPayCooldownMs() {
      return (long)ConfigManager.getPayCooldownSeconds() * 1000L;
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pay")
               .requires(
                  src -> src.hasPermission(2) || src.getPlayer() != null && PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.economy.pay")
               ))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .suggests(
                     (ctx, builder) -> SharedSuggestionProvider.suggest(
                           ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                           builder
                        )
                  )
                  .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(ctx -> execute(ctx)))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("p")
               .requires(
                  src -> src.hasPermission(2) || src.getPlayer() != null && PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.economy.pay")
               ))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .suggests(
                     (ctx, builder) -> SharedSuggestionProvider.suggest(
                           ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                           builder
                        )
                  )
                  .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(ctx -> execute(ctx)))
            )
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), "neoessentials.economy.pay");
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         ServerPlayer sender = permResult.getPlayer();
         long now = System.currentTimeMillis();
         long cooldownMs = getPayCooldownMs();
         Long lastPay = payCooldowns.putIfAbsent(sender.getUUID(), now);
         if (lastPay != null) {
            long timeSince = now - lastPay;
            if (timeSince < cooldownMs) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.cooldown"));
               return 0;
            }

            if (!payCooldowns.replace(sender.getUUID(), lastPay, now)) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.cooldown"));
               return 0;
            }
         }

         if (!EconomyManager.getInstance().isEnabled()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.economy.disabled"));
            return 0;
         } else {
            String targetName = StringArgumentType.getString(ctx, "player");
            double amountRaw = DoubleArgumentType.getDouble(ctx, "amount");
            InputValidator.ValidationResult nameValidation = InputValidator.validatePlayerName(targetName);
            if (!nameValidation.isValid()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(nameValidation.getErrorMessage()));
               return 0;
            } else {
               InputValidator.ValidationResult amountValidation = InputValidator.validateEconomyAmount(amountRaw);
               if (!amountValidation.isValid()) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(amountValidation.getErrorMessage()));
                  return 0;
               } else {
                  MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                  UUID recipientUUID = null;
                  String resolvedRecipientName = targetName;
                  ServerPlayer onlineRecipient = server.getPlayerList().getPlayerByName(targetName);
                  if (onlineRecipient != null) {
                     recipientUUID = onlineRecipient.getUUID();
                     resolvedRecipientName = onlineRecipient.getName().getString();
                  } else {
                     boolean canPayOffline = PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.economy.pay.offline");
                     if (!canPayOffline) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.offline_not_allowed"));
                        return 0;
                     }

                     Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, targetName);
                     if (uuidOpt.isEmpty()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.player_not_found", targetName));
                        return 0;
                     }

                     recipientUUID = uuidOpt.get();
                  }

                  String finalRecipientName = resolvedRecipientName;
                  if (recipientUUID.equals(sender.getUUID())) {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.cannot_pay_self"));
                     return 0;
                  } else if (!PayToggleManager.getInstance().getPayToggle(recipientUUID)) {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.toggled_off"));
                     return 0;
                  } else if (onlineRecipient != null && IgnoreManager.isIgnoring(onlineRecipient, sender)) {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.toggled_off"));
                     return 0;
                  } else {
                     BigDecimal amount = amountValidation.getValue(BigDecimal.class);
                     double taxPercent = ConfigManager.getEconomyTaxPercentage();
                     BigDecimal fee = amount.multiply(BigDecimal.valueOf(taxPercent / 100.0));
                     BigDecimal netAmount = amount.subtract(fee);
                     boolean success = EconomyAPI.payPlayer(sender.getUUID(), recipientUUID, amount);
                     if (!success) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.pay.insufficient_funds"));
                        return 0;
                     } else {
                        String currency = EconomyManager.getInstance().getCurrencySymbol();
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.pay.success_fee", finalRecipientName, amount, fee, netAmount, currency), false
                           );
                        if (onlineRecipient != null) {
                           onlineRecipient.sendSystemMessage(
                              MessageUtil.info("commands.neoessentials.pay.received_fee", sender.getGameProfile().getName(), netAmount, fee, currency)
                           );
                        }

                        TransactionHistoryManager.getInstance()
                           .addTransaction(sender.getUUID(), MessageUtil.localize("commands.neoessentials.transaction.paid", finalRecipientName, amount, fee));
                        TransactionHistoryManager.getInstance()
                           .addTransaction(
                              recipientUUID,
                              MessageUtil.localize("commands.neoessentials.transaction.received", netAmount, sender.getGameProfile().getName(), fee)
                           );
                        NeoForge.EVENT_BUS
                           .post(
                              new EconomyTransactionEvent(
                                 EconomyTransactionEvent.Type.PAY,
                                 sender.getUUID(),
                                 recipientUUID,
                                 netAmount,
                                 MessageUtil.localize("commands.neoessentials.transaction.pay_description", fee)
                              )
                           );
                        BaltopCommand.invalidateCache();
                        return 1;
                     }
                  }
               }
            }
         }
      }
   }
}
