package com.zerog.neoessentials.permissions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionGroup {
   private final String name;
   private final Set<String> permissions;
   private final Set<String> inherits;
   private String prefix = "";
   private String suffix = "";

   public PermissionGroup(String name) {
      this.name = name;
      this.permissions = ConcurrentHashMap.newKeySet();
      this.inherits = ConcurrentHashMap.newKeySet();
   }

   public String getName() {
      return this.name;
   }

   public Set<String> getPermissions() {
      return this.permissions;
   }

   public Set<String> getInherits() {
      return this.inherits;
   }

   public String getPrefix() {
      return this.prefix;
   }

   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   public String getSuffix() {
      return this.suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   public void addPermission(String permission) {
      this.permissions.add(permission.toLowerCase());
   }

   public void removePermission(String permission) {
      this.permissions.remove(permission.toLowerCase());
   }

   public void addInheritance(String groupName) {
      this.inherits.add(groupName);
   }

   public void removeInheritance(String groupName) {
      this.inherits.remove(groupName);
   }
}
