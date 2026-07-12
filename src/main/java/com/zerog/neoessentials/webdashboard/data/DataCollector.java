package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(DataCollector.class);
   private static DataCollector INSTANCE;
   private final Map<String, DataCollector.CachedData> dataCache = new ConcurrentHashMap<>();
   private PlayerDataCollector playerCollector;
   private ServerDataCollector serverCollector;
   private GameDataCollector gameCollector;
   private LoggingDataCollector loggingCollector;

   private DataCollector() {
   }

   public static DataCollector getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DataCollector();
      }

      return INSTANCE;
   }

   public void initialize(MinecraftServer server) {
      this.playerCollector = new PlayerDataCollector(server);
      this.serverCollector = new ServerDataCollector(server);
      this.gameCollector = new GameDataCollector(server);
      this.loggingCollector = new LoggingDataCollector();
      LOGGER.info("Data Collector initialized with all specialized collectors");
   }

   public void shutdown() {
      this.dataCache.clear();
      LOGGER.info("Data Collector stopped");
   }

   public JsonObject getPlayerProfile(UUID playerUuid) {
      return this.playerCollector.getPlayerProfile(playerUuid);
   }

   public JsonObject getPlayerStatistics(UUID playerUuid) {
      return this.playerCollector.getPlayerStatistics(playerUuid);
   }

   public JsonObject getPlayerAchievements(UUID playerUuid) {
      return this.playerCollector.getPlayerAchievements(playerUuid);
   }

   public JsonObject getPlayerInventory(UUID playerUuid) {
      return this.playerCollector.getPlayerInventory(playerUuid);
   }

   public JsonObject getPlayerStatus(UUID playerUuid) {
      return this.playerCollector.getPlayerStatus(playerUuid);
   }

   public JsonObject getPlayerHealth(UUID playerUuid) {
      return this.playerCollector.getPlayerHealth(playerUuid);
   }

   public JsonObject getPlayerXP(UUID playerUuid) {
      return this.playerCollector.getPlayerXP(playerUuid);
   }

   public JsonObject getPlayerLocation(UUID playerUuid) {
      return this.playerCollector.getPlayerLocation(playerUuid);
   }

   public JsonObject getPlayerHomes(String username) {
      return this.playerCollector.getPlayerHomes(username);
   }

   public JsonObject getOnlinePlayers() {
      return this.playerCollector.getOnlinePlayers();
   }

   public JsonObject getServerProfile() {
      return this.serverCollector.getServerProfile();
   }

   public JsonObject getServerStatistics() {
      return this.serverCollector.getServerStatistics();
   }

   public JsonObject getServerStatus() {
      return this.getCachedOrCompute("server_status", () -> this.serverCollector.getServerStatus(), 1000L);
   }

   public JsonObject getServerHealth() {
      return this.serverCollector.getServerHealth();
   }

   public JsonObject getServerWorlds() {
      return this.serverCollector.getServerWorlds();
   }

   public JsonObject getServerConfig() {
      return this.serverCollector.getServerConfig();
   }

   public JsonObject getServerPerformance() {
      return this.serverCollector.getServerPerformance();
   }

   public JsonObject getServerInfo() {
      return this.getCachedOrCompute("server_info", () -> this.serverCollector.getServerProfile(), 60000L);
   }

   public JsonObject getMemoryInfo() {
      return this.getCachedOrCompute("memory_info", () -> {
         JsonObject stats = this.serverCollector.getServerStatistics();
         return stats.getAsJsonObject("memory");
      }, 2000L);
   }

   public JsonObject getGameEvents(int limit) {
      return this.gameCollector.getGameEvents(limit);
   }

   public JsonObject getGameStatistics() {
      return this.gameCollector.getGameStatistics();
   }

   public JsonObject getGameActivity() {
      return this.gameCollector.getGameActivity();
   }

   public JsonObject getTopBlocks() {
      return this.gameCollector.getTopBlocks();
   }

   public void clearGameEvents() {
      this.gameCollector.clearEvents();
   }

   public JsonObject getRequestLogs(int limit) {
      return this.loggingCollector.getRequestLogs(limit);
   }

   public JsonObject getErrorLogs(int limit, String severity) {
      return this.loggingCollector.getErrorLogs(limit, severity);
   }

   public JsonObject getPerformanceMetrics() {
      return this.loggingCollector.getPerformanceMetrics();
   }

   public JsonObject getUserActivity() {
      return this.loggingCollector.getUserActivity();
   }

   public JsonObject getServerLogs(int lines) {
      return this.loggingCollector.getServerLogs(lines);
   }

   public JsonObject getErrorStatistics() {
      return this.loggingCollector.getErrorStatistics();
   }

   public void clearLogs(String logType) {
      this.loggingCollector.clearLogs(logType);
   }

   public static void logRequest(String endpoint, String method, int statusCode, long responseTimeMs, String username) {
      LoggingDataCollector.logRequest(endpoint, method, statusCode, responseTimeMs, username);
   }

   public static void logError(String message, String severity, Exception exception) {
      LoggingDataCollector.logError(message, severity, exception);
   }

   public JsonObject getWorldData() {
      return this.serverCollector.getServerWorlds();
   }

   public JsonObject getEconomyData() {
      return new JsonObject();
   }

   private JsonObject getCachedOrCompute(String key, DataCollector.DataSupplier supplier, long cacheDuration) {
      DataCollector.CachedData cached = this.dataCache.get(key);
      long now = System.currentTimeMillis();
      if (cached != null && now - cached.timestamp < cacheDuration) {
         return cached.data;
      } else {
         JsonObject data = supplier.get();
         this.dataCache.put(key, new DataCollector.CachedData(data, now));
         return data;
      }
   }

   public void clearCache() {
      this.dataCache.clear();
      LOGGER.info("All cached data cleared");
   }

   public void clearCache(String key) {
      this.dataCache.remove(key);
      LOGGER.info("Cache cleared for key: {}", key);
   }

   private static class CachedData {
      final JsonObject data;
      final long timestamp;

      CachedData(JsonObject data, long timestamp) {
         this.data = data;
         this.timestamp = timestamp;
      }
   }

   @FunctionalInterface
   private interface DataSupplier {
      JsonObject get();
   }
}
