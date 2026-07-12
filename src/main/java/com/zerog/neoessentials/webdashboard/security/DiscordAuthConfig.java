package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordAuthConfig {
   private static final Logger LOGGER = LoggerFactory.getLogger(DiscordAuthConfig.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Path CONFIG_FILE = Paths.get("config", "neoessentials", "discord_auth.json");
   public static final String CONFIG_NAME = "discord_auth.json";
   private boolean enabled = true;
   private boolean requireLinkedAccount = true;
   private boolean allowAutoRegistration = true;
   private User.Role defaultRole = User.Role.VIEWER;
   private Map<String, String> roleMapping = new HashMap<>();
   private Map<String, List<String>> roleHierarchy = new HashMap<>();
   private List<String> whitelistedRoles = new ArrayList<>();
   private List<String> blacklistedUsers = new ArrayList<>();
   private long sessionDuration = 86400000L;
   private boolean permissionSyncEnabled = true;
   private boolean syncOnJoin = true;
   private Map<String, List<String>> permissionMappings = new HashMap<>();
   private String oauth2ClientId = "";
   private String oauth2ClientSecret = "";
   private String oauth2RedirectUri = "http://localhost:8080/api/auth/discord/callback";
   private String oauth2Scopes = "identify guilds.members.read";

   private DiscordAuthConfig() {
   }

   public static DiscordAuthConfig load() {
      DiscordAuthConfig config = new DiscordAuthConfig();

      try {
         ConfigManager configManager = ConfigManager.getInstance();
         JsonObject root = configManager.getConfig("discord_auth.json");
         if (root == null) {
            LOGGER.warn("Failed to load Discord auth config from ConfigManager, using defaults");
            return config;
         }

         config.enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
         config.requireLinkedAccount = !root.has("requireLinkedAccount") || root.get("requireLinkedAccount").getAsBoolean();
         config.allowAutoRegistration = !root.has("allowAutoRegistration") || root.get("allowAutoRegistration").getAsBoolean();
         if (root.has("defaultRole")) {
            try {
               config.defaultRole = User.Role.valueOf(root.get("defaultRole").getAsString().toUpperCase());
            } catch (IllegalArgumentException var5) {
               LOGGER.warn("Invalid defaultRole in config, using VIEWER: {}", root.get("defaultRole").getAsString());
               config.defaultRole = User.Role.VIEWER;
            }
         }

         if (root.has("roleMapping")) {
            JsonObject mappingObj = root.getAsJsonObject("roleMapping");
            mappingObj.entrySet().forEach(entry -> {
               if (!((String)entry.getKey()).startsWith("_")) {
                  config.roleMapping.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString().toUpperCase());
               }
            });
         }

         if (root.has("roleHierarchy")) {
            JsonObject hierarchyObj = root.getAsJsonObject("roleHierarchy");
            hierarchyObj.entrySet().forEach(entry -> {
               if (!((String)entry.getKey()).startsWith("_")) {
                  List<String> roles = new ArrayList<>();
                  ((JsonElement)entry.getValue()).getAsJsonArray().forEach(elem -> roles.add(elem.getAsString()));
                  config.roleHierarchy.put((String)entry.getKey(), roles);
               }
            });
         }

         if (root.has("whitelistedRoles")) {
            root.getAsJsonArray("whitelistedRoles").forEach(elem -> config.whitelistedRoles.add(elem.getAsString()));
         }

         if (root.has("blacklistedUsers")) {
            root.getAsJsonArray("blacklistedUsers").forEach(elem -> config.blacklistedUsers.add(elem.getAsString()));
         }

         if (root.has("sessionDuration")) {
            config.sessionDuration = root.get("sessionDuration").getAsLong();
         }

         if (root.has("permissionSync")) {
            JsonObject syncObj = root.getAsJsonObject("permissionSync");
            if (syncObj.has("enabled")) {
               config.permissionSyncEnabled = syncObj.get("enabled").getAsBoolean();
            }

            if (syncObj.has("syncOnJoin")) {
               config.syncOnJoin = syncObj.get("syncOnJoin").getAsBoolean();
            }

            if (syncObj.has("permissionMappings")) {
               JsonObject mappingsObj = syncObj.getAsJsonObject("permissionMappings");
               mappingsObj.entrySet().forEach(entry -> {
                  if (!((String)entry.getKey()).startsWith("_")) {
                     List<String> permissions = new ArrayList<>();
                     ((JsonElement)entry.getValue()).getAsJsonArray().forEach(elem -> permissions.add(elem.getAsString()));
                     config.permissionMappings.put((String)entry.getKey(), permissions);
                  }
               });
            }
         }

         if (root.has("oauth2")) {
            JsonObject oauth2Obj = root.getAsJsonObject("oauth2");
            if (oauth2Obj.has("clientId")) {
               config.oauth2ClientId = oauth2Obj.get("clientId").getAsString();
            }

            if (oauth2Obj.has("clientSecret")) {
               config.oauth2ClientSecret = oauth2Obj.get("clientSecret").getAsString();
            }

            if (oauth2Obj.has("redirectUri")) {
               config.oauth2RedirectUri = oauth2Obj.get("redirectUri").getAsString();
            }

            if (oauth2Obj.has("scopes")) {
               config.oauth2Scopes = oauth2Obj.get("scopes").getAsString();
            }
         }

         LOGGER.info(
            "Discord auth config loaded successfully. Enabled: {}, Permission Sync: {}, OAuth2 configured: {}",
            new Object[]{config.enabled, config.permissionSyncEnabled, !config.oauth2ClientId.isEmpty()}
         );
      } catch (Exception var6) {
         LOGGER.error("Failed to load Discord auth config: {}", var6.getMessage(), var6);
      }

      return config;
   }

   public User.Role mapDiscordRole(String discordRoleId) {
      if (discordRoleId == null) {
         return this.defaultRole;
      } else {
         String mapped = this.roleMapping.get(discordRoleId);
         if (mapped == null) {
            return this.defaultRole;
         } else {
            try {
               return User.Role.valueOf(mapped);
            } catch (IllegalArgumentException var4) {
               LOGGER.warn("Invalid role mapping for Discord role ID '{}': {}", discordRoleId, mapped);
               return this.defaultRole;
            }
         }
      }
   }

   public User.Role getHighestRole(List<String> discordRoleIds) {
      if (discordRoleIds != null && !discordRoleIds.isEmpty()) {
         User.Role highestRole = this.defaultRole;

         for (String discordRoleId : discordRoleIds) {
            User.Role mappedRole = this.mapDiscordRole(discordRoleId);
            if (mappedRole.ordinal() > highestRole.ordinal()) {
               highestRole = mappedRole;
            }
         }

         return highestRole;
      } else {
         return this.defaultRole;
      }
   }

   public boolean passesWhitelist(List<String> discordRoleIds) {
      if (this.whitelistedRoles.isEmpty()) {
         return true;
      } else {
         return discordRoleIds != null && !discordRoleIds.isEmpty()
            ? discordRoleIds.stream().anyMatch(roleId -> this.whitelistedRoles.contains(roleId))
            : false;
      }
   }

   public boolean isBlacklisted(String discordId) {
      return discordId != null && this.blacklistedUsers.contains(discordId);
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean requiresLinkedAccount() {
      return this.requireLinkedAccount;
   }

   public boolean allowsAutoRegistration() {
      return this.allowAutoRegistration;
   }

   public User.Role getDefaultRole() {
      return this.defaultRole;
   }

   public Map<String, String> getRoleMapping() {
      return new HashMap<>(this.roleMapping);
   }

   public Map<String, List<String>> getRoleHierarchy() {
      return new HashMap<>(this.roleHierarchy);
   }

   public List<String> getWhitelistedRoles() {
      return new ArrayList<>(this.whitelistedRoles);
   }

   public List<String> getBlacklistedUsers() {
      return new ArrayList<>(this.blacklistedUsers);
   }

   public long getSessionDuration() {
      return this.sessionDuration;
   }

   public boolean isPermissionSyncEnabled() {
      return this.permissionSyncEnabled;
   }

   public boolean isSyncOnJoin() {
      return this.syncOnJoin;
   }

   public Map<String, List<String>> getPermissionMappings() {
      return new HashMap<>(this.permissionMappings);
   }

   public String getOauth2ClientId() {
      return this.oauth2ClientId;
   }

   public String getOauth2ClientSecret() {
      return this.oauth2ClientSecret;
   }

   public String getOauth2RedirectUri() {
      return this.oauth2RedirectUri;
   }

   public String getOauth2Scopes() {
      return this.oauth2Scopes;
   }

   public boolean isOauth2Configured() {
      return this.oauth2ClientId != null && !this.oauth2ClientId.isEmpty() && this.oauth2ClientSecret != null && !this.oauth2ClientSecret.isEmpty();
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setRequireLinkedAccount(boolean require) {
      this.requireLinkedAccount = require;
   }

   public void setAllowAutoRegistration(boolean allow) {
      this.allowAutoRegistration = allow;
   }

   public void setDefaultRole(User.Role role) {
      this.defaultRole = role;
   }

   public void setSessionDuration(long duration) {
      this.sessionDuration = duration;
   }

   public void setPermissionSyncEnabled(boolean enabled) {
      this.permissionSyncEnabled = enabled;
   }

   public void setSyncOnJoin(boolean syncOnJoin) {
      this.syncOnJoin = syncOnJoin;
   }
}
