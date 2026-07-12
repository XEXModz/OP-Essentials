package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitResetCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(KitResetCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isKitSystemEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kitreset").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kitreset");
               }))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("kitname", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(KitManager.getInstance().getKitNames(), builder))
                        .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "kitname"), null)))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                              .suggests(
                                 (ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder)
                              )
                              .requires(src -> {
                                 ServerPlayer p = src.getPlayer();
                                 return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kitreset.others");
                              }))
                           .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "kitname"), StringArgumentType.getString(ctx, "target")))
                     )
               )
         );
      }
   }

   private static int executeReset(CommandContext<CommandSourceStack> ctx, String kitName, String targetName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer sender = source.getPlayer();
      if (KitManager.getInstance().getKit(kitName) == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.kits.not_found", kitName));
         return 0;
      } else {
         ServerPlayer target;
         if (targetName != null) {
            target = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
               return 0;
            }
         } else {
            if (sender == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.kits.console_needs_target"));
               return 0;
            }

            target = sender;
         }

         KitManager.getInstance().resetCooldown(target.getUUID(), kitName);
         String tName = target.getName().getString();
         if (sender != null && target.getUUID().equals(sender.getUUID())) {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kits.reset_self", kitName), false);
         } else {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kits.reset_other", kitName, tName), true);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.kits.reset_notify", kitName));
         }

         LOGGER.info("{} reset kit cooldown '{}' for {}", new Object[]{sender != null ? sender.getName().getString() : "Console", kitName, tName});
         return 1;
      }
   }
}
