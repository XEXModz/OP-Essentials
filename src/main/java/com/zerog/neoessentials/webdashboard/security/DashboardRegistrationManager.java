package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardRegistrationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DashboardRegistrationManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static DashboardRegistrationManager INSTANCE;
   private static final Path REGISTRATIONS_FILE = Paths.get("neoessentials", "dashboard_registrations.json");
   private final Map<UUID, DashboardAccountRegistration> registrations = new ConcurrentHashMap<>();
   private final Map<String, DashboardRegistrationManager.PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();

   private DashboardRegistrationManager() {
      LOGGER.info("Initializing DashboardRegistrationManager...");
      this.loadRegistrations();
      LOGGER.info("DashboardRegistrationManager initialized with {} existing registration(s)", this.registrations.size());
   }

   public static DashboardRegistrationManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DashboardRegistrationManager();
      }

      return INSTANCE;
   }

   public boolean isRegistered(UUID minecraftUuid) {
      return this.registrations.containsKey(minecraftUuid);
   }

   public DashboardAccountRegistration getRegistration(UUID minecraftUuid) {
      return this.registrations.get(minecraftUuid);
   }

   public DashboardAccountRegistration getRegistrationByUsername(String username) {
      return this.registrations.values().stream().filter(reg -> reg.getDashboardUsername().equalsIgnoreCase(username)).findFirst().orElse(null);
   }

   public String startRegistration(UUID minecraftUuid, String minecraftUsername) {
      if (this.isRegistered(minecraftUuid)) {
         LOGGER.warn("Player {} already has a registered dashboard account", minecraftUsername);
         return null;
      } else {
         String token = this.generateToken();
         DashboardRegistrationManager.PendingRegistration pending = new DashboardRegistrationManager.PendingRegistration(
            token, minecraftUuid, minecraftUsername, System.currentTimeMillis() + 300000L
         );
         this.pendingRegistrations.put(token, pending);
         LOGGER.info("Started dashboard registration for player {}: token={}", minecraftUsername, token);
         return token;
      }
   }

   public DashboardAccountRegistration completeRegistration(String token, String dashboardUsername, String password) {
      DashboardRegistrationManager.PendingRegistration pending = this.pendingRegistrations.get(token);
      if (pending == null) {
         LOGGER.warn("Invalid or expired registration token: {}", token);
         return null;
      } else if (System.currentTimeMillis() > pending.getExpiresAt()) {
         this.pendingRegistrations.remove(token);
         LOGGER.warn("Registration token expired for player: {}", pending.getMinecraftUsername());
         return null;
      } else if (this.getRegistrationByUsername(dashboardUsername) != null) {
         LOGGER.warn("Dashboard username already taken: {}", dashboardUsername);
         return null;
      } else if (password != null && password.length() >= 8) {
         DashboardAccountRegistration registration = new DashboardAccountRegistration(
            pending.getMinecraftUuid(), pending.getMinecraftUsername(), dashboardUsername, this.hashPassword(password), System.currentTimeMillis()
         );
         this.registrations.put(pending.getMinecraftUuid(), registration);
         this.pendingRegistrations.remove(token);
         this.saveRegistrations();

         try {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            User.Role role = this.determineRole(pending.getMinecraftUuid());
            authManager.createUser(dashboardUsername, password, null, role);
            LOGGER.info("Completed dashboard registration for {} (Minecraft: {})", dashboardUsername, pending.getMinecraftUsername());
            return registration;
         } catch (Exception var8) {
            LOGGER.error("Failed to create user account for registration: {}", var8.getMessage(), var8);
            this.registrations.remove(pending.getMinecraftUuid());
            this.saveRegistrations();
            return null;
         }
      } else {
         LOGGER.warn("Password too weak for dashboard registration: {}", pending.getMinecraftUsername());
         return null;
      }
   }

   public DashboardAccountRegistration completeRegistrationByUuid(UUID playerUuid, String dashboardUsername, String password) {
      DashboardRegistrationManager.PendingRegistration pending = this.pendingRegistrations
         .values()
         .stream()
         .filter(p -> p.getMinecraftUuid().equals(playerUuid))
         .findFirst()
         .orElse(null);
      if (pending == null) {
         LOGGER.warn("No pending registration found for UUID: {}", playerUuid);
         return null;
      } else {
         return this.completeRegistration(pending.getToken(), dashboardUsername, password);
      }
   }

   public boolean linkDiscordAccount(UUID minecraftUuid, String discordId, String discordUsername) {
      DashboardAccountRegistration registration = this.registrations.get(minecraftUuid);
      if (registration == null) {
         return false;
      } else {
         registration.setDiscordId(discordId);
         registration.setDiscordUsername(discordUsername);
         registration.setDiscordLinkedAt(System.currentTimeMillis());
         this.saveRegistrations();
         LOGGER.info("Linked Discord account {} to dashboard user {}", discordUsername, registration.getDashboardUsername());
         return true;
      }
   }

   public boolean unlinkDiscordAccount(UUID minecraftUuid) {
      DashboardAccountRegistration registration = this.registrations.get(minecraftUuid);
      if (registration == null) {
         return false;
      } else {
         registration.setDiscordId(null);
         registration.setDiscordUsername(null);
         registration.setDiscordLinkedAt(0L);
         this.saveRegistrations();
         LOGGER.info("Unlinked Discord account from dashboard user {}", registration.getDashboardUsername());
         return true;
      }
   }

   private User.Role determineRole(UUID minecraftUuid) {
      if (PermissionAPI.hasPermission(minecraftUuid, "neoessentials.dashboard.admin")) {
         return User.Role.ADMIN;
      } else if (PermissionAPI.hasPermission(minecraftUuid, "neoessentials.dashboard.moderator")) {
         return User.Role.MODERATOR;
      } else {
         return PermissionAPI.hasPermission(minecraftUuid, "neoessentials.dashboard.manage") ? User.Role.OPERATOR : User.Role.VIEWER;
      }
   }

   private String generateToken() {
      try {
         SecureRandom random = SecureRandom.getInstanceStrong();
         byte[] bytes = new byte[32];
         random.nextBytes(bytes);
         return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      } catch (NoSuchAlgorithmException var4) {
         SecureRandom randomx = new SecureRandom();
         byte[] bytesx = new byte[32];
         randomx.nextBytes(bytesx);
         return Base64.getUrlEncoder().withoutPadding().encodeToString(bytesx);
      }
   }

   private String hashPassword(String password) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
         return Base64.getEncoder().encodeToString(hash);
      } catch (NoSuchAlgorithmException var4) {
         throw new RuntimeException("SHA-256 not available", var4);
      }
   }

   private void loadRegistrations() {
      if (!Files.exists(REGISTRATIONS_FILE)) {
         LOGGER.info("No existing dashboard registrations file found");
      } else {
         try {
            String content = Files.readString(REGISTRATIONS_FILE, StandardCharsets.UTF_8);
            JsonObject data = (JsonObject)GSON.fromJson(content, JsonObject.class);
            if (data == null) {
               LOGGER.warn("Dashboard registrations file is empty or invalid, starting fresh");
               return;
            }

            if (data.has("registrations")) {
               data.getAsJsonArray("registrations").forEach(element -> {
                  JsonObject regObj = element.getAsJsonObject();
                  DashboardAccountRegistration reg = DashboardAccountRegistration.fromJson(regObj);
                  if (reg != null) {
                     this.registrations.put(reg.getMinecraftUuid(), reg);
                  }
               });
            }

            LOGGER.info("Loaded {} dashboard registrations", this.registrations.size());
         } catch (IOException var3) {
            LOGGER.error("Failed to load dashboard registrations: {}", var3.getMessage(), var3);
         }
      }
   }

   private void saveRegistrations() {
      try {
         Files.createDirectories(REGISTRATIONS_FILE.getParent());
         JsonObject data = new JsonObject();
         data.addProperty("version", 1);
         data.addProperty("lastUpdated", System.currentTimeMillis());
         JsonArray regsArray = new JsonArray();
         this.registrations.values().forEach(reg -> regsArray.add(reg.toJson()));
         data.add("registrations", regsArray);
         String json = GSON.toJson(data);
         Files.writeString(REGISTRATIONS_FILE, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException var4) {
         LOGGER.error("Failed to save dashboard registrations: {}", var4.getMessage(), var4);
      }
   }

   public void cleanupExpiredPending() {
      long now = System.currentTimeMillis();
      this.pendingRegistrations.entrySet().removeIf(entry -> now > entry.getValue().getExpiresAt());
   }

   private static class PendingRegistration {
      private final String token;
      private final UUID minecraftUuid;
      private final String minecraftUsername;
      private final long expiresAt;

      public PendingRegistration(String token, UUID minecraftUuid, String minecraftUsername, long expiresAt) {
         this.token = token;
         this.minecraftUuid = minecraftUuid;
         this.minecraftUsername = minecraftUsername;
         this.expiresAt = expiresAt;
      }

      public String getToken() {
         return this.token;
      }

      public UUID getMinecraftUuid() {
         return this.minecraftUuid;
      }

      public String getMinecraftUsername() {
         return this.minecraftUsername;
      }

      public long getExpiresAt() {
         return this.expiresAt;
      }
   }
}
