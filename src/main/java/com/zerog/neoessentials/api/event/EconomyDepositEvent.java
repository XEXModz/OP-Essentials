package com.zerog.neoessentials.api.event;

import java.util.UUID;

public class EconomyDepositEvent extends EconomyEvent {
   public EconomyDepositEvent(UUID playerId, double amount) {
      super(playerId, amount);
   }
}
