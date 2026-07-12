package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelKitCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(DelKitCommand.class);
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_KITS = (context, builder) -> suggestKits(context, builder);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isKitSystemEnabled()) {
         registerDelKitCommand(dispatcher, "delkit");
         registerDelKitCommand(dispatcher, "deletekit");
         registerDelKitCommand(dispatcher, "removekit");
         registerDelKitCommand(dispatcher, "rkit");
      }
   }

   private static void registerDelKitCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer player
                        ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.delete")
                        : source.hasPermission(4)
               ))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("kitname", StringArgumentType.word()).suggests(SUGGEST_KITS).executes(DelKitCommand::deleteKit))
                  .then(Commands.literal("confirm").executes(DelKitCommand::confirmDeleteKit))
            )
      );
   }

   private static CompletableFuture<Suggestions> suggestKits(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      try {
         KitManager kitManager = KitManager.getInstance();
         Set<String> kitNames = kitManager.getAllKitNames();
         return SharedSuggestionProvider.suggest(kitNames, builder);
      } catch (Exception var4) {
         LOGGER.warn("Error suggesting kits for delkit command: {}", var4.getMessage());
         return builder.buildFuture();
      }
   }

   private static int deleteKit(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      String kitName = StringArgumentType.getString(context, "kitname");

      try {
         KitManager kitManager = KitManager.getInstance();
         Kit kit = kitManager.getKit(kitName);
         if (kit == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.not_found", kitName));
            return 0;
         } else {
            source.sendSuccess(
               () -> MessageUtil.warning("commands.neoessentials.delkit.confirm_prompt", kitName, kit.getDisplayName(), kit.getItems().size()), false
            );
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.delkit.confirm_instructions", kitName), false);
            return 1;
         }
      } catch (Exception var5) {
         LOGGER.error("Error processing delkit command for kit '{}': {}", new Object[]{kitName, var5.getMessage(), var5});
         source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.error"));
         return 0;
      }
   }

   private static int confirmDeleteKit(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      String kitName = StringArgumentType.getString(context, "kitname");

      try {
         if (source.getEntity() instanceof ServerPlayer player) {
            int cost = (int)ConfigManager.getKitCommandCost("delkit");
            if (cost > 0 && EconomyManager.getInstance().isEnabled()) {
               EconomyManager eco = EconomyManager.getInstance();
               BigDecimal bal = eco.getBalance(player.getUUID());
               if (bal.doubleValue() < (double)cost) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.not_enough_money", cost));
                  return 0;
               }

               if (!eco.subtractBalance(player.getUUID(), BigDecimal.valueOf((long)cost))) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.charge_failed"));
                  return 0;
               }
            }
         }

         KitManager kitManager = KitManager.getInstance();
         Kit kit = kitManager.getKit(kitName);
         if (kit == null) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.not_found", kitName));
            return 0;
         } else {
            boolean success = kitManager.deleteKit(kitName);
            if (success) {
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.delkit.deleted", kitName), false);
               String playerName = "Console";
               if (source.getEntity() instanceof ServerPlayer playerx) {
                  playerName = playerx.getName().getString();
               }

               LOGGER.info("Kit '{}' deleted by {}", kitName, playerName);
               return 1;
            } else {
               source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.failed"));
               return 0;
            }
         }
      } catch (Exception var10) {
         LOGGER.error("Error confirming delkit for kit '{}': {}", new Object[]{kitName, var10.getMessage(), var10});
         source.sendFailure(MessageUtil.error("commands.neoessentials.delkit.error"));
         return 0;
      }
   }
}
