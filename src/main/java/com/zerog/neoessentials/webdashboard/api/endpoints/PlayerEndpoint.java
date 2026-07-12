package com.zerog.neoessentials.webdashboard.api.endpoints;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.data.PlayerDataCollector;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerEndpoint implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerEndpoint.class);
   private final MinecraftServer server;
   private final PlayerDataCollector playerCollector;

   public PlayerEndpoint(MinecraftServer server) {
      this.server = server;
      this.playerCollector = new PlayerDataCollector(server);
   }

   private UUID usernameToUuid(String username) {
      ServerPlayer player = this.server.getPlayerList().getPlayerByName(username);
      return player != null ? player.getUUID() : null;
   }

   @Override
   public void handle(HttpExchange exchange) {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      LOGGER.debug("PlayerEndpoint handling request: {} {}", method, path);

      try {
         if ("GET".equals(method)) {
            CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
               try {
                  LOGGER.debug("Collecting player data for endpoint: {}", path);
                  return this.getResponse(path);
               } catch (Exception var4x) {
                  LOGGER.error("Error collecting player data for path: {}", path, var4x);
                  JsonObject error = new JsonObject();
                  error.addProperty("error", var4x.getMessage() != null ? var4x.getMessage() : var4x.getClass().getSimpleName());
                  return error;
               }
            }, this.server);

            JsonObject response;
            try {
               response = future.get(10L, TimeUnit.SECONDS);
               LOGGER.debug("Player data collected successfully for: {}", path);
            } catch (TimeoutException var24) {
               LOGGER.error("Timeout waiting for player data collection: {}", path);
               response = new JsonObject();
               response.addProperty("error", "Request timeout - server may be overloaded");
            } catch (ExecutionException var25) {
               LOGGER.error("Execution error during player data collection: {}", path, var25);
               response = new JsonObject();
               response.addProperty("error", "Internal server error: " + (var25.getCause() != null ? var25.getCause().getMessage() : var25.getMessage()));
            }

            if (response.has("error")) {
               String errorMsg = response.get("error").getAsString();
               if (!errorMsg.equals("Player not found") && !errorMsg.equals("Endpoint not found")) {
                  this.sendResponse(exchange, 500, response.toString());
               } else {
                  this.sendResponse(exchange, 404, response.toString());
               }

               return;
            } else {
               this.sendResponse(exchange, 200, response.toString());
               return;
            }
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

   private JsonObject getResponse(String path) {
      JsonObject response;
      if (path.matches("/api/player/profile/.*")) {
         String username = path.substring("/api/player/profile/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerProfile(uuid);
      } else if (path.matches("/api/player/stats/.*")) {
         String username = path.substring("/api/player/stats/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerStatistics(uuid);
      } else if (path.matches("/api/player/achievements/.*")) {
         String username = path.substring("/api/player/achievements/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerAchievements(uuid);
      } else if (path.matches("/api/player/inventory/.*")) {
         String username = path.substring("/api/player/inventory/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerInventory(uuid);
      } else if (path.matches("/api/player/status/.*")) {
         String username = path.substring("/api/player/status/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerStatus(uuid);
      } else if (path.matches("/api/player/health/.*")) {
         String username = path.substring("/api/player/health/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerHealth(uuid);
      } else if (path.matches("/api/player/xp/.*")) {
         String username = path.substring("/api/player/xp/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerXP(uuid);
      } else if (path.matches("/api/player/location/.*")) {
         String username = path.substring("/api/player/location/".length());
         UUID uuid = this.usernameToUuid(username);
         if (uuid == null) {
            response = new JsonObject();
            response.addProperty("error", "Player not found");
            return response;
         }

         response = this.playerCollector.getPlayerLocation(uuid);
      } else if (path.matches("/api/player/homes/.*")) {
         String username = path.substring("/api/player/homes/".length());
         response = this.playerCollector.getPlayerHomes(username);
      } else {
         if (!path.equals("/api/player/online")) {
            response = new JsonObject();
            response.addProperty("error", "Endpoint not found");
            return response;
         }

         response = this.playerCollector.getOnlinePlayers();
      }

      return response;
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
