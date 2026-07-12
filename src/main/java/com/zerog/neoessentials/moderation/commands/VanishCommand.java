package com.zerog.neoessentials.moderation.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanishCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(VanishCommand.class);
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_VANISHED_PLAYERS = (ctx, builder) -> {
      VanishManager vanishManager = VanishManager.getInstance();
      return SharedSuggestionProvider.suggest(
         vanishManager.getVanishedPlayers()
            .stream()
            .map(uuid -> ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayer(uuid))
            .filter(player -> player != null)
            .map(player -> player.getName().getString())
            .collect(Collectors.toList()),
         builder
      );
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isVanishSystemEnabled()) {
         registerVanishCommand(dispatcher, "vanish");
         registerVanishCommand(dispatcher, "v");
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unvanish")
                     .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.vanish").hasPermission()))
                  .executes(ctx -> executeUnvanish(ctx, null)))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                        .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.vanish.others").hasPermission()))
                     .suggests(SUGGEST_VANISHED_PLAYERS)
                     .executes(ctx -> executeUnvanish(ctx, StringArgumentType.getString(ctx, "player")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("vanishlist")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.vanishlist").hasPermission()))
               .executes(ctx -> executeVanishList(ctx))
         );
      }
   }

   private static void registerVanishCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.vanish").hasPermission()))
               .executes(ctx -> executeToggleVanish(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                     .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.vanish.others").hasPermission()))
                  .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                  .executes(ctx -> executeToggleVanish(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
   }

   private static int executeToggleVanish(CommandContext<CommandSourceStack> ctx, String targetPlayerName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String vanishedBy = getCommandSender(source);

      try {
         VanishManager vanishManager = VanishManager.getInstance();
         MinecraftServer server = source.getServer();
         ServerPlayer targetPlayer;
         if (targetPlayerName == null) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_only_command"));
               return 0;
            }

            targetPlayer = player;
         } else {
            targetPlayer = server.getPlayerList().getPlayerByName(targetPlayerName);
            if (targetPlayer == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", targetPlayerName));
               return 0;
            }
         }

         String targetName = targetPlayer.getName().getString();
         UUID targetId = targetPlayer.getUUID();
         boolean isVanished = vanishManager.isPlayerVanished(targetId);
         if (isVanished) {
            boolean success = vanishManager.unvanishPlayer(targetId);
            if (success) {
               if (targetPlayerName == null) {
                  String message = MessageUtil.localize("neoessentials.moderation.vanish_disabled_self");
                  source.sendSuccess(() -> MessageUtil.success(message), false);
               } else {
                  String confirmMessage = MessageUtil.localize("neoessentials.moderation.vanish_disabled_other", targetName);
                  source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
                  String targetMessage = MessageUtil.localize("neoessentials.moderation.vanish_disabled_by", vanishedBy);
                  targetPlayer.sendSystemMessage(MessageUtil.info(targetMessage));
               }

               if (ConfigManager.isBroadcastToStaffVanishEnabled()) {
                  broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.vanish_disabled_broadcast", targetName, vanishedBy));
               }

               if (ConfigManager.isBroadcastToAllVanishEnabled()) {
                  broadcastToAll(server, MessageUtil.localize("neoessentials.moderation.vanish_disabled_broadcast", targetName, vanishedBy));
               }

               if (ConfigManager.isLogVanishActionsEnabled()) {
                  LOGGER.info("Player {} unvanished by {}", targetName, vanishedBy);
               }

               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.vanish_failed", targetName));
               return 0;
            }
         } else {
            boolean success = vanishManager.vanishPlayer(targetId, targetName, vanishedBy, targetPlayerName == null);
            if (success) {
               if (targetPlayerName == null) {
                  String message = MessageUtil.localize("neoessentials.moderation.vanish_enabled_self");
                  source.sendSuccess(() -> MessageUtil.success(message), false);
               } else {
                  String confirmMessage = MessageUtil.localize("neoessentials.moderation.vanish_enabled_other", targetName);
                  source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
                  String targetMessage = MessageUtil.localize("neoessentials.moderation.vanish_enabled_by", vanishedBy);
                  targetPlayer.sendSystemMessage(MessageUtil.info(targetMessage));
               }

               if (ConfigManager.isBroadcastToStaffVanishEnabled()) {
                  broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.vanish_enabled_broadcast", targetName, vanishedBy));
               }

               if (ConfigManager.isBroadcastToAllVanishEnabled()) {
                  broadcastToAll(server, MessageUtil.localize("neoessentials.moderation.vanish_enabled_broadcast", targetName, vanishedBy));
               }

               if (ConfigManager.isLogVanishActionsEnabled()) {
                  LOGGER.info("Player {} vanished by {}", targetName, vanishedBy);
               }

               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.vanish_failed", targetName));
               return 0;
            }
         }
      } catch (Exception var13) {
         LOGGER.error("Error executing vanish command", var13);
         source.sendFailure(MessageUtil.error("An error occurred while executing the vanish command."));
         return 0;
      }
   }

   private static int executeUnvanish(CommandContext<CommandSourceStack> ctx, String targetPlayerName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String vanishedBy = getCommandSender(source);

      try {
         VanishManager vanishManager = VanishManager.getInstance();
         MinecraftServer server = source.getServer();
         ServerPlayer targetPlayer;
         if (targetPlayerName == null) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_only_command"));
               return 0;
            }

            targetPlayer = player;
         } else {
            targetPlayer = server.getPlayerList().getPlayerByName(targetPlayerName);
            if (targetPlayer == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", targetPlayerName));
               return 0;
            }
         }

         String targetName = targetPlayer.getName().getString();
         UUID targetId = targetPlayer.getUUID();
         if (!vanishManager.isPlayerVanished(targetId)) {
            String message = MessageUtil.localize("neoessentials.moderation.player_not_vanished", targetName);
            source.sendFailure(MessageUtil.error(message));
            return 0;
         } else {
            boolean success = vanishManager.unvanishPlayer(targetId);
            if (success) {
               if (targetPlayerName == null) {
                  String message = MessageUtil.localize("neoessentials.moderation.vanish_disabled_self");
                  source.sendSuccess(() -> MessageUtil.success(message), false);
               } else {
                  String confirmMessage = MessageUtil.localize("neoessentials.moderation.vanish_disabled_other", targetName);
                  source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
                  String targetMessage = MessageUtil.localize("neoessentials.moderation.vanish_disabled_by", vanishedBy);
                  targetPlayer.sendSystemMessage(MessageUtil.info(targetMessage));
               }

               if (ConfigManager.isBroadcastToStaffVanishEnabled()) {
                  broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.vanish_disabled_broadcast", targetName, vanishedBy));
               }

               if (ConfigManager.isLogVanishActionsEnabled()) {
                  LOGGER.info("Player {} unvanished by {}", targetName, vanishedBy);
               }

               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.vanish_failed", targetName));
               return 0;
            }
         }
      } catch (Exception var12) {
         LOGGER.error("Error executing unvanish command", var12);
         source.sendFailure(MessageUtil.error("An error occurred while executing the unvanish command."));
         return 0;
      }
   }

   private static int executeVanishList(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         VanishManager vanishManager = VanishManager.getInstance();
         MinecraftServer server = source.getServer();
         Set<UUID> vanishedPlayers = vanishManager.getVanishedPlayers();
         if (vanishedPlayers.isEmpty()) {
            String message = MessageUtil.localize("neoessentials.moderation.vanishlist_empty");
            source.sendSuccess(() -> MessageUtil.info(message), false);
            return 1;
         } else {
            String header = MessageUtil.localize("neoessentials.moderation.vanishlist_header", vanishedPlayers.size());
            source.sendSuccess(() -> MessageUtil.info(header), false);

            for (UUID playerId : vanishedPlayers) {
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  String playerName = player.getName().getString();
                  String vanishInfo = MessageUtil.localize("neoessentials.moderation.vanishlist_entry", playerName);
                  source.sendSuccess(() -> MessageUtil.info(vanishInfo), false);
               } else {
                  String offlineInfo = MessageUtil.localize("neoessentials.moderation.vanishlist_offline", playerId.toString());
                  source.sendSuccess(() -> MessageUtil.warning(offlineInfo), false);
               }
            }

            return 1;
         }
      } catch (Exception var11) {
         LOGGER.error("Error executing vanishlist command", var11);
         source.sendFailure(MessageUtil.error("An error occurred while executing the vanishlist command."));
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

   private static void broadcastToAll(MinecraftServer server, String message) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         player.sendSystemMessage(MessageUtil.info(message));
      }
   }

   private static String getCommandSender(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "Console";
   }

   private static UUID getPlayerUUID(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
   }
}
