package com.zerog.neoessentials.webdashboard.filters;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Filter.Chain;
import com.zerog.neoessentials.webdashboard.security.AuthenticationManager;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import com.zerog.neoessentials.webdashboard.security.Session;
import com.zerog.neoessentials.webdashboard.security.User;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationFilter extends Filter {
   private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);
   private static final Gson GSON = new Gson();
   private static final Set<String> PUBLIC_ENDPOINTS = new HashSet<>();
   private static final Set<String> ADMIN_ONLY = new HashSet<>();
   private static final Set<String> OPERATOR_ONLY = new HashSet<>();

   @Override
   public String description() {
      return "Authentication Filter - Validates session tokens and enforces RBAC";
   }

   @Override
   public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();
      if ("OPTIONS".equals(method)) {
         CorsHandler.applyFull(exchange, "GET, POST, PUT, DELETE, OPTIONS", "Content-Type, Authorization");
         exchange.sendResponseHeaders(204, -1L);
      } else if (path.equals("/password_change.html") || path.equals("/api/change-password")) {
         String sessionId = this.extractSessionId(exchange);
         if (sessionId != null) {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            Session session = authManager.validateSession(sessionId);
            if (session != null && session.requiresPasswordChange()) {
               exchange.setAttribute("session", session);
               chain.doFilter(exchange);
               return;
            }
         }

         this.sendUnauthorized(exchange, "Authentication required");
      } else if (this.isPublicEndpoint(path)) {
         chain.doFilter(exchange);
      } else {
         String sessionId = this.extractSessionId(exchange);
         if (sessionId == null) {
            this.sendUnauthorized(exchange, "Authentication required");
         } else {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            Session session = authManager.validateSession(sessionId);
            if (session == null) {
               this.sendUnauthorized(exchange, "Invalid or expired session");
            } else if (!this.checkPermissions(path, method, session)) {
               this.sendForbidden(exchange, "Insufficient permissions");
            } else {
               exchange.setAttribute("session", session);
               exchange.setAttribute("userId", session.getUserId());
               exchange.setAttribute("username", session.getUsername());
               exchange.setAttribute("role", session.getRole().name());
               chain.doFilter(exchange);
            }
         }
      }
   }

   private boolean isPublicEndpoint(String path) {
      return PUBLIC_ENDPOINTS.contains(path)
         ? true
         : path.startsWith("/assets/") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/");
   }

   private boolean checkPermissions(String path, String method, Session session) {
      User.Role role = session.getRole();
      if (role == User.Role.ADMIN) {
         return true;
      } else if (this.requiresAdminAccess(path, method)) {
         return false;
      } else if (this.requiresOperatorAccess(path, method)) {
         return role == User.Role.OPERATOR || role == User.Role.MODERATOR;
      } else if (role != User.Role.MODERATOR || !path.startsWith("/api/players") && !path.startsWith("/api/chat")) {
         if (role != User.Role.OPERATOR || !path.startsWith("/api/commands") && !path.startsWith("/api/server")) {
            if (role != User.Role.VIEWER) {
               return false;
            } else {
               return !"GET".equals(method) ? false : !path.startsWith("/api/auth/users") && !path.startsWith("/api/auth/sessions");
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private boolean requiresAdminAccess(String path, String method) {
      for (String adminPath : ADMIN_ONLY) {
         if (path.equals(adminPath) || path.startsWith(adminPath + "/")) {
            return true;
         }
      }

      return path.startsWith("/api/auth/users") && !"GET".equals(method)
         ? true
         : path.startsWith("/api/files") && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method));
   }

   private boolean requiresOperatorAccess(String path, String method) {
      for (String opPath : OPERATOR_ONLY) {
         if (path.equals(opPath) || path.startsWith(opPath + "/")) {
            return true;
         }
      }

      return false;
   }

   private String extractSessionId(HttpExchange exchange) {
      String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
         return authHeader.substring(7);
      } else {
         String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
         if (cookieHeader != null) {
            String[] cookies = cookieHeader.split("; ");

            for (String cookie : cookies) {
               if (cookie.startsWith("sessionId=")) {
                  return cookie.substring("sessionId=".length());
               }
            }
         }

         return null;
      }
   }

   private void sendUnauthorized(HttpExchange exchange, String message) throws IOException {
      String path = exchange.getRequestURI().getPath();
      if (!"/index.html".equals(path) && !"/".equals(path)) {
         JsonObject error = new JsonObject();
         error.addProperty("error", message);
         error.addProperty("code", 401);
         error.addProperty("timestamp", System.currentTimeMillis());
         byte[] response = GSON.toJson(error).getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
         CorsHandler.apply(exchange);
         exchange.sendResponseHeaders(401, (long)response.length);

         try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
         }
      } else {
         exchange.getResponseHeaders().set("Location", "/login.html");
         exchange.sendResponseHeaders(302, -1L);
      }
   }

   private void sendForbidden(HttpExchange exchange, String message) throws IOException {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("code", 403);
      error.addProperty("timestamp", System.currentTimeMillis());
      byte[] response = GSON.toJson(error).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(403, (long)response.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(response);
      }
   }

   static {
      PUBLIC_ENDPOINTS.add("/");
      PUBLIC_ENDPOINTS.add("/login.html");
      PUBLIC_ENDPOINTS.add("/space-dashboard.css");
      PUBLIC_ENDPOINTS.add("/space-dashboard.js");
      PUBLIC_ENDPOINTS.add("/space-theme.css");
      PUBLIC_ENDPOINTS.add("/space-glass.css");
      PUBLIC_ENDPOINTS.add("/orbitron.css");
      PUBLIC_ENDPOINTS.add("/favicon.ico");
      PUBLIC_ENDPOINTS.add("/api/auth/login");
      ADMIN_ONLY.add("/api/auth/users");
      ADMIN_ONLY.add("/api/auth/sessions");
      ADMIN_ONLY.add("/api/commands/execute");
      ADMIN_ONLY.add("/api/files/write");
      ADMIN_ONLY.add("/api/files/create");
      ADMIN_ONLY.add("/api/files/delete");
      OPERATOR_ONLY.add("/api/server/stop");
      OPERATOR_ONLY.add("/api/server/restart");
      OPERATOR_ONLY.add("/api/server/config");
   }
}
