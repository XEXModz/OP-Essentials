package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.MuteManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class MuteListCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("mutelist").executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();
         ServerPlayer sender = source.getPlayer();
         if (sender == null) {
            source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
            return 0;
         } else {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager != null && !chatManager.isMuteListEnabled()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mutelist.disabled"));
               return 0;
            } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.mute")) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.no_permission"));
               return 0;
            } else {
               List<String> muted = new ArrayList<>(MuteManager.getMutedPlayers());
               if (muted.isEmpty()) {
                  source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.mutelist.empty"), false);
               } else {
                  String mutedList = String.join(", ", muted);
                  source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.mutelist.list", mutedList), false);
               }

               return 1;
            }
         }
      }));
   }
}
