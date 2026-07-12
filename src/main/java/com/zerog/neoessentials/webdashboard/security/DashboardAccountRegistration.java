package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.JsonObject;
import java.util.UUID;

public class DashboardAccountRegistration {
   private final UUID minecraftUuid;
   private final String minecraftUsername;
   private final String dashboardUsername;
   private final String passwordHash;
   private final long registeredAt;
   private String discordId;
   private String discordUsername;
   private long discordLinkedAt;

   public DashboardAccountRegistration(UUID minecraftUuid, String minecraftUsername, String dashboardUsername, String passwordHash, long registeredAt) {
      this.minecraftUuid = minecraftUuid;
      this.minecraftUsername = minecraftUsername;
      this.dashboardUsername = dashboardUsername;
      this.passwordHash = passwordHash;
      this.registeredAt = registeredAt;
   }

   public UUID getMinecraftUuid() {
      return this.minecraftUuid;
   }

   public String getMinecraftUsername() {
      return this.minecraftUsername;
   }

   public String getDashboardUsername() {
      return this.dashboardUsername;
   }

   public String getPasswordHash() {
      return this.passwordHash;
   }

   public long getRegisteredAt() {
      return this.registeredAt;
   }

   public String getDiscordId() {
      return this.discordId;
   }

   public String getDiscordUsername() {
      return this.discordUsername;
   }

   public long getDiscordLinkedAt() {
      return this.discordLinkedAt;
   }

   public boolean isDiscordLinked() {
      return this.discordId != null && !this.discordId.isEmpty();
   }

   public void setDiscordId(String discordId) {
      this.discordId = discordId;
   }

   public void setDiscordUsername(String discordUsername) {
      this.discordUsername = discordUsername;
   }

   public void setDiscordLinkedAt(long discordLinkedAt) {
      this.discordLinkedAt = discordLinkedAt;
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("minecraftUuid", this.minecraftUuid.toString());
      json.addProperty("minecraftUsername", this.minecraftUsername);
      json.addProperty("dashboardUsername", this.dashboardUsername);
      json.addProperty("passwordHash", this.passwordHash);
      json.addProperty("registeredAt", this.registeredAt);
      if (this.discordId != null) {
         json.addProperty("discordId", this.discordId);
         json.addProperty("discordUsername", this.discordUsername);
         json.addProperty("discordLinkedAt", this.discordLinkedAt);
      }

      return json;
   }

   public static DashboardAccountRegistration fromJson(JsonObject json) {
      try {
         UUID minecraftUuid = UUID.fromString(json.get("minecraftUuid").getAsString());
         String minecraftUsername = json.get("minecraftUsername").getAsString();
         String dashboardUsername = json.get("dashboardUsername").getAsString();
         String passwordHash = json.get("passwordHash").getAsString();
         long registeredAt = json.get("registeredAt").getAsLong();
         DashboardAccountRegistration reg = new DashboardAccountRegistration(minecraftUuid, minecraftUsername, dashboardUsername, passwordHash, registeredAt);
         if (json.has("discordId")) {
            reg.setDiscordId(json.get("discordId").getAsString());
            reg.setDiscordUsername(json.has("discordUsername") ? json.get("discordUsername").getAsString() : null);
            reg.setDiscordLinkedAt(json.has("discordLinkedAt") ? json.get("discordLinkedAt").getAsLong() : 0L);
         }

         return reg;
      } catch (Exception var8) {
         return null;
      }
   }
}
