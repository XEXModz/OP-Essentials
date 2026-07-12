package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules.GameRuleTypeVisitor;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.GameRules.Type;
import net.minecraft.world.level.GameRules.Value;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerDataCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(ServerDataCollector.class);
   private final MinecraftServer server;
   private final DecimalFormat df = new DecimalFormat("#.##");

   public ServerDataCollector(MinecraftServer server) {
      this.server = server;
      LOGGER.debug("ServerDataCollector initialized");
   }

   public JsonObject getServerProfile() {
      LOGGER.debug("=== Collecting Server Profile Data ===");
      JsonObject profile = new JsonObject();

      try {
         profile.addProperty("serverName", this.server.getServerModName());
         LOGGER.debug("Server name: {}", this.server.getServerModName());
         profile.addProperty("motd", this.server.getMotd());
         profile.addProperty("minecraftVersion", this.server.getServerVersion());
         LOGGER.debug("Minecraft version: {}", this.server.getServerVersion());
         String neoforgeVersion = "Unknown";

         try {
            Optional<? extends ModContainer> neoforgeModOpt = ModList.get().getModContainerById("neoforge");
            if (neoforgeModOpt.isPresent()) {
               neoforgeVersion = "NeoForge " + neoforgeModOpt.get().getModInfo().getVersion().toString();
            }
         } catch (Exception var6) {
            LOGGER.warn("Could not determine NeoForge version: {}", var6.getMessage());
            neoforgeVersion = "NeoForge (version unavailable)";
         }

         profile.addProperty("modVersion", neoforgeVersion);
         profile.addProperty("neoforgeVersion", neoforgeVersion);
         profile.addProperty("gameVersion", "1.21.1");
         profile.addProperty("difficulty", this.server.getWorldData().getDifficulty().getKey());
         profile.addProperty("hardcore", this.server.getWorldData().isHardcore());
         profile.addProperty("maxPlayers", this.server.getMaxPlayers());
         profile.addProperty("pvpEnabled", this.server.isPvpAllowed());
         profile.addProperty("onlineMode", this.server.usesAuthentication());
         profile.addProperty("commandBlocksEnabled", this.server.isCommandBlockEnabled());
         JsonArray mods = new JsonArray();

         try {
            ModList.get().getMods().forEach(modInfo -> {
               JsonObject mod = new JsonObject();
               mod.addProperty("id", modInfo.getModId());
               mod.addProperty("name", modInfo.getDisplayName());
               mod.addProperty("version", modInfo.getVersion().toString());
               mods.add(mod);
            });
            profile.add("mods", mods);
            profile.addProperty("modCount", mods.size());
            profile.addProperty("modsLoaded", mods.size());
            LOGGER.debug("Successfully collected profile data: {} mods loaded", mods.size());
         } catch (Exception var5) {
            LOGGER.error("Error collecting mod list", var5);
            profile.add("mods", new JsonArray());
            profile.addProperty("modCount", 0);
            profile.addProperty("modsLoaded", 0);
         }

         LOGGER.debug("=== Server Profile Data Collection Complete ===");
         return profile;
      } catch (Exception var7) {
         LOGGER.error("Critical error collecting server profile", var7);
         if (!profile.has("serverName")) {
            profile.addProperty("serverName", "Unknown");
         }

         if (!profile.has("minecraftVersion")) {
            profile.addProperty("minecraftVersion", "Unknown");
         }

         if (!profile.has("maxPlayers")) {
            profile.addProperty("maxPlayers", 20);
         }

         profile.addProperty("error", "Partial data due to error: " + var7.getMessage());
         return profile;
      }
   }

   public JsonObject getServerStatistics() {
      LOGGER.debug("=== Collecting Server Statistics ===");
      JsonObject stats = new JsonObject();

      try {
         double avgTickTime = (double)this.server.getAverageTickTimeNanos() / 1000000.0;
         double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTickTime));
         stats.addProperty("tps", this.df.format(tps));
         stats.addProperty("averageTickTime", this.df.format(avgTickTime));
         stats.addProperty("tpsPercent", this.df.format(tps / 20.0 * 100.0));
         LOGGER.debug("TPS: {} ({} ms avg tick time)", this.df.format(tps), this.df.format(avgTickTime));
         Runtime runtime = Runtime.getRuntime();
         long maxMemory = runtime.maxMemory();
         long totalMemory = runtime.totalMemory();
         long freeMemory = runtime.freeMemory();
         long usedMemory = totalMemory - freeMemory;
         JsonObject memory = new JsonObject();
         memory.addProperty("used", this.formatBytes(usedMemory));
         memory.addProperty("free", this.formatBytes(freeMemory));
         memory.addProperty("allocated", this.formatBytes(totalMemory));
         memory.addProperty("max", this.formatBytes(maxMemory));
         memory.addProperty("usedMB", usedMemory / 1048576L);
         memory.addProperty("maxMB", maxMemory / 1048576L);
         memory.addProperty("usedPercent", this.df.format((double)usedMemory / (double)maxMemory * 100.0));
         stats.add("memory", memory);
         LOGGER.debug(
            "Memory: {} / {} ({} MB / {} MB)",
            new Object[]{this.formatBytes(usedMemory), this.formatBytes(maxMemory), usedMemory / 1048576L, maxMemory / 1048576L}
         );
         OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
         JsonObject cpu = new JsonObject();
         cpu.addProperty("processors", osBean.getAvailableProcessors());
         cpu.addProperty("loadAverage", this.df.format(osBean.getSystemLoadAverage()));
         stats.add("cpu", cpu);
         LOGGER.debug("CPU: {} processors, load avg: {}", osBean.getAvailableProcessors(), this.df.format(osBean.getSystemLoadAverage()));
         int playerCount = this.server.getPlayerCount();
         int maxPlayers = this.server.getMaxPlayers();
         stats.addProperty("playersOnline", playerCount);
         stats.addProperty("playersMax", maxPlayers);
         LOGGER.debug("Players: {} / {}", playerCount, maxPlayers);
         int worldCount = 0;

         for (ServerLevel level : this.server.getAllLevels()) {
            worldCount++;
         }

         stats.addProperty("worldsLoaded", worldCount);
         LOGGER.debug("Worlds loaded: {}", worldCount);
         JsonArray worldChunks = new JsonArray();
         int[] totalLoadedChunks = new int[]{0};
         this.server.getAllLevels().forEach(level -> {
            JsonObject worldChunk = new JsonObject();
            worldChunk.addProperty("dimension", level.dimension().location().toString());

            int loadedChunks;
            try {
               ServerChunkCache chunkSource = level.getChunkSource();
               loadedChunks = chunkSource.getLoadedChunksCount();
               LOGGER.debug("Loaded chunks for {}: {}", level.dimension().location(), loadedChunks);
            } catch (Exception var6x) {
               LOGGER.debug("Failed to count chunks for statistics: {}", var6x.getMessage());
               loadedChunks = 0;
            }

            worldChunk.addProperty("loadedChunks", loadedChunks);
            worldChunks.add(worldChunk);
            totalLoadedChunks[0] += loadedChunks;
         });
         stats.add("chunks", worldChunks);
         stats.addProperty("totalLoadedChunks", totalLoadedChunks[0]);
         LOGGER.debug("Total chunks loaded: {}", totalLoadedChunks[0]);
         LOGGER.debug("=== Server Statistics Collection Complete ===");
         return stats;
      } catch (Exception var23) {
         LOGGER.error("Error collecting server statistics", var23);
         stats.addProperty("tps", "20.0");
         stats.addProperty("playersOnline", 0);
         stats.addProperty("playersMax", 20);
         stats.addProperty("error", "Error collecting statistics: " + var23.getMessage());
         return stats;
      }
   }

   public JsonObject getServerStatus() {
      LOGGER.debug("=== Collecting Server Status ===");
      JsonObject status = new JsonObject();

      try {
         boolean isOnline = !this.server.isStopped();
         int playerCount = this.server.getPlayerCount();
         int maxPlayers = this.server.getMaxPlayers();
         status.addProperty("online", isOnline);
         status.addProperty("playersOnline", playerCount);
         status.addProperty("playersMax", maxPlayers);
         LOGGER.debug("Server status: online={}, players={}/{}", new Object[]{isOnline, playerCount, maxPlayers});
         long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
         status.addProperty("uptimeMillis", uptimeMillis);
         status.addProperty("uptimeFormatted", this.formatUptime(uptimeMillis));
         LOGGER.debug("Uptime: {} ms ({})", uptimeMillis, this.formatUptime(uptimeMillis));
         double avgTickTime = (double)this.server.getAverageTickTimeNanos() / 1000000.0;
         double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTickTime));
         status.addProperty("tps", this.df.format(tps));
         LOGGER.debug("TPS: {}", this.df.format(tps));
         String health = "healthy";
         if (tps < 15.0) {
            health = "struggling";
         }

         if (tps < 10.0) {
            health = "critical";
         }

         status.addProperty("health", health);
         LOGGER.debug("Health: {}", health);
         LOGGER.debug("=== Server Status Collection Complete ===");
         return status;
      } catch (Exception var12) {
         LOGGER.error("Error collecting server status", var12);
         status.addProperty("online", true);
         status.addProperty("playersOnline", 0);
         status.addProperty("playersMax", 20);
         status.addProperty("tps", "20.0");
         status.addProperty("health", "unknown");
         status.addProperty("error", "Error: " + var12.getMessage());
         return status;
      }
   }

   public JsonObject getServerHealth() {
      JsonObject health = new JsonObject();
      double avgTickTime = (double)this.server.getAverageTickTimeNanos() / 1000000.0;
      double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTickTime));
      JsonObject tpsHealth = new JsonObject();
      tpsHealth.addProperty("value", this.df.format(tps));
      tpsHealth.addProperty("status", tps >= 18.0 ? "good" : (tps >= 15.0 ? "warning" : "critical"));
      tpsHealth.addProperty("percentage", this.df.format(tps / 20.0 * 100.0));
      health.add("tps", tpsHealth);
      Runtime runtime = Runtime.getRuntime();
      long maxMemory = runtime.maxMemory();
      long usedMemory = runtime.totalMemory() - runtime.freeMemory();
      double memoryPercent = (double)usedMemory / (double)maxMemory * 100.0;
      JsonObject memoryHealth = new JsonObject();
      memoryHealth.addProperty("used", this.formatBytes(usedMemory));
      memoryHealth.addProperty("max", this.formatBytes(maxMemory));
      memoryHealth.addProperty("percentage", this.df.format(memoryPercent));
      memoryHealth.addProperty("status", memoryPercent < 70.0 ? "good" : (memoryPercent < 85.0 ? "warning" : "critical"));
      health.add("memory", memoryHealth);
      String overallStatus = "healthy";
      if (tps < 15.0 || memoryPercent > 85.0) {
         overallStatus = "warning";
      }

      if (tps < 10.0 || memoryPercent > 95.0) {
         overallStatus = "critical";
      }

      health.addProperty("overall", overallStatus);
      return health;
   }

   public JsonObject getServerWorlds() {
      LOGGER.debug("=== Starting getServerWorlds data collection ===");
      JsonObject worlds = new JsonObject();
      JsonArray worldsList = new JsonArray();
      LOGGER.debug("Total players online: {}", this.server.getPlayerList().getPlayers().size());

      for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
         LOGGER.debug("  - Player: {}, Dimension: {}", p.getName().getString(), p.level().dimension().location());
      }

      this.server.getAllLevels().forEach(level -> {
         JsonObject world = new JsonObject();
         String dimensionKey = level.dimension().location().toString();
         LOGGER.debug("Processing dimension: {}", dimensionKey);
         world.addProperty("dimension", dimensionKey);
         world.addProperty("name", this.getDimensionDisplayName(dimensionKey));
         world.addProperty("difficulty", level.getDifficulty().getKey());
         int playersInDimension = 0;

         for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            String playerDim = player.level().dimension().location().toString();
            boolean matches = playerDim.equals(dimensionKey);
            LOGGER.debug("  Checking player {}: dimension={}, matches={}", new Object[]{player.getName().getString(), playerDim, matches});
            if (matches) {
               playersInDimension++;
            }
         }

         world.addProperty("playersInWorld", playersInDimension);
         LOGGER.debug("  Final player count for {}: {}", dimensionKey, playersInDimension);

         int loadedChunks;
         try {
            ServerChunkCache chunkSource = level.getChunkSource();
            loadedChunks = chunkSource.getLoadedChunksCount();
            LOGGER.debug("  Loaded chunks for {}: {}", dimensionKey, loadedChunks);
         } catch (Exception var11) {
            LOGGER.warn("  Failed to count chunks for {}: {}", dimensionKey, var11.getMessage());
            loadedChunks = 0;
         }

         world.addProperty("loadedChunks", loadedChunks);
         int entityCount = 0;

         try {
            for (Entity entity : level.getAllEntities()) {
               entityCount++;
            }

            LOGGER.debug("  Total entities in {}: {}", dimensionKey, entityCount);
         } catch (Exception var12) {
            LOGGER.warn("  Failed to count entities for {}: {}", dimensionKey, var12.getMessage());
            entityCount = 0;
         }

         world.addProperty("entities", entityCount);
         world.addProperty("time", level.getDayTime());
         world.addProperty("raining", level.isRaining());
         world.addProperty("thundering", level.isThundering());
         JsonObject spawn = new JsonObject();
         spawn.addProperty("x", level.getSharedSpawnPos().getX());
         spawn.addProperty("y", level.getSharedSpawnPos().getY());
         spawn.addProperty("z", level.getSharedSpawnPos().getZ());
         world.add("spawn", spawn);
         worldsList.add(world);
         LOGGER.debug("Completed processing dimension: {}", dimensionKey);
      });
      worlds.add("worlds", worldsList);
      worlds.addProperty("count", worldsList.size());
      LOGGER.debug("=== Completed getServerWorlds data collection ===");
      return worlds;
   }

   public JsonObject getServerConfig() {
      JsonObject config = new JsonObject();
      config.addProperty("viewDistance", this.server.getPlayerList().getViewDistance());
      config.addProperty("simulationDistance", this.server.getPlayerList().getSimulationDistance());
      final ServerLevel overworld = this.server.getLevel(Level.OVERWORLD);
      if (overworld != null) {
         final JsonObject gameRules = new JsonObject();
         GameRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
            public <T extends Value<T>> void visit(Key<T> key, Type<T> type) {
               gameRules.addProperty(key.getId(), overworld.getGameRules().getRule(key).toString());
            }
         });
         config.add("gameRules", gameRules);
      }

      return config;
   }

   public JsonObject getServerPerformance() {
      JsonObject performance = new JsonObject();
      double avgTickTime = (double)this.server.getAverageTickTimeNanos() / 1000000.0;
      double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTickTime));
      performance.addProperty("currentTPS", this.df.format(tps));
      performance.addProperty("averageTickTime", this.df.format(avgTickTime));
      return performance;
   }

   private String formatBytes(long bytes) {
      if (bytes < 1024L) {
         return bytes + " B";
      } else {
         int exp = (int)(Math.log((double)bytes) / Math.log(1024.0));
         String pre = "KMGTPE".charAt(exp - 1) + "";
         return String.format("%.2f %sB", (double)bytes / Math.pow(1024.0, (double)exp), pre);
      }
   }

   private String formatUptime(long uptimeMillis) {
      long seconds = uptimeMillis / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      long days = hours / 24L;
      if (days > 0L) {
         return String.format("%dd %dh %dm", days, hours % 24L, minutes % 60L);
      } else if (hours > 0L) {
         return String.format("%dh %dm %ds", hours, minutes % 60L, seconds % 60L);
      } else {
         return minutes > 0L ? String.format("%dm %ds", minutes, seconds % 60L) : String.format("%ds", seconds);
      }
   }

   private String getDimensionDisplayName(String dimensionKey) {
      return switch (dimensionKey) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:the_end" -> "End";
         default -> dimensionKey;
      };
   }
}
