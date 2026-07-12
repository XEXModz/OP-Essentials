package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingDataCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDataCollector.class);
   private static final int MAX_LOG_ENTRIES = 10000;
   private static final ConcurrentLinkedQueue<LoggingDataCollector.LogEntry> requestLogs = new ConcurrentLinkedQueue<>();
   private static final ConcurrentLinkedQueue<LoggingDataCollector.LogEntry> errorLogs = new ConcurrentLinkedQueue<>();
   private static final ConcurrentLinkedQueue<LoggingDataCollector.PerformanceMetric> performanceMetrics = new ConcurrentLinkedQueue<>();

   public JsonObject getRequestLogs(int limit) {
      JsonObject response = new JsonObject();
      JsonArray logs = new JsonArray();
      List<LoggingDataCollector.LogEntry> recentLogs = new ArrayList<>();

      for (LoggingDataCollector.LogEntry log : requestLogs) {
         if (recentLogs.size() >= limit) {
            break;
         }

         recentLogs.add(log);
      }

      recentLogs.forEach(logx -> logs.add(logx.toJson()));
      response.add("logs", logs);
      response.addProperty("count", logs.size());
      response.addProperty("total", requestLogs.size());
      return response;
   }

   public JsonObject getErrorLogs(int limit, String severity) {
      JsonObject response = new JsonObject();
      JsonArray logs = new JsonArray();
      List<LoggingDataCollector.LogEntry> recentErrors = new ArrayList<>();

      for (LoggingDataCollector.LogEntry log : errorLogs) {
         if (recentErrors.size() >= limit) {
            break;
         }

         if (severity == null || log.severity.equalsIgnoreCase(severity)) {
            recentErrors.add(log);
         }
      }

      recentErrors.forEach(logx -> logs.add(logx.toJson()));
      response.add("logs", logs);
      response.addProperty("count", logs.size());
      response.addProperty("total", errorLogs.size());
      return response;
   }

   public JsonObject getPerformanceMetrics() {
      JsonObject response = new JsonObject();
      JsonArray metrics = new JsonArray();
      List<LoggingDataCollector.PerformanceMetric> recentMetrics = new ArrayList<>(performanceMetrics);
      recentMetrics.forEach(metric -> metrics.add(metric.toJson()));
      if (!recentMetrics.isEmpty()) {
         double avgResponseTime = recentMetrics.stream().mapToLong(m -> m.responseTimeMs).average().orElse(0.0);
         long maxResponseTime = recentMetrics.stream().mapToLong(m -> m.responseTimeMs).max().orElse(0L);
         long minResponseTime = recentMetrics.stream().mapToLong(m -> m.responseTimeMs).min().orElse(0L);
         response.addProperty("averageResponseTime", avgResponseTime);
         response.addProperty("maxResponseTime", maxResponseTime);
         response.addProperty("minResponseTime", minResponseTime);
      }

      response.add("metrics", metrics);
      response.addProperty("count", metrics.size());
      return response;
   }

   public JsonObject getUserActivity() {
      JsonObject activity = new JsonObject();
      long oneHourAgo = Instant.now().toEpochMilli() - 3600000L;
      int totalRequests = 0;
      int uniqueUsers = 0;
      int successfulRequests = 0;
      int failedRequests = 0;

      for (LoggingDataCollector.LogEntry log : requestLogs) {
         if (log.timestamp >= oneHourAgo) {
            totalRequests++;
            if (log.statusCode >= 200 && log.statusCode < 300) {
               successfulRequests++;
            } else if (log.statusCode >= 400) {
               failedRequests++;
            }
         }
      }

      activity.addProperty("totalRequests", totalRequests);
      activity.addProperty("uniqueUsers", uniqueUsers);
      activity.addProperty("successfulRequests", successfulRequests);
      activity.addProperty("failedRequests", failedRequests);
      activity.addProperty("period", "last_hour");
      return activity;
   }

   public JsonObject getServerLogs(int lines) {
      JsonObject response = new JsonObject();
      JsonArray logs = new JsonArray();
      File logFile = new File("logs/latest.log");
      if (logFile.exists()) {
         try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            List<String> allLines = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
               allLines.add(line);
            }

            int startIndex = Math.max(0, allLines.size() - lines);

            for (int i = startIndex; i < allLines.size(); i++) {
               logs.add(allLines.get(i));
            }

            response.add("logs", logs);
            response.addProperty("count", logs.size());
            response.addProperty("totalLines", allLines.size());
         } catch (IOException var12) {
            LOGGER.error("Failed to read server log file", var12);
            response.addProperty("error", "Failed to read log file: " + var12.getMessage());
         }
      } else {
         response.addProperty("error", "Log file not found");
      }

      return response;
   }

   public JsonObject getErrorStatistics() {
      JsonObject stats = new JsonObject();
      int criticalErrors = 0;
      int warnings = 0;
      int errors = 0;

      for (LoggingDataCollector.LogEntry log : errorLogs) {
         String var7 = log.severity.toLowerCase();
         switch (var7) {
            case "critical":
               criticalErrors++;
               break;
            case "error":
               errors++;
               break;
            case "warning":
               warnings++;
         }
      }

      stats.addProperty("critical", criticalErrors);
      stats.addProperty("errors", errors);
      stats.addProperty("warnings", warnings);
      stats.addProperty("total", errorLogs.size());
      return stats;
   }

   public static void logRequest(String endpoint, String method, int statusCode, long responseTimeMs, String username) {
      LoggingDataCollector.LogEntry entry = new LoggingDataCollector.LogEntry(
         "REQUEST", endpoint + " " + method + " -> " + statusCode, "INFO", statusCode, username
      );
      requestLogs.offer(entry);
      if (requestLogs.size() > 10000) {
         requestLogs.poll();
      }

      LoggingDataCollector.PerformanceMetric metric = new LoggingDataCollector.PerformanceMetric(endpoint, method, responseTimeMs, statusCode);
      performanceMetrics.offer(metric);
      if (performanceMetrics.size() > 10000) {
         performanceMetrics.poll();
      }
   }

   public static void logError(String message, String severity, Exception exception) {
      String fullMessage = message;
      if (exception != null) {
         fullMessage = message + ": " + exception.getMessage();
      }

      LoggingDataCollector.LogEntry entry = new LoggingDataCollector.LogEntry("ERROR", fullMessage, severity, 500, "system");
      errorLogs.offer(entry);
      if (errorLogs.size() > 10000) {
         errorLogs.poll();
      }

      LOGGER.error(message, exception);
   }

   public void clearLogs(String logType) {
      String var2 = logType.toLowerCase();
      switch (var2) {
         case "requests":
            requestLogs.clear();
            LOGGER.info("Request logs cleared");
            break;
         case "errors":
            errorLogs.clear();
            LOGGER.info("Error logs cleared");
            break;
         case "performance":
            performanceMetrics.clear();
            LOGGER.info("Performance metrics cleared");
            break;
         case "all":
            requestLogs.clear();
            errorLogs.clear();
            performanceMetrics.clear();
            LOGGER.info("All logs cleared");
      }
   }

   public static class LogEntry {
      public final String type;
      public final String message;
      public final String severity;
      public final int statusCode;
      public final String username;
      public final long timestamp;

      public LogEntry(String type, String message, String severity, int statusCode, String username) {
         this.type = type;
         this.message = message;
         this.severity = severity;
         this.statusCode = statusCode;
         this.username = username;
         this.timestamp = Instant.now().toEpochMilli();
      }

      public JsonObject toJson() {
         JsonObject json = new JsonObject();
         json.addProperty("type", this.type);
         json.addProperty("message", this.message);
         json.addProperty("severity", this.severity);
         json.addProperty("statusCode", this.statusCode);
         json.addProperty("username", this.username);
         json.addProperty("timestamp", this.timestamp);
         return json;
      }
   }

   public static class PerformanceMetric {
      public final String endpoint;
      public final String method;
      public final long responseTimeMs;
      public final int statusCode;
      public final long timestamp;

      public PerformanceMetric(String endpoint, String method, long responseTimeMs, int statusCode) {
         this.endpoint = endpoint;
         this.method = method;
         this.responseTimeMs = responseTimeMs;
         this.statusCode = statusCode;
         this.timestamp = Instant.now().toEpochMilli();
      }

      public JsonObject toJson() {
         JsonObject json = new JsonObject();
         json.addProperty("endpoint", this.endpoint);
         json.addProperty("method", this.method);
         json.addProperty("responseTime", this.responseTimeMs);
         json.addProperty("statusCode", this.statusCode);
         json.addProperty("timestamp", this.timestamp);
         return json;
      }
   }
}
