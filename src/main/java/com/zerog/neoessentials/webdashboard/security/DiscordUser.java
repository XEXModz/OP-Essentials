package com.zerog.neoessentials.webdashboard.security;

import java.util.List;

public class DiscordUser {
   private final String discordId;
   private final String discordUsername;
   private final String minecraftUsername;
   private final String minecraftUuid;
   private final List<String> discordRoles;

   public DiscordUser(String discordId, String discordUsername, String minecraftUsername, String minecraftUuid, List<String> discordRoles) {
      this.discordId = discordId;
      this.discordUsername = discordUsername;
      this.minecraftUsername = minecraftUsername;
      this.minecraftUuid = minecraftUuid;
      this.discordRoles = discordRoles;
   }

   public String getDiscordId() {
      return this.discordId;
   }

   public String getDiscordUsername() {
      return this.discordUsername;
   }

   public String getMinecraftUsername() {
      return this.minecraftUsername;
   }

   public String getMinecraftUuid() {
      return this.minecraftUuid;
   }

   public List<String> getDiscordRoles() {
      return this.discordRoles;
   }

   public boolean isLinked() {
      return this.discordId != null && !this.discordId.isEmpty() && this.minecraftUuid != null && !this.minecraftUuid.isEmpty();
   }
}
