package com.zerog.neoessentials.config;

@Deprecated
public class ConfigUtil {
   public static boolean isCommandEnabled(String command) {
      return ConfigManager.getInstance().isCommandEnabled(command);
   }
}
