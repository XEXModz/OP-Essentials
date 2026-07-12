package com.zerog.neoessentials.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntiSpamManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(AntiSpamManager.class);
   private static volatile AntiSpamManager instance;
   private static final Pattern URL_PATTERN = Pattern.compile("\\b(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%.]+)\\b", 2);
   private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();
   private final Map<UUID, AntiSpamManager.MessageRateTracker> rateTrackers = new ConcurrentHashMap<>();

   private AntiSpamManager() {
   }

   public static AntiSpamManager getInstance() {
      if (instance == null) {
         synchronized (AntiSpamManager.class) {
            if (instance == null) {
               instance = new AntiSpamManager();
            }
         }
      }

      return instance;
   }

   public AntiSpamManager.FilterResult filterMessage(ServerPlayer player, String message) {
      if (!this.isAntiSpamEnabled()) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else {
         UUID playerId = player.getUUID();
         AntiSpamManager.FilterResult spamCheck = this.checkSpamFilter(player, message);
         if (!spamCheck.allowed) {
            return spamCheck;
         } else {
            AntiSpamManager.FilterResult repeatCheck = this.checkRepeatFilter(player, message);
            if (!repeatCheck.allowed) {
               return repeatCheck;
            } else {
               AntiSpamManager.FilterResult linkCheck = this.checkLinkFilter(player, message);
               if (!linkCheck.allowed) {
                  return linkCheck;
               } else {
                  AntiSpamManager.FilterResult capsCheck = this.checkCapsFilter(player, message);
                  if (!capsCheck.allowed) {
                     return capsCheck;
                  } else {
                     this.lastMessages.put(playerId, message);
                     this.lastMessageTimes.put(playerId, System.currentTimeMillis());
                     return new AntiSpamManager.FilterResult(true, capsCheck.filteredMessage, null);
                  }
               }
            }
         }
      }
   }

   private AntiSpamManager.FilterResult checkSpamFilter(ServerPlayer player, String message) {
      if (!this.isSpamFilterEnabled()) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat.spam.bypass")) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else {
         try {
            JsonObject config = this.getSpamFilterConfig();
            int maxMessages = config.get("messagesPerPeriod").getAsInt();
            int periodSeconds = config.get("periodSeconds").getAsInt();
            AntiSpamManager.MessageRateTracker tracker = this.rateTrackers
               .computeIfAbsent(player.getUUID(), k -> new AntiSpamManager.MessageRateTracker(maxMessages, periodSeconds));
            if (!tracker.allowMessage()) {
               String action = config.get("action").getAsString();
               if ("block".equals(action)) {
                  return new AntiSpamManager.FilterResult(false, null, "§cYou are sending messages too quickly! Please slow down.");
               }
            }
         } catch (Exception var8) {
            LOGGER.error("Error checking spam filter: {}", var8.getMessage());
         }

         return new AntiSpamManager.FilterResult(true, message, null);
      }
   }

   private AntiSpamManager.FilterResult checkRepeatFilter(ServerPlayer player, String message) {
      if (!this.isRepeatFilterEnabled()) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat.repeat.bypass")) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else {
         try {
            UUID playerId = player.getUUID();
            String lastMessage = this.lastMessages.get(playerId);
            Long lastTime = this.lastMessageTimes.get(playerId);
            if (lastMessage != null && lastMessage.equals(message)) {
               JsonObject config = this.getRepeatFilterConfig();
               int cooldown = config.get("cooldownSeconds").getAsInt();
               long currentTime = System.currentTimeMillis();
               if (lastTime != null && currentTime - lastTime < (long)cooldown * 1000L) {
                  String action = config.get("action").getAsString();
                  if ("block".equals(action)) {
                     long remainingSeconds = (long)cooldown - (currentTime - lastTime) / 1000L;
                     return new AntiSpamManager.FilterResult(
                        false, null, "§cPlease wait " + remainingSeconds + " seconds before sending the same message again."
                     );
                  }
               }
            }
         } catch (Exception var13) {
            LOGGER.error("Error checking repeat filter: {}", var13.getMessage());
         }

         return new AntiSpamManager.FilterResult(true, message, null);
      }
   }

   private AntiSpamManager.FilterResult checkLinkFilter(ServerPlayer player, String message) {
      if (!this.isLinkFilterEnabled()) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat.links.bypass")) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else {
         try {
            JsonObject config = this.getLinkFilterConfig();
            String action = config.get("action").getAsString();
            if ("allow".equals(action)) {
               return new AntiSpamManager.FilterResult(true, message, null);
            }

            Matcher matcher = URL_PATTERN.matcher(message);
            if (matcher.find()) {
               if ("block".equals(action)) {
                  return new AntiSpamManager.FilterResult(false, null, "§cLinks are not allowed in chat!");
               }

               if ("whitelist".equals(action)) {
                  JsonArray whitelist = config.getAsJsonArray("whitelist");
                  String url = matcher.group(1).toLowerCase();
                  boolean allowed = false;

                  for (JsonElement element : whitelist) {
                     String domain = element.getAsString().toLowerCase();
                     if (url.contains(domain)) {
                        allowed = true;
                        break;
                     }
                  }

                  if (!allowed) {
                     return new AntiSpamManager.FilterResult(false, null, "§cOnly whitelisted links are allowed in chat!");
                  }
               }
            }
         } catch (Exception var12) {
            LOGGER.error("Error checking link filter: {}", var12.getMessage());
         }

         return new AntiSpamManager.FilterResult(true, message, null);
      }
   }

   private AntiSpamManager.FilterResult checkCapsFilter(ServerPlayer player, String message) {
      if (!this.isCapsFilterEnabled()) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else if (PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat.caps.bypass")) {
         return new AntiSpamManager.FilterResult(true, message, null);
      } else {
         try {
            JsonObject config = this.getCapsFilterConfig();
            int minLength = config.get("minimumLength").getAsInt();
            if (message.length() < minLength) {
               return new AntiSpamManager.FilterResult(true, message, null);
            }

            int totalLetters = 0;
            int capsLetters = 0;

            for (char c : message.toCharArray()) {
               if (Character.isLetter(c)) {
                  totalLetters++;
                  if (Character.isUpperCase(c)) {
                     capsLetters++;
                  }
               }
            }

            if (totalLetters == 0) {
               return new AntiSpamManager.FilterResult(true, message, null);
            }

            int capsPercentage = capsLetters * 100 / totalLetters;
            int maxPercentage = config.get("maxPercentage").getAsInt();
            if (capsPercentage > maxPercentage) {
               String action = config.get("action").getAsString();
               if ("lowercase".equals(action)) {
                  return new AntiSpamManager.FilterResult(true, message.toLowerCase(), null);
               }

               if ("block".equals(action)) {
                  return new AntiSpamManager.FilterResult(false, null, "§cPlease don't use excessive caps in chat!");
               }

               if ("warn".equals(action)) {
                  player.sendSystemMessage(Component.literal("§ePlease avoid using excessive caps in your messages."));
                  return new AntiSpamManager.FilterResult(true, message, null);
               }
            }
         } catch (Exception var11) {
            LOGGER.error("Error checking caps filter: {}", var11.getMessage());
         }

         return new AntiSpamManager.FilterResult(true, message, null);
      }
   }

   private boolean isAntiSpamEnabled() {
      try {
         JsonObject chatConfig = this.getChatConfigSection();
         if (chatConfig.has("antiSpam")) {
            return chatConfig.getAsJsonObject("antiSpam").get("enabled").getAsBoolean();
         }
      } catch (Exception var2) {
      }

      return true;
   }

   private boolean isSpamFilterEnabled() {
      try {
         JsonObject chatConfig = this.getChatConfigSection();
         if (chatConfig.has("antiSpam")) {
            JsonObject antiSpam = chatConfig.getAsJsonObject("antiSpam");
            if (antiSpam.has("spamFilter")) {
               return antiSpam.getAsJsonObject("spamFilter").get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   private boolean isRepeatFilterEnabled() {
      try {
         JsonObject chatConfig = this.getChatConfigSection();
         if (chatConfig.has("antiSpam")) {
            JsonObject antiSpam = chatConfig.getAsJsonObject("antiSpam");
            if (antiSpam.has("repeatFilter")) {
               return antiSpam.getAsJsonObject("repeatFilter").get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   private boolean isLinkFilterEnabled() {
      try {
         JsonObject chatConfig = this.getChatConfigSection();
         if (chatConfig.has("antiSpam")) {
            JsonObject antiSpam = chatConfig.getAsJsonObject("antiSpam");
            if (antiSpam.has("linkFilter")) {
               return antiSpam.getAsJsonObject("linkFilter").get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   private boolean isCapsFilterEnabled() {
      try {
         JsonObject chatConfig = this.getChatConfigSection();
         if (chatConfig.has("antiSpam")) {
            JsonObject antiSpam = chatConfig.getAsJsonObject("antiSpam");
            if (antiSpam.has("capsFilter")) {
               return antiSpam.getAsJsonObject("capsFilter").get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   private JsonObject getSafeJsonObject(JsonObject parent, String key) {
      if (parent != null && parent.has(key) && !parent.get(key).isJsonNull()) {
         try {
            return parent.getAsJsonObject(key);
         } catch (Exception var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   private JsonObject getSpamFilterConfig() {
      JsonObject chatConfig = this.getChatConfigSection();
      JsonObject antiSpam = this.getSafeJsonObject(chatConfig, "antiSpam");
      return this.getSafeJsonObject(antiSpam, "spamFilter");
   }

   private JsonObject getRepeatFilterConfig() {
      JsonObject chatConfig = this.getChatConfigSection();
      JsonObject antiSpam = this.getSafeJsonObject(chatConfig, "antiSpam");
      return this.getSafeJsonObject(antiSpam, "repeatFilter");
   }

   private JsonObject getLinkFilterConfig() {
      JsonObject chatConfig = this.getChatConfigSection();
      JsonObject antiSpam = this.getSafeJsonObject(chatConfig, "antiSpam");
      return this.getSafeJsonObject(antiSpam, "linkFilter");
   }

   private JsonObject getCapsFilterConfig() {
      JsonObject chatConfig = this.getChatConfigSection();
      JsonObject antiSpam = this.getSafeJsonObject(chatConfig, "antiSpam");
      return this.getSafeJsonObject(antiSpam, "capsFilter");
   }

   private JsonObject getChatConfigSection() {
      try {
         JsonObject rawConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (rawConfig == null) {
            return null;
         } else {
            return rawConfig.has("chat") ? rawConfig.getAsJsonObject("chat") : rawConfig;
         }
      } catch (Exception var2) {
         return null;
      }
   }

   public static class FilterResult {
      public final boolean allowed;
      public final String filteredMessage;
      public final String denyReason;

      public FilterResult(boolean allowed, String filteredMessage, String denyReason) {
         this.allowed = allowed;
         this.filteredMessage = filteredMessage;
         this.denyReason = denyReason;
      }
   }

   private static class MessageRateTracker {
      private final int maxMessages;
      private final long periodMs;
      private final Deque<Long> messageTimes = new ConcurrentLinkedDeque<>();

      public MessageRateTracker(int maxMessages, int periodSeconds) {
         this.maxMessages = maxMessages;
         this.periodMs = (long)periodSeconds * 1000L;
      }

      public boolean allowMessage() {
         long now = System.currentTimeMillis();
         long cutoff = now - this.periodMs;
         this.messageTimes.removeIf(time -> time < cutoff);
         if (this.messageTimes.size() >= this.maxMessages) {
            return false;
         } else {
            this.messageTimes.add(now);
            return true;
         }
      }
   }
}
