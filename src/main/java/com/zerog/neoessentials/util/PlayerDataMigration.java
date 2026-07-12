package com.zerog.neoessentials.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerDataMigration {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataMigration.class);

   public static int migrateToPlayerData(String oldFileName, String dataType) {
      File oldFile = ResourceUtil.getConfigFile(oldFileName);
      if (!oldFile.exists()) {
         LOGGER.debug("No old {} file to migrate", oldFileName);
         return 0;
      } else {
         LOGGER.info("════════════════════════════════════════════════════════");
         LOGGER.info("Migrating {} to per-player storage...", dataType);
         LOGGER.info("════════════════════════════════════════════════════════");

         try {
            FileReader reader = new FileReader(oldFile);

            JsonObject oldData;
            try {
               oldData = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Throwable var13) {
               try {
                  reader.close();
               } catch (Throwable var11) {
                  var13.addSuppressed(var11);
               }

               throw var13;
            }

            reader.close();
            if (oldData.keySet().isEmpty()) {
               LOGGER.info("Old {} file is empty, skipping migration", oldFileName);
               return 0;
            } else {
               PlayerDataStore store = new PlayerDataStore(dataType);
               int migratedCount = 0;
               int failedCount = 0;

               for (String playerIdStr : oldData.keySet()) {
                  try {
                     UUID playerId = UUID.fromString(playerIdStr);
                     JsonObject playerData = oldData.getAsJsonObject(playerIdStr);
                     store.save(playerId, playerData);
                     migratedCount++;
                     LOGGER.debug("Migrated {} data for player {}", dataType, playerId);
                  } catch (Exception var12) {
                     LOGGER.error("Failed to migrate {} for player {}: {}", new Object[]{dataType, playerIdStr, var12.getMessage()});
                     failedCount++;
                  }
               }

               store.flushAll();
               File backupFile = new File(oldFile.getAbsolutePath() + ".backup");
               Files.copy(oldFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               LOGGER.info("✓ Backed up old {} file to {}", oldFileName, backupFile.getName());
               File migratedFile = new File(oldFile.getAbsolutePath() + ".migrated");
               Files.move(oldFile.toPath(), migratedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               LOGGER.info("✓ Renamed old {} file to {} (safe to delete manually)", oldFileName, migratedFile.getName());
               LOGGER.info("════════════════════════════════════════════════════════");
               LOGGER.info("✓ Migration complete!");
               LOGGER.info("  - {} migrated: {} players", dataType, migratedCount);
               if (failedCount > 0) {
                  LOGGER.warn("  - Failed: {} players", failedCount);
               }

               LOGGER.info("  - Old file backed up: {}", backupFile.getName());
               LOGGER.info("  - New location: config/neoessentials/playerdata/{}/", dataType);
               LOGGER.info("════════════════════════════════════════════════════════");
               return migratedCount;
            }
         } catch (Exception var14) {
            LOGGER.error("Failed to migrate {}: {}", new Object[]{oldFileName, var14.getMessage(), var14});
            return 0;
         }
      }
   }

   public static void migrateAll() {
      LOGGER.info("Checking for data migrations...");
      int totalMigrated = 0;
      totalMigrated += migrateToPlayerData("homes.json", "homes");
      if (totalMigrated > 0) {
         LOGGER.info("Total players migrated across all systems: {}", totalMigrated);
      } else {
         LOGGER.debug("No migrations needed");
      }
   }

   public static boolean needsMigration(String oldFileName) {
      File oldFile = ResourceUtil.getConfigFile(oldFileName);
      File migratedFile = new File(oldFile.getAbsolutePath() + ".migrated");
      return oldFile.exists() && !migratedFile.exists();
   }
}
