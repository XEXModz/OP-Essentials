package com.zerog.neoessentials.webdashboard.auth;

public class AuthSession {
   private final String sessionId;
   private final User user;
   private final String token;
   private final long createdAt;
   private final long expiresAt;
   private final String ipAddress;

   public AuthSession(String sessionId, User user, String token, long expiresAt, String ipAddress) {
      this.sessionId = sessionId;
      this.user = user;
      this.token = token;
      this.createdAt = System.currentTimeMillis();
      this.expiresAt = expiresAt;
      this.ipAddress = ipAddress;
   }

   public boolean isExpired(long currentTime) {
      return currentTime > this.expiresAt;
   }

   public boolean hasPermission(String permission) {
      return this.user.hasPermission(permission);
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public User getUser() {
      return this.user;
   }

   public String getToken() {
      return this.token;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public long getExpiresAt() {
      return this.expiresAt;
   }

   public String getIpAddress() {
      return this.ipAddress;
   }
}
