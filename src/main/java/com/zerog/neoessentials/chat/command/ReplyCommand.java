package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.PlaceholderAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.IgnoreManager;
import com.zerog.neoessentials.chat.LastMessageManager;
import com.zerog.neoessentials.chat.MsgToggleManager;
import com.zerog.neoessentials.chat.MuteManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.util.ChatDebugUtil;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplyCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(ReplyCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ChatDebugUtil.debug("ReplyCommand - Registering /reply command");
      registerCommand(dispatcher, "reply");
      registerCommand(dispatcher, "r");
   }

   private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal(commandName)
            .then(
               Commands.argument("message", StringArgumentType.greedyString())
                  .executes(
                     ctx -> {
                        ChatDebugUtil.debug("ReplyCommand - Command executed!");
                        CommandSourceStack source = (CommandSourceStack)ctx.getSource();
                        String message = StringArgumentType.getString(ctx, "message");
                        ServerPlayer sender = source.getPlayer();
                        if (sender == null) {
                           source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
                           return 0;
                        } else {
                           ChatDebugUtil.debug("ReplyCommand - Looking for last messager for %s", sender.getName().getString());
                           ServerPlayer target = LastMessageManager.getLastMessager(sender);
                           ChatDebugUtil.debug("ReplyCommand - Found target: %s", target != null ? target.getName().getString() : "null");
                           if (target == null) {
                              source.sendFailure(MessageUtil.error("commands.neoessentials.reply.no_target"));
                              return 0;
                           } else {
                              LOGGER.debug("Player {} replying to {}", sender.getName().getString(), target.getName().getString());
                              if (!target.getServer().getPlayerList().getPlayers().contains(target)) {
                                 source.sendFailure(MessageUtil.error("commands.neoessentials.reply.target_offline"));
                                 return 0;
                              } else if (!ConfigManager.isChatEnabled()) {
                                 source.sendFailure(MessageUtil.error("commands.neoessentials.reply.disabled"));
                                 return 0;
                              } else if (!ConfigManager.getInstance().isCommandEnabled("reply")) {
                                 source.sendFailure(MessageUtil.error("commands.neoessentials.reply.disabled"));
                                 return 0;
                              } else {
                                 ChatManager chatManager = ChatAPI.getChatManager();
                                 if (chatManager != null && !chatManager.isReplyEnabled()) {
                                    source.sendFailure(MessageUtil.error("commands.neoessentials.reply.disabled"));
                                    return 0;
                                 } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.reply")) {
                                    source.sendFailure(MessageUtil.error("commands.neoessentials.reply.no_permission"));
                                    return 0;
                                 } else if (MuteManager.isMuted(sender)) {
                                    source.sendFailure(MessageUtil.error("commands.neoessentials.reply.sender_muted"));
                                    return 0;
                                 } else if (IgnoreManager.isIgnoring(target, sender)) {
                                    source.sendFailure(MessageUtil.error("commands.neoessentials.reply.target_ignoring"));
                                    return 0;
                                 } else if (MsgToggleManager.isMsgToggled(target)
                                    && !sender.hasPermissions(4)
                                    && !PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.msgtoggle.bypass")) {
                                    source.sendFailure(MessageUtil.error("commands.neoessentials.reply.target_toggled_off", target.getName().getString()));
                                    return 0;
                                 } else {
                                    String toTemplate = MessageUtil.localize("commands.neoessentials.reply.format.to");
                                    String fromTemplate = MessageUtil.localize("commands.neoessentials.reply.format.from");
                                    String toMessage = toTemplate.replace("{MESSAGE}", message);
                                    String fromMessage = fromTemplate.replace("{MESSAGE}", message);
                                    String resolvedToMessage = PlaceholderAPI.setPlaceholders(target, toMessage);
                                    String resolvedFromMessage = PlaceholderAPI.setPlaceholders(sender, fromMessage);
                                    target.sendSystemMessage(MessageUtil.coloredText(resolvedFromMessage));
                                    sender.sendSystemMessage(MessageUtil.coloredText(resolvedToMessage));
                                    ChatDebugUtil.debug(
                                       "ReplyCommand - Setting last messager: %s can reply to %s", target.getName().getString(), sender.getName().getString()
                                    );
                                    LastMessageManager.setLastMessager(target, sender);
                                    ChatAPI.broadcastSocialSpy(sender, target, message);
                                    ChatIntegrationManager.broadcastPrivateMessage(sender, target, message);
                                    return 1;
                                 }
                              }
                           }
                        }
                     }
                  )
            )
      );
   }
}
