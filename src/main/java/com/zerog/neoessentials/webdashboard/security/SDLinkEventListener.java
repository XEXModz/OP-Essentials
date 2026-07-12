package com.zerog.neoessentials.webdashboard.security;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDLinkEventListener {
   private static final Logger LOGGER = LoggerFactory.getLogger(SDLinkEventListener.class);
   private static boolean botReady = false;

   public static boolean isBotReady() {
      try {
         Class<?> botControllerClass = Class.forName("com.hypherionmc.sdlink.core.discord.BotController");
         Object botController = botControllerClass.getField("INSTANCE").get(null);
         Method isBotReadyMethod = botControllerClass.getMethod("isBotReady");
         Boolean ready = (Boolean)isBotReadyMethod.invoke(botController);
         botReady = ready != null && ready;
      } catch (Exception var4) {
         LOGGER.debug("SDLink BotController not available: {}", var4.getMessage());
         botReady = false;
      }

      return botReady;
   }

   public static void setBotReady(boolean ready) {
      botReady = ready;
   }
}
