package com.zerog.neoessentials.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatDebugUtil {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChatDebugUtil.class);

   public static void debug(String message) {
      LOGGER.debug("[CHAT] {}", message);
   }

   public static void debug(String format, Object... args) {
      LOGGER.debug("[CHAT] " + format, args);
   }
}
