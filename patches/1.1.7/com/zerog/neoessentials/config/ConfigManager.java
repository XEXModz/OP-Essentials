package com.zerog.neoessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
   private final ConcurrentHashMap<String, JsonObject> configCache = new ConcurrentHashMap<>();
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   public static final String MAIN_CONFIG = "config.json";
   public static final String ECONOMY_CONFIG = "economy.json";
   public static final String PERMISSIONS_CONFIG = "permissions.json";
   public static final String KITS_CONFIG = "kits.json";
   public static final String DISCORD_AUTH_CONFIG = "discord_auth.json";
   public static final String TABLIST_CONFIG = "tablist.json";
   public static final String CHAT_CONFIG = "chat.json";
   private static final String CONFIG_VERSION_KEY = "_configVersion";
   private static final Map<String, Integer> EXPECTED_CONFIG_VERSIONS = new HashMap<String, Integer>() {
      {
         this.put("config.json", Integer.valueOf(20));
         this.put("economy.json", Integer.valueOf(2));
         this.put("permissions.json", Integer.valueOf(5));
         this.put("kits.json", Integer.valueOf(1));
         this.put("discord_auth.json", Integer.valueOf(6));
         this.put("tablist.json", Integer.valueOf(1));
      }
   };

   public static boolean isLogKickActionsEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("logKickActions")) {
            return kickSettings.get("logKickActions").getAsBoolean();
         }
      }

      return true;
   }

   public static String getKickMessage() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("kickMessage")) {
            String val = kickSettings.get("kickMessage").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "You have been kicked from the server.\nReason: {reason}\nKicked by: {kicker}";
   }

   public static String getKickAllMessage() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("kickAllMessage")) {
            String val = kickSettings.get("kickAllMessage").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "Server maintenance in progress. Please reconnect in a few minutes.";
   }

   public static boolean isNotifyStaffOnKickEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("notifyStaffOnKick")) {
            return kickSettings.get("notifyStaffOnKick").getAsBoolean();
         }
      }

      return true;
   }

   public static String getDefaultKickReason() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("defaultKickReason")) {
            String val = kickSettings.get("defaultKickReason").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "Kicked by an operator";
   }

   public static int getMaxKickReasonLength() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("maxKickReason")) {
            try {
               int val = kickSettings.get("maxKickReason").getAsInt();
               if (val > 0) {
                  return val;
               }
            } catch (Exception var3) {
            }
         }
      }

      return 500;
   }

   public static boolean isBroadcastKicksEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("broadcastKicks")) {
            return kickSettings.get("broadcastKicks").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isKickSystemEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("kickSettings")) {
         JsonObject kickSettings = config.getAsJsonObject("moderation").getAsJsonObject("kickSettings");
         if (kickSettings.has("enableKickSystem")) {
            return kickSettings.get("enableKickSystem").getAsBoolean();
         }
      }

      return true;
   }

   public static String getFreezeMessage() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("freezeMessage")) {
            String val = freezeSettings.get("freezeMessage").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "neoessentials.moderation.frozen_message";
   }

   public static String getUnfreezeMessage() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("unfreezeMessage")) {
            String val = freezeSettings.get("unfreezeMessage").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "neoessentials.moderation.unfrozen_message";
   }

   public static String getFreezeReminder() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("freezeReminder")) {
            String val = freezeSettings.get("freezeReminder").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "neoessentials.moderation.freeze_reminder";
   }

   public static String getDefaultFreezeReason() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("defaultFreezeReason")) {
            String val = freezeSettings.get("defaultFreezeReason").getAsString();
            if (val != null && !val.trim().isEmpty()) {
               return val;
            }
         }
      }

      return "Frozen by an operator";
   }

   public static int getMaxFreezeReasonLength() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("maxFreezeReason")) {
            try {
               int val = freezeSettings.get("maxFreezeReason").getAsInt();
               if (val > 0) {
                  return val;
               }
            } catch (Exception var3) {
            }
         }
      }

      return 500;
   }

   public static int getFreezeReminderInterval() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("freezeReminderInterval")) {
            try {
               int val = freezeSettings.get("freezeReminderInterval").getAsInt();
               if (val >= 0) {
                  return val;
               }
            } catch (Exception var3) {
            }
         }
      }

      return 30;
   }

   public static boolean isLogFreezeActionsEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("logFreezeActions")) {
            return freezeSettings.get("logFreezeActions").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isFreezeOnLoginEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("freezeOnLogin")) {
            return freezeSettings.get("freezeOnLogin").getAsBoolean();
         }
      }

      return true;
   }

   public static List<String> getFreezeAllowedCommands() {
      List<String> allowed = new ArrayList<>();
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("allowedCommands") && freezeSettings.get("allowedCommands").isJsonArray()) {
            for (JsonElement el : freezeSettings.getAsJsonArray("allowedCommands")) {
               if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                  allowed.add(el.getAsString().toLowerCase());
               }
            }
         }
      }

      return allowed;
   }

   public static boolean isFreezeSystemEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("enableFreezeSystem")) {
            return freezeSettings.get("enableFreezeSystem").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isFreezePreventCommandsEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("freezeSettings")) {
         JsonObject freezeSettings = config.getAsJsonObject("moderation").getAsJsonObject("freezeSettings");
         if (freezeSettings.has("preventCommands")) {
            return freezeSettings.get("preventCommands").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isVanishPreventInteractionEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("preventInteraction")) {
            return vanishSettings.get("preventInteraction").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isBroadcastToStaffVanishEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("broadcastToStaffVanish")) {
            return vanishSettings.get("broadcastToStaffVanish").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isBroadcastToAllVanishEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("BroadcastToAllVanish")) {
            return vanishSettings.get("BroadcastToAllVanish").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isHideFromTabListEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("hideFromTabList")) {
            return vanishSettings.get("hideFromTabList").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isLogVanishActionsEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("logVanishActions")) {
            return vanishSettings.get("logVanishActions").getAsBoolean();
         }
      }

      return true;
   }

   public boolean isVanishOnJoinEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation") && config.getAsJsonObject("moderation").has("vanishSettings")) {
         JsonObject vanishSettings = config.getAsJsonObject("moderation").getAsJsonObject("vanishSettings");
         if (vanishSettings.has("vanishOnJoin")) {
            return vanishSettings.get("vanishOnJoin").getAsBoolean();
         }
      }

      return false;
   }

   public boolean isVanishSystemEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("vanishSettings")) {
            JsonObject vanishSettings = moderation.getAsJsonObject("vanishSettings");
            if (vanishSettings.has("enableVanishSystem")) {
               return vanishSettings.get("enableVanishSystem").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isLogJailActionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("logJailActions")) {
               return jailSettings.get("logJailActions").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isPreventJailEscapeEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("preventJailEscape")) {
               return jailSettings.get("preventJailEscape").getAsBoolean();
            }
         }
      }

      return false;
   }

   public String getJailMessageFormat() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("jailMessageFormat")) {
               String val = jailSettings.get("jailMessageFormat").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "You cannot leave jail!";
   }

   public static int getMaxJailsBeforeTempBan() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("maxJailsBeforeTempBan")) {
               return jailSettings.get("maxJailsBeforeTempBan").getAsInt();
            }
         }
      }

      return 3;
   }

   public boolean isJailTeleportOnLoginEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("jailTeleportOnLogin")) {
               return jailSettings.get("jailTeleportOnLogin").getAsBoolean();
            }
         }
      }

      return true;
   }

   public String getStaffNotificationPermission() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("generalSettings")) {
            JsonObject general = moderation.getAsJsonObject("generalSettings");
            if (general.has("staffNotificationPermission")) {
               String val = general.get("staffNotificationPermission").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "neoessentials.moderation.notify";
   }

   public boolean isBroadcastBansEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("broadcastBans")) {
               return banSettings.get("broadcastBans").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isLogBanActionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("logBanActions")) {
               return banSettings.get("logBanActions").getAsBoolean();
            }
         }
      }

      return true;
   }

   public int getCheckExpiredBansInterval() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("checkExpiredBansInterval")) {
               try {
                  int val = banSettings.get("checkExpiredBansInterval").getAsInt();
                  if (val <= 0) {
                     return 0;
                  }

                  return Math.max(val, 5);
               } catch (Exception var5) {
               }
            }
         }
      }

      return 300;
   }

   public String getDefaultBanReason() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("defaultBanReason")) {
               String val = banSettings.get("defaultBanReason").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "Banned by an operator";
   }

   public int getMaxBanReasonLength() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("maxBanReason")) {
               try {
                  int val = banSettings.get("maxBanReason").getAsInt();
                  if (val > 0) {
                     return val;
                  }
               } catch (Exception var5) {
               }
            }
         }
      }

      return 500;
   }

   public boolean isIPBansEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("enableIPBans")) {
               return banSettings.get("enableIPBans").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isPermanentBansEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("enablePermanentBans")) {
               return banSettings.get("enablePermanentBans").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isTempBansEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("enableTempBans")) {
               return banSettings.get("enableTempBans").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isAutoExpireTempBansEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("autoExpireTempBans")) {
               return banSettings.get("autoExpireTempBans").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean getEnableParticleEffects() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("generalSettings")) {
            JsonObject general = tp.getAsJsonObject("generalSettings");
            if (general.has("enableParticleEffects")) {
               return general.get("enableParticleEffects").getAsBoolean();
            }
         }
      }

      return true;
   }

   public int getMaxTeleportDistance() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("generalSettings")) {
            JsonObject general = tp.getAsJsonObject("generalSettings");
            if (general.has("maxTeleportDistance")) {
               try {
                  return general.get("maxTeleportDistance").getAsInt();
               } catch (Exception var5) {
               }
            }
         }
      }

      return -1;
   }

   public boolean isAllowTeleportInCombatEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("generalSettings")) {
            JsonObject general = tp.getAsJsonObject("generalSettings");
            if (general.has("allowTeleportInCombat")) {
               return general.get("allowTeleportInCombat").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isLogTeleportRequestsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("logTeleportRequests")) {
               return req.get("logTeleportRequests").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isAutoAcceptTeleportFromFriendsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("autoAcceptFromFriends")) {
               return req.get("autoAcceptFromFriends").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isTeleportRequestNotificationsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("enableRequestNotifications")) {
               return req.get("enableRequestNotifications").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isAllowMultipleTeleportRequestsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("allowMultipleRequests")) {
               return req.get("allowMultipleRequests").getAsBoolean();
            }
         }
      }

      return false;
   }

   public int getMaxPendingTeleportRequests() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("maxPendingRequests")) {
               try {
                  int val = req.get("maxPendingRequests").getAsInt();
                  if (val > 0) {
                     return val;
                  }
               } catch (Exception var5) {
               }
            }
         }
      }

      return 5;
   }

   public int getTeleportRequestTimeoutSeconds() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("requestTimeout")) {
               try {
                  int val = req.get("requestTimeout").getAsInt();
                  if (val > 0) {
                     return val;
                  }
               } catch (Exception var5) {
               }
            }
         }
      }

      return 60;
   }

   public int getCooldownBetweenTeleportRequestsSeconds() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("teleportRequestSettings")) {
            JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
            if (req.has("cooldownBetweenRequests")) {
               try {
                  int val = req.get("cooldownBetweenRequests").getAsInt();
                  if (val >= 0) {
                     return val;
                  }
               } catch (Exception var5) {
               }
            }
         }
      }

      return 10;
   }

   public boolean isLogSpawnActionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("spawnSettings")) {
            JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
            if (spawnSettings.has("logSpawnActions")) {
               return spawnSettings.get("logSpawnActions").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isCancelOnDamageEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("generalSettings")) {
            JsonObject general = tp.getAsJsonObject("generalSettings");
            if (general.has("cancelOnDamage")) {
               return general.get("cancelOnDamage").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isTeleportationEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("teleportationEnabled")) {
            return modules.get("teleportationEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public boolean isRequireConfirmationForDeleteEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("homeSettings")) {
            JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
            if (homeSettings.has("requireConfirmationForDelete")) {
               return homeSettings.get("requireConfirmationForDelete").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isLogHomeActionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("homeSettings")) {
            JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
            if (homeSettings.has("logHomeActions")) {
               return homeSettings.get("logHomeActions").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isNewPlayerKitEnabled() {
      JsonObject config = this.getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("newPlayerKit")) {
            JsonObject npk = kits.getAsJsonObject("newPlayerKit");
            if (npk.has("enabled")) {
               return npk.get("enabled").getAsBoolean();
            }
         }
      }

      return false;
   }

   public String getNewPlayerKitName() {
      JsonObject config = this.getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("newPlayerKit")) {
            JsonObject npk = kits.getAsJsonObject("newPlayerKit");
            if (npk.has("kitName")) {
               return npk.get("kitName").getAsString();
            }
         }
      }

      return "";
   }

   public int getMaxKitsPerPlayer() {
      JsonObject config = this.getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("maxKitsPerPlayer")) {
            try {
               return kits.get("maxKitsPerPlayer").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return -1;
   }

   public boolean isAfkEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("afk")) {
         JsonObject afk = config.getAsJsonObject("afk");
         if (afk.has("enabled")) {
            return afk.get("enabled").getAsBoolean();
         }
      }

      return true;
   }

   public int getPermissionCacheExpiryMinutes() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("permissions")) {
         JsonObject perms = config.getAsJsonObject("permissions");
         if (perms.has("permissionCacheExpiryMinutes")) {
            try {
               int val = perms.get("permissionCacheExpiryMinutes").getAsInt();
               if (val > 0) {
                  return val;
               }
            } catch (Exception var4) {
            }
         }
      }

      return 5;
   }

   public boolean isPermissionCacheEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("permissions")) {
         JsonObject perms = config.getAsJsonObject("permissions");
         if (perms.has("cachePermissions")) {
            return perms.get("cachePermissions").getAsBoolean();
         }
      }

      return true;
   }

   public boolean isOpsBypassPermissionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("permissions")) {
         JsonObject perms = config.getAsJsonObject("permissions");
         if (perms.has("opsBypassPermissions")) {
            return perms.get("opsBypassPermissions").getAsBoolean();
         }
      }

      return true;
   }

   public String getDefaultGroup() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("permissions")) {
         JsonObject perms = config.getAsJsonObject("permissions");
         if (perms.has("defaultGroup")) {
            String group = perms.get("defaultGroup").getAsString();
            if (group != null && !group.trim().isEmpty()) {
               return group.trim();
            }
         }
      }

      return "default";
   }

   public boolean isCommandEnabled(String command) {
      JsonObject config = this.getConfig("config.json");
      if (config.has("commands")) {
         JsonObject commands = config.getAsJsonObject("commands");
         if (commands.has(command)) {
            return commands.get(command).getAsBoolean();
         }
      }

      return true;
   }

   public boolean isAllowKitOverrideEnabled() {
      JsonObject config = this.getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("allowKitOverride")) {
            return kits.get("allowKitOverride").getAsBoolean();
         }
      }

      return false;
   }


   // BUGFIX 1.1.7: in split-config mode the merged view exposes kits.json's kit
   // ARRAY under "kits"; these getters used to cast it to an object ->
   // ClassCastException on every join (newPlayerKit never fired). Kit SETTINGS
   // now live in kits.json under a top-level "settings" object.
   private static JsonObject kitSettings(JsonObject config) {
      com.google.gson.JsonElement ke = config.get("kits");
      if (ke != null && ke.isJsonObject()) {
         return ke.getAsJsonObject();
      }

      try {
         JsonObject kcfg = getInstance().getConfig("kits.json");
         if (kcfg != null && kcfg.has("settings") && kcfg.get("settings").isJsonObject()) {
            return kcfg.getAsJsonObject("settings");
         }
      } catch (Exception var3) {
      }

      return new JsonObject();
   }

   public JsonObject getConfig(String configName) {
      this.lock.readLock().lock();
      FileReader reader = null;

      JsonObject obj;
      try {
         if (this.configCache.containsKey(configName)) {
            return this.configCache.get(configName);
         }

         if (!configName.equals("config.json") || !ConfigSplitter.isSplittingEnabled()) {
            File file = ResourceUtil.getConfigFile(configName);
            reader = new FileReader(file, StandardCharsets.UTF_8);
            obj = JsonParser.parseReader(reader).getAsJsonObject();
            this.configCache.put(configName, obj);
            return obj;
         }

         JsonObject merged = ConfigSplitter.mergeSplitConfigs();
         this.configCache.put(configName, merged);
         obj = merged;
      } catch (IOException var17) {
         LOGGER.error("Failed to read config file {}: {}", configName, var17.getMessage());
         obj = new JsonObject();
         this.configCache.put(configName, obj);
         return obj;
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException var16) {
            }
         }

         this.lock.readLock().unlock();
      }

      return obj;
   }

   public static ConfigManager getInstance() {
      return ConfigManager.SingletonHolder.INSTANCE;
   }

   private ConfigManager() {
      this.ensureDefaultConfigs();
   }

   private void ensureDefaultConfigs() {
      String[] requiredConfigs = new String[]{"config.json", "economy.json", "permissions.json", "kits.json", "discord_auth.json", "tablist.json"};
      boolean splitConfigsEnabled = ConfigSplitter.isSplittingEnabled();
      boolean externalPermsEnabled = false;

      try {
         externalPermsEnabled = this.isExternalPermissionsEnabled();
      } catch (Exception var9) {
      }

      if (splitConfigsEnabled) {
         LOGGER.info("Split configs enabled - ensuring all split config files are up to date");
         ConfigSplitter.ensureSplitConfigsUpToDate();

         for (String configName : requiredConfigs) {
            if (!configName.equals("config.json") && (!configName.equals("permissions.json") || !externalPermsEnabled)) {
               File configFile = ResourceUtil.getConfigFile(configName);
               if (!configFile.exists()) {
                  this.copyDefaultConfig(configName, configFile);
               } else {
                  this.checkAndUpdateConfigVersion(configName, configFile);
               }
            }
         }
      } else {
         for (String configNamex : requiredConfigs) {
            if (!configNamex.equals("permissions.json") || !externalPermsEnabled) {
               File configFile = ResourceUtil.getConfigFile(configNamex);
               if (!configFile.exists()) {
                  this.copyDefaultConfig(configNamex, configFile);
               } else {
                  this.checkAndUpdateConfigVersion(configNamex, configFile);
               }
            }
         }
      }
   }

   private void checkAndUpdateConfigVersion(String configName, File configFile) {
      Integer expectedVersion = EXPECTED_CONFIG_VERSIONS.get(configName);
      if (expectedVersion != null) {
         try {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
               JsonObject onDisk = JsonParser.parseReader(reader).getAsJsonObject();
               int currentVersion = 0;
               if (onDisk.has("_configVersion")) {
                  currentVersion = onDisk.get("_configVersion").getAsInt();
               }

               if (currentVersion >= expectedVersion) {
                  if (currentVersion > expectedVersion) {
                     LOGGER.warn(
                        "Config file {} has a newer version ({}) than expected ({}). This may indicate a downgrade.",
                        new Object[]{configName, currentVersion, expectedVersion}
                     );
                  } else {
                     LOGGER.debug("Config file {} is up to date (version {})", configName, currentVersion);
                  }

                  return;
               }

               LOGGER.warn(
                  "Config file {} is outdated (version {} < {}). Merging new keys from JAR template (user values preserved)...",
                  new Object[]{configName, currentVersion, expectedVersion}
               );
               JsonObject jarTemplate = null;

               try (InputStream in = ResourceUtil.getJarConfigResource(configName)) {
                  if (in != null) {
                     jarTemplate = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                  }
               } catch (Exception var17) {
                  LOGGER.error("Could not load JAR template for {}: {}", configName, var17.getMessage());
               }

               if (jarTemplate != null) {
                  this.createConfigBackup(configFile, currentVersion);
                  boolean changed = this.mergeNewKeys(jarTemplate, onDisk);
                  onDisk.addProperty("_configVersion", expectedVersion);

                  try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                     new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(onDisk, writer);
                  }

                  this.configCache.remove(configName);
                  LOGGER.info("Config file {} merged to version {} ({} new key(s) added).", new Object[]{configName, expectedVersion, changed ? "some" : "no"});
                  MessageUtil.ensureLanguageFileUpToDate();
                  return;
               }

               LOGGER.warn("JAR template not found for {}. Skipping update.", configName);
            }
         } catch (Exception var19) {
            LOGGER.error("Failed to check/update version for config {}: {}", new Object[]{configName, var19.getMessage(), var19});
         }
      }
   }

   private boolean mergeNewKeys(JsonObject source, JsonObject target) {
      boolean changed = false;

      for (Entry<String, JsonElement> entry : source.entrySet()) {
         String key = entry.getKey();
         JsonElement sourceVal = entry.getValue();
         if (!target.has(key)) {
            target.add(key, sourceVal.deepCopy());
            changed = true;
            LOGGER.debug("  + Added missing config key: {}", key);
         } else if (sourceVal.isJsonObject() && target.get(key).isJsonObject()) {
            changed |= this.mergeNewKeys(sourceVal.getAsJsonObject(), target.get(key).getAsJsonObject());
         }
      }

      return changed;
   }

   private void createConfigBackup(File configFile, int oldVersion) {
      try {
         String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
         String backupName = configFile.getName().replace(".json", String.format("_v%d_backup_%s.json", oldVersion, timestamp));
         File backupFile = new File(configFile.getParentFile(), backupName);
         Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         LOGGER.info("Created backup of old config: {}", backupFile.getName());
      } catch (Exception var6) {
         LOGGER.error("Failed to create backup for {}: {}", configFile.getName(), var6.getMessage());
      }
   }

   private void copyDefaultConfig(String configName, File configFile) {
      try (InputStream in = ResourceUtil.getJarConfigResource(configName)) {
         if (in == null) {
            LOGGER.warn("Default config resource not found in JAR: {}", configName);
         } else {
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
               LOGGER.warn("Failed to create parent directories for {}", configFile.getAbsolutePath());
            }

            try (OutputStream out = new FileOutputStream(configFile)) {
               byte[] buffer = new byte[8192];

               int len;
               while ((len = in.read(buffer)) > 0) {
                  out.write(buffer, 0, len);
               }
            }

            LOGGER.info("Copied default config {} to {}", configName, configFile.getAbsolutePath());
         }
      } catch (Exception var12) {
         LOGGER.error("Failed to copy default config {}: {}", configName, var12.getMessage());
      }
   }

   public boolean isExternalPermissionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("permissions")) {
         JsonObject perms = config.getAsJsonObject("permissions");
         if (perms.has("useExternalPermissions")) {
            return perms.get("useExternalPermissions").getAsBoolean();
         }
      }

      return false;
   }

   public boolean isXSSProtectionEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("enableXSSProtection")) {
            return security.get("enableXSSProtection").getAsBoolean();
         }
      }

      return true;
   }

   public boolean isInputValidationEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("enableInputValidation")) {
            return security.get("enableInputValidation").getAsBoolean();
         }
      }

      return true;
   }

   public boolean isCommandLengthEnforcerEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("enableCommandLengthEnforcer")) {
            return security.get("enableCommandLengthEnforcer").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isChatFormattingEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("chat")) {
         JsonObject chat = config.getAsJsonObject("chat");
         if (chat.has("enable-chat-formatting")) {
            return chat.get("enable-chat-formatting").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isColorCodesEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("chat")) {
         JsonObject chat = config.getAsJsonObject("chat");
         if (chat.has("enable-color-codes")) {
            return chat.get("enable-color-codes").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isEconomyEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("economyEnabled")) {
            return modules.get("economyEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isChestShopEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("chestShopEnabled")) {
            return modules.get("chestShopEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static double getEconomyStartingBalance() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("startingBalance")) {
         try {
            return config.get("startingBalance").getAsDouble();
         } catch (Exception var2) {
         }
      }

      return 100.0;
   }

   public static String getCurrencySymbol() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("currencySymbol")) {
         String symbol = config.get("currencySymbol").getAsString();
         if (symbol != null && !symbol.isEmpty()) {
            return symbol;
         }
      }

      return "$";
   }

   public static double getMaxBalance() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("maxBalance")) {
         try {
            return config.get("maxBalance").getAsDouble();
         } catch (Exception var2) {
         }
      }

      return 9.9999999999E8;
   }

   public static double getTaxPercentage() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("taxPercentage")) {
         try {
            return config.get("taxPercentage").getAsDouble();
         } catch (Exception var2) {
         }
      }

      return 0.0;
   }

   public static double getEconomyTaxPercentage() {
      return getTaxPercentage();
   }

   public static boolean allowNegativeBalances() {
      JsonObject config = getInstance().getConfig("economy.json");
      return config.has("allowNegativeBalances") ? config.get("allowNegativeBalances").getAsBoolean() : false;
   }

   public static boolean isCleanupInactiveAccountsEnabled() {
      JsonObject config = getInstance().getConfig("economy.json");
      return config.has("cleanupInactiveAccounts") ? config.get("cleanupInactiveAccounts").getAsBoolean() : true;
   }

   public static int getInactiveAccountCleanupDays() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("inactiveAccountCleanupDays")) {
         try {
            return config.get("inactiveAccountCleanupDays").getAsInt();
         } catch (Exception var2) {
         }
      }

      return 30;
   }

   public static double getMaxTransferAmount() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("maxTransferAmount")) {
         try {
            return config.get("maxTransferAmount").getAsDouble();
         } catch (Exception var2) {
         }
      }

      return 10000.0;
   }

   public static boolean getPayToggleDefault() {
      JsonObject config = getInstance().getConfig("economy.json");
      return config.has("paytoggleDefault") ? config.get("paytoggleDefault").getAsBoolean() : true;
   }

   public static int getCacheMaximumSize() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("cacheMaximumSize")) {
         try {
            return config.get("cacheMaximumSize").getAsInt();
         } catch (Exception var2) {
         }
      }

      return 10000;
   }

   public static int getCacheExpireAfterAccessMinutes() {
      JsonObject config = getInstance().getConfig("economy.json");
      if (config.has("cacheExpireAfterAccessMinutes")) {
         try {
            return config.get("cacheExpireAfterAccessMinutes").getAsInt();
         } catch (Exception var2) {
         }
      }

      return 60;
   }

   public static int getPayCooldownSeconds() {
      return 0;
   }

   public void clearCache() {
      this.lock.writeLock().lock();

      try {
         this.configCache.clear();
         LOGGER.info("Configuration cache cleared - configs will be reloaded from disk");
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public static void loadAll() {
      getInstance().clearCache();
      getInstance().ensureDefaultConfigs();
   }

   public static boolean isChatEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("chatEnabled")) {
            return modules.get("chatEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isModerationEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("moderationEnabled")) {
            return modules.get("moderationEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isWebDashboardEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("enabled")) {
            return dashboard.get("enabled").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isUnsafeEnchantsAllowed() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("items")) {
         JsonObject items = config.getAsJsonObject("items");
         if (items.has("unsafe-enchantments")) {
            return items.get("unsafe-enchantments").getAsBoolean();
         }
      }

      return true;
   }

   public static int getDefaultStackSize() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("items")) {
         JsonObject items = config.getAsJsonObject("items");
         if (items.has("default-stack-size")) {
            try {
               return items.get("default-stack-size").getAsInt();
            } catch (Exception var3) {
            }
         }
      }

      return -1;
   }

   public static int getOversizedStackSize() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("items")) {
         JsonObject items = config.getAsJsonObject("items");
         if (items.has("oversized-stacksize")) {
            try {
               return items.get("oversized-stacksize").getAsInt();
            } catch (Exception var3) {
            }
         }
      }

      return 64;
   }

   public static List<String> getItemSpawnBlacklist() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("items")) {
         JsonObject items = config.getAsJsonObject("items");
         if (items.has("item-spawn-blacklist") && items.get("item-spawn-blacklist").isJsonArray()) {
            List<String> list = new ArrayList<>();
            items.getAsJsonArray("item-spawn-blacklist").forEach(e -> list.add(e.getAsString()));
            return list;
         }
      }

      return Collections.emptyList();
   }

   public static boolean isPermissionBasedItemSpawn() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("items")) {
         JsonObject items = config.getAsJsonObject("items");
         if (items.has("permission-based-item-spawn")) {
            return items.get("permission-based-item-spawn").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isKitModuleEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("kitsEnabled")) {
            return modules.get("kitsEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isKitSystemEnabled() {
      return isKitModuleEnabled();
   }

   public static double getKitCommandCost(String commandName) {
      JsonObject config = getInstance().getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("commandCosts")) {
            JsonObject costs = kits.getAsJsonObject("commandCosts");
            if (costs.has(commandName)) {
               try {
                  return costs.get(commandName).getAsDouble();
               } catch (Exception var5) {
               }
            }
         }
      }

      return 0.0;
   }

   public static boolean isPastebinCreatekitEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("pastebinCreatekit")) {
            return kits.get("pastebinCreatekit").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isSkipUsedOneTimeKitsFromKitList() {
      JsonObject config = getInstance().getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("skipUsedOneTimeKitsFromKitList")) {
            return kits.get("skipUsedOneTimeKitsFromKitList").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isKitAutoEquipEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("kitAutoEquip")) {
            return kits.get("kitAutoEquip").getAsBoolean();
         }
      }

      return false;
   }

   public static boolean isLogKitUsageEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      JsonObject kits = kitSettings(config);
         if (!kits.entrySet().isEmpty()) {
         if (kits.has("logKitUsage")) {
            return kits.get("logKitUsage").getAsBoolean();
         }
      }

      return true;
   }

   public static boolean isRequireJailLocationEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jailSettings")) {
            JsonObject jailSettings = moderation.getAsJsonObject("jailSettings");
            if (jailSettings.has("requireJailLocation")) {
               return jailSettings.get("requireJailLocation").getAsBoolean();
            }
         }
      }

      return true;
   }

   public static String getBanMessageFormat() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("banMessageFormat")) {
               String val = banSettings.get("banMessageFormat").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "You have been banned from this server.\nReason: {reason}\nBanned by: {bannedBy}\n{duration}";
   }

   public static String getTempBanMessageFormat() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("tempBanMessageFormat")) {
               String val = banSettings.get("tempBanMessageFormat").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "You have been temporarily banned from this server.\nReason: {reason}\nBanned by: {bannedBy}\nExpires: {expiry}";
   }

   public static String getIPBanMessageFormat() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("banSettings")) {
            JsonObject banSettings = moderation.getAsJsonObject("banSettings");
            if (banSettings.has("ipBanMessageFormat")) {
               String val = banSettings.get("ipBanMessageFormat").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "Your IP address has been banned from this server.\nReason: {reason}\nBanned by: {bannedBy}";
   }

   public boolean isLogWarpActionsEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("warpSettings")) {
            JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
            if (warpSettings.has("logWarpActions")) {
               return warpSettings.get("logWarpActions").getAsBoolean();
            }
         }
      }

      return true;
   }

   public boolean isPerWarpPermissionEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("warpSettings")) {
            JsonObject warpSettings = tp.getAsJsonObject("warpSettings");
            if (warpSettings.has("perWarpPermission")) {
               return warpSettings.get("perWarpPermission").getAsBoolean();
            }
         }
      }

      return false;
   }

   public boolean isDebugLoggingEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("logging")) {
         JsonObject logging = config.getAsJsonObject("logging");
         if (logging.has("enableDebugLogging")) {
            return logging.get("enableDebugLogging").getAsBoolean();
         }
      }

      return false;
   }

   public String getSeeVanishedPermission() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("generalSettings")) {
            JsonObject general = moderation.getAsJsonObject("generalSettings");
            if (general.has("seeVanishedPermission")) {
               String val = general.get("seeVanishedPermission").getAsString();
               if (val != null && !val.trim().isEmpty()) {
                  return val;
               }
            }
         }
      }

      return "neoessentials.moderation.seevanished";
   }

   public int getWebDashboardPort() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("port")) {
            try {
               return dashboard.get("port").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 8080;
   }

   public String getWebDashboardBindAddress() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("bindAddress")) {
            String addr = dashboard.get("bindAddress").getAsString();
            if (addr != null && !addr.trim().isEmpty()) {
               return addr;
            }
         }
      }

      return "0.0.0.0";
   }

   public int getWebDashboardMaxThreads() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("maxThreads")) {
            try {
               return dashboard.get("maxThreads").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 10;
   }

   public int getWebDashboardWebSocketPort() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("webSocketPort")) {
            try {
               return dashboard.get("webSocketPort").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 8081;
   }

   public String getWebDashboardHostname() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("hostname")) {
            String hostname = dashboard.get("hostname").getAsString();
            if (hostname != null && !hostname.trim().isEmpty()) {
               return hostname;
            }
         }
      }

      return "localhost";
   }

   public String getWebDashboardCustomUrl() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("webDashboard")) {
         JsonObject dashboard = config.getAsJsonObject("webDashboard");
         if (dashboard.has("customUrl")) {
            String customUrl = dashboard.get("customUrl").getAsString();
            if (customUrl != null && !customUrl.trim().isEmpty()) {
               return customUrl.trim();
            }
         }
      }

      return "";
   }

   public String getWebDashboardUrl() {
      String customUrl = this.getWebDashboardCustomUrl();
      if (!customUrl.isEmpty()) {
         return customUrl;
      } else {
         String hostname = this.getWebDashboardHostname();
         int port = this.getWebDashboardPort();
         return "http://" + hostname + ":" + port;
      }
   }

   public boolean isDashboardAuthRequired() {
      JsonObject config = this.getConfig("config.json");

      try {
         if (config.has("webDashboard")) {
            JsonObject wd = config.getAsJsonObject("webDashboard");
            JsonObject sec = null;
            if (wd.has("securitySettings")) {
               sec = wd.getAsJsonObject("securitySettings");
            } else if (wd.has("security")) {
               sec = wd.getAsJsonObject("security");
            }

            if (sec != null && sec.has("requireAuthentication")) {
               return sec.get("requireAuthentication").getAsBoolean();
            }
         }
      } catch (Exception var4) {
      }

      return true;
   }

   public boolean isDashboardRateLimitingEnabled() {
      JsonObject config = this.getConfig("config.json");

      try {
         if (config.has("webDashboard")) {
            JsonObject wd = config.getAsJsonObject("webDashboard");
            JsonObject sec = null;
            if (wd.has("securitySettings")) {
               sec = wd.getAsJsonObject("securitySettings");
            } else if (wd.has("security")) {
               sec = wd.getAsJsonObject("security");
            }

            if (sec != null && sec.has("enableRateLimiting")) {
               return sec.get("enableRateLimiting").getAsBoolean();
            }
         }
      } catch (Exception var4) {
      }

      return true;
   }

   public int getDashboardMaxRequestsPerMinute() {
      JsonObject config = this.getConfig("config.json");

      try {
         if (config.has("webDashboard")) {
            JsonObject wd = config.getAsJsonObject("webDashboard");
            JsonObject sec = null;
            if (wd.has("securitySettings")) {
               sec = wd.getAsJsonObject("securitySettings");
            } else if (wd.has("security")) {
               sec = wd.getAsJsonObject("security");
            }

            if (sec != null && sec.has("maxRequestsPerMinute")) {
               int val = sec.get("maxRequestsPerMinute").getAsInt();
               return val > 0 ? val : 200;
            }
         }
      } catch (Exception var5) {
      }

      return 200;
   }

   public boolean isCorsEnabled() {
      JsonObject config = this.getConfig("config.json");

      try {
         if (config.has("webDashboard")) {
            JsonObject wd = config.getAsJsonObject("webDashboard");
            if (wd.has("enableCORS")) {
               return wd.get("enableCORS").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   public String getCorsAllowedOrigin() {
      JsonObject config = this.getConfig("config.json");

      try {
         if (config.has("webDashboard")) {
            JsonObject wd = config.getAsJsonObject("webDashboard");
            if (wd.has("corsAllowedOrigin")) {
               String origin = wd.get("corsAllowedOrigin").getAsString();
               if (origin != null && !origin.trim().isEmpty()) {
                  return origin.trim();
               }
            }
         }
      } catch (Exception var4) {
      }

      return "";
   }

   public int getMaxCommandLength() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("maxCommandLength")) {
            try {
               return security.get("maxCommandLength").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 256;
   }

   public int getMaxReasonLength() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("maxReasonLength")) {
            try {
               return security.get("maxReasonLength").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 500;
   }

   public BigDecimal getMaxEconomyAmount() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("maxEconomyAmount")) {
            try {
               return BigDecimal.valueOf(security.get("maxEconomyAmount").getAsDouble());
            } catch (Exception var4) {
            }
         }
      }

      return BigDecimal.valueOf(9.9999999999E8);
   }

   public BigDecimal getMinEconomyAmount() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("minEconomyAmount")) {
            try {
               return BigDecimal.valueOf(security.get("minEconomyAmount").getAsDouble());
            } catch (Exception var4) {
            }
         }
      }

      return BigDecimal.valueOf(0.01);
   }

   public boolean isUnsafeCommandsAllowed() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("allowUnsafeCommands")) {
            return security.get("allowUnsafeCommands").getAsBoolean();
         }
      }

      return false;
   }

   public int getMaxUnsafeEnchantmentLevel() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("security")) {
         JsonObject security = config.getAsJsonObject("security");
         if (security.has("maxUnsafeEnchantmentLevel")) {
            try {
               return security.get("maxUnsafeEnchantmentLevel").getAsInt();
            } catch (Exception var4) {
            }
         }
      }

      return 10;
   }

   public static boolean isJailSystemEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("jailEnabled")) {
            return modules.get("jailEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static int getMaxJailsBeforePermBan() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jail")) {
            JsonObject jail = moderation.getAsJsonObject("jail");
            if (jail.has("maxJailsBeforePermBan")) {
               return jail.get("maxJailsBeforePermBan").getAsInt();
            }
         }
      }

      return 3;
   }

   public static int getTempBanDurationMinutes() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("moderation")) {
         JsonObject moderation = config.getAsJsonObject("moderation");
         if (moderation.has("jail")) {
            JsonObject jail = moderation.getAsJsonObject("jail");
            if (jail.has("tempBanDurationMinutes")) {
               return jail.get("tempBanDurationMinutes").getAsInt();
            }
         }
      }

      return 1440;
   }

   public static boolean isPermissionsEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("modules")) {
         JsonObject modules = config.getAsJsonObject("modules");
         if (modules.has("permissionsEnabled")) {
            return modules.get("permissionsEnabled").getAsBoolean();
         }
      }

      return true;
   }

   public static List<String> getProtectedAreas() {
      JsonObject config = getInstance().getConfig("config.json");
      List<String> areas = new ArrayList<>();
      if (config.has("teleportation")) {
         JsonObject teleportation = config.getAsJsonObject("teleportation");
         if (teleportation.has("protectedAreas")) {
            teleportation.getAsJsonArray("protectedAreas").forEach(element -> areas.add(element.getAsString()));
         }
      }

      return areas;
   }

   public static boolean isCancelOnMovementEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject teleportation = config.getAsJsonObject("teleportation");
         if (teleportation.has("generalSettings")) {
            JsonObject general = teleportation.getAsJsonObject("generalSettings");
            if (general.has("cancelOnMovement")) {
               return general.get("cancelOnMovement").getAsBoolean();
            }
         }
      }

      return true;
   }

   public static boolean getEnableSoundEffects() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject teleportation = config.getAsJsonObject("teleportation");
         if (teleportation.has("generalSettings")) {
            JsonObject general = teleportation.getAsJsonObject("generalSettings");
            if (general.has("enableSoundEffects")) {
               return general.get("enableSoundEffects").getAsBoolean();
            }
         }
      }

      return true;
   }

   public static boolean isDebugModeEnabled() {
      JsonObject config = getInstance().getConfig("config.json");
      if (config.has("debug")) {
         JsonObject debug = config.getAsJsonObject("debug");
         if (debug.has("enabled")) {
            return debug.get("enabled").getAsBoolean();
         }
      }

      return false;
   }

   public void saveConfig(String configName, JsonObject config) {
      this.lock.writeLock().lock();

      try {
         if (!ConfigSplitter.isSplittingEnabled() || !configName.equals("config.json")) {
            File file = ResourceUtil.getConfigFile(configName);

            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
               Gson gson = new GsonBuilder().setPrettyPrinting().create();
               gson.toJson(config, writer);
            }

            this.configCache.put(configName, config);
            return;
         }

         LOGGER.info("Split configs enabled - skipping write to config.json, updating split files only");
      } catch (IOException var14) {
         LOGGER.error("Failed to save config file {}: {}", configName, var14.getMessage());
         return;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public boolean isHomeTeleportSafetyEnabled() {
      JsonObject config = this.getConfig("config.json");
      if (config.has("teleportation")) {
         JsonObject tp = config.getAsJsonObject("teleportation");
         if (tp.has("homeSettings")) {
            JsonObject homeSettings = tp.getAsJsonObject("homeSettings");
            if (homeSettings.has("enableHomeTeleportSafety")) {
               return homeSettings.get("enableHomeTeleportSafety").getAsBoolean();
            }
         }
      }

      return true;
   }

   public static void ensureSplitConfigsOnStartup() {
      if (ConfigSplitter.isSplittingEnabled()) {
         ConfigSplitter.ensureSplitConfigsUpToDate();
      }
   }

   private static File getConfigDirectory() {
      File configDir = new File("config/neoessentials/");
      ResourceUtil.ensureDirectoryExists("config/neoessentials/");
      return configDir;
   }

   public String getMotd() {
      try {
         JsonObject config = this.getConfig("config.json");
         if (config.has("general") && config.getAsJsonObject("general").has("motd")) {
            String val = config.getAsJsonObject("general").get("motd").getAsString();
            return val.isBlank() ? null : val;
         }
      } catch (Exception var3) {
      }

      return null;
   }

   public String getRules() {
      try {
         JsonObject config = this.getConfig("config.json");
         if (config.has("general") && config.getAsJsonObject("general").has("rules")) {
            String val = config.getAsJsonObject("general").get("rules").getAsString();
            return val.isBlank() ? null : val;
         }
      } catch (Exception var3) {
      }

      return null;
   }

   public String getBackupCommand() {
      try {
         JsonObject config = this.getConfig("config.json");
         if (config.has("commands") && config.getAsJsonObject("commands").has("backupCommand")) {
            String val = config.getAsJsonObject("commands").get("backupCommand").getAsString();
            return val.isBlank() ? null : val;
         }
      } catch (Exception var3) {
      }

      return null;
   }

   private static class SingletonHolder {
      private static final ConfigManager INSTANCE = new ConfigManager();
   }
}
