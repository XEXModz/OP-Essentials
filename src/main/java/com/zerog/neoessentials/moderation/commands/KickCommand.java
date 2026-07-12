package com.zerog.neoessentials.moderation.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KickCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(KickCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kick")
               .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.kick").hasPermission()))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                     .executes(ctx -> executeKick(ctx, StringArgumentType.getString(ctx, "player"), "Kicked by an operator")))
                  .then(
                     Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> executeKick(ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason")))
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kickall")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.kickall").hasPermission()))
               .executes(ctx -> executeKickAll(ctx, "Server maintenance")))
            .then(
               Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> executeKickAll(ctx, StringArgumentType.getString(ctx, "reason")))
            )
      );
   }

   private static int executeKick(CommandContext<CommandSourceStack> ctx, String playerName, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String kickedBy = getCommandSender(source);

      try {
         InputValidator.ValidationResult reasonResult = InputValidator.validateReason(reason);
         if (!reasonResult.isValid()) {
            source.sendFailure(MessageUtil.error("Invalid reason: " + reasonResult.getErrorMessage()));
            return 0;
         } else {
            reason = (String)reasonResult.getValue();
            MinecraftServer server = source.getServer();
            ServerPlayer targetPlayer = null;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               if (player.getName().getString().equalsIgnoreCase(playerName)) {
                  targetPlayer = player;
                  break;
               }
            }

            if (targetPlayer == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
               return 0;
            } else {
               if (source.getEntity() instanceof ServerPlayer sourcePlayer && sourcePlayer.equals(targetPlayer)) {
                  source.sendFailure(MessageUtil.error("neoessentials.moderation.cannot_kick_self"));
                  return 0;
               }

               String playerDisplayName = targetPlayer.getName().getString();
               String kickMessageTemplate = ConfigManager.getKickMessage();
               String kickMessage = kickMessageTemplate.replace("{reason}", reason).replace("{kicker}", kickedBy);
               targetPlayer.connection.disconnect(Component.literal(kickMessage));
               String confirmMessage = MessageUtil.localize("neoessentials.moderation.kick_success", playerDisplayName, reason);
               source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
               String broadcastMsg = MessageUtil.localize("neoessentials.moderation.kick_broadcast", playerDisplayName, kickedBy, reason);
               if (ConfigManager.isBroadcastKicksEnabled()) {
                  for (ServerPlayer playerx : server.getPlayerList().getPlayers()) {
                     playerx.sendSystemMessage(MessageUtil.info(broadcastMsg));
                  }
               }

               if (ConfigManager.isNotifyStaffOnKickEnabled()) {
                  broadcastToStaff(server, broadcastMsg);
               }

               if (ConfigManager.isLogKickActionsEnabled()) {
                  LOGGER.info("Player {} kicked by {} for: {}", new Object[]{playerDisplayName, kickedBy, reason});
               }

               return 1;
            }
         }
      } catch (Exception var15) {
         LOGGER.error("Error executing kick command", var15);
         source.sendFailure(MessageUtil.error("An error occurred while executing the kick command."));
         return 0;
      }
   }

   private static int executeKickAll(CommandContext<CommandSourceStack> ctx, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String kickedBy = getCommandSender(source);

      try {
         MinecraftServer server = source.getServer();
         List<ServerPlayer> playersToKick = server.getPlayerList().getPlayers().stream().filter(playerx -> !playerx.equals(source.getEntity())).toList();
         if (playersToKick.isEmpty()) {
            String message = MessageUtil.localize("neoessentials.moderation.kickall_no_players");
            source.sendSuccess(() -> MessageUtil.info(message), false);
            return 1;
         } else {
            String kickAllMessageTemplate = ConfigManager.getKickAllMessage();
            String kickAllMessage = kickAllMessageTemplate.replace("{reason}", reason).replace("{kicker}", kickedBy);

            for (ServerPlayer player : playersToKick) {
               player.connection.disconnect(Component.literal(kickAllMessage));
            }

            String confirmMessage = MessageUtil.localize("neoessentials.moderation.kickall_success", playersToKick.size(), reason);
            source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
            if (ConfigManager.isLogKickActionsEnabled()) {
               LOGGER.info("Kicked {} players by {} for: {}", new Object[]{playersToKick.size(), kickedBy, reason});
            }

            return 1;
         }
      } catch (Exception var10) {
         LOGGER.error("Error executing kickall command", var10);
         source.sendFailure(MessageUtil.error("An error occurred while executing the kickall command."));
         return 0;
      }
   }

   private static void broadcastToStaff(MinecraftServer server, String message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.moderation.notifications")) {
            player.sendSystemMessage(MessageUtil.info(message));
         }
      }
   }

   private static String getCommandSender(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "Console";
   }

   private static UUID getPlayerUUID(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
   }
}
