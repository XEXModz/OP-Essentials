package com.zerog.neoessentials.util.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerAdminCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(ServerAdminCommands.class);
   private static final List<String> TIME_NAMES = Arrays.asList("sunrise", "day", "morning", "noon", "afternoon", "sunset", "night", "midnight");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerBroadcast(dispatcher);
      registerTime(dispatcher);
      registerWeather(dispatcher);
      registerKill(dispatcher);
      registerGamemode(dispatcher);
      registerTpo(dispatcher);
      registerTpoffline(dispatcher);
   }

   private static void registerBroadcast(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("broadcast").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.broadcast");
            }))
            .then(
               Commands.argument("message", StringArgumentType.greedyString())
                  .executes(
                     ctx -> {
                        String msg = StringArgumentType.getString(ctx, "message");
                        String senderName = ((CommandSourceStack)ctx.getSource()).getPlayer() != null
                           ? ((CommandSourceStack)ctx.getSource()).getPlayer().getName().getString()
                           : "Console";
                        Component broadcast = MessageUtil.coloredText("§6[Broadcast] §f" + msg);
                        ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(broadcast));
                        ((CommandSourceStack)ctx.getSource()).getServer().sendSystemMessage(broadcast);
                        LOGGER.info("[Broadcast] {} : {}", senderName, msg);
                        return 1;
                     }
                  )
            )
      );
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bc").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.broadcast");
      })).then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
         String msg = StringArgumentType.getString(ctx, "message");
         Component broadcast = MessageUtil.coloredText("§6[Broadcast] §f" + msg);
         ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(broadcast));
         ((CommandSourceStack)ctx.getSource()).getServer().sendSystemMessage(broadcast);
         return 1;
      })));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("announce").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.broadcast");
      })).then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
         String msg = StringArgumentType.getString(ctx, "message");
         Component broadcast = MessageUtil.coloredText("§6[Broadcast] §f" + msg);
         ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(broadcast));
         ((CommandSourceStack)ctx.getSource()).getServer().sendSystemMessage(broadcast);
         return 1;
      })));
   }

   private static void registerTime(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("time")
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time");
                        }))
                     .executes(ServerAdminCommands::executeTimeGet))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("set").requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time.set");
                        }))
                        .then(
                           Commands.argument("value", StringArgumentType.word())
                              .suggests((ctx, b) -> SharedSuggestionProvider.suggest(TIME_NAMES, b))
                              .executes(ctx -> executeTimeSet(ctx, StringArgumentType.getString(ctx, "value"), false))
                        )
                  ))
               .then(((LiteralArgumentBuilder)Commands.literal("add").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time.set");
               })).then(
                  Commands.argument("value", StringArgumentType.word()).executes(ctx -> executeTimeSet(ctx, StringArgumentType.getString(ctx, "value"), true))
               )))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("value", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(TIME_NAMES, b))
                     .requires(src -> {
                        ServerPlayer p = src.getPlayer();
                        return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time.set");
                     }))
                  .executes(ctx -> executeTimeSet(ctx, StringArgumentType.getString(ctx, "value"), false))
            )
      );
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("day").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time.set");
      })).executes(ctx -> setAllWorldsTime(ctx, 1000L, false)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("night").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.time.set");
      })).executes(ctx -> setAllWorldsTime(ctx, 13000L, false)));
   }

   private static int executeTimeGet(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerLevel level = src.getLevel();
      long time = level.getDayTime() % 24000L;
      src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.time.current", level.dimension().location().getPath(), time, ticksToName(time)), false);
      return 1;
   }

   private static int executeTimeSet(CommandContext<CommandSourceStack> ctx, String value, boolean add) {
      long ticks = parseTimeTicks(value);
      if (ticks < 0L) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.time.invalid", value));
         return 0;
      } else {
         return setAllWorldsTime(ctx, ticks, add);
      }
   }

   private static int setAllWorldsTime(CommandContext<CommandSourceStack> ctx, long ticks, boolean add) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();

      for (ServerLevel level : src.getServer().getAllLevels()) {
         if (add) {
            level.setDayTime(level.getDayTime() + ticks);
         } else {
            level.setDayTime(ticks);
         }
      }

      String op = add ? "Added" : "Set";
      src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.time.set", op, ticks, add ? "" : " (" + ticksToName(ticks) + ")"), true);
      return 1;
   }

   private static long parseTimeTicks(String value) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.StackOverflowError
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent.lambda$toJava$0(VarExprent.java:167)
      //
      // Bytecode:
      // 000: aload 0
      // 001: invokevirtual java/lang/String.toLowerCase ()Ljava/lang/String;
      // 004: astore 1
      // 005: bipush -1
      // 006: istore 2
      // 007: aload 1
      // 008: invokevirtual java/lang/String.hashCode ()I
      // 00b: lookupswitch 207 9 -1856560363 81 -1640863024 195 -891172202 151 99228 95 3095209 180 3387232 123 104817688 165 1020028732 137 1240152004 109
      // 05c: aload 1
      // 05d: ldc "sunrise"
      // 05f: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 062: ifeq 0da
      // 065: bipush 0
      // 066: istore 2
      // 067: goto 0da
      // 06a: aload 1
      // 06b: ldc "day"
      // 06d: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 070: ifeq 0da
      // 073: bipush 1
      // 074: istore 2
      // 075: goto 0da
      // 078: aload 1
      // 079: ldc "morning"
      // 07b: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 07e: ifeq 0da
      // 081: bipush 2
      // 082: istore 2
      // 083: goto 0da
      // 086: aload 1
      // 087: ldc "noon"
      // 089: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 08c: ifeq 0da
      // 08f: bipush 3
      // 090: istore 2
      // 091: goto 0da
      // 094: aload 1
      // 095: ldc "afternoon"
      // 097: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 09a: ifeq 0da
      // 09d: bipush 4
      // 09e: istore 2
      // 09f: goto 0da
      // 0a2: aload 1
      // 0a3: ldc "sunset"
      // 0a5: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 0a8: ifeq 0da
      // 0ab: bipush 5
      // 0ac: istore 2
      // 0ad: goto 0da
      // 0b0: aload 1
      // 0b1: ldc "night"
      // 0b3: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 0b6: ifeq 0da
      // 0b9: bipush 6
      // 0bb: istore 2
      // 0bc: goto 0da
      // 0bf: aload 1
      // 0c0: ldc "dusk"
      // 0c2: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 0c5: ifeq 0da
      // 0c8: bipush 7
      // 0ca: istore 2
      // 0cb: goto 0da
      // 0ce: aload 1
      // 0cf: ldc "midnight"
      // 0d1: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 0d4: ifeq 0da
      // 0d7: bipush 8
      // 0d9: istore 2
      // 0da: iload 2
      // 0db: tableswitch 105 0 8 49 57 57 65 73 81 89 89 97
      // 10c: ldc2_w 23000
      // 10f: lstore 3
      // 110: lload 3
      // 111: goto 157
      // 114: ldc2_w 1000
      // 117: lstore 3
      // 118: lload 3
      // 119: goto 157
      // 11c: ldc2_w 6000
      // 11f: lstore 3
      // 120: lload 3
      // 121: goto 157
      // 124: ldc2_w 9000
      // 127: lstore 3
      // 128: lload 3
      // 129: goto 157
      // 12c: ldc2_w 12000
      // 12f: lstore 3
      // 130: lload 3
      // 131: goto 157
      // 134: ldc2_w 13000
      // 137: lstore 3
      // 138: lload 3
      // 139: goto 157
      // 13c: ldc2_w 18000
      // 13f: lstore 3
      // 140: lload 3
      // 141: goto 157
      // 144: aload 0
      // 145: invokestatic java/lang/Long.parseLong (Ljava/lang/String;)J
      // 148: lstore 3
      // 149: lload 3
      // 14a: goto 157
      // 14d: astore 5
      // 14f: ldc2_w -1
      // 152: lstore 3
      // 153: lload 3
      // 154: goto 157
      // 157: lreturn
   }

   private static String ticksToName(long ticks) {
      long t = ticks % 24000L;
      if (t < 1500L) {
         return "sunrise";
      } else if (t < 6000L) {
         return "day";
      } else if (t < 9000L) {
         return "noon";
      } else if (t < 12000L) {
         return "afternoon";
      } else if (t < 13800L) {
         return "sunset";
      } else {
         return t < 18000L ? "night" : "midnight";
      }
   }

   private static void registerWeather(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "weather"
                           )
                           .requires(src -> {
                              ServerPlayer p = src.getPlayer();
                              return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.weather");
                           }))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("sun").executes(ctx -> executeWeather(ctx, "sun", 0)))
                              .then(
                                 Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                    .executes(ctx -> executeWeather(ctx, "sun", IntegerArgumentType.getInteger(ctx, "duration")))
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("clear").executes(ctx -> executeWeather(ctx, "sun", 0)))
                           .then(
                              Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                 .executes(ctx -> executeWeather(ctx, "sun", IntegerArgumentType.getInteger(ctx, "duration")))
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("rain").executes(ctx -> executeWeather(ctx, "storm", 0)))
                        .then(
                           Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                              .executes(ctx -> executeWeather(ctx, "storm", IntegerArgumentType.getInteger(ctx, "duration")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("storm").executes(ctx -> executeWeather(ctx, "storm", 0)))
                     .then(
                        Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                           .executes(ctx -> executeWeather(ctx, "storm", IntegerArgumentType.getInteger(ctx, "duration")))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("thunder").executes(ctx -> executeWeather(ctx, "thunder", 0)))
                  .then(
                     Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                        .executes(ctx -> executeWeather(ctx, "thunder", IntegerArgumentType.getInteger(ctx, "duration")))
                  )
            )
      );
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("sun").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.weather");
      })).executes(ctx -> executeWeather(ctx, "sun", 0)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("storm").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.weather");
      })).executes(ctx -> executeWeather(ctx, "storm", 0)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("thunder").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.weather");
      })).executes(ctx -> executeWeather(ctx, "thunder", 0)));
   }

   private static int executeWeather(CommandContext<CommandSourceStack> ctx, String type, int durationSeconds) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();

      for (ServerLevel level : src.getServer().getAllLevels()) {
         if (level.dimensionType().hasSkyLight()) {
            int ticks = durationSeconds > 0 ? durationSeconds * 20 : 6000;
            switch (type) {
               case "sun":
                  level.setWeatherParameters(ticks, 0, false, false);
                  break;
               case "storm":
                  level.setWeatherParameters(0, ticks, true, false);
                  break;
               case "thunder":
                  level.setWeatherParameters(0, ticks, true, true);
            }
         }
      }

      String label = durationSeconds > 0 ? type + " for " + durationSeconds + "s" : type;
      src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.weather.set", label), true);
      LOGGER.info("{} set weather to {}", src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console", label);
      return 1;
   }

   private static void registerKill(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kill").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kill");
            }))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> executeKill(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeKill(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
      if (target == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         return 0;
      } else if (PermissionAPI.hasPermission(target.getUUID(), "neoessentials.kill.exempt")
         && src.getPlayer() != null
         && !PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.kill.force")) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.kill.exempt", targetName));
         return 0;
      } else {
         target.hurt(target.damageSources().genericKill(), Float.MAX_VALUE);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kill.success", targetName), true);
         LOGGER.info("{} killed {}", src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console", targetName);
         return 1;
      }
   }

   private static void registerGamemode(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                       "gamemode"
                                    )
                                    .requires(src -> {
                                       ServerPlayer p = src.getPlayer();
                                       return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.gamemode");
                                    }))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("survival").executes(ctx -> executeGamemode(ctx, GameType.SURVIVAL, null)))
                                       .then(
                                          ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                                                .suggests(
                                                   (ctx, b) -> SharedSuggestionProvider.suggest(
                                                         ((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b
                                                      )
                                                )
                                                .requires(
                                                   src -> src.getPlayer() == null
                                                         || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.gamemode.others")
                                                ))
                                             .executes(ctx -> executeGamemode(ctx, GameType.SURVIVAL, StringArgumentType.getString(ctx, "target")))
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("creative").executes(ctx -> executeGamemode(ctx, GameType.CREATIVE, null)))
                                    .then(
                                       ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                                             .suggests(
                                                (ctx, b) -> SharedSuggestionProvider.suggest(
                                                      ((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b
                                                   )
                                             )
                                             .requires(
                                                src -> src.getPlayer() == null
                                                      || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.gamemode.others")
                                             ))
                                          .executes(ctx -> executeGamemode(ctx, GameType.CREATIVE, StringArgumentType.getString(ctx, "target")))
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("adventure").executes(ctx -> executeGamemode(ctx, GameType.ADVENTURE, null)))
                                 .then(
                                    ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                                          .suggests(
                                             (ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b)
                                          )
                                          .requires(
                                             src -> src.getPlayer() == null
                                                   || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.gamemode.others")
                                          ))
                                       .executes(ctx -> executeGamemode(ctx, GameType.ADVENTURE, StringArgumentType.getString(ctx, "target")))
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("spectator").executes(ctx -> executeGamemode(ctx, GameType.SPECTATOR, null)))
                              .then(
                                 ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                                       .suggests(
                                          (ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b)
                                       )
                                       .requires(
                                          src -> src.getPlayer() == null
                                                || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.gamemode.others")
                                       ))
                                    .executes(ctx -> executeGamemode(ctx, GameType.SPECTATOR, StringArgumentType.getString(ctx, "target")))
                              )
                        ))
                     .then(Commands.literal("0").executes(ctx -> executeGamemode(ctx, GameType.SURVIVAL, null))))
                  .then(Commands.literal("1").executes(ctx -> executeGamemode(ctx, GameType.CREATIVE, null))))
               .then(Commands.literal("2").executes(ctx -> executeGamemode(ctx, GameType.ADVENTURE, null))))
            .then(Commands.literal("3").executes(ctx -> executeGamemode(ctx, GameType.SPECTATOR, null)))
      );
   }

   private static int executeGamemode(CommandContext<CommandSourceStack> ctx, GameType mode, String targetName) {
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
         target.setGameMode(mode);
         String modeName = mode.getName();
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
         if (isOther) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.gamemode.other", target.getName().getString(), modeName), true);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.gamemode.self", modeName));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.gamemode.self", modeName), false);
         }

         return 1;
      }
   }

   private static void registerTpo(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpo").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tpo");
            }))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> {
                     CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                     ServerPlayer self = src.getPlayer();
                     if (self == null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                        return 0;
                     } else {
                        String name = StringArgumentType.getString(ctx, "target");
                        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(name);
                        if (target == null) {
                           src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", name));
                           return 0;
                        } else {
                           self.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                           src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.tpo.success", name), false);
                           return 1;
                        }
                     }
                  })
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpohere").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tpohere");
            }))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> {
                     CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                     ServerPlayer self = src.getPlayer();
                     if (self == null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                        return 0;
                     } else {
                        String name = StringArgumentType.getString(ctx, "target");
                        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(name);
                        if (target == null) {
                           src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", name));
                           return 0;
                        } else {
                           target.teleportTo(self.serverLevel(), self.getX(), self.getY(), self.getZ(), self.getYRot(), self.getXRot());
                           src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.tpohere.success", name), true);
                           target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.tpohere.notify", self.getName().getString()));
                           return 1;
                        }
                     }
                  })
            )
      );
   }

   private static void registerTpoffline(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpoffline").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tpoffline");
            }))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .executes(
                     ctx -> {
                        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                        ServerPlayer self = src.getPlayer();
                        if (self == null) {
                           src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                           return 0;
                        } else {
                           String name = StringArgumentType.getString(ctx, "player");
                           ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
                           if (online != null) {
                              self.teleportTo(online.serverLevel(), online.getX(), online.getY(), online.getZ(), online.getYRot(), online.getXRot());
                              src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.tpoffline.online", name), false);
                              return 1;
                           } else {
                              GameProfileCache userCache = src.getServer().getProfileCache();
                              Optional<GameProfile> profile = userCache != null ? userCache.get(name) : Optional.empty();
                              if (profile.isEmpty()) {
                                 src.sendFailure(MessageUtil.error("commands.neoessentials.teleport.tpoffline.not_found", name));
                                 return 0;
                              } else {
                                 UUID uuid = profile.get().getId();
                                 CompoundTag tag = loadOfflinePlayerData(src.getServer(), uuid);
                                 if (tag != null && tag.contains("Pos")) {
                                    ListTag pos = tag.getList("Pos", 6);
                                    double x = pos.getDouble(0);
                                    double y = pos.getDouble(1);
                                    double z = pos.getDouble(2);
                                    ListTag rot = tag.getList("Rotation", 5);
                                    float yaw = !rot.isEmpty() ? rot.getFloat(0) : 0.0F;
                                    float pitch = rot.size() > 1 ? rot.getFloat(1) : 0.0F;
                                    ResourceLocation dimKey = tag.contains("Dimension") ? ResourceLocation.tryParse(tag.getString("Dimension")) : null;
                                    ServerLevel level = dimKey != null
                                       ? StreamSupport.<ServerLevel>stream(src.getServer().getAllLevels().spliterator(), false)
                                          .filter(l -> l.dimension().location().equals(dimKey))
                                          .findFirst()
                                          .orElse(src.getServer().overworld())
                                       : src.getServer().overworld();
                                    self.teleportTo(level, x, y, z, yaw, pitch);
                                    src.sendSuccess(
                                       () -> MessageUtil.success(
                                             "commands.neoessentials.teleport.tpoffline.success", name, String.format("%.1f, %.1f, %.1f", x, y, z)
                                          ),
                                       false
                                    );
                                    return 1;
                                 } else {
                                    src.sendFailure(MessageUtil.error("commands.neoessentials.teleport.tpoffline.no_data", name));
                                    return 0;
                                 }
                              }
                           }
                        }
                     }
                  )
            )
      );
   }

   private static CompoundTag loadOfflinePlayerData(MinecraftServer server, UUID uuid) {
      try {
         File playerDataDir = new File(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile(), "");
         File playerFile = new File(playerDataDir, uuid + ".dat");
         return !playerFile.exists() ? null : NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
      } catch (Exception var4) {
         return null;
      }
   }

   public static void registerWorldCommands(CommandDispatcher<CommandSourceStack> d) {
      registerWorld(d);
      registerSpawner(d);
      registerRecipe(d);
   }

   private static void registerWorld(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("world")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.world")))
               .executes(ctx -> {
                  CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                  List<String> worlds = new ArrayList<>();

                  for (ServerLevel level : src.getServer().getAllLevels()) {
                     worlds.add(level.dimension().location().toString());
                  }

                  src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.world.list", String.join(", ", worlds)), false);
                  return 1;
               }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("dimension", StringArgumentType.word())
                     .suggests(
                        (ctx, b) -> SharedSuggestionProvider.suggest(
                              StreamSupport.<ServerLevel>stream(((CommandSourceStack)ctx.getSource()).getServer().getAllLevels().spliterator(), false)
                                 .map(l -> l.dimension().location().getPath())
                                 .collect(Collectors.toList()),
                              b
                           )
                     )
                     .executes(ctx -> executeWorld(ctx, StringArgumentType.getString(ctx, "dimension"), null)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.world.others")))
                        .executes(ctx -> executeWorld(ctx, StringArgumentType.getString(ctx, "dimension"), StringArgumentType.getString(ctx, "target")))
                  )
            )
      );
   }

   private static int executeWorld(CommandContext<CommandSourceStack> ctx, String dimName, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (player == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         ServerLevel target = null;

         for (ServerLevel level : src.getServer().getAllLevels()) {
            ResourceLocation key = level.dimension().location();
            if (key.getPath().equalsIgnoreCase(dimName) || key.toString().equalsIgnoreCase(dimName)) {
               target = level;
               break;
            }
         }

         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.world.not_found", dimName));
            return 0;
         } else {
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, (double)spawn.getX() + 0.5, (double)spawn.getY(), (double)spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.world.teleported", player.getName().getString(), dimName), false);
            return 1;
         }
      }
   }

   private static void registerSpawner(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("spawner")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.spawner")))
            .then(
               Commands.argument("mob", StringArgumentType.word())
                  .suggests(
                     (ctx, b) -> SharedSuggestionProvider.suggest(
                           BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::getPath).collect(Collectors.toList()), b
                        )
                  )
                  .executes(ctx -> executeSpawner(ctx, StringArgumentType.getString(ctx, "mob")))
            )
      );
   }

   private static int executeSpawner(CommandContext<CommandSourceStack> ctx, String mobName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.spawner." + mobName.toLowerCase())
         && !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.spawner.*")) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.spawner.no_perm_mob", mobName));
         return 0;
      } else {
         String id = mobName.contains(":") ? mobName : "minecraft:" + mobName;
         ResourceLocation loc = ResourceLocation.tryParse(id);
         Optional<EntityType<?>> typeOpt = loc != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(loc) : Optional.empty();
         if (typeOpt.isEmpty()) {
            typeOpt = BuiltInRegistries.ENTITY_TYPE
               .entrySet()
               .stream()
               .filter(e -> ((ResourceKey)e.getKey()).location().getPath().equalsIgnoreCase(mobName))
               .map(Entry::getValue)
               .findFirst();
         }

         if (typeOpt.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.spawnmob.unknown", mobName));
            return 0;
         } else {
            HitResult hit = player.pick(6.0, 1.0F, false);
            BlockPos bpos = BlockPos.containing(hit.getLocation());
            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(bpos);
            if (state.is(Blocks.SPAWNER) && level.getBlockEntity(bpos) instanceof SpawnerBlockEntity spawnerBE) {
               spawnerBE.setEntityId(typeOpt.get(), level.getRandom());
               level.sendBlockUpdated(bpos, state, state, 3);
               spawnerBE.setChanged();
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.spawner.changed", mobName), true);
               LOGGER.info("{} changed spawner at {} to {}", new Object[]{player.getName().getString(), bpos, mobName});
               return 1;
            } else {
               src.sendFailure(MessageUtil.error("commands.neoessentials.spawner.not_looking_at_spawner"));
               return 0;
            }
         }
      }
   }

   private static void registerRecipe(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("recipe")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.recipe")))
               .executes(ctx -> executeRecipe(ctx, null)))
            .then(
               Commands.argument("item", StringArgumentType.word())
                  .suggests(
                     (ctx, b) -> SharedSuggestionProvider.suggest(
                           BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::getPath).collect(Collectors.toList()), b
                        )
                  )
                  .executes(ctx -> executeRecipe(ctx, StringArgumentType.getString(ctx, "item")))
            )
      );
   }

   private static int executeRecipe(CommandContext<CommandSourceStack> ctx, String itemName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         Item item = null;
         if (itemName == null) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.recipe.no_item"));
               return 0;
            }

            item = held.getItem();
         } else {
            String id = itemName.contains(":") ? itemName : "minecraft:" + itemName;
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc != null) {
               item = (Item)BuiltInRegistries.ITEM.get(loc);
            }

            if (item == null || item == Items.AIR) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.recipe.unknown_item", itemName));
               return 0;
            }
         }

         Item finalItem = item;
         List<RecipeHolder<?>> matching = new ArrayList<>();

         for (RecipeHolder<?> holder : src.getServer().getRecipeManager().getRecipes()) {
            try {
               if (holder.value().getResultItem(src.getServer().registryAccess()).getItem() == finalItem) {
                  matching.add(holder);
               }
            } catch (Exception var10) {
            }
         }

         if (matching.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.recipe.no_recipe", finalItem.getDescriptionId()));
            return 0;
         } else {
            player.awardRecipes(matching);
            int fc = matching.size();
            String desc = BuiltInRegistries.ITEM.getKey(finalItem).getPath();
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.recipe.unlocked", fc, desc), false);
            return 1;
         }
      }
   }
}
