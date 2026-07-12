package com.zerog.neoessentials.chat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public class MsgToggleManager {
   private static final Set<String> toggledPlayers = ConcurrentHashMap.newKeySet();

   public static boolean toggleMsg(ServerPlayer player) {
      String name = player.getName().getString().toLowerCase();
      if (toggledPlayers.contains(name)) {
         toggledPlayers.remove(name);
         return true;
      } else {
         toggledPlayers.add(name);
         return false;
      }
   }

   public static boolean isMsgToggled(ServerPlayer player) {
      return toggledPlayers.contains(player.getName().getString().toLowerCase());
   }
}
