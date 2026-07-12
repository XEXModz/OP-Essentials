package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.MuteManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class MuteCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerMuteCommand(dispatcher, "mute");
      registerMuteCommand(dispatcher, "silence");
   }

   private static void registerMuteCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal(commandName)
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", EntityArgument.player()).executes(ctx -> executeMute(ctx, "")))
                  .then(
                     Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> executeMute(ctx, StringArgumentType.getString(ctx, "reason")))
                  )
            )
      );
   }

   private static int executeMute(CommandContext<CommandSourceStack> ctx, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      if (!ConfigManager.isChatEnabled()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mute.disabled"));
         return 0;
      } else if (!ConfigManager.getInstance().isCommandEnabled("mute")) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mute.disabled"));
         return 0;
      } else {
         ServerPlayer targetPlayer;
         try {
            targetPlayer = EntityArgument.getPlayer(ctx, "target");
         } catch (CommandSyntaxException var9) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.mute.player_not_found"));
            return 0;
         }

         String targetName = targetPlayer.getName().getString();
         ServerPlayer sender = source.getPlayer();
         if (sender == null) {
            source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
            return 0;
         } else {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager != null && !chatManager.isMuteEnabled()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mute.disabled"));
               return 0;
            } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.mute")) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
               return 0;
            } else if (sender.getName().getString().equalsIgnoreCase(targetName)) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mute.self"));
               return 0;
            } else if (PermissionAPI.hasPermission(targetPlayer.getUUID(), "neoessentials.chat.mute.exempt")) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mute.exempt", targetName));
               return 0;
            } else {
               MuteManager.mute(sender, targetName);

               try {
                  ChatIntegrationManager.broadcastMuteEvent(targetPlayer, reason.isEmpty() ? "No reason given" : reason, true);
               } catch (Exception var8) {
               }

               if (reason.isEmpty()) {
                  source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mute.success", targetName), false);
               } else {
                  source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mute.success_with_reason", targetName, reason), false);
               }

               return 1;
            }
         }
      }
   }
}
