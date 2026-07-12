package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class User {
   private final String id;
   private final String username;
   private String passwordHash;
   private String email;
   private User.Role role;
   private boolean enabled;
   private final long createdAt;
   private long lastLoginAt;
   private String lastLoginIp;
   private int failedLoginAttempts;
   private long lockoutUntil;
   private boolean requiresPasswordChange;
   private boolean isTempPassword;
   private final Set<String> permissions;

   public User(String username, String passwordHash) {
      this.id = UUID.randomUUID().toString();
      this.username = username;
      this.passwordHash = passwordHash;
      this.role = User.Role.VIEWER;
      this.enabled = true;
      this.createdAt = System.currentTimeMillis();
      this.permissions = new HashSet<>();
      this.failedLoginAttempts = 0;
      this.lockoutUntil = 0L;
      this.requiresPasswordChange = false;
      this.isTempPassword = false;
   }

   public User(String id, String username, String passwordHash, String email, User.Role role, boolean enabled, long createdAt, Set<String> permissions) {
      this.id = id;
      this.username = username;
      this.passwordHash = passwordHash;
      this.email = email;
      this.role = role;
      this.enabled = enabled;
      this.createdAt = createdAt;
      this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
      this.failedLoginAttempts = 0;
      this.lockoutUntil = 0L;
      this.requiresPasswordChange = false;
      this.isTempPassword = false;
   }

   public boolean isLockedOut() {
      return System.currentTimeMillis() < this.lockoutUntil;
   }

   public boolean hasPermission(String permission) {
      return this.role == User.Role.ADMIN ? true : this.permissions.contains(permission) || this.permissions.contains("*");
   }

   public void addPermission(String permission) {
      this.permissions.add(permission);
   }

   public void removePermission(String permission) {
      this.permissions.remove(permission);
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("id", this.id);
      json.addProperty("username", this.username);
      json.addProperty("email", this.email);
      json.addProperty("role", this.role.name());
      json.addProperty("enabled", this.enabled);
      json.addProperty("createdAt", this.createdAt);
      json.addProperty("lastLoginAt", this.lastLoginAt);
      json.addProperty("lastLoginIp", this.lastLoginIp);
      json.addProperty("failedLoginAttempts", this.failedLoginAttempts);
      json.addProperty("lockoutUntil", this.lockoutUntil);
      json.addProperty("requiresPasswordChange", this.requiresPasswordChange);
      json.addProperty("isTempPassword", this.isTempPassword);
      JsonArray permsArray = new JsonArray();

      for (String perm : this.permissions) {
         permsArray.add(perm);
      }

      json.add("permissions", permsArray);
      return json;
   }

   public String getId() {
      return this.id;
   }

   public String getUsername() {
      return this.username;
   }

   public String getPasswordHash() {
      return this.passwordHash;
   }

   public String getEmail() {
      return this.email;
   }

   public User.Role getRole() {
      return this.role;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public long getLastLoginAt() {
      return this.lastLoginAt;
   }

   public String getLastLoginIp() {
      return this.lastLoginIp;
   }

   public int getFailedLoginAttempts() {
      return this.failedLoginAttempts;
   }

   public long getLockoutUntil() {
      return this.lockoutUntil;
   }

   public boolean requiresPasswordChange() {
      return this.requiresPasswordChange;
   }

   public boolean isTempPassword() {
      return this.isTempPassword;
   }

   public Set<String> getPermissions() {
      return new HashSet<>(this.permissions);
   }

   public void setPasswordHash(String passwordHash) {
      this.passwordHash = passwordHash;
   }

   public void setEmail(String email) {
      this.email = email;
   }

   public void setRole(User.Role role) {
      this.role = role;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setLastLoginAt(long lastLoginAt) {
      this.lastLoginAt = lastLoginAt;
   }

   public void setLastLoginIp(String lastLoginIp) {
      this.lastLoginIp = lastLoginIp;
   }

   public void setFailedLoginAttempts(int failedLoginAttempts) {
      this.failedLoginAttempts = failedLoginAttempts;
   }

   public void setLockoutUntil(long lockoutUntil) {
      this.lockoutUntil = lockoutUntil;
   }

   public void setRequiresPasswordChange(boolean requiresPasswordChange) {
      this.requiresPasswordChange = requiresPasswordChange;
   }

   public void setTempPassword(boolean isTempPassword) {
      this.isTempPassword = isTempPassword;
   }

   public static enum Role {
      ADMIN,
      OPERATOR,
      MODERATOR,
      VIEWER;
   }
}
