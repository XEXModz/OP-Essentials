package com.zerog.neoessentials.moderation;

import java.time.Instant;
import java.util.UUID;

public class BanEntry {
   private String id = UUID.randomUUID().toString();
   private BanEntry.BanType type;
   private String target;
   private String playerName;
   private String reason;
   private String evidence;
   private Instant bannedAt = Instant.now();
   private Instant expiresAt;
   private String bannedBy;
   private boolean isActive = true;
   private BanEntry.BanAppeal appeal;

   public String getId() {
      return this.id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public BanEntry.BanType getType() {
      return this.type;
   }

   public void setType(BanEntry.BanType type) {
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

   public String getReason() {
      return this.reason;
   }

   public void setReason(String reason) {
      this.reason = reason;
   }

   public String getEvidence() {
      return this.evidence;
   }

   public void setEvidence(String evidence) {
      this.evidence = evidence;
   }

   public Instant getBannedAt() {
      return this.bannedAt;
   }

   public void setBannedAt(Instant bannedAt) {
      this.bannedAt = bannedAt;
   }

   public Instant getExpiresAt() {
      return this.expiresAt;
   }

   public void setExpiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
   }

   public String getBannedBy() {
      return this.bannedBy;
   }

   public void setBannedBy(String bannedBy) {
      this.bannedBy = bannedBy;
   }

   public boolean isActive() {
      return this.isActive;
   }

   public void setActive(boolean active) {
      this.isActive = active;
   }

   public BanEntry.BanAppeal getAppeal() {
      return this.appeal;
   }

   public void setAppeal(BanEntry.BanAppeal appeal) {
      this.appeal = appeal;
   }

   public boolean isPermanent() {
      return this.expiresAt == null;
   }

   public boolean isExpired() {
      return !this.isPermanent() && Instant.now().isAfter(this.expiresAt);
   }

   public boolean hasAppeal() {
      return this.appeal != null;
   }

   public boolean isAppealPending() {
      return this.hasAppeal() && this.appeal.getStatus() == BanEntry.BanAppeal.AppealStatus.PENDING;
   }

   public static class BanAppeal {
      private String appealText;
      private Instant appealedAt = Instant.now();
      private BanEntry.BanAppeal.AppealStatus status = BanEntry.BanAppeal.AppealStatus.PENDING;
      private String reviewedBy;
      private Instant reviewedAt;
      private String reviewNotes;

      public String getAppealText() {
         return this.appealText;
      }

      public void setAppealText(String appealText) {
         this.appealText = appealText;
      }

      public Instant getAppealedAt() {
         return this.appealedAt;
      }

      public void setAppealedAt(Instant appealedAt) {
         this.appealedAt = appealedAt;
      }

      public BanEntry.BanAppeal.AppealStatus getStatus() {
         return this.status;
      }

      public void setStatus(BanEntry.BanAppeal.AppealStatus status) {
         this.status = status;
      }

      public String getReviewedBy() {
         return this.reviewedBy;
      }

      public void setReviewedBy(String reviewedBy) {
         this.reviewedBy = reviewedBy;
      }

      public Instant getReviewedAt() {
         return this.reviewedAt;
      }

      public void setReviewedAt(Instant reviewedAt) {
         this.reviewedAt = reviewedAt;
      }

      public String getReviewNotes() {
         return this.reviewNotes;
      }

      public void setReviewNotes(String reviewNotes) {
         this.reviewNotes = reviewNotes;
      }

      public static enum AppealStatus {
         PENDING,
         APPROVED,
         DENIED;
      }
   }

   public static enum BanType {
      UUID,
      IP,
      BOTH;
   }
}
