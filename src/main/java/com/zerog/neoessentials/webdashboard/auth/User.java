package com.zerog.neoessentials.webdashboard.auth;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class User {
   private final UUID uuid;
   private final String username;
   private String passwordHash;
   private boolean requirePasswordChange;
   private final Set<String> permissions;
   private final Set<String> roles;
   private String discordId;
   private long lastLogin;

   public User(UUID uuid, String username) {
      this.uuid = uuid;
      this.username = username;
      this.permissions = new HashSet<>();
      this.roles = new HashSet<>();
      this.requirePasswordChange = false;
   }

   public boolean hasPermission(String permission) {
      return this.permissions.contains(permission) || this.permissions.contains("dashboard.*");
   }

   public void addPermission(String permission) {
      this.permissions.add(permission);
   }

   public void removePermission(String permission) {
      this.permissions.remove(permission);
   }

   public void addRole(String role) {
      this.roles.add(role);
   }

   public void removeRole(String role) {
      this.roles.remove(role);
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public String getUsername() {
      return this.username;
   }

   public String getPasswordHash() {
      return this.passwordHash;
   }

   public void setPasswordHash(String passwordHash) {
      this.passwordHash = passwordHash;
   }

   public boolean requiresPasswordChange() {
      return this.requirePasswordChange;
   }

   public void setRequirePasswordChange(boolean require) {
      this.requirePasswordChange = require;
   }

   public Set<String> getPermissions() {
      return new HashSet<>(this.permissions);
   }

   public Set<String> getRoles() {
      return new HashSet<>(this.roles);
   }

   public String getDiscordId() {
      return this.discordId;
   }

   public void setDiscordId(String discordId) {
      this.discordId = discordId;
   }

   public long getLastLogin() {
      return this.lastLogin;
   }

   public void setLastLogin(long lastLogin) {
      this.lastLogin = lastLogin;
   }
}
