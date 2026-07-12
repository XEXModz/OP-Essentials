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
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import com.zerog.neoessentials.util.commands.MailCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JailCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(JailCommand.class);
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_JAILED_PLAYERS = (ctx, builder) -> {
      JailManager jailManager = JailManager.getInstance();
      return SharedSuggestionProvider.suggest(jailManager.getAllJailedPlayers().stream().map(jail -> jail.playerName).collect(Collectors.toList()), builder);
   };
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_JAIL_NAMES = (ctx, builder) -> {
      JailManager jailManager = JailManager.getInstance();
      return SharedSuggestionProvider.suggest(jailManager.getAllJailLocations().stream().map(jail -> jail.name).collect(Collectors.toList()), builder);
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isModerationEnabled() && JailManager.isJailSystemEnabled()) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jail")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jail").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("jail", StringArgumentType.word())
                              .suggests(SUGGEST_JAIL_NAMES)
                              .executes(
                                 ctx -> {
                                    String defaultReason = ConfigManager.getInstance().getConfig("config.json").has("moderation")
                                          && ConfigManager.getInstance().getConfig("config.json").getAsJsonObject("moderation").has("jailSettings")
                                          && ConfigManager.getInstance()
                                             .getConfig("config.json")
                                             .getAsJsonObject("moderation")
                                             .getAsJsonObject("jailSettings")
                                             .has("defaultJailReason")
                                       ? ConfigManager.getInstance()
                                          .getConfig("config.json")
                                          .getAsJsonObject("moderation")
                                          .getAsJsonObject("jailSettings")
                                          .get("defaultJailReason")
                                          .getAsString()
                                       : "Jailed by an operator";
                                    return executeJail(
                                       ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "jail"), defaultReason, 0L
                                    );
                                 }
                              ))
                           .then(
                              Commands.argument("reason", StringArgumentType.greedyString())
                                 .executes(
                                    ctx -> executeJail(
                                          ctx,
                                          StringArgumentType.getString(ctx, "player"),
                                          StringArgumentType.getString(ctx, "jail"),
                                          StringArgumentType.getString(ctx, "reason"),
                                          0L
                                       )
                                 )
                           )
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jailfor")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jail").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                     .then(
                        Commands.argument("jail", StringArgumentType.word())
                           .suggests(SUGGEST_JAIL_NAMES)
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("duration", StringArgumentType.word())
                                    .executes(
                                       ctx -> {
                                          long dur = MailCommand.parseDuration(StringArgumentType.getString(ctx, "duration"));
                                          if (dur < 0L) {
                                             ((CommandSourceStack)ctx.getSource())
                                                .sendFailure(
                                                   MessageUtil.error(
                                                      "commands.neoessentials.jail.invalid_duration", StringArgumentType.getString(ctx, "duration")
                                                   )
                                                );
                                             return 0;
                                          } else {
                                             String defaultReason = "Jailed by an operator";
                                             return executeJail(
                                                ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "jail"), defaultReason, dur
                                             );
                                          }
                                       }
                                    ))
                                 .then(
                                    Commands.argument("reason", StringArgumentType.greedyString())
                                       .executes(
                                          ctx -> {
                                             long dur = MailCommand.parseDuration(StringArgumentType.getString(ctx, "duration"));
                                             if (dur < 0L) {
                                                ((CommandSourceStack)ctx.getSource())
                                                   .sendFailure(
                                                      MessageUtil.error(
                                                         "commands.neoessentials.jail.invalid_duration", StringArgumentType.getString(ctx, "duration")
                                                      )
                                                   );
                                                return 0;
                                             } else {
                                                return executeJail(
                                                   ctx,
                                                   StringArgumentType.getString(ctx, "player"),
                                                   StringArgumentType.getString(ctx, "jail"),
                                                   StringArgumentType.getString(ctx, "reason"),
                                                   dur
                                                );
                                             }
                                          }
                                       )
                                 )
                           )
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unjail")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.unjail").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests(SUGGEST_JAILED_PLAYERS)
                     .executes(ctx -> executeUnjail(ctx, StringArgumentType.getString(ctx, "player")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("setjail")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.setjail").hasPermission()))
               .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> executeSetJail(ctx, StringArgumentType.getString(ctx, "name"))))
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jaillist")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jaillist").hasPermission()))
               .executes(ctx -> executeJailList(ctx))
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jailinfo")
                     .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jailinfo").hasPermission()))
                  .executes(ctx -> executeJailInfo(ctx, null)))
               .then(
                  Commands.argument("jail", StringArgumentType.word())
                     .suggests(SUGGEST_JAIL_NAMES)
                     .executes(ctx -> executeJailInfo(ctx, StringArgumentType.getString(ctx, "jail")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("deljail")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.setjail").hasPermission()))
               .then(
                  Commands.argument("name", StringArgumentType.word())
                     .suggests(SUGGEST_JAIL_NAMES)
                     .executes(ctx -> executeDelJail(ctx, StringArgumentType.getString(ctx, "name")))
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jails")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jaillist").hasPermission()))
               .executes(ctx -> executeJailList(ctx))
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("togglejail")
                  .requires(source -> PermissionValidator.validatePermission(source, "neoessentials.moderation.jail").hasPermission()))
               .then(
                  Commands.argument("player", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder))
                     .executes(ctx -> executeToggleJail(ctx, StringArgumentType.getString(ctx, "player")))
               )
         );
      }
   }

   private static int executeToggleJail(CommandContext<CommandSourceStack> ctx, String playerName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      JailManager jailManager = JailManager.getInstance();
      MinecraftServer server = source.getServer();
      UUID playerId = null;
      String resolvedName = playerName;

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (p.getName().getString().equalsIgnoreCase(playerName)) {
            playerId = p.getUUID();
            resolvedName = p.getName().getString();
            break;
         }
      }

      if (playerId == null) {
         source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
         return 0;
      } else {
         boolean isJailed = jailManager.isPlayerJailed(playerId);
         if (isJailed) {
            boolean ok = jailManager.unjailPlayer(playerId);
            if (ok) {
               String name = resolvedName;
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.jail.unjail_success", name), true);
               return 1;
            }
         } else {
            List<JailManager.JailLocation> locations = jailManager.getAllJailLocations();
            if (locations.isEmpty()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.jail.no_locations"));
               return 0;
            }

            String jailName = locations.get(0).name;
            boolean ok = jailManager.jailPlayer(resolvedName, playerId, "Toggled by staff", getCommandSender(source), jailName, 0L);
            if (ok) {
               String name = resolvedName;
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.jail.jail_success", name, jailName), true);
               return 1;
            }
         }

         source.sendFailure(MessageUtil.error("commands.neoessentials.jail.toggle_failed", resolvedName));
         return 0;
      }
   }

   private static int executeJail(CommandContext<CommandSourceStack> ctx, String playerName, String jailName, String reason, long durationMillis) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String jailedBy = getCommandSender(source);

      try {
         InputValidator.ValidationResult reasonResult = InputValidator.validateReason(reason);
         if (!reasonResult.isValid()) {
            source.sendFailure(MessageUtil.error("Invalid reason: " + reasonResult.getErrorMessage()));
            return 0;
         } else {
            reason = (String)reasonResult.getValue();
            JailManager jailManager = JailManager.getInstance();
            MinecraftServer server = source.getServer();
            boolean requireJailLocation = ConfigManager.isRequireJailLocationEnabled();
            if (requireJailLocation && jailManager.getAllJailLocations().isEmpty()) {
               source.sendFailure(MessageUtil.error("No jail locations are set. Please set a jail location before jailing players."));
               return 0;
            } else if (jailManager.getJailLocation(jailName) == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.jail_not_found", jailName));
               return 0;
            } else {
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
                  boolean success = jailManager.jailPlayer(resolvedName, playerId, reason, jailedBy, jailName, durationMillis);
                  if (success) {
                     String confirmMessage = MessageUtil.localize("neoessentials.moderation.jail_success", resolvedName, jailName, reason);
                     source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
                     broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.jail_broadcast", resolvedName, jailName, jailedBy, reason));
                     LOGGER.info("Player {} jailed by {} in {} for: {}", new Object[]{resolvedName, jailedBy, jailName, reason});
                     return 1;
                  } else {
                     source.sendFailure(MessageUtil.error("neoessentials.moderation.jail_failed", resolvedName));
                     return 0;
                  }
               }
            }
         }
      } catch (Exception var16) {
         LOGGER.error("Error executing jail command", var16);
         source.sendFailure(MessageUtil.error("An error occurred while executing the jail command."));
         return 0;
      }
   }

   private static int executeUnjail(CommandContext<CommandSourceStack> ctx, String playerName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String unjailedBy = getCommandSender(source);

      try {
         JailManager jailManager = JailManager.getInstance();
         MinecraftServer server = source.getServer();
         UUID playerId = null;
         String resolvedName = playerName;
         ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(playerName);
         if (onlinePlayer != null) {
            playerId = onlinePlayer.getUUID();
            resolvedName = onlinePlayer.getName().getString();
         } else {
            Optional<GameProfile> profile = server.getProfileCache().get(playerName);
            if (profile.isPresent()) {
               playerId = profile.get().getId();
               resolvedName = profile.get().getName();
            }
         }

         if (playerId == null) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_found", playerName));
            return 0;
         } else if (!jailManager.isPlayerJailed(playerId)) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_not_jailed", resolvedName));
            return 0;
         } else {
            boolean success = jailManager.unjailPlayer(playerId);
            if (success) {
               String confirmMessage = MessageUtil.localize("neoessentials.moderation.unjail_success", resolvedName, unjailedBy);
               source.sendSuccess(() -> MessageUtil.success(confirmMessage), true);
               broadcastToStaff(server, MessageUtil.localize("neoessentials.moderation.unjail_broadcast", resolvedName, unjailedBy));
               LOGGER.info("Player {} unjailed by {}", resolvedName, unjailedBy);
               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.unjail_failed", resolvedName));
               return 0;
            }
         }
      } catch (Exception var11) {
         LOGGER.error("Error executing unjail command", var11);
         source.sendFailure(MessageUtil.error("An error occurred while executing the unjail command."));
         return 0;
      }
   }

   private static int executeSetJail(CommandContext<CommandSourceStack> ctx, String jailName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         if (source.getEntity() instanceof ServerPlayer player) {
            JailManager jailManager = JailManager.getInstance();
            BlockPos position = player.blockPosition();
            String dimension = player.level().dimension().location().toString();
            String createdBy = player.getName().getString();
            boolean success = jailManager.setJailLocation(jailName, position, dimension, createdBy);
            if (success) {
               String message = MessageUtil.localize("neoessentials.moderation.setjail_success", jailName, position.getX(), position.getY(), position.getZ());
               source.sendSuccess(() -> MessageUtil.success(message), true);
               LOGGER.info("Jail '{}' set at {} by {}", new Object[]{jailName, position, createdBy});
               return 1;
            } else {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.setjail_failed", jailName));
               return 0;
            }
         } else {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.player_only_command"));
            return 0;
         }
      } catch (Exception var10) {
         LOGGER.error("Error executing setjail command", var10);
         source.sendFailure(MessageUtil.error("An error occurred while executing the setjail command."));
         return 0;
      }
   }

   private static int executeJailList(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         JailManager jailManager = JailManager.getInstance();
         List<JailManager.JailEntry> jailedPlayers = jailManager.getAllJailedPlayers();
         if (jailedPlayers.isEmpty()) {
            String message = MessageUtil.localize("neoessentials.moderation.jaillist_empty");
            source.sendSuccess(() -> MessageUtil.info(message), false);
            return 1;
         } else {
            String header = MessageUtil.localize("neoessentials.moderation.jaillist_header", jailedPlayers.size());
            source.sendSuccess(() -> MessageUtil.info(header), false);

            for (JailManager.JailEntry jail : jailedPlayers) {
               String jailInfo = MessageUtil.localize(
                  "neoessentials.moderation.jaillist_entry", jail.playerName, jail.jailName, jail.reason, jail.jailedBy, jail.getFormattedJailTime()
               );
               source.sendSuccess(() -> MessageUtil.info(jailInfo), false);
            }

            return 1;
         }
      } catch (Exception var8) {
         LOGGER.error("Error executing jaillist command", var8);
         source.sendFailure(MessageUtil.error("An error occurred while executing the jaillist command."));
         return 0;
      }
   }

   private static int executeJailInfo(CommandContext<CommandSourceStack> ctx, String jailName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         JailManager jailManager = JailManager.getInstance();
         if (jailName == null) {
            List<JailManager.JailLocation> jailLocations = jailManager.getAllJailLocations();
            if (jailLocations.isEmpty()) {
               String message = MessageUtil.localize("neoessentials.moderation.jailinfo_no_jails");
               source.sendSuccess(() -> MessageUtil.warning(message), false);
               return 1;
            }

            String message = MessageUtil.localize("neoessentials.moderation.jailinfo_all_header");
            source.sendSuccess(() -> MessageUtil.warning(message), false);

            for (JailManager.JailLocation jail : jailLocations) {
               String locationInfo = MessageUtil.localize(
                  "neoessentials.moderation.jailinfo_location",
                  jail.name,
                  jail.position.getX(),
                  jail.position.getY(),
                  jail.position.getZ(),
                  jail.dimension,
                  jail.createdBy,
                  jail.getFormattedCreatedTime()
               );
               source.sendSuccess(() -> MessageUtil.info(locationInfo), false);
            }

            String countInfo = MessageUtil.localize("neoessentials.moderation.jailinfo_count", jailLocations.size());
            source.sendSuccess(() -> MessageUtil.info(countInfo), false);
         } else {
            JailManager.JailLocation jail = jailManager.getJailLocation(jailName);
            if (jail == null) {
               source.sendFailure(MessageUtil.error("neoessentials.moderation.jail_not_found", jailName));
               return 0;
            }

            String locationInfo = MessageUtil.localize(
               "neoessentials.moderation.jailinfo_specific",
               jail.name,
               jail.position.getX(),
               jail.position.getY(),
               jail.position.getZ(),
               jail.dimension,
               jail.createdBy,
               jail.getFormattedCreatedTime()
            );
            source.sendSuccess(() -> MessageUtil.info(locationInfo), false);
            long playersInJail = jailManager.getAllJailedPlayers().stream().filter(j -> j.jailName.equals(jailName)).count();
            if (playersInJail > 0L) {
               String playerInfo = MessageUtil.localize("neoessentials.moderation.jailinfo_players", playersInJail);
               source.sendSuccess(() -> MessageUtil.info(playerInfo), false);
            }
         }

         return 1;
      } catch (Exception var9) {
         LOGGER.error("Error executing jailinfo command", var9);
         source.sendFailure(MessageUtil.error("An error occurred while executing the jailinfo command."));
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

   private static int executeDelJail(CommandContext<CommandSourceStack> ctx, String jailName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         JailManager jailManager = JailManager.getInstance();
         if (jailManager.getJailLocation(jailName) == null) {
            source.sendFailure(MessageUtil.error("neoessentials.moderation.jail_not_found", jailName));
            return 0;
         } else {
            long inmates = jailManager.getAllJailedPlayers().stream().filter(j -> j.jailName.equals(jailName)).count();
            jailManager.removeJailLocation(jailName);
            String msg = MessageUtil.localize("commands.neoessentials.jail.deljail_success", jailName);
            source.sendSuccess(() -> MessageUtil.success(msg), true);
            if (inmates > 0L) {
               String warn = MessageUtil.localize("commands.neoessentials.jail.deljail_had_inmates", inmates);
               source.sendSuccess(() -> MessageUtil.warning(warn), false);
            }

            LOGGER.info("Jail location '{}' deleted by {}", jailName, getCommandSender(source));
            return 1;
         }
      } catch (Exception var8) {
         LOGGER.error("Error executing deljail command", var8);
         source.sendFailure(MessageUtil.error("An error occurred while deleting the jail."));
         return 0;
      }
   }

   private static String getCommandSender(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "Console";
   }

   private static UUID getPlayerUUID(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
   }
}
