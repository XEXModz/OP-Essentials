package com.zerog.neoessentials.chat.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.PlaceholderAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.teleportation.Spawn.SpawnManager;
import com.zerog.neoessentials.util.ResourceUtil;
import com.zerog.neoessentials.util.commands.MailCommand;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class PlayerJoinQuitHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerJoinQuitHandler.class);
   private static final Object FIRST_JOIN_LOCK = new Object();

   @SubscribeEvent
   public static void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            ConfigManager config = ConfigManager.getInstance();
            if (config.isVanishSystemEnabled() && config.isVanishOnJoinEnabled()) {
               UUID playerUuid = player.getUUID();
               String playerName = player.getName().getString();
               boolean hasVanishPerm = PermissionAPI.hasPermission(playerUuid, "neoessentials.moderation.vanish");
               if (hasVanishPerm) {
                  VanishManager vanishManager = VanishManager.getInstance();
                  if (!vanishManager.isPlayerVanished(playerUuid)) {
                     vanishManager.vanishPlayer(playerUuid, playerName, "AutoVanishOnJoin", true);
                  }
               }
            }
         } catch (Exception var22) {
            LOGGER.error("Error handling vanish-on-join for player {}: {}", player.getName().getString(), var22.getMessage());
         }

         try {
            ConfigManager config = ConfigManager.getInstance();
            if (config.isNewPlayerKitEnabled()) {
               String kitName = config.getNewPlayerKitName();
               if (kitName != null && !kitName.trim().isEmpty()) {
                  boolean isFirstJoin;
                  synchronized (FIRST_JOIN_LOCK) {
                     File firstJoinFile = ResourceUtil.getDataFile("first_joined.json");
                     Set<UUID> joined = new HashSet<>();
                     if (firstJoinFile.exists() && firstJoinFile.length() > 0L) {
                        try (Reader r = new InputStreamReader(new FileInputStream(firstJoinFile), StandardCharsets.UTF_8)) {
                           JsonElement parsed = JsonParser.parseReader(r);
                           if (parsed != null && parsed.isJsonArray()) {
                              for (JsonElement el : parsed.getAsJsonArray()) {
                                 try {
                                    if (el != null && el.isJsonPrimitive()) {
                                       joined.add(UUID.fromString(el.getAsString()));
                                    }
                                 } catch (Exception var21) {
                                 }
                              }
                           } else {
                              LOGGER.warn(
                                 "first_joined.json is not a JSON array (found {}); treating as empty and rewriting",
                                 parsed == null ? "null" : parsed.getClass().getSimpleName()
                              );
                           }
                        } catch (JsonSyntaxException var26) {
                           LOGGER.warn("first_joined.json is malformed ({}); treating as empty and rewriting", var26.getMessage());
                        } catch (Exception var27) {
                           LOGGER.warn("Could not read first_joined.json ({}); treating as empty", var27.getMessage());
                        }
                     }

                     isFirstJoin = !joined.contains(player.getUUID());
                     if (isFirstJoin) {
                        KitManager kitManager = KitManager.getInstance();
                        kitManager.giveKit(player, kitName);
                        joined.add(player.getUUID());

                        try {
                           File parent = firstJoinFile.getParentFile();
                           if (parent != null && !parent.exists()) {
                              parent.mkdirs();
                           }

                           JsonArray arr = new JsonArray();

                           for (UUID id : joined) {
                              arr.add(id.toString());
                           }

                           File tmp = new File(firstJoinFile.getAbsolutePath() + ".tmp");

                           try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                              w.write(arr.toString());
                              w.flush();
                           }

                           Files.move(tmp.toPath(), firstJoinFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        } catch (Exception var24) {
                           LOGGER.error("Could not persist first_joined.json for {}: {}", player.getName().getString(), var24.getMessage());
                        }

                        player.sendSystemMessage(Component.literal("You have received a starter kit!"));
                     }
                  }

                  try {
                     boolean spawnOnJoin = false;
                     if (config != null) {
                        JsonObject mainConfig = config.getConfig("config.json");
                        if (mainConfig.has("teleportation")) {
                           JsonObject tp = mainConfig.getAsJsonObject("teleportation");
                           if (tp.has("spawnSettings")) {
                              JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                              if (spawnSettings.has("spawnOnJoin")) {
                                 spawnOnJoin = spawnSettings.get("spawnOnJoin").getAsBoolean();
                              }
                           }
                        }
                     }

                     if (isFirstJoin && spawnOnJoin) {
                        SpawnManager.getInstance().teleportToSpawn(player);
                     }
                  } catch (Exception var17) {
                     LOGGER.error("Error handling spawnOnJoin for player {}: {}", player.getName().getString(), var17.getMessage());
                  }
               }
            }
         } catch (Exception var29) {
            LOGGER.error("Error handling newPlayerKit for player {}: {}", player.getName().getString(), var29.getMessage());
         }

         try {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager == null) {
               LOGGER.warn("ChatManager not available, using default join messages");
               return;
            }

            String customJoinMessage = chatManager.getCustomJoinMessage();
            if (customJoinMessage != null && !customJoinMessage.equals("none") && !customJoinMessage.trim().isEmpty()) {
               String resolvedMessage = PlaceholderAPI.setPlaceholders(player, customJoinMessage);
               String coloredMessage = resolvedMessage.replaceAll("&([0-9a-fk-or])", "§$1");
               Component formattedMessage = Component.literal(coloredMessage);
               player.getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
               LOGGER.debug("Displayed custom join message for player {}: {}", player.getName().getString(), formattedMessage.getString());
            } else {
               LOGGER.debug("Using default join message for player {}", player.getName().getString());
            }

            ChatIntegrationManager.broadcastPlayerJoin(player);

            try {
               MailCommand.notifyOnLogin(player);
            } catch (Exception var16) {
               LOGGER.debug("Could not send mail notification to {}: {}", player.getName().getString(), var16.getMessage());
            }
         } catch (Exception var23) {
            LOGGER.error("Error handling join event for player {}: {}", new Object[]{player.getName().getString(), var23.getMessage(), var23});
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerQuit(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            ChatManager chatManager = ChatAPI.getChatManager();
            if (chatManager == null) {
               LOGGER.warn("ChatManager not available, using default quit messages");
               return;
            }

            String customQuitMessage = chatManager.getCustomQuitMessage();
            if (customQuitMessage != null && !customQuitMessage.equals("none") && !customQuitMessage.trim().isEmpty()) {
               String resolvedMessage = PlaceholderAPI.setPlaceholders(player, customQuitMessage);
               String coloredMessage = resolvedMessage.replaceAll("&([0-9a-fk-or])", "§$1");
               Component formattedMessage = Component.literal(coloredMessage);
               player.getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
               LOGGER.debug("Displayed custom quit message for player {}: {}", player.getName().getString(), formattedMessage.getString());
            } else {
               LOGGER.debug("Using default quit message for player {}", player.getName().getString());
            }

            ChatIntegrationManager.broadcastPlayerQuit(player);
         } catch (Exception var7) {
            LOGGER.error("Error handling quit event for player {}: {}", new Object[]{player.getName().getString(), var7.getMessage(), var7});
         }
      }
   }
}
