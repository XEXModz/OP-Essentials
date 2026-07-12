package com.zerog.neoessentials.webdashboard.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.permissions.ExternalPermissionAdapter;
import com.zerog.neoessentials.permissions.LuckPermsAdapter;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import com.zerog.neoessentials.webdashboard.DashboardAPI;
import com.zerog.neoessentials.webdashboard.security.AuthenticationManager;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import com.zerog.neoessentials.webdashboard.security.DiscordAuthConfig;
import com.zerog.neoessentials.webdashboard.security.DiscordAuthProvider;
import com.zerog.neoessentials.webdashboard.security.DiscordUser;
import com.zerog.neoessentials.webdashboard.security.Session;
import com.zerog.neoessentials.webdashboard.security.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationHandler.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      CorsHandler.applyFull(exchange, "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
      if ("OPTIONS".equals(exchange.getRequestMethod())) {
         exchange.sendResponseHeaders(204, -1L);
      } else {
         String method = exchange.getRequestMethod();
         String path = exchange.getRequestURI().getPath();

         try {
            if ("POST".equals(method) && path.endsWith("/login")) {
               this.handleLogin(exchange);
            } else if ("GET".equals(method) && path.endsWith("/discord")) {
               this.handleDiscordAuth(exchange);
            } else if ("GET".equals(method) && path.contains("/discord/callback")) {
               this.handleDiscordOAuthCallback(exchange);
            } else if ("GET".equals(method) && path.endsWith("/discord/authorize")) {
               this.handleDiscordAuthorizeRedirect(exchange);
            } else if ("POST".equals(method) && path.endsWith("/logout")) {
               this.handleLogout(exchange);
            } else if ("GET".equals(method) && path.endsWith("/validate")) {
               this.handleValidate(exchange);
            } else if ("GET".equals(method) && path.endsWith("/users")) {
               this.handleGetUsers(exchange);
            } else if ("POST".equals(method) && path.endsWith("/users")) {
               this.handleCreateUser(exchange);
            } else if ("PUT".equals(method) && path.contains("/users/")) {
               this.handleUpdateUser(exchange);
            } else if ("DELETE".equals(method) && path.contains("/users/")) {
               this.handleDeleteUser(exchange);
            } else if ("GET".equals(method) && path.endsWith("/sessions")) {
               this.handleGetSessions(exchange);
            } else if (!"GET".equals(method) || !path.endsWith("/session") && !path.equals("/api/session")) {
               if ("POST".equals(method) && path.endsWith("/change-password")) {
                  this.handleChangePassword(exchange);
               } else {
                  this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid endpoint"));
               }
            } else {
               this.handleGetCurrentSession(exchange);
            }
         } catch (Exception var5) {
            LOGGER.error("Error handling authentication request", var5);
            this.sendJsonResponse(exchange, 500, this.createErrorResponse("Internal server error: " + var5.getMessage()));
         }
      }
   }

   private void handleChangePassword(HttpExchange exchange) throws IOException {
      String sessionId = this.getSessionIdFromCookie(exchange);
      if (sessionId == null) {
         this.sendJsonResponse(exchange, 401, this.createErrorResponse("No active session"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         Session session = authManager.validateSession(sessionId);
         if (session == null) {
            this.sendJsonResponse(exchange, 401, this.createErrorResponse("Invalid or expired session"));
         } else {
            String userId = session.getUserId();
            User user = authManager.getUser(userId);
            if (user == null) {
               this.sendJsonResponse(exchange, 404, this.createErrorResponse("User not found"));
            } else {
               String requestBody = this.readRequestBody(exchange);
               JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
               if (request.has("oldPassword") && request.has("newPassword")) {
                  String oldPassword = request.get("oldPassword").getAsString();
                  String newPassword = request.get("newPassword").getAsString();
                  String oldHash = authManager.hashPassword(oldPassword);
                  if (!oldHash.equals(user.getPasswordHash())) {
                     this.sendJsonResponse(exchange, 403, this.createErrorResponse("Old password is incorrect"));
                  } else {
                     try {
                        authManager.updatePassword(userId, newPassword);
                        user.setRequiresPasswordChange(false);
                        user.setTempPassword(false);
                        authManager.saveUsers();
                        if (LOGGER.isDebugEnabled()) {
                           LOGGER.debug(
                              "Password changed for user '{}': requiresPasswordChange={}, isTempPassword={}",
                              new Object[]{user.getUsername(), user.requiresPasswordChange(), user.isTempPassword()}
                           );
                           Session sessionObj = authManager.validateSession(sessionId);
                           if (ConfigManager.isDebugModeEnabled()) {
                              LOGGER.debug(
                                 "Session state after password change: sessionId={}, active={}, requiresPasswordChange={}",
                                 new Object[]{
                                    sessionId,
                                    sessionObj != null ? sessionObj.isActive() : "null",
                                    sessionObj != null ? sessionObj.requiresPasswordChange() : "null"
                                 }
                              );
                           }
                        }

                        authManager.logout(sessionId);
                        JsonObject response = new JsonObject();
                        response.addProperty("success", true);
                        response.addProperty("message", "Password changed successfully. Please log in again.");
                        this.sendJsonResponse(exchange, 200, response);
                     } catch (IllegalArgumentException var13) {
                        this.sendJsonResponse(exchange, 400, this.createErrorResponse(var13.getMessage()));
                     }
                  }
               } else {
                  this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing oldPassword or newPassword"));
               }
            }
         }
      }
   }

   private void handleLogin(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
      String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
      AuthenticationManager authManager = AuthenticationManager.getInstance();
      if (request.has("discordCode")) {
         Session session = this.handleDiscordOAuth(request.get("discordCode").getAsString(), ipAddress, userAgent);
         if (session == null) {
            this.sendJsonResponse(exchange, 401, this.createErrorResponse("Discord authentication failed"));
         } else {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("sessionId", session.getSessionId());
            response.addProperty("authType", "discord");
            response.add("session", session.toJson());
            User user = authManager.getUser(session.getUserId());
            if (user != null) {
               response.add("user", user.toJson());
            }

            exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + session.getSessionId() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
            this.sendJsonResponse(exchange, 200, response);
         }
      } else if (!request.has("username")) {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing username"));
      } else {
         String username = request.get("username").getAsString();
         if (request.has("type") && "minecraft".equals(request.get("type").getAsString())) {
            LOGGER.warn("Legacy Minecraft auth used by {}, this method is deprecated - use registration-based auth instead", username);
            Session session = this.handleMinecraftAuth(username, ipAddress, userAgent);
            if (session == null) {
               this.sendJsonResponse(exchange, 403, this.createErrorResponse("You don't have permission to access the dashboard or are not online"));
            } else {
               JsonObject response = new JsonObject();
               response.addProperty("success", true);
               response.addProperty("sessionId", session.getSessionId());
               response.addProperty("authType", "minecraft");
               response.add("session", session.toJson());
               User user = authManager.getUser(session.getUserId());
               if (user != null) {
                  response.add("user", user.toJson());
               }

               exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + session.getSessionId() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
               this.sendJsonResponse(exchange, 200, response);
            }
         } else if (!request.has("password")) {
            this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing password"));
         } else {
            String password = request.get("password").getAsString();
            Session session = authManager.authenticate(username, password, ipAddress, userAgent);
            if (ConfigManager.isDebugModeEnabled()) {
               if (session != null) {
                  LOGGER.debug(
                     "Session created for user '{}': sessionId={}, requiresPasswordChange={}",
                     new Object[]{username, session.getSessionId(), session.requiresPasswordChange()}
                  );
               } else {
                  LOGGER.debug("Login failed for user '{}': no session created", username);
               }
            }

            if (session == null) {
               this.sendJsonResponse(exchange, 401, this.createErrorResponse("Invalid credentials or account locked"));
            } else {
               JsonObject response = new JsonObject();
               response.addProperty("success", true);
               response.addProperty("sessionId", session.getSessionId());
               response.addProperty("authType", "password");
               response.add("session", session.toJson());
               User user = authManager.getUser(session.getUserId());
               if (user != null) {
                  response.add("user", user.toJson());
               }

               exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + session.getSessionId() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
               this.sendJsonResponse(exchange, 200, response);
            }
         }
      }
   }

   private Session handleMinecraftAuth(String minecraftUsername, String ipAddress, String userAgent) {
      try {
         MinecraftServer server = DashboardAPI.getInstance().getServer();
         if (server == null) {
            LOGGER.error("Cannot authenticate: Server instance not available");
            return null;
         } else {
            UUID playerUuid = null;
            GameProfile profile = (GameProfile)server.getProfileCache().get(minecraftUsername).orElse(null);
            if (profile != null) {
               playerUuid = profile.getId();
               LOGGER.debug("Found player UUID from server profile cache: {}", playerUuid);
            }

            if (playerUuid == null) {
               PermissionManager permManager = PermissionAPI.getManager();
               if (permManager != null) {
                  for (PermissionUser permUser : permManager.getUsers()) {
                     GameProfile cachedProfile = (GameProfile)server.getProfileCache().get(permUser.getUuid()).orElse(null);
                     if (cachedProfile != null && cachedProfile.getName().equalsIgnoreCase(minecraftUsername)) {
                        playerUuid = permUser.getUuid();
                        LOGGER.debug("Found player UUID from permission system: {}", playerUuid);
                        break;
                     }
                  }
               }
            }

            if (playerUuid == null && PermissionAPI.isUsingExternal()) {
               try {
                  ExternalPermissionAdapter adapter = PermissionAPI.getExternalAdapter();
                  if (adapter instanceof LuckPermsAdapter) {
                     LuckPerms luckPerms = LuckPermsProvider.get();
                     net.luckperms.api.model.user.User lpUser = luckPerms.getUserManager().getUser(minecraftUsername);
                     if (lpUser != null) {
                        playerUuid = lpUser.getUniqueId();
                        LOGGER.debug("Found player UUID from LuckPerms: {}", playerUuid);
                     }
                  }
               } catch (Exception var13) {
                  LOGGER.debug("Could not get UUID from LuckPerms: {}", var13.getMessage());
               }
            }

            if (playerUuid == null) {
               try {
                  LOGGER.info("Attempting to fetch UUID from Mojang API for username: {}", minecraftUsername);
                  playerUuid = this.fetchUuidFromMojangAPI(minecraftUsername);
                  if (playerUuid != null) {
                     LOGGER.info("Retrieved UUID from Mojang API: {}", playerUuid);
                  }
               } catch (Exception var12) {
                  LOGGER.warn("Failed to fetch UUID from Mojang API: {}", var12.getMessage());
               }
            }

            if (playerUuid == null) {
               LOGGER.warn("Could not find UUID for player: {} - They may have never joined the server and are not in any permission system", minecraftUsername);
               return null;
            } else {
               boolean hasAccess = PermissionAPI.hasPermission(playerUuid, "neoessentials.dashboard.access");
               if (!hasAccess) {
                  LOGGER.warn("Player {} (UUID: {}) does not have dashboard access permission", minecraftUsername, playerUuid);
                  return null;
               } else {
                  User.Role role = User.Role.VIEWER;
                  if (PermissionAPI.hasPermission(playerUuid, "neoessentials.dashboard.admin")) {
                     role = User.Role.ADMIN;
                  } else if (PermissionAPI.hasPermission(playerUuid, "neoessentials.dashboard.moderator")) {
                     role = User.Role.MODERATOR;
                  }

                  AuthenticationManager authManager = AuthenticationManager.getInstance();
                  User user = authManager.getUserByUsername(minecraftUsername);
                  if (user == null) {
                     String randomPassword = UUID.randomUUID().toString();
                     user = authManager.createUser(minecraftUsername, randomPassword, playerUuid.toString() + "@minecraft", role);
                     LOGGER.info("Auto-created dashboard user for Minecraft player: {} (UUID: {}, Role: {})", new Object[]{minecraftUsername, playerUuid, role});
                  } else if (user.getRole() != role) {
                     user.setRole(role);
                     authManager.saveUsers();
                     LOGGER.info("Updated dashboard role for {} (UUID: {}): {}", new Object[]{minecraftUsername, playerUuid, role});
                  }

                  Session session = authManager.createSession(user.getId(), ipAddress, userAgent);
                  LOGGER.info(
                     "Minecraft player {} (UUID: {}) authenticated to dashboard with role: {} (Offline-capable)",
                     new Object[]{minecraftUsername, playerUuid, role}
                  );
                  return session;
               }
            }
         }
      } catch (Exception var14) {
         LOGGER.error("Error during Minecraft authentication for {}: {}", new Object[]{minecraftUsername, var14.getMessage(), var14});
         return null;
      }
   }

   private Session handleDiscordOAuth(String oauthCode, String ipAddress, String userAgent) {
      DiscordAuthConfig discordConfig = DiscordAuthConfig.load();
      if (!discordConfig.isEnabled()) {
         LOGGER.warn("Discord OAuth2 authentication requested but Discord auth is disabled");
         return null;
      } else if (!discordConfig.isOauth2Configured()) {
         LOGGER.warn("Discord OAuth2 authentication requested but clientId/clientSecret are not configured in discord_auth.json");
         return null;
      } else {
         try {
            String tokenJson = this.exchangeDiscordCode(
               oauthCode, discordConfig.getOauth2ClientId(), discordConfig.getOauth2ClientSecret(), discordConfig.getOauth2RedirectUri()
            );
            if (tokenJson == null) {
               LOGGER.error("Discord token exchange failed — no response");
               return null;
            } else {
               JsonObject tokenResponse = JsonParser.parseString(tokenJson).getAsJsonObject();
               if (tokenResponse.has("error")) {
                  LOGGER.error(
                     "Discord token exchange error: {} — {}",
                     tokenResponse.get("error").getAsString(),
                     tokenResponse.has("error_description") ? tokenResponse.get("error_description").getAsString() : ""
                  );
                  return null;
               } else {
                  String accessToken = tokenResponse.get("access_token").getAsString();
                  String userJson = this.fetchDiscordApi("https://discord.com/api/v10/users/@me", accessToken);
                  if (userJson == null) {
                     LOGGER.error("Failed to fetch Discord user info");
                     return null;
                  } else {
                     JsonObject discordUserObj = JsonParser.parseString(userJson).getAsJsonObject();
                     String discordId = discordUserObj.get("id").getAsString();
                     String discordUsername = discordUserObj.has("global_name") && !discordUserObj.get("global_name").isJsonNull()
                        ? discordUserObj.get("global_name").getAsString()
                        : discordUserObj.get("username").getAsString();
                     LOGGER.info("Discord OAuth2: authenticated Discord user {} (ID: {})", discordUsername, discordId);
                     if (discordConfig.isBlacklisted(discordId)) {
                        LOGGER.warn("Blacklisted Discord user attempted OAuth2 login: {} ({})", discordUsername, discordId);
                        return null;
                     } else {
                        DiscordAuthProvider discordProvider = DiscordAuthProvider.getInstance();
                        List<String> discordRoles;
                        if (discordProvider.isAvailable()) {
                           discordRoles = discordProvider.getDiscordRoles(discordId);
                           LOGGER.debug("Fetched {} Discord roles from SDLink cache for {}", discordRoles.size(), discordId);
                        } else {
                           discordRoles = new ArrayList<>();
                           LOGGER.debug("SDLink not available; role mapping will use empty role list for {}", discordUsername);
                        }

                        if (!discordConfig.passesWhitelist(discordRoles)) {
                           LOGGER.warn("Discord user {} ({}) does not have a whitelisted role, denying OAuth2 login", discordUsername, discordId);
                           return null;
                        } else {
                           User.Role dashboardRole = discordConfig.getHighestRole(discordRoles);
                           String minecraftUsername = null;
                           if (discordProvider.isAvailable()) {
                              DiscordUser linkedAccount = discordProvider.getLinkedAccountByDiscordId(discordId);
                              if (linkedAccount != null && linkedAccount.isLinked()) {
                                 minecraftUsername = linkedAccount.getMinecraftUsername();
                                 LOGGER.info("Discord OAuth2: found linked Minecraft account {} for Discord user {}", minecraftUsername, discordUsername);
                              } else if (discordConfig.requiresLinkedAccount()) {
                                 LOGGER.warn("Discord OAuth2: no linked Minecraft account for {} and requireLinkedAccount=true", discordUsername);
                                 return null;
                              }
                           } else if (discordConfig.requiresLinkedAccount()) {
                              LOGGER.warn("Discord OAuth2: SDLink not available and requireLinkedAccount=true — cannot verify link");
                              return null;
                           }

                           AuthenticationManager authManager = AuthenticationManager.getInstance();
                           String accountUsername = minecraftUsername != null ? minecraftUsername : discordUsername;
                           User user = authManager.getUserByUsername(accountUsername);
                           if (user == null) {
                              if (!discordConfig.allowsAutoRegistration()) {
                                 LOGGER.warn("Discord OAuth2: account not found and auto-registration is disabled for {}", accountUsername);
                                 return null;
                              }

                              String email = discordId + "@discord.oauth";
                              user = authManager.createUser(accountUsername, UUID.randomUUID().toString(), email, dashboardRole);
                              LOGGER.info(
                                 "Discord OAuth2: auto-created dashboard user '{}' with role {} (Discord: {})",
                                 new Object[]{accountUsername, dashboardRole, discordUsername}
                              );
                           } else if (user.getRole() != dashboardRole) {
                              user.setRole(dashboardRole);
                              authManager.saveUsers();
                              LOGGER.info("Discord OAuth2: updated role for '{}' to {} based on Discord roles", accountUsername, dashboardRole);
                           }

                           Session session = authManager.createSession(
                              user.getId(), ipAddress, userAgent != null ? userAgent : "Discord-OAuth2/" + discordUsername
                           );
                           LOGGER.info(
                              "Discord OAuth2 authentication successful: {} (Discord: {}, Role: {}, IP: {})",
                              new Object[]{accountUsername, discordUsername, dashboardRole, ipAddress}
                           );
                           return session;
                        }
                     }
                  }
               }
            }
         } catch (Exception var20) {
            LOGGER.error("Error during Discord OAuth2 flow: {}", var20.getMessage(), var20);
            return null;
         }
      }
   }

   private String exchangeDiscordCode(String code, String clientId, String clientSecret, String redirectUri) {
      try {
         String body = "client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret)
            + "&grant_type=authorization_code&code="
            + encode(code)
            + "&redirect_uri="
            + encode(redirectUri);
         URL url = new URL("https://discord.com/api/v10/oauth2/token");
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("POST");
         conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
         conn.setRequestProperty("User-Agent", "NeoEssentials-Dashboard/1.0");
         conn.setDoOutput(true);
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);

         try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
         }

         int status = conn.getResponseCode();
         InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
         if (is == null) {
            return null;
         } else {
            String var13;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
               StringBuilder sb = new StringBuilder();

               String line;
               while ((line = reader.readLine()) != null) {
                  sb.append(line);
               }

               if (status >= 400) {
                  LOGGER.error("Discord token endpoint returned HTTP {}: {}", status, sb);
               }

               var13 = sb.toString();
            }

            return var13;
         }
      } catch (Exception var18) {
         LOGGER.error("Error exchanging Discord authorization code: {}", var18.getMessage(), var18);
         return null;
      }
   }

   private String fetchDiscordApi(String apiUrl, String accessToken) {
      try {
         URL url = new URL(apiUrl);
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         conn.setRequestProperty("Authorization", "Bearer " + accessToken);
         conn.setRequestProperty("User-Agent", "NeoEssentials-Dashboard/1.0");
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);
         int status = conn.getResponseCode();
         InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
         if (is == null) {
            return null;
         } else {
            String var10;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
               StringBuilder sb = new StringBuilder();

               String line;
               while ((line = reader.readLine()) != null) {
                  sb.append(line);
               }

               if (status >= 400) {
                  LOGGER.error("Discord API {} returned HTTP {}: {}", new Object[]{apiUrl, status, sb});
                  return null;
               }

               var10 = sb.toString();
            }

            return var10;
         }
      } catch (Exception var13) {
         LOGGER.error("Error fetching Discord API {}: {}", new Object[]{apiUrl, var13.getMessage(), var13});
         return null;
      }
   }

   private static String encode(String value) {
      try {
         return URLEncoder.encode(value, StandardCharsets.UTF_8);
      } catch (Exception var2) {
         return value;
      }
   }

   private void handleDiscordAuthorizeRedirect(HttpExchange exchange) throws IOException {
      DiscordAuthConfig discordConfig = DiscordAuthConfig.load();
      if (!discordConfig.isEnabled()) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Discord authentication is disabled"));
      } else if (!discordConfig.isOauth2Configured()) {
         this.sendJsonResponse(exchange, 503, this.createErrorResponse("Discord OAuth2 is not configured. Set clientId and clientSecret in discord_auth.json"));
      } else {
         String state = UUID.randomUUID().toString().replace("-", "");
         String authorizeUrl = "https://discord.com/api/oauth2/authorize?client_id="
            + encode(discordConfig.getOauth2ClientId())
            + "&redirect_uri="
            + encode(discordConfig.getOauth2RedirectUri())
            + "&response_type=code&scope="
            + encode(discordConfig.getOauth2Scopes())
            + "&state="
            + state;
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("authorizeUrl", authorizeUrl);
         response.addProperty("state", state);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleDiscordOAuthCallback(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getQuery();
      if (query == null) {
         this.sendHtmlRedirect(exchange, "/dashboard/login.html?error=missing_code");
      } else {
         Map<String, String> params = new HashMap<>();

         for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
               params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
         }

         String code = params.get("code");
         String error = params.get("error");
         if (error != null) {
            LOGGER.warn("Discord OAuth2 callback received error: {}", error);
            this.sendHtmlRedirect(exchange, "/dashboard/login.html?error=" + encode(error));
         } else if (code != null && !code.isEmpty()) {
            String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            Session session = this.handleDiscordOAuth(code, ipAddress, userAgent);
            if (session == null) {
               this.sendHtmlRedirect(exchange, "/dashboard/login.html?error=discord_auth_failed");
            } else {
               exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + session.getSessionId() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
               this.sendHtmlRedirect(exchange, "/dashboard/index.html");
            }
         } else {
            this.sendHtmlRedirect(exchange, "/dashboard/login.html?error=missing_code");
         }
      }
   }

   private void sendHtmlRedirect(HttpExchange exchange, String location) throws IOException {
      exchange.getResponseHeaders().add("Location", location);
      byte[] body = ("<html><body>Redirecting… <a href=\"" + location + "\">click here</a></body></html>").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(302, (long)body.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(body);
      }
   }

   private UUID fetchUuidFromMojangAPI(String username) {
      try {
         URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            if (responseCode != 204 && responseCode != 404) {
               LOGGER.warn("Mojang API returned unexpected status code: {}", responseCode);
               return null;
            } else {
               LOGGER.debug("Player '{}' not found in Mojang database", username);
               return null;
            }
         } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            String uuidString = json.get("id").getAsString();
            String formattedUuid = uuidString.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
            return UUID.fromString(formattedUuid);
         }
      } catch (Exception var11) {
         LOGGER.debug("Error fetching UUID from Mojang API: {}", var11.getMessage());
         return null;
      }
   }

   private void handleDiscordAuth(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getQuery();
      if (query != null && query.contains("username=")) {
         String minecraftUsername = null;

         for (String param : query.split("&")) {
            if (param.startsWith("username=")) {
               minecraftUsername = param.substring("username=".length());
               break;
            }
         }

         if (minecraftUsername != null && !minecraftUsername.isEmpty()) {
            DiscordAuthConfig discordConfig = DiscordAuthConfig.load();
            if (!discordConfig.isEnabled()) {
               this.sendJsonResponse(exchange, 403, this.createErrorResponse("Discord authentication is disabled"));
            } else {
               DiscordAuthProvider discordProvider = DiscordAuthProvider.getInstance();
               if (!discordProvider.isAvailable()) {
                  this.sendJsonResponse(exchange, 503, this.createErrorResponse("Discord authentication unavailable. Simple Discord Link mod is required."));
               } else {
                  DiscordUser discordUser = discordProvider.getLinkedAccount(minecraftUsername);
                  if (discordUser == null || !discordUser.isLinked()) {
                     this.sendJsonResponse(
                        exchange, 404, this.createErrorResponse("No Discord account linked. Please link your account using /discord link in-game.")
                     );
                  } else if (discordConfig.isBlacklisted(discordUser.getDiscordId())) {
                     this.sendJsonResponse(exchange, 403, this.createErrorResponse("Access denied"));
                     LOGGER.warn("Blacklisted Discord user attempted login: {} ({})", discordUser.getDiscordUsername(), discordUser.getDiscordId());
                  } else if (!discordConfig.passesWhitelist(discordUser.getDiscordRoles())) {
                     this.sendJsonResponse(exchange, 403, this.createErrorResponse("You do not have the required Discord role to access the dashboard"));
                     LOGGER.warn(
                        "Discord user without required role attempted login: {} (roles: {})", discordUser.getDiscordUsername(), discordUser.getDiscordRoles()
                     );
                  } else {
                     User.Role dashboardRole = discordConfig.getHighestRole(discordUser.getDiscordRoles());
                     AuthenticationManager authManager = AuthenticationManager.getInstance();
                     User user = authManager.getUserByUsername(minecraftUsername);
                     if (user == null) {
                        if (!discordConfig.allowsAutoRegistration()) {
                           this.sendJsonResponse(exchange, 403, this.createErrorResponse("Account not found and auto-registration is disabled"));
                           return;
                        }

                        String email = discordUser.getDiscordId() + "@discord.link";
                        user = authManager.createUser(minecraftUsername, UUID.randomUUID().toString(), email, dashboardRole);
                        LOGGER.info("Auto-created dashboard user from Discord: {} with role {}", minecraftUsername, dashboardRole);
                     } else if (dashboardRole.ordinal() != user.getRole().ordinal()) {
                        user.setRole(dashboardRole);
                        LOGGER.info("Updated user {} role to {} based on Discord roles", minecraftUsername, dashboardRole);
                     }

                     String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
                     String userAgent = "Discord-" + discordUser.getDiscordUsername();
                     Session session = authManager.createSession(user.getId(), ipAddress, userAgent);
                     JsonObject response = new JsonObject();
                     response.addProperty("success", true);
                     response.addProperty("sessionId", session.getSessionId());
                     response.add("session", session.toJson());
                     JsonObject userJson = user.toJson();
                     userJson.addProperty("discordId", discordUser.getDiscordId());
                     userJson.addProperty("discordUsername", discordUser.getDiscordUsername());
                     JsonArray discordRolesArray = new JsonArray();
                     discordUser.getDiscordRoles().forEach(discordRolesArray::add);
                     userJson.add("discordRoles", discordRolesArray);
                     response.add("user", userJson);
                     LOGGER.info(
                        "Discord authentication successful: {} (Discord: {}, Role: {})",
                        new Object[]{minecraftUsername, discordUser.getDiscordUsername(), dashboardRole}
                     );
                     exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + session.getSessionId() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400");
                     this.sendJsonResponse(exchange, 200, response);
                  }
               }
            }
         } else {
            this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid username"));
         }
      } else {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing username parameter"));
      }
   }

   private void handleLogout(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (sessionId == null) {
         this.sendJsonResponse(exchange, 401, this.createErrorResponse("Not authenticated"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         authManager.logout(sessionId);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Logged out successfully");
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleValidate(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (sessionId == null) {
         this.sendJsonResponse(exchange, 401, this.createErrorResponse("Not authenticated"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         Session session = authManager.validateSession(sessionId);
         if (session == null) {
            this.sendJsonResponse(exchange, 401, this.createErrorResponse("Invalid or expired session"));
         } else {
            JsonObject response = new JsonObject();
            response.addProperty("valid", true);
            response.add("session", session.toJson());
            User user = authManager.getUser(session.getUserId());
            if (user != null) {
               response.add("user", user.toJson());
            }

            this.sendJsonResponse(exchange, 200, response);
         }
      }
   }

   private void handleGetUsers(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (!this.requireAdmin(sessionId)) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Admin access required"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         JsonObject response = new JsonObject();
         JsonArray usersArray = new JsonArray();
         authManager.getAllUsers().forEach(user -> usersArray.add(user.toJson()));
         response.add("users", usersArray);
         response.addProperty("count", usersArray.size());
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleCreateUser(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (!this.requireAdmin(sessionId)) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Admin access required"));
      } else {
         String requestBody = this.readRequestBody(exchange);
         JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
         if (request.has("username") && request.has("password")) {
            String username = request.get("username").getAsString();
            String password = request.get("password").getAsString();
            String email = request.has("email") ? request.get("email").getAsString() : null;
            User.Role role = request.has("role") ? User.Role.valueOf(request.get("role").getAsString()) : User.Role.VIEWER;

            try {
               AuthenticationManager authManager = AuthenticationManager.getInstance();
               User user = authManager.createUser(username, password, email, role);
               JsonObject response = new JsonObject();
               response.addProperty("success", true);
               response.addProperty("message", "User created successfully");
               response.add("user", user.toJson());
               this.sendJsonResponse(exchange, 201, response);
            } catch (IllegalArgumentException var12) {
               this.sendJsonResponse(exchange, 400, this.createErrorResponse(var12.getMessage()));
            }
         } else {
            this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing username or password"));
         }
      }
   }

   private void handleUpdateUser(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (!this.requireAdmin(sessionId)) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Admin access required"));
      } else {
         String path = exchange.getRequestURI().getPath();
         String userId = path.substring(path.lastIndexOf(47) + 1);
         String requestBody = this.readRequestBody(exchange);
         JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         User user = authManager.getUser(userId);
         if (user == null) {
            this.sendJsonResponse(exchange, 404, this.createErrorResponse("User not found"));
         } else {
            try {
               if (request.has("password")) {
                  authManager.updatePassword(userId, request.get("password").getAsString());
               }

               if (request.has("role")) {
                  User.Role newRole = User.Role.valueOf(request.get("role").getAsString());
                  authManager.updateUserRole(userId, newRole);
               }

               if (request.has("enabled")) {
                  authManager.setUserEnabled(userId, request.get("enabled").getAsBoolean());
               }

               if (request.has("email")) {
                  user.setEmail(request.get("email").getAsString());
               }

               JsonObject response = new JsonObject();
               response.addProperty("success", true);
               response.addProperty("message", "User updated successfully");
               response.add("user", user.toJson());
               this.sendJsonResponse(exchange, 200, response);
            } catch (IllegalArgumentException var10) {
               this.sendJsonResponse(exchange, 400, this.createErrorResponse(var10.getMessage()));
            }
         }
      }
   }

   private void handleDeleteUser(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (!this.requireAdmin(sessionId)) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Admin access required"));
      } else {
         String path = exchange.getRequestURI().getPath();
         String userId = path.substring(path.lastIndexOf(47) + 1);

         try {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            authManager.deleteUser(userId);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "User deleted successfully");
            this.sendJsonResponse(exchange, 200, response);
         } catch (IllegalArgumentException var7) {
            this.sendJsonResponse(exchange, 404, this.createErrorResponse(var7.getMessage()));
         }
      }
   }

   private void handleGetSessions(HttpExchange exchange) throws IOException {
      String sessionId = this.extractSessionId(exchange);
      if (!this.requireAdmin(sessionId)) {
         this.sendJsonResponse(exchange, 403, this.createErrorResponse("Admin access required"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         JsonObject response = new JsonObject();
         JsonArray sessionsArray = new JsonArray();
         authManager.getActiveSessions().forEach(session -> sessionsArray.add(session.toJson()));
         response.add("sessions", sessionsArray);
         response.addProperty("count", sessionsArray.size());
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleGetCurrentSession(HttpExchange exchange) throws IOException {
      if (ConfigManager.isDebugModeEnabled()) {
         String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
         LOGGER.debug("handleGetCurrentSession: Raw Cookie header: {}", cookieHeader);
      }

      String sessionId = this.getSessionIdFromCookie(exchange);
      if (ConfigManager.isDebugModeEnabled()) {
         LOGGER.debug("handleGetCurrentSession: Extracted sessionId: {}", sessionId);
      }

      if (sessionId == null) {
         this.sendJsonResponse(exchange, 401, this.createErrorResponse("No active session"));
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         Session session = authManager.validateSession(sessionId);
         if (session == null) {
            this.sendJsonResponse(exchange, 401, this.createErrorResponse("Invalid or expired session"));
         } else {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("session", session.toJson());
            User user = authManager.getUser(session.getUserId());
            if (user != null) {
               response.add("user", user.toJson());
            }

            this.sendJsonResponse(exchange, 200, response);
         }
      }
   }

   private String getSessionIdFromCookie(HttpExchange exchange) {
      String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
      if (cookieHeader == null) {
         return null;
      } else {
         String[] cookies = cookieHeader.split(";");

         for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "sessionId".equals(parts[0])) {
               return parts[1];
            }
         }

         return null;
      }
   }

   private String extractSessionId(HttpExchange exchange) {
      String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
      return authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
   }

   private boolean requireAdmin(String sessionId) {
      if (sessionId == null) {
         return false;
      } else {
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         Session session = authManager.validateSession(sessionId);
         return session != null && session.getRole() == User.Role.ADMIN;
      }
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
