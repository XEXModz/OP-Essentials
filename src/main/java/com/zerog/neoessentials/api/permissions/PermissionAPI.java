package com.zerog.neoessentials.api.permissions;

import com.mojang.authlib.GameProfile;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.permissions.ExternalPermissionAdapter;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionAPI {
   private static PermissionManager manager;
   private static ExternalPermissionAdapter externalAdapter = null;
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionAPI.class);

   public static void setManager(PermissionManager m) {
      manager = m;
   }

   public static void setExternalAdapter(ExternalPermissionAdapter adapter) {
      externalAdapter = adapter;
      LOGGER.info("External permission adapter set: " + (adapter != null ? adapter.getName() : "none"));
   }

   public static ExternalPermissionAdapter getExternalAdapter() {
      return externalAdapter;
   }

   public static boolean isUsingExternal() {
      return externalAdapter != null;
   }

   public static boolean hasPermission(UUID uuid, String permission) {
      if (uuid == null) {
         LOGGER.warn("PermissionAPI.hasPermission: UUID is null");
         return false;
      } else if (permission != null && !permission.trim().isEmpty()) {
         LOGGER.debug("═══ PERMISSION CHECK ═══");
         LOGGER.debug("Player UUID: {}", uuid);
         LOGGER.debug("Permission: {}", permission);
         LOGGER.debug("External adapter: {}", externalAdapter != null ? externalAdapter.getName() : "NONE");
         if (externalAdapter != null) {
            LOGGER.debug("Using external permission system: {}", externalAdapter.getName());
            boolean hasExternalPerm = externalAdapter.hasPermission(uuid, permission);
            LOGGER.debug("External system returned: {}", hasExternalPerm);
            LOGGER.debug("═══════════════════════");
            return hasExternalPerm;
         } else {
            LOGGER.debug("Using INTERNAL permission system");
            if (ConfigManager.getInstance().isOpsBypassPermissionsEnabled() && isPlayerOpped(uuid)) {
               LOGGER.debug("Player is OP - bypassing permission check");
               LOGGER.debug("Result: TRUE (op bypass)");
               LOGGER.debug("═══════════════════════");
               return true;
            } else if (manager == null) {
               LOGGER.warn("PermissionAPI.hasPermission: PermissionManager is null - returning false");
               LOGGER.debug("Result: FALSE (no manager)");
               LOGGER.debug("═══════════════════════");
               return false;
            } else {
               boolean hasInternalPerm = manager.hasPermission(uuid, permission);
               LOGGER.debug("Internal system returned: {}", hasInternalPerm);
               LOGGER.debug("Result: {}", hasInternalPerm);
               LOGGER.debug("═══════════════════════");
               return hasInternalPerm;
            }
         }
      } else {
         LOGGER.warn("PermissionAPI.hasPermission: Permission string is null or empty");
         return false;
      }
   }

   private static boolean isPlayerOpped(UUID uuid) {
      try {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
               return player.hasPermissions(2);
            }

            GameProfileCache profileCache = server.getProfileCache();
            if (profileCache != null) {
               GameProfile profile = (GameProfile)profileCache.get(uuid).orElse(null);
               if (profile != null) {
                  return server.getPlayerList().isOp(profile);
               }
            }
         }
      } catch (Exception var5) {
         LOGGER.debug("Could not check op status for UUID {}: {}", uuid, var5.getMessage());
      }

      return false;
   }

   public static PermissionManager getManager() {
      return manager;
   }

   public static String getPrefix(UUID uuid) {
      if (uuid == null) {
         LOGGER.warn("PermissionAPI.getPrefix: UUID is null");
         return "";
      } else {
         LOGGER.debug(">>> PermissionAPI.getPrefix() called for UUID: {}", uuid);
         LOGGER.debug(">>> Using external adapter: {}", externalAdapter != null ? externalAdapter.getName() : "NONE");
         if (externalAdapter != null) {
            LOGGER.debug(">>> Querying external adapter for prefix...");
            String prefix = externalAdapter.getPrefix(uuid);
            LOGGER.debug(">>> External adapter returned: [{}]", prefix);
            return prefix != null ? prefix : "";
         } else {
            LOGGER.debug(">>> Using internal permission system (no external adapter)");
            if (manager == null) {
               LOGGER.warn("PermissionAPI.getPrefix: PermissionManager is null");
               return "";
            } else {
               PermissionUser user = manager.getUser(uuid);
               String groupName = user != null && user.getGroup() != null ? user.getGroup() : manager.getDefaultGroup();
               if (groupName == null) {
                  LOGGER.warn("PermissionAPI.getPrefix: Default group name is null");
                  return "";
               } else {
                  PermissionGroup group = manager.getGroup(groupName);
                  if (group == null) {
                     LOGGER.warn("PermissionAPI.getPrefix: No PermissionGroup found for group '" + groupName + "'");
                     return "";
                  } else {
                     String prefix = group.getPrefix();
                     LOGGER.debug(">>> Internal system prefix: [{}]", prefix);
                     return prefix != null ? prefix : "";
                  }
               }
            }
         }
      }
   }

   public static String getSuffix(UUID uuid) {
      if (uuid == null) {
         LOGGER.warn("PermissionAPI.getSuffix: UUID is null");
         return "";
      } else if (externalAdapter != null) {
         String suffix = externalAdapter.getSuffix(uuid);
         return suffix != null ? suffix : "";
      } else if (manager == null) {
         LOGGER.warn("PermissionAPI.getSuffix: PermissionManager is null");
         return "";
      } else {
         PermissionUser user = manager.getUser(uuid);
         if (user == null) {
            LOGGER.warn("PermissionAPI.getSuffix: No PermissionUser found for UUID " + uuid);
         }

         String groupName = user != null && user.getGroup() != null ? user.getGroup() : manager.getDefaultGroup();
         if (groupName == null) {
            LOGGER.warn("PermissionAPI.getSuffix: Default group name is null");
            return "";
         } else {
            PermissionGroup group = manager.getGroup(groupName);
            if (group == null) {
               LOGGER.warn("PermissionAPI.getSuffix: No PermissionGroup found for group '" + groupName + "'");
               return "";
            } else {
               String suffix = group.getSuffix();
               return suffix != null ? suffix : "";
            }
         }
      }
   }

   public static void reload() throws Exception {
      if (externalAdapter != null) {
         externalAdapter.reload();
      } else {
         if (manager == null) {
            LOGGER.warn("PermissionAPI.reload: Both externalAdapter and manager are null - nothing to reload");
            throw new IllegalStateException("Permission system not initialized - cannot reload");
         }

         manager.reload();
      }
   }
}
