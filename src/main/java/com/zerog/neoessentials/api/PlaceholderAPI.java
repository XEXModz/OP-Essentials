package com.zerog.neoessentials.api;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

public interface PlaceholderAPI {
   static boolean registerPlaceholder(String identifier, PlaceholderProvider provider) {
      return PlaceholderManager.getInstance().registerPlaceholder(identifier, provider);
   }

   static boolean unregisterPlaceholder(String identifier) {
      return PlaceholderManager.getInstance().unregisterPlaceholder(identifier);
   }

   static String setPlaceholders(@Nullable ServerPlayer player, String text) {
      return PlaceholderManager.getInstance().setPlaceholders(player, text);
   }

   @Nullable
   static String getPlaceholderValue(@Nullable ServerPlayer player, String identifier, @Nullable String params) {
      return PlaceholderManager.getInstance().getPlaceholderValue(player, identifier, params);
   }

   static boolean isPlaceholderRegistered(String identifier) {
      return PlaceholderManager.getInstance().isPlaceholderRegistered(identifier);
   }

   static Set<String> getRegisteredPlaceholders() {
      return PlaceholderManager.getInstance().getRegisteredPlaceholders();
   }

   static boolean registerExpansion(PlaceholderExpansion expansion) {
      return PlaceholderManager.getInstance().registerExpansion(expansion);
   }

   static boolean unregisterExpansion(PlaceholderExpansion expansion) {
      return PlaceholderManager.getInstance().unregisterExpansion(expansion);
   }
}
