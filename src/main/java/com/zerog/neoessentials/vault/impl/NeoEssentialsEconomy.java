package com.zerog.neoessentials.vault.impl;

import com.zerog.neoessentials.api.event.EconomyDepositEvent;
import com.zerog.neoessentials.api.event.EconomyWithdrawEvent;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.vault.api.VaultEconomy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoEssentialsEconomy extends VaultEconomy {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentialsEconomy.class);
   private final DecimalFormat fmt = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));

   @Override
   public String getName() {
      return "NeoEssentials Economy";
   }

   @Override
   public boolean isEnabled() {
      return ConfigManager.isEconomyEnabled() && EconomyManager.getInstance() != null;
   }

   @Override
   public String format(double amount) {
      String symbol = EconomyManager.getInstance().getCurrencySymbol();
      return symbol + this.fmt.format(amount);
   }

   @Override
   public String currencyNameSingular() {
      return EconomyManager.getInstance().getCurrencySymbol();
   }

   @Override
   public String currencyNamePlural() {
      return EconomyManager.getInstance().getCurrencySymbol();
   }

   @Override
   public boolean hasAccount(UUID playerId) {
      try {
         return EconomyManager.getInstance().getAllBalances().containsKey(playerId);
      } catch (Exception var3) {
         LOGGER.error("VaultEconomy: hasAccount error for {}: {}", playerId, var3.getMessage());
         return false;
      }
   }

   @Override
   public boolean createPlayerAccount(UUID playerId) {
      try {
         if (this.hasAccount(playerId)) {
            return true;
         } else {
            BigDecimal start = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
            EconomyManager.getInstance().setBalance(playerId, start);
            return true;
         }
      } catch (Exception var3) {
         LOGGER.error("VaultEconomy: createPlayerAccount error for {}: {}", playerId, var3.getMessage());
         return false;
      }
   }

   @Override
   public double getBalance(UUID playerId) {
      try {
         return EconomyManager.getInstance().getBalance(playerId).doubleValue();
      } catch (Exception var3) {
         LOGGER.error("VaultEconomy: getBalance error for {}: {}", playerId, var3.getMessage());
         return 0.0;
      }
   }

   @Override
   public boolean has(UUID playerId, double amount) {
      return this.getBalance(playerId) >= amount;
   }

   @Override
   public VaultEconomy.EconomyResponse withdrawPlayer(UUID playerId, double amount) {
      if (amount < 0.0) {
         return this.fail(amount, playerId, "Cannot withdraw a negative amount");
      } else {
         try {
            BigDecimal amt = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            boolean ok = EconomyManager.getInstance().subtractBalance(playerId, amt);
            if (!ok) {
               return this.fail(amount, playerId, "Insufficient funds");
            } else {
               NeoForge.EVENT_BUS.post(new EconomyWithdrawEvent(playerId, amount));
               return new VaultEconomy.EconomyResponse(amount, this.getBalance(playerId), VaultEconomy.EconomyResponse.ResponseType.SUCCESS, "");
            }
         } catch (Exception var6) {
            LOGGER.error("VaultEconomy: withdrawPlayer error for {}: {}", playerId, var6.getMessage());
            return this.fail(amount, playerId, var6.getMessage());
         }
      }
   }

   @Override
   public VaultEconomy.EconomyResponse depositPlayer(UUID playerId, double amount) {
      if (amount < 0.0) {
         return this.fail(amount, playerId, "Cannot deposit a negative amount");
      } else {
         try {
            if (!this.hasAccount(playerId)) {
               this.createPlayerAccount(playerId);
            }

            BigDecimal amt = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            boolean ok = EconomyManager.getInstance().addBalance(playerId, amt);
            if (!ok) {
               return this.fail(amount, playerId, "Deposit rejected (max balance reached?)");
            } else {
               NeoForge.EVENT_BUS.post(new EconomyDepositEvent(playerId, amount));
               return new VaultEconomy.EconomyResponse(amount, this.getBalance(playerId), VaultEconomy.EconomyResponse.ResponseType.SUCCESS, "");
            }
         } catch (Exception var6) {
            LOGGER.error("VaultEconomy: depositPlayer error for {}: {}", playerId, var6.getMessage());
            return this.fail(amount, playerId, var6.getMessage());
         }
      }
   }

   private VaultEconomy.EconomyResponse fail(double amount, UUID playerId, String msg) {
      return new VaultEconomy.EconomyResponse(amount, this.getBalance(playerId), VaultEconomy.EconomyResponse.ResponseType.FAILURE, msg);
   }
}
