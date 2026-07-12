package com.zerog.neoessentials.util;

import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CommandSourceHelper {
   public static boolean isPlayer(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer;
   }

   public static boolean isConsole(CommandSourceStack source) {
      return !isPlayer(source);
   }

   public static ServerPlayer getPlayer(CommandSourceStack source) {
      Entity var2 = source.getEntity();
      return var2 instanceof ServerPlayer ? (ServerPlayer)var2 : null;
   }

   public static UUID getPlayerUUID(CommandSourceStack source) {
      ServerPlayer player = getPlayer(source);
      return player != null ? player.getUUID() : null;
   }

   public static String getSenderName(CommandSourceStack source) {
      ServerPlayer player = getPlayer(source);
      return player != null ? player.getName().getString() : "Console";
   }

   public static ServerPlayer requirePlayer(CommandSourceStack source) {
      ServerPlayer player = getPlayer(source);
      if (player == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.error.player_only"));
      }

      return player;
   }

   public static ServerPlayer requirePlayer(CommandSourceStack source, String errorMessage) {
      ServerPlayer player = getPlayer(source);
      if (player == null) {
         source.sendFailure(MessageUtil.error(errorMessage));
      }

      return player;
   }
}
