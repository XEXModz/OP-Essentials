package com.zerog.neoessentials.vault.api;

import java.util.List;
import java.util.UUID;

public abstract class VaultEconomy {
   public abstract String getName();

   public abstract boolean isEnabled();

   public abstract String format(double var1);

   public abstract String currencyNameSingular();

   public abstract String currencyNamePlural();

   public int fractionalDigits() {
      return 2;
   }

   public abstract boolean hasAccount(UUID var1);

   public boolean hasAccount(String playerName) {
      return false;
   }

   public abstract boolean createPlayerAccount(UUID var1);

   public boolean createPlayerAccount(String playerName) {
      return false;
   }

   public abstract double getBalance(UUID var1);

   public double getBalance(String playerName) {
      return 0.0;
   }

   public boolean has(UUID playerId, double amount) {
      return this.getBalance(playerId) >= amount;
   }

   public abstract VaultEconomy.EconomyResponse withdrawPlayer(UUID var1, double var2);

   public abstract VaultEconomy.EconomyResponse depositPlayer(UUID var1, double var2);

   public boolean hasBankSupport() {
      return false;
   }

   public VaultEconomy.EconomyResponse createBank(String name, UUID ownerId) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse deleteBank(String name) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse bankBalance(String name) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse bankHas(String name, double amount) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse bankWithdraw(String name, double amount) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse bankDeposit(String name, double amount) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse isBankOwner(String name, UUID playerId) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public VaultEconomy.EconomyResponse isBankMember(String name, UUID playerId) {
      return new VaultEconomy.EconomyResponse(0.0, 0.0, VaultEconomy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
   }

   public List<String> getBanks() {
      return List.of();
   }

   public static class EconomyResponse {
      public final double amount;
      public final double balance;
      public final VaultEconomy.EconomyResponse.ResponseType type;
      public final String errorMessage;

      public EconomyResponse(double amount, double balance, VaultEconomy.EconomyResponse.ResponseType type, String errorMessage) {
         this.amount = amount;
         this.balance = balance;
         this.type = type;
         this.errorMessage = errorMessage != null ? errorMessage : "";
      }

      public boolean transactionSuccess() {
         return this.type == VaultEconomy.EconomyResponse.ResponseType.SUCCESS;
      }

      public static enum ResponseType {
         SUCCESS,
         FAILURE,
         NOT_IMPLEMENTED;
      }
   }
}
