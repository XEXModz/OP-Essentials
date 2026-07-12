package com.zerog.neoessentials.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagerRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger(ManagerRegistry.class);
   private static volatile ManagerRegistry instance;
   private final Map<String, ManagerRegistry.ManagerInfo> managers = new ConcurrentHashMap<>();
   private final Map<String, Long> initializationTimes = new ConcurrentHashMap<>();

   private ManagerRegistry() {
      LOGGER.debug("ManagerRegistry initialized");
   }

   public static ManagerRegistry getInstance() {
      if (instance == null) {
         synchronized (ManagerRegistry.class) {
            if (instance == null) {
               instance = new ManagerRegistry();
            }
         }
      }

      return instance;
   }

   public void registerManager(String name, String category, Class<?> managerClass, Supplier<?> initSupplier) {
      if (name != null && !name.trim().isEmpty()) {
         long startTime = System.currentTimeMillis();

         try {
            Object instance = null;
            if (initSupplier != null) {
               instance = initSupplier.get();
            }

            ManagerRegistry.ManagerInfo info = new ManagerRegistry.ManagerInfo(name, category, managerClass, instance != null, System.currentTimeMillis());
            this.managers.put(name, info);
            long duration = System.currentTimeMillis() - startTime;
            this.initializationTimes.put(name, duration);
            LOGGER.debug("Registered manager: {} ({}) [{}ms]", new Object[]{name, category, duration});
         } catch (Exception var11) {
            LOGGER.error("Failed to register manager '{}': {}", new Object[]{name, var11.getMessage(), var11});
            ManagerRegistry.ManagerInfo info = new ManagerRegistry.ManagerInfo(name, category, managerClass, false, System.currentTimeMillis());
            info.markAsFailed(var11.getMessage());
            this.managers.put(name, info);
         }
      } else {
         LOGGER.warn("Attempted to register manager with null or empty name");
      }
   }

   public void registerManager(String name, String category, Class<?> managerClass) {
      this.registerManager(name, category, managerClass, null);
   }

   public void markInitialized(String name) {
      ManagerRegistry.ManagerInfo info = this.managers.get(name);
      if (info != null) {
         info.markAsInitialized();
         LOGGER.debug("Manager '{}' marked as initialized", name);
      }
   }

   public void markFailed(String name, String reason) {
      ManagerRegistry.ManagerInfo info = this.managers.get(name);
      if (info != null) {
         info.markAsFailed(reason);
         LOGGER.error("Manager '{}' marked as failed: {}", name, reason);
      }
   }

   public boolean isRegistered(String name) {
      return this.managers.containsKey(name);
   }

   public boolean isInitialized(String name) {
      ManagerRegistry.ManagerInfo info = this.managers.get(name);
      return info != null && info.isInitialized();
   }

   public int getManagerCount() {
      return this.managers.size();
   }

   public int getInitializedCount() {
      return (int)this.managers.values().stream().filter(ManagerRegistry.ManagerInfo::isInitialized).count();
   }

   public int getFailedCount() {
      return (int)this.managers.values().stream().filter(ManagerRegistry.ManagerInfo::isFailed).count();
   }

   public Map<String, List<String>> getManagersByCategory() {
      Map<String, List<String>> result = new LinkedHashMap<>();

      for (ManagerRegistry.ManagerInfo info : this.managers.values()) {
         result.computeIfAbsent(info.category(), k -> new ArrayList<>()).add(info.name());
      }

      Map<String, List<String>> sorted = new TreeMap<>(result);
      sorted.values().forEach(Collections::sort);
      return sorted;
   }

   public List<ManagerRegistry.ManagerInfo> getAllManagers() {
      return new ArrayList<>(this.managers.values());
   }

   public Map<String, String> getFailedManagers() {
      Map<String, String> failed = new LinkedHashMap<>();

      for (ManagerRegistry.ManagerInfo info : this.managers.values()) {
         if (info.isFailed()) {
            failed.put(info.name(), info.failureReason());
         }
      }

      return failed;
   }

   public String generateDiagnosticReport() {
      StringBuilder report = new StringBuilder();
      report.append("\n╔════════════════════════════════════════════════════════════════╗\n");
      report.append("║           NEOESSENTIALS MANAGER REGISTRY REPORT            ║\n");
      report.append("╚════════════════════════════════════════════════════════════════╝\n\n");
      report.append("Summary:\n");
      report.append(String.format("  Total Managers: %d\n", this.getManagerCount()));
      report.append(
         String.format(
            "  Initialized: %d (%.1f%%)\n",
            this.getInitializedCount(),
            (double)this.getInitializedCount() * 100.0 / (double)Math.max(1, this.getManagerCount())
         )
      );
      report.append(String.format("  Failed: %d\n\n", this.getFailedCount()));
      Map<String, List<String>> byCategory = this.getManagersByCategory();

      for (Entry<String, List<String>> entry : byCategory.entrySet()) {
         String category = entry.getKey();
         List<String> managerNames = entry.getValue();
         report.append(String.format("Category: %s (%d managers)\n", category.toUpperCase(), managerNames.size()));

         for (String name : managerNames) {
            ManagerRegistry.ManagerInfo info = this.managers.get(name);
            String status = info.isInitialized() ? "✓" : (info.isFailed() ? "✗" : "○");
            Long initTime = this.initializationTimes.get(name);
            String timeStr = initTime != null ? String.format(" [%dms]", initTime) : "";
            report.append(String.format("  %s %s%s", status, name, timeStr));
            if (info.isFailed()) {
               report.append(String.format(" - FAILED: %s", info.failureReason()));
            }

            report.append("\n");
         }

         report.append("\n");
      }

      Map<String, String> failed = this.getFailedManagers();
      if (!failed.isEmpty()) {
         report.append("⚠ FAILED MANAGERS:\n");

         for (Entry<String, String> entry : failed.entrySet()) {
            report.append(String.format("  ✗ %s: %s\n", entry.getKey(), entry.getValue()));
         }

         report.append("\n");
      }

      report.append("Legend: ✓ = Initialized | ✗ = Failed | ○ = Registered\n");
      report.append("════════════════════════════════════════════════════════════════\n");
      return report.toString();
   }

   public void clear() {
      this.managers.clear();
      this.initializationTimes.clear();
      LOGGER.info("Cleared all registered managers");
   }

   public static class ManagerInfo {
      private final String name;
      private final String category;
      private final Class<?> managerClass;
      private boolean initialized;
      private boolean failed;
      private String failureReason;
      private final long registrationTime;

      public ManagerInfo(String name, String category, Class<?> managerClass, boolean initialized, long registrationTime) {
         this.name = name;
         this.category = category;
         this.managerClass = managerClass;
         this.initialized = initialized;
         this.failed = false;
         this.failureReason = null;
         this.registrationTime = registrationTime;
      }

      public String name() {
         return this.name;
      }

      public String category() {
         return this.category;
      }

      public Class<?> managerClass() {
         return this.managerClass;
      }

      public boolean isInitialized() {
         return this.initialized;
      }

      public boolean isFailed() {
         return this.failed;
      }

      public String failureReason() {
         return this.failureReason;
      }

      public long registrationTime() {
         return this.registrationTime;
      }

      void markAsInitialized() {
         this.initialized = true;
         this.failed = false;
      }

      void markAsFailed(String reason) {
         this.initialized = false;
         this.failed = true;
         this.failureReason = reason;
      }
   }
}
