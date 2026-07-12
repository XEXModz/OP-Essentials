package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber
public class GameDataCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(GameDataCollector.class);
   private static final int MAX_EVENTS = 1000;
   private final MinecraftServer server;
   private static final ConcurrentLinkedQueue<GameDataCollector.GameEvent> eventQueue = new ConcurrentLinkedQueue<>();

   public GameDataCollector(MinecraftServer server) {
      this.server = server;
   }

   public JsonObject getGameEvents(int limit) {
      JsonObject response = new JsonObject();
      JsonArray events = new JsonArray();
      List<GameDataCollector.GameEvent> recentEvents = new ArrayList<>();

      for (GameDataCollector.GameEvent event : eventQueue) {
         if (recentEvents.size() >= limit) {
            break;
         }

         recentEvents.add(event);
      }

      recentEvents.forEach(eventx -> events.add(eventx.toJson()));
      response.add("events", events);
      response.addProperty("count", events.size());
      response.addProperty("total", eventQueue.size());
      return response;
   }

   public JsonObject getGameStatistics() {
      JsonObject stats = new JsonObject();
      JsonObject players = new JsonObject();
      players.addProperty("totalPlayers", 0);
      players.addProperty("totalPlayTime", 0);
      stats.add("players", players);
      JsonObject blocks = new JsonObject();
      blocks.addProperty("totalBroken", 0);
      blocks.addProperty("totalPlaced", 0);
      stats.add("blocks", blocks);
      JsonObject combat = new JsonObject();
      combat.addProperty("totalKills", 0);
      combat.addProperty("totalDeaths", 0);
      stats.add("combat", combat);
      return stats;
   }

   public JsonObject getGameActivity() {
      JsonObject activity = new JsonObject();
      long oneHourAgo = Instant.now().toEpochMilli() - 3600000L;
      int playerJoins = 0;
      int playerLeaves = 0;
      int blockBreaks = 0;
      int blockPlaces = 0;
      int deaths = 0;

      for (GameDataCollector.GameEvent event : eventQueue) {
         if (event.timestamp >= oneHourAgo) {
            String var11 = event.type;
            switch (var11) {
               case "player.join":
                  playerJoins++;
                  break;
               case "player.leave":
                  playerLeaves++;
                  break;
               case "block.break":
                  blockBreaks++;
                  break;
               case "block.place":
                  blockPlaces++;
                  break;
               case "player.death":
                  deaths++;
            }
         }
      }

      activity.addProperty("playerJoins", playerJoins);
      activity.addProperty("playerLeaves", playerLeaves);
      activity.addProperty("blockBreaks", blockBreaks);
      activity.addProperty("blockPlaces", blockPlaces);
      activity.addProperty("deaths", deaths);
      activity.addProperty("period", "last_hour");
      return activity;
   }

   public JsonObject getTopBlocks() {
      JsonObject response = new JsonObject();
      JsonArray topBroken = new JsonArray();
      JsonArray topPlaced = new JsonArray();
      response.add("topBroken", topBroken);
      response.add("topPlaced", topPlaced);
      return response;
   }

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      addEvent(
         new GameDataCollector.GameEvent("player.join", event.getEntity().getName().getString() + " joined the game", event.getEntity().getUUID().toString())
      );
   }

   @SubscribeEvent
   public static void onPlayerLeave(PlayerLoggedOutEvent event) {
      addEvent(
         new GameDataCollector.GameEvent("player.leave", event.getEntity().getName().getString() + " left the game", event.getEntity().getUUID().toString())
      );
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      String blockName = event.getState().getBlock().getName().getString();
      String playerName = event.getPlayer().getName().getString();
      JsonObject data = new JsonObject();
      data.addProperty("block", blockName);
      data.addProperty("x", event.getPos().getX());
      data.addProperty("y", event.getPos().getY());
      data.addProperty("z", event.getPos().getZ());
      addEvent(new GameDataCollector.GameEvent("block.break", playerName + " broke " + blockName, event.getPlayer().getUUID().toString(), data));
   }

   private static void addEvent(GameDataCollector.GameEvent event) {
      eventQueue.offer(event);

      while (eventQueue.size() > 1000) {
         eventQueue.poll();
      }
   }

   public void clearEvents() {
      eventQueue.clear();
      LOGGER.info("Game event history cleared");
   }

   public static class GameEvent {
      public final String type;
      public final String message;
      public final String playerUuid;
      public final long timestamp;
      public final JsonObject data;

      public GameEvent(String type, String message, String playerUuid) {
         this(type, message, playerUuid, new JsonObject());
      }

      public GameEvent(String type, String message, String playerUuid, JsonObject data) {
         this.type = type;
         this.message = message;
         this.playerUuid = playerUuid;
         this.timestamp = Instant.now().toEpochMilli();
         this.data = data;
      }

      public JsonObject toJson() {
         JsonObject json = new JsonObject();
         json.addProperty("type", this.type);
         json.addProperty("message", this.message);
         json.addProperty("playerUuid", this.playerUuid);
         json.addProperty("timestamp", this.timestamp);
         if (this.data != null && !this.data.isEmpty()) {
            json.add("data", this.data);
         }

         return json;
      }
   }
}
