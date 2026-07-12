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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

public class GetPosCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("getpos")) {
         registerGetPosCommand(dispatcher, "getpos");
         registerGetPosCommand(dispatcher, "coords");
         registerGetPosCommand(dispatcher, "pos");
         registerGetPosCommand(dispatcher, "whereami");
      }
   }

   private static void registerGetPosCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.argument("player", EntityArgument.player())
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.getpos.others"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                              ServerPlayer requester = CommandSourceHelper.getPlayer((CommandSourceStack)ctx.getSource());
                              showPositionInfo((CommandSourceStack)ctx.getSource(), target, requester);
                              return 1;
                           }
                        }
                     )
               ))
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.getpos.player_only");
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.getpos"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        showPositionInfo((CommandSourceStack)ctx.getSource(), player, player);
                        return 1;
                     }
                  }
               }
            )
      );
   }

   private static void showPositionInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
      BlockPos pos = target.blockPosition();
      Level level = target.level();
      double x = target.getX();
      double y = target.getY();
      double z = target.getZ();
      String worldName = CommandUtil.getWorldName(level);
      String dimensionName = CommandUtil.getDimensionName(level);
      Biome biome = (Biome)level.getBiome(pos).value();
      String biomeName = CommandUtil.getBiomeName(biome, level, pos);
      int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
      int skyLight = level.getBrightness(LightLayer.SKY, pos);
      int totalLight = Math.max(blockLight, skyLight);
      String blockAtFeet = level.getBlockState(pos.below()).getBlock().getName().getString();
      long worldTime = level.getDayTime();
      long gameTime = level.getGameTime();
      String exactCoords = String.format("%.3f, %.3f, %.3f", x, y, z);
      String blockCoords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
      if (target == requester) {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.getpos.header_self"), false);
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.getpos.header_other", target.getName().getString()), false);
      }

      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.exact_coords", exactCoords), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.block_coords", blockCoords), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.world", worldName, dimensionName), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.biome", biomeName), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.light_levels", totalLight, blockLight, skyLight), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.block_at_feet", blockAtFeet), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.world_time", worldTime, gameTime), false);
      if (CommandUtil.isNether(level)) {
         int overworldX = CommandUtil.netherToOverworld(pos.getX());
         int overworldZ = CommandUtil.netherToOverworld(pos.getZ());
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.overworld_equiv", overworldX, overworldZ), false);
      } else if (CommandUtil.isOverworld(level)) {
         int netherX = CommandUtil.overworldToNether(pos.getX());
         int netherZ = CommandUtil.overworldToNether(pos.getZ());
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.getpos.nether_equiv", netherX, netherZ), false);
      }
   }
}
