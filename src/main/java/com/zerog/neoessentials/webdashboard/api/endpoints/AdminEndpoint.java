package com.zerog.neoessentials.webdashboard.api.endpoints;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminEndpoint implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AdminEndpoint.class);
   private final MinecraftServer server;

   public AdminEndpoint(MinecraftServer server) {
      this.server = server;
   }

   @Override
   public void handle(HttpExchange exchange) {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      LOGGER.info("AdminEndpoint handling request: {} {}", method, path);

      try {
         if (path.equals("/api/admin/status") && "GET".equals(method)) {
            this.handleGetStatus(exchange);
         } else if (path.equals("/api/admin/restart") && "POST".equals(method)) {
            this.handleRestart(exchange);
         } else if (path.equals("/api/admin/stop") && "POST".equals(method)) {
            this.handleStop(exchange);
         } else if (path.equals("/api/admin/reload") && "POST".equals(method)) {
            this.handleReload(exchange);
         } else if (path.equals("/api/admin/save") && "POST".equals(method)) {
            this.handleSaveAll(exchange);
         } else {
            this.sendResponse(exchange, 404, "{\"error\":\"Endpoint not found\"}");
         }
      } catch (Exception var7) {
         Exception e = var7;
         LOGGER.error("Error handling admin request: {} {}", new Object[]{method, path, var7});

         try {
            String errorResponse = String.format(
               "{\"success\":false,\"error\":\"Internal error: %s\"}", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            );
            this.sendResponse(exchange, 500, errorResponse);
         } catch (IOException var6) {
            LOGGER.error("Failed to send error response", var6);
         }
      }
   }

   private void handleGetStatus(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("serverRunning", !this.server.isStopped());
      response.addProperty("canRestart", true);
      response.addProperty("canStop", true);
      response.addProperty("canReload", true);
      response.addProperty("canSave", true);
      this.sendResponse(exchange, 200, response.toString());
   }

   private void handleRestart(HttpExchange exchange) throws IOException {
      LOGGER.warn("Server restart requested via dashboard");
      CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(
         () -> {
            try {
               JsonObject response = new JsonObject();
               this.server
                  .getPlayerList()
                  .getPlayers()
                  .forEach(player -> player.sendSystemMessage(MessageUtil.component("commands.neoessentials.admin.server_restarting")));
               LOGGER.info("Broadcasting restart message and scheduling restart in 5 seconds");
               new Thread(() -> {
                  try {
                     Thread.sleep(5000L);
                     LOGGER.info("Saving all worlds before restart...");
                     this.server.saveAllChunks(true, true, true);
                     LOGGER.info("Stopping server for restart...");
                     this.server.halt(false);
                  } catch (InterruptedException var2x) {
                     LOGGER.error("Restart interrupted", var2x);
                     Thread.currentThread().interrupt();
                  }
               }, "Dashboard-Restart").start();
               response.addProperty("success", true);
               response.addProperty("message", "Server restart initiated. Restarting in 5 seconds...");
               return response;
            } catch (Exception var3x) {
               LOGGER.error("Error initiating restart", var3x);
               JsonObject error = new JsonObject();
               error.addProperty("success", false);
               error.addProperty("error", "Failed to restart: " + var3x.getMessage());
               return error;
            }
         },
         this.server
      );

      try {
         JsonObject response = future.get(2L, TimeUnit.SECONDS);
         this.sendResponse(exchange, 200, response.toString());
      } catch (Exception var4) {
         LOGGER.error("Timeout or error getting restart response", var4);
         this.sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Restart request timeout\"}");
      }
   }

   private void handleStop(HttpExchange exchange) throws IOException {
      LOGGER.warn("Server stop requested via dashboard");
      CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(
         () -> {
            try {
               JsonObject response = new JsonObject();
               this.server
                  .getPlayerList()
                  .getPlayers()
                  .forEach(player -> player.sendSystemMessage(MessageUtil.component("commands.neoessentials.admin.server_shutting_down")));
               LOGGER.info("Broadcasting shutdown message and scheduling stop in 5 seconds");
               new Thread(() -> {
                  try {
                     Thread.sleep(5000L);
                     LOGGER.info("Saving all worlds before shutdown...");
                     this.server.saveAllChunks(true, true, true);
                     LOGGER.info("Stopping server...");
                     this.server.halt(false);
                  } catch (InterruptedException var2x) {
                     LOGGER.error("Stop interrupted", var2x);
                     Thread.currentThread().interrupt();
                  }
               }, "Dashboard-Stop").start();
               response.addProperty("success", true);
               response.addProperty("message", "Server shutdown initiated. Stopping in 5 seconds...");
               return response;
            } catch (Exception var3x) {
               LOGGER.error("Error initiating stop", var3x);
               JsonObject error = new JsonObject();
               error.addProperty("success", false);
               error.addProperty("error", "Failed to stop: " + var3x.getMessage());
               return error;
            }
         },
         this.server
      );

      try {
         JsonObject response = future.get(2L, TimeUnit.SECONDS);
         this.sendResponse(exchange, 200, response.toString());
      } catch (Exception var4) {
         LOGGER.error("Timeout or error getting stop response", var4);
         this.sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Stop request timeout\"}");
      }
   }

   private void handleReload(HttpExchange exchange) throws IOException {
      LOGGER.info("Config reload requested via dashboard");
      CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
         try {
            JsonObject response = new JsonObject();
            int successCount = 0;
            int totalCount = 0;
            StringBuilder details = new StringBuilder();
            totalCount++;

            try {
               ConfigManager.loadAll();
               LOGGER.info("✓ Configuration files reloaded");
               details.append("Configuration files: OK\n");
               successCount++;
            } catch (Exception var7) {
               LOGGER.error("✗ Failed to reload configuration files: {}", var7.getMessage(), var7);
               details.append("Configuration files: FAILED (").append(var7.getMessage()).append(")\n");
            }

            totalCount++;

            try {
               MessageUtil.reloadTranslations();
               LOGGER.info("✓ Translations reloaded");
               details.append("Translations: OK\n");
               successCount++;
            } catch (Exception var6) {
               LOGGER.error("✗ Failed to reload translations: {}", var6.getMessage(), var6);
               details.append("Translations: FAILED (").append(var6.getMessage()).append(")\n");
            }

            totalCount++;

            try {
               PermissionAPI.reload();
               LOGGER.info("✓ Permission system reloaded");
               details.append("Permissions: OK\n");
               successCount++;
            } catch (Exception var5) {
               LOGGER.error("✗ Failed to reload permissions: {}", var5.getMessage(), var5);
               details.append("Permissions: FAILED (").append(var5.getMessage()).append(")\n");
            }

            boolean allSuccess = successCount == totalCount;
            response.addProperty("success", allSuccess);
            response.addProperty("successCount", successCount);
            response.addProperty("totalCount", totalCount);
            response.addProperty("message", String.format("Reload completed: %d/%d systems reloaded successfully", successCount, totalCount));
            response.addProperty("details", details.toString());
            LOGGER.info("Dashboard reload completed: {}/{} systems", successCount, totalCount);
            return response;
         } catch (Exception var8) {
            LOGGER.error("Error executing reload", var8);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Failed to reload: " + var8.getMessage());
            return error;
         }
      }, this.server);

      try {
         JsonObject response = future.get(30L, TimeUnit.SECONDS);
         this.sendResponse(exchange, 200, response.toString());
      } catch (Exception var4) {
         LOGGER.error("Timeout or error getting reload response", var4);
         this.sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Reload request timeout\"}");
      }
   }

   private void handleSaveAll(HttpExchange exchange) throws IOException {
      LOGGER.info("Save all requested via dashboard");
      CompletableFuture<JsonObject> future = CompletableFuture.supplyAsync(() -> {
         try {
            JsonObject response = new JsonObject();
            boolean saved = this.server.saveAllChunks(true, true, true);
            if (saved) {
               response.addProperty("success", true);
               response.addProperty("message", "All worlds saved successfully");
               LOGGER.info("Dashboard save-all completed successfully");
            } else {
               response.addProperty("success", false);
               response.addProperty("error", "Save operation returned false");
               LOGGER.warn("Dashboard save-all returned false");
            }

            return response;
         } catch (Exception var3x) {
            LOGGER.error("Error executing save-all", var3x);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Failed to save: " + var3x.getMessage());
            return error;
         }
      }, this.server);

      try {
         JsonObject response = future.get(30L, TimeUnit.SECONDS);
         this.sendResponse(exchange, 200, response.toString());
      } catch (Exception var4) {
         LOGGER.error("Timeout or error getting save response", var4);
         this.sendResponse(exchange, 500, "{\"success\":false,\"error\":\"Save request timeout\"}");
      }
   }

   private void sendResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
      byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(statusCode, (long)bytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }
}
