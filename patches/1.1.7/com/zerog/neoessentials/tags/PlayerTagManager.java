package com.zerog.neoessentials.tags;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerTagManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTagManager.class);
   private static volatile PlayerTagManager instance;
   private final Map<String, File> customTagFiles = new ConcurrentHashMap<>();
   private boolean customImagesLoaded = false;

   private PlayerTagManager() {
   }

   public static PlayerTagManager getInstance() {
      if (instance == null) {
         synchronized (PlayerTagManager.class) {
            if (instance == null) {
               instance = new PlayerTagManager();
            }
         }
      }

      return instance;
   }

   public String getPlayerTag(ServerPlayer player) {
      return !this.isAboveHeadTagsEnabled() ? "" : this.getPrimaryGroup(player);
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

   public void loadCustomTagImages(File assetsDir) {
      this.customTagFiles.clear();
      if (assetsDir != null && assetsDir.exists() && assetsDir.isDirectory()) {
         File[] files = assetsDir.listFiles((dir, name) -> name.matches("[a-zA-Z0-9_-]+\\.(png|jpg|jpeg|gif)"));
         if (files != null) {
            for (File file : files) {
               String tagName = file.getName().replaceFirst("\\.[^.]+$", "");
               this.customTagFiles.put(tagName, file);
               LOGGER.debug("Loaded custom tag image: {} -> {}", tagName, file.getAbsolutePath());
            }
         }

         this.customImagesLoaded = true;
         LOGGER.info("PlayerTagManager: Loaded {} custom tag images from {}", this.customTagFiles.size(), assetsDir.getAbsolutePath());
      } else {
         LOGGER.warn("PlayerTagManager: Provided assetsDir is invalid: {}", assetsDir);
         this.customImagesLoaded = false;
      }
   }

   public void reloadCustomTagImages(File assetsDir) {
      this.loadCustomTagImages(assetsDir);
   }

   public Set<String> getAvailableTagNames() {
      return this.customTagFiles.keySet();
   }

   public File getCustomTagFile(String tagName) {
      return this.customTagFiles.get(tagName);
   }

   public boolean isAboveHeadTagsEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("aboveHeadTagsEnabled")) {
               return badges.get("aboveHeadTagsEnabled").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }
}
