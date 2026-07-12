package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.IgnoreManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class UnignoreCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerUnignoreCommand(dispatcher, "unignore");
      registerUnignoreCommand(dispatcher, "unblock");
   }

   private static void registerUnignoreCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal(commandName).then(Commands.argument("target", EntityArgument.player()).executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();

         ServerPlayer targetPlayer;
         try {
            targetPlayer = EntityArgument.getPlayer(ctx, "target");
         } catch (CommandSyntaxException var6) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.unignore.player_not_found"));
            return 0;
         }

         String targetName = targetPlayer.getName().getString();
         ServerPlayer sender = source.getPlayer();
         if (sender == null) {
            source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
            return 0;
         } else {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager != null && !chatManager.isUnignoreEnabled()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.unignore.disabled"));
               return 0;
            } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.ignore")) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.unignore.no_permission"));
               return 0;
            } else if (!IgnoreManager.isIgnoring(sender, targetName)) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.unignore.not_ignored", targetName));
               return 0;
            } else {
               IgnoreManager.unignore(sender, targetName);
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.unignore.success", targetName), false);
               return 1;
            }
         }
      })));
   }
}
