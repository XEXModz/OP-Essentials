package com.zerog.neoessentials.integrations;

import com.zerog.neoessentials.integrations.impl.DCIntegrationAdapter;
import com.zerog.neoessentials.integrations.impl.DiscordSRVAdapter;
import com.zerog.neoessentials.integrations.impl.SDLinkAdapter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatIntegrationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChatIntegrationManager.class);
   private static final List<ChatIntegrationAdapter> adapters = new ArrayList<>();

   public static void initialize() {
      LOGGER.info("Initializing chat integration adapters...");
      clearAdapters();
      List<ChatIntegrationAdapter> candidates = List.of(new SDLinkAdapter(), new DCIntegrationAdapter(), new DiscordSRVAdapter());
      int loaded = 0;

      for (ChatIntegrationAdapter adapter : candidates) {
         try {
            if (adapter.initialize()) {
               registerAdapter(adapter);
               loaded++;
            }
         } catch (Exception var5) {
            LOGGER.error("Failed to initialize chat integration adapter '{}': {}", new Object[]{adapter.getName(), var5.getMessage(), var5});
         }
      }

      if (loaded == 0) {
         LOGGER.info("No external chat integration mods found. Running in standalone mode.");
      } else {
         LOGGER.info("Initialized {} chat integration adapter(s).", loaded);
      }
   }

   public static void shutdown() {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.shutdown();
         } catch (Exception var3) {
            LOGGER.error("Error shutting down chat integration adapter '{}': {}", new Object[]{adapter.getName(), var3.getMessage(), var3});
         }
      }

      clearAdapters();
      LOGGER.info("Chat integration adapters shut down.");
   }

   public static void registerAdapter(ChatIntegrationAdapter adapter) {
      if (adapter != null && !adapters.contains(adapter)) {
         adapters.add(adapter);
         LOGGER.info("Registered chat mod integration adapter: {}", adapter.getName());
      }
   }

   public static void unregisterAdapter(ChatIntegrationAdapter adapter) {
      if (adapters.remove(adapter)) {
         LOGGER.info("Unregistered chat mod integration adapter: {}", adapter.getName());
      }
   }

   public static void broadcastPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onPlayerChat(player, channel, message, formattedMessage, discordChannelId);
         } catch (Exception var8) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var8.getMessage(), var8});
         }
      }
   }

   public static void broadcastPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onPrivateMessage(sender, recipient, message);
         } catch (Exception var6) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var6.getMessage(), var6});
         }
      }
   }

   public static void broadcastMuteEvent(ServerPlayer player, String reason, boolean isMuted) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onPlayerMute(player, reason, isMuted);
         } catch (Exception var6) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var6.getMessage(), var6});
         }
      }
   }

   public static void broadcastAfkEvent(ServerPlayer player, boolean isAfk, String reason) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onAfkStatusChange(player, isAfk, reason);
         } catch (Exception var6) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var6.getMessage(), var6});
         }
      }
   }

   public static void broadcastPlayerJoin(ServerPlayer player) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onPlayerJoin(player);
         } catch (Exception var4) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var4.getMessage(), var4});
         }
      }
   }

   public static void broadcastPlayerQuit(ServerPlayer player) {
      for (ChatIntegrationAdapter adapter : adapters) {
         try {
            adapter.onPlayerQuit(player);
         } catch (Exception var4) {
            LOGGER.error("Error in chat integration adapter {}: {}", new Object[]{adapter.getName(), var4.getMessage(), var4});
         }
      }
   }

   public static List<ChatIntegrationAdapter> getAdapters() {
      return new ArrayList<>(adapters);
   }

   public static boolean hasIntegrations() {
      return !adapters.isEmpty();
   }

   public static void clearAdapters() {
      adapters.clear();
      LOGGER.info("Cleared all chat mod integration adapters");
   }
}
