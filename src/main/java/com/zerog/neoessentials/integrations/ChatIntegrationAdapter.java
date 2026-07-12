package com.zerog.neoessentials.integrations;

import net.minecraft.server.level.ServerPlayer;

public interface ChatIntegrationAdapter {
   String getName();

   default void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
   }

   default void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
   }

   default void onPlayerMute(ServerPlayer player, String reason, boolean isMuted) {
   }

   default void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason) {
   }

   default void onPlayerJoin(ServerPlayer player) {
   }

   default void onPlayerQuit(ServerPlayer player) {
   }

   default boolean isEnabled() {
      return true;
   }

   default boolean initialize() {
      return true;
   }

   default void shutdown() {
   }
}
