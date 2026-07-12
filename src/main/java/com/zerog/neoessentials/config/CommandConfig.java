package com.zerog.neoessentials.config;

@Deprecated
public class CommandConfig {
   public static boolean isCommandEnabled(String command) {
      return ConfigManager.getInstance().isCommandEnabled(command);
   }

   public static void load() {
      ConfigManager.loadAll();
   }
}
