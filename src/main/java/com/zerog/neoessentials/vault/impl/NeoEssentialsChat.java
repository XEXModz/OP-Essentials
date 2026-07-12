package com.zerog.neoessentials.vault.impl;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionStorage;
import com.zerog.neoessentials.vault.api.VaultChat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoEssentialsChat extends VaultChat {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentialsChat.class);

   @Override
   public String getName() {
      return "NeoEssentials Chat";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public String getPlayerPrefix(String world, UUID playerId) {
      try {
         return PermissionAPI.getPrefix(playerId);
      } catch (Exception var4) {
         LOGGER.debug("VaultChat: getPlayerPrefix error for {}: {}", playerId, var4.getMessage());
         return "";
      }
   }

   @Override
   public void setPlayerPrefix(String world, UUID playerId, String prefix) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return;
         }

         pm.getUser(playerId).setPrefix(prefix);
         PermissionStorage.save(pm);
      } catch (Exception var5) {
         LOGGER.error("VaultChat: setPlayerPrefix error: {}", var5.getMessage());
      }
   }

   @Override
   public String getPlayerSuffix(String world, UUID playerId) {
      try {
         return PermissionAPI.getSuffix(playerId);
      } catch (Exception var4) {
         LOGGER.debug("VaultChat: getPlayerSuffix error for {}: {}", playerId, var4.getMessage());
         return "";
      }
   }

   @Override
   public void setPlayerSuffix(String world, UUID playerId, String suffix) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return;
         }

         pm.getUser(playerId).setSuffix(suffix);
         PermissionStorage.save(pm);
      } catch (Exception var5) {
         LOGGER.error("VaultChat: setPlayerSuffix error: {}", var5.getMessage());
      }
   }

   @Override
   public String getGroupPrefix(String world, String group) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return "";
         } else {
            PermissionGroup grp = pm.getGroup(group);
            return grp != null && grp.getPrefix() != null ? grp.getPrefix() : "";
         }
      } catch (Exception var5) {
         LOGGER.debug("VaultChat: getGroupPrefix error: {}", var5.getMessage());
         return "";
      }
   }

   @Override
   public void setGroupPrefix(String world, String group, String prefix) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return;
         }

         PermissionGroup grp = pm.getGroup(group);
         if (grp == null) {
            grp = new PermissionGroup(group);
            pm.addGroup(grp);
         }

         grp.setPrefix(prefix);
         PermissionStorage.save(pm);
      } catch (Exception var6) {
         LOGGER.error("VaultChat: setGroupPrefix error: {}", var6.getMessage());
      }
   }

   @Override
   public String getGroupSuffix(String world, String group) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return "";
         } else {
            PermissionGroup grp = pm.getGroup(group);
            return grp != null && grp.getSuffix() != null ? grp.getSuffix() : "";
         }
      } catch (Exception var5) {
         LOGGER.debug("VaultChat: getGroupSuffix error: {}", var5.getMessage());
         return "";
      }
   }

   @Override
   public void setGroupSuffix(String world, String group, String suffix) {
      try {
         PermissionManager pm = PermissionAPI.getManager();
         if (pm == null) {
            return;
         }

         PermissionGroup grp = pm.getGroup(group);
         if (grp == null) {
            grp = new PermissionGroup(group);
            pm.addGroup(grp);
         }

         grp.setSuffix(suffix);
         PermissionStorage.save(pm);
      } catch (Exception var6) {
         LOGGER.error("VaultChat: setGroupSuffix error: {}", var6.getMessage());
      }
   }
}
