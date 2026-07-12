package com.zerog.neoessentials.vault.api;

import java.util.UUID;

public abstract class VaultPermission {
   public abstract String getName();

   public abstract boolean isEnabled();

   public abstract boolean supportsWorlds();

   public abstract boolean playerHas(String var1, UUID var2, String var3);

   public boolean playerHas(UUID playerId, String permission) {
      return this.playerHas(null, playerId, permission);
   }

   public abstract boolean playerAdd(String var1, UUID var2, String var3);

   public boolean playerAdd(UUID playerId, String permission) {
      return this.playerAdd(null, playerId, permission);
   }

   public abstract boolean playerRemove(String var1, UUID var2, String var3);

   public boolean playerRemove(UUID playerId, String permission) {
      return this.playerRemove(null, playerId, permission);
   }

   public abstract boolean hasGroupSupport();

   public abstract boolean groupHas(String var1, String var2, String var3);

   public abstract boolean groupAdd(String var1, String var2, String var3);

   public abstract boolean groupRemove(String var1, String var2, String var3);

   public abstract boolean playerInGroup(String var1, UUID var2, String var3);

   public boolean playerInGroup(UUID playerId, String group) {
      return this.playerInGroup(null, playerId, group);
   }

   public abstract boolean playerAddGroup(String var1, UUID var2, String var3);

   public abstract boolean playerRemoveGroup(String var1, UUID var2, String var3);

   public abstract String[] getPlayerGroups(String var1, UUID var2);

   public String[] getPlayerGroups(UUID playerId) {
      return this.getPlayerGroups(null, playerId);
   }

   public abstract String getPrimaryGroup(String var1, UUID var2);

   public String getPrimaryGroup(UUID playerId) {
      return this.getPrimaryGroup(null, playerId);
   }

   public abstract String[] getGroups();
}
