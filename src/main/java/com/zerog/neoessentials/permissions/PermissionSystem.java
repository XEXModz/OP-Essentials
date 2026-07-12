package com.zerog.neoessentials.permissions;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.api.permissions.PermissionValidator;
import com.zerog.neoessentials.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionSystem {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSystem.class);
   private static boolean initialized = false;
   private static PermissionManager manager;
   private static boolean usingExternal = false;

   public static void initialize() {
      if (initialized) {
         LOGGER.warn("Permission system already initialized, skipping");
      } else {
         try {
            LOGGER.info("═══════════════════════════════════════════════════════════");
            LOGGER.info("Initializing NeoEssentials Permission System...");
            LOGGER.info("═══════════════════════════════════════════════════════════");
            boolean useExternal = ConfigManager.getInstance().isExternalPermissionsEnabled();
            LOGGER.info("External permissions enabled in config: {}", useExternal);
            if (useExternal) {
               LOGGER.info("Attempting to detect external permission system...");
               ExternalPermissionAdapter externalAdapter = detectExternalPermissions();
               if (externalAdapter != null && externalAdapter.isAvailable()) {
                  LOGGER.info("✓ External permission system detected: {}", externalAdapter.getName());
                  String detectedPlugin = null;

                  try {
                     Class.forName("net.luckperms.api.LuckPerms");
                     detectedPlugin = "LuckPerms";
                  } catch (ClassNotFoundException var6) {
                  }

                  try {
                     Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
                     detectedPlugin = "FTB Ranks";
                  } catch (ClassNotFoundException var5) {
                  }

                  try {
                     Class.forName("org.bukkit.Bukkit");
                     detectedPlugin = "Bukkit/Arclight";
                  } catch (ClassNotFoundException var4) {
                  }

                  if (detectedPlugin != null) {
                     LOGGER.info("✓ Detected permission plugin: {}", detectedPlugin);
                  }

                  LOGGER.info("✓ Using {} for ALL permission checks, prefixes, and suffixes", externalAdapter.getName());
                  PermissionAPI.setExternalAdapter(externalAdapter);
                  usingExternal = true;
                  LOGGER.warn("  ⚠ Internal permissions.json will be IGNORED for all permission checks");
                  LOGGER.warn("  ⚠ All permissions/groups MUST be managed in {}", externalAdapter.getName());
                  manager = new PermissionManager();
                  PermissionStorage.load(manager);
                  PermissionAPI.setManager(manager);
                  initialized = true;
                  LOGGER.info("✓ Permission system initialized with {} (internal groups loaded but NOT USED)", externalAdapter.getName());
                  LOGGER.info("═══════════════════════════════════════════════════════════");
                  return;
               }

               LOGGER.warn("✗ External permissions enabled but no compatible system found!");
               LOGGER.warn("✗ Falling back to internal NeoEssentials permission system");
               LOGGER.warn("✗ To use LuckPerms: Install LuckPerms mod and set useExternalPermissions: true");
            } else {
               LOGGER.info("External permissions disabled in config");
            }

            LOGGER.info("Loading internal NeoEssentials permission system...");
            manager = new PermissionManager();
            PermissionStorage.load(manager);
            PermissionAPI.setManager(manager);
            usingExternal = false;
            initialized = true;
            LOGGER.info("✓ Internal permission system initialized with {} groups", manager.getGroups().size());
            if (!manager.getGroups().isEmpty()) {
               LOGGER.info("Loaded permission groups:");

               for (PermissionGroup group : manager.getGroups()) {
                  LOGGER.info(
                     "  ├─ Group: '{}' ({} permissions, prefix: '{}')", new Object[]{group.getName(), group.getPermissions().size(), group.getPrefix()}
                  );
               }
            }

            LOGGER.info("Permission System Configuration:");
            LOGGER.info("  ├─ Ops bypass permissions: {}", ConfigManager.getInstance().isOpsBypassPermissionsEnabled());
            LOGGER.info("  ├─ Permission caching: {}", ConfigManager.getInstance().isPermissionCacheEnabled());
            LOGGER.info("  ├─ Cache expiry: {} minutes", ConfigManager.getInstance().getPermissionCacheExpiryMinutes());
            LOGGER.info("  └─ Default group: {}", manager.getDefaultGroup());
            LOGGER.info("");
            PermissionValidator.ValidationResult validation = PermissionValidator.validate(manager);
            if (validation.hasIssues()) {
               LOGGER.warn("⚠ PERMISSION VALIDATION FOUND {} ISSUES!", validation.getIssuesFound());
               LOGGER.warn("⚠ Some permissions may not work correctly!");
               LOGGER.warn("⚠ Check the validation output above for details.");

               for (String warning : validation.getWarnings()) {
                  LOGGER.warn(warning);
               }

               for (String suggestion : validation.getSuggestions()) {
                  LOGGER.warn(suggestion);
               }
            } else {
               LOGGER.info("✓ Permission validation passed - all permissions are properly configured");
            }

            LOGGER.info("═══════════════════════════════════════════════════════════");
         } catch (Exception var7) {
            LOGGER.error("Failed to initialize permission system", var7);
            throw new RuntimeException("Permission system initialization failed", var7);
         }
      }
   }

   private static ExternalPermissionAdapter detectExternalPermissions() {
      LOGGER.info("Scanning for external permission systems...");

      try {
         Class.forName("net.luckperms.api.LuckPerms");
         LOGGER.info("  ├─ LuckPerms API class found, attempting to load adapter...");
         LuckPermsAdapter adapter = new LuckPermsAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ LuckPerms adapter loaded successfully");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ LuckPerms detected but adapter not available");
      } catch (ClassNotFoundException var11) {
         LOGGER.info("  ├─ LuckPerms not found (ClassNotFoundException)");
      } catch (Exception var12) {
         LOGGER.error("  └─ ✗ Failed to initialize LuckPerms adapter: {}", var12.getMessage(), var12);
      }

      try {
         Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
         LOGGER.info("  ├─ FTB Ranks API class found, attempting to load adapter...");
         FtbRanksAdapter adapter = new FtbRanksAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ FTB Ranks adapter loaded successfully");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ FTB Ranks detected but adapter not available");
      } catch (ClassNotFoundException var9) {
         LOGGER.info("  ├─ FTB Ranks not found (ClassNotFoundException)");
      } catch (Exception var10) {
         LOGGER.error("  └─ ✗ Failed to initialize FTB Ranks adapter: {}", var10.getMessage(), var10);
      }

      try {
         Class.forName("io.izzel.arclight.api.Arclight");
         LOGGER.info("  ├─ Arclight API class found, attempting to load Bukkit-compatible adapter...");
         BukkitSpongeAdapter adapter = new BukkitSpongeAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ Arclight adapter loaded successfully (Bukkit-compatible)");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ Arclight detected but adapter not available");
      } catch (ClassNotFoundException var7) {
         LOGGER.info("  ├─ Arclight not found (ClassNotFoundException)");
      } catch (Exception var8) {
         LOGGER.error("  └─ ✗ Failed to initialize Arclight adapter: {}", var8.getMessage(), var8);
      }

      try {
         Class.forName("org.bukkit.Bukkit");
         LOGGER.info("  ├─ Bukkit API class found (may be Mohist/Arclight), attempting to load adapter...");
         BukkitSpongeAdapter adapter = new BukkitSpongeAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ Bukkit/Mohist/Arclight adapter loaded successfully");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ Bukkit/Mohist/Arclight detected but adapter not available");
      } catch (ClassNotFoundException var5) {
         LOGGER.info("  ├─ Bukkit/Mohist/Arclight not found (ClassNotFoundException)");
      } catch (Exception var6) {
         LOGGER.error("  └─ ✗ Failed to initialize Bukkit/Mohist/Arclight adapter: {}", var6.getMessage(), var6);
      }

      try {
         Class.forName("org.spongepowered.api.Sponge");
         LOGGER.info("  ├─ Sponge API class found, attempting to load adapter...");
         BukkitSpongeAdapter adapter = new BukkitSpongeAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ Sponge adapter loaded successfully");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ Sponge detected but adapter not available");
      } catch (ClassNotFoundException var3) {
         LOGGER.info("  ├─ Sponge not found (ClassNotFoundException)");
      } catch (Exception var4) {
         LOGGER.error("  └─ ✗ Failed to initialize Sponge adapter: {}", var4.getMessage(), var4);
      }

      try {
         Class.forName("com.mohistmc.api.MohistAPI");
         LOGGER.info("  ├─ Mohist API class found, attempting to load Bukkit-compatible adapter...");
         BukkitSpongeAdapter adapter = new BukkitSpongeAdapter();
         if (adapter.isAvailable()) {
            LOGGER.info("  └─ ✓ Mohist adapter loaded successfully (Bukkit-compatible)");
            return adapter;
         }

         LOGGER.warn("  └─ ✗ Mohist detected but adapter not available");
      } catch (ClassNotFoundException var1) {
         LOGGER.info("  ├─ Mohist not found (ClassNotFoundException)");
      } catch (Exception var2) {
         LOGGER.error("  └─ ✗ Failed to initialize Mohist adapter: {}", var2.getMessage(), var2);
      }

      LOGGER.info("  └─ No external permission system detected");
      return null;
   }

   public static void reload() {
      if (!initialized) {
         LOGGER.warn("Cannot reload: permission system not initialized");
         initialize();
      } else {
         try {
            LOGGER.info("Reloading permission system...");
            if (isUsingExternal()) {
               PermissionAPI.reload();
               LOGGER.info("External permission system reloaded");
            } else {
               if (manager != null) {
                  manager.reload();
               } else {
                  LOGGER.warn("PermissionSystem.reload: manager is null, re-initializing...");
                  initialized = false;
                  initialize();
               }

               LOGGER.info("Permission system reloaded successfully");
            }
         } catch (Exception var1) {
            LOGGER.error("Failed to reload permission system", var1);
            throw new RuntimeException("Permission reload failed", var1);
         }
      }
   }

   public static PermissionManager getManager() {
      if (!initialized) {
         LOGGER.error("Permission system not initialized! Call initialize() first!");
         throw new IllegalStateException("Permission system not initialized");
      } else {
         return manager;
      }
   }

   public static boolean isInitialized() {
      return initialized;
   }

   public static boolean isUsingExternal() {
      return usingExternal;
   }

   public static void shutdown() {
      if (initialized) {
         try {
            LOGGER.info("Shutting down permission system...");
            if (manager != null) {
               try {
                  PermissionStorage.save(manager);
               } catch (Exception var1) {
                  LOGGER.error("Failed to save permissions during shutdown", var1);
               }
            }

            LOGGER.info("Permission system shutdown complete");
         } catch (Exception var2) {
            LOGGER.error("Failed to shutdown permission system", var2);
         }
      }
   }
}
