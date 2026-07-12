package com.zerog.neoessentials.webdashboard.api.endpoints;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.data.LoggingDataCollector;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEndpoint implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(LoggingEndpoint.class);
   private final LoggingDataCollector loggingCollector = new LoggingDataCollector();

   @Override
   public void handle(HttpExchange exchange) {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      LOGGER.debug("LoggingEndpoint handling request: {} {}", method, path);

      try {
         if ("GET".equals(method)) {
            JsonObject response = switch (path) {
               case "/api/logging/requests" -> this.loggingCollector.getRequestLogs(100);
               case "/api/logging/errors" -> this.loggingCollector.getErrorLogs(100, "ALL");
               case "/api/logging/performance" -> this.loggingCollector.getPerformanceMetrics();
               default -> {
                  this.sendResponse(exchange, 404, "{\"error\":\"Endpoint not found\"}");
                  yield null;
               }
            };
            if (response != null) {
               this.sendResponse(exchange, 200, response.toString());
            }

            return;
         }

         this.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
      } catch (IOException var22) {
         String errorMsg = var22.getMessage() != null ? var22.getMessage() : var22.getClass().getSimpleName();
         if (!errorMsg.contains("stream is closed") && !errorMsg.contains("Broken pipe") && !errorMsg.contains("Connection reset")) {
            LOGGER.error("IOException handling request: {} {}", new Object[]{method, path, var22});

            try {
               String errorResponse = String.format("{\"error\":\"IO Error: %s\"}", errorMsg);
               this.sendResponse(exchange, 500, errorResponse);
            } catch (IOException var21) {
               LOGGER.debug("Could not send error response (client likely disconnected): {}", var21.getMessage());
            }

            return;
         } else {
            LOGGER.warn("Client disconnected during request: {} {} - {}", new Object[]{method, path, errorMsg});
            return;
         }
      } catch (Exception var23) {
         Exception e = var23;
         LOGGER.error("Unexpected error handling request: {} {}", new Object[]{method, path, var23});

         try {
            String errorMsgx = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error";
            String errorResponse = String.format("{\"error\":\"%s\"}", errorMsgx);
            this.sendResponse(exchange, 500, errorResponse);
         } catch (IOException var20) {
            LOGGER.debug("Could not send error response (client likely disconnected): {}", var20.getMessage());
         }

         return;
      } finally {
         try {
            exchange.close();
         } catch (Exception var19) {
         }
      }
   }

   private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(statusCode, (long)bytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }
}
