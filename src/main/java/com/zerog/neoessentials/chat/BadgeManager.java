package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(BadgeManager.class);
   private static volatile BadgeManager instance;
   private final Map<String, File> customBadgeFiles = new ConcurrentHashMap<>();
   private boolean customImagesLoaded = false;

   private BadgeManager() {
   }

   public static BadgeManager getInstance() {
      if (instance == null) {
         synchronized (BadgeManager.class) {
            if (instance == null) {
               instance = new BadgeManager();
            }
         }
      }

      return instance;
   }

   public String getRankBadge(ServerPlayer player) {
      if (!this.isChatBadgesEnabled()) {
         return "";
      } else {
         try {
            String group = this.getPrimaryGroup(player);
            if (group == null || group.isEmpty()) {
               return "";
            }

            String groupLower = group.toLowerCase();
            if (this.isCustomImagesEnabled() && this.hasCustomBadgeImage(groupLower)) {
               LOGGER.debug("Custom badge image found for rank: {}", groupLower);
            }

            JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
            if (chatConfig.has("badges")) {
               JsonObject badges = chatConfig.getAsJsonObject("badges");
               if (badges.has("rankBadges")) {
                  JsonObject rankBadges = badges.getAsJsonObject("rankBadges");
                  if (rankBadges.has(groupLower)) {
                     return rankBadges.get(groupLower).getAsString() + " ";
                  }
               }
            }
         } catch (Exception var7) {
            LOGGER.debug("Error getting rank badge: {}", var7.getMessage());
         }

         return "";
      }
   }

   private boolean isCustomImagesEnabled() {
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

   public String getStatusIcons(ServerPlayer player) {
      if (this.isChatBadgesEnabled() && this.isStatusIconsEnabled()) {
         StringBuilder icons = new StringBuilder();

         try {
            JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
            if (chatConfig.has("badges")) {
               JsonObject badges = chatConfig.getAsJsonObject("badges");
               if (badges.has("statusIcons")) {
                  JsonObject statusIcons = badges.getAsJsonObject("statusIcons");
                  if (this.isPlayerAfk(player) && statusIcons.has("afk")) {
                     icons.append(statusIcons.get("afk").getAsString());
                  }

                  if (this.isPlayerVanished(player) && statusIcons.has("vanished")) {
                     if (!icons.isEmpty()) {
                        icons.append(" ");
                     }

                     icons.append(statusIcons.get("vanished").getAsString());
                  }

                  if (this.isPlayerMuted(player) && statusIcons.has("muted")) {
                     if (!icons.isEmpty()) {
                        icons.append(" ");
                     }

                     icons.append(statusIcons.get("muted").getAsString());
                  }
               }
            }
         } catch (Exception var6) {
            LOGGER.debug("Error getting status icons: {}", var6.getMessage());
         }

         return !icons.isEmpty() ? icons + " " : "";
      } else {
         return "";
      }
   }

   public String applyBadgesAndIcons(ServerPlayer player, String template) {
      if (!this.isChatBadgesEnabled()) {
         return template;
      } else {
         String result = template;

         try {
            String badgePosition = this.getBadgePosition();
            String iconPosition = this.getIconPosition();
            String rankBadge = this.getRankBadge(player);
            String statusIcons = this.getStatusIcons(player);
            if (!rankBadge.isEmpty()) {
               switch (badgePosition) {
                  case "before_prefix":
                     result = rankBadge + result;
                     break;
                  case "after_prefix":
                     result = result.replace("{neoessentials_prefix}", "{neoessentials_prefix}" + rankBadge);
                     break;
                  case "before_name":
                     result = result.replace("{neoessentials_username}", rankBadge + "{neoessentials_username}");
                     result = result.replace("{neoessentials_name}", rankBadge + "{neoessentials_name}");
                     result = result.replace("{neoessentials_displayname}", rankBadge + "{neoessentials_displayname}");
                     break;
                  case "after_name":
                     result = result.replace("{neoessentials_username}", "{neoessentials_username}" + rankBadge);
                     result = result.replace("{neoessentials_name}", "{neoessentials_name}" + rankBadge);
                     result = result.replace("{neoessentials_displayname}", "{neoessentials_displayname}" + rankBadge);
               }
            }

            if (!statusIcons.isEmpty()) {
               switch (iconPosition) {
                  case "before_name":
                     result = result.replace("{neoessentials_username}", statusIcons + "{neoessentials_username}");
                     result = result.replace("{neoessentials_name}", statusIcons + "{neoessentials_name}");
                     result = result.replace("{neoessentials_displayname}", statusIcons + "{neoessentials_displayname}");
                     break;
                  case "after_name":
                     result = result.replace("{neoessentials_username}", "{neoessentials_username}" + statusIcons);
                     result = result.replace("{neoessentials_name}", "{neoessentials_name}" + statusIcons);
                     result = result.replace("{neoessentials_displayname}", "{neoessentials_displayname}" + statusIcons);
                     break;
                  case "after_message":
                     result = result + " " + statusIcons;
               }
            }
         } catch (Exception var10) {
            LOGGER.error("Error applying badges and icons: {}", var10.getMessage());
         }

         return result;
      }
   }

   public void loadCustomBadgeImages() {
      if (this.isCustomImagesEnabled() && !this.customImagesLoaded) {
         try {
            String badgePath = this.getCustomImagePath();
            File badgeDir = new File(badgePath);
            if (!badgeDir.exists()) {
               if (!badgeDir.mkdirs()) {
                  LOGGER.error("Failed to create badge directory at: {}", badgeDir.getAbsolutePath());
                  this.customImagesLoaded = true;
                  return;
               }

               LOGGER.info("Created custom badge images directory at: {}", badgeDir.getAbsolutePath());
               this.createReadmeFile(badgeDir);
               this.customImagesLoaded = true;
               return;
            }

            File[] imageFiles = badgeDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (imageFiles == null || imageFiles.length == 0) {
               LOGGER.warn("No badge images found in {}. Place PNG files named after ranks (e.g., admin.png, vip.png)", badgePath);
               this.createReadmeFile(badgeDir);
               this.customImagesLoaded = true;
               return;
            }

            LOGGER.info("Found {} custom badge images in {}", imageFiles.length, badgePath);

            for (File imageFile : imageFiles) {
               String rankName = imageFile.getName().replace(".png", "").toLowerCase();
               this.customBadgeFiles.put(rankName, imageFile);
               LOGGER.debug("Registered custom badge image for rank: {}", rankName);
            }

            LOGGER.info("Successfully registered {} custom badge images", this.customBadgeFiles.size());
            this.customImagesLoaded = true;
         } catch (Exception var9) {
            LOGGER.error("Error loading custom badge images: {}", var9.getMessage(), var9);
         }
      }
   }

   private void createReadmeFile(File badgeDir) {
      try {
         File readmeFile = new File(badgeDir, "README.txt");
         if (!readmeFile.exists()) {
            String readme = "╔══════════════════════════════════════════════════════════╗\n║           NeoEssentials Custom Badge Images              ║\n╚══════════════════════════════════════════════════════════╝\n\nPlace your custom badge PNG images in this folder!\n\nHOW TO USE:\n-----------\n1. Create or find PNG images for your ranks\n   - Recommended size: 16x16 or 32x32 pixels\n   - Use transparency (alpha channel) for best results\n   - Keep file size small (under 50KB)\n\n2. Name the files after your ranks (lowercase):\n   - admin.png\n   - moderator.png\n   - vip.png\n   - helper.png\n   - builder.png\n   - owner.png\n   - etc.\n\n3. Enable custom images in config:\n   Edit: config/neoessentials/config.json\n   Set: \"useCustomImages\": true\n\n4. Restart the server\n\nEXAMPLE STRUCTURE:\n------------------\nconfig/neoessentials/badges/\n├── admin.png       (Crown image)\n├── moderator.png   (Shield image)\n├── vip.png         (Diamond image)\n├── helper.png      (Wrench image)\n└── README.txt      (This file)\n\nTIPS:\n-----\n• Use bright colors for visibility in chat\n• Keep designs simple and recognizable\n• Add a 1-2px outline for better contrast\n• Test on both light and dark backgrounds\n• File names must match your LuckPerms group names\n\nIMPORTANT NOTES:\n----------------\n• This feature requires clients to have a compatible resource pack\n• The mod will use Unicode emoji badges as fallback\n• Custom images work best with 16x16 or 32x32 pixel sizes\n• PNG format with transparency is recommended\n\nFor more information and resource pack setup:\nSee: docs/CUSTOM_BADGES.md\n\n══════════════════════════════════════════════════════════\n";
            Files.writeString(readmeFile.toPath(), readme);
            LOGGER.info("Created README.txt in badges directory");
         }
      } catch (Exception var4) {
         LOGGER.warn("Failed to create README file: {}", var4.getMessage());
      }
   }

   private String getCustomImagePath() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("customImagePath")) {
               return badges.get("customImagePath").getAsString();
            }
         }
      } catch (Exception var3) {
      }

      return "config/neoessentials/badges";
   }

   public boolean hasCustomBadgeImage(String rankName) {
      return this.customBadgeFiles.containsKey(rankName.toLowerCase());
   }

   private boolean isChatBadgesEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("enabled")) {
               return badges.get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   private boolean isStatusIconsEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("statusIcons")) {
               return badges.getAsJsonObject("statusIcons").get("enabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return true;
   }

   private String getBadgePosition() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            return chatConfig.getAsJsonObject("badges").get("badgePosition").getAsString();
         }
      } catch (Exception var2) {
      }

      return "before_prefix";
   }

   private String getIconPosition() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("statusIcons")) {
               return badges.getAsJsonObject("statusIcons").get("iconPosition").getAsString();
            }
         }
      } catch (Exception var3) {
      }

      return "after_name";
   }

   private String getPrimaryGroup(ServerPlayer player) {
      try {
         PermissionManager permManager = PermissionAPI.getManager();
         if (permManager != null) {
            PermissionUser user = permManager.getUser(player.getUUID());
            if (user != null) {
               return user.getGroup();
            }
         }
      } catch (Exception var4) {
         LOGGER.debug("Error getting primary group: {}", var4.getMessage());
      }

      return "default";
   }

   private boolean isPlayerAfk(ServerPlayer player) {
      try {
         AfkManager afkManager = AfkManager.getInstance();
         return afkManager.isAfk(player);
      } catch (Exception var3) {
         return false;
      }
   }

   private boolean isPlayerVanished(ServerPlayer player) {
      try {
         VanishManager vanishManager = VanishManager.getInstance();
         return vanishManager.isPlayerVanished(player.getUUID());
      } catch (Exception var3) {
         return false;
      }
   }

   private boolean isPlayerMuted(ServerPlayer player) {
      try {
         return MuteManager.isMuted(player);
      } catch (Exception var3) {
         return false;
      }
   }
}
