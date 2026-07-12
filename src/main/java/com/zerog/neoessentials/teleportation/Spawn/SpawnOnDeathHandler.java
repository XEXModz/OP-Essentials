package com.zerog.neoessentials.teleportation.Spawn;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class SpawnOnDeathHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(SpawnOnDeathHandler.class);

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            ConfigManager config = ConfigManager.getInstance();
            boolean spawnOnDeath = false;
            if (config != null) {
               JsonObject mainConfig = config.getConfig("config.json");
               if (mainConfig.has("teleportation")) {
                  JsonObject tp = mainConfig.getAsJsonObject("teleportation");
                  if (tp.has("spawnSettings")) {
                     JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                     if (spawnSettings.has("spawnOnDeath")) {
                        spawnOnDeath = spawnSettings.get("spawnOnDeath").getAsBoolean();
                     }
                  }
               }
            }

            if (spawnOnDeath) {
               SpawnManager.getInstance().teleportToSpawn(player);
               LOGGER.info("Teleported {} to spawn on death (spawnOnDeath enabled)", player.getName().getString());
            }
         } catch (Exception var7) {
            LOGGER.error("Error handling spawnOnDeath for player {}: {}", event.getEntity().getName().getString(), var7.getMessage());
         }
      }
   }
}
