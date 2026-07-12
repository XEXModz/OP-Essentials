package com.zerog.neoessentials.webdashboard.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.webdashboard.cloud.CloudProviderManager;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileManagementHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(FileManagementHandler.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final List<Path> ALLOWED_PATHS = Arrays.asList(Paths.get("config"), Paths.get("logs"), Paths.get("neoessentials"), Paths.get("world"));
   private static final Set<String> EDITABLE_EXTENSIONS = new HashSet<>(
      Arrays.asList(".json", ".txt", ".properties", ".yml", ".yaml", ".toml", ".conf", ".cfg", ".log")
   );
   private static final long MAX_FILE_SIZE = 10485760L;

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      CorsHandler.applyWithMethods(exchange, "GET, POST, PUT, DELETE, OPTIONS");
      if ("OPTIONS".equals(exchange.getRequestMethod())) {
         exchange.sendResponseHeaders(204, -1L);
      } else {
         String method = exchange.getRequestMethod();
         String path = exchange.getRequestURI().getPath();

         try {
            switch (method) {
               case "GET":
                  if (path.endsWith("/browse")) {
                     this.handleBrowse(exchange);
                  } else if (path.endsWith("/read")) {
                     this.handleRead(exchange);
                  } else if (path.endsWith("/download")) {
                     this.handleDownload(exchange);
                  } else if (path.endsWith("/listBackups")) {
                     this.handleListBackups(exchange);
                  } else if (path.endsWith("/cloudProviders")) {
                     this.handleCloudProviders(exchange);
                  } else if (path.endsWith("/server/statistics")) {
                     this.handleServerStatistics(exchange);
                  } else if (path.endsWith("/player/statistics")) {
                     this.handlePlayerStatistics(exchange);
                  } else if (path.endsWith("/user/activityLog")) {
                     this.handleUserActivityLog(exchange);
                  } else {
                     this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid GET endpoint"));
                  }
                  break;
               case "POST":
                  if (path.endsWith("/write")) {
                     this.handleWrite(exchange);
                  } else if (path.endsWith("/create")) {
                     this.handleCreate(exchange);
                  } else if (path.endsWith("/upload")) {
                     this.handleUpload(exchange);
                  } else if (path.endsWith("/restore")) {
                     this.handleRestore(exchange);
                  } else if (path.endsWith("/cloudBackup")) {
                     this.handleCloudBackup(exchange);
                  } else if (path.endsWith("/cloudRestore")) {
                     this.handleCloudRestore(exchange);
                  } else if (path.endsWith("/cloudLink")) {
                     this.handleCloudLink(exchange);
                  } else if (path.endsWith("/cloudUnlink")) {
                     this.handleCloudUnlink(exchange);
                  } else {
                     this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid POST endpoint"));
                  }
                  break;
               case "DELETE":
                  if (path.endsWith("/delete")) {
                     this.handleDelete(exchange);
                  } else {
                     this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid DELETE endpoint"));
                  }
                  break;
               default:
                  this.sendJsonResponse(exchange, 405, this.createErrorResponse("Method not allowed"));
            }
         } catch (SecurityException var6) {
            LOGGER.warn("Security violation attempt: {}", var6.getMessage());
            this.sendJsonResponse(exchange, 403, this.createErrorResponse("Access denied"));
         } catch (Exception var7) {
            LOGGER.error("Error handling file management request", var7);
            this.sendJsonResponse(exchange, 500, this.createErrorResponse("Internal server error: " + var7.getMessage()));
         }
      }
   }

   private void handleBrowse(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String pathParam = params.getOrDefault("path", "");
      Path targetPath = this.resolvePath(pathParam);
      this.validatePath(targetPath);
      if (!Files.exists(targetPath)) {
         this.sendJsonResponse(exchange, 404, this.createErrorResponse("Path not found"));
      } else if (!Files.isDirectory(targetPath)) {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Path is not a directory"));
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("path", pathParam);
         response.addProperty("absolutePath", targetPath.toAbsolutePath().toString());
         JsonArray items = new JsonArray();

         try (Stream<Path> paths = Files.list(targetPath)) {
            paths.sorted().forEach(path -> {
               try {
                  JsonObject item = new JsonObject();
                  item.addProperty("name", path.getFileName().toString());
                  item.addProperty("type", Files.isDirectory(path) ? "directory" : "file");
                  BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                  item.addProperty("size", attrs.size());
                  item.addProperty("modified", attrs.lastModifiedTime().toMillis());
                  item.addProperty("created", attrs.creationTime().toMillis());
                  if (!Files.isDirectory(path)) {
                     String fileName = path.getFileName().toString();
                     String extension = this.getFileExtension(fileName);
                     item.addProperty("extension", extension);
                     item.addProperty("editable", EDITABLE_EXTENSIONS.contains(extension.toLowerCase()));
                  }

                  items.add(item);
               } catch (IOException var7x) {
                  LOGGER.warn("Error reading file attributes: {}", path, var7x);
               }
            });
         }

         response.add("items", items);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleRead(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String pathParam = params.getOrDefault("path", "");
      Path targetPath = this.resolvePath(pathParam);
      this.validatePath(targetPath);
      if (!Files.exists(targetPath)) {
         this.sendJsonResponse(exchange, 404, this.createErrorResponse("File not found"));
      } else if (Files.isDirectory(targetPath)) {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Path is a directory"));
      } else {
         long fileSize = Files.size(targetPath);
         if (fileSize > 10485760L) {
            this.sendJsonResponse(exchange, 400, this.createErrorResponse("File too large to read (max 10 MB)"));
         } else {
            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            JsonObject response = new JsonObject();
            response.addProperty("path", pathParam);
            response.addProperty("content", content);
            response.addProperty("size", fileSize);
            response.addProperty("extension", this.getFileExtension(targetPath.getFileName().toString()));
            this.sendJsonResponse(exchange, 200, response);
         }
      }
   }

   private void handleDownload(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String pathParam = params.getOrDefault("path", "");
      Path targetPath = this.resolvePath(pathParam);
      this.validatePath(targetPath);
      if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
         byte[] fileBytes = Files.readAllBytes(targetPath);
         exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
         exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + targetPath.getFileName().toString() + "\"");
         exchange.sendResponseHeaders(200, (long)fileBytes.length);

         try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
         }
      } else {
         this.sendJsonResponse(exchange, 404, this.createErrorResponse("File not found"));
      }
   }

   private void handleWrite(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (request.has("path") && request.has("content")) {
         String pathParam = request.get("path").getAsString();
         String content = request.get("content").getAsString();
         Path targetPath = this.resolvePath(pathParam);
         this.validatePath(targetPath);
         if (!Files.exists(targetPath)) {
            this.sendJsonResponse(exchange, 404, this.createErrorResponse("File not found"));
         } else {
            Path backupPath = this.createBackup(targetPath);

            try {
               Files.writeString(targetPath, content, StandardCharsets.UTF_8);
               JsonObject response = new JsonObject();
               response.addProperty("success", true);
               response.addProperty("message", "File written successfully");
               response.addProperty("path", pathParam);
               response.addProperty("backup", backupPath.toString());
               this.sendJsonResponse(exchange, 200, response);
            } catch (IOException var9) {
               LOGGER.error("Error writing file", var9);
               this.sendJsonResponse(exchange, 500, this.createErrorResponse("Failed to write file: " + var9.getMessage()));
            }
         }
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'path' or 'content' field"));
      }
   }

   private void handleCreate(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (request.has("path") && request.has("type")) {
         String pathParam = request.get("path").getAsString();
         String type = request.get("type").getAsString();
         Path targetPath = this.resolvePath(pathParam);
         this.validatePath(targetPath);
         if (Files.exists(targetPath)) {
            this.sendJsonResponse(exchange, 409, this.createErrorResponse("Path already exists"));
         } else {
            if ("directory".equals(type)) {
               Files.createDirectories(targetPath);
            } else {
               if (!"file".equals(type)) {
                  this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid type (must be 'file' or 'directory')"));
                  return;
               }

               if (targetPath.getParent() != null) {
                  Files.createDirectories(targetPath.getParent());
               }

               String content = request.has("content") ? request.get("content").getAsString() : "";
               Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", type + " created successfully");
            response.addProperty("path", pathParam);
            this.sendJsonResponse(exchange, 201, response);
         }
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'path' or 'type' field"));
      }
   }

   private void handleUpload(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (request.has("path") && request.has("content")) {
         String pathParam = request.get("path").getAsString();
         String base64Content = request.get("content").getAsString();
         Path targetPath = this.resolvePath(pathParam);
         this.validatePath(targetPath);
         if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
         }

         byte[] decodedContent = Base64.getDecoder().decode(base64Content);
         Files.write(targetPath, decodedContent);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "File uploaded successfully");
         response.addProperty("path", pathParam);
         response.addProperty("size", decodedContent.length);
         this.sendJsonResponse(exchange, 201, response);
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'path' or 'content' field"));
      }
   }

   private void handleDelete(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String pathParam = params.getOrDefault("path", "");
      Path targetPath = this.resolvePath(pathParam);
      this.validatePath(targetPath);
      if (!Files.exists(targetPath)) {
         this.sendJsonResponse(exchange, 404, this.createErrorResponse("Path not found"));
      } else {
         Path backupPath = this.createBackup(targetPath);
         if (Files.isDirectory(targetPath)) {
            this.deleteDirectory(targetPath);
         } else {
            Files.delete(targetPath);
         }

         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Path deleted successfully");
         response.addProperty("path", pathParam);
         response.addProperty("backup", backupPath.toString());
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleListBackups(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String pathParam = params.getOrDefault("path", "");
      Path targetPath = this.resolvePath(pathParam);
      this.validatePath(targetPath);
      String fileName = targetPath.getFileName().toString();
      Path backupDir = Paths.get("neoessentials", "backups", "files");
      Files.createDirectories(backupDir);
      JsonArray backups = new JsonArray();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, fileName + ".*.backup")) {
         for (Path backup : stream) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", backup.getFileName().toString());
            obj.addProperty("path", backup.toString());
            obj.addProperty("modified", Files.getLastModifiedTime(backup).toMillis());
            obj.addProperty("size", Files.size(backup));
            backups.add(obj);
         }
      }

      JsonObject response = new JsonObject();
      response.add("backups", backups);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleRestore(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (request.has("targetPath") && request.has("backupPath")) {
         Path targetPath = this.resolvePath(request.get("targetPath").getAsString());
         Path backupPath = Paths.get(request.get("backupPath").getAsString()).normalize().toAbsolutePath();
         this.validatePath(targetPath);
         Path backupDir = Paths.get("neoessentials", "backups", "files").toAbsolutePath().normalize();
         if (!backupPath.startsWith(backupDir)) {
            this.sendJsonResponse(exchange, 403, this.createErrorResponse("Invalid backup path"));
         } else {
            if (targetPath.getParent() != null) {
               Files.createDirectories(targetPath.getParent());
            }

            Files.copy(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "File restored from backup");
            response.addProperty("targetPath", targetPath.toString());
            response.addProperty("backupPath", backupPath.toString());
            this.sendJsonResponse(exchange, 200, response);
         }
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'targetPath' or 'backupPath' field"));
      }
   }

   private void handleCloudProviders(HttpExchange exchange) throws IOException {
      CloudProviderManager cloudManager = CloudProviderManager.getInstance();
      JsonArray providers = new JsonArray();
      JsonObject google = new JsonObject();
      google.addProperty("name", "Google Drive");
      google.addProperty("id", "google_drive");
      google.addProperty("linked", cloudManager.isProviderLinked("google_drive"));
      google.addProperty("description", "Store backups on Google Drive");
      google.addProperty("icon", "☁️");
      providers.add(google);
      JsonObject dropbox = new JsonObject();
      dropbox.addProperty("name", "Dropbox");
      dropbox.addProperty("id", "dropbox");
      dropbox.addProperty("linked", cloudManager.isProviderLinked("dropbox"));
      dropbox.addProperty("description", "Store backups on Dropbox");
      dropbox.addProperty("icon", "\ud83d\udce6");
      providers.add(dropbox);
      JsonObject onedrive = new JsonObject();
      onedrive.addProperty("name", "OneDrive");
      onedrive.addProperty("id", "onedrive");
      onedrive.addProperty("linked", cloudManager.isProviderLinked("onedrive"));
      onedrive.addProperty("description", "Store backups on Microsoft OneDrive");
      onedrive.addProperty("icon", "☁️");
      providers.add(onedrive);
      JsonObject response = new JsonObject();
      response.add("providers", providers);
      response.addProperty("stub", true);
      response.addProperty("message", "Cloud provider OAuth is ready. Actual file sync requires provider-specific API implementation.");
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleCloudLink(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (request.has("provider") && request.has("accessToken")) {
         String provider = request.get("provider").getAsString();
         String accessToken = request.get("accessToken").getAsString();
         String refreshToken = request.has("refreshToken") ? request.get("refreshToken").getAsString() : null;
         long expiresIn = request.has("expiresIn") ? request.get("expiresIn").getAsLong() : 3600L;
         CloudProviderManager cloudManager = CloudProviderManager.getInstance();
         cloudManager.storeToken(provider, accessToken, refreshToken, expiresIn);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Cloud provider linked successfully");
         response.addProperty("provider", provider);
         response.addProperty("linked", true);
         this.sendJsonResponse(exchange, 200, response);
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'provider' or 'accessToken' field"));
      }
   }

   private void handleCloudUnlink(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (!request.has("provider")) {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'provider' field"));
      } else {
         String provider = request.get("provider").getAsString();
         CloudProviderManager cloudManager = CloudProviderManager.getInstance();
         cloudManager.removeToken(provider);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Cloud provider unlinked successfully");
         response.addProperty("provider", provider);
         response.addProperty("linked", false);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleCloudBackup(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty(
         "error", "Cloud backup requires an external cloud storage provider to be configured. Link a provider via /api/files/cloudLink first."
      );
      this.sendJsonResponse(exchange, 501, response);
   }

   private void handleCloudRestore(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty(
         "error", "Cloud restore requires an external cloud storage provider to be configured. Link a provider via /api/files/cloudLink first."
      );
      this.sendJsonResponse(exchange, 501, response);
   }

   private void handleServerStatistics(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();

      try {
         MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
         if (srv != null) {
            double avgTickMs = (double)srv.getAverageTickTimeNanos() / 1000000.0;
            double tps = Math.min(20.0, 1000.0 / Math.max(avgTickMs, 1.0));
            response.addProperty("tps", (double)Math.round(tps * 10.0) / 10.0);
            response.addProperty("avgTickMs", (double)Math.round(avgTickMs * 10.0) / 10.0);
            response.addProperty("onlinePlayers", srv.getPlayerCount());
            response.addProperty("maxPlayers", srv.getMaxPlayers());
            response.addProperty("motd", srv.getMotd());
            response.addProperty("uptimeSeconds", srv.getTickCount() / 20);
         } else {
            response.addProperty("tps", 0.0);
            response.addProperty("avgTickMs", 0.0);
            response.addProperty("onlinePlayers", 0);
            response.addProperty("maxPlayers", 0);
            response.addProperty("uptimeSeconds", 0);
         }

         Runtime rt = Runtime.getRuntime();
         long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
         long totalMB = rt.totalMemory() / 1048576L;
         long maxMB = rt.maxMemory() / 1048576L;
         response.addProperty("ramUsedMB", usedMB);
         response.addProperty("ramTotalMB", totalMB);
         response.addProperty("ramMaxMB", maxMB);
         response.addProperty("ramUsedPercent", maxMB > 0L ? Math.round((double)usedMB / (double)maxMB * 100.0) : 0L);
         OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
         response.addProperty("cpuProcessors", os.getAvailableProcessors());
         double loadAvg = os.getSystemLoadAverage();
         response.addProperty("cpuLoadAverage", loadAvg < 0.0 ? 0.0 : (double)Math.round(loadAvg * 100.0) / 100.0);
         double cpuUsage = -1.0;
         if (os instanceof com.sun.management.OperatingSystemMXBean hotspot) {
            cpuUsage = hotspot.getProcessCpuLoad() * 100.0;
         }

         response.addProperty("cpuUsagePercent", cpuUsage < 0.0 ? -1.0 : (double)Math.round(cpuUsage * 10.0) / 10.0);
         response.addProperty("success", true);
      } catch (Exception var17) {
         LOGGER.error("Error collecting server statistics: {}", var17.getMessage(), var17);
         response.addProperty("success", false);
         response.addProperty("error", var17.getMessage());
      }

      this.sendJsonResponse(exchange, 200, response);
   }

   private void handlePlayerStatistics(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();

      try {
         String query = exchange.getRequestURI().getQuery();
         String playerName = null;
         if (query != null) {
            for (String param : query.split("&")) {
               String[] kv = param.split("=", 2);
               if (kv.length == 2 && "player".equals(kv[0])) {
                  playerName = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
               }
            }
         }

         MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
         if (srv == null) {
            response.addProperty("success", false);
            response.addProperty("error", "Server not available");
            this.sendJsonResponse(exchange, 503, response);
            return;
         }

         if (playerName != null && !playerName.isEmpty()) {
            ServerPlayer player = srv.getPlayerList().getPlayerByName(playerName);
            if (player == null) {
               response.addProperty("success", false);
               response.addProperty("error", "Player not found or not online: " + playerName);
               this.sendJsonResponse(exchange, 404, response);
               return;
            }

            response.addProperty("success", true);
            response.add("player", this.buildPlayerStatsObject(player));
         } else {
            JsonArray players = new JsonArray();

            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
               players.add(this.buildPlayerStatsObject(p));
            }

            response.addProperty("success", true);
            response.add("players", players);
         }
      } catch (Exception var10) {
         LOGGER.error("Error collecting player statistics: {}", var10.getMessage(), var10);
         response.addProperty("success", false);
         response.addProperty("error", var10.getMessage());
      }

      this.sendJsonResponse(exchange, 200, response);
   }

   private JsonObject buildPlayerStatsObject(ServerPlayer p) {
      JsonObject obj = new JsonObject();
      obj.addProperty("name", p.getName().getString());
      obj.addProperty("uuid", p.getStringUUID());
      obj.addProperty("world", p.serverLevel().dimension().location().toString());
      obj.addProperty("health", p.getHealth());
      obj.addProperty("maxHealth", p.getMaxHealth());
      obj.addProperty("foodLevel", p.getFoodData().getFoodLevel());
      obj.addProperty("xp", p.experienceLevel);
      obj.addProperty("ping", p.connection.latency());
      obj.addProperty("gamemode", p.gameMode.getGameModeForPlayer().getName());
      obj.addProperty("x", p.getBlockX());
      obj.addProperty("y", p.getBlockY());
      obj.addProperty("z", p.getBlockZ());

      try {
         int playTicks = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
         obj.addProperty("playTimeSeconds", playTicks / 20);
      } catch (Exception var5) {
         obj.addProperty("playTimeSeconds", 0);
      }

      try {
         BigDecimal bal = EconomyManager.getInstance().getBalance(p.getUUID());
         obj.addProperty("balance", bal.doubleValue());
      } catch (Exception var4) {
         obj.addProperty("balance", 0.0);
      }

      return obj;
   }

   private void handleUserActivityLog(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();

      try {
         int limit = 100;
         String query = exchange.getRequestURI().getQuery();
         if (query != null) {
            for (String param : query.split("&")) {
               String[] kv = param.split("=", 2);
               if (kv.length == 2 && "limit".equals(kv[0])) {
                  try {
                     limit = Integer.parseInt(kv[1]);
                  } catch (NumberFormatException var11) {
                  }
               }
            }
         }

         Path auditLog = Paths.get("neoessentials", "dashboard_audit.log");
         JsonArray entries = new JsonArray();
         if (Files.exists(auditLog)) {
            List<String> lines = Files.readAllLines(auditLog, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);

            for (int i = lines.size() - 1; i >= start; i--) {
               String line = lines.get(i).trim();
               if (!line.isEmpty()) {
                  entries.add(line);
               }
            }
         }

         response.addProperty("success", true);
         response.addProperty("total", entries.size());
         response.add("log", entries);
      } catch (Exception var12) {
         LOGGER.error("Error reading user activity log: {}", var12.getMessage(), var12);
         response.addProperty("success", false);
         response.addProperty("error", var12.getMessage());
      }

      this.sendJsonResponse(exchange, 200, response);
   }

   private Path resolvePath(String pathParam) {
      return pathParam.isEmpty() ? Paths.get(".") : Paths.get(pathParam).normalize();
   }

   private void validatePath(Path path) throws SecurityException {
      Path normalized = path.normalize().toAbsolutePath();
      boolean allowed = ALLOWED_PATHS.stream().anyMatch(allowedPath -> {
         try {
            Path allowedNormalized = allowedPath.normalize().toAbsolutePath();
            return normalized.startsWith(allowedNormalized);
         } catch (Exception var3x) {
            return false;
         }
      });
      if (!allowed) {
         throw new SecurityException("Access to path denied: " + path);
      }
   }

   private Path createBackup(Path file) throws IOException {
      Path backupDir = Paths.get("neoessentials", "backups", "files");
      Files.createDirectories(backupDir);
      String timestamp = String.valueOf(System.currentTimeMillis());
      String fileName = file.getFileName().toString();
      Path backupPath = backupDir.resolve(fileName + "." + timestamp + ".backup");
      if (Files.isDirectory(file)) {
         return backupDir;
      } else {
         Files.copy(file, backupPath);
         return backupPath;
      }
   }

   private void deleteDirectory(Path directory) throws IOException {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
         @NotNull
         public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         @NotNull
         public FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
         }
      });
   }

   private String getFileExtension(String fileName) {
      int lastDot = fileName.lastIndexOf(46);
      return lastDot > 0 && lastDot < fileName.length() - 1 ? fileName.substring(lastDot) : "";
   }

   private Map<String, String> parseQueryParams(String query) {
      return query != null && !query.isEmpty()
         ? Arrays.stream(query.split("&"))
            .map(param -> param.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(
               Collectors.toMap(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
            )
         : Collections.emptyMap();
   }

   private String readRequestBody(HttpExchange exchange) throws IOException {
      String var4;
      try (
         InputStream is = exchange.getRequestBody();
         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      ) {
         var4 = reader.lines().collect(Collectors.joining("\n"));
      }

      return var4;
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
      byte[] response = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.sendResponseHeaders(statusCode, (long)response.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(response);
      }
   }

   private JsonObject createErrorResponse(String message) {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("timestamp", System.currentTimeMillis());
      return error;
   }
}
