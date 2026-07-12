package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import com.zerog.neoessentials.util.ChatDebugUtil;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class ChatHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
   private static final Map<UUID, String> playerChannelMap = new ConcurrentHashMap<>();

   public static void setPlayerChannel(UUID playerUUID, String channel) {
      if (channel != null && !channel.isEmpty()) {
         playerChannelMap.put(playerUUID, channel);
      } else {
         playerChannelMap.remove(playerUUID);
      }
   }

   public static String getPlayerChannel(UUID playerUUID) {
      return playerChannelMap.get(playerUUID);
   }

   public static void clearPlayerChannel(UUID playerUUID) {
      playerChannelMap.remove(playerUUID);
   }

   @SubscribeEvent
   public static void onServerChat(ServerChatEvent event) {
      try {
         ServerPlayer player = event.getPlayer();
         String rawMessage = event.getRawText();
         String playerName = player.getName().getString();
         boolean isMuted = MuteManager.isMuted(player);
         ChatDebugUtil.debug("ChatHandler - Checking mute for %s, result: %s", playerName, isMuted);
         if (isMuted) {
            event.setCanceled(true);
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.chat.muted"));
            return;
         }

         AntiSpamManager.FilterResult filterResult = AntiSpamManager.getInstance().filterMessage(player, rawMessage);
         if (!filterResult.allowed) {
            event.setCanceled(true);
            if (filterResult.denyReason != null) {
               player.sendSystemMessage(Component.literal(filterResult.denyReason));
            }

            return;
         }

         String processedMessage = filterResult.filteredMessage != null ? filterResult.filteredMessage : rawMessage;
         ChatManager chatManager = ChatAPI.getChatManager();
         if (chatManager != null) {
            Set<String> requiredPerms = chatManager.getPlayerChatPermissions();
            if (requiredPerms != null && !requiredPerms.isEmpty()) {
               boolean hasAny = false;

               for (String perm : requiredPerms) {
                  if (PermissionAPI.hasPermission(player.getUUID(), perm)) {
                     hasAny = true;
                     break;
                  }
               }

               if (!hasAny) {
                  event.setCanceled(true);
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.chat.no_permission"));
                  return;
               }
            }
         }

         if (chatManager != null) {
            String trimmed = processedMessage.trim();
            if (trimmed.startsWith("/")) {
               String[] split = trimmed.substring(1).split(" ", 2);
               String command = split[0].toLowerCase();
               if (chatManager.isCommandMuted(command)) {
                  event.setCanceled(true);
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.chat.command_muted", command));
                  return;
               }
            }
         }

         if (chatManager == null) {
            LOGGER.warn("ChatManager not available, using default chat formatting");
            return;
         }

         JsonObject mainConfig = ConfigManager.getInstance().getConfig("config.json");
         JsonObject chatConfig = mainConfig.has("chat") ? mainConfig.getAsJsonObject("chat") : new JsonObject();
         JsonObject channelsConfig = chatConfig.has("channels") ? chatConfig.getAsJsonObject("channels") : null;
         boolean channelsEnabled = true;
         if (channelsConfig != null && channelsConfig.has("enabled")) {
            channelsEnabled = channelsConfig.get("enabled").getAsBoolean();
         }

         if (channelsConfig == null || !channelsEnabled) {
            LOGGER.debug("Chat channels system disabled, using global chat");
            channelsConfig = null;
         }

         String message = processedMessage;
         String channel = null;
         if (channelsConfig != null) {
            for (String ch : channelsConfig.keySet()) {
               if (!ch.equals("enabled") && !ch.endsWith("-description")) {
                  JsonObject chObj = channelsConfig.getAsJsonObject(ch);
                  if (!chObj.has("enabled") || chObj.get("enabled").getAsBoolean()) {
                     String prefix = chObj.has("prefix") ? chObj.get("prefix").getAsString() : "";
                     if (!prefix.isEmpty() && message.startsWith(prefix)) {
                        channel = ch;
                        message = message.substring(prefix.length()).stripLeading();
                        break;
                     }
                  }
               }
            }
         }

         if (channel == null) {
            channel = playerChannelMap.getOrDefault(player.getUUID(), null);
         }

         if (channel == null && channelsConfig != null) {
            for (String chx : channelsConfig.keySet()) {
               if (!chx.equals("enabled") && !chx.endsWith("-description")) {
                  JsonObject chObj = channelsConfig.getAsJsonObject(chx);
                  if (chObj.has("enabled") && chObj.get("enabled").getAsBoolean() && chObj.has("default") && chObj.get("default").getAsBoolean()) {
                     channel = chx;
                     break;
                  }
               }
            }
         }

         if (channel == null) {
            channel = "global";
         }

         String group = null;

         try {
            PermissionManager permManager = PermissionAPI.getManager();
            if (permManager != null) {
               PermissionUser user = permManager.getUser(player.getUUID());
               if (user != null && user.getGroup() != null) {
                  group = user.getGroup();
               } else {
                  group = permManager.getDefaultGroup();
               }
            }
         } catch (Exception var32) {
            LOGGER.debug("Could not get group for player {}: {}", playerName, var32.getMessage());
         }

         String world = null;

         try {
            Level level = player.level();
            world = level.dimension().location().getPath();
         } catch (Exception var29) {
            LOGGER.debug("Could not get world for player {}: {}", playerName, var29.getMessage());
         }

         if (ConfigManager.isChatFormattingEnabled()) {
            String chatFormat = chatManager.getChatFormat(group, world);
            event.setCanceled(true);

            try {
               Component formattedMessage = ChatFormatter.formatMessage(chatFormat, player, message);
               JsonObject channelObj = null;
               if (channelsConfig != null && channelsConfig.has(channel)) {
                  channelObj = channelsConfig.getAsJsonObject(channel);
               }

               boolean hasRadius = channelObj != null && channelObj.has("radius");
               int radius = hasRadius ? channelObj.get("radius").getAsInt() : 0;
               String requiredPermission = null;
               if (channelObj != null && channelObj.has("permission")) {
                  requiredPermission = channelObj.get("permission").getAsString();
               }

               MinecraftServer server = player.getServer();
               PlayerList playerList = server != null ? server.getPlayerList() : null;
               if (playerList != null) {
                  if (hasRadius) {
                     Vec3 playerPos = player.position();
                     Level playerLevel = player.level();

                     for (ServerPlayer target : playerList.getPlayers()) {
                        if (requiredPermission == null || PermissionAPI.hasPermission(target.getUUID(), requiredPermission)) {
                           Level targetLevel = target.level();
                           if (targetLevel.dimension().equals(playerLevel.dimension()) && target.position().distanceTo(playerPos) <= (double)radius) {
                              target.sendSystemMessage(formattedMessage);
                           }
                        }
                     }

                     if (isConsoleLoggingEnabled()) {
                        LOGGER.info("[{}] (radius:{}) <{}> {}", new Object[]{channel, radius, playerName, message});
                     }
                  } else if (requiredPermission != null) {
                     for (ServerPlayer targetx : playerList.getPlayers()) {
                        if (PermissionAPI.hasPermission(targetx.getUUID(), requiredPermission)) {
                           targetx.sendSystemMessage(formattedMessage);
                        }
                     }

                     if (isConsoleLoggingEnabled()) {
                        LOGGER.info("[{}] <{}> {}", new Object[]{channel, playerName, message});
                     }
                  } else {
                     for (ServerPlayer targetxx : playerList.getPlayers()) {
                        targetxx.sendSystemMessage(formattedMessage);
                     }

                     if (isConsoleLoggingEnabled()) {
                        LOGGER.info("[{}] <{}> {}", new Object[]{channel, playerName, message});
                     }
                  }

                  if (server != null && isConsoleLoggingEnabled()) {
                     server.sendSystemMessage(formattedMessage);
                  }
               }

               try {
                  boolean discordEnabled = false;
                  String discordChannelId = null;
                  boolean permissionPassed = true;
                  if (channelObj != null && channelObj.has("discord")) {
                     JsonObject discordConfig = channelObj.getAsJsonObject("discord");
                     if (discordConfig.has("enabled")) {
                        discordEnabled = discordConfig.get("enabled").getAsBoolean();
                     }

                     if (discordEnabled && discordConfig.has("channelId")) {
                        discordChannelId = discordConfig.get("channelId").getAsString();
                        if (discordChannelId != null && discordChannelId.trim().isEmpty()) {
                           discordChannelId = null;
                        }
                     }

                     LOGGER.debug("Channel '{}' Discord relay config: enabled={}, channelId={}", new Object[]{channel, discordEnabled, discordChannelId});
                  } else {
                     LOGGER.debug("Channel '{}' has no Discord relay config.", channel);
                  }

                  if (requiredPermission != null && !PermissionAPI.hasPermission(player.getUUID(), requiredPermission)) {
                     permissionPassed = false;
                     LOGGER.debug(
                        "Player '{}' does not have required permission '{}' for channel '{}'. Discord relay skipped.",
                        new Object[]{playerName, requiredPermission, channel}
                     );
                  }

                  if (discordEnabled && permissionPassed) {
                     if (discordChannelId == null) {
                        LOGGER.debug("Discord relay enabled for channel '{}' but no channelId set. Using fallback logic.", channel);
                     }

                     String formattedMessageText = formattedMessage.getString();
                     LOGGER.debug(
                        "Relaying message to Discord: channel='{}', discordChannelId='{}', message='{}'",
                        new Object[]{channel, discordChannelId, formattedMessage.getString()}
                     );
                     ChatIntegrationManager.broadcastPlayerChat(player, channel, message, formattedMessageText, discordChannelId);
                  } else if (!discordEnabled) {
                     LOGGER.debug("Discord relay is disabled for channel '{}'. Message will NOT be sent to Discord.", channel);
                  } else if (!permissionPassed) {
                     LOGGER.debug(
                        "Discord relay not sent: player '{}' lacks permission '{}' for channel '{}'", new Object[]{playerName, requiredPermission, channel}
                     );
                  }
               } catch (Exception var30) {
                  LOGGER.warn("Failed to send chat to Discord integration: {}", var30.getMessage());
                  LOGGER.debug("Discord integration error detail:", var30);
               }
            } catch (Exception var31) {
               LOGGER.warn("Chat formatting failed for player {}, falling back to vanilla: {}", playerName, var31.getMessage());
               event.setCanceled(false);
            }
         }
      } catch (Exception var33) {
         LOGGER.error("Error handling chat event for player {}: {}", new Object[]{event.getPlayer().getName().getString(), var33.getMessage(), var33});
      }
   }

   private static boolean isConsoleLoggingEnabled() {
      try {
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         if (config.has("chat")) {
            JsonObject chat = config.getAsJsonObject("chat");
            if (chat.has("logChatToConsole")) {
               return chat.get("logChatToConsole").getAsBoolean();
            }
         }
      } catch (Exception var2) {
      }

      return true;
   }
}
