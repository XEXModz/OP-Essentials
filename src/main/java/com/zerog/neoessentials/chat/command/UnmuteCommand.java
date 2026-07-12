package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

public class UnmuteCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("unmute").then(Commands.argument("target", EntityArgument.player()).executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();

         ServerPlayer targetPlayer;
         try {
            targetPlayer = EntityArgument.getPlayer(ctx, "target");
         } catch (CommandSyntaxException var8) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.unmute.player_not_found"));
            return 0;
         }

         String targetName = targetPlayer.getName().getString();
         ServerPlayer sender = source.getPlayer();
         if (sender == null) {
            source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
            return 0;
         } else if (!ConfigManager.isChatEnabled()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.unmute.disabled"));
            return 0;
         } else if (!ConfigManager.getInstance().isCommandEnabled("unmute")) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.unmute.disabled"));
            return 0;
         } else {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager != null && !chatManager.isUnmuteEnabled()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.unmute.disabled"));
               return 0;
            } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.mute")) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
               return 0;
            } else if (!MuteManager.getMutedPlayers().contains(targetName.toLowerCase())) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.unmute.not_muted", targetName));
               return 0;
            } else {
               MuteManager.unmute(sender, targetName);

               try {
                  ChatIntegrationManager.broadcastMuteEvent(targetPlayer, null, false);
               } catch (Exception var7) {
               }

               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.unmute.success", targetName), false);
               return 1;
            }
         }
      })));
   }
}
