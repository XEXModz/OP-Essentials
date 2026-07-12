package com.zerog.neoessentials.chat;

import com.zerog.neoessentials.util.ChatDebugUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public class LastMessageManager {
   private static final Map<String, String> lastMessagerMap = new ConcurrentHashMap<>();

   public static void setLastMessager(ServerPlayer recipient, ServerPlayer sender) {
      if (recipient != null && sender != null) {
         String recipientName = recipient.getName().getString().toLowerCase();
         String senderName = sender.getName().getString().toLowerCase();
         lastMessagerMap.put(recipientName, senderName);
         ChatDebugUtil.debug("LastMessageManager - Stored: %s -> %s, Map size: %d", recipientName, senderName, lastMessagerMap.size());
         ChatDebugUtil.debug("LastMessageManager - Current map: %s", lastMessagerMap);
      }
   }

   public static ServerPlayer getLastMessager(ServerPlayer player) {
      if (player != null && player.getServer() != null) {
         String playerName = player.getName().getString().toLowerCase();
         String lastMessagerName = lastMessagerMap.get(playerName);
         ChatDebugUtil.debug("LastMessageManager - Looking up: %s -> %s", playerName, lastMessagerName);
         ChatDebugUtil.debug("LastMessageManager - Current map: %s", lastMessagerMap);
         if (lastMessagerName != null && !lastMessagerName.isEmpty()) {
            ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(lastMessagerName);
            ChatDebugUtil.debug("LastMessageManager - Player lookup for %s: %s", lastMessagerName, target != null ? "found" : "not found");
            if (target != null && target.connection != null) {
               ChatDebugUtil.debug("LastMessageManager - Returning valid target: %s", target.getName().getString());
               return target;
            } else {
               ChatDebugUtil.debug("LastMessageManager - Cleaning up offline player: %s", lastMessagerName);
               lastMessagerMap.remove(playerName);
               return null;
            }
         } else {
            ChatDebugUtil.debug("LastMessageManager - No last messager found for %s", playerName);
            return null;
         }
      } else {
         ChatDebugUtil.debug("LastMessageManager - getLastMessager called with null player or server");
         return null;
      }
   }

   public static void cleanupPlayer(ServerPlayer player) {
      if (player != null) {
         String playerName = player.getName().getString().toLowerCase();
         lastMessagerMap.remove(playerName);
         lastMessagerMap.entrySet().removeIf(entry -> entry.getValue().equals(playerName));
      }
   }

   public static boolean hasReplyTarget(ServerPlayer player) {
      return player == null ? false : getLastMessager(player) != null;
   }

   public static int getMessageMapSize() {
      return lastMessagerMap.size();
   }

   public static boolean hasMessageEntry(ServerPlayer player) {
      return player == null ? false : lastMessagerMap.containsKey(player.getName().getString().toLowerCase());
   }
}
