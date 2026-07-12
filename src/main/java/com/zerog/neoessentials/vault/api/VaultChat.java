package com.zerog.neoessentials.vault.api;

import java.util.UUID;

public abstract class VaultChat {
   public abstract String getName();

   public abstract boolean isEnabled();

   public abstract String getPlayerPrefix(String var1, UUID var2);

   public String getPlayerPrefix(UUID playerId) {
      return this.getPlayerPrefix(null, playerId);
   }

   public abstract void setPlayerPrefix(String var1, UUID var2, String var3);

   public void setPlayerPrefix(UUID playerId, String prefix) {
      this.setPlayerPrefix(null, playerId, prefix);
   }

   public abstract String getPlayerSuffix(String var1, UUID var2);

   public String getPlayerSuffix(UUID playerId) {
      return this.getPlayerSuffix(null, playerId);
   }

   public abstract void setPlayerSuffix(String var1, UUID var2, String var3);

   public void setPlayerSuffix(UUID playerId, String suffix) {
      this.setPlayerSuffix(null, playerId, suffix);
   }

   public String getPlayerInfoString(String world, UUID playerId, String node) {
      return "";
   }

   public void setPlayerInfoString(String world, UUID playerId, String node, String value) {
   }

   public int getPlayerInfoInteger(String world, UUID playerId, String node, int defaultValue) {
      return defaultValue;
   }

   public void setPlayerInfoInteger(String world, UUID playerId, String node, int value) {
   }

   public double getPlayerInfoDouble(String world, UUID playerId, String node, double defaultValue) {
      return defaultValue;
   }

   public void setPlayerInfoDouble(String world, UUID playerId, String node, double value) {
   }

   public boolean getPlayerInfoBoolean(String world, UUID playerId, String node, boolean defaultValue) {
      return defaultValue;
   }

   public void setPlayerInfoBoolean(String world, UUID playerId, String node, boolean value) {
   }

   public abstract String getGroupPrefix(String var1, String var2);

   public String getGroupPrefix(String group) {
      return this.getGroupPrefix(null, group);
   }

   public abstract void setGroupPrefix(String var1, String var2, String var3);

   public void setGroupPrefix(String group, String prefix) {
      this.setGroupPrefix(null, group, prefix);
   }

   public abstract String getGroupSuffix(String var1, String var2);

   public String getGroupSuffix(String group) {
      return this.getGroupSuffix(null, group);
   }

   public abstract void setGroupSuffix(String var1, String var2, String var3);

   public void setGroupSuffix(String group, String suffix) {
      this.setGroupSuffix(null, group, suffix);
   }

   public String getGroupInfoString(String world, String group, String node) {
      return "";
   }

   public void setGroupInfoString(String world, String group, String node, String value) {
   }

   public int getGroupInfoInteger(String world, String group, String node, int defaultValue) {
      return defaultValue;
   }

   public void setGroupInfoInteger(String world, String group, String node, int value) {
   }

   public double getGroupInfoDouble(String world, String group, String node, double defaultValue) {
      return defaultValue;
   }

   public void setGroupInfoDouble(String world, String group, String node, double value) {
   }

   public boolean getGroupInfoBoolean(String world, String group, String node, boolean defaultValue) {
      return defaultValue;
   }

   public void setGroupInfoBoolean(String world, String group, String node, boolean value) {
   }
}
