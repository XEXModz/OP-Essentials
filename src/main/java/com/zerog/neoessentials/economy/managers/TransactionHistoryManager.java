package com.zerog.neoessentials.economy.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.util.DebugUtil;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransactionHistoryManager {
   private static TransactionHistoryManager instance;
   private static final int HISTORY_LIMIT = 20;
   private final Map<UUID, Deque<String>> historyMap = new ConcurrentHashMap<>();
   private final File historyFile = ResourceUtil.getDataFile("transaction_history.json");
   private final Gson gson = new Gson();
   private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "TransactionHistory-Save");
      t.setDaemon(true);
      return t;
   });
   private final AtomicBoolean saveQueued = new AtomicBoolean(false);

   public static TransactionHistoryManager getInstance() {
      if (instance == null) {
         instance = new TransactionHistoryManager();
      }

      return instance;
   }

   private TransactionHistoryManager() {
      this.loadHistory();
      this.saveExecutor.scheduleAtFixedRate(this::saveHistoryAtomic, 5L, 5L, TimeUnit.MINUTES);
   }

   private void loadHistory() {
      if (!this.historyFile.getParentFile().exists()) {
         this.historyFile.getParentFile().mkdirs();
      }

      if (this.historyFile.exists()) {
         try (FileReader reader = new FileReader(this.historyFile)) {
            Type type = (new TypeToken<Map<String, List<String>>>() {
            }).getType();
            Map<String, List<String>> data = (Map<String, List<String>>)this.gson.fromJson(reader, type);
            if (data != null) {
               for (Entry<String, List<String>> entry : data.entrySet()) {
                  Deque<String> deque = new ArrayDeque<>(entry.getValue());
                  this.historyMap.put(UUID.fromString(entry.getKey()), deque);
               }
            }
         } catch (Exception var9) {
            DebugUtil.debugStackTrace(var9);
         }
      }
   }

   private void saveHistoryAtomic() {
      if (!this.historyFile.getParentFile().exists()) {
         this.historyFile.getParentFile().mkdirs();
      }

      try {
         File tempFile = new File(this.historyFile.getAbsolutePath() + ".tmp");

         try (FileWriter writer = new FileWriter(tempFile)) {
            Map<String, List<String>> data = new HashMap<>();

            for (Entry<UUID, Deque<String>> entry : this.historyMap.entrySet()) {
               data.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }

            this.gson.toJson(data, writer);
         }

         Files.move(tempFile.toPath(), this.historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var8) {
         DebugUtil.debugStackTrace(var8);
      }
   }

   private void queueAsyncSave() {
      if (this.saveQueued.compareAndSet(false, true)) {
         this.saveExecutor.execute(() -> {
            try {
               this.saveHistoryAtomic();
            } finally {
               this.saveQueued.set(false);
            }
         });
      }
   }

   public void addTransaction(UUID player, String entry) {
      this.historyMap.computeIfAbsent(player, k -> new ArrayDeque<>());
      Deque<String> deque = this.historyMap.get(player);
      if (deque.size() >= 20) {
         deque.removeFirst();
      }

      deque.addLast(entry);
      this.queueAsyncSave();
   }

   public List<String> getHistory(UUID player) {
      return new ArrayList<>(this.historyMap.getOrDefault(player, new ArrayDeque<>()));
   }

   public void shutdown() {
      try {
         this.saveHistoryAtomic();
         this.saveExecutor.shutdown();

         try {
            if (!this.saveExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
               DebugUtil.debug("TransactionHistoryManager executor did not terminate gracefully, forcing shutdown...");
               this.saveExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            DebugUtil.debug("Interrupted while waiting for TransactionHistoryManager executor shutdown");
            this.saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
         }

         DebugUtil.debug("TransactionHistoryManager shutdown complete.");
      } catch (Exception var3) {
         DebugUtil.debugStackTrace(var3);
      }
   }
}
