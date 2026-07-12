package com.zerog.neoessentials.webdashboard.security;

import com.sun.net.httpserver.HttpExchange;
import com.zerog.neoessentials.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorsHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(CorsHandler.class);
   private static final String DEFAULT_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
   private static final String DEFAULT_HEADERS = "Content-Type, Authorization";

   private CorsHandler() {
   }

   public static String getAllowedOrigin() {
      try {
         ConfigManager config = ConfigManager.getInstance();
         if (config == null) {
            return null;
         } else if (!config.isCorsEnabled()) {
            return null;
         } else {
            String allowedOrigin = config.getCorsAllowedOrigin();
            return allowedOrigin != null && !allowedOrigin.isEmpty() ? allowedOrigin : config.getWebDashboardUrl();
         }
      } catch (Exception var2) {
         LOGGER.debug("Could not determine CORS origin, defaulting to dashboard URL: {}", var2.getMessage());
         return "http://localhost:8080";
      }
   }

   public static void apply(HttpExchange exchange) {
      applyWithMethods(exchange, "GET, POST, PUT, DELETE, OPTIONS");
   }

   public static void applyWithMethods(HttpExchange exchange, String methods) {
      applyFull(exchange, methods, "Content-Type, Authorization");
   }

   public static void applyFull(HttpExchange exchange, String methods, String headers) {
      String origin = getAllowedOrigin();
      if (origin != null) {
         exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
         if (methods != null && !methods.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", methods);
         }

         if (headers != null && !headers.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", headers);
         }
      }
   }

   public static boolean handlePreflight(HttpExchange exchange) {
      if (!"OPTIONS".equals(exchange.getRequestMethod())) {
         return false;
      } else {
         try {
            applyFull(exchange, "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
            exchange.sendResponseHeaders(204, -1L);
         } catch (Exception var2) {
            LOGGER.debug("Error handling CORS preflight: {}", var2.getMessage());
         }

         return true;
      }
   }
}
