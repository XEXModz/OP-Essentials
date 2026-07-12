package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.JsonObject;
import java.util.UUID;

public class Session {
   private final String sessionId = UUID.randomUUID().toString();
   private final String userId;
   private final String username;
   private final User.Role role;
   private final String ipAddress;
   private final String userAgent;
   private final long createdAt;
   private long lastAccessTime;
   private boolean requiresPasswordChange;
   private boolean valid;
   private static final long SESSION_TIMEOUT_MS = 1800000L;

   public Session(String userId, String username, User.Role role, String ipAddress, String userAgent) {
      this.userId = userId;
      this.username = username;
      this.role = role;
      this.ipAddress = ipAddress;
      this.userAgent = userAgent;
      this.createdAt = System.currentTimeMillis();
      this.lastAccessTime = this.createdAt;
      this.requiresPasswordChange = false;
      this.valid = true;
   }

   public boolean isValid() {
      if (!this.valid) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastAccessTime > 1800000L) {
            this.valid = false;
            return false;
         } else {
            return true;
         }
      }
   }

   public boolean isActive() {
      return this.isValid();
   }

   public void updateAccessTime() {
      this.lastAccessTime = System.currentTimeMillis();
   }

   public void invalidate() {
      this.valid = false;
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("sessionId", this.sessionId);
      json.addProperty("userId", this.userId);
      json.addProperty("username", this.username);
      json.addProperty("role", this.role.name());
      json.addProperty("ipAddress", this.ipAddress);
      json.addProperty("userAgent", this.userAgent);
      json.addProperty("createdAt", this.createdAt);
      json.addProperty("lastAccessTime", this.lastAccessTime);
      json.addProperty("requiresPasswordChange", this.requiresPasswordChange);
      json.addProperty("valid", this.valid);
      return json;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public String getUserId() {
      return this.userId;
   }

   public String getUsername() {
      return this.username;
   }

   public User.Role getRole() {
      return this.role;
   }

   public String getIpAddress() {
      return this.ipAddress;
   }

   public String getUserAgent() {
      return this.userAgent;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public long getLastAccessTime() {
      return this.lastAccessTime;
   }

   public boolean requiresPasswordChange() {
      return this.requiresPasswordChange;
   }

   public void setRequiresPasswordChange(boolean requiresPasswordChange) {
      this.requiresPasswordChange = requiresPasswordChange;
   }
}
