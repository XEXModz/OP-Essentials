package com.zerog.neoessentials.webdashboard;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.webdashboard.api.endpoints.AdminEndpoint;
import com.zerog.neoessentials.webdashboard.api.endpoints.GameEndpoint;
import com.zerog.neoessentials.webdashboard.api.endpoints.LoggingEndpoint;
import com.zerog.neoessentials.webdashboard.api.endpoints.PlayerEndpoint;
import com.zerog.neoessentials.webdashboard.api.endpoints.ServerEndpoint;
import com.zerog.neoessentials.webdashboard.endpoints.PermissionEndpoint;
import com.zerog.neoessentials.webdashboard.handlers.AuthHandler;
import com.zerog.neoessentials.webdashboard.handlers.FileManagementHandler;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardAPI {
   private static final Logger LOGGER = LoggerFactory.getLogger(DashboardAPI.class);
   private static DashboardAPI INSTANCE;
   private HttpServer apiServer;
   private ExecutorService executor;
   private boolean running = false;
   private MinecraftServer server;
   private final Map<String, ArrayDeque<Long>> rateLimitMap = new ConcurrentHashMap<>();

   private DashboardAPI() {
   }

   public static DashboardAPI getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DashboardAPI();
      }

      return INSTANCE;
   }

   public void setServer(MinecraftServer server) {
      this.server = server;
   }

   public MinecraftServer getServer() {
      return this.server;
   }

   public int getPort() {
      return ConfigManager.getInstance().getWebDashboardPort();
   }

   public String getBindAddress() {
      return ConfigManager.getInstance().getWebDashboardBindAddress();
   }

   public void start() {
      if (this.running) {
         LOGGER.warn("Dashboard API is already running");
      } else if (this.server == null) {
         LOGGER.error("Cannot start Dashboard API: MinecraftServer not set");
      } else {
         try {
            int port = this.getPort();
            String bindAddress = this.getBindAddress();
            LOGGER.info("Starting Dashboard API on {}:{}", bindAddress, port);

            try {
               InetSocketAddress address = new InetSocketAddress(bindAddress, port);
               this.apiServer = HttpServer.create(address, 0);
            } catch (BindException var7) {
               LOGGER.warn("Cannot bind to {}:{}. Error: {}", new Object[]{bindAddress, port, var7.getMessage()});
               if ("0.0.0.0".equals(bindAddress)) {
                  LOGGER.error("Cannot bind to any interface on port {}!", port);
                  LOGGER.error("Possible solutions:");
                  LOGGER.error("  1. Change the port in config/neoessentials/config.json → webDashboard.port");
                  LOGGER.error("  2. Check if another application is using port {}", port);
                  LOGGER.error("  3. Verify your server's firewall and network settings");
                  throw var7;
               }

               LOGGER.info("Attempting fallback to 0.0.0.0:{} (all interfaces)...", port);

               try {
                  InetSocketAddress addressx = new InetSocketAddress("0.0.0.0", port);
                  this.apiServer = HttpServer.create(addressx, 0);
                  LOGGER.info("Successfully bound to fallback address 0.0.0.0:{}", port);
                  bindAddress = "0.0.0.0";
               } catch (BindException var6) {
                  LOGGER.error("Fallback also failed! Port {} may be in use or system doesn't support network binding.", port);
                  LOGGER.error("Possible solutions:");
                  LOGGER.error("  1. Change the port in config/neoessentials/config.json → webDashboard.port");
                  LOGGER.error("  2. Check if another application is using port {}", port);
                  LOGGER.error("  3. Verify your server's network configuration");
                  throw var6;
               }
            }

            this.executor = Executors.newFixedThreadPool(10);
            this.apiServer.setExecutor(this.executor);
            this.registerEndpoints();
            this.apiServer.start();
            this.running = true;
            ConfigManager config = ConfigManager.getInstance();
            String dashboardUrl = config.getWebDashboardUrl();
            LOGGER.info("Dashboard API started successfully on {}:{}", bindAddress, port);
            LOGGER.info("Access the dashboard at: {}", dashboardUrl);
            LOGGER.info("API Endpoints available at: {}/api/", dashboardUrl);
         } catch (IOException var8) {
            LOGGER.error("Failed to start Dashboard API server", var8);
            this.running = false;
         }
      }
   }

   public void stop() {
      if (this.running && this.apiServer != null) {
         try {
            LOGGER.info("Stopping Dashboard API server...");
            this.apiServer.stop(2);
            if (this.executor != null) {
               this.executor.shutdown();

               try {
                  if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                     LOGGER.warn("Dashboard executor did not terminate gracefully, forcing shutdown...");
                     this.executor.shutdownNow();
                     if (!this.executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                        LOGGER.error("Dashboard executor did not terminate after forced shutdown");
                     }
                  }
               } catch (InterruptedException var2) {
                  LOGGER.warn("Interrupted while waiting for Dashboard executor shutdown");
                  this.executor.shutdownNow();
                  Thread.currentThread().interrupt();
               }
            }

            this.running = false;
            LOGGER.info("Dashboard API stopped successfully");
         } catch (Exception var3) {
            LOGGER.error("Error stopping Dashboard API", var3);
         }
      }
   }

   private HttpHandler withAuth(HttpHandler handler) {
      return exchange -> {
         try {
            ConfigManager cfg = ConfigManager.getInstance();
            if (cfg.isDashboardRateLimitingEnabled()) {
               String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
               int maxReq = cfg.getDashboardMaxRequestsPerMinute();
               long now = System.currentTimeMillis();
               long windowStart = now - 60000L;
               this.rateLimitMap.compute(ip, (k, deque) -> {
                  if (deque == null) {
                     deque = new ArrayDeque<>();
                  }

                  while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                     deque.pollFirst();
                  }

                  deque.addLast(now);
                  return deque;
               });
               int reqCount = this.rateLimitMap.get(ip).size();
               if (reqCount > maxReq) {
                  String response = "{\"success\":false,\"error\":\"Rate limit exceeded. Max " + maxReq + " requests/min.\"}";
                  byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                  exchange.getResponseHeaders().set("Content-Type", "application/json");
                  CorsHandler.apply(exchange);
                  exchange.getResponseHeaders().set("Retry-After", "60");
                  exchange.sendResponseHeaders(429, (long)bytes.length);

                  try (OutputStream os = exchange.getResponseBody()) {
                     os.write(bytes);
                  }

                  LOGGER.debug("Rate limit exceeded for IP {} ({}/{})", new Object[]{ip, reqCount, maxReq});
                  return;
               }
            }

            if (cfg.isDashboardAuthRequired()) {
               String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
               String token = null;
               if (authHeader != null && authHeader.startsWith("Bearer ")) {
                  token = authHeader.substring(7);
               }

               if (!AuthHandler.validateToken(token)) {
                  LOGGER.debug("Unauthorized API request to {} - token: {}", exchange.getRequestURI(), token == null ? "null" : "invalid");
                  String response = "{\"success\":false,\"error\":\"Unauthorized - Please login first\"}";
                  byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                  exchange.getResponseHeaders().set("Content-Type", "application/json");
                  CorsHandler.apply(exchange);
                  exchange.sendResponseHeaders(401, (long)bytes.length);

                  try (OutputStream os = exchange.getResponseBody()) {
                     os.write(bytes);
                  }

                  return;
               }

               exchange.setAttribute("auth-username", AuthHandler.getUsername(token));
               exchange.setAttribute("auth-admin", AuthHandler.isAdmin(token));
               LOGGER.debug("Authenticated API request to {} by {}", exchange.getRequestURI(), AuthHandler.getUsername(token));
            }

            handler.handle(exchange);
         } catch (Exception var23) {
            Exception e = var23;
            LOGGER.error("Error in auth middleware for {}", exchange.getRequestURI(), var23);

            try {
               if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                  String errorResponse = "{\"success\":false,\"error\":\"Authentication error: " + e.getMessage() + "\"}";
                  byte[] bytes = errorResponse.getBytes(StandardCharsets.UTF_8);
                  exchange.getResponseHeaders().set("Content-Type", "application/json");
                  CorsHandler.apply(exchange);
                  exchange.sendResponseHeaders(500, (long)bytes.length);

                  try (OutputStream os = exchange.getResponseBody()) {
                     os.write(bytes);
                  }
               }
            } catch (Exception var20) {
               LOGGER.error("Failed to send error response", var20);
            }
         }
      };
   }

   private void registerEndpoints() {
      this.apiServer.createContext("/api/auth", new AuthHandler(this.server));
      this.apiServer.createContext("/api/player", this.withAuth(new PlayerEndpoint(this.server)));
      this.apiServer.createContext("/api/server", this.withAuth(new ServerEndpoint(this.server)));
      this.apiServer.createContext("/api/game", this.withAuth(new GameEndpoint(this.server)));
      this.apiServer.createContext("/api/logging", this.withAuth(new LoggingEndpoint()));
      this.apiServer.createContext("/api/admin", this.withAuth(new AdminEndpoint(this.server)));
      this.apiServer.createContext("/api/files", this.withAuth(new FileManagementHandler()));
      this.apiServer.createContext("/api/permissions", this.withAuth(new PermissionEndpoint(this.server)));
      LOGGER.info("API endpoints registered:");
      LOGGER.info("  - /api/auth/* (login, logout, validate, discord)");
      LOGGER.info("  - /api/textures/* (item, block textures from server resource packs)");
      LOGGER.info("  - /api/player/* (profile, stats, achievements, inventory, status, health, xp, location, homes, online) [AUTH REQUIRED]");
      LOGGER.info("  - /api/server/* (profile, performance, worlds, players, entities, memory, history, assets) [AUTH REQUIRED]");
      LOGGER.info("  - /api/game/* (statistics, events, activity, blocks) [AUTH REQUIRED]");
      LOGGER.info("  - /api/logging/* (requests, errors, performance) [AUTH REQUIRED]");
      LOGGER.info("  - /api/admin/* (restart, stop, reload, save) [AUTH REQUIRED - ADMIN ONLY]");
      LOGGER.info("  - /api/files/* (browse, read, write, create, upload, delete, backup, restore, cloud) [AUTH REQUIRED]");
      LOGGER.info("  - /api/permissions/* (overview, groups, users, manage) [AUTH REQUIRED - ADMIN ONLY]");

      try (InputStream testStream = this.getClass().getResourceAsStream("/webdashboard/index.html")) {
         if (testStream != null) {
            LOGGER.info("Dashboard resources verified - index.html found");
         } else {
            LOGGER.error("Dashboard resources NOT found - /webdashboard/index.html is null!");
         }
      } catch (Exception var6) {
         LOGGER.error("Error checking dashboard resources", var6);
      }

      this.apiServer.createContext("/", exchange -> {
         String path = exchange.getRequestURI().getPath();
         LOGGER.debug("Serving static file: {}", path);
         if (path.equals("/") || path.equals("/index.html")) {
            path = "/index.html";
         }

         try {
            try (InputStream in = this.getClass().getResourceAsStream("/webdashboard" + path)) {
               if (in != null) {
                  byte[] bytes = in.readAllBytes();
                  String etag = "\"" + Integer.toHexString(Arrays.hashCode(bytes)) + "\"";
                  String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                  if (etag.equals(ifNoneMatch)) {
                     exchange.sendResponseHeaders(304, -1L);
                     LOGGER.debug("Served 304 Not Modified for: {} (ETag: {})", path, etag);
                     return;
                  } else {
                     String contentType = this.getContentType(path);
                     exchange.getResponseHeaders().set("Content-Type", contentType);
                     CorsHandler.apply(exchange);
                     exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
                     exchange.getResponseHeaders().set("Pragma", "no-cache");
                     exchange.getResponseHeaders().set("Expires", "0");
                     exchange.getResponseHeaders().set("ETag", etag);
                     exchange.getResponseHeaders().set("Last-Modified", "Fri, 03 Jan 2026 00:00:00 GMT");
                     exchange.getResponseHeaders().set("X-NeoEssentials-Version", this.getBuildNumber());
                     exchange.sendResponseHeaders(200, (long)bytes.length);

                     try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                     }

                     LOGGER.debug("Successfully served: {} ({} bytes, ETag: {})", new Object[]{path, bytes.length, etag});
                     return;
                  }
               }
            } catch (FileNotFoundException var31) {
               LOGGER.warn("File not found: {}", path);
               String response = "404 Not Found: " + path;
               byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
               exchange.getResponseHeaders().set("Content-Type", "text/plain");
               exchange.sendResponseHeaders(404, (long)bytes.length);

               try (OutputStream os = exchange.getResponseBody()) {
                  os.write(bytes);
               }
            } catch (Exception var32) {
               LOGGER.error("Error serving file: {}", path, var32);
               String response = "500 Internal Server Error: " + var32.getMessage();
               byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
               exchange.getResponseHeaders().set("Content-Type", "text/plain");
               exchange.sendResponseHeaders(500, (long)bytes.length);

               try (OutputStream os = exchange.getResponseBody()) {
                  os.write(bytes);
               }
            }
         } finally {
            exchange.close();
         }
      });
      LOGGER.info("Static file serving enabled for frontend");
   }

   private String getContentType(String path) {
      if (path.endsWith(".html")) {
         return "text/html";
      } else if (path.endsWith(".css")) {
         return "text/css";
      } else if (path.endsWith(".js")) {
         return "application/javascript";
      } else if (path.endsWith(".json")) {
         return "application/json";
      } else if (path.endsWith(".png")) {
         return "image/png";
      } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
         return "image/jpeg";
      } else {
         return path.endsWith(".svg") ? "image/svg+xml" : "text/plain";
      }
   }

   private String getBuildNumber() {
      try (InputStream in = this.getClass().getResourceAsStream("/build_number.txt")) {
         return in != null ? new String(in.readAllBytes(), StandardCharsets.UTF_8).trim() : "unknown";
      } catch (Exception var6) {
         LOGGER.debug("Could not read build number: {}", var6.getMessage());
         return "unknown";
      }
   }

   public boolean isRunning() {
      return this.running;
   }
}
