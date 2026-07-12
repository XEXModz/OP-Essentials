package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AfkCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerAfkCommand(dispatcher, "afk");
      registerAfkCommand(dispatcher, "away");
   }

   private static void registerAfkCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.argument("message", StringArgumentType.greedyString())
                     .executes(
                        ctx -> {
                           ServerPlayer player = CommandSourceHelper.requirePlayer(
                              (CommandSourceStack)ctx.getSource(), "commands.neoessentials.afk.player_only"
                           );
                           if (player == null) {
                              return 0;
                           } else {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.afk"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else if (!ConfigManager.isChatEnabled()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                                 return 0;
                              } else if (!ConfigManager.getInstance().isCommandEnabled("afk")) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                                 return 0;
                              } else {
                                 ChatManager chatManager = ChatAPI.getChatManager();
                                 if (chatManager != null && !chatManager.isAfkEnabled()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                                    return 0;
                                 } else {
                                    String message = StringArgumentType.getString(ctx, "message");
                                    boolean wasAfk = AfkManager.getInstance().isAfk(player);
                                    AfkManager.getInstance().toggleAfk(player, message);
                                    if (!wasAfk) {
                                       ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§eYou are now AFK."), false);
                                    } else {
                                       ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§eYou are no longer AFK."), false);
                                    }

                                    return 1;
                                 }
                              }
                           }
                        }
                     )
               ))
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.afk.player_only");
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.afk"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else if (!ConfigManager.isChatEnabled()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                        return 0;
                     } else if (!ConfigManager.getInstance().isCommandEnabled("afk")) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                        return 0;
                     } else {
                        ChatManager chatManager = ChatAPI.getChatManager();
                        if (chatManager != null && !chatManager.isAfkEnabled()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.afk.disabled"));
                           return 0;
                        } else {
                           boolean wasAfk = AfkManager.getInstance().isAfk(player);
                           AfkManager.getInstance().toggleAfk(player, null);
                           if (!wasAfk) {
                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§eYou are now AFK."), false);
                           } else {
                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§eYou are no longer AFK."), false);
                           }

                           return 1;
                        }
                     }
                  }
               }
            )
      );
   }
}
