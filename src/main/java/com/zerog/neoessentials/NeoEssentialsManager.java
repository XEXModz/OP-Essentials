package com.zerog.neoessentials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zerog.neoessentials.api.economy.EconomyService;
import com.zerog.neoessentials.api.economy.EconomyServiceImpl;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoEssentialsManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentialsManager.class);
   private final Map<UUID, NeoEssentialsManager.PlayerData> playerDataMap = new ConcurrentHashMap<>();
   private EconomyService economyService = new EconomyServiceImpl(ResourceUtil.getDataPath("balances.json"));
   private static final String PLAYERDATA_DIR = "neoessentials/playerdata/";
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   public static NeoEssentialsManager getInstance() {
      return NeoEssentialsManager.SingletonHolder.INSTANCE;
   }

   private NeoEssentialsManager() {
   }

   public void registerCommand(Object command) {
   }

   public NeoEssentialsManager.PlayerData getPlayerData(UUID playerId) {
      return this.playerDataMap.computeIfAbsent(playerId, k -> new NeoEssentialsManager.PlayerData());
   }

   public void setEconomyService(EconomyService service) {
      this.economyService = service;
   }

   public EconomyService getEconomyService() {
      return this.economyService;
   }

   public void savePlayerData(UUID playerId) {
      NeoEssentialsManager.PlayerData data = this.playerDataMap.get(playerId);
      if (data != null) {
         try {
            Path dir = Paths.get("neoessentials/playerdata/");
            if (!Files.exists(dir)) {
               Files.createDirectories(dir);
            }

            File file = dir.resolve(playerId + ".json").toFile();

            try (Writer writer = new FileWriter(file)) {
               GSON.toJson(data, writer);
            }
         } catch (IOException var10) {
            LOGGER.error("Failed to save player data for {}", playerId, var10);
         }
      }
   }

   public void loadPlayerData(UUID playerId) {
      try {
         Path dir = Paths.get("neoessentials/playerdata/");
         File file = dir.resolve(playerId + ".json").toFile();
         if (!file.exists()) {
            return;
         }

         try (Reader reader = new FileReader(file)) {
            NeoEssentialsManager.PlayerData data = (NeoEssentialsManager.PlayerData)GSON.fromJson(reader, NeoEssentialsManager.PlayerData.class);
            this.playerDataMap.put(playerId, data);
         }
      } catch (IOException var9) {
         LOGGER.error("Failed to load player data for {}", playerId, var9);
      }
   }

   public void saveAllPlayerData() {
      for (UUID uuid : this.playerDataMap.keySet()) {
         this.savePlayerData(uuid);
      }
   }

   public void loadAllPlayerData() {
      try {
         Path dir = Paths.get("neoessentials/playerdata/");
         if (!Files.exists(dir)) {
            return;
         }

         try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
               try (Reader reader = new FileReader(p.toFile())) {
                  NeoEssentialsManager.PlayerData data = (NeoEssentialsManager.PlayerData)GSON.fromJson(reader, NeoEssentialsManager.PlayerData.class);
                  String fileName = p.getFileName().toString();
                  String uuidStr = fileName.substring(0, fileName.length() - 5);
                  UUID uuid = UUID.fromString(uuidStr);
                  this.playerDataMap.put(uuid, data);
               } catch (Exception var9) {
                  LOGGER.error("Failed to load individual player data file", var9);
               }
            });
         }
      } catch (IOException var7) {
         LOGGER.error("Failed to load all player data", var7);
      }
   }

   public static class PlayerData {
      public Map<String, Object> homes = new ConcurrentHashMap<>();
      public Map<String, Object> warps = new ConcurrentHashMap<>();
      public Map<String, Object> mail = new ConcurrentHashMap<>();
      private boolean vanishMode = false;
      private boolean godMode = false;
      private boolean flyMode = false;
      private String lastLocation = null;
      private boolean tpToggle = true;
      private boolean msgToggle = true;
      private final List<String> ignoreList = new ArrayList<>();

      public boolean isVanishMode() {
         return this.vanishMode;
      }

      public void setVanishMode(boolean vanish) {
         this.vanishMode = vanish;
      }

      public boolean isGodMode() {
         return this.godMode;
      }

      public void setGodMode(boolean god) {
         this.godMode = god;
      }

      public boolean isFlyMode() {
         return this.flyMode;
      }

      public void setFlyMode(boolean fly) {
         this.flyMode = fly;
      }

      public String getLastLocation() {
         return this.lastLocation;
      }

      public void setLastLocation(String location) {
         this.lastLocation = location;
      }

      public boolean isTpToggle() {
         return this.tpToggle;
      }

      public void setTpToggle(boolean enabled) {
         this.tpToggle = enabled;
      }

      public boolean isMsgToggle() {
         return this.msgToggle;
      }

      public void setMsgToggle(boolean enabled) {
         this.msgToggle = enabled;
      }

      public List<String> getIgnoreList() {
         return this.ignoreList;
      }

      public void addToIgnoreList(String player) {
         this.ignoreList.add(player);
      }

      public void removeFromIgnoreList(String player) {
         this.ignoreList.remove(player);
      }
   }

   private static class SingletonHolder {
      private static final NeoEssentialsManager INSTANCE = new NeoEssentialsManager();
   }
}
