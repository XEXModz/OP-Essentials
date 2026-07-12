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
import net.minecraft.server.level.ServerPlayer;

public class PingCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("ping")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ping")
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.ping.others"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                 ServerPlayer requester = CommandSourceHelper.getPlayer((CommandSourceStack)ctx.getSource());
                                 showPingInfo((CommandSourceStack)ctx.getSource(), target, requester);
                                 return 1;
                              }
                           }
                        )
                  ))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.ping"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        ServerPlayer player = permResult.getPlayer();
                        showPingInfo((CommandSourceStack)ctx.getSource(), player, player);
                        return 1;
                     }
                  }
               )
         );
      }
   }

   private static void showPingInfo(CommandSourceStack source, ServerPlayer target, ServerPlayer requester) {
      int ping = target.connection.latency();
      String qualityDescription = getPingQuality(ping);
      if (requester != null && target == requester) {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ping.self", ping, qualityDescription), false);
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ping.other", target.getName().getString(), ping, qualityDescription), false);
      }

      if (ping > 300) {
         source.sendSuccess(() -> MessageUtil.warning("commands.neoessentials.ping.high_warning"), false);
      } else if (ping > 150) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.ping.moderate_info"), false);
      } else if (ping < 50) {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ping.excellent_info"), false);
      }
   }

   private static String getPingQuality(int ping) {
      if (ping < 30) {
         return "Excellent";
      } else if (ping < 60) {
         return "Very Good";
      } else if (ping < 100) {
         return "Good";
      } else if (ping < 150) {
         return "Fair";
      } else if (ping < 250) {
         return "Poor";
      } else {
         return ping < 400 ? "Very Poor" : "Terrible";
      }
   }
}
