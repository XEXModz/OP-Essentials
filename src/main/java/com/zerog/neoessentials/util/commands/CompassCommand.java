package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class CompassCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("compass")) {
         registerCompassCommand(dispatcher, "compass");
         registerCompassCommand(dispatcher, "direction");
         registerCompassCommand(dispatcher, "bearing");
      }
   }

   private static void registerCompassCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.argument("player", EntityArgument.player())
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.compass.others"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                              ServerPlayer requester = CommandSourceHelper.getPlayer((CommandSourceStack)ctx.getSource());
                              showCompassInfo((CommandSourceStack)ctx.getSource(), target, requester);
                              return 1;
                           }
                        }
                     )
               ))
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.compass.player_only");
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.compass"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        showCompassInfo((CommandSourceStack)ctx.getSource(), player, player);
                        showCompassInfo((CommandSourceStack)ctx.getSource(), player, player);
                        return 1;
                     }
                  }
               }
            )
      );
   }

   private static void showCompassInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
      BlockPos pos = target.blockPosition();
      Level level = target.level();
      float yaw = target.getYRot();
      String cardinalDirection = CommandUtil.getCardinalDirection(yaw);
      String exactDirection = getExactDirection(yaw);
      double x = target.getX();
      double y = target.getY();
      double z = target.getZ();
      String worldName = CommandUtil.getWorldName(level.dimension().location().toString());
      Biome biome = (Biome)level.getBiome(pos).value();
      String biomeName = CommandUtil.getBiomeName(biome, level, pos);
      BlockPos spawnPos = level.getSharedSpawnPos();
      double distanceToSpawn = Math.sqrt(Math.pow((double)(pos.getX() - spawnPos.getX()), 2.0) + Math.pow((double)(pos.getZ() - spawnPos.getZ()), 2.0));
      if (target == requester) {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.compass.header_self"), false);
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.compass.header_other", target.getName().getString()), false);
      }

      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.facing", cardinalDirection, exactDirection), false);
      source.sendSuccess(
         () -> MessageUtil.info("commands.neoessentials.compass.coordinates", String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z)),
         false
      );
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.world", worldName), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.biome", biomeName), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.distance_to_spawn", String.format("%.1f", distanceToSpawn)), false);
      String directionToSpawn = CommandUtil.getDirectionFromOffset((double)(spawnPos.getX() - pos.getX()), (double)(spawnPos.getZ() - pos.getZ()));
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.direction_to_spawn", directionToSpawn), false);
      if (CommandUtil.isOverworld(level)) {
         double borderDistance = getDistanceToWorldBorder(level, pos);
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.world_border_distance", String.format("%.0f", borderDistance)), false);
      } else if (CommandUtil.isNether(level)) {
         int overworldX = CommandUtil.netherToOverworld(pos.getX());
         int overworldZ = CommandUtil.netherToOverworld(pos.getZ());
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.overworld_equivalent", overworldX, overworldZ), false);
      } else if (CommandUtil.isEnd(level)) {
         double distanceToCenter = Math.sqrt((double)(pos.getX() * pos.getX() + pos.getZ() * pos.getZ()));
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.compass.distance_to_center", String.format("%.1f", distanceToCenter)), false);
      }
   }

   private static String getExactDirection(float yaw) {
      yaw = (yaw % 360.0F + 360.0F) % 360.0F;
      return String.format("%.1f°", yaw);
   }

   private static double getDistanceToWorldBorder(Level level, BlockPos pos) {
      double borderSize = level.getWorldBorder().getSize() / 2.0;
      double centerX = level.getWorldBorder().getCenterX();
      double centerZ = level.getWorldBorder().getCenterZ();
      double distanceX = Math.abs((double)pos.getX() - centerX);
      double distanceZ = Math.abs((double)pos.getZ() - centerZ);
      return Math.min(borderSize - distanceX, borderSize - distanceZ);
   }
}
