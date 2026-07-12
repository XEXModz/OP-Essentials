package com.zerog.neoessentials.api.permissions.external;

import com.zerog.neoessentials.api.permissions.PermissionRegistry;
import com.zerog.neoessentials.api.permissions.PermissionScanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalPermissionProvider {
   private static final Logger LOGGER = LoggerFactory.getLogger(ExternalPermissionProvider.class);
   private static List<String> cachedPermissions = null;
   private static long lastCacheTime = 0L;
   private static final long CACHE_DURATION_MS = 30000L;

   public static List<String> getAllNeoEssentialsPermissions() {
      try {
         long now = System.currentTimeMillis();
         if (cachedPermissions != null && now - lastCacheTime < 30000L) {
            return cachedPermissions;
         } else {
            PermissionRegistry registry = PermissionRegistry.getInstance();
            PermissionScanner scanner = PermissionScanner.getInstance();
            if (cachedPermissions == null) {
               scanner.scanForPermissions();
            }

            Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
            allPermissions.addAll(scanner.getDiscoveredPermissions());
            cachedPermissions = allPermissions.stream().sorted().collect(Collectors.toList());
            lastCacheTime = now;
            LOGGER.debug("External plugin requested {} NeoEssentials permissions", cachedPermissions.size());
            return cachedPermissions;
         }
      } catch (Exception var5) {
         LOGGER.error("Failed to provide permissions to external plugin", var5);
         return cachedPermissions != null ? cachedPermissions : List.of();
      }
   }

   public static List<String> getPermissionsStartingWith(String prefix) {
      try {
         return getAllNeoEssentialsPermissions().stream().filter(perm -> perm.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
      } catch (Exception var2) {
         LOGGER.error("Failed to filter permissions for external plugin", var2);
         return List.of();
      }
   }

   public static boolean hasPermission(String permission) {
      try {
         return getAllNeoEssentialsPermissions().contains(permission);
      } catch (Exception var2) {
         LOGGER.error("Failed to check permission for external plugin", var2);
         return false;
      }
   }

   public static List<String> getPermissionsByCategory(String category) {
      try {
         return getAllNeoEssentialsPermissions().stream().filter(perm -> {
            String[] parts = perm.split("\\.");
            return parts.length >= 2 && parts[1].equalsIgnoreCase(category);
         }).collect(Collectors.toList());
      } catch (Exception var2) {
         LOGGER.error("Failed to get permissions by category for external plugin", var2);
         return List.of();
      }
   }

   public static void clearCache() {
      cachedPermissions = null;
      lastCacheTime = 0L;
      LOGGER.debug("Permission cache cleared");
   }

   public static void initialize() {
      LOGGER.info("Initializing External Permission Provider for PermissionsEX integration...");

      try {
         clearCache();
         List<String> permissions = getAllNeoEssentialsPermissions();
         LOGGER.info("External Permission Provider initialized with {} permissions available", permissions.size());
         LOGGER.info("Sample permissions available for external plugins:");
         permissions.stream().limit(10L).forEach(perm -> LOGGER.info("  - {}", perm));
         if (permissions.size() > 10) {
            LOGGER.info("  ... and {} more permissions", permissions.size() - 10);
         }
      } catch (Exception var1) {
         LOGGER.error("Failed to initialize External Permission Provider", var1);
      }
   }

   public static List<String> getWildcardPermissions() {
      return List.of(
         "neoessentials.*",
         "neoessentials.teleport.*",
         "neoessentials.economy.*",
         "neoessentials.kits.*",
         "neoessentials.chat.*",
         "neoessentials.admin.*",
         "neoessentials.utility.*",
         "neoessentials.teleport.admin.*",
         "neoessentials.teleport.home.*",
         "neoessentials.teleport.spawn.*",
         "neoessentials.teleport.warp.*",
         "neoessentials.teleport.request.*",
         "neoessentials.teleport.misc.*"
      );
   }

   public static String exportForPermissionsEX() {
      StringBuilder sb = new StringBuilder();
      sb.append("# NeoEssentials Permissions for PermissionsEX\n");
      sb.append("# Generated automatically - copy these permissions into your PermissionsEX configuration\n");
      sb.append("# Total permissions: ").append(getAllNeoEssentialsPermissions().size()).append("\n\n");
      String[] categories = new String[]{"teleport", "economy", "kits", "chat", "utility", "admin"};

      for (String category : categories) {
         List<String> categoryPerms = getPermissionsByCategory(category);
         if (!categoryPerms.isEmpty()) {
            sb.append("# ").append(category.toUpperCase()).append(" PERMISSIONS (").append(categoryPerms.size()).append(")\n");

            for (String perm : categoryPerms) {
               sb.append(perm).append("\n");
            }

            sb.append("\n");
         }
      }

      sb.append("# WILDCARD PERMISSIONS\n");

      for (String wildcard : getWildcardPermissions()) {
         sb.append(wildcard).append("\n");
      }

      return sb.toString();
   }
}
