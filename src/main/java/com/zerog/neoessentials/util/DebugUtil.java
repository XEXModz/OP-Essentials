package com.zerog.neoessentials.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugUtil {
   private static final Logger LOGGER = LoggerFactory.getLogger(DebugUtil.class);
   private static boolean debugEnabled = false;
   private static boolean loaded = false;

   public static boolean isDebugEnabled() {
      if (!loaded) {
         reload();
      }

      return debugEnabled;
   }

   public static void debug(String msg) {
      if (isDebugEnabled()) {
         LOGGER.info("[DEBUG] {}", msg);
      }
   }

   public static void debugErr(String msg) {
      if (isDebugEnabled()) {
         LOGGER.error("[DEBUG] {}", msg);
      }
   }

   public static void debugStackTrace(Throwable t) {
      if (isDebugEnabled()) {
         LOGGER.error("[DEBUG] Exception occurred", t);
      }
   }

   public static void reload() {
      try {
         File configFile = ResourceUtil.getConfigFile("config.json");
         if (configFile.exists()) {
            String json = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("debug")) {
               JsonObject debugObj = obj.getAsJsonObject("debug");
               if (debugObj.has("debugEnabled")) {
                  debugEnabled = debugObj.get("debugEnabled").getAsBoolean();
               }
            }
         }
      } catch (Exception var4) {
         debugEnabled = false;
      }

      loaded = true;
   }
}
