package com.zerog.neoessentials.economy.events;

import java.math.BigDecimal;
import java.util.UUID;
import net.neoforged.bus.api.Event;

public class EconomyTransactionEvent extends Event {
   private final EconomyTransactionEvent.Type type;
   private final UUID sender;
   private final UUID recipient;
   private final BigDecimal amount;
   private final String reason;

   public EconomyTransactionEvent(EconomyTransactionEvent.Type type, UUID sender, UUID recipient, BigDecimal amount, String reason) {
      this.type = type;
      this.sender = sender;
      this.recipient = recipient;
      this.amount = amount;
      this.reason = reason;
   }

   public EconomyTransactionEvent.Type getType() {
      return this.type;
   }

   public UUID getSender() {
      return this.sender;
   }

   public UUID getRecipient() {
      return this.recipient;
   }

   public BigDecimal getAmount() {
      return this.amount;
   }

   public String getReason() {
      return this.reason;
   }

   public static enum Type {
      PAY,
      ADMIN_GIVE,
      ADMIN_TAKE,
      ADMIN_SET;
   }
}
