package com.zerog.neoessentials.api.event;

import java.util.UUID;

public class EconomyWithdrawEvent extends EconomyEvent {
   public EconomyWithdrawEvent(UUID playerId, double amount) {
      super(playerId, amount);
   }
}
