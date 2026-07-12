package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModerationHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(ModerationHandler.class);
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();

      try {
         switch (method) {
            case "GET":
               this.handleGet(exchange, path);
               break;
            case "POST":
               this.handlePost(exchange, path);
               break;
            case "PUT":
               this.handlePut(exchange, path);
               break;
            case "DELETE":
               this.handleDelete(exchange, path);
               break;
            default:
               this.sendErrorResponse(exchange, 405, "Method not allowed");
         }
      } catch (Exception var6) {
         LOGGER.error("Error handling moderation request", var6);
         this.sendErrorResponse(exchange, 500, "Internal server error: " + var6.getMessage());
      }
   }

   private void handleGet(HttpExchange exchange, String path) throws IOException {
      String[] parts = path.split("/");
      if (parts.length == 4 && parts[3].equals("bans")) {
         this.handleListBans(exchange);
      } else if (parts.length == 5 && parts[3].equals("bans") && parts[4].equals("active")) {
         this.handleListActiveBans(exchange);
      } else if (parts.length == 5 && parts[3].equals("bans")) {
         String banId = parts[4];
         this.handleGetBan(exchange, banId);
      } else if (parts.length == 6 && parts[3].equals("bans") && parts[4].equals("history")) {
         String target = parts[5];
         this.handleGetBanHistory(exchange, target);
      } else if (parts.length == 4 && parts[3].equals("whitelist")) {
         this.handleListWhitelist(exchange);
      } else if (parts.length == 5 && parts[3].equals("whitelist") && parts[4].equals("status")) {
         this.handleGetWhitelistStatus(exchange);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handlePost(HttpExchange exchange, String path) throws IOException {
      String[] parts = path.split("/");
      if (parts.length == 4 && parts[3].equals("bans")) {
         this.handleAddBan(exchange);
      } else if (parts.length == 5 && parts[3].equals("bans") && parts[4].equals("check")) {
         this.handleCheckBan(exchange);
      } else if (parts.length == 6 && parts[3].equals("bans") && parts[5].equals("appeal")) {
         String banId = parts[4];
         this.handleSubmitAppeal(exchange, banId);
      } else if (parts.length == 6 && parts[3].equals("bans") && parts[5].equals("review")) {
         String banId = parts[4];
         this.handleReviewAppeal(exchange, banId);
      } else if (parts.length == 4 && parts[3].equals("whitelist")) {
         this.handleAddWhitelist(exchange);
      } else if (parts.length == 5 && parts[3].equals("whitelist") && parts[4].equals("import")) {
         this.handleImportWhitelist(exchange);
      } else if (parts.length == 5 && parts[3].equals("whitelist") && parts[4].equals("toggle")) {
         this.handleToggleWhitelist(exchange);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handlePut(HttpExchange exchange, String path) throws IOException {
      this.sendErrorResponse(exchange, 404, "Endpoint not found");
   }

   private void handleDelete(HttpExchange exchange, String path) throws IOException {
      String[] parts = path.split("/");
      if (parts.length == 5 && parts[3].equals("bans")) {
         String banId = parts[4];
         this.handleRemoveBan(exchange, banId);
      } else if (parts.length == 5 && parts[3].equals("whitelist")) {
         String entryId = parts[4];
         this.handleRemoveWhitelist(exchange, entryId);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handleListBans(HttpExchange exchange) throws IOException {
      Collection<BanEntry> bans = ModerationManager.getInstance().getAllBans();
      JsonObject response = new JsonObject();
      response.addProperty("banCount", bans.size());
      JsonArray bansArray = new JsonArray();

      for (BanEntry ban : bans) {
         bansArray.add(this.banToJson(ban));
      }

      response.add("bans", bansArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleListActiveBans(HttpExchange exchange) throws IOException {
      Collection<BanEntry> bans = ModerationManager.getInstance().getActiveBans();
      JsonObject response = new JsonObject();
      response.addProperty("banCount", bans.size());
      JsonArray bansArray = new JsonArray();

      for (BanEntry ban : bans) {
         bansArray.add(this.banToJson(ban));
      }

      response.add("bans", bansArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleGetBan(HttpExchange exchange, String banId) throws IOException {
      BanEntry ban = ModerationManager.getInstance().getBan(banId);
      if (ban == null) {
         this.sendErrorResponse(exchange, 404, "Ban not found");
      } else {
         JsonObject response = this.banToJson(ban);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleGetBanHistory(HttpExchange exchange, String target) throws IOException {
      List<BanEntry> history = ModerationManager.getInstance().getBanHistory(target);
      JsonObject response = new JsonObject();
      response.addProperty("target", target);
      response.addProperty("banCount", history.size());
      JsonArray historyArray = new JsonArray();

      for (BanEntry ban : history) {
         historyArray.add(this.banToJson(ban));
      }

      response.add("history", historyArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleAddBan(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (data.has("type") && data.has("target") && data.has("reason")) {
         BanEntry.BanType type = BanEntry.BanType.valueOf(data.get("type").getAsString());
         String target = data.get("target").getAsString();
         String playerName = data.has("playerName") ? data.get("playerName").getAsString() : null;
         String reason = data.get("reason").getAsString();
         String evidence = data.has("evidence") ? data.get("evidence").getAsString() : null;
         Instant expiresAt = data.has("expiresAt") ? Instant.parse(data.get("expiresAt").getAsString()) : null;
         String bannedBy = this.getUsernameFromSession(exchange);
         InputValidator.ValidationResult reasonResult = InputValidator.validateReason(reason);
         if (!reasonResult.isValid()) {
            this.sendErrorResponse(exchange, 400, "Invalid reason: " + reasonResult.getErrorMessage());
         } else {
            reason = (String)reasonResult.getValue();
            BanEntry ban = ModerationManager.getInstance().addBan(type, target, playerName, reason, evidence, expiresAt, bannedBy);
            JsonObject response = this.banToJson(ban);
            this.sendJsonResponse(exchange, 201, response);
         }
      } else {
         this.sendErrorResponse(exchange, 400, "Missing required fields: type, target, reason");
      }
   }

   private void handleRemoveBan(HttpExchange exchange, String banId) throws IOException {
      boolean removed = ModerationManager.getInstance().removeBan(banId);
      if (!removed) {
         this.sendErrorResponse(exchange, 404, "Ban not found");
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Ban removed");
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleCheckBan(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      String uuid = data.has("uuid") ? data.get("uuid").getAsString() : null;
      String ip = data.has("ip") ? data.get("ip").getAsString() : null;
      BanEntry ban = ModerationManager.getInstance().checkBan(uuid, ip);
      JsonObject response = new JsonObject();
      response.addProperty("isBanned", ban != null);
      if (ban != null) {
         response.add("ban", this.banToJson(ban));
      }

      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleSubmitAppeal(HttpExchange exchange, String banId) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (!data.has("appealText")) {
         this.sendErrorResponse(exchange, 400, "Missing required field: appealText");
      } else {
         String appealText = data.get("appealText").getAsString();
         boolean success = ModerationManager.getInstance().submitAppeal(banId, appealText);
         if (!success) {
            this.sendErrorResponse(exchange, 404, "Ban not found or already inactive");
         } else {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Appeal submitted");
            this.sendJsonResponse(exchange, 200, response);
         }
      }
   }

   private void handleReviewAppeal(HttpExchange exchange, String banId) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (data.has("status") && data.has("reviewNotes")) {
         BanEntry.BanAppeal.AppealStatus status = BanEntry.BanAppeal.AppealStatus.valueOf(data.get("status").getAsString());
         String reviewNotes = data.get("reviewNotes").getAsString();
         String reviewedBy = this.getUsernameFromSession(exchange);
         boolean success = ModerationManager.getInstance().reviewAppeal(banId, status, reviewedBy, reviewNotes);
         if (!success) {
            this.sendErrorResponse(exchange, 404, "Ban not found or no appeal submitted");
         } else {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Appeal reviewed");
            this.sendJsonResponse(exchange, 200, response);
         }
      } else {
         this.sendErrorResponse(exchange, 400, "Missing required fields: status, reviewNotes");
      }
   }

   private void handleListWhitelist(HttpExchange exchange) throws IOException {
      Collection<WhitelistEntry> entries = ModerationManager.getInstance().getAllWhitelist();
      JsonObject response = new JsonObject();
      response.addProperty("entryCount", entries.size());
      response.addProperty("whitelistEnabled", ModerationManager.getInstance().isWhitelistEnabled());
      JsonArray entriesArray = new JsonArray();

      for (WhitelistEntry entry : entries) {
         entriesArray.add(this.whitelistToJson(entry));
      }

      response.add("entries", entriesArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleGetWhitelistStatus(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("enabled", ModerationManager.getInstance().isWhitelistEnabled());
      response.addProperty("entryCount", ModerationManager.getInstance().getAllWhitelist().size());
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleAddWhitelist(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (data.has("type") && data.has("target")) {
         WhitelistEntry.WhitelistType type = WhitelistEntry.WhitelistType.valueOf(data.get("type").getAsString());
         String target = data.get("target").getAsString();
         String playerName = data.has("playerName") ? data.get("playerName").getAsString() : null;
         String notes = data.has("notes") ? data.get("notes").getAsString() : null;
         String addedBy = this.getUsernameFromSession(exchange);
         WhitelistEntry entry = ModerationManager.getInstance().addWhitelist(type, target, playerName, addedBy, notes);
         JsonObject response = this.whitelistToJson(entry);
         this.sendJsonResponse(exchange, 201, response);
      } else {
         this.sendErrorResponse(exchange, 400, "Missing required fields: type, target");
      }
   }

   private void handleRemoveWhitelist(HttpExchange exchange, String entryId) throws IOException {
      boolean removed = ModerationManager.getInstance().removeWhitelist(entryId);
      if (!removed) {
         this.sendErrorResponse(exchange, 404, "Whitelist entry not found");
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Whitelist entry removed");
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleImportWhitelist(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (!data.has("entries")) {
         this.sendErrorResponse(exchange, 400, "Missing required field: entries");
      } else {
         List<WhitelistEntry> entries = new ArrayList<>();
         JsonArray entriesArray = data.getAsJsonArray("entries");

         for (int i = 0; i < entriesArray.size(); i++) {
            JsonObject entryObj = entriesArray.get(i).getAsJsonObject();
            WhitelistEntry entry = new WhitelistEntry();
            entry.setType(WhitelistEntry.WhitelistType.valueOf(entryObj.get("type").getAsString()));
            entry.setTarget(entryObj.get("target").getAsString());
            if (entryObj.has("playerName")) {
               entry.setPlayerName(entryObj.get("playerName").getAsString());
            }

            if (entryObj.has("notes")) {
               entry.setNotes(entryObj.get("notes").getAsString());
            }

            entries.add(entry);
         }

         String importedBy = this.getUsernameFromSession(exchange);
         int imported = ModerationManager.getInstance().importWhitelist(entries, importedBy);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("imported", imported);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleToggleWhitelist(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JsonObject data = (JsonObject)this.gson.fromJson(body, JsonObject.class);
      if (!data.has("enabled")) {
         this.sendErrorResponse(exchange, 400, "Missing required field: enabled");
      } else {
         boolean enabled = data.get("enabled").getAsBoolean();
         ModerationManager.getInstance().setWhitelistEnabled(enabled);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("enabled", enabled);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private JsonObject banToJson(BanEntry ban) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", ban.getId());
      obj.addProperty("type", ban.getType().name());
      obj.addProperty("target", ban.getTarget());
      obj.addProperty("playerName", ban.getPlayerName());
      obj.addProperty("reason", ban.getReason());
      obj.addProperty("evidence", ban.getEvidence());
      obj.addProperty("bannedAt", ban.getBannedAt() != null ? ban.getBannedAt().toString() : null);
      obj.addProperty("expiresAt", ban.getExpiresAt() != null ? ban.getExpiresAt().toString() : null);
      obj.addProperty("bannedBy", ban.getBannedBy());
      obj.addProperty("isActive", ban.isActive());
      obj.addProperty("isPermanent", ban.isPermanent());
      obj.addProperty("isExpired", ban.isExpired());
      if (ban.hasAppeal()) {
         JsonObject appealObj = new JsonObject();
         BanEntry.BanAppeal appeal = ban.getAppeal();
         appealObj.addProperty("appealText", appeal.getAppealText());
         appealObj.addProperty("appealedAt", appeal.getAppealedAt() != null ? appeal.getAppealedAt().toString() : null);
         appealObj.addProperty("status", appeal.getStatus().name());
         appealObj.addProperty("reviewedBy", appeal.getReviewedBy());
         appealObj.addProperty("reviewedAt", appeal.getReviewedAt() != null ? appeal.getReviewedAt().toString() : null);
         appealObj.addProperty("reviewNotes", appeal.getReviewNotes());
         obj.add("appeal", appealObj);
      }

      return obj;
   }

   private JsonObject whitelistToJson(WhitelistEntry entry) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", entry.getId());
      obj.addProperty("type", entry.getType().name());
      obj.addProperty("target", entry.getTarget());
      obj.addProperty("playerName", entry.getPlayerName());
      obj.addProperty("addedBy", entry.getAddedBy());
      obj.addProperty("addedAt", entry.getAddedAt() != null ? entry.getAddedAt().toString() : null);
      obj.addProperty("notes", entry.getNotes());
      return obj;
   }

   private String getUsernameFromSession(HttpExchange exchange) {
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      return cookie != null && cookie.contains("sessionId=") ? "admin" : "system";
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject data) throws IOException {
      String response = this.gson.toJson(data);
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(statusCode, (long)bytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }

   private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("timestamp", System.currentTimeMillis());
      this.sendJsonResponse(exchange, statusCode, error);
   }
}
