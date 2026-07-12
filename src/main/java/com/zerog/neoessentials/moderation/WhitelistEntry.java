package com.zerog.neoessentials.moderation;

import java.time.Instant;
import java.util.UUID;

public class WhitelistEntry {
   private String id = UUID.randomUUID().toString();
   private WhitelistEntry.WhitelistType type;
   private String target;
   private String playerName;
   private String addedBy;
   private Instant addedAt = Instant.now();
   private String notes;

   public String getId() {
      return this.id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public WhitelistEntry.WhitelistType getType() {
      return this.type;
   }

   public void setType(WhitelistEntry.WhitelistType type) {
      this.type = type;
   }

   public String getTarget() {
      return this.target;
   }

   public void setTarget(String target) {
      this.target = target;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public void setPlayerName(String playerName) {
      this.playerName = playerName;
   }

   public String getAddedBy() {
      return this.addedBy;
   }

   public void setAddedBy(String addedBy) {
      this.addedBy = addedBy;
   }

   public Instant getAddedAt() {
      return this.addedAt;
   }

   public void setAddedAt(Instant addedAt) {
      this.addedAt = addedAt;
   }

   public String getNotes() {
      return this.notes;
   }

   public void setNotes(String notes) {
      this.notes = notes;
   }

   public static enum WhitelistType {
      UUID,
      IP;
   }
}
