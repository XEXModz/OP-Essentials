package com.zerog.neoessentials.api.economy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.api.event.EconomyDepositEvent;
import com.zerog.neoessentials.api.event.EconomyWithdrawEvent;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class EconomyServiceImpl implements EconomyService {
   private static final Logger LOGGER = LoggerFactory.getLogger(EconomyServiceImpl.class);
   private final Path dataFile;
   private static boolean migrated = false;

   public EconomyServiceImpl(Path dataFile) {
      this.dataFile = dataFile;
      if (!migrated && Files.exists(dataFile)) {
         migrated = true;
         LOGGER.info("=== EconomyServiceImpl Migration ===");
         LOGGER.info("Detecting old balance data format - migrating to EconomyManager...");
         this.migrateOldBalances();
      } else {
         LOGGER.debug("EconomyServiceImpl initialized as wrapper around EconomyManager");
      }
   }

   @Override
   public double getBalance(UUID playerId) {
      BigDecimal balance = EconomyManager.getInstance().getBalance(playerId);
      return balance.doubleValue();
   }

   @Override
   public boolean deposit(UUID playerId, double amount) {
      if (amount <= 0.0) {
         return false;
      } else {
         boolean success = EconomyManager.getInstance().addBalance(playerId, BigDecimal.valueOf(amount));
         if (success) {
            NeoForge.EVENT_BUS.post(new EconomyDepositEvent(playerId, amount));
         }

         return success;
      }
   }

   @Override
   public boolean withdraw(UUID playerId, double amount) {
      if (amount <= 0.0) {
         return false;
      } else {
         boolean success = EconomyManager.getInstance().subtractBalance(playerId, BigDecimal.valueOf(amount));
         if (success) {
            NeoForge.EVENT_BUS.post(new EconomyWithdrawEvent(playerId, amount));
         }

         return success;
      }
   }

   @Override
   public boolean setBalance(UUID playerId, double amount) {
      if (amount < 0.0) {
         return false;
      } else {
         EconomyManager.getInstance().setBalance(playerId, BigDecimal.valueOf(amount));
         return true;
      }
   }

   @Override
   public boolean resetBalance(UUID playerId) {
      EconomyManager.getInstance().setBalance(playerId, BigDecimal.ZERO);
      return true;
   }

   @Override
   public boolean hasAccount(UUID playerId) {
      return EconomyManager.getInstance().getAllBalances().containsKey(playerId);
   }

   @Override
   public boolean createAccount(UUID playerId) {
      if (this.hasAccount(playerId)) {
         return false;
      } else {
         BigDecimal startingBalance = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
         EconomyManager.getInstance().setBalance(playerId, startingBalance);
         return true;
      }
   }

   @Override
   public boolean deleteAccount(UUID playerId) {
      if (!this.hasAccount(playerId)) {
         return false;
      } else {
         EconomyManager.getInstance().setBalance(playerId, BigDecimal.ZERO);
         return true;
      }
   }

   @Override
   public String format(double amount) {
      return String.format("%s%.2f", this.getCurrencySymbol(), amount);
   }

   @Override
   public String getCurrencySymbol() {
      return ConfigManager.getCurrencySymbol();
   }

   private void migrateOldBalances() {
      try {
         if (!Files.exists(this.dataFile)) {
            LOGGER.info("No old balance data found, skipping migration");
         } else {
            try (Reader reader = Files.newBufferedReader(this.dataFile)) {
               Type type = (new TypeToken<Map<String, Object>>() {
               }).getType();
               Map<String, Object> raw = (Map<String, Object>)new Gson().fromJson(reader, type);
               if (raw != null && !raw.isEmpty()) {
                  if (raw.containsKey("_dataVersion")) {
                     LOGGER.info("Balance data already in new format, no migration needed");
                     return;
                  }

                  int migratedCount = 0;

                  for (Entry<String, Object> entry : raw.entrySet()) {
                     try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        double amount = ((Number)entry.getValue()).doubleValue();
                        EconomyManager.getInstance().setBalance(playerId, BigDecimal.valueOf(amount));
                        migratedCount++;
                     } catch (Exception var11) {
                        LOGGER.warn("Failed to migrate balance for key: {}", entry.getKey(), var11);
                     }
                  }

                  LOGGER.info("✓ Successfully migrated {} player balances to EconomyManager", migratedCount);
                  Path backupPath = this.dataFile.getParent().resolve(this.dataFile.getFileName() + ".old");
                  Files.move(this.dataFile, backupPath);
                  LOGGER.info("✓ Old balance file backed up to: {}", backupPath.getFileName());
                  return;
               }

               LOGGER.info("Old balance file is empty, skipping migration");
            }
         }
      } catch (Exception var13) {
         LOGGER.error("Failed to migrate old balance data - manual recovery may be needed!", var13);
      }
   }

   @Deprecated
   @Override
   public Optional<Double> getBalanceOptional(UUID playerId) {
      double balance = this.getBalance(playerId);
      return Optional.of(balance);
   }
}
