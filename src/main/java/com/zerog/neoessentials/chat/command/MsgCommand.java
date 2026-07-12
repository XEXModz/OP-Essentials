package com.zerog.neoessentials.chat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(MsgCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ChatDebugUtil.debug("MsgCommand - Registering /msg command");
      registerCommand(dispatcher, "msg");
      registerCommand(dispatcher, "tell");
      registerCommand(dispatcher, "w");
      ChatDebugUtil.debug("MsgCommand - Also registering test commands: /message, /pm");
      registerCommand(dispatcher, "message");
      registerCommand(dispatcher, "pm");
   }

   private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal(commandName)
            .then(
               Commands.argument("target", EntityArgument.player())
                  .then(
                     Commands.argument("message", StringArgumentType.greedyString())
                        .executes(
                           ctx -> {
                              ChatDebugUtil.debug("MsgCommand - Command executed!");
                              CommandSourceStack source = (CommandSourceStack)ctx.getSource();

                              ServerPlayer target;
                              try {
                                 target = EntityArgument.getPlayer(ctx, "target");
                              } catch (CommandSyntaxException var16) {
                                 source.sendFailure(MessageUtil.error("commands.neoessentials.msg.not_found", "Unknown"));
                                 return 0;
                              }

                              String message = StringArgumentType.getString(ctx, "message");
                              ServerPlayer sender = source.getPlayer();
                              if (sender == null) {
                                 source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
                                 return 0;
                              } else {
                                 MinecraftServer server = sender.getServer();
                                 if (server == null) {
                                    source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
                                    return 0;
                                 } else {
                                    ChatDebugUtil.debug(
                                       "MsgCommand - Processing message from %s to %s", sender.getName().getString(), target.getName().getString()
                                    );
                                    if (sender.equals(target)) {
                                       ChatDebugUtil.debug("MsgCommand - FAILED: Player trying to message self");
                                       source.sendFailure(MessageUtil.error("commands.neoessentials.msg.self"));
                                       return 0;
                                    } else if (!ConfigManager.isChatEnabled()) {
                                       ChatDebugUtil.debug("MsgCommand - FAILED: Chat module is disabled");
                                       source.sendFailure(MessageUtil.error("commands.neoessentials.msg.disabled"));
                                       return 0;
                                    } else if (!ConfigManager.getInstance().isCommandEnabled("msg")) {
                                       ChatDebugUtil.debug("MsgCommand - FAILED: Msg command is disabled");
                                       source.sendFailure(MessageUtil.error("commands.neoessentials.msg.disabled"));
                                       return 0;
                                    } else {
                                       ChatManager chatManager = ChatAPI.getChatManager();
                                       if (chatManager != null && !chatManager.isMsgEnabled()) {
                                          ChatDebugUtil.debug("MsgCommand - FAILED: Messaging is disabled (legacy check)");
                                          source.sendFailure(MessageUtil.error("commands.neoessentials.msg.disabled"));
                                          return 0;
                                       } else {
                                          boolean hasPermission = PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.msg");
                                          ChatDebugUtil.debug("MsgCommand - Permission check for %s: %s", sender.getName().getString(), hasPermission);
                                          if (!hasPermission) {
                                             ChatDebugUtil.debug("MsgCommand - FAILED: No permission for neoessentials.chat.msg");
                                             source.sendFailure(MessageUtil.error("commands.neoessentials.msg.no_permission"));
                                             return 0;
                                          } else {
                                             String senderName = sender.getName().getString();
                                             boolean isMuted = MuteManager.isMuted(sender);
                                             ChatDebugUtil.debug("MsgCommand - Checking mute for %s, result: %s", senderName, isMuted);
                                             if (isMuted) {
                                                ChatDebugUtil.debug("MsgCommand - FAILED: Player is muted");
                                                LOGGER.debug("Blocked /msg from muted player: {}", senderName);
                                                source.sendFailure(MessageUtil.error("commands.neoessentials.msg.sender_muted"));
                                                return 0;
                                             } else if (IgnoreManager.isIgnoring(target, sender)) {
                                                ChatDebugUtil.debug("MsgCommand - FAILED: Target is ignoring sender");
                                                source.sendFailure(MessageUtil.error("commands.neoessentials.msg.target_ignoring"));
                                                return 0;
                                             } else if (MsgToggleManager.isMsgToggled(target)
                                                && !sender.hasPermissions(4)
                                                && !PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.msgtoggle.bypass")) {
                                                ChatDebugUtil.debug("MsgCommand - FAILED: Target has messaging toggled off and sender lacks bypass");
                                                source.sendFailure(
                                                   MessageUtil.error("commands.neoessentials.msg.target_toggled_off", target.getName().getString())
                                                );
                                                return 0;
                                             } else {
                                                ChatDebugUtil.debug("MsgCommand - SUCCESS: All checks passed, sending message");
                                                String toTemplate = MessageUtil.localize("commands.neoessentials.msg.format.to");
                                                String fromTemplate = MessageUtil.localize("commands.neoessentials.msg.format.from");
                                                String toMessage = toTemplate.replace("{MESSAGE}", message);
                                                String fromMessage = fromTemplate.replace("{MESSAGE}", message);
                                                String resolvedToMessage = PlaceholderAPI.setPlaceholders(target, toMessage);
                                                String resolvedFromMessage = PlaceholderAPI.setPlaceholders(sender, fromMessage);
                                                target.sendSystemMessage(MessageUtil.coloredText(resolvedFromMessage));
                                                sender.sendSystemMessage(MessageUtil.coloredText(resolvedToMessage));
                                                ChatDebugUtil.debug(
                                                   "MsgCommand - Setting last messager: %s can reply to %s",
                                                   target.getName().getString(),
                                                   sender.getName().getString()
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
                              }
                           }
                        )
                  )
            )
      );
   }
}
