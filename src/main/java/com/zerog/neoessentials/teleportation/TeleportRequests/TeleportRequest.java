package com.zerog.neoessentials.teleportation.TeleportRequests;

import java.util.Objects;
import java.util.UUID;

public class TeleportRequest {
   private final UUID requesterId;
   private final String requesterName;
   private final UUID targetId;
   private final String targetName;
   private final TeleportRequestType type;
   private final long expiryTime;
   private final long createdTime;

   public TeleportRequest(UUID requesterId, String requesterName, UUID targetId, String targetName, TeleportRequestType type, long expiryTime) {
      this.requesterId = requesterId;
      this.requesterName = requesterName;
      this.targetId = targetId;
      this.targetName = targetName;
      this.type = type;
      this.expiryTime = expiryTime;
      this.createdTime = System.currentTimeMillis();
   }

   public UUID getRequesterId() {
      return this.requesterId;
   }

   public String getRequesterName() {
      return this.requesterName;
   }

   public UUID getTargetId() {
      return this.targetId;
   }

   public String getTargetName() {
      return this.targetName;
   }

   public TeleportRequestType getType() {
      return this.type;
   }

   public long getExpiryTime() {
      return this.expiryTime;
   }

   public long getCreatedTime() {
      return this.createdTime;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() > this.expiryTime;
   }

   public long getRemainingTimeSeconds() {
      long remaining = this.expiryTime - System.currentTimeMillis();
      return Math.max(0L, remaining / 1000L);
   }

   public long getAgeSeconds() {
      return (System.currentTimeMillis() - this.createdTime) / 1000L;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         TeleportRequest that = (TeleportRequest)obj;
         return Objects.equals(this.requesterId, that.requesterId)
            && Objects.equals(this.targetId, that.targetId)
            && this.type == that.type
            && this.createdTime == that.createdTime;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.requesterId, this.targetId, this.type, this.createdTime);
   }

   @Override
   public String toString() {
      return String.format(
         "TeleportRequest{requester=%s, target=%s, type=%s, age=%ds, remaining=%ds}",
         this.requesterName,
         this.targetName,
         this.type,
         this.getAgeSeconds(),
         this.getRemainingTimeSeconds()
      );
   }
}
