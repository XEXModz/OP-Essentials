package com.zerog.neoessentials.webdashboard.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerSessionTracker {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSessionTracker.class);
   private static PlayerSessionTracker INSTANCE;
   private final Map<UUID, PlayerSessionTracker.PlayerSession> activeSessions = new ConcurrentHashMap<>();
   private final List<PlayerSessionTracker.PlayerSession> historicalSessions = new ArrayList<>();

   private PlayerSessionTracker() {
   }

   public static PlayerSessionTracker getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PlayerSessionTracker();
      }

      return INSTANCE;
   }

   public void trackPlayerJoin(UUID playerUuid, String playerName) {
      PlayerSessionTracker.PlayerSession session = new PlayerSessionTracker.PlayerSession(playerUuid, playerName, System.currentTimeMillis());
      this.activeSessions.put(playerUuid, session);
      LOGGER.debug("Started tracking session for player: {} ({})", playerName, playerUuid);
   }

   public void trackPlayerLeave(UUID playerUuid) {
      PlayerSessionTracker.PlayerSession session = this.activeSessions.remove(playerUuid);
      if (session != null) {
         session.setEndTime(System.currentTimeMillis());
         this.historicalSessions.add(session);
         LOGGER.debug("Ended session for player: {} - Duration: {}ms", session.getPlayerName(), session.getDuration());
      }
   }

   public Collection<PlayerSessionTracker.PlayerSession> getActiveSessions() {
      return new ArrayList<>(this.activeSessions.values());
   }

   public List<PlayerSessionTracker.PlayerSession> getHistoricalSessions() {
      return new ArrayList<>(this.historicalSessions);
   }

   public PlayerSessionTracker.PlayerSession getPlayerSession(UUID playerUuid) {
      return this.activeSessions.get(playerUuid);
   }

   public void clearHistory() {
      this.historicalSessions.clear();
   }

   public static class PlayerSession {
      private final UUID playerUuid;
      private final String playerName;
      private final long startTime;
      private long endTime;

      public PlayerSession(UUID playerUuid, String playerName, long startTime) {
         this.playerUuid = playerUuid;
         this.playerName = playerName;
         this.startTime = startTime;
         this.endTime = 0L;
      }

      public UUID getPlayerUuid() {
         return this.playerUuid;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public long getStartTime() {
         return this.startTime;
      }

      public long getEndTime() {
         return this.endTime;
      }

      public void setEndTime(long endTime) {
         this.endTime = endTime;
      }

      public long getDuration() {
         return this.endTime == 0L ? System.currentTimeMillis() - this.startTime : this.endTime - this.startTime;
      }

      public boolean isActive() {
         return this.endTime == 0L;
      }
   }
}
