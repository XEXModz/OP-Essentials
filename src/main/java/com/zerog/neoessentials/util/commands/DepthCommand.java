package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class DepthCommand {
   private static final int SEA_LEVEL = 63;
   private static final int BEDROCK_LEVEL = -64;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("depth")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("depth")
                     .requires(cs -> cs.getEntity() instanceof ServerPlayer))
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.depth.others"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                 showDepthInfo((CommandSourceStack)ctx.getSource(), target, permResult.getPlayer());
                                 return 1;
                              }
                           }
                        )
                  ))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.depth"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        ServerPlayer player = permResult.getPlayer();
                        showDepthInfo((CommandSourceStack)ctx.getSource(), player, player);
                        return 1;
                     }
                  }
               )
         );
      }
   }

   private static void showDepthInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
      BlockPos pos = target.blockPosition();
      Level level = target.level();
      int currentY = pos.getY();
      int depthBelowSeaLevel = 63 - currentY;
      int heightAboveBedrock = currentY - -64;
      String layerDescription = getLayerDescription(currentY, level);
      if (target == requester) {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.depth.header_self"), false);
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.depth.header_other", target.getName().getString()), false);
      }

      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.current_y", currentY), false);
      if (depthBelowSeaLevel > 0) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.below_sea_level", depthBelowSeaLevel), false);
      } else {
         int heightAboveSeaLevel = Math.abs(depthBelowSeaLevel);
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.above_sea_level", heightAboveSeaLevel), false);
      }

      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.above_bedrock", heightAboveBedrock), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.layer_info", layerDescription), false);
      if (level.dimension() == Level.OVERWORLD) {
         if (currentY < 0) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.deepslate_region"), false);
         }

         if (currentY <= 16) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.diamond_level"), false);
         }

         if (currentY >= 90) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.cloud_level"), false);
         }
      } else if (level.dimension() == Level.NETHER) {
         if (currentY <= 31) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.nether_lava_level"), false);
         }

         if (currentY >= 100) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.depth.nether_ceiling"), false);
         }
      }
   }

   private static String getLayerDescription(int y, Level level) {
      if (level.dimension() == Level.OVERWORLD) {
         if (y >= 200) {
            return "Sky Limit";
         } else if (y >= 150) {
            return "High Altitude";
         } else if (y >= 90) {
            return "Cloud Level";
         } else if (y >= 63) {
            return "Surface Level";
         } else if (y >= 32) {
            return "Cave Level";
         } else if (y >= 0) {
            return "Deep Cave Level";
         } else if (y >= -32) {
            return "Deepslate Level";
         } else {
            return y >= -48 ? "Deep Deepslate" : "Near Bedrock";
         }
      } else if (level.dimension() == Level.NETHER) {
         if (y >= 120) {
            return "Above Nether Ceiling";
         } else if (y >= 100) {
            return "Nether Ceiling";
         } else if (y >= 64) {
            return "Upper Nether";
         } else if (y >= 32) {
            return "Mid Nether";
         } else {
            return y >= 16 ? "Lower Nether" : "Nether Floor";
         }
      } else if (level.dimension() == Level.END) {
         if (y >= 80) {
            return "High End";
         } else if (y >= 60) {
            return "End Surface";
         } else {
            return y >= 40 ? "Mid End" : "Lower End";
         }
      } else {
         return "Unknown Layer";
      }
   }
}
