package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static AuthenticationManager INSTANCE;
   private static final Path USERS_FILE = Paths.get("neoessentials", "dashboard_users.json");
   private static final Path AUDIT_LOG = Paths.get("neoessentials", "dashboard_audit.log");
   private final Map<String, User> users = new ConcurrentHashMap<>();
   private final Map<String, Session> sessions = new ConcurrentHashMap<>();
   private final Map<String, String> userIdByUsername = new ConcurrentHashMap<>();
   private static final int MAX_FAILED_ATTEMPTS = 5;
   private static final long LOCKOUT_DURATION_MS = 900000L;
   private static final int MIN_PASSWORD_LENGTH = 8;

   private AuthenticationManager() {
      this.loadUsers();
      this.startSessionCleanupTask();
   }

   public static AuthenticationManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AuthenticationManager();
      }

      return INSTANCE;
   }

   public Session authenticate(String username, String password, String ipAddress, String userAgent) {
      String userId = this.userIdByUsername.get(username.toLowerCase());
      if (userId == null) {
         this.logAuditEvent("LOGIN_FAILED", username, ipAddress, "User not found");
         return null;
      } else {
         User user = this.users.get(userId);
         if (user == null) {
            return null;
         } else if (user.isLockedOut()) {
            this.logAuditEvent("LOGIN_BLOCKED", username, ipAddress, "Account locked due to failed attempts");
            return null;
         } else if (!user.isEnabled()) {
            this.logAuditEvent("LOGIN_BLOCKED", username, ipAddress, "Account disabled");
            return null;
         } else if (!this.verifyPassword(password, user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= 5) {
               user.setLockoutUntil(System.currentTimeMillis() + 900000L);
               this.logAuditEvent("ACCOUNT_LOCKED", username, ipAddress, "Account locked due to 5 failed attempts");
            }

            this.saveUsers();
            this.logAuditEvent("LOGIN_FAILED", username, ipAddress, "Invalid password");
            return null;
         } else {
            user.setFailedLoginAttempts(0);
            user.setLockoutUntil(0L);
            user.setLastLoginAt(System.currentTimeMillis());
            user.setLastLoginIp(ipAddress);
            if (!user.getPasswordHash().startsWith("PBKDF2:")) {
               LOGGER.info("Upgrading password hash to PBKDF2 for user: {}", username);
               user.setPasswordHash(this.hashPassword(password));
            }

            this.saveUsers();
            Session session = new Session(user.getId(), user.getUsername(), user.getRole(), ipAddress, userAgent);
            if (!user.requiresPasswordChange() && !user.isTempPassword()) {
               this.logAuditEvent("LOGIN_SUCCESS", username, ipAddress, "Session created: " + session.getSessionId());
            } else {
               session.setRequiresPasswordChange(true);
               this.logAuditEvent("LOGIN_SUCCESS", username, ipAddress, "Session created with password change requirement: " + session.getSessionId());
            }

            this.sessions.put(session.getSessionId(), session);
            return session;
         }
      }
   }

   public Session createSession(String userId, String ipAddress, String userAgent) {
      User user = this.users.get(userId);
      if (user == null) {
         LOGGER.error("Cannot create session: user not found with ID {}", userId);
         return null;
      } else {
         user.setLastLoginAt(System.currentTimeMillis());
         user.setLastLoginIp(ipAddress);
         this.saveUsers();
         Session session = new Session(user.getId(), user.getUsername(), user.getRole(), ipAddress, userAgent);
         this.sessions.put(session.getSessionId(), session);
         this.logAuditEvent("LOGIN_SUCCESS", user.getUsername(), ipAddress, "External auth session created: " + session.getSessionId());
         return session;
      }
   }

   public Session validateSession(String sessionId) {
      if (sessionId != null && !sessionId.isEmpty()) {
         Session session = this.sessions.get(sessionId);
         if (session == null) {
            if (ConfigManager.isDebugModeEnabled()) {
               LOGGER.debug("validateSession: sessionId '{}' not found in sessions map", sessionId);
            }

            return null;
         } else if (!session.isValid()) {
            if (ConfigManager.isDebugModeEnabled()) {
               LOGGER.debug("validateSession: sessionId '{}' found but session is not valid", sessionId);
            }

            return null;
         } else {
            session.updateAccessTime();
            if (ConfigManager.isDebugModeEnabled()) {
               LOGGER.debug(
                  "validateSession: sessionId '{}' is valid for user '{}', requiresPasswordChange={}",
                  new Object[]{sessionId, session.getUsername(), session.requiresPasswordChange()}
               );
            }

            return session;
         }
      } else {
         if (ConfigManager.isDebugModeEnabled()) {
            LOGGER.debug("validateSession: sessionId is null or empty");
         }

         return null;
      }
   }

   public void logout(String sessionId) {
      Session session = this.sessions.get(sessionId);
      if (session != null) {
         session.invalidate();
         this.sessions.remove(sessionId);
         this.logAuditEvent("LOGOUT", session.getUsername(), session.getIpAddress(), "Session invalidated: " + sessionId);
      }
   }

   public User createUser(String username, String password, String email, User.Role role) {
      if (username != null && !username.isEmpty()) {
         if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
         } else if (this.userIdByUsername.containsKey(username.toLowerCase())) {
            throw new IllegalArgumentException("Username already exists");
         } else {
            String passwordHash = this.hashPassword(password);
            User user = new User(username, passwordHash);
            user.setEmail(email);
            user.setRole(role != null ? role : User.Role.VIEWER);
            this.users.put(user.getId(), user);
            this.userIdByUsername.put(username.toLowerCase(), user.getId());
            this.saveUsers();
            this.logAuditEvent("USER_CREATED", username, "system", "Role: " + user.getRole());
            return user;
         }
      } else {
         throw new IllegalArgumentException("Username cannot be empty");
      }
   }

   public void updatePassword(String userId, String newPassword) {
      User user = this.users.get(userId);
      if (user == null) {
         throw new IllegalArgumentException("User not found");
      } else if (newPassword != null && newPassword.length() >= 8) {
         String passwordHash = this.hashPassword(newPassword);
         user.setPasswordHash(passwordHash);
         user.setRequiresPasswordChange(false);
         user.setTempPassword(false);
         this.saveUsers();
         this.logAuditEvent("PASSWORD_CHANGED", user.getUsername(), "system", "Password updated and flags cleared");
      } else {
         throw new IllegalArgumentException("Password must be at least 8 characters");
      }
   }

   public void updateUserRole(String userId, User.Role newRole) {
      User user = this.users.get(userId);
      if (user == null) {
         throw new IllegalArgumentException("User not found");
      } else {
         User.Role oldRole = user.getRole();
         user.setRole(newRole);
         this.saveUsers();
         this.logAuditEvent("ROLE_CHANGED", user.getUsername(), "system", "Role changed from " + oldRole + " to " + newRole);
      }
   }

   public void setUserEnabled(String userId, boolean enabled) {
      User user = this.users.get(userId);
      if (user == null) {
         throw new IllegalArgumentException("User not found");
      } else {
         user.setEnabled(enabled);
         this.saveUsers();
         this.logAuditEvent(enabled ? "USER_ENABLED" : "USER_DISABLED", user.getUsername(), "system", "Account " + (enabled ? "enabled" : "disabled"));
      }
   }

   public void deleteUser(String userId) {
      User user = this.users.get(userId);
      if (user == null) {
         throw new IllegalArgumentException("User not found");
      } else {
         this.users.remove(userId);
         this.userIdByUsername.remove(user.getUsername().toLowerCase());
         this.saveUsers();
         this.sessions.values().stream().filter(s -> s.getUserId().equals(userId)).forEach(Session::invalidate);
         this.logAuditEvent("USER_DELETED", user.getUsername(), "system", "Account deleted");
      }
   }

   public User getUser(String userId) {
      return this.users.get(userId);
   }

   public User getUserByUsername(String username) {
      String userId = this.userIdByUsername.get(username.toLowerCase());
      return userId != null ? this.users.get(userId) : null;
   }

   public Collection<User> getAllUsers() {
      return new ArrayList<>(this.users.values());
   }

   public Collection<Session> getActiveSessions() {
      return this.sessions.values().stream().filter(Session::isValid).collect(Collectors.toList());
   }

   public String generateTempPassword(String username) {
      User user = this.getUserByUsername(username);
      if (user == null) {
         throw new IllegalArgumentException("User not found: " + username);
      } else {
         String tempPassword = this.generateRandomPassword(12);
         String passwordHash = this.hashPassword(tempPassword);
         user.setPasswordHash(passwordHash);
         user.setTempPassword(true);
         user.setRequiresPasswordChange(true);
         user.setFailedLoginAttempts(0);
         user.setLockoutUntil(0L);
         this.saveUsers();
         this.logAuditEvent("TEMP_PASSWORD_GENERATED", username, "system", "Temporary password created");
         return tempPassword;
      }
   }

   private String generateRandomPassword(int length) {
      String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
      SecureRandom random = new SecureRandom();
      StringBuilder password = new StringBuilder();

      for (int i = 0; i < length; i++) {
         password.append(chars.charAt(random.nextInt(chars.length())));
      }

      return password.toString();
   }

   public boolean hasPermission(String sessionId, String permission) {
      Session session = this.validateSession(sessionId);
      if (session == null) {
         return false;
      } else {
         User user = this.users.get(session.getUserId());
         return user == null ? false : user.hasPermission(permission);
      }
   }

   public String hashPassword(String password) {
      try {
         SecureRandom random = new SecureRandom();
         byte[] salt = new byte[16];
         random.nextBytes(salt);
         int iterations = 65536;
         int keyLength = 256;
         PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
         SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
         byte[] hash = factory.generateSecret(spec).getEncoded();
         return "PBKDF2:" + iterations + ":" + bytesToHex(salt) + ":" + bytesToHex(hash);
      } catch (Exception var9) {
         throw new RuntimeException("PBKDF2 hashing failed", var9);
      }
   }

   public boolean verifyPassword(String password, String storedHash) {
      if (storedHash == null || password == null) {
         return false;
      } else if (storedHash.startsWith("PBKDF2:")) {
         try {
            String[] parts = storedHash.split(":");
            if (parts.length != 4) {
               return false;
            } else {
               int iterations = Integer.parseInt(parts[1]);
               byte[] salt = hexToBytes(parts[2]);
               byte[] expectedHash = hexToBytes(parts[3]);
               PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.length * 8);
               SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
               byte[] actualHash = factory.generateSecret(spec).getEncoded();
               return MessageDigest.isEqual(expectedHash, actualHash);
            }
         } catch (Exception var10) {
            LOGGER.error("PBKDF2 verification failed", var10);
            return false;
         }
      } else {
         try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            String legacyHash = bytesToHex(hash);
            return legacyHash.equals(storedHash);
         } catch (NoSuchAlgorithmException var11) {
            return false;
         }
      }
   }

   private static String bytesToHex(byte[] bytes) {
      StringBuilder sb = new StringBuilder();

      for (byte b : bytes) {
         String hex = Integer.toHexString(255 & b);
         if (hex.length() == 1) {
            sb.append('0');
         }

         sb.append(hex);
      }

      return sb.toString();
   }

   private static byte[] hexToBytes(String hex) {
      int len = hex.length();
      byte[] bytes = new byte[len / 2];

      for (int i = 0; i < len; i += 2) {
         bytes[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
      }

      return bytes;
   }

   private void loadUsers() {
      if (!Files.exists(USERS_FILE)) {
         LOGGER.info("Creating default admin user (username: admin, password: admin123)");
         this.createUser("admin", "admin123", "admin@localhost", User.Role.ADMIN);
         LOGGER.warn("SECURITY WARNING: Default admin account created with password 'admin123'. Please change it immediately!");
      } else {
         try {
            String json = Files.readString(USERS_FILE, StandardCharsets.UTF_8);
            JsonObject root = (JsonObject)GSON.fromJson(json, JsonObject.class);

            for (JsonElement element : root.getAsJsonArray("users")) {
               JsonObject userJson = element.getAsJsonObject();
               String id = userJson.get("id").getAsString();
               String username = userJson.get("username").getAsString();
               String passwordHash = userJson.get("passwordHash").getAsString();
               String email = userJson.has("email") ? userJson.get("email").getAsString() : null;
               User.Role role = User.Role.valueOf(userJson.get("role").getAsString());
               boolean enabled = userJson.get("enabled").getAsBoolean();
               long createdAt = userJson.get("createdAt").getAsLong();
               Set<String> permissions = new HashSet<>();
               if (userJson.has("permissions")) {
                  JsonArray permsArray = userJson.getAsJsonArray("permissions");
                  permsArray.forEach(p -> permissions.add(p.getAsString()));
               }

               User user = new User(id, username, passwordHash, email, role, enabled, createdAt, permissions);
               if (userJson.has("requiresPasswordChange")) {
                  user.setRequiresPasswordChange(userJson.get("requiresPasswordChange").getAsBoolean());
               }

               if (userJson.has("isTempPassword")) {
                  user.setTempPassword(userJson.get("isTempPassword").getAsBoolean());
               }

               this.users.put(id, user);
               this.userIdByUsername.put(username.toLowerCase(), id);
            }

            LOGGER.info("Loaded {} users from file", this.users.size());
         } catch (IOException var17) {
            LOGGER.error("Failed to load users", var17);
         }
      }
   }

   public void saveUsers() {
      try {
         Path parent = USERS_FILE.getParent();
         if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
         }

         JsonObject root = new JsonObject();
         JsonArray usersArray = new JsonArray();

         for (User user : this.users.values()) {
            JsonObject userJson = new JsonObject();
            userJson.addProperty("id", user.getId());
            userJson.addProperty("username", user.getUsername());
            userJson.addProperty("passwordHash", user.getPasswordHash());
            userJson.addProperty("email", user.getEmail());
            userJson.addProperty("role", user.getRole().name());
            userJson.addProperty("enabled", user.isEnabled());
            userJson.addProperty("createdAt", user.getCreatedAt());
            userJson.addProperty("requiresPasswordChange", user.requiresPasswordChange());
            userJson.addProperty("isTempPassword", user.isTempPassword());
            JsonArray permsArray = new JsonArray();
            user.getPermissions().forEach(permsArray::add);
            userJson.add("permissions", permsArray);
            usersArray.add(userJson);
         }

         root.add("users", usersArray);
         Files.writeString(USERS_FILE, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var8) {
         LOGGER.error("Failed to save users", var8);
      }
   }

   private void logAuditEvent(String eventType, String username, String ipAddress, String details) {
      try {
         Path parent = AUDIT_LOG.getParent();
         if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
         }

         String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
         String logEntry = String.format("[%s] %s | User: %s | IP: %s | %s%n", timestamp, eventType, username, ipAddress, details);
         Files.writeString(AUDIT_LOG, logEntry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         LOGGER.info("AUDIT: {}", logEntry.trim());
      } catch (IOException var8) {
         LOGGER.error("Failed to write audit log", var8);
      }
   }

   private void startSessionCleanupTask() {
      Timer timer = new Timer("SessionCleanup", true);
      timer.scheduleAtFixedRate(new TimerTask() {
         @Override
         public void run() {
            int removed = 0;
            Iterator<Entry<String, Session>> iterator = AuthenticationManager.this.sessions.entrySet().iterator();

            while (iterator.hasNext()) {
               Entry<String, Session> entry = iterator.next();
               if (!entry.getValue().isValid()) {
                  iterator.remove();
                  removed++;
               }
            }

            if (removed > 0) {
               AuthenticationManager.LOGGER.debug("Cleaned up {} expired sessions", removed);
            }
         }
      }, 60000L, 60000L);
   }
}
