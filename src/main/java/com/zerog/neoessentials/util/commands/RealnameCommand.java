package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RealnameCommand {
   private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS_AND_NICKS = (context, builder) -> {
      String input = builder.getRemaining().toLowerCase();

      for (ServerPlayer player : ((CommandSourceStack)context.getSource()).getServer().getPlayerList().getPlayers()) {
         String realName = player.getName().getString();
         String nickname = NickCommand.getNickname(player.getUUID());
         if (realName.toLowerCase().startsWith(input)) {
            builder.suggest(realName);
         }

         if (nickname != null) {
            String cleanNickname = nickname.replaceAll("&[0-9a-fk-or#]", "").replaceAll("&#[0-9a-fA-F]{6}", "");
            if (cleanNickname.toLowerCase().startsWith(input)) {
               builder.suggest(cleanNickname, Component.literal("Nickname of " + realName));
            }
         }
      }

      return builder.buildFuture();
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("realname")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("realname")
                  .then(
                     Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_PLAYERS_AND_NICKS)
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.realname"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 String playerName = StringArgumentType.getString(ctx, "player");
                                 return showRealName((CommandSourceStack)ctx.getSource(), playerName);
                              }
                           }
                        )
                  ))
               .executes(ctx -> {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.info("commands.neoessentials.realname.usage"));
                  return 0;
               })
         );
      }
   }

   private static int showRealName(CommandSourceStack source, String query) {
      List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
      ServerPlayer exactMatch = players.stream().filter(p -> p.getName().getString().equalsIgnoreCase(query)).findFirst().orElse(null);
      if (exactMatch != null) {
         return showPlayerInfo(source, exactMatch, true);
      } else {
         List<ServerPlayer> nicknameMatches = players.stream().filter(p -> {
            String nicknamex = NickCommand.getNickname(p.getUUID());
            if (nicknamex == null) {
               return false;
            } else {
               String cleanNickname = nicknamex.replaceAll("&[0-9a-fk-or]", "").toLowerCase();
               return cleanNickname.equals(query.toLowerCase());
            }
         }).collect(Collectors.toList());
         if (nicknameMatches.size() == 1) {
            return showPlayerInfo(source, nicknameMatches.get(0), false);
         } else if (nicknameMatches.size() <= 1) {
            List<ServerPlayer> partialMatches = players.stream().filter(p -> {
               if (p.getName().getString().toLowerCase().contains(query.toLowerCase())) {
                  return true;
               } else {
                  String nicknamex = NickCommand.getNickname(p.getUUID());
                  if (nicknamex != null) {
                     String cleanNickname = nicknamex.replaceAll("&[0-9a-fk-or]", "").toLowerCase();
                     return cleanNickname.contains(query.toLowerCase());
                  } else {
                     return false;
                  }
               }
            }).collect(Collectors.toList());
            if (partialMatches.isEmpty()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.realname.not_found", query));
               return 0;
            } else if (partialMatches.size() == 1) {
               return showPlayerInfo(source, partialMatches.get(0), false);
            } else {
               source.sendFailure(MessageUtil.error("commands.neoessentials.realname.multiple_matches", query));
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.partial_matches_header"), false);

               for (ServerPlayer player : partialMatches.subList(0, Math.min(10, partialMatches.size()))) {
                  String nickname = NickCommand.getNickname(player.getUUID());
                  if (nickname != null) {
                     String formattedNick = nickname.replace("&", "§");
                     source.sendSuccess(
                        () -> MessageUtil.info("commands.neoessentials.realname.match_entry", formattedNick, player.getName().getString()), false
                     );
                  } else {
                     source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.no_nick_entry", player.getName().getString()), false);
                  }
               }

               if (partialMatches.size() > 10) {
                  source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.more_matches", partialMatches.size() - 10), false);
               }

               return 1;
            }
         } else {
            source.sendFailure(MessageUtil.error("commands.neoessentials.realname.multiple_matches", query));
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.matches_header"), false);

            for (ServerPlayer playerx : nicknameMatches) {
               String nickname = NickCommand.getDisplayName(playerx);
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.match_entry", nickname, player.getName().getString()), false);
            }

            return 1;
         }
      }
   }

   private static int showPlayerInfo(CommandSourceStack source, ServerPlayer player, boolean searchedByRealName) {
      String realName = player.getName().getString();
      String nickname = NickCommand.getNickname(player.getUUID());
      if (nickname == null) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.no_nickname", realName), false);
      } else {
         String formattedNick = nickname.replace("&", "§");
         if (searchedByRealName) {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.realname.by_realname", realName, formattedNick), false);
         } else {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.realname.by_nickname", formattedNick, realName), false);
         }
      }

      source.sendSuccess(
         () -> MessageUtil.info(
               "commands.neoessentials.realname.player_info",
               realName,
               player.getUUID().toString(),
               player.level().toString(),
               String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ())
            ),
         false
      );
      StringBuilder statusBuilder = new StringBuilder();
      if (player.hasPermissions(4)) {
         statusBuilder.append("§cOperator");
      }

      VanishManager vanishManager = VanishManager.getInstance();
      if (vanishManager.isPlayerVanished(player.getUUID())) {
         if (statusBuilder.length() > 0) {
            statusBuilder.append("§7, ");
         }

         statusBuilder.append("§7Vanished");
      }

      AfkManager afkManager = AfkManager.getInstance();
      if (afkManager.isAfk(player)) {
         if (statusBuilder.length() > 0) {
            statusBuilder.append("§7, ");
         }

         statusBuilder.append("§eAFK");
      }

      if (statusBuilder.length() > 0) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.status", statusBuilder.toString()), false);
      }

      return 1;
   }
}
