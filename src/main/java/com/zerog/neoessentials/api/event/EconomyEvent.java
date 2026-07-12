package com.zerog.neoessentials.api.event;

import java.util.UUID;
import net.neoforged.bus.api.Event;

public abstract class EconomyEvent extends Event {
   private final UUID playerId;
   private final double amount;

   public EconomyEvent(UUID playerId, double amount) {
      this.playerId = playerId;
      this.amount = amount;
   }

   public UUID getPlayerId() {
      return this.playerId;
   }

   public double getAmount() {
      return this.amount;
   }
}
