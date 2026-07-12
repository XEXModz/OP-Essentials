package com.zerog.neoessentials.resourcepack;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials",
   bus = Bus.GAME
)
public class ResourcePackManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackManager.class);
   private static volatile ResourcePackManager instance;
   private String resourcePackUrl = null;
   private String resourcePackHash = null;
   private boolean autoSendEnabled = false;

   private ResourcePackManager() {
   }

   public static ResourcePackManager getInstance() {
      if (instance == null) {
         synchronized (ResourcePackManager.class) {
            if (instance == null) {
               instance = new ResourcePackManager();
            }
         }
      }

      return instance;
   }

   public void initialize() {
      try {
         if (!this.isAutoSendEnabled()) {
            LOGGER.info("Auto-send resource pack is disabled in config");
            return;
         }

         if (this.shouldGeneratePack()) {
            LOGGER.info("Generating badge resource pack...");
            Path packPath = ResourcePackGenerator.generateResourcePack();
            if (packPath != null) {
               this.loadResourcePackInfo(packPath);
               this.autoSendEnabled = true;
               LOGGER.info("Resource pack system initialized successfully");
            } else {
               LOGGER.warn("Failed to generate resource pack - will use emoji badges");
               this.autoSendEnabled = false;
            }
         } else {
            LOGGER.info("Resource pack generation skipped (custom images not enabled)");
         }
      } catch (Exception var2) {
         LOGGER.error("Failed to initialize resource pack system: {}", var2.getMessage(), var2);
         this.autoSendEnabled = false;
      }
   }

   private void loadResourcePackInfo(Path packPath) throws Exception {
      String configuredUrl = this.getConfiguredPackUrl();
      if (configuredUrl != null && !configuredUrl.isEmpty()) {
         this.resourcePackUrl = configuredUrl;
         LOGGER.info("Using configured resource pack URL: {}", this.resourcePackUrl);
      } else {
         this.resourcePackUrl = packPath.toAbsolutePath().toString();
         LOGGER.warn("No resource pack URL configured. Pack generated at: {}", this.resourcePackUrl);
         LOGGER.warn("To use auto-send, upload pack to a web server and set 'resourcePackUrl' in config");
      }

      Path sha1File = Paths.get("config/neoessentials/NeoEssentials-Badges.sha1");
      if (Files.exists(sha1File)) {
         this.resourcePackHash = Files.readString(sha1File).trim();
         LOGGER.info("Loaded resource pack SHA-1: {}", this.resourcePackHash);
      }
   }

   public void sendResourcePack(ServerPlayer player) {
      if (this.autoSendEnabled && this.resourcePackUrl != null) {
         try {
            LOGGER.debug("Resource pack auto-send requested for player: {}", player.getName().getString());
            LOGGER.warn("Auto-send not yet implemented - please configure server.properties");
            LOGGER.warn("Add to server.properties:");
            LOGGER.warn("  resource-pack={}", this.resourcePackUrl);
            if (this.resourcePackHash != null) {
               LOGGER.warn("  resource-pack-sha1={}", this.resourcePackHash);
            }
         } catch (Exception var3) {
            LOGGER.error("Failed to send resource pack to {}: {}", player.getName().getString(), var3.getMessage());
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         MinecraftServer server = player.getServer();
         if (server != null) {
            server.execute(() -> {
               try {
                  Thread.sleep(1000L);
                  getInstance().sendResourcePack(player);
               } catch (InterruptedException var2) {
                  Thread.currentThread().interrupt();
               }
            });
         }
      }
   }

   private boolean isAutoSendEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("autoSendResourcePack")) {
               return badges.get("autoSendResourcePack").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   private boolean shouldGeneratePack() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("useCustomImages")) {
               return badges.get("useCustomImages").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   private boolean isPackRequired() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("requireResourcePack")) {
               return badges.get("requireResourcePack").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   private String getConfiguredPackUrl() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("resourcePackUrl")) {
               return badges.get("resourcePackUrl").getAsString();
            }
         }
      } catch (Exception var3) {
      }

      return null;
   }

   private String getPackPrompt() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("resourcePackPrompt")) {
               return badges.get("resourcePackPrompt").getAsString();
            }
         }
      } catch (Exception var3) {
      }

      return "This server uses custom badge images. Please accept the resource pack for the best experience!";
   }
}
