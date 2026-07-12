package com.zerog.neoessentials.permissions;

import com.zerog.neoessentials.config.ConfigManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionManager.class);
   private final Map<String, PermissionGroup> groups = new ConcurrentHashMap<>();
   private final Map<UUID, PermissionUser> users = new ConcurrentHashMap<>();
   private String defaultGroup;
   private final Map<String, PermissionManager.CachedPermission> permissionCache = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastModification = new ConcurrentHashMap<>();
   private static final long MODIFICATION_COOLDOWN = 1000L;

   private long getCacheTtlMs() {
      int minutes = ConfigManager.getInstance().getPermissionCacheExpiryMinutes();
      return (long)minutes * 60000L;
   }

   public PermissionManager() {
      String configDefault = ConfigManager.getInstance().getDefaultGroup();
      this.defaultGroup = configDefault != null && !configDefault.trim().isEmpty() ? configDefault.trim() : "default";
   }

   public void reload() throws Exception {
      this.groups.clear();
      this.users.clear();
      this.permissionCache.clear();
      PermissionStorage.load(this);
      LOGGER.info("Permissions reloaded, cache cleared");
   }

   public void setDefaultGroup(String groupName) {
      if (groupName != null && !groupName.trim().isEmpty()) {
         this.defaultGroup = groupName.trim();
      }
   }

   public String getDefaultGroup() {
      return this.defaultGroup;
   }

   public void addGroup(PermissionGroup group) {
      this.groups.put(group.getName().toLowerCase(), group);
   }

   public PermissionGroup getGroup(String name) {
      return this.groups.get(name.toLowerCase());
   }

   public Collection<PermissionGroup> getGroups() {
      return this.groups.values();
   }

   public void addUser(PermissionUser user) {
      this.users.put(user.getUuid(), user);
   }

   public PermissionUser getUser(UUID uuid) {
      PermissionUser user = this.users.get(uuid);
      if (user == null) {
         user = new PermissionUser(uuid, this.defaultGroup);
         this.addUser(user);
         LOGGER.info("Auto-created user {} with default group '{}'", uuid, this.defaultGroup);
      }

      return user;
   }

   public Collection<PermissionUser> getUsers() {
      return this.users.values();
   }

   public boolean hasPermission(UUID uuid, String permission) {
      permission = permission.toLowerCase();
      String cacheKey = uuid + ":" + permission;
      boolean cacheEnabled = ConfigManager.getInstance().isPermissionCacheEnabled();
      long cacheTtl = this.getCacheTtlMs();
      if (cacheEnabled) {
         PermissionManager.CachedPermission cached = this.permissionCache.get(cacheKey);
         if (cached != null && !cached.isExpired(cacheTtl)) {
            return cached.result;
         }
      }

      boolean result = this.computePermission(uuid, permission);
      if (cacheEnabled) {
         this.permissionCache.put(cacheKey, new PermissionManager.CachedPermission(result));
         if (this.permissionCache.size() % 100 == 0) {
            this.cleanExpiredCache();
         }
      }

      return result;
   }

   private boolean computePermission(UUID uuid, String permission) {
      LOGGER.debug("Computing permission '{}' for UUID {}", permission, uuid);
      PermissionUser user = this.getUser(uuid);
      String groupName = user != null && user.getGroup() != null ? user.getGroup() : this.defaultGroup;
      LOGGER.debug("  User group: {}", groupName);
      if (user != null && this.hasNegativePermission(user.getPermissions(), permission)) {
         LOGGER.debug("  -> Denied by user negative permission");
         return false;
      } else if (this.hasGroupNegativePermission(groupName, permission, new HashSet<>())) {
         LOGGER.debug("  -> Denied by group negative permission");
         return false;
      } else if (user != null && this.hasPermissionWithWildcards(user.getPermissions(), permission)) {
         LOGGER.debug("  -> Granted by user permission");
         return true;
      } else {
         boolean result = this.hasGroupPermission(groupName, permission, new HashSet<>());
         LOGGER.debug("  -> Group permission check result: {}", result);
         return result;
      }
   }

   private void cleanExpiredCache() {
      long cacheTtl = this.getCacheTtlMs();
      this.permissionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(cacheTtl));
      LOGGER.debug("Cleaned permission cache, {} entries remaining", this.permissionCache.size());
   }

   public void clearCache() {
      this.permissionCache.clear();
      LOGGER.debug("Permission cache cleared");
   }

   public static boolean isValidPermission(String permission) {
      if (permission != null && !permission.trim().isEmpty()) {
         permission = permission.trim().toLowerCase();
         if (permission.endsWith(".*")) {
            String prefix = permission.substring(0, permission.length() - 2);
            return prefix.isEmpty() || !prefix.matches("^[a-z0-9._-]+$") ? false : !prefix.startsWith(".") && !prefix.endsWith(".") && !prefix.contains("..");
         } else if (permission.startsWith("-")) {
            String actualPerm = permission.substring(1);
            return isValidPermission(actualPerm);
         } else if (!permission.matches("^[a-z0-9._-]+$")) {
            return false;
         } else {
            return permission.startsWith(".") || permission.endsWith(".") ? false : !permission.contains("..");
         }
      } else {
         return false;
      }
   }

   public boolean canModifyPermissions(UUID executor) {
      long currentTime = System.currentTimeMillis();
      Long lastTime = this.lastModification.get(executor);
      if (lastTime != null && currentTime - lastTime < 1000L) {
         return false;
      } else {
         this.lastModification.put(executor, currentTime);
         return true;
      }
   }

   private boolean hasNegativePermission(Set<String> perms, String permission) {
      for (String perm : perms) {
         if (perm.equals("-" + permission)) {
            return true;
         }

         if (perm.startsWith("-")) {
            String neg = perm.substring(1);
            if (neg.endsWith(".*")) {
               String prefix = neg.substring(0, neg.length() - 2);
               if (permission.startsWith(prefix + ".")) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean hasGroupNegativePermission(String groupName, String permission, Set<String> visited) {
      if (groupName != null && !visited.contains(groupName.toLowerCase())) {
         visited.add(groupName.toLowerCase());
         PermissionGroup group = this.getGroup(groupName);
         if (group == null) {
            return false;
         } else if (this.hasNegativePermission(group.getPermissions(), permission)) {
            return true;
         } else {
            for (String parent : group.getInherits()) {
               if (this.hasGroupNegativePermission(parent, permission, visited)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean hasPermissionWithWildcards(Set<String> perms, String permission) {
      for (String perm : perms) {
         LOGGER.debug("Checking perm '{}' against permission '{}'", perm, permission);
         if (perm.equals(permission)) {
            LOGGER.debug("  -> Exact match!");
            return true;
         }

         if (perm.endsWith(".*")) {
            String prefix = perm.substring(0, perm.length() - 2);
            LOGGER.debug("  -> Wildcard check: does '{}' start with '{}'?", permission, prefix + ".");
            if (permission.startsWith(prefix + ".")) {
               LOGGER.debug("  -> Wildcard match!");
               return true;
            }
         }
      }

      LOGGER.debug("No match found for permission '{}'", permission);
      return false;
   }

   private boolean hasGroupPermission(String groupName, String permission, Set<String> visited) {
      if (groupName != null && !visited.contains(groupName.toLowerCase())) {
         visited.add(groupName.toLowerCase());
         PermissionGroup group = this.getGroup(groupName);
         if (group == null) {
            LOGGER.debug("  Group '{}' not found", groupName);
            return false;
         } else {
            LOGGER.debug("  Checking group '{}' with {} permissions", groupName, group.getPermissions().size());
            if (this.hasPermissionWithWildcards(group.getPermissions(), permission)) {
               return true;
            } else {
               for (String parent : group.getInherits()) {
                  LOGGER.debug("  Checking inherited group '{}'", parent);
                  if (this.hasGroupPermission(parent, permission, visited)) {
                     return true;
                  }
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static class CachedPermission {
      final boolean result;
      final long timestamp;

      CachedPermission(boolean result) {
         this.result = result;
         this.timestamp = System.currentTimeMillis();
      }

      boolean isExpired(long ttlMs) {
         return System.currentTimeMillis() - this.timestamp > ttlMs;
      }
   }
}
