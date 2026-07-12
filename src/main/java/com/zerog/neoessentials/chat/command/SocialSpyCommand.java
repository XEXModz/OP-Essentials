package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.SocialSpyManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class SocialSpyCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerSocialSpyCommand(dispatcher, "socialspy");
      registerSocialSpyCommand(dispatcher, "ss");
      registerSocialSpyCommand(dispatcher, "spy");
   }

   private static void registerSocialSpyCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal(commandName).executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();
         ServerPlayer sender = source.getPlayer();
         if (sender == null) {
            source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
            return 0;
         } else {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (!ConfigManager.isChatEnabled()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.socialspy.disabled"));
               return 0;
            } else if (!ConfigManager.getInstance().isCommandEnabled("socialspy")) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.socialspy.disabled"));
               return 0;
            } else if (chatManager != null && !chatManager.isSocialSpyEnabled()) {
               sender.sendSystemMessage(MessageUtil.error("commands.neoessentials.socialspy.disabled"));
               return 0;
            } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.socialspy")) {
               MessageUtil.debugKey("commands.neoessentials.socialspy.no_permission");
               sender.sendSystemMessage(MessageUtil.error("commands.neoessentials.socialspy.no_permission"));
               return 0;
            } else {
               SocialSpyManager.toggleSocialSpy(sender);
               boolean isNowEnabled = SocialSpyManager.hasSocialSpy(sender);
               if (isNowEnabled) {
                  sender.sendSystemMessage(MessageUtil.success("commands.neoessentials.socialspy.enabled"));
               } else {
                  sender.sendSystemMessage(MessageUtil.success("commands.neoessentials.socialspy.disabled_status"));
               }

               return 1;
            }
         }
      }));
   }
}
