package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.MsgToggleManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class MsgToggleCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerMsgToggleCommand(dispatcher, "msgtoggle");
      registerMsgToggleCommand(dispatcher, "togglemsg");
      registerMsgToggleCommand(dispatcher, "mt");
   }

   private static void registerMsgToggleCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal(commandName).executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();
         ServerPlayer sender = source.getPlayer();
         ChatManager chatManager = ChatAPI.getChatManager();
         if (chatManager != null && !chatManager.isMsgToggleEnabled()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.msgtoggle.disabled"));
            return 0;
         } else if (!sender.hasPermissions(4) && !PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.msgtoggle")) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
            return 0;
         } else {
            boolean newState = MsgToggleManager.toggleMsg(sender);
            if (newState) {
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.msgtoggle.enabled"), false);
            } else {
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.msgtoggle.disabled_status"), false);
            }

            return 1;
         }
      }));
   }
}
