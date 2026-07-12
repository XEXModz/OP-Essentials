package com.zerog.neoessentials.moderation.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.BanManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(BanCommand.class);
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_BANNED_PLAYERS = (ctx, builder) -> {
      BanManager banManager = BanManager.getInstance();
      return SharedSuggestionProvider.suggest(banManager.getAllPlayerBans().stream().map(ban -> ban.playerName).collect(Collectors.toList()), builder);
   };
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_BANNED_IPS = (ctx, builder) -> {
      BanManager banManager = BanManager.getInstance();
      return SharedSuggestionProvider.suggest(banManager.getAllIPBans().stream().map(ban -> ban.ipAddress).collect(Collectors.toList()), builder);
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isModerationEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ban")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.ban").hasPermission()))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.greedyString())
                        .suggests(
                           (ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder)
                        )
                        .executes(ctx -> executeBan(ctx, StringArgumentType.getString(ctx, "player"), ConfigManager.getInstance().getDefaultBanReason())))
                     .then(
                        Commands.argument("reason", StringArgumentType.greedyString())
                           .executes(
                              ctx -> executeBan(
                                    ctx,
                                    ctx.getInput().split(" ", 3)[1],
                                    ctx.getInput().split(" ", 3).length > 2
                                       ? ctx.getInput().split(" ", 3)[2]
                                       : ConfigManager.getInstance().getDefaultBanReason()
                                 )
                           )
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tempban")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.tempban").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("duration", StringArgumentType.word())
                              .executes(
                                 ctx -> executeTempBan(
                                       ctx,
                                       StringArgumentType.getString(ctx, "player"),
                                       StringArgumentType.getString(ctx, "duration"),
                                       ConfigManager.getInstance().getDefaultBanReason()
                                    )
                              ))
                           .then(
                              Commands.argument("reason", StringArgumentType.greedyString())
                                 .executes(
                                    ctx -> executeTempBan(
                                          ctx,
                                          StringArgumentType.getString(ctx, "player"),
                                          StringArgumentType.getString(ctx, "duration"),
                                          StringArgumentType.getString(ctx, "reason")
                                       )
                                 )
                           )
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("banip")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.banip").hasPermission()))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("ip", StringArgumentType.word())
                        .executes(ctx -> executeBanIP(ctx, StringArgumentType.getString(ctx, "ip"), ConfigManager.getInstance().getDefaultBanReason())))
                     .then(
                        Commands.argument("reason", StringArgumentType.greedyString())
                           .executes(ctx -> executeBanIP(ctx, StringArgumentType.getString(ctx, "ip"), StringArgumentType.getString(ctx, "reason")))
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unban")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.unban").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests(SUGGEST_BANNED_PLAYERS)
                     .executes(ctx -> executeUnban(ctx, StringArgumentType.getString(ctx, "player")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unbanip")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.unbanip").hasPermission()))
               .then(
                  Commands.argument("ip", StringArgumentType.word())
                     .suggests(SUGGEST_BANNED_IPS)
                     .executes(ctx -> executeUnbanIP(ctx, StringArgumentType.getString(ctx, "ip")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("banlist")
                        .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.banlist").hasPermission()))
                     .executes(ctx -> executeBanList(ctx, "players")))
                  .then(Commands.literal("players").executes(ctx -> executeBanList(ctx, "players"))))
               .then(Commands.literal("ips").executes(ctx -> executeBanList(ctx, "ips")))
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tempbanip")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.tempbanip").hasPermission()))
               .then(
                  Commands.argument("ip", StringArgumentType.word())
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("duration", StringArgumentType.word())
                              .executes(
                                 ctx -> executeTempBanIP(
                                       ctx,
                                       StringArgumentType.getString(ctx, "ip"),
                                       StringArgumentType.getString(ctx, "duration"),
                                       ConfigManager.getInstance().getDefaultBanReason()
                                    )
                              ))
                           .then(
                              Commands.argument("reason", StringArgumentType.greedyString())
                                 .executes(
                                    ctx -> executeTempBanIP(
                                          ctx,
                                          StringArgumentType.getString(ctx, "ip"),
                                          StringArgumentType.getString(ctx, "duration"),
                                          StringArgumentType.getString(ctx, "reason")
                                       )
                                 )
                           )
                     )
               )
         );
      }
   }

   private static int executeBan(CommandContext<CommandSourceStack> ctx, String playerName, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String bannedBy = getCommandSender(source);

      try {
         BanManager banManager = BanManager.getInstance();
         MinecraftServer server = source.getServer();
         UUID playerId = null;
         String resolvedName = playerName;

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(playerName)) {
               playerId = player.getUUID();
               resolvedName = player.getName().getString();
               break;
            }
         }

         if (playerId == null) {
            Optional<GameProfile> profile = server.getProfileCache().get(playerName);
            if (profile.isPresent()) {
               playerId = profile.get().getId();
               resolvedName = profile.get().getName();
            }
         }

         if (playerId == null) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
            return 0;
         } else {
            boolean success = banManager.banPlayer(resolvedName, playerId, reason, bannedBy);
            if (success) {
               String message = MessageUtil.localize("neoessentials.moderation.ban_success", resolvedName, reason);
               source.sendSuccess(() -> MessageUtil.success(message), true);
               broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.ban_broadcast", resolvedName, bannedBy, reason));
               LOGGER.info("Player {} banned by {} for: {}", new Object[]{resolvedName, bannedBy, reason});
               return 1;
            } else {
               String message = MessageUtil.localize("neoessentials.moderation.ban_failed", resolvedName);
               source.sendFailure(MessageUtil.error(message));
               return 0;
            }
         }
      } catch (Exception var11) {
         LOGGER.error("Error executing ban command", var11);
         source.sendFailure(MessageUtil.error("An error occurred while executing the ban command."));
         return 0;
      }
   }

   private static int executeTempBan(CommandContext<CommandSourceStack> ctx, String playerName, String duration, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String bannedBy = getCommandSender(source);
      String durationStr = duration;

      try {
         BanManager banManager = BanManager.getInstance();
         long durationMillis = BanManager.parseDuration(duration);
         if (durationMillis <= 0L) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.invalid_duration", duration));
            return 0;
         } else {
            MinecraftServer server = source.getServer();
            UUID playerId = null;
            String resolvedName = playerName;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               if (player.getName().getString().equalsIgnoreCase(playerName)) {
                  playerId = player.getUUID();
                  resolvedName = player.getName().getString();
                  break;
               }
            }

            if (playerId == null) {
               Optional<GameProfile> profile = server.getProfileCache().get(playerName);
               if (profile.isPresent()) {
                  playerId = profile.get().getId();
                  resolvedName = profile.get().getName();
               }
            }

            if (playerId == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
               return 0;
            } else {
               boolean success = banManager.tempBanPlayer(resolvedName, playerId, reason, bannedBy, durationMillis);
               if (success) {
                  String message = MessageUtil.localize("neoessentials.moderation.tempban_success", resolvedName, durationStr, reason);
                  source.sendSuccess(() -> MessageUtil.success(message), true);
                  broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.tempban_broadcast", resolvedName, durationStr, bannedBy, reason));
                  LOGGER.info("Player {} temp banned by {} for {} - Reason: {}", new Object[]{resolvedName, bannedBy, durationStr, reason});
                  return 1;
               } else {
                  String message = MessageUtil.localize("neoessentials.moderation.ban_failed", resolvedName);
                  source.sendFailure(MessageUtil.error(message));
                  return 0;
               }
            }
         }
      } catch (Exception var15) {
         LOGGER.error("Error executing tempban command", var15);
         source.sendFailure(MessageUtil.error("An error occurred while executing the tempban command."));
         return 0;
      }
   }

   private static int executeTempBanIP(CommandContext<CommandSourceStack> ctx, String ipAddress, String durationStr, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String bannedBy = getCommandSender(source);

      try {
         BanManager banManager = BanManager.getInstance();
         MinecraftServer server = source.getServer();
         long durationMillis = BanManager.parseDuration(durationStr);
         if (durationMillis <= 0L) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.invalid_duration", durationStr));
            return 0;
         } else if (!isValidIP(ipAddress)) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.invalid_ip", ipAddress));
            return 0;
         } else {
            boolean success = banManager.tempBanIP(ipAddress, reason, bannedBy, durationMillis);
            if (success) {
               String formattedDur = BanManager.formatDuration(durationMillis);
               String msg = MessageUtil.localize("neoessentials.moderation.tempbanip_success", ipAddress, formattedDur, reason);
               source.sendSuccess(() -> MessageUtil.success(msg), true);
               broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.tempbanip_broadcast", ipAddress, formattedDur, bannedBy, reason));
               LOGGER.info("IP {} temp-banned by {} for {} - Reason: {}", new Object[]{ipAddress, bannedBy, formattedDur, reason});
               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.banip_failed", ipAddress));
               return 0;
            }
         }
      } catch (Exception var13) {
         LOGGER.error("Error executing tempbanip command", var13);
         source.sendFailure(MessageUtil.error("An error occurred while executing the tempbanip command."));
         return 0;
      }
   }

   private static int executeBanIP(CommandContext<CommandSourceStack> ctx, String ipAddress, String reason) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String bannedBy = getCommandSender(source);

      try {
         BanManager banManager = BanManager.getInstance();
         MinecraftServer server = source.getServer();
         if (!isValidIP(ipAddress)) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.invalid_ip", ipAddress));
            return 0;
         } else {
            boolean success = banManager.banIP(ipAddress, reason, bannedBy);
            if (success) {
               String message = MessageUtil.localize("neoessentials.moderation.banip_success", ipAddress, reason);
               source.sendSuccess(() -> MessageUtil.success(message), true);
               broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.banip_broadcast", ipAddress, bannedBy, reason));
               LOGGER.info("IP {} banned by {} for: {}", new Object[]{ipAddress, bannedBy, reason});
               return 1;
            } else {
               String message = MessageUtil.localize("neoessentials.moderation.banip_failed", ipAddress);
               source.sendFailure(MessageUtil.error(message));
               return 0;
            }
         }
      } catch (Exception var9) {
         LOGGER.error("Error executing banip command", var9);
         source.sendFailure(MessageUtil.error("An error occurred while executing the banip command."));
         return 0;
      }
   }

   private static int executeUnban(CommandContext<CommandSourceStack> ctx, String playerName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String unbannedBy = getCommandSender(source);

      try {
         BanManager banManager = BanManager.getInstance();
         MinecraftServer server = source.getServer();
         UUID playerId = null;
         String resolvedName = playerName;
         List<BanManager.BanEntry> allBans = banManager.getAllPlayerBans();
         LOGGER.info("Checking {} active bans for player '{}'", allBans.size(), playerName);

         for (BanManager.BanEntry ban : allBans) {
            LOGGER.debug("Checking ban: {} (UUID: {})", ban.playerName, ban.playerId);
            if (ban.playerName.equalsIgnoreCase(playerName)) {
               playerId = ban.playerId;
               resolvedName = ban.playerName;
               LOGGER.info("Found banned player: {} with UUID {}", resolvedName, playerId);
               break;
            }
         }

         if (playerId == null) {
            LOGGER.info("Player '{}' not found in ban list, checking player cache", playerName);
            Optional<GameProfile> profile = server.getProfileCache().get(playerName);
            if (profile.isPresent()) {
               playerId = profile.get().getId();
               resolvedName = profile.get().getName();
               LOGGER.info("Found player in cache: {} with UUID {}", resolvedName, playerId);
            }
         }

         if (playerId == null) {
            LOGGER.warn("Could not find player '{}' in bans or player cache", playerName);
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
            return 0;
         } else if (!banManager.isPlayerBanned(playerId)) {
            LOGGER.info("Player {} ({}) is not currently banned", resolvedName, playerId);
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_banned", resolvedName));
            return 0;
         } else {
            boolean success = banManager.unbanPlayer(playerId);
            if (success) {
               String message = MessageUtil.localize("neoessentials.moderation.unban_success", resolvedName);
               source.sendSuccess(() -> MessageUtil.success(message), true);
               broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.unban_broadcast", resolvedName, unbannedBy));
               LOGGER.info("Player {} unbanned by {}", resolvedName, unbannedBy);
               return 1;
            } else {
               LOGGER.error("Failed to unban player {} ({}): unbanPlayer returned false", resolvedName, playerId);
               String message = MessageUtil.localize("neoessentials.moderation.unban_failed", resolvedName);
               source.sendFailure(MessageUtil.error(message));
               return 0;
            }
         }
      } catch (Exception var11) {
         LOGGER.error("Error executing unban command", var11);
         source.sendFailure(MessageUtil.error("An error occurred while executing the unban command."));
         return 0;
      }
   }

   private static int executeUnbanIP(CommandContext<CommandSourceStack> ctx, String ipAddress) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String unbannedBy = getCommandSender(source);

      try {
         BanManager banManager = BanManager.getInstance();
         MinecraftServer server = source.getServer();
         boolean success = banManager.unbanIP(ipAddress);
         if (success) {
            String message = MessageUtil.localize("neoessentials.moderation.unbanip_success", ipAddress);
            source.sendSuccess(() -> MessageUtil.success(message), true);
            broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.unbanip_broadcast", ipAddress, unbannedBy));
            LOGGER.info("IP {} unbanned by {}", ipAddress, unbannedBy);
            return 1;
         } else {
            String message = MessageUtil.localize("neoessentials.moderation.unbanip_failed", ipAddress);
            source.sendFailure(MessageUtil.error(message));
            return 0;
         }
      } catch (Exception var8) {
         LOGGER.error("Error executing unbanip command", var8);
         source.sendFailure(MessageUtil.error("An error occurred while executing the unbanip command."));
         return 0;
      }
   }

   private static int executeBanList(CommandContext<CommandSourceStack> ctx, String type) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         BanManager banManager = BanManager.getInstance();
         if ("players".equals(type)) {
            List<BanManager.BanEntry> bannedPlayers = banManager.getAllPlayerBans();
            if (bannedPlayers.isEmpty()) {
               String message = MessageUtil.localize("neoessentials.moderation.banlist_empty_players");
               source.sendSuccess(() -> MessageUtil.info(message), false);
               return 1;
            }

            String header = MessageUtil.localize("neoessentials.moderation.banlist_header_players", bannedPlayers.size());
            source.sendSuccess(() -> MessageUtil.info(header), false);

            for (BanManager.BanEntry ban : bannedPlayers) {
               String expireInfo = ban.expireTime > 0L
                  ? MessageUtil.localize("neoessentials.moderation.banlist_expires", ban.getFormattedExpireTime())
                  : MessageUtil.localize("neoessentials.moderation.banlist_permanent");
               String banInfo = MessageUtil.localize(
                  "neoessentials.moderation.banlist_entry_player", ban.playerName, ban.reason, ban.bannedBy, ban.getFormattedBanTime(), expireInfo
               );
               source.sendSuccess(() -> MessageUtil.info(banInfo), false);
            }
         } else {
            List<BanManager.IPBanEntry> bannedIPs = banManager.getAllIPBans();
            if (bannedIPs.isEmpty()) {
               String message = MessageUtil.localize("neoessentials.moderation.banlist_empty_ips");
               source.sendSuccess(() -> MessageUtil.info(message), false);
               return 1;
            }

            String header = MessageUtil.localize("neoessentials.moderation.banlist_header_ips", bannedIPs.size());
            source.sendSuccess(() -> MessageUtil.info(header), false);

            for (BanManager.IPBanEntry ban : bannedIPs) {
               String banInfo = MessageUtil.localize(
                  "neoessentials.moderation.banlist_entry_ip", ban.ipAddress, ban.reason, ban.bannedBy, ban.getFormattedBanTime()
               );
               source.sendSuccess(() -> MessageUtil.info(banInfo), false);
            }
         }

         return 1;
      } catch (Exception var10) {
         LOGGER.error("Error executing banlist command", var10);
         source.sendFailure(MessageUtil.error("An error occurred while executing the banlist command."));
         return 0;
      }
   }

   private static boolean isValidIP(String ip) {
      if (ip != null && !ip.isEmpty()) {
         String[] parts = ip.split("\\.");
         if (parts.length != 4) {
            return false;
         } else {
            try {
               for (String part : parts) {
                  int num = Integer.parseInt(part);
                  if (num < 0 || num > 255) {
                     return false;
                  }
               }

               return true;
            } catch (NumberFormatException var7) {
               return false;
            }
         }
      } else {
         return false;
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
