package com.zerog.neoessentials.api.economy;

import java.util.Optional;
import java.util.UUID;

public interface EconomyService {
   double getBalance(UUID var1);

   default Optional<Double> getBalanceOptional(UUID playerId) {
      double bal = this.getBalance(playerId);
      return bal == 0.0 ? Optional.empty() : Optional.of(bal);
   }

   boolean deposit(UUID var1, double var2);

   boolean withdraw(UUID var1, double var2);

   boolean setBalance(UUID var1, double var2);

   boolean resetBalance(UUID var1);

   boolean hasAccount(UUID var1);

   boolean createAccount(UUID var1);

   boolean deleteAccount(UUID var1);

   String format(double var1);

   String getCurrencySymbol();
}
