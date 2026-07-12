package com.zerog.neoessentials.util.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.MsgToggleManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerInfoCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerInfoCommands.class);
   private static final Map<UUID, Boolean> msgToggleBlocked = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> rtoggleEnabled = new ConcurrentHashMap<>();

   public static boolean isMsgBlocked(UUID uuid) {
      return msgToggleBlocked.getOrDefault(uuid, false);
   }

   public static boolean isRtoggleEnabled(UUID uuid) {
      return rtoggleEnabled.getOrDefault(uuid, true);
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("seen")) {
         registerSeen(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("near")) {
         registerNear(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("ping")) {
         registerPing(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("playtime")) {
         registerPlaytime(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("whois")) {
         registerWhois(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("realname")) {
         registerRealname(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("sudo")) {
         registerSudo(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("suicide")) {
         registerSuicide(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("msgtoggle")) {
         registerMsgToggle(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("rtoggle")) {
         registerRtoggle(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("motd")) {
         registerMotd(dispatcher);
      }

      if (ConfigManager.getInstance().isCommandEnabled("rules")) {
         registerRules(dispatcher);
      }
   }

   private static void registerSeen(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("seen")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.seen")))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> executeSeen(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
   }

   private static int executeSeen(CommandContext<CommandSourceStack> ctx, String name) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
      if (online != null) {
         int ping = online.connection.latency();
         src.sendSuccess(
            () -> MessageUtil.info(
                  "commands.neoessentials.seen.online",
                  online.getName().getString(),
                  online.serverLevel().dimension().location().getPath(),
                  String.format("%.0f, %.0f, %.0f", online.getX(), online.getY(), online.getZ()),
                  ping
               ),
            false
         );
         return 1;
      } else {
         GameProfileCache cache = src.getServer().getProfileCache();
         Optional<GameProfile> profile = cache != null ? cache.get(name) : Optional.empty();
         if (profile.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", name));
            return 0;
         } else {
            src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.seen.offline", profile.get().getName()), false);
            return 1;
         }
      }
   }

   private static void registerNear(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("near")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.near")))
               .executes(ctx -> executeNear(ctx, 200)))
            .then(
               Commands.argument("radius", IntegerArgumentType.integer(1, 10000))
                  .executes(ctx -> executeNear(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
            )
      );
   }

   private static int executeNear(CommandContext<CommandSourceStack> ctx, int radius) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         double r2 = (double)radius * (double)radius;
         List<String> nearby = new ArrayList<>();

         for (ServerPlayer other : src.getServer().getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(player.getUUID()) && other.serverLevel().equals(player.serverLevel())) {
               double dist = other.distanceToSqr(player.getX(), player.getY(), player.getZ());
               if (dist <= r2) {
                  nearby.add(other.getName().getString() + " §7(" + (int)Math.sqrt(dist) + "m)");
               }
            }
         }

         nearby.sort(Comparator.naturalOrder());
         String list = nearby.isEmpty() ? "§7none" : String.join("§r, §e", nearby);
         src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.near.result", radius, list), false);
         return 1;
      }
   }

   private static void registerPing(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ping")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ping")))
               .executes(ctx -> executePing(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ping.others")))
                  .executes(ctx -> executePing(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executePing(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (target == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         int ping = target.connection.latency();
         String color = ping < 80 ? "§a" : (ping < 200 ? "§e" : "§c");
         String name = target.getName().getString();
         src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.ping.result", name, color + ping + "§r"), false);
         return 1;
      }
   }

   private static void registerPlaytime(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("playtime")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.playtime")))
               .executes(ctx -> executePlaytime(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.playtime.others")))
                  .executes(ctx -> executePlaytime(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executePlaytime(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (target == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         int ticks = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
         long seconds = (long)ticks / 20L;
         long hours = seconds / 3600L;
         long minutes = seconds % 3600L / 60L;
         long secs = seconds % 60L;
         String name = target.getName().getString();
         String time = String.format("%dh %dm %ds", hours, minutes, secs);
         src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.playtime.result", name, time), false);
         return 1;
      }
   }

   private static void registerWhois(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("whois")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.whois")))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> executeWhois(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeWhois(CommandContext<CommandSourceStack> ctx, String name) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(name);
      if (target == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", name));
         return 0;
      } else {
         String uuid = target.getUUID().toString();
         String world = target.serverLevel().dimension().location().getPath();
         String pos = String.format("%.1f, %.1f, %.1f", target.getX(), target.getY(), target.getZ());
         String gm = target.gameMode.getGameModeForPlayer().getSerializedName();
         int ping = target.connection.latency();
         int health = (int)target.getHealth();
         int food = target.getFoodData().getFoodLevel();
         src.sendSuccess(
            () -> Component.literal(
                  "§e--- §fWhois: §b"
                     + target.getName().getString()
                     + " §e---\n§7UUID: §f"
                     + uuid
                     + "\n§7World: §f"
                     + world
                     + " §7@ §f"
                     + pos
                     + "\n§7Gamemode: §f"
                     + gm
                     + "  §7Ping: §f"
                     + ping
                     + "ms\n§7Health: §f"
                     + health
                     + "/20  §7Food: §f"
                     + food
                     + "/20"
               ),
            false
         );
         return 1;
      }
   }

   private static void registerRealname(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("realname")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.realname")))
            .then(Commands.argument("nickname", StringArgumentType.word()).executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               String nick = StringArgumentType.getString(ctx, "nickname");

               for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
                  String displayName = p.getDisplayName().getString();
                  if (displayName.equalsIgnoreCase(nick) || ChatFormatting.stripFormatting(displayName).equalsIgnoreCase(nick)) {
                     String real = p.getName().getString();
                     src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.realname.result", nick, real), false);
                     return 1;
                  }
               }

               src.sendFailure(MessageUtil.error("commands.neoessentials.realname.not_found", nick));
               return 0;
            }))
      );
   }

   private static void registerSudo(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("sudo")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.sudo")))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .then(Commands.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
                     CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                     String targetName = StringArgumentType.getString(ctx, "target");
                     String command = StringArgumentType.getString(ctx, "command");
                     ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
                     if (target == null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
                        return 0;
                     } else if (PermissionAPI.hasPermission(target.getUUID(), "neoessentials.sudo.exempt") && src.getPlayer() != null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.sudo.exempt", targetName));
                        return 0;
                     } else {
                        if (command.toLowerCase().startsWith("c:")) {
                           String chatMsg = command.substring(2);
                           src.getServer().getCommands().performPrefixedCommand(target.createCommandSourceStack(), "say " + chatMsg);
                        } else {
                           String stripped = command.startsWith("/") ? command.substring(1) : command;
                           src.getServer().getCommands().performPrefixedCommand(target.createCommandSourceStack(), stripped);
                        }

                        src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.sudo.ran", targetName, command), true);
                        LOGGER.info("{} sudoed {} to run: {}", new Object[]{src.getTextName(), targetName, command});
                        return 1;
                     }
                  }))
            )
      );
   }

   private static void registerSuicide(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("suicide")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.suicide")))
            .executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               ServerPlayer player = src.getPlayer();
               if (player == null) {
                  src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                  return 0;
               } else {
                  player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
                  String name = player.getName().getString();

                  for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
                     if (!p.getUUID().equals(player.getUUID())) {
                        p.sendSystemMessage(MessageUtil.info("commands.neoessentials.suicide.broadcast", name));
                     }
                  }

                  return 1;
               }
            })
      );
   }

   private static void registerMsgToggle(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "msgtoggle"
                        )
                        .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.msgtoggle")))
                     .executes(ctx -> executeMsgToggle(ctx, null, null)))
                  .then(Commands.literal("on").executes(ctx -> executeMsgToggle(ctx, null, false))))
               .then(Commands.literal("off").executes(ctx -> executeMsgToggle(ctx, null, true))))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.msgtoggle.others")))
                        .executes(ctx -> executeMsgToggle(ctx, StringArgumentType.getString(ctx, "target"), null)))
                     .then(Commands.literal("on").executes(ctx -> executeMsgToggle(ctx, StringArgumentType.getString(ctx, "target"), false))))
                  .then(Commands.literal("off").executes(ctx -> executeMsgToggle(ctx, StringArgumentType.getString(ctx, "target"), true)))
            )
      );
   }

   private static int executeMsgToggle(CommandContext<CommandSourceStack> ctx, String targetName, Boolean block) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (target == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         boolean cur = msgToggleBlocked.getOrDefault(target.getUUID(), false);
         boolean newBlocked = block != null ? block : !cur;
         msgToggleBlocked.put(target.getUUID(), newBlocked);
         boolean currentMsgToggle = MsgToggleManager.isMsgToggled(target);
         if (currentMsgToggle != newBlocked) {
            MsgToggleManager.toggleMsg(target);
         }

         String label = newBlocked ? "§cdisabled" : "§aenabled";
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
         if (isOther) {
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.msgtoggle.self", label));
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.msgtoggle.other", target.getName().getString(), label), false);
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.msgtoggle.self", label), false);
         }

         return 1;
      }
   }

   private static void registerRtoggle(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rtoggle")
                        .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.rtoggle")))
                     .executes(ctx -> executeRtoggle(ctx, null, null)))
                  .then(Commands.literal("on").executes(ctx -> executeRtoggle(ctx, null, true))))
               .then(Commands.literal("off").executes(ctx -> executeRtoggle(ctx, null, false))))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.rtoggle.others")))
                        .executes(ctx -> executeRtoggle(ctx, StringArgumentType.getString(ctx, "target"), null)))
                     .then(Commands.literal("on").executes(ctx -> executeRtoggle(ctx, StringArgumentType.getString(ctx, "target"), true))))
                  .then(Commands.literal("off").executes(ctx -> executeRtoggle(ctx, StringArgumentType.getString(ctx, "target"), false)))
            )
      );
   }

   private static int executeRtoggle(CommandContext<CommandSourceStack> ctx, String targetName, Boolean enable) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (target == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         boolean cur = rtoggleEnabled.getOrDefault(target.getUUID(), true);
         boolean newState = enable != null ? enable : !cur;
         rtoggleEnabled.put(target.getUUID(), newState);
         String label = newState ? "§aenabled" : "§cdisabled";
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
         if (isOther) {
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.rtoggle.self", label));
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rtoggle.other", target.getName().getString(), label), false);
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rtoggle.self", label), false);
         }

         return 1;
      }
   }

   private static void registerMotd(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("motd")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.motd")))
            .executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               String motd = ConfigManager.getInstance().getMotd();
               if (motd != null && !motd.isBlank()) {
                  String playerName = src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console";
                  String formatted = motd.replace("{player}", playerName);
                  src.sendSuccess(() -> MessageUtil.coloredText(formatted), false);
               } else {
                  src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.motd.empty"), false);
               }

               return 1;
            })
      );
   }

   private static void registerRules(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rules")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.rules")))
            .executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               String rules = ConfigManager.getInstance().getRules();
               if (rules != null && !rules.isBlank()) {
                  src.sendSuccess(() -> MessageUtil.coloredText(rules), false);
               } else {
                  src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.rules.empty"), false);
               }

               return 1;
            })
      );
   }
}
