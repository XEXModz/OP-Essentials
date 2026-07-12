package com.zerog.neoessentials.webdashboard.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerLocationTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerLocationTracker.class);
   private static PlayerLocationTracker INSTANCE;
   private final Map<UUID, PlayerLocationTracker.PlayerLocation> playerLocations = new ConcurrentHashMap<>();
   private final List<PlayerLocationTracker.LocationUpdateListener> listeners = new ArrayList<>();

   private PlayerLocationTracker() {
   }

   public static PlayerLocationTracker getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PlayerLocationTracker();
      }

      return INSTANCE;
   }

   public void updatePlayerLocation(ServerPlayer player) {
      try {
         PlayerLocationTracker.PlayerLocation location = new PlayerLocationTracker.PlayerLocation();
         location.uuid = player.getUUID();
         location.name = player.getGameProfile().getName();
         location.x = player.getX();
         location.y = player.getY();
         location.z = player.getZ();
         location.yaw = player.getYRot();
         location.pitch = player.getXRot();
         location.health = player.getHealth();
         location.maxHealth = player.getMaxHealth();
         location.dimension = player.level().dimension().location().toString();
         location.blockX = player.blockPosition().getX();
         location.blockY = player.blockPosition().getY();
         location.blockZ = player.blockPosition().getZ();
         location.chunkX = player.chunkPosition().x;
         location.chunkZ = player.chunkPosition().z;
         location.timestamp = System.currentTimeMillis();
         this.playerLocations.put(player.getUUID(), location);
         this.notifyListeners(location);
      } catch (Exception var3) {
         LOGGER.error("Error updating player location: {}", player.getGameProfile().getName(), var3);
      }
   }

   public void removePlayer(UUID playerId) {
      this.playerLocations.remove(playerId);
   }

   public Collection<PlayerLocationTracker.PlayerLocation> getAllPlayerLocations() {
      return new ArrayList<>(this.playerLocations.values());
   }

   public List<PlayerLocationTracker.PlayerLocation> getPlayerLocationsInDimension(String dimension) {
      List<PlayerLocationTracker.PlayerLocation> locations = new ArrayList<>();

      for (PlayerLocationTracker.PlayerLocation loc : this.playerLocations.values()) {
         if (loc.dimension.equals(dimension)) {
            locations.add(loc);
         }
      }

      return locations;
   }

   public PlayerLocationTracker.PlayerLocation getPlayerLocation(UUID playerId) {
      return this.playerLocations.get(playerId);
   }

   public Set<String> getTrackedDimensions() {
      Set<String> dimensions = new HashSet<>();

      for (PlayerLocationTracker.PlayerLocation loc : this.playerLocations.values()) {
         dimensions.add(loc.dimension);
      }

      return dimensions;
   }

   public JsonObject getPlayerLocationsJson() {
      JsonObject response = new JsonObject();
      response.addProperty("timestamp", System.currentTimeMillis());
      response.addProperty("playerCount", this.playerLocations.size());
      JsonArray playersArray = new JsonArray();

      for (PlayerLocationTracker.PlayerLocation loc : this.playerLocations.values()) {
         JsonObject playerObj = new JsonObject();
         playerObj.addProperty("uuid", loc.uuid.toString());
         playerObj.addProperty("name", loc.name);
         playerObj.addProperty("x", loc.x);
         playerObj.addProperty("y", loc.y);
         playerObj.addProperty("z", loc.z);
         playerObj.addProperty("blockX", loc.blockX);
         playerObj.addProperty("blockY", loc.blockY);
         playerObj.addProperty("blockZ", loc.blockZ);
         playerObj.addProperty("chunkX", loc.chunkX);
         playerObj.addProperty("chunkZ", loc.chunkZ);
         playerObj.addProperty("yaw", loc.yaw);
         playerObj.addProperty("pitch", loc.pitch);
         playerObj.addProperty("health", loc.health);
         playerObj.addProperty("maxHealth", loc.maxHealth);
         playerObj.addProperty("dimension", loc.dimension);
         playerObj.addProperty("timestamp", loc.timestamp);
         playersArray.add(playerObj);
      }

      response.add("players", playersArray);
      return response;
   }

   public JsonObject getPlayerLocationsJson(String dimension) {
      JsonObject response = new JsonObject();
      response.addProperty("timestamp", System.currentTimeMillis());
      response.addProperty("dimension", dimension);
      List<PlayerLocationTracker.PlayerLocation> locations = this.getPlayerLocationsInDimension(dimension);
      response.addProperty("playerCount", locations.size());
      JsonArray playersArray = new JsonArray();

      for (PlayerLocationTracker.PlayerLocation loc : locations) {
         JsonObject playerObj = new JsonObject();
         playerObj.addProperty("uuid", loc.uuid.toString());
         playerObj.addProperty("name", loc.name);
         playerObj.addProperty("x", loc.x);
         playerObj.addProperty("y", loc.y);
         playerObj.addProperty("z", loc.z);
         playerObj.addProperty("blockX", loc.blockX);
         playerObj.addProperty("blockY", loc.blockY);
         playerObj.addProperty("blockZ", loc.blockZ);
         playerObj.addProperty("chunkX", loc.chunkX);
         playerObj.addProperty("chunkZ", loc.chunkZ);
         playerObj.addProperty("yaw", loc.yaw);
         playerObj.addProperty("pitch", loc.pitch);
         playerObj.addProperty("health", loc.health);
         playerObj.addProperty("maxHealth", loc.maxHealth);
         playerObj.addProperty("timestamp", loc.timestamp);
         playersArray.add(playerObj);
      }

      response.add("players", playersArray);
      return response;
   }

   public void addListener(PlayerLocationTracker.LocationUpdateListener listener) {
      this.listeners.add(listener);
   }

   public void removeListener(PlayerLocationTracker.LocationUpdateListener listener) {
      this.listeners.remove(listener);
   }

   private void notifyListeners(PlayerLocationTracker.PlayerLocation location) {
      for (PlayerLocationTracker.LocationUpdateListener listener : this.listeners) {
         try {
            listener.onLocationUpdate(location);
         } catch (Exception var5) {
            LOGGER.error("Error notifying location listener", var5);
         }
      }
   }

   public interface LocationUpdateListener {
      void onLocationUpdate(PlayerLocationTracker.PlayerLocation var1);
   }

   public static class PlayerLocation {
      public UUID uuid;
      public String name;
      public double x;
      public double y;
      public double z;
      public int blockX;
      public int blockY;
      public int blockZ;
      public int chunkX;
      public int chunkZ;
      public float yaw;
      public float pitch;
      public float health;
      public float maxHealth;
      public String dimension;
      public long timestamp;
   }
}
