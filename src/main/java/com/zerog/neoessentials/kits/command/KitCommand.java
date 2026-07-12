package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(KitCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isKitSystemEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kit").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kits.use");
               })).executes(KitCommand::listAvailableKits))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("kitname", StringArgumentType.word())
                        .suggests(KitCommand::suggestKits)
                        .executes(ctx -> executeGiveKit(ctx, StringArgumentType.getString(ctx, "kitname"), null)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                              .suggests(
                                 (ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder)
                              )
                              .requires(src -> {
                                 ServerPlayer p = src.getPlayer();
                                 return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kit.others");
                              }))
                           .executes(ctx -> executeGiveKit(ctx, StringArgumentType.getString(ctx, "kitname"), StringArgumentType.getString(ctx, "target")))
                     )
               )
         );
      }
   }

   private static CompletableFuture<Suggestions> suggestKits(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayer();

      for (Kit kit : KitManager.getInstance().getAllKits()) {
         if (kit.isEnabled()) {
            if (p != null) {
               String perm = kit.getPermission() != null && !kit.getPermission().isEmpty()
                  ? kit.getPermission()
                  : "neoessentials.kits." + kit.getName().toLowerCase();
               if (!PermissionAPI.hasPermission(p.getUUID(), perm)) {
                  continue;
               }
            }

            builder.suggest(kit.getName());
         }
      }

      return builder.buildFuture();
   }

   private static int listAvailableKits(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      List<Kit> available = (List<Kit>)(player != null
         ? KitManager.getInstance().getAvailableKits(player)
         : new ArrayList<>(KitManager.getInstance().getAllKits()));
      if (available.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.kits.list_empty"), false);
         return 1;
      } else {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.kits.list_header", available.size()), false);

         for (Kit kit : available) {
            long remaining = player != null ? KitManager.getInstance().getRemainingCooldownPublic(player.getUUID(), kit.getName()) : 0L;
            String cooldownStr = remaining > 0L
               ? MessageUtil.localize("commands.neoessentials.kits.list_cooldown", formatTime(remaining))
               : MessageUtil.localize("commands.neoessentials.kits.list_ready");
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.kits.list_entry", kit.getName(), kit.getItems().size(), cooldownStr), false);
         }

         return 1;
      }
   }

   private static int executeGiveKit(CommandContext<CommandSourceStack> ctx, String kitName, String targetName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer sender = source.getPlayer();
      if (sender == null && targetName == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.kits.console_needs_target"));
         return 0;
      } else {
         ServerPlayer recipient;
         if (targetName != null) {
            recipient = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (recipient == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
               return 0;
            }
         } else {
            recipient = sender;
         }

         if (sender != null) {
            Kit kit = KitManager.getInstance().getKit(kitName);
            if (kit == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.not_found", kitName));
               return 0;
            }

            String perm = kit.getPermission() != null && !kit.getPermission().isEmpty() ? kit.getPermission() : "neoessentials.kits." + kitName.toLowerCase();
            if (!PermissionAPI.hasPermission(sender.getUUID(), perm)) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.no_permission_kit", kitName));
               return 0;
            }
         }

         int cost = (int)ConfigManager.getKitCommandCost("kit");
         if (cost > 0 && sender != null && EconomyManager.getInstance().isEnabled()) {
            EconomyManager eco = EconomyManager.getInstance();
            if (eco.getBalance(sender.getUUID()).doubleValue() < (double)cost) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.not_enough_money", cost));
               return 0;
            }

            if (!eco.subtractBalance(sender.getUUID(), BigDecimal.valueOf((long)cost))) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.charge_failed"));
               return 0;
            }
         }

         KitManager.KitUsageResult canUse = KitManager.getInstance().canUseKit(recipient, kitName);
         if (!canUse.isAllowed()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.kits.cannot_use", canUse.getMessage()));
            return 0;
         } else {
            KitManager.KitUsageResult giveResult = KitManager.getInstance().giveKit(recipient, kitName);
            if (!giveResult.isAllowed()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.give_failed", giveResult.getMessage()));
               return 0;
            } else {
               if (targetName != null) {
                  String rName = recipient.getName().getString();
                  source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kits.gave_to", kitName, rName), true);
                  recipient.sendSystemMessage(
                     MessageUtil.info("commands.neoessentials.kits.received_from", kitName, sender != null ? sender.getName().getString() : "Console")
                  );
               } else {
                  Kit kitx = KitManager.getInstance().getKit(kitName);
                  String display = kitx != null ? kitx.getDisplayName() : kitName;
                  source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kits.given", display), false);
               }

               LOGGER.info(
                  "{} gave kit '{}' to {}", new Object[]{sender != null ? sender.getName().getString() : "Console", kitName, recipient.getName().getString()}
               );
               return 1;
            }
         }
      }
   }

   private static String formatTime(long millis) {
      long s = millis / 1000L;
      long m = s / 60L;
      long h = m / 60L;
      if (h > 0L) {
         return h + "h " + m % 60L + "m";
      } else {
         return m > 0L ? m + "m " + s % 60L + "s" : s + "s";
      }
   }
}
