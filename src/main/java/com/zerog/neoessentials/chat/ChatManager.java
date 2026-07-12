package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChatManager.class);
   private final Set<String> mutedCommands;
   private final Set<String> playerChatPermissions;
   private final boolean sleepIgnoresAfkPlayers;
   private final boolean sleepIgnoresVanishedPlayers;
   private final String afkListName;
   private final boolean broadcastAfkMessage;
   private final boolean deathMessages;
   private final String vanishingItemsPolicy;
   private final String bindingItemsPolicy;
   private final boolean sendInfoAfterDeath;
   private final boolean allowSilentJoinQuit;
   private final String customJoinMessage;
   private final String customQuitMessage;
   private final String customNewUsernameMessage;
   private final boolean useCustomServerFullMessage;
   private final int hideJoinQuitMessagesAbove;
   private final String defaultChatFormat;
   private final Map<String, String> chatFormatMap;
   private final JsonObject commandsConfig;

   public Set<String> getPlayerChatPermissions() {
      return this.playerChatPermissions;
   }

   public ChatManager(JsonObject chatConfig, JsonObject commandsConfig) {
      this.mutedCommands = this.toSet(chatConfig, "muteCommands");
      this.playerChatPermissions = this.toSet(chatConfig, "playerChatPermissions");
      this.sleepIgnoresAfkPlayers = chatConfig.has("sleepIgnoresAfkPlayers") && chatConfig.get("sleepIgnoresAfkPlayers").getAsBoolean();
      this.sleepIgnoresVanishedPlayers = chatConfig.has("sleepIgnoresVanishedPlayers") && chatConfig.get("sleepIgnoresVanishedPlayers").getAsBoolean();
      this.afkListName = chatConfig.has("afkListName") ? chatConfig.get("afkListName").getAsString() : "none";
      this.broadcastAfkMessage = chatConfig.has("broadcastAfkMessage") && chatConfig.get("broadcastAfkMessage").getAsBoolean();
      this.deathMessages = chatConfig.has("deathMessages") && chatConfig.get("deathMessages").getAsBoolean();
      this.vanishingItemsPolicy = chatConfig.has("vanishingItemsPolicy") ? chatConfig.get("vanishingItemsPolicy").getAsString() : "keep";
      this.bindingItemsPolicy = chatConfig.has("bindingItemsPolicy") ? chatConfig.get("bindingItemsPolicy").getAsString() : "keep";
      this.sendInfoAfterDeath = chatConfig.has("sendInfoAfterDeath") && chatConfig.get("sendInfoAfterDeath").getAsBoolean();
      this.allowSilentJoinQuit = chatConfig.has("allowSilentJoinQuit") && chatConfig.get("allowSilentJoinQuit").getAsBoolean();
      this.customJoinMessage = chatConfig.has("customJoinMessage") ? chatConfig.get("customJoinMessage").getAsString() : "none";
      this.customQuitMessage = chatConfig.has("customQuitMessage") ? chatConfig.get("customQuitMessage").getAsString() : "none";
      this.customNewUsernameMessage = chatConfig.has("customNewUsernameMessage") ? chatConfig.get("customNewUsernameMessage").getAsString() : "none";
      this.useCustomServerFullMessage = chatConfig.has("useCustomServerFullMessage") && chatConfig.get("useCustomServerFullMessage").getAsBoolean();
      this.hideJoinQuitMessagesAbove = chatConfig.has("hideJoinQuitMessagesAbove") ? chatConfig.get("hideJoinQuitMessagesAbove").getAsInt() : -1;
      if (chatConfig.has("chat-format")) {
         if (chatConfig.get("chat-format").isJsonObject()) {
            JsonObject obj = chatConfig.getAsJsonObject("chat-format");
            Map<String, String> map = new ConcurrentHashMap<>();
            String def = null;

            for (String key : obj.keySet()) {
               if (key.equalsIgnoreCase("default")) {
                  def = obj.get(key).getAsString();
               } else {
                  map.put(key, obj.get(key).getAsString());
               }
            }

            this.defaultChatFormat = def != null ? def : "{neoessentials_displayname}: {MESSAGE}";
            this.chatFormatMap = map;
            LOGGER.info("Loaded chat-format (object): default=[{}], map size={}", this.defaultChatFormat, map.size());
         } else {
            this.defaultChatFormat = chatConfig.get("chat-format").getAsString();
            this.chatFormatMap = Collections.emptyMap();
            LOGGER.info("Loaded chat-format (string): [{}]", this.defaultChatFormat);
         }
      } else {
         this.defaultChatFormat = "{neoessentials_displayname}: {MESSAGE}";
         this.chatFormatMap = Collections.emptyMap();
         LOGGER.info("No chat-format in config, using default: [{}]", this.defaultChatFormat);
      }

      this.commandsConfig = commandsConfig;
   }

   private Set<String> toSet(JsonObject obj, String key) {
      if (obj.has(key) && obj.get(key).isJsonArray()) {
         Set<String> set = ConcurrentHashMap.newKeySet();
         obj.getAsJsonArray(key).forEach(e -> set.add(e.getAsString()));
         return set;
      } else {
         return Collections.emptySet();
      }
   }

   public boolean isCommandMuted(String command) {
      return this.mutedCommands.contains(command);
   }

   public boolean hasChatPermission(String permission) {
      return this.playerChatPermissions.contains(permission);
   }

   public boolean shouldSleepIgnoreAfk() {
      return this.sleepIgnoresAfkPlayers;
   }

   public boolean shouldSleepIgnoreVanished() {
      return this.sleepIgnoresVanishedPlayers;
   }

   public String getAfkListName() {
      return this.afkListName;
   }

   public boolean shouldBroadcastAfk() {
      return this.broadcastAfkMessage;
   }

   public boolean showDeathMessages() {
      return this.deathMessages;
   }

   public String getVanishingItemsPolicy() {
      return this.vanishingItemsPolicy;
   }

   public String getBindingItemsPolicy() {
      return this.bindingItemsPolicy;
   }

   public boolean shouldSendInfoAfterDeath() {
      return this.sendInfoAfterDeath;
   }

   public boolean allowSilentJoinQuit() {
      return this.allowSilentJoinQuit;
   }

   public String getCustomJoinMessage() {
      return this.customJoinMessage;
   }

   public String getCustomQuitMessage() {
      return this.customQuitMessage;
   }

   public String getCustomNewUsernameMessage() {
      return this.customNewUsernameMessage;
   }

   public boolean useCustomServerFullMessage() {
      return this.useCustomServerFullMessage;
   }

   public int getHideJoinQuitMessagesAbove() {
      return this.hideJoinQuitMessagesAbove;
   }

   public String getChatFormat(String group, String world) {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("formatTemplates")) {
            JsonObject templates = chatConfig.getAsJsonObject("formatTemplates");
            if (templates.has("enabled") && templates.get("enabled").getAsBoolean()) {
               String activeTemplate = templates.has("activeTemplate") ? templates.get("activeTemplate").getAsString() : "default";
               if (templates.has("templates")) {
                  JsonObject templateMap = templates.getAsJsonObject("templates");
                  if (templateMap.has(activeTemplate)) {
                     return templateMap.get(activeTemplate).getAsString();
                  }
               }
            }
         }
      } catch (Exception var7) {
      }

      if (group != null && world != null) {
         String key = "group:" + group.toLowerCase() + ":world:" + world.toLowerCase();
         if (this.chatFormatMap.containsKey(key)) {
            return this.chatFormatMap.get(key);
         }
      }

      if (group != null) {
         String key = "group:" + group.toLowerCase();
         if (this.chatFormatMap.containsKey(key)) {
            return this.chatFormatMap.get(key);
         }
      }

      if (world != null) {
         String key = "world:" + world.toLowerCase();
         if (this.chatFormatMap.containsKey(key)) {
            return this.chatFormatMap.get(key);
         }
      }

      return this.defaultChatFormat;
   }

   public String getDefaultChatFormat() {
      return this.defaultChatFormat;
   }

   public boolean isAfkEnabled() {
      return this.isCommandEnabled("afk");
   }

   public boolean isIgnoreEnabled() {
      return this.isCommandEnabled("ignore");
   }

   public boolean isMsgEnabled() {
      return this.isCommandEnabled("msg");
   }

   public boolean isMsgToggleEnabled() {
      return this.isCommandEnabled("msgtoggle");
   }

   public boolean isMuteEnabled() {
      return this.isCommandEnabled("mute");
   }

   public boolean isMuteListEnabled() {
      return this.isCommandEnabled("mutelist");
   }

   public boolean isReplyEnabled() {
      return this.isCommandEnabled("reply");
   }

   public boolean isSocialSpyEnabled() {
      return this.isCommandEnabled("socialspy");
   }

   public boolean isUnignoreEnabled() {
      return this.isCommandEnabled("unignore");
   }

   public boolean isUnmuteEnabled() {
      return this.isCommandEnabled("unmute");
   }

   private boolean isCommandEnabled(String command) {
      return this.commandsConfig != null && this.commandsConfig.has(command) && this.commandsConfig.get(command).getAsBoolean();
   }
}
