package com.zerog.neoessentials.webdashboard.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.webdashboard.security.AuthenticationManager;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import com.zerog.neoessentials.webdashboard.security.DiscordAuthProvider;
import com.zerog.neoessentials.webdashboard.security.DiscordUser;
import com.zerog.neoessentials.webdashboard.security.Session;
import com.zerog.neoessentials.webdashboard.security.User;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AuthHandler.class);
   private final MinecraftServer server;
   private static final Map<String, AuthHandler.SessionData> sessions = new ConcurrentHashMap<>();
   private static final long SESSION_TIMEOUT = 86400000L;
   private static final SecureRandom random = new SecureRandom();
   private static final String DASHBOARD_VIEW_PERMISSION = "neoessentials.dashboard.view";
   private static final String DASHBOARD_ADMIN_PERMISSION = "neoessentials.dashboard.admin";

   public AuthHandler(MinecraftServer server) {
      this.server = server;
   }

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();

      try {
         if (path.endsWith("/auth/login") && "POST".equals(method)) {
            this.handleLogin(exchange);
         } else if (path.endsWith("/auth/logout") && "POST".equals(method)) {
            this.handleLogout(exchange);
         } else if (path.endsWith("/auth/validate") && "GET".equals(method)) {
            this.handleValidate(exchange);
         } else if (path.endsWith("/auth/discord") && "POST".equals(method)) {
            this.handleDiscordAuth(exchange);
         } else {
            this.sendError(exchange, 404, "Not Found");
         }
      } catch (Exception var5) {
         LOGGER.error("Error handling auth request", var5);
         this.sendError(exchange, 500, "Internal Server Error");
      }
   }

   private void handleLogin(HttpExchange exchange) throws IOException {
      try {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         JsonObject request = JsonParser.parseString(body).getAsJsonObject();
         String username = request.has("username") ? request.get("username").getAsString() : null;
         String password = request.has("password") ? request.get("password").getAsString() : null;
         if (username == null || username.isEmpty()) {
            this.sendError(exchange, 400, "Username is required");
            return;
         }

         if (password != null && !password.isEmpty()) {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            Session authSession = authManager.authenticate(username, password, ipAddress, userAgent != null ? userAgent : "Dashboard");
            if (authSession == null) {
               this.sendError(exchange, 401, "Invalid username or password");
               return;
            }

            String token = authSession.getSessionId();
            boolean isAdmin = authSession.getRole() == User.Role.ADMIN;
            AuthHandler.SessionData session = new AuthHandler.SessionData(
               authSession.getUsername(), authSession.getUserId(), "password", isAdmin, System.currentTimeMillis()
            );
            sessions.put(token, session);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("token", token);
            response.addProperty("sessionId", token);
            response.addProperty("username", authSession.getUsername());
            response.addProperty("isAdmin", isAdmin);
            response.addProperty("authType", "password");
            JsonObject userObj = new JsonObject();
            userObj.addProperty("username", authSession.getUsername());
            userObj.addProperty("role", authSession.getRole().name());
            userObj.addProperty("isAdmin", isAdmin);
            response.add("user", userObj);
            if (authSession.requiresPasswordChange()) {
               response.addProperty("requiresPasswordChange", true);
            }

            this.sendJson(exchange, 200, response.toString());
            LOGGER.info("User {} authenticated to dashboard via password (admin: {})", username, isAdmin);
            return;
         }

         ServerPlayer player = this.server.getPlayerList().getPlayerByName(username);
         if (player == null) {
            this.sendError(exchange, 401, "Player must be online on the server to authenticate");
            return;
         }

         boolean isOp = this.server.getPlayerList().isOp(player.getGameProfile());
         boolean hasViewPermission = isOp || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.dashboard.view");
         boolean hasAdminPermission = isOp || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.dashboard.admin");
         if (!hasViewPermission) {
            this.sendError(exchange, 403, "You don't have permission to access the dashboard");
            return;
         }

         String token = this.generateToken();
         AuthHandler.SessionData session = new AuthHandler.SessionData(
            username, player.getStringUUID(), "minecraft", hasAdminPermission, System.currentTimeMillis()
         );
         sessions.put(token, session);
         this.cleanupSessions();
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("token", token);
         response.addProperty("username", username);
         response.addProperty("isAdmin", hasAdminPermission);
         response.addProperty("authType", "minecraft");
         this.sendJson(exchange, 200, response.toString());
         LOGGER.info("Player {} authenticated to dashboard (admin: {})", username, hasAdminPermission);
      } catch (Exception var15) {
         LOGGER.error("Error during login", var15);
         this.sendError(exchange, 500, "Authentication failed");
      }
   }

   private void handleDiscordAuth(HttpExchange exchange) throws IOException {
      try {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         JsonObject request = JsonParser.parseString(body).getAsJsonObject();
         String discordId = request.has("discordId") ? request.get("discordId").getAsString() : null;
         String discordUsername = request.has("username") ? request.get("username").getAsString() : null;
         if (discordId == null || discordUsername == null) {
            this.sendError(exchange, 400, "Discord ID and username are required");
            return;
         }

         boolean hasPermission = this.checkDiscordPermission(discordId);
         if (!hasPermission) {
            this.sendError(exchange, 403, "Your Discord role doesn't have permission to access the dashboard");
            return;
         }

         String token = this.generateToken();
         AuthHandler.SessionData session = new AuthHandler.SessionData(
            discordUsername, discordId, "discord", this.checkDiscordAdminPermission(discordId), System.currentTimeMillis()
         );
         sessions.put(token, session);
         this.cleanupSessions();
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("token", token);
         response.addProperty("username", discordUsername);
         response.addProperty("isAdmin", session.isAdmin);
         response.addProperty("authType", "discord");
         this.sendJson(exchange, 200, response.toString());
         LOGGER.info("Discord user {} authenticated to dashboard (admin: {})", discordUsername, session.isAdmin);
      } catch (Exception var10) {
         LOGGER.error("Error during Discord authentication", var10);
         this.sendError(exchange, 500, "Authentication failed");
      }
   }

   private void handleValidate(HttpExchange exchange) throws IOException {
      String token = this.getTokenFromHeader(exchange);
      if (token == null) {
         this.sendError(exchange, 401, "No token provided");
      } else {
         AuthHandler.SessionData session = sessions.get(token);
         if (session != null && !isExpired(session)) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("username", session.username);
            response.addProperty("isAdmin", session.isAdmin);
            response.addProperty("authType", session.authType);
            this.sendJson(exchange, 200, response.toString());
         } else {
            if (session != null) {
               sessions.remove(token);
            }

            this.sendError(exchange, 401, "Invalid or expired token");
         }
      }
   }

   private void handleLogout(HttpExchange exchange) throws IOException {
      String token = this.getTokenFromHeader(exchange);
      if (token != null) {
         sessions.remove(token);
      }

      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      this.sendJson(exchange, 200, response.toString());
   }

   public static boolean validateToken(String token) {
      if (token == null) {
         return false;
      } else {
         AuthHandler.SessionData session = sessions.get(token);
         if (session != null && !isExpired(session)) {
            return true;
         } else {
            if (session != null) {
               sessions.remove(token);
            }

            return false;
         }
      }
   }

   public static boolean isAdmin(String token) {
      AuthHandler.SessionData session = sessions.get(token);
      return session != null && !isExpired(session) && session.isAdmin;
   }

   public static String getUsername(String token) {
      AuthHandler.SessionData session = sessions.get(token);
      return session != null && !isExpired(session) ? session.username : null;
   }

   private String generateToken() {
      byte[] bytes = new byte[32];
      random.nextBytes(bytes);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   }

   private String getTokenFromHeader(HttpExchange exchange) {
      String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
      return authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
   }

   private static boolean isExpired(AuthHandler.SessionData session) {
      return System.currentTimeMillis() - session.timestamp > 86400000L;
   }

   private void cleanupSessions() {
      sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
   }

   private boolean checkDiscordPermission(String discordId) {
      List<String> allowedRoles = this.getDiscordAllowedRoles();
      DiscordAuthProvider provider = DiscordAuthProvider.getInstance();
      if (provider.isAvailable()) {
         DiscordUser discordUser = provider.getLinkedAccountByDiscordId(discordId);
         if (discordUser == null) {
            LOGGER.debug("Discord user {} has no linked Minecraft account via SDLink", discordId);
            return false;
         } else {
            List<String> userRoles = discordUser.getDiscordRoles();

            for (String allowed : allowedRoles) {
               if (userRoles.contains(allowed)) {
                  LOGGER.debug("Discord user {} has allowed role: {}", discordId, allowed);
                  return true;
               }
            }

            LOGGER.debug("Discord user {} has no matching allowed roles. Has: {}", discordId, userRoles);
            return false;
         }
      } else {
         LOGGER.debug("SDLink not available — standalone Discord auth mode for user {}", discordId);
         return allowedRoles.contains("*") || allowedRoles.isEmpty();
      }
   }

   private boolean checkDiscordAdminPermission(String discordId) {
      List<String> adminRoles = this.getDiscordAdminRoles();
      DiscordAuthProvider provider = DiscordAuthProvider.getInstance();
      if (!provider.isAvailable()) {
         return false;
      } else {
         DiscordUser discordUser = provider.getLinkedAccountByDiscordId(discordId);
         if (discordUser != null) {
            List<String> userRoles = discordUser.getDiscordRoles();

            for (String adminRole : adminRoles) {
               if (userRoles.contains(adminRole)) {
                  LOGGER.debug("Discord user {} has admin role: {}", discordId, adminRole);
                  return true;
               }
            }
         }

         return false;
      }
   }

   private List<String> getDiscordAllowedRoles() {
      List<String> roles = new ArrayList<>();

      try {
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         if (config.has("webDashboard") && config.getAsJsonObject("webDashboard").has("discord")) {
            JsonObject discord = config.getAsJsonObject("webDashboard").getAsJsonObject("discord");
            if (discord.has("allowedRoles") && discord.get("allowedRoles").isJsonArray()) {
               for (JsonElement el : discord.getAsJsonArray("allowedRoles")) {
                  if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                     roles.add(el.getAsString());
                  }
               }
            }
         }
      } catch (Exception var6) {
         LOGGER.warn("Failed to read Discord allowed roles from config", var6);
      }

      if (roles.isEmpty()) {
         roles.addAll(Arrays.asList("Admin", "Moderator", "Staff"));
      }

      return roles;
   }

   private List<String> getDiscordAdminRoles() {
      List<String> roles = new ArrayList<>();

      try {
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         if (config.has("webDashboard") && config.getAsJsonObject("webDashboard").has("discord")) {
            JsonObject discord = config.getAsJsonObject("webDashboard").getAsJsonObject("discord");
            if (discord.has("adminRoles") && discord.get("adminRoles").isJsonArray()) {
               for (JsonElement el : discord.getAsJsonArray("adminRoles")) {
                  if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                     roles.add(el.getAsString());
                  }
               }
            }
         }
      } catch (Exception var6) {
         LOGGER.warn("Failed to read Discord admin roles from config", var6);
      }

      if (roles.isEmpty()) {
         roles.add("Admin");
      }

      return roles;
   }

   private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
      byte[] response = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(code, (long)response.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(response);
      }
   }

   private void sendError(HttpExchange exchange, int code, String message) throws IOException {
      JsonObject error = new JsonObject();
      error.addProperty("success", false);
      error.addProperty("error", message);
      this.sendJson(exchange, code, error.toString());
   }

   private static class SessionData {
      final String username;
      final String userId;
      final String authType;
      final boolean isAdmin;
      final long timestamp;

      SessionData(String username, String userId, String authType, boolean isAdmin, long timestamp) {
         this.username = username;
         this.userId = userId;
         this.authType = authType;
         this.isAdmin = isAdmin;
         this.timestamp = timestamp;
      }
   }
}
