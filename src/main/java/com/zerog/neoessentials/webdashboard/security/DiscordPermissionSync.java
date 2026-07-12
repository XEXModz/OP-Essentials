package com.zerog.neoessentials.webdashboard.security;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionStorage;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordPermissionSync {
   private static final Logger LOGGER = LoggerFactory.getLogger(DiscordPermissionSync.class);
   private static DiscordPermissionSync INSTANCE;
   private boolean enabled = true;
   private DiscordAuthConfig authConfig = DiscordAuthConfig.load();

   private DiscordPermissionSync() {
   }

   public static DiscordPermissionSync getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DiscordPermissionSync();
      }

      return INSTANCE;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public DiscordPermissionSync.SyncResult syncPlayerPermissions(ServerPlayer player) {
      if (!this.enabled) {
         return new DiscordPermissionSync.SyncResult(false, "Permission sync is disabled", 0);
      } else {
         try {
            if (!SDLinkEventListener.isBotReady()) {
               return new DiscordPermissionSync.SyncResult(false, "Discord bot not ready", 0);
            } else {
               DiscordAuthProvider provider = DiscordAuthProvider.getInstance();
               DiscordUser discordUser = provider.getLinkedAccountByUuid(player.getUUID());
               if (discordUser != null && discordUser.isLinked()) {
                  int permissionsGranted = 0;
                  PermissionManager permManager = PermissionAPI.getManager();

                  for (String role : discordUser.getDiscordRoles()) {
                     String permissionGroup = this.mapDiscordRoleToPermissionGroup(role);
                     if (permissionGroup != null && !permissionGroup.isEmpty()) {
                        LOGGER.debug(
                           "Granting permission group '{}' to player {} based on Discord role '{}'",
                           new Object[]{permissionGroup, player.getName().getString(), role}
                        );
                        PermissionUser user = permManager.getUser(player.getUUID());
                        if (user != null) {
                           user.setGroup(permissionGroup);
                           permissionsGranted++;

                           try {
                              PermissionStorage.save(permManager);
                           } catch (Exception var11) {
                              LOGGER.error("Failed to save permission changes for player {}", player.getName().getString(), var11);
                           }
                        }
                     } else {
                        LOGGER.debug("No permission mapping for Discord role: {}", role);
                     }
                  }

                  return new DiscordPermissionSync.SyncResult(true, "Permissions synced successfully", permissionsGranted);
               } else {
                  return new DiscordPermissionSync.SyncResult(false, "Player not linked to Discord", 0);
               }
            }
         } catch (Exception var12) {
            LOGGER.error("Error syncing permissions for player {}: {}", player.getName().getString(), var12.getMessage());
            return new DiscordPermissionSync.SyncResult(false, "Error: " + var12.getMessage(), 0);
         }
      }
   }

   private String mapDiscordRoleToPermissionGroup(String discordRole) {
      if (discordRole != null && !discordRole.isEmpty()) {
         if (this.authConfig != null && this.authConfig.isPermissionSyncEnabled()) {
            Map<String, List<String>> permissionMappings = this.authConfig.getPermissionMappings();
            if (permissionMappings.containsKey(discordRole)) {
               List<String> permissions = permissionMappings.get(discordRole);
               if (permissions != null && !permissions.isEmpty()) {
                  String mappedGroup = permissions.get(0);
                  LOGGER.debug("Found custom role mapping: Discord role '{}' -> permission group '{}'", discordRole, mappedGroup);
                  return mappedGroup;
               }
            }
         }

         String roleLower = discordRole.toLowerCase();
         if (roleLower.contains("admin") || roleLower.contains("administrator")) {
            return "admin";
         } else if (roleLower.contains("moderator") || roleLower.contains("mod")) {
            return "moderator";
         } else if (roleLower.contains("helper") || roleLower.contains("support")) {
            return "helper";
         } else if (roleLower.contains("vip") || roleLower.contains("premium")) {
            return "vip";
         } else {
            return !roleLower.contains("member") && !roleLower.contains("player") ? null : "default";
         }
      } else {
         return null;
      }
   }

   public void reloadConfig() {
      this.authConfig = DiscordAuthConfig.load();
      LOGGER.info("Discord auth config reloaded for permission sync");
   }

   public static class SyncResult {
      private final boolean success;
      private final String message;
      private final int permissionsGranted;

      public SyncResult(boolean success, String message, int permissionsGranted) {
         this.success = success;
         this.message = message;
         this.permissionsGranted = permissionsGranted;
      }

      public boolean isSuccess() {
         return this.success;
      }

      public String getMessage() {
         return this.message;
      }

      public int getPermissionsGranted() {
         return this.permissionsGranted;
      }
   }
}
