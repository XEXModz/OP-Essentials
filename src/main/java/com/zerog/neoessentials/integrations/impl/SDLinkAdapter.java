package com.zerog.neoessentials.integrations.impl;

import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDLinkAdapter implements ChatIntegrationAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(SDLinkAdapter.class);
   private boolean sdLinkLoaded = false;
   private Method sendMessageMethod = null;
   private Object messagingInstance = null;

   @Override
   public String getName() {
      return "Simple Discord Link";
   }

   @Override
   public boolean initialize() {
      this.sdLinkLoaded = ModList.get().isLoaded("sdlink");
      if (!this.sdLinkLoaded) {
         LOGGER.debug("Simple Discord Link mod not found, integration disabled");
         return false;
      } else {
         try {
            LOGGER.info("Simple Discord Link mod detected, initializing messaging integration...");
            if (this.tryInitBotController()) {
               LOGGER.info("SDLink BotController messaging API initialised successfully");
               return true;
            } else if (this.tryInitStaticApi()) {
               LOGGER.info("SDLink static messaging API initialised successfully");
               return true;
            } else {
               LOGGER.warn("SDLink detected but no supported messaging API found. Messages will be logged only. Ensure SDLink v3.2+ is installed.");
               this.sdLinkLoaded = true;
               return true;
            }
         } catch (Exception var2) {
            LOGGER.error("Failed to initialize Simple Discord Link integration: {}", var2.getMessage(), var2);
            return false;
         }
      }
   }

   private boolean tryInitBotController() {
      try {
         Class<?> botControllerClass = Class.forName("com.hypherionmc.sdlink.core.discord.BotController");
         Object instance = botControllerClass.getField("INSTANCE").get(null);
         Method method = botControllerClass.getMethod("sendMessage", String.class, String.class);
         this.messagingInstance = instance;
         this.sendMessageMethod = method;
         return true;
      } catch (Exception var4) {
         LOGGER.debug("BotController.sendMessage not found: {}", var4.getMessage());
         return false;
      }
   }

   private boolean tryInitStaticApi() {
      try {
         Class<?> apiClass = Class.forName("com.hypherionmc.sdlink.api.SDLinkAPI");
         Method method = apiClass.getMethod("sendMessage", String.class, String.class);
         this.messagingInstance = null;
         this.sendMessageMethod = method;
         return true;
      } catch (Exception var3) {
         LOGGER.debug("SDLinkAPI.sendMessage not found: {}", var3.getMessage());
         return false;
      }
   }

   @Override
   public boolean isEnabled() {
      return this.sdLinkLoaded;
   }

   @Override
   public void onPlayerChat(ServerPlayer player, String channel, String message, String formattedMessage, String discordChannelId) {
      if (this.isEnabled()) {
         try {
            String emoji = this.getChannelEmoji(channel);
            String cleanMessage = message.replaceAll("§[0-9a-fk-or]", "");
            String discordMessage = String.format("%s **[%s]** %s: %s", emoji, channel.toUpperCase(), player.getName().getString(), cleanMessage);
            String targetChannel;
            if (discordChannelId != null && !discordChannelId.isEmpty()) {
               targetChannel = discordChannelId;
            } else {
               String var10 = channel.toLowerCase();

               targetChannel = switch (var10) {
                  case "staff" -> "staff";
                  case "global" -> "chat";
                  case "local" -> "chat";
                  default -> "chat";
               };
            }

            this.sendToDiscord(targetChannel, discordMessage);
         } catch (Exception var12) {
            LOGGER.error("Failed to send chat message to Discord: {}", var12.getMessage());
         }
      }
   }

   private String getChannelEmoji(String channel) {
      String var2 = channel.toLowerCase();

      return switch (var2) {
         case "local" -> "\ud83d\udcac";
         case "global" -> "\ud83c\udf0d";
         case "staff" -> "\ud83d\udee1️";
         default -> "\ud83d\udcad";
      };
   }

   @Override
   public void onPrivateMessage(ServerPlayer sender, ServerPlayer recipient, String message) {
      if (this.isEnabled()) {
         try {
            String discordMessage = String.format(
               "\ud83d\udce9 **Private Message** | %s → %s: %s", sender.getName().getString(), recipient.getName().getString(), message
            );
            this.sendToDiscord("private-messages", discordMessage);
         } catch (Exception var5) {
            LOGGER.error("Failed to send private message to Discord: {}", var5.getMessage());
         }
      }
   }

   @Override
   public void onPlayerMute(ServerPlayer player, String reason, boolean isMuted) {
      if (this.isEnabled()) {
         try {
            String action = isMuted ? "muted" : "unmuted";
            String emoji = isMuted ? "\ud83d\udd07" : "\ud83d\udd0a";
            String discordMessage = String.format(
               "%s **%s** has been %s%s", emoji, player.getName().getString(), action, reason != null && !reason.isEmpty() ? " (Reason: " + reason + ")" : ""
            );
            this.sendToDiscord("moderation", discordMessage);
         } catch (Exception var7) {
            LOGGER.error("Failed to send mute event to Discord: {}", var7.getMessage());
         }
      }
   }

   @Override
   public void onAfkStatusChange(ServerPlayer player, boolean isAfk, String reason) {
      if (this.isEnabled()) {
         try {
            String status = isAfk ? "is now AFK" : "is no longer AFK";
            String emoji = isAfk ? "\ud83d\udca4" : "✅";
            String discordMessage = String.format(
               "%s **%s** %s%s", emoji, player.getName().getString(), status, isAfk && reason != null && !reason.isEmpty() ? " (" + reason + ")" : ""
            );
            this.sendToDiscord("chat", discordMessage);
         } catch (Exception var7) {
            LOGGER.error("Failed to send AFK event to Discord: {}", var7.getMessage());
         }
      }
   }

   @Override
   public void onPlayerJoin(ServerPlayer player) {
      if (this.isEnabled()) {
         try {
            String discordMessage = String.format("➡️ **%s** joined the server", player.getName().getString());
            this.sendToDiscord("chat", discordMessage);
         } catch (Exception var3) {
            LOGGER.error("Failed to send join event to Discord: {}", var3.getMessage());
         }
      }
   }

   @Override
   public void onPlayerQuit(ServerPlayer player) {
      if (this.isEnabled()) {
         try {
            String discordMessage = String.format("⬅️ **%s** left the server", player.getName().getString());
            this.sendToDiscord("chat", discordMessage);
         } catch (Exception var3) {
            LOGGER.error("Failed to send quit event to Discord: {}", var3.getMessage());
         }
      }
   }

   private void sendToDiscord(String channel, String message) {
      if (this.sendMessageMethod != null) {
         try {
            this.sendMessageMethod.invoke(this.messagingInstance, channel, message);
            LOGGER.debug("Sent to Discord channel '{}' via SDLink: {}", channel, message);
         } catch (Exception var4) {
            LOGGER.warn("SDLink sendMessage failed for channel '{}': {}", channel, var4.getMessage());
         }
      } else {
         LOGGER.info("[Discord->{}] {}", channel, message);
      }
   }

   @Override
   public void shutdown() {
      this.sendMessageMethod = null;
      this.messagingInstance = null;
      LOGGER.info("Simple Discord Link integration shut down");
   }
}
