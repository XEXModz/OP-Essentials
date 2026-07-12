package com.zerog.neoessentials.webdashboard.api.endpoints;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.data.ServerAssetCollector;
import com.zerog.neoessentials.webdashboard.data.ServerDataCollector;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerEndpoint implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(ServerEndpoint.class);
   private final MinecraftServer server;
   private final ServerDataCollector serverCollector;
   private final ServerAssetCollector assetCollector;

   public ServerEndpoint(MinecraftServer server) {
      this.server = server;
      this.serverCollector = new ServerDataCollector(server);
      this.assetCollector = new ServerAssetCollector(server);
   }

   @Override
   public void handle(HttpExchange exchange) {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      LOGGER.debug("ServerEndpoint handling request: {} {}", method, path);

      try {
         if ("GET".equals(method)) {
            CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
               try {
                  LOGGER.debug("Collecting data for endpoint: {}", path);
                  if (path.startsWith("/api/server/assets/")) {
                     String namespace = path.substring("/api/server/assets/".length());
                     return this.assetCollector.getNamespaceAssets(namespace);
                  } else {
                     return switch (path) {
                        case "/api/server/profile" -> this.serverCollector.getServerProfile();
                        case "/api/server/performance" -> this.serverCollector.getServerPerformance();
                        case "/api/server/statistics" -> this.serverCollector.getServerStatistics();
                        case "/api/server/status" -> this.serverCollector.getServerStatus();
                        case "/api/server/health" -> this.serverCollector.getServerHealth();
                        case "/api/server/worlds" -> this.serverCollector.getServerWorlds();
                        case "/api/server/config" -> this.serverCollector.getServerConfig();
                        case "/api/server/assets" -> this.assetCollector.getAllAssets();
                        default -> {
                           JsonObject error = new JsonObject();
                           error.addProperty("error", "Endpoint not found");
                           yield error;
                        }
                     };
                  }
               } catch (Exception var5x) {
                  LOGGER.error("Error collecting server data for path: {}", path, var5x);
                  JsonObject error = new JsonObject();
                  error.addProperty("error", var5x.getMessage() != null ? var5x.getMessage() : var5x.getClass().getSimpleName());
                  return error;
               }
            }, this.server);

            JsonObject response;
            try {
               response = future.get(10L, TimeUnit.SECONDS);
               LOGGER.debug("Data collected successfully for: {}", path);
            } catch (TimeoutException var24) {
               LOGGER.error("Timeout waiting for data collection: {}", path);
               response = new JsonObject();
               response.addProperty("error", "Request timeout - server may be overloaded");
            } catch (ExecutionException var25) {
               LOGGER.error("Execution error during data collection: {}", path, var25);
               response = new JsonObject();
               response.addProperty("error", "Internal server error: " + (var25.getCause() != null ? var25.getCause().getMessage() : var25.getMessage()));
            }

            if (response.has("error") && !path.equals("/api/server/profile")) {
               this.sendResponse(exchange, response.get("error").getAsString().equals("Endpoint not found") ? 404 : 500, response.toString());
               return;
            }

            this.sendResponse(exchange, 200, response.toString());
            return;
         }

         this.sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
      } catch (IOException var26) {
         String errorMsg = var26.getMessage() != null ? var26.getMessage() : var26.getClass().getSimpleName();
         if (!errorMsg.contains("stream is closed") && !errorMsg.contains("Broken pipe") && !errorMsg.contains("Connection reset")) {
            LOGGER.error("IOException handling request: {} {}", new Object[]{method, path, var26});

            try {
               String errorResponse = String.format("{\"error\":\"IO Error: %s\"}", errorMsg);
               this.sendResponse(exchange, 500, errorResponse);
            } catch (IOException var23) {
               LOGGER.debug("Could not send error response (client likely disconnected): {}", var23.getMessage());
            }

            return;
         } else {
            LOGGER.warn("Client disconnected during request: {} {} - {}", new Object[]{method, path, errorMsg});
            return;
         }
      } catch (Exception var27) {
         Exception e = var27;
         LOGGER.error("Unexpected error handling request: {} {}", new Object[]{method, path, var27});

         try {
            String errorMsgx = e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error";
            String errorResponse = String.format("{\"error\":\"%s\"}", errorMsgx);
            this.sendResponse(exchange, 500, errorResponse);
         } catch (IOException var22) {
            LOGGER.debug("Could not send error response (client likely disconnected): {}", var22.getMessage());
         }

         return;
      } finally {
         try {
            exchange.close();
         } catch (Exception var21) {
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
