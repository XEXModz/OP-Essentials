package com.zerog.neoessentials.teleportation.DirectTeleport;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class DirectTeleportCommands {

   // FEATURE 1.1.10 (Spock): admin TP commands broadcast "[X: Teleported ...]"
   // to all ops via sendSuccess(broadcast=true). When the executing staffer is
   // VANISHED, suppress that broadcast so vanish is truly silent. Visible staff
   // behave exactly as before.
   private static boolean shouldBroadcast(net.minecraft.commands.CommandSourceStack source) {
      try {
         net.minecraft.server.level.ServerPlayer p = source.getPlayer();
         if (p != null && com.zerog.neoessentials.moderation.VanishManager.getInstance().isPlayerVanished(p.getUUID())) {
            return false;
         }
      } catch (Exception ignored) {
      }
      return true;
   }

   private static final String PERMISSION_TP = "neoessentials.teleport.tp";
   private static final String PERMISSION_TPHERE = "neoessentials.teleport.tphere";
   private static final String PERMISSION_TPPOS = "neoessentials.teleport.tppos";
   private static final String PERMISSION_TOP = "neoessentials.teleport.top";
   private static final String PERMISSION_TPR = "neoessentials.teleport.tpr";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled() && config.isCommandEnabled("tp")) {
         registerTpCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("tphere")) {
         registerTphereCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("tpall")) {
         registerTpallCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("tppos")) {
         registerTpposCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("top")) {
         registerTopCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("jumpto")) {
         registerJumptoCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("jump")) {
         registerJumpCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("tpr")) {
         registerTprCommand(dispatcher);
      }

      if (config.isTeleportationEnabled() && config.isCommandEnabled("tpo")) {
         registerTpoCommand(dispatcher);
      }
   }

   private static void registerTpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tp")
                     .requires(
                        source -> source.getEntity() instanceof ServerPlayer p
                              ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tp")
                              : source.hasPermission(2)
                     ))
                  .then(
                     Commands.argument("target", EntityArgument.player())
                        .executes(
                           ctx -> teleportToPlayer(ctx, ((CommandSourceStack)ctx.getSource()).getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"))
                        )
                  ))
               .then(
                  Commands.argument("player", EntityArgument.player())
                     .then(
                        Commands.argument("target", EntityArgument.player())
                           .executes(ctx -> teleportToPlayer(ctx, EntityArgument.getPlayer(ctx, "player"), EntityArgument.getPlayer(ctx, "target")))
                     )
               ))
            .then(
               Commands.argument("x", DoubleArgumentType.doubleArg())
                  .then(
                     Commands.argument("y", DoubleArgumentType.doubleArg())
                        .then(
                           Commands.argument("z", DoubleArgumentType.doubleArg())
                              .executes(
                                 ctx -> teleportToCoordinates(
                                       ctx,
                                       ((CommandSourceStack)ctx.getSource()).getPlayerOrException(),
                                       DoubleArgumentType.getDouble(ctx, "x"),
                                       DoubleArgumentType.getDouble(ctx, "y"),
                                       DoubleArgumentType.getDouble(ctx, "z")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static void registerTphereCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tphere")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tphere")
                        : source.hasPermission(2)
               ))
            .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> teleportPlayerHere(ctx, EntityArgument.getPlayer(ctx, "player"))))
      );
   }

   private static void registerTpallCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpall")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.admin.tpall")
                        : source.hasPermission(2)
               ))
            .executes(DirectTeleportCommands::teleportAllPlayers)
      );
   }

   private static void registerTpposCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tppos")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tppos")
                        : source.hasPermission(2)
               ))
            .then(Commands.argument("coordinates", Vec3Argument.vec3()).executes(ctx -> {
               try {
                  ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                  Coordinates coords = Vec3Argument.getCoordinates(ctx, "coordinates");
                  Vec3 pos = coords.getPosition((CommandSourceStack)ctx.getSource());
                  return teleportToCoordinates(ctx, player, pos.x, pos.y, pos.z);
               } catch (CommandSyntaxException var4) {
                  ((CommandSourceStack)ctx.getSource())
                     .sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed_coords", var4.getMessage()));
                  return 0;
               }
            }))
      );
   }

   private static void registerTopCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("top")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.top")
                        : source.hasPermission(0)
               ))
            .executes(DirectTeleportCommands::teleportToTop)
      );
   }

   private static void registerJumptoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jumpto")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.jumpto")
                        : source.hasPermission(2)
               ))
            .executes(DirectTeleportCommands::jumpToTargetBlock)
      );
   }

   private static void registerJumpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jump")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.jump")
                        : source.hasPermission(2)
               ))
            .executes(DirectTeleportCommands::jumpToTargetBlock)
      );
   }

   private static void registerTprCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (String alias : new String[]{"tpr", "rtp", "randomtp", "randomteleport"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias)
                     .requires(
                        source -> source.getEntity() instanceof ServerPlayer p
                              ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.tpr")
                              : source.hasPermission(0)
                     ))
                  .executes(ctx -> randomTeleport(ctx, "")))
               .then(
                  Commands.argument("locationName", StringArgumentType.word())
                     .executes(ctx -> randomTeleport(ctx, StringArgumentType.getString(ctx, "locationName")))
               )
         );
      }

      for (String root : new String[]{"neoe", "neoessentials"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(root)
                        .then(Commands.literal("tpr").executes(ctx -> randomTeleport(ctx, ""))))
                     .then(Commands.literal("rtp").executes(ctx -> randomTeleport(ctx, ""))))
                  .then(Commands.literal("randomtp").executes(ctx -> randomTeleport(ctx, ""))))
               .then(Commands.literal("randomteleport").executes(ctx -> randomTeleport(ctx, "")))
         );
      }

      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("settpr")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.settpr")
                        : source.hasPermission(2)
               ))
            .then(Commands.argument("locationName", StringArgumentType.word()).executes(DirectTeleportCommands::setTprLocation))
      );
   }

   private static void registerTpoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpo")
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer p
                        ? PermissionAPI.hasPermission(p.getUUID(), "neoessentials.teleport.admin.tpo")
                        : source.hasPermission(2)
               ))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .executes(ctx -> teleportToOfflinePlayer(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
   }

   private static int teleportToPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ServerPlayer target) {
      try {
         if (player == target) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.self"));
            return 0;
         } else {
            player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(
                  () -> MessageUtil.success(
                        "commands.neoessentials.teleport.admin.teleported_player", player.getName().getString(), target.getName().getString()
                     ),
                  shouldBroadcast((net.minecraft.commands.CommandSourceStack) ctx.getSource())
               );
            return 1;
         }
      } catch (Exception var4) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed", var4.getMessage()));
         return 0;
      }
   }

   private static int teleportToCoordinates(CommandContext<CommandSourceStack> ctx, ServerPlayer player, double x, double y, double z) {
      try {
         player.teleportTo(player.serverLevel(), x, y, z, player.getYRot(), player.getXRot());
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> MessageUtil.success(
                     "commands.neoessentials.teleport.admin.teleported_player_coords",
                     player.getName().getString(),
                     String.valueOf((int)x),
                     String.valueOf((int)y),
                     String.valueOf((int)z)
                  ),
               shouldBroadcast((net.minecraft.commands.CommandSourceStack) ctx.getSource())
            );
         return 1;
      } catch (Exception var9) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed_coords", var9.getMessage()));
         return 0;
      }
   }

   private static int teleportPlayerHere(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         target.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), target.getYRot(), target.getXRot());
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.admin.teleported_to", target.getName().getString()), shouldBroadcast((net.minecraft.commands.CommandSourceStack) ctx.getSource()));
         target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.admin.player_teleported_to_you", player.getName().getString()));
         return 1;
      } catch (Exception var3) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed", var3.getMessage()));
         return 0;
      }
   }

   private static int teleportAllPlayers(CommandContext<CommandSourceStack> ctx) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         MinecraftServer server = player.getServer();
         if (server == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed", "Server not available"));
            return 0;
         } else {
            Collection<ServerPlayer> players = server.getPlayerList().getPlayers();
            int count = 0;

            for (ServerPlayer target : players) {
               if (target != player) {
                  target.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), target.getYRot(), target.getXRot());
                  count++;
               }
            }

            if (count == 0) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.tpall.no_players"));
               return 0;
            } else {
               int finalCount = count;
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(
                     () -> MessageUtil.success(
                           "commands.neoessentials.teleport.admin.tpall.teleported", String.valueOf(finalCount), player.getName().getString()
                        ),
                     shouldBroadcast((net.minecraft.commands.CommandSourceStack) ctx.getSource())
                  );
               return count;
            }
         }
      } catch (Exception var7) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.failed", var7.getMessage()));
         return 0;
      }
   }

   private static int teleportToTop(CommandContext<CommandSourceStack> ctx) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         BlockPos currentPos = player.blockPosition();
         ServerLevel level = player.serverLevel();
         BlockPos highestPos = null;

         for (int y = level.getMaxBuildHeight() - 1; y > currentPos.getY(); y--) {
            BlockPos checkPos = new BlockPos(currentPos.getX(), y, currentPos.getZ());
            if (!level.getBlockState(checkPos).isAir() && level.getBlockState(checkPos.above()).isAir()) {
               highestPos = checkPos.above();
               break;
            }
         }

         if (highestPos == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.no_solid_block"));
            return 0;
         } else {
            player.teleportTo(
               level, (double)highestPos.getX() + 0.5, (double)highestPos.getY(), (double)highestPos.getZ() + 0.5, player.getYRot(), player.getXRot()
            );
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.misc.top_success"), false);
            return 1;
         }
      } catch (Exception var7) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.top_failed", var7.getMessage()));
         return 0;
      }
   }

   private static int jumpToTargetBlock(CommandContext<CommandSourceStack> ctx) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         ServerLevel level = player.serverLevel();
         Vec3 start = player.getEyePosition();
         Vec3 end = start.add(player.getLookAngle().scale(100.0));
         BlockHitResult hit = level.clip(new ClipContext(start, end, Block.OUTLINE, Fluid.NONE, player));
         if (hit.getType() == Type.MISS) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.no_block_in_sight"));
            return 0;
         } else {
            BlockPos teleportPos = hit.getBlockPos().above();
            if (level.getBlockState(teleportPos).isAir() && level.getBlockState(teleportPos.above()).isAir()) {
               player.teleportTo(
                  level, (double)teleportPos.getX() + 0.5, (double)teleportPos.getY(), (double)teleportPos.getZ() + 0.5, player.getYRot(), player.getXRot()
               );
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.misc.jumpto_success"), false);
               return 1;
            } else {
               ((CommandSourceStack)ctx.getSource())
                  .sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.jumpto_failed", "Target location unsafe"));
               return 0;
            }
         }
      } catch (Exception var7) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.jumpto_failed", var7.getMessage()));
         return 0;
      }
   }

   private static int randomTeleport(CommandContext<CommandSourceStack> ctx, String locationName) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         RandomTeleportManager.getInstance().randomTeleport(player, locationName);
         return 1;
      } catch (CommandSyntaxException var3) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_failed", var3.getMessage()));
         return 0;
      } catch (Exception var4) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_failed", var4.getMessage()));
         return 0;
      }
   }

   private static int setTprLocation(CommandContext<CommandSourceStack> ctx) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         String name = StringArgumentType.getString(ctx, "locationName");
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         JsonObject teleportation = config.has("teleportation") ? config.getAsJsonObject("teleportation") : new JsonObject();
         JsonObject tprSettings = teleportation.has("randomTeleportSettings") ? teleportation.getAsJsonObject("randomTeleportSettings") : new JsonObject();
         JsonObject locations = tprSettings.has("locations") ? tprSettings.getAsJsonObject("locations") : new JsonObject();
         JsonObject locEntry = locations.has(name) ? locations.getAsJsonObject(name) : new JsonObject();
         JsonObject center = new JsonObject();
         center.addProperty("x", player.getX());
         center.addProperty("y", player.getY());
         center.addProperty("z", player.getZ());
         center.addProperty("world", player.level().dimension().location().toString());
         locEntry.add("center", center);
         if (!locEntry.has("minRange")) {
            locEntry.addProperty("minRange", 0);
         }

         if (!locEntry.has("maxRange")) {
            locEntry.addProperty("maxRange", 10000);
         }

         locations.add(name, locEntry);
         tprSettings.add("locations", locations);
         teleportation.add("randomTeleportSettings", tprSettings);
         config.add("teleportation", teleportation);
         ConfigManager.getInstance().saveConfig("config.json", config);
         RandomTeleportManager.getInstance().clearCache(name);
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> MessageUtil.success(
                     "commands.neoessentials.teleport.misc.settpr_success",
                     name,
                     String.format("%.1f", player.getX()),
                     String.format("%.1f", player.getY()),
                     String.format("%.1f", player.getZ())
                  ),
               shouldBroadcast((net.minecraft.commands.CommandSourceStack) ctx.getSource())
            );
         return 1;
      } catch (CommandSyntaxException var9) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.player_only"));
         return 0;
      } catch (Exception var10) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.misc.settpr_failed", var10.getMessage()));
         return 0;
      }
   }

   private static int teleportToOfflinePlayer(CommandContext<CommandSourceStack> ctx, String playerName) {
      try {
         ServerPlayer executor = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         boolean success = DirectTeleportManager.getInstance().teleportToOfflinePlayer(executor, playerName);
         return success ? 1 : 0;
      } catch (CommandSyntaxException var4) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.player_only"));
         return 0;
      } catch (Exception var5) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.admin.tpo_failed", var5.getMessage()));
         return 0;
      }
   }
}
