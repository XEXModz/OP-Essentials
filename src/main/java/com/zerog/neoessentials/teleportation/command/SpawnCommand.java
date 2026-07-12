package com.zerog.neoessentials.teleportation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SpawnCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("spawn").executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();
         if (source.getEntity() instanceof ServerPlayer player) {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(source, "neoessentials.teleport.spawn");
            if (!permResult.hasPermission()) {
               source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            } else {
               ServerLevel level = player.serverLevel();
               BlockPos spawnPos = level.getSharedSpawnPos();
               TeleportLocation spawnLoc = new TeleportLocation(level, spawnPos, 0.0F, 0.0F, player.getName().getString());
               TeleportUtil.teleportPlayer(player, spawnLoc);
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.spawn.success"));
               return 1;
            }
         } else {
            source.sendFailure(MessageUtil.error("commands.neoessentials.player_only"));
            return 0;
         }
      }));
   }
}
