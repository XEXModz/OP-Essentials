package com.zerog.neoessentials.economy.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
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

public class PayToggleManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(PayToggleManager.class);
   private final ConcurrentHashMap<UUID, Boolean> paytoggleCache = new ConcurrentHashMap<>();
   private final File togglesFile = ResourceUtil.getDataFile("paytoggles.json");
   private final Gson gson = new Gson();
   private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "PayToggle-Save");
      t.setDaemon(true);
      return t;
   });
   private final AtomicBoolean saveQueued = new AtomicBoolean(false);
   private final ConfigManager configManager = ConfigManager.getInstance();

   public static PayToggleManager getInstance() {
      return PayToggleManager.SingletonHolder.INSTANCE;
   }

   private PayToggleManager() {
      this.loadToggles();
      this.saveExecutor.scheduleAtFixedRate(this::saveTogglesAtomic, 5L, 5L, TimeUnit.MINUTES);
   }

   private void loadToggles() {
      if (!this.togglesFile.getParentFile().exists()) {
         this.togglesFile.getParentFile().mkdirs();
      }

      if (this.togglesFile.exists()) {
         try (FileReader reader = new FileReader(this.togglesFile)) {
            Type type = (new TypeToken<Map<String, Boolean>>() {
            }).getType();
            Map<String, Boolean> data = (Map<String, Boolean>)this.gson.fromJson(reader, type);
            if (data != null) {
               for (Entry<String, Boolean> entry : data.entrySet()) {
                  this.paytoggleCache.put(UUID.fromString(entry.getKey()), entry.getValue());
               }
            }
         } catch (Exception var8) {
            LOGGER.error("Failed to load pay toggles", var8);
         }
      }
   }

   private void saveTogglesAtomic() {
      if (!this.togglesFile.getParentFile().exists()) {
         this.togglesFile.getParentFile().mkdirs();
      }

      try {
         File tempFile = new File(this.togglesFile.getAbsolutePath() + ".tmp");

         try (FileWriter writer = new FileWriter(tempFile)) {
            Map<String, Boolean> data = new ConcurrentHashMap<>();

            for (Entry<UUID, Boolean> entry : this.paytoggleCache.entrySet()) {
               data.put(entry.getKey().toString(), entry.getValue());
            }

            this.gson.toJson(data, writer);
         }

         Files.move(tempFile.toPath(), this.togglesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var8) {
         LOGGER.error("Failed to save pay toggles", var8);
      }
   }

   private void queueAsyncSave() {
      if (this.saveQueued.compareAndSet(false, true)) {
         this.saveExecutor.execute(() -> {
            try {
               this.saveTogglesAtomic();
            } finally {
               this.saveQueued.set(false);
            }
         });
      }
   }

   public boolean getPayToggle(UUID player) {
      return this.paytoggleCache.computeIfAbsent(player, uuid -> ConfigManager.getPayToggleDefault());
   }

   public void setPayToggle(UUID player, boolean enabled) {
      this.paytoggleCache.put(player, enabled);
      this.queueAsyncSave();
   }

   public Map<UUID, Boolean> getAllToggles() {
      return new ConcurrentHashMap<>(this.paytoggleCache);
   }

   public void shutdown() {
      try {
         this.saveTogglesAtomic();
         this.saveExecutor.shutdown();

         try {
            if (!this.saveExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
               LOGGER.warn("PayToggleManager executor did not terminate gracefully, forcing shutdown...");
               this.saveExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            LOGGER.warn("Interrupted while waiting for PayToggleManager executor shutdown");
            this.saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
         }

         LOGGER.info("PayToggleManager shutdown complete.");
      } catch (Exception var3) {
         LOGGER.error("Error during PayToggleManager shutdown", var3);
      }
   }

   private static class SingletonHolder {
      private static final PayToggleManager INSTANCE = new PayToggleManager();
   }
}
