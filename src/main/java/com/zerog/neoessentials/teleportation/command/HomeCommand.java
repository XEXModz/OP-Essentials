package com.zerog.neoessentials.teleportation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.teleportation.HomeManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class HomeCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("home").executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();
         if (source.getEntity() instanceof ServerPlayer player) {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(source, "neoessentials.teleport.home");
            if (!permResult.hasPermission()) {
               source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            } else {
               HomeManager.getInstance().teleportToDefaultHome(player);
               return 1;
            }
         } else {
            source.sendFailure(MessageUtil.error("commands.neoessentials.player_only"));
            return 0;
         }
      }));
   }
}
