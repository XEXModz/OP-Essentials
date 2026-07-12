package com.zerog.neoessentials.api;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.IgnoreManager;
import com.zerog.neoessentials.chat.MsgToggleManager;
import com.zerog.neoessentials.chat.MuteManager;
import com.zerog.neoessentials.chat.SocialSpyManager;
import net.minecraft.server.level.ServerPlayer;

public class ChatAPI {
   private static ChatManager chatManager;

   public static void setChatManager(ChatManager manager) {
      chatManager = manager;
   }

   public static ChatManager getChatManager() {
      return chatManager;
   }

   public static boolean isMutedOrIgnored(ServerPlayer sender, ServerPlayer target) {
      if (MuteManager.isMuted(sender)) {
         return true;
      } else if (IgnoreManager.isIgnoring(target, sender)) {
         return true;
      } else {
         return MsgToggleManager.isMsgToggled(target)
            ? !sender.hasPermissions(4) && !PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.chat.msgtoggle.bypass")
            : false;
      }
   }

   public static void broadcastSocialSpy(ServerPlayer sender, ServerPlayer target, String message) {
      SocialSpyManager.broadcast(sender, target, message);
   }
}
