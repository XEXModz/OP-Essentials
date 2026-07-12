package com.zerog.neoessentials.config;

import java.io.File;
import java.math.BigDecimal;

@Deprecated
public class EconomyConfig {
   public BigDecimal startingBalance = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
   public String currencySymbol = ConfigManager.getCurrencySymbol();
   public BigDecimal maxBalance = BigDecimal.valueOf(ConfigManager.getMaxBalance());
   public boolean cleanupInactiveAccounts;
   public double taxPercentage = ConfigManager.getTaxPercentage();
   public BigDecimal maxTransferAmount;
   public boolean paytoggleDefault;
   public boolean allowNegativeBalances = ConfigManager.allowNegativeBalances();
   public int inactiveAccountCleanupDays;
   public int cacheMaximumSize;
   public int cacheExpireAfterAccessMinutes;

   public EconomyConfig() {
      this.cleanupInactiveAccounts = ConfigManager.isCleanupInactiveAccountsEnabled();
      this.inactiveAccountCleanupDays = ConfigManager.getInactiveAccountCleanupDays();
      this.maxTransferAmount = BigDecimal.valueOf(ConfigManager.getMaxTransferAmount());
      this.paytoggleDefault = ConfigManager.getPayToggleDefault();
      this.cacheMaximumSize = ConfigManager.getCacheMaximumSize();
      this.cacheExpireAfterAccessMinutes = ConfigManager.getCacheExpireAfterAccessMinutes();
   }

   public static EconomyConfig load(File configFile) {
      ConfigManager.loadAll();
      return new EconomyConfig();
   }
}
