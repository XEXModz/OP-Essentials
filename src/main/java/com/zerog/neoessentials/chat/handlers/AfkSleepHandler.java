package com.zerog.neoessentials.chat.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AfkSleepHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkSleepHandler.class);

   public static void initialize() {
      LOGGER.info("AFK sleep handler initialized (sleep events disabled for compatibility)");
   }
}
