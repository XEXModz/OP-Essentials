package com.zerog.neoessentials.chat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public class IgnoreManager {
   private static final Map<String, Set<String>> ignoreMap = new ConcurrentHashMap<>();

   public static void ignore(ServerPlayer player, String targetName) {
      String playerName = player.getName().getString().toLowerCase();
      ignoreMap.computeIfAbsent(playerName, k -> ConcurrentHashMap.newKeySet()).add(targetName.toLowerCase());
   }

   public static void unignore(ServerPlayer player, String targetName) {
      String playerName = player.getName().getString().toLowerCase();
      Set<String> ignored = ignoreMap.get(playerName);
      if (ignored != null) {
         ignored.remove(targetName.toLowerCase());
         if (ignored.isEmpty()) {
            ignoreMap.remove(playerName);
         }
      }
   }

   public static boolean isIgnoring(ServerPlayer player, ServerPlayer target) {
      String playerName = player.getName().getString().toLowerCase();
      String targetName = target.getName().getString().toLowerCase();
      Set<String> ignored = ignoreMap.get(playerName);
      return ignored != null && ignored.contains(targetName);
   }

   public static boolean isIgnoring(ServerPlayer player, String targetName) {
      String playerName = player.getName().getString().toLowerCase();
      Set<String> ignored = ignoreMap.get(playerName);
      return ignored != null && ignored.contains(targetName.toLowerCase());
   }

   public static Set<String> getIgnoreList(ServerPlayer player) {
      String playerName = player.getName().getString().toLowerCase();
      Set<String> ignored = ignoreMap.get(playerName);
      return ignored != null ? Set.copyOf(ignored) : Set.of();
   }

   public static void cleanupPlayer(ServerPlayer player) {
      String playerName = player.getName().getString().toLowerCase();
      ignoreMap.remove(playerName);
      ignoreMap.values().forEach(ignoreSet -> ignoreSet.remove(playerName));
      ignoreMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
   }
}
