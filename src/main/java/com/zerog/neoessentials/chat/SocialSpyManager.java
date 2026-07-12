package com.zerog.neoessentials.chat;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public class SocialSpyManager {
   private static final Set<String> socialSpyPlayers = ConcurrentHashMap.newKeySet();

   public static void toggleSocialSpy(ServerPlayer player) {
      String name = player.getName().getString().toLowerCase();
      if (socialSpyPlayers.contains(name)) {
         socialSpyPlayers.remove(name);
      } else {
         socialSpyPlayers.add(name);
      }
   }

   public static boolean hasSocialSpy(ServerPlayer player) {
      return socialSpyPlayers.contains(player.getName().getString().toLowerCase());
   }

   public static void broadcast(ServerPlayer sender, ServerPlayer target, String message) {
      if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.socialspy.exempt")) {
         for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
            if (hasSocialSpy(player) && !player.equals(sender) && !player.equals(target)) {
               if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat.socialspy")) {
                  player.sendSystemMessage(
                     MessageUtil.component("neoessentials.socialspy.format", sender.getName().getString(), target.getName().getString(), message)
                  );
               } else {
                  socialSpyPlayers.remove(player.getName().getString().toLowerCase());
               }
            }
         }
      }
   }
}
