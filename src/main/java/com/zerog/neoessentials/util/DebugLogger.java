package com.zerog.neoessentials.util;

import com.zerog.neoessentials.config.ConfigManager;
import org.slf4j.Logger;

public class DebugLogger {
   public static void log(Logger logger, String message) {
      if (isDebugEnabled()) {
         logger.debug(message);
      }
   }

   public static void log(Logger logger, String format, Object arg) {
      if (isDebugEnabled()) {
         logger.debug(format, arg);
      }
   }

   public static void log(Logger logger, String format, Object arg1, Object arg2) {
      if (isDebugEnabled()) {
         logger.debug(format, arg1, arg2);
      }
   }

   public static void log(Logger logger, String format, Object arg1, Object arg2, Object arg3) {
      if (isDebugEnabled()) {
         logger.debug(format, new Object[]{arg1, arg2, arg3});
      }
   }

   public static void log(Logger logger, String format, Object... arguments) {
      if (isDebugEnabled()) {
         logger.debug(format, arguments);
      }
   }

   public static void log(Logger logger, String message, Throwable throwable) {
      if (isDebugEnabled()) {
         logger.debug(message, throwable);
      }
   }

   public static boolean isDebugEnabled() {
      try {
         return ConfigManager.getInstance().isDebugLoggingEnabled();
      } catch (Exception var1) {
         return false;
      }
   }

   public static void logUnchecked(Logger logger, String message) {
      logger.debug(message);
   }

   public static void logUnchecked(Logger logger, String format, Object... arguments) {
      logger.debug(format, arguments);
   }
}
