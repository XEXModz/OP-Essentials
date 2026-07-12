package com.zerog.neoessentials.webdashboard.auth;

import com.zerog.neoessentials.webdashboard.security.Session;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationManager.class);
   private static AuthenticationManager INSTANCE;
   private final Map<String, AuthSession> activeSessions = new ConcurrentHashMap<>();
   private final Map<String, User> users = new ConcurrentHashMap<>();

   private AuthenticationManager() {
   }

   public static AuthenticationManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AuthenticationManager();
      }

      return INSTANCE;
   }

   private com.zerog.neoessentials.webdashboard.security.AuthenticationManager real() {
      return com.zerog.neoessentials.webdashboard.security.AuthenticationManager.getInstance();
   }

   public AuthResult authenticate(String username, String password) {
      LOGGER.debug("Authentication attempt for user: {}", username);

      try {
         Session session = this.real().authenticate(username, password, "dashboard", "DashboardAPI");
         if (session == null) {
            return AuthResult.failure("Invalid credentials or account locked");
         } else {
            User legacyUser = new User(UUID.randomUUID(), session.getUsername());
            legacyUser.addPermission("dashboard.*");
            AuthSession authSession = new AuthSession(
               session.getSessionId(), legacyUser, session.getSessionId(), System.currentTimeMillis() + 86400000L, "dashboard"
            );
            return AuthResult.success(session.getSessionId(), session.getSessionId(), authSession);
         }
      } catch (Exception var6) {
         LOGGER.error("Error during authentication for {}: {}", new Object[]{username, var6.getMessage(), var6});
         return AuthResult.failure("Authentication error");
      }
   }

   public AuthSession validateToken(String token) {
      try {
         Session session = this.real().validateSession(token);
         if (session == null) {
            return null;
         } else {
            User legacyUser = new User(UUID.randomUUID(), session.getUsername());
            legacyUser.addPermission("dashboard.*");
            return new AuthSession(session.getSessionId(), legacyUser, session.getSessionId(), System.currentTimeMillis() + 86400000L, "dashboard");
         }
      } catch (Exception var4) {
         LOGGER.error("Error validating token: {}", var4.getMessage(), var4);
         return null;
      }
   }

   public String refreshToken(String refreshToken) {
      try {
         Session session = this.real().validateSession(refreshToken);
         return session != null ? session.getSessionId() : null;
      } catch (Exception var3) {
         LOGGER.error("Error refreshing token: {}", var3.getMessage(), var3);
         return null;
      }
   }

   public void logout(String token) {
      LOGGER.debug("Logout request for token");

      try {
         this.real().logout(token);
      } catch (Exception var3) {
         LOGGER.error("Error during logout: {}", var3.getMessage(), var3);
      }
   }

   public boolean hasPermission(AuthSession session, String permission) {
      if (session == null) {
         return false;
      } else {
         try {
            return this.real().hasPermission(session.getSessionId(), permission);
         } catch (Exception var4) {
            LOGGER.error("Error checking permission: {}", var4.getMessage(), var4);
            return false;
         }
      }
   }

   public void cleanupExpiredSessions() {
   }
}
