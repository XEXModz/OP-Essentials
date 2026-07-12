package com.zerog.neoessentials.webdashboard.auth;

public class AuthResult {
   private final boolean success;
   private final String token;
   private final String refreshToken;
   private final String message;
   private final AuthSession session;

   private AuthResult(boolean success, String token, String refreshToken, String message, AuthSession session) {
      this.success = success;
      this.token = token;
      this.refreshToken = refreshToken;
      this.message = message;
      this.session = session;
   }

   public static AuthResult success(String token, String refreshToken, AuthSession session) {
      return new AuthResult(true, token, refreshToken, "Authentication successful", session);
   }

   public static AuthResult failure(String message) {
      return new AuthResult(false, null, null, message, null);
   }

   public boolean isSuccess() {
      return this.success;
   }

   public String getToken() {
      return this.token;
   }

   public String getRefreshToken() {
      return this.refreshToken;
   }

   public String getMessage() {
      return this.message;
   }

   public AuthSession getSession() {
      return this.session;
   }
}
