package com.zerog.neoessentials.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerDataStore {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataStore.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final String dataType;
   private final File dataDirectory;
   private final Map<UUID, JsonObject> cache = new ConcurrentHashMap<>();
   private final Set<UUID> dirtyEntries = ConcurrentHashMap.newKeySet();

   public PlayerDataStore(String dataType) {
      this.dataType = dataType;
      this.dataDirectory = new File("neoessentials/", "playerdata/" + dataType);
      if (!this.dataDirectory.exists() && this.dataDirectory.mkdirs()) {
         LOGGER.info("Created playerdata directory for {}: {}", dataType, this.dataDirectory.getPath());
      }
   }

   public JsonObject load(UUID playerId) {
      if (this.cache.containsKey(playerId)) {
         return this.cache.get(playerId);
      } else {
         File playerFile = this.getPlayerFile(playerId);
         JsonObject data;
         if (playerFile.exists()) {
            try (FileReader reader = new FileReader(playerFile)) {
               data = JsonParser.parseReader(reader).getAsJsonObject();
               LOGGER.debug("Loaded {} data for player {}", this.dataType, playerId);
            } catch (Exception var9) {
               LOGGER.error("Failed to load {} data for player {}: {}", new Object[]{this.dataType, playerId, var9.getMessage(), var9});
               data = new JsonObject();
            }
         } else {
            data = new JsonObject();
            LOGGER.debug("No {} data found for player {}, using empty data", this.dataType, playerId);
         }

         this.cache.put(playerId, data);
         return data;
      }
   }

   public void save(UUID playerId, JsonObject data) {
      this.cache.put(playerId, data);
      this.dirtyEntries.add(playerId);
      this.flush(playerId);
   }

   public void flush(UUID playerId) {
      if (this.dirtyEntries.contains(playerId)) {
         JsonObject data = this.cache.get(playerId);
         if (data != null) {
            File playerFile = this.getPlayerFile(playerId);
            File tempFile = new File(playerFile.getAbsolutePath() + ".tmp");

            try {
               try (FileWriter writer = new FileWriter(tempFile)) {
                  GSON.toJson(data, writer);
               }

               Files.move(tempFile.toPath(), playerFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               this.dirtyEntries.remove(playerId);
               LOGGER.debug("Saved {} data for player {}", this.dataType, playerId);
            } catch (Exception var10) {
               LOGGER.error("Failed to save {} data for player {}: {}", new Object[]{this.dataType, playerId, var10.getMessage(), var10});
               if (tempFile.exists()) {
                  tempFile.delete();
               }
            }
         }
      }
   }

   public void flushAll() {
      if (!this.dirtyEntries.isEmpty()) {
         LOGGER.info("Flushing {} dirty {} entries...", this.dirtyEntries.size(), this.dataType);

         for (UUID playerId : new HashSet<>(this.dirtyEntries)) {
            this.flush(playerId);
         }

         LOGGER.info("Flushed all {} data", this.dataType);
      }
   }

   public boolean delete(UUID playerId) {
      this.cache.remove(playerId);
      this.dirtyEntries.remove(playerId);
      File playerFile = this.getPlayerFile(playerId);
      if (playerFile.exists()) {
         boolean deleted = playerFile.delete();
         if (deleted) {
            LOGGER.info("Deleted {} data for player {}", this.dataType, playerId);
         }

         return deleted;
      } else {
         return true;
      }
   }

   public boolean hasData(UUID playerId) {
      return this.getPlayerFile(playerId).exists() || this.cache.containsKey(playerId);
   }

   public Set<UUID> getAllPlayerIds() {
      Set<UUID> playerIds = new HashSet<>();
      playerIds.addAll(this.cache.keySet());
      File[] files = this.dataDirectory.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
         for (File file : files) {
            try {
               String fileName = file.getName();
               String uuidStr = fileName.substring(0, fileName.length() - 5);
               UUID uuid = UUID.fromString(uuidStr);
               playerIds.add(uuid);
            } catch (Exception var10) {
               LOGGER.warn("Invalid player data file: {}", file.getName());
            }
         }
      }

      return playerIds;
   }

   private File getPlayerFile(UUID playerId) {
      return new File(this.dataDirectory, playerId.toString() + ".json");
   }

   public void unload(UUID playerId) {
      this.flush(playerId);
      this.cache.remove(playerId);
      LOGGER.debug("Unloaded {} data for player {} from cache", this.dataType, playerId);
   }

   public void clearAll() {
      this.cache.clear();
      this.dirtyEntries.clear();
      File[] files = this.dataDirectory.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
         for (File file : files) {
            file.delete();
         }
      }

      LOGGER.warn("Cleared all {} data", this.dataType);
   }

   public int getCacheSize() {
      return this.cache.size();
   }

   public int getTotalPlayers() {
      File[] files = this.dataDirectory.listFiles((dir, name) -> name.endsWith(".json"));
      return files != null ? files.length : 0;
   }

   public String getStatistics() {
      return String.format(
         "%s DataStore: %d players total, %d in cache, %d dirty", this.dataType, this.getTotalPlayers(), this.getCacheSize(), this.dirtyEntries.size()
      );
   }
}
