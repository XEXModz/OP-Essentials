package com.zerog.neoessentials.webdashboard.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldInfoCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(WorldInfoCollector.class);
   private static WorldInfoCollector INSTANCE;
   private MinecraftServer server;

   private WorldInfoCollector() {
   }

   public static WorldInfoCollector getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WorldInfoCollector();
      }

      return INSTANCE;
   }

   public void setServer(MinecraftServer server) {
      this.server = server;
   }

   public JsonObject getWorldInfoJson() {
      if (this.server == null) {
         JsonObject error = new JsonObject();
         error.addProperty("error", "Server not initialized");
         return error;
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("timestamp", System.currentTimeMillis());
         JsonArray dimensionsArray = new JsonArray();

         for (ServerLevel level : this.server.getAllLevels()) {
            JsonObject dimObj = this.getDimensionInfo(level);
            dimensionsArray.add(dimObj);
         }

         response.add("dimensions", dimensionsArray);
         JsonObject serverInfo = new JsonObject();
         serverInfo.addProperty("motd", this.server.getMotd());
         serverInfo.addProperty("maxPlayers", this.server.getMaxPlayers());
         serverInfo.addProperty("onlinePlayers", this.server.getPlayerCount());
         serverInfo.addProperty("difficulty", this.server.getWorldData().getDifficulty().name());
         serverInfo.addProperty("hardcore", this.server.getWorldData().isHardcore());
         response.add("serverInfo", serverInfo);
         return response;
      }
   }

   public JsonObject getDimensionInfo(ServerLevel level) {
      JsonObject dimObj = new JsonObject();
      String dimensionKey = level.dimension().location().toString();
      dimObj.addProperty("dimension", dimensionKey);
      dimObj.addProperty("name", this.getDimensionName(dimensionKey));
      BlockPos spawnPos = level.getSharedSpawnPos();
      JsonObject spawnObj = new JsonObject();
      spawnObj.addProperty("x", spawnPos.getX());
      spawnObj.addProperty("y", spawnPos.getY());
      spawnObj.addProperty("z", spawnPos.getZ());
      dimObj.add("spawn", spawnObj);
      WorldBorder border = level.getWorldBorder();
      JsonObject borderObj = new JsonObject();
      borderObj.addProperty("centerX", border.getCenterX());
      borderObj.addProperty("centerZ", border.getCenterZ());
      borderObj.addProperty("size", border.getSize());
      borderObj.addProperty("damagePerBlock", border.getDamagePerBlock());
      borderObj.addProperty("warningDistance", border.getWarningBlocks());
      dimObj.add("worldBorder", borderObj);
      dimObj.addProperty("dayTime", level.getDayTime());
      dimObj.addProperty("gameTime", level.getGameTime());
      dimObj.addProperty("isRaining", level.isRaining());
      dimObj.addProperty("isThundering", level.isThundering());
      dimObj.addProperty("rainLevel", level.getRainLevel(1.0F));
      dimObj.addProperty("thunderLevel", level.getThunderLevel(1.0F));
      dimObj.addProperty("loadedChunks", level.getChunkSource().getLoadedChunksCount());
      dimObj.addProperty("hasSkyLight", level.dimensionType().hasSkyLight());
      dimObj.addProperty("hasCeiling", level.dimensionType().hasCeiling());
      dimObj.addProperty("ultraWarm", level.dimensionType().ultraWarm());
      dimObj.addProperty("natural", level.dimensionType().natural());
      dimObj.addProperty("minY", level.dimensionType().minY());
      dimObj.addProperty("maxY", level.dimensionType().minY() + level.dimensionType().height());
      dimObj.addProperty("height", level.dimensionType().height());
      return dimObj;
   }

   public JsonObject getDimensionInfoJson(String dimensionKey) {
      if (this.server == null) {
         JsonObject error = new JsonObject();
         error.addProperty("error", "Server not initialized");
         return error;
      } else {
         for (ServerLevel level : this.server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionKey)) {
               JsonObject response = new JsonObject();
               response.addProperty("timestamp", System.currentTimeMillis());
               response.add("dimension", this.getDimensionInfo(level));
               return response;
            }
         }

         JsonObject error = new JsonObject();
         error.addProperty("error", "Dimension not found: " + dimensionKey);
         return error;
      }
   }

   public JsonObject getDimensionsListJson() {
      if (this.server == null) {
         JsonObject error = new JsonObject();
         error.addProperty("error", "Server not initialized");
         return error;
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("timestamp", System.currentTimeMillis());
         JsonArray dimensionsArray = new JsonArray();

         for (ServerLevel level : this.server.getAllLevels()) {
            JsonObject dimObj = new JsonObject();
            String dimensionKey = level.dimension().location().toString();
            dimObj.addProperty("key", dimensionKey);
            dimObj.addProperty("name", this.getDimensionName(dimensionKey));
            dimObj.addProperty("playerCount", level.players().size());
            dimObj.addProperty("loadedChunks", level.getChunkSource().getLoadedChunksCount());
            dimensionsArray.add(dimObj);
         }

         response.add("dimensions", dimensionsArray);
         response.addProperty("dimensionCount", dimensionsArray.size());
         return response;
      }
   }

   public JsonObject getSpawnPointsJson() {
      if (this.server == null) {
         JsonObject error = new JsonObject();
         error.addProperty("error", "Server not initialized");
         return error;
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("timestamp", System.currentTimeMillis());
         JsonArray spawnsArray = new JsonArray();

         for (ServerLevel level : this.server.getAllLevels()) {
            JsonObject spawnObj = new JsonObject();
            String dimensionKey = level.dimension().location().toString();
            spawnObj.addProperty("dimension", dimensionKey);
            spawnObj.addProperty("name", this.getDimensionName(dimensionKey));
            BlockPos spawnPos = level.getSharedSpawnPos();
            spawnObj.addProperty("x", spawnPos.getX());
            spawnObj.addProperty("y", spawnPos.getY());
            spawnObj.addProperty("z", spawnPos.getZ());
            spawnsArray.add(spawnObj);
         }

         response.add("spawns", spawnsArray);
         return response;
      }
   }

   private String getDimensionName(String dimensionKey) {
      if (dimensionKey.contains("overworld")) {
         return "Overworld";
      } else if (dimensionKey.contains("nether")) {
         return "The Nether";
      } else if (dimensionKey.contains("end")) {
         return "The End";
      } else {
         int colonIndex = dimensionKey.lastIndexOf(58);
         if (colonIndex >= 0 && colonIndex < dimensionKey.length() - 1) {
            String name = dimensionKey.substring(colonIndex + 1);
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            return name.replace('_', ' ');
         } else {
            return dimensionKey;
         }
      }
   }
}
