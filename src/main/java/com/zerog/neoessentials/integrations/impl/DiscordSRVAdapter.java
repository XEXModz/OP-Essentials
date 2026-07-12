package com.zerog.neoessentials.integrations.impl;

import com.zerog.neoessentials.integrations.ChatIntegrationAdapter;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordSRVAdapter implements ChatIntegrationAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(DiscordSRVAdapter.class);
   private boolean discordSRVLoaded = false;
   private Method sendMessageMethod = null;
   private Object messagingInstance = null;

   @Override
   public String getName() {
      return "DiscordSRV";
   }

   @Override
   public boolean initialize() {
      this.discordSRVLoaded = ModList.get().isLoaded("discordsrv");
      if (!this.discordSRVLoaded) {
         LOGGER.debug("DiscordSRV mod not found, integration disabled");
         return false;
      } else {
         try {
            LOGGER.info("DiscordSRV mod detected, initializing messaging integration...");
            if (this.tryInitDiscordSRVV2()) {
               LOGGER.info("DiscordSRV v2 messaging API initialised");
               return true;
            } else if (this.tryInitDiscordSRVClassic()) {
               LOGGER.info("DiscordSRV classic messaging API initialised");
               return true;
            } else {
               LOGGER.warn("DiscordSRV detected but no supported messaging API found. Events will be logged only. Ensure DiscordSRV is up to date.");
               this.discordSRVLoaded = true;
               return true;
            }
         } catch (Exception var2) {
            LOGGER.error("Failed to initialize DiscordSRV integration: {}", var2.getMessage(), var2);
            return false;
         }
      }
   }

   private boolean tryInitDiscordSRVV2() {
      try {
         Class<?> dsrvClass = Class.forName("com.discordsrv.api.DiscordSRVApi");
         Method getMethod = Class.forName("com.discordsrv.api.DiscordSRV").getMethod("get");
         Object instance = getMethod.invoke(null);
         Method method = dsrvClass.getMethod("sendMessage", String.class, String.class);
         this.messagingInstance = instance;
         this.sendMessageMethod = method;
         return true;
      } catch (Exception var5) {
         LOGGER.debug("DiscordSRV v2 API not found: {}", var5.getMessage());
         return false;
      }
   }

   private boolean tryInitDiscordSRVClassic() {
      try {
         Class<?> pluginClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
         Method getPlugin = pluginClass.getMethod("getPlugin");
         Object plugin = getPlugin.invoke(null);
         Class<?> utilClass = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
         Method method = utilClass.getMethod("sendMessage", Class.forName("net.dv8tion.jda.api.entities.TextChannel"), String.class);
         this.messagingInstance = plugin;
         this.sendMessageMethod = method;
         return true;
      } catch (Exception var6) {
         LOGGER.debug("DiscordSRV classic API not found: {}", var6.getMessage());
         return false;
      }
   }

   @Override
   public boolean isEnabled() {
      return this.discordSRVLoaded;
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
            if (this.messagingInstance != null
               && this.messagingInstance.getClass().getName().contains("DiscordSRV")
               && this.sendMessageMethod.getParameterCount() == 2
               && !this.sendMessageMethod.getParameterTypes()[0].equals(String.class)) {
               Method getChannel = this.messagingInstance.getClass().getMethod("getMainTextChannel");
               Object textChannel = getChannel.invoke(this.messagingInstance);
               if (textChannel != null) {
                  this.sendMessageMethod.invoke(null, textChannel, message);
               }
            } else {
               this.sendMessageMethod.invoke(this.messagingInstance, channel, message);
            }

            LOGGER.debug("Sent to Discord channel '{}' via DiscordSRV: {}", channel, message);
         } catch (Exception var5) {
            LOGGER.warn("DiscordSRV sendMessage failed for channel '{}': {}", channel, var5.getMessage());
         }
      } else {
         LOGGER.info("[Discord->{}] {}", channel, message);
      }
   }

   @Override
   public void shutdown() {
      this.sendMessageMethod = null;
      this.messagingInstance = null;
      LOGGER.info("DiscordSRV integration shut down");
   }
}
