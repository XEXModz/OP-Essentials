package com.zerog.neoessentials.permissions;

import java.util.UUID;

public interface ExternalPermissionAdapter {
   boolean hasPermission(UUID var1, String var2);

   String getPrefix(UUID var1);

   String getSuffix(UUID var1);

   void reload();

   String getName();

   boolean isAvailable();

   default String getVersion() {
      return "unknown";
   }

   default boolean isHealthy() {
      return true;
   }

   default int getConsecutiveFailures() {
      return 0;
   }

   default boolean isExplicitlyDenied(UUID uuid, String permission) {
      return false;
   }
}
