package com.zerog.neoessentials.vault.impl;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionStorage;
import com.zerog.neoessentials.permissions.PermissionUser;
import com.zerog.neoessentials.vault.api.VaultPermission;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoEssentialsPermission extends VaultPermission {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentialsPermission.class);

   @Override
   public String getName() {
      return "NeoEssentials Permissions";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public boolean supportsWorlds() {
      return false;
   }

   @Override
   public boolean playerHas(String world, UUID playerId, String permission) {
      try {
         return PermissionAPI.hasPermission(playerId, permission);
      } catch (Exception var5) {
         LOGGER.error("VaultPermission: playerHas error: {}", var5.getMessage());
         return false;
      }
   }

   @Override
   public boolean playerAdd(String world, UUID playerId, String permission) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionUser user = pm.getUser(playerId);
            user.addPermission(permission);
            PermissionStorage.save(pm);
            return true;
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: playerAdd error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean playerRemove(String world, UUID playerId, String permission) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionUser user = pm.getUser(playerId);
            user.removePermission(permission);
            PermissionStorage.save(pm);
            return true;
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: playerRemove error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean hasGroupSupport() {
      return true;
   }

   @Override
   public boolean groupHas(String world, String group, String permission) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionGroup grp = pm.getGroup(group);
            return grp != null && grp.getPermissions().contains(permission.toLowerCase());
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: groupHas error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean groupAdd(String world, String group, String permission) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionGroup grp = pm.getGroup(group);
            if (grp == null) {
               grp = new PermissionGroup(group);
               pm.addGroup(grp);
            }

            grp.addPermission(permission);
            PermissionStorage.save(pm);
            return true;
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: groupAdd error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean groupRemove(String world, String group, String permission) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionGroup grp = pm.getGroup(group);
            if (grp == null) {
               return false;
            } else {
               grp.removePermission(permission);
               PermissionStorage.save(pm);
               return true;
            }
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: groupRemove error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean playerInGroup(String world, UUID playerId, String group) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionUser user = pm.getUser(playerId);
            return group.equalsIgnoreCase(user.getGroup());
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: playerInGroup error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean playerAddGroup(String world, UUID playerId, String group) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionUser user = pm.getUser(playerId);
            user.setGroup(group);
            PermissionStorage.save(pm);
            return true;
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: playerAddGroup error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean playerRemoveGroup(String world, UUID playerId, String group) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return false;
         } else {
            PermissionUser user = pm.getUser(playerId);
            if (group.equalsIgnoreCase(user.getGroup())) {
               user.setGroup(pm.getDefaultGroup());
               PermissionStorage.save(pm);
            }

            return true;
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: playerRemoveGroup error: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public String[] getPlayerGroups(String world, UUID playerId) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return new String[0];
         } else {
            PermissionUser user = pm.getUser(playerId);
            String g = user.getGroup();
            return g != null ? new String[]{g} : new String[0];
         }
      } catch (Exception var6) {
         LOGGER.error("VaultPermission: getPlayerGroups error: {}", var6.getMessage());
         return new String[0];
      }
   }

   @Override
   public String getPrimaryGroup(String world, UUID playerId) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         return pm == null ? null : pm.getUser(playerId).getGroup();
      } catch (Exception var4) {
         LOGGER.error("VaultPermission: getPrimaryGroup error: {}", var4.getMessage());
         return null;
      }
   }

   @Override
   public String[] getGroups() {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         return pm == null ? new String[0] : pm.getGroups().stream().map(PermissionGroup::getName).toArray(String[]::new);
      } catch (Exception var2) {
         LOGGER.error("VaultPermission: getGroups error: {}", var2.getMessage());
         return new String[0];
      }
   }
}
