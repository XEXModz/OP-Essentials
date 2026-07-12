package com.zerog.neoessentials.util;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionValidator {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionValidator.class);

   public static PermissionValidator.PermissionResult validatePermission(CommandSourceStack source, String permission) {
      try {
         ServerPlayer player = source.getPlayer();
         if (player == null) {
            return source.hasPermission(2)
               ? PermissionValidator.PermissionResult.success()
               : PermissionValidator.PermissionResult.failure("This command can only be used by players or server operators");
         } else {
            UUID playerUuid = player.getUUID();
            if (!PermissionAPI.hasPermission(playerUuid, permission)) {
               LOGGER.debug("Permission denied for player {} ({}): {}", new Object[]{player.getGameProfile().getName(), playerUuid, permission});
               return PermissionValidator.PermissionResult.failure("You don't have permission to use this command.\n§7Required: §f" + permission);
            } else {
               return PermissionValidator.PermissionResult.success(player);
            }
         }
      } catch (Exception var4) {
         LOGGER.error("Error validating permission '{}' for source: {}", new Object[]{permission, var4.getMessage(), var4});
         return PermissionValidator.PermissionResult.failure("Internal permission error");
      }
   }

   public static PermissionValidator.PermissionResult validateAnyPermission(CommandSourceStack source, String... permissions) {
      try {
         ServerPlayer player = source.getPlayer();
         if (player == null) {
            return source.hasPermission(2)
               ? PermissionValidator.PermissionResult.success()
               : PermissionValidator.PermissionResult.failure("This command can only be used by players or server operators");
         } else {
            UUID playerUuid = player.getUUID();

            for (String permission : permissions) {
               if (PermissionAPI.hasPermission(playerUuid, permission)) {
                  return PermissionValidator.PermissionResult.success(player);
               }
            }

            LOGGER.debug(
               "Permission denied for player {} ({}): none of {}",
               new Object[]{player.getGameProfile().getName(), playerUuid, Arrays.toString((Object[])permissions)}
            );
            return PermissionValidator.PermissionResult.failure(
               "You don't have permission to use this command.\n§7Required (any): §f" + String.join("§7 or §f", permissions)
            );
         }
      } catch (Exception var8) {
         LOGGER.error("Error validating permissions {} for source: {}", new Object[]{Arrays.toString((Object[])permissions), var8.getMessage(), var8});
         return PermissionValidator.PermissionResult.failure("Internal permission error");
      }
   }

   public static PermissionValidator.PermissionResult validateAdminPermission(CommandSourceStack source, String adminPermission) {
      try {
         return source.hasPermission(2) ? PermissionValidator.PermissionResult.success(source.getPlayer()) : validatePermission(source, adminPermission);
      } catch (Exception var3) {
         LOGGER.error("Error validating admin permission '{}': {}", new Object[]{adminPermission, var3.getMessage(), var3});
         return PermissionValidator.PermissionResult.failure("Internal permission error");
      }
   }

   public static PermissionValidator.PermissionResult validateTargetPermission(ServerPlayer executor, ServerPlayer target, String basePermission) {
      try {
         UUID executorUuid = executor.getUUID();
         UUID targetUuid = target.getUUID();
         if (executorUuid.equals(targetUuid)) {
            return PermissionValidator.PermissionResult.failure("You cannot target yourself with this command");
         } else if (!PermissionAPI.hasPermission(executorUuid, basePermission)) {
            return PermissionValidator.PermissionResult.failure("You don't have permission to use this command.\n§7Required: §f" + basePermission);
         } else {
            String targetProtectionPerm = basePermission + ".exempt";
            if (PermissionAPI.hasPermission(targetUuid, targetProtectionPerm)) {
               String overridePerm = basePermission + ".override";
               if (!PermissionAPI.hasPermission(executorUuid, overridePerm)) {
                  return PermissionValidator.PermissionResult.failure("You cannot target this player - they are protected");
               }
            }

            return PermissionValidator.PermissionResult.success(executor);
         }
      } catch (Exception var7) {
         LOGGER.error("Error validating target permission: {}", var7.getMessage(), var7);
         return PermissionValidator.PermissionResult.failure("Internal permission error");
      }
   }

   public static class PermissionResult {
      private final boolean hasPermission;
      private final String errorMessage;
      private final ServerPlayer player;

      private PermissionResult(boolean hasPermission, String errorMessage, ServerPlayer player) {
         this.hasPermission = hasPermission;
         this.errorMessage = errorMessage;
         this.player = player;
      }

      public static PermissionValidator.PermissionResult success() {
         return new PermissionValidator.PermissionResult(true, null, null);
      }

      public static PermissionValidator.PermissionResult success(ServerPlayer player) {
         return new PermissionValidator.PermissionResult(true, null, player);
      }

      public static PermissionValidator.PermissionResult failure(String errorMessage) {
         return new PermissionValidator.PermissionResult(false, errorMessage, null);
      }

      public boolean hasPermission() {
         return this.hasPermission;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public ServerPlayer getPlayer() {
         return this.player;
      }
   }
}
