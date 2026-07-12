package com.zerog.neoessentials.economy.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.config.EconomyConfig;
import com.zerog.neoessentials.economy.EconomyTransactionLogger;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(EconomyManager.class);
   private static final String DATA_VERSION_KEY = "_dataVersion";
   private static final int CURRENT_DATA_VERSION = 1;
   private ConcurrentHashMap<UUID, BigDecimal> balancesCache;
   private final File balancesFile = ResourceUtil.getDataFile("balances.json");
   private final Gson gson = new Gson();
   private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "EconomyManager-Save");
      t.setDaemon(true);
      return t;
   });
   private final AtomicBoolean saveQueued = new AtomicBoolean(false);
   private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
   private final ConcurrentHashMap<UUID, Long> lastActivityMap = new ConcurrentHashMap<>();
   private final File lastActivityFile = new File("neoessentials/balances_activity.json");
   private volatile int currentBalancesVersion = 1;
   private volatile int currentActivityVersion = 1;

   public static EconomyManager getInstance() {
      return EconomyManager.SingletonHolder.INSTANCE;
   }

   private void loadBalances() {
      if (!this.balancesFile.getParentFile().exists()) {
         this.balancesFile.getParentFile().mkdirs();
      }

      if (this.balancesFile.exists()) {
         try (FileReader reader = new FileReader(this.balancesFile)) {
            Type type = (new TypeToken<Map<String, Object>>() {
            }).getType();
            Map<String, Object> data = (Map<String, Object>)this.gson.fromJson(reader, type);
            if (data != null) {
               if (data.containsKey("_dataVersion")) {
                  Object versionObj = data.get("_dataVersion");
                  if (versionObj instanceof Number) {
                     this.currentBalancesVersion = ((Number)versionObj).intValue();
                  }

                  data.remove("_dataVersion");
               }

               for (Entry<String, Object> entry : data.entrySet()) {
                  if (!entry.getKey().startsWith("_")) {
                     this.balancesCache.put(UUID.fromString(entry.getKey()), new BigDecimal(entry.getValue().toString()));
                  }
               }
            }
         } catch (Exception var8) {
            LOGGER.error("Failed to load balances from file", var8);
         }
      }
   }

   private void saveBalancesAtomic() {
      if (!this.balancesFile.getParentFile().exists()) {
         this.balancesFile.getParentFile().mkdirs();
      }

      try {
         if (this.balancesFile.exists() && this.shouldCreateBackup(this.balancesFile, this.currentBalancesVersion)) {
            LOGGER.info("Economy data version mismatch detected, creating backup...");
            this.createBackupFile(this.balancesFile);
         }

         File tempFile = new File(this.balancesFile.getAbsolutePath() + ".tmp");

         try (FileWriter writer = new FileWriter(tempFile)) {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("_dataVersion", 1);

            for (Entry<UUID, BigDecimal> entry : this.balancesCache.entrySet()) {
               data.put(entry.getKey().toString(), entry.getValue().toPlainString());
            }

            this.gson.toJson(data, writer);
         }

         Files.move(tempFile.toPath(), this.balancesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         this.currentBalancesVersion = 1;
      } catch (IOException var8) {
         LOGGER.error("Failed to save balances to file", var8);
      }
   }

   private void queueAsyncSave() {
      if (!this.shuttingDown.get()) {
         if (this.saveQueued.compareAndSet(false, true)) {
            this.saveExecutor.execute(() -> {
               try {
                  this.saveBalancesAtomic();
               } finally {
                  this.saveQueued.set(false);
               }
            });
         }
      }
   }

   private void cleanupInactiveAccounts() {
      if (ConfigManager.isCleanupInactiveAccountsEnabled()) {
         long now = System.currentTimeMillis();
         long thresholdMillis = (long)ConfigManager.getInactiveAccountCleanupDays() * 24L * 60L * 60L * 1000L;

         for (UUID uuid : this.balancesCache.keySet()) {
            Long lastActive = this.lastActivityMap.get(uuid);
            if (lastActive == null || now - lastActive >= thresholdMillis) {
               this.balancesCache.remove(uuid);
               this.lastActivityMap.remove(uuid);
            }
         }

         this.queueAsyncSave();
         this.queueAsyncSaveActivity();
      }
   }

   private void loadLastActivity() {
      if (!this.lastActivityFile.getParentFile().exists()) {
         this.lastActivityFile.getParentFile().mkdirs();
      }

      if (this.lastActivityFile.exists()) {
         try (FileReader reader = new FileReader(this.lastActivityFile)) {
            Type type = (new TypeToken<Map<String, Object>>() {
            }).getType();
            Map<String, Object> data = (Map<String, Object>)this.gson.fromJson(reader, type);
            if (data != null) {
               if (data.containsKey("_dataVersion")) {
                  Object versionObj = data.get("_dataVersion");
                  if (versionObj instanceof Number) {
                     this.currentActivityVersion = ((Number)versionObj).intValue();
                  }

                  data.remove("_dataVersion");
               }

               for (Entry<String, Object> entry : data.entrySet()) {
                  if (!entry.getKey().startsWith("_")) {
                     this.lastActivityMap.put(UUID.fromString(entry.getKey()), ((Number)entry.getValue()).longValue());
                  }
               }
            }
         } catch (Exception var8) {
            LOGGER.error("Failed to load last activity data", var8);
         }
      }
   }

   private void saveLastActivityAtomic() {
      if (!this.lastActivityFile.getParentFile().exists()) {
         this.lastActivityFile.getParentFile().mkdirs();
      }

      try {
         if (this.lastActivityFile.exists() && this.shouldCreateBackup(this.lastActivityFile, this.currentActivityVersion)) {
            LOGGER.info("Activity data version mismatch detected, creating backup...");
            this.createBackupFile(this.lastActivityFile);
         }

         File tempFile = new File(this.lastActivityFile.getAbsolutePath() + ".tmp");

         try (FileWriter writer = new FileWriter(tempFile)) {
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("_dataVersion", 1);

            for (Entry<UUID, Long> entry : this.lastActivityMap.entrySet()) {
               data.put(entry.getKey().toString(), entry.getValue());
            }

            this.gson.toJson(data, writer);
         }

         Files.move(tempFile.toPath(), this.lastActivityFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         this.currentActivityVersion = 1;
      } catch (IOException var8) {
         LOGGER.error("Failed to save last activity data", var8);
      }
   }

   private void queueAsyncSaveActivity() {
      this.saveExecutor.execute(this::saveLastActivityAtomic);
   }

   private EconomyManager() {
      if (ConfigManager.isEconomyEnabled()) {
         this.balancesCache = new ConcurrentHashMap<>();
         this.loadBalances();
         this.loadLastActivity();
         this.saveExecutor.scheduleAtFixedRate(this::saveBalancesAtomic, 5L, 5L, TimeUnit.MINUTES);
         this.saveExecutor.scheduleAtFixedRate(this::saveLastActivityAtomic, 5L, 5L, TimeUnit.MINUTES);
         this.saveExecutor.scheduleAtFixedRate(this::logCacheMetrics, 30L, 30L, TimeUnit.MINUTES);
         this.saveExecutor.scheduleAtFixedRate(this::cleanupInactiveAccounts, 1L, 1L, TimeUnit.HOURS);
      }
   }

   public BigDecimal getBalance(UUID player) {
      return this.balancesCache.computeIfAbsent(player, uuid -> BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance()));
   }

   public void setBalance(UUID player, BigDecimal amount) {
      if (this.shuttingDown.get()) {
         LOGGER.warn("Attempted to modify balance during shutdown for player {}", player);
      } else {
         if (!ConfigManager.allowNegativeBalances() && amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
         }

         BigDecimal maxBalance = BigDecimal.valueOf(ConfigManager.getMaxBalance());
         if (amount.compareTo(maxBalance) > 0) {
            amount = maxBalance;
         }

         BigDecimal oldAmount = this.balancesCache.put(player, amount);
         this.lastActivityMap.put(player, System.currentTimeMillis());
         this.queueAsyncSave();
         this.queueAsyncSaveActivity();
         EconomyTransactionLogger.log(
            "SET",
            player.toString(),
            "SERVER",
            amount.toPlainString(),
            "Set balance (was: " + (oldAmount != null ? oldAmount.toPlainString() : "new account") + ")"
         );
      }
   }

   public boolean addBalance(UUID player, BigDecimal amount) {
      if (this.shuttingDown.get()) {
         LOGGER.warn("Attempted to modify balance during shutdown for player {}", player);
         return false;
      } else {
         BigDecimal maxBalance = BigDecimal.valueOf(ConfigManager.getMaxBalance());
         boolean allowNegative = ConfigManager.allowNegativeBalances();
         BigDecimal[] result = new BigDecimal[1];
         this.balancesCache.compute(player, (uuid, current) -> {
            if (current == null) {
               current = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
            }

            BigDecimal newAmount = current.add(amount);
            if (!allowNegative && newAmount.compareTo(BigDecimal.ZERO) < 0) {
               result[0] = null;
               return (BigDecimal)current;
            } else {
               if (newAmount.compareTo(maxBalance) > 0) {
                  newAmount = maxBalance;
               }

               result[0] = newAmount;
               return newAmount;
            }
         });
         if (result[0] == null) {
            return false;
         } else {
            this.lastActivityMap.put(player, System.currentTimeMillis());
            this.queueAsyncSave();
            this.queueAsyncSaveActivity();
            EconomyTransactionLogger.log("ADD", "SERVER", player.toString(), amount.toPlainString(), "Add to balance");
            return true;
         }
      }
   }

   public boolean subtractBalance(UUID player, BigDecimal amount) {
      if (this.shuttingDown.get()) {
         LOGGER.warn("Attempted to modify balance during shutdown for player {}", player);
         return false;
      } else {
         boolean allowNegative = ConfigManager.allowNegativeBalances();
         BigDecimal[] result = new BigDecimal[1];
         this.balancesCache.compute(player, (uuid, current) -> {
            if (current == null) {
               current = BigDecimal.valueOf(ConfigManager.getEconomyStartingBalance());
            }

            BigDecimal newAmount = current.subtract(amount);
            if (!allowNegative && newAmount.compareTo(BigDecimal.ZERO) < 0) {
               result[0] = null;
               return (BigDecimal)current;
            } else {
               result[0] = newAmount;
               return newAmount;
            }
         });
         if (result[0] == null) {
            return false;
         } else {
            this.lastActivityMap.put(player, System.currentTimeMillis());
            this.queueAsyncSave();
            this.queueAsyncSaveActivity();
            EconomyTransactionLogger.log("SUBTRACT", player.toString(), "SERVER", amount.toPlainString(), "Subtract from balance");
            return true;
         }
      }
   }

   public Map<UUID, BigDecimal> getAllBalances() {
      return new ConcurrentHashMap<>(this.balancesCache);
   }

   @Deprecated
   public EconomyConfig getConfig() {
      return new EconomyConfig();
   }

   public boolean isEnabled() {
      return ConfigManager.isEconomyEnabled();
   }

   public String getCurrencySymbol() {
      return ConfigManager.getCurrencySymbol();
   }

   public void optimizeCache() {
   }

   public String getCacheStats() {
      return "EconomyManager Cache Size: " + this.balancesCache.size();
   }

   private void logCacheMetrics() {
      LOGGER.info("EconomyManager Cache Metrics - Size: {}", this.balancesCache.size());
   }

   public void shutdown() {
      LOGGER.info("Shutting down EconomyManager...");
      this.shuttingDown.set(true);

      try {
         Thread.sleep(100L);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }

      this.saveBalancesAtomic();
      this.saveLastActivityAtomic();
      this.saveExecutor.shutdown();

      try {
         if (!this.saveExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
            LOGGER.warn("EconomyManager executor did not terminate gracefully, forcing shutdown...");
            this.saveExecutor.shutdownNow();
         }
      } catch (InterruptedException var2) {
         LOGGER.warn("Interrupted while waiting for EconomyManager executor shutdown");
         this.saveExecutor.shutdownNow();
         Thread.currentThread().interrupt();
      }

      LOGGER.info("EconomyManager shutdown complete.");
   }

   private boolean shouldCreateBackup(File file, int currentVersion) {
      if (!file.exists()) {
         return false;
      } else {
         try {
            boolean var12;
            try (FileReader reader = new FileReader(file)) {
               Type type = (new TypeToken<Map<String, Object>>() {
               }).getType();
               Map<String, Object> data = (Map<String, Object>)this.gson.fromJson(reader, type);
               if (data != null && data.containsKey("_dataVersion")) {
                  Object versionObj = data.get("_dataVersion");
                  if (versionObj instanceof Number) {
                     int fileVersion = ((Number)versionObj).intValue();
                     return fileVersion != 1;
                  }
               }

               var12 = true;
            }

            return var12;
         } catch (Exception var11) {
            LOGGER.warn("Error checking version for {}: {}", file.getName(), var11.getMessage());
            return false;
         }
      }
   }

   private void createBackupFile(File originalFile) {
      try {
         File parent = originalFile.getParentFile();
         String baseName = originalFile.getName();
         File backupFile = null;

         for (int i = 1; i <= 999; i++) {
            File candidate = new File(parent, baseName + ".bak" + i);
            if (!candidate.exists()) {
               backupFile = candidate;
               break;
            }
         }

         if (backupFile == null) {
            backupFile = new File(parent, baseName + ".bak999");
         }

         Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         LOGGER.info("✓ Created backup: {} -> {} (version mismatch detected)", originalFile.getName(), backupFile.getName());
      } catch (IOException var7) {
         LOGGER.error("Failed to create backup for {}: {}", originalFile.getName(), var7.getMessage());
      }
   }

   private static class SingletonHolder {
      private static final EconomyManager INSTANCE = new EconomyManager();
   }
}
