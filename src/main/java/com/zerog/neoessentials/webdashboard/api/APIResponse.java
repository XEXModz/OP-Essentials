package com.zerog.neoessentials.webdashboard.api;

import com.google.gson.JsonObject;

public class APIResponse {
   private final boolean success;
   private final String message;
   private final JsonObject data;
   private final int statusCode;
   private final long timestamp;

   private APIResponse(boolean success, String message, JsonObject data, int statusCode) {
      this.success = success;
      this.message = message;
      this.data = data;
      this.statusCode = statusCode;
      this.timestamp = System.currentTimeMillis();
   }

   public static APIResponse success(JsonObject data) {
      return new APIResponse(true, "Success", data, 200);
   }

   public static APIResponse success(String message, JsonObject data) {
      return new APIResponse(true, message, data, 200);
   }

   public static APIResponse error(String message, int statusCode) {
      return new APIResponse(false, message, null, statusCode);
   }

   public static APIResponse badRequest(String message) {
      return error(message, 400);
   }

   public static APIResponse unauthorized(String message) {
      return error(message, 401);
   }

   public static APIResponse forbidden(String message) {
      return error(message, 403);
   }

   public static APIResponse notFound(String message) {
      return error(message, 404);
   }

   public static APIResponse serverError(String message) {
      return error(message, 500);
   }

   public String toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("success", this.success);
      json.addProperty("message", this.message);
      json.addProperty("timestamp", this.timestamp);
      json.addProperty("statusCode", this.statusCode);
      if (this.data != null) {
         json.add("data", this.data);
      }

      return json.toString();
   }

   public boolean isSuccess() {
      return this.success;
   }

   public String getMessage() {
      return this.message;
   }

   public JsonObject getData() {
      return this.data;
   }

   public int getStatusCode() {
      return this.statusCode;
   }

   public long getTimestamp() {
      return this.timestamp;
   }
}
