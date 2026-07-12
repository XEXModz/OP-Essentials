package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestManager;
import com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestType;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldInteractionCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(WorldInteractionCommands.class);
   private static final List<String> PROJECTILE_TYPES = Arrays.asList(
      "fireball", "small", "large", "arrow", "skull", "egg", "snowball", "expbottle", "dragon", "trident", "windcharge"
   );
   private static final List<String> TREE_TYPES = Arrays.asList(
      "oak", "birch", "spruce", "jungle", "acacia", "darkoak", "mangrove", "cherry", "azalea", "bigoak", "mega_spruce", "mega_jungle"
   );

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerFireball(dispatcher);
      registerTree(dispatcher);
      registerBreak(dispatcher);
      registerIce(dispatcher);
      registerBottom(dispatcher);
      registerTpaAll(dispatcher);
      registerBroadcastWorld(dispatcher);
   }

   private static void registerFireball(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fireball")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.fireball")))
               .executes(ctx -> executeFireball(ctx, "fireball", 2.0, false)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("type", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PROJECTILE_TYPES, b))
                     .executes(ctx -> executeFireball(ctx, StringArgumentType.getString(ctx, "type"), 2.0, false)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, 10.0))
                           .executes(ctx -> executeFireball(ctx, StringArgumentType.getString(ctx, "type"), DoubleArgumentType.getDouble(ctx, "speed"), false)))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("ride")
                                 .requires(
                                    src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.fireball.ride")
                                 ))
                              .executes(
                                 ctx -> executeFireball(ctx, StringArgumentType.getString(ctx, "type"), DoubleArgumentType.getDouble(ctx, "speed"), true)
                              )
                        )
                  )
            )
      );
   }

   private static int executeFireball(CommandContext<CommandSourceStack> ctx, String type, double speed, boolean ride) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.fireball." + type)
         && !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.fireball.*")) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.fireball.no_perm_type", type));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         Vec3 eyePos = player.getEyePosition();
         Vec3 dir = player.getLookAngle().scale(speed);
         Vec3 spawnPos = eyePos.add(dir);
         String ft = type.toLowerCase();

         Entity projectile = (Entity)(switch (ft) {
            case "small" -> {
               SmallFireball fb = new SmallFireball(level, player, dir);
               fb.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               yield fb;
            }
            case "large" -> {
               LargeFireball fb = new LargeFireball(level, player, dir, 1);
               fb.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               yield fb;
            }
            case "skull" -> {
               WitherSkull sk = new WitherSkull(level, player, dir);
               sk.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               yield sk;
            }
            case "arrow" -> {
               Arrow ar = new Arrow(EntityType.ARROW, level);
               ar.setOwner(player);
               ar.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               ar.setDeltaMovement(dir);
               yield ar;
            }
            case "egg" -> {
               ThrownEgg eg = new ThrownEgg(level, player);
               eg.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               eg.setDeltaMovement(dir);
               yield eg;
            }
            case "snowball" -> {
               Snowball sb = new Snowball(level, player);
               sb.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               sb.setDeltaMovement(dir);
               yield sb;
            }
            case "expbottle" -> {
               ThrownExperienceBottle eb = new ThrownExperienceBottle(level, player);
               eb.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               eb.setDeltaMovement(dir);
               yield eb;
            }
            case "dragon" -> {
               DragonFireball df = new DragonFireball(level, player, dir);
               df.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               yield df;
            }
            case "windcharge" -> {
               WindCharge wc = (WindCharge)EntityType.WIND_CHARGE.create(level);
               if (wc != null) {
                  wc.setOwner(player);
                  wc.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
                  wc.setDeltaMovement(dir);
               }

               yield wc != null ? wc : new LargeFireball(level, player, dir, 1);
            }
            default -> {
               LargeFireball fb = new LargeFireball(level, player, dir, 1);
               fb.moveTo(spawnPos.x, spawnPos.y, spawnPos.z);
               yield fb;
            }
         });
         level.addFreshEntity(projectile);
         if (ride) {
            player.startRiding(projectile, true);
         }

         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.fireball.shot", type), false);
         return 1;
      }
   }

   private static void registerTree(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tree")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tree")))
            .then(
               Commands.argument("type", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(TREE_TYPES, b))
                  .executes(ctx -> executeTree(ctx, StringArgumentType.getString(ctx, "type")))
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bigtree")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tree")))
            .executes(ctx -> executeTree(ctx, "bigoak"))
      );
   }

   private static int executeTree(CommandContext<CommandSourceStack> ctx, String typeName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         HitResult hit = player.pick(20.0, 1.0F, false);
         BlockPos target = BlockPos.containing(hit.getLocation()).above();
         ResourceLocation treeFeatureKey = resolveTreeFeatureKey(typeName);
         if (treeFeatureKey == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.tree.unknown", typeName));
            return 0;
         } else {
            boolean placed = tryPlaceTree(level, target, treeFeatureKey);
            if (!placed) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.tree.failed"));
               return 0;
            } else {
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tree.spawned", typeName), false);
               return 1;
            }
         }
      }
   }

   private static ResourceLocation resolveTreeFeatureKey(String name) {
      String var1 = name.toLowerCase();

      return switch (var1) {
         case "oak" -> ResourceLocation.withDefaultNamespace("oak");
         case "birch" -> ResourceLocation.withDefaultNamespace("birch");
         case "spruce" -> ResourceLocation.withDefaultNamespace("spruce");
         case "jungle" -> ResourceLocation.withDefaultNamespace("jungle_tree");
         case "acacia" -> ResourceLocation.withDefaultNamespace("acacia");
         case "darkoak", "dark_oak" -> ResourceLocation.withDefaultNamespace("dark_oak");
         case "mangrove" -> ResourceLocation.withDefaultNamespace("mangrove");
         case "cherry" -> ResourceLocation.withDefaultNamespace("cherry");
         case "bigoak", "big_oak" -> ResourceLocation.withDefaultNamespace("fancy_oak");
         case "mega_spruce" -> ResourceLocation.withDefaultNamespace("mega_spruce");
         case "mega_jungle" -> ResourceLocation.withDefaultNamespace("mega_jungle_tree");
         case "azalea" -> ResourceLocation.withDefaultNamespace("azalea_tree");
         default -> null;
      };
   }

   private static boolean tryPlaceTree(ServerLevel level, BlockPos pos, ResourceLocation featureKey) {
      try {
         Optional<Registry<ConfiguredFeature<?, ?>>> registry = level.registryAccess().registry(Registries.CONFIGURED_FEATURE);
         if (registry.isEmpty()) {
            return false;
         } else {
            ConfiguredFeature<?, ? extends Feature<?>> holder = (ConfiguredFeature<?, ? extends Feature<?>>)registry.get().get(featureKey);
            return holder == null ? false : holder.place(level, level.getChunkSource().getGenerator(), level.getRandom(), pos);
         }
      } catch (Exception var5) {
         LOGGER.warn("Failed to place tree '{}': {}", featureKey, var5.getMessage());
         return false;
      }
   }

   private static void registerBreak(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("break")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.break")))
            .executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               ServerPlayer player = src.getPlayer();
               if (player == null) {
                  src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                  return 0;
               } else {
                  HitResult hit = player.pick(20.0, 1.0F, false);
                  if (hit.getType() == Type.MISS) {
                     src.sendFailure(MessageUtil.error("commands.neoessentials.break.nothing"));
                     return 0;
                  } else {
                     BlockPos bpos = BlockPos.containing(hit.getLocation());
                     ServerLevel level = player.serverLevel();
                     BlockState state = level.getBlockState(bpos);
                     if (state.isAir()) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.break.nothing"));
                        return 0;
                     } else if (state.is(Blocks.BEDROCK) && !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.break.bedrock")) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.break.bedrock"));
                        return 0;
                     } else {
                        level.destroyBlock(bpos, false, player);
                        src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.break.broken"), false);
                        return 1;
                     }
                  }
               }
            })
      );
   }

   private static void registerIce(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ice")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ice")))
               .executes(ctx -> executeIce(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ice.others")))
                  .executes(ctx -> executeIce(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeIce(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target;
      if (targetName != null) {
         target = src.getServer().getPlayerList().getPlayerByName(targetName);
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
            return 0;
         }
      } else {
         target = src.getPlayer();
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         }
      }

      target.setTicksFrozen(target.getTicksRequiredToFreeze() + 1);
      if (targetName != null) {
         target.sendSystemMessage(MessageUtil.info("commands.neoessentials.ice.frozen"));
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ice.frozen_other", target.getName().getString()), true);
      } else {
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ice.frozen"), false);
      }

      return 1;
   }

   private static void registerBottom(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bottom")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.bottom")))
            .executes(
               ctx -> {
                  CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                  ServerPlayer player = src.getPlayer();
                  if (player == null) {
                     src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                     return 0;
                  } else {
                     ServerLevel level = player.serverLevel();
                     int x = player.getBlockX();
                     int z = player.getBlockZ();
                     int minY = level.getMinBuildHeight();
                     BlockPos safePos = null;

                     for (int y = minY; y < player.getBlockY(); y++) {
                        BlockPos check = new BlockPos(x, y, z);
                        BlockPos above = check.above();
                        if (!level.getBlockState(check).isAir() && level.getBlockState(above).isAir() && level.getBlockState(above.above()).isAir()) {
                           safePos = above;
                        }
                     }

                     if (safePos == null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.bottom.no_safe"));
                        return 0;
                     } else {
                        MiscTeleportManager.getInstance().saveBackLocation(player);
                        BlockPos finalPos = safePos;
                        player.teleportTo(
                           level, (double)finalPos.getX() + 0.5, (double)finalPos.getY(), (double)finalPos.getZ() + 0.5, player.getYRot(), player.getXRot()
                        );
                        src.sendSuccess(
                           () -> MessageUtil.success(
                                 "commands.neoessentials.bottom.teleported",
                                 level.dimension().location().getPath(),
                                 finalPos.getX(),
                                 finalPos.getY(),
                                 finalPos.getZ()
                              ),
                           false
                        );
                        return 1;
                     }
                  }
               }
            )
      );
   }

   private static void registerTpaAll(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpaall")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tpaall")))
               .executes(ctx -> executeTpaAll(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tpaall.others")))
                  .executes(ctx -> executeTpaAll(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeTpaAll(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer hub = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (hub == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         int sent = 0;
         TeleportRequestManager manager = TeleportRequestManager.getInstance();

         for (ServerPlayer online : src.getServer().getPlayerList().getPlayers()) {
            if (!online.getUUID().equals(hub.getUUID()) && ItemCustomisationCommands.isTpToggleAllowed(online.getUUID())) {
               boolean success = manager.sendTeleportRequest(hub, online, TeleportRequestType.TPAHERE);
               if (success) {
                  sent++;
               }
            }
         }

         int fs = sent;
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tpaall.sent", fs), true);
         LOGGER.info("{} sent tpaall, {} requests sent", src.getTextName(), sent);
         return sent > 0 ? 1 : 0;
      }
   }

   private static void registerBroadcastWorld(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("broadcastworld")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.broadcastworld")))
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               String msg = StringArgumentType.getString(ctx, "message");
               ServerLevel targetLevel = src.getLevel();
               Component broadcast = MessageUtil.coloredText("§6[World] §e" + msg);
               int count = 0;

               for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
                  boolean sameLevel = p.serverLevel() == targetLevel;
                  if (sameLevel) {
                     p.sendSystemMessage(broadcast);
                     count++;
                  }
               }

               int fc = count;
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.broadcastworld.sent", targetLevel.dimension().location().getPath(), fc), false);
               LOGGER.info("[BroadcastWorld:{}] {}", targetLevel.dimension().location().getPath(), msg);
               return 1;
            }))
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bcastworld")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.broadcastworld")))
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
               CommandSourceStack src = (CommandSourceStack)ctx.getSource();
               src.getServer().getCommands().performPrefixedCommand(src, "broadcastworld " + StringArgumentType.getString(ctx, "message"));
               return 1;
            }))
      );
   }
}
