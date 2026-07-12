package com.zerog.neoessentials.webdashboard;

import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.webdashboard.data.DataCollector;
import com.zerog.neoessentials.webdashboard.websocket.DashboardWebSocketServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class DashboardLifecycleManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DashboardLifecycleManager.class);
   private static boolean manuallyDisabled = false;

   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      if (!ConfigManager.isWebDashboardEnabled()) {
         LOGGER.info("Dashboard is disabled in configuration");
      } else if (manuallyDisabled) {
         LOGGER.info("Dashboard was manually disabled and will not auto-start");
      } else {
         try {
            MinecraftServer server = event.getServer();
            DataCollector.getInstance().initialize(server);
            DashboardAPI.getInstance().setServer(server);
            DashboardAPI.getInstance().start();

            try {
               int wsPort = ConfigManager.getInstance().getWebDashboardWebSocketPort();
               DashboardWebSocketServer wsServer = DashboardWebSocketServer.getInstance(wsPort);
               if (wsServer.startIfNotStarted()) {
                  LOGGER.info("Dashboard WebSocket server started on port {}", wsPort);
               } else {
                  LOGGER.info("Dashboard WebSocket server already running on port {}", wsPort);
               }
            } catch (Exception var4) {
               LOGGER.error("Failed to start WebSocket server: {}", var4.getMessage(), var4);
            }

            LOGGER.info("Dashboard auto-started successfully");
         } catch (Exception var5) {
            LOGGER.error("Failed to auto-start dashboard", var5);
         }
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      try {
         if (DashboardAPI.getInstance().isRunning()) {
            LOGGER.info("Server stopping - shutting down Dashboard...");
            long startTime = System.currentTimeMillis();
            DashboardAPI.getInstance().stop();
            long dashboardStopTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Dashboard API stopped in {}ms", dashboardStopTime);

            try {
               DashboardWebSocketServer.shutdownAndReset(2000);
               LOGGER.info("Dashboard WebSocket server stopped");
            } catch (Exception var7) {
               LOGGER.warn("Error stopping WebSocket server: {}", var7.getMessage());
            }

            startTime = System.currentTimeMillis();
            DataCollector.getInstance().shutdown();
            long collectorStopTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Data Collector stopped in {}ms", collectorStopTime);
            LOGGER.info("Dashboard shutdown complete (total: {}ms)", dashboardStopTime + collectorStopTime);
         }
      } catch (Exception var8) {
         LOGGER.error("Error stopping dashboard", var8);
      }
   }

   public static boolean startDashboard(MinecraftServer server) {
      try {
         if (DashboardAPI.getInstance().isRunning()) {
            return false;
         } else {
            DataCollector.getInstance().initialize(server);
            DashboardAPI.getInstance().setServer(server);
            DashboardAPI.getInstance().start();

            try {
               int wsPort = ConfigManager.getInstance().getWebDashboardWebSocketPort();
               DashboardWebSocketServer.getInstance(wsPort).startIfNotStarted();
            } catch (Exception var2) {
               LOGGER.error("Failed to start WebSocket server (manual): {}", var2.getMessage(), var2);
            }

            manuallyDisabled = false;
            return true;
         }
      } catch (Exception var3) {
         LOGGER.error("Failed to start dashboard manually", var3);
         return false;
      }
   }

   public static boolean stopDashboard() {
      try {
         if (!DashboardAPI.getInstance().isRunning()) {
            return false;
         } else {
            DashboardAPI.getInstance().stop();

            try {
               DashboardWebSocketServer.shutdownAndReset(2000);
            } catch (Exception var1) {
               LOGGER.warn("Error stopping WebSocket server (manual): {}", var1.getMessage());
            }

            DataCollector.getInstance().shutdown();
            manuallyDisabled = true;
            return true;
         }
      } catch (Exception var2) {
         LOGGER.error("Failed to stop dashboard manually", var2);
         return false;
      }
   }

   public static DashboardLifecycleManager.DashboardStatus getStatus() {
      boolean running = DashboardAPI.getInstance().isRunning();
      boolean enabled = ConfigManager.isWebDashboardEnabled();
      String url = String.format("http://%s:%d", DashboardAPI.getInstance().getBindAddress(), DashboardAPI.getInstance().getPort());
      return new DashboardLifecycleManager.DashboardStatus(running, enabled, manuallyDisabled, url);
   }

   public static class DashboardStatus {
      public final boolean running;
      public final boolean configEnabled;
      public final boolean manuallyDisabled;
      public final String url;

      public DashboardStatus(boolean running, boolean configEnabled, boolean manuallyDisabled, String url) {
         this.running = running;
         this.configEnabled = configEnabled;
         this.manuallyDisabled = manuallyDisabled;
         this.url = url;
      }
   }
}
