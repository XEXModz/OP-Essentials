package com.zerog.neoessentials.permissions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BukkitSpongeAdapter implements ExternalPermissionAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(BukkitSpongeAdapter.class);
   private final boolean available;
   private Object pluginManager;
   private Class<?> pluginManagerClass;
   private boolean isBukkit;
   private boolean isSponge;

   public BukkitSpongeAdapter() {
      boolean found = false;

      try {
         Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
         this.pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
         this.pluginManagerClass = Class.forName("org.bukkit.plugin.PluginManager");
         this.isBukkit = true;
         found = true;
         LOGGER.info("Bukkit API detected for permissions.");
      } catch (Exception var5) {
         try {
            Class<?> spongeClass = Class.forName("org.spongepowered.api.Sponge");
            this.pluginManager = spongeClass.getMethod("server").invoke(null);
            this.pluginManagerClass = Class.forName("org.spongepowered.api.Server");
            this.isSponge = true;
            found = true;
            LOGGER.info("Sponge API detected for permissions.");
         } catch (Exception var4) {
         }
      }

      this.available = found;
   }

   @Override
   public boolean hasPermission(UUID uuid, String permission) {
      if (!this.available) {
         return false;
      } else {
         try {
            if (this.isBukkit) {
               Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
               Object player = bukkitClass.getMethod("getPlayer", UUID.class).invoke(null, uuid);
               if (player != null) {
                  return (Boolean)player.getClass().getMethod("hasPermission", String.class).invoke(player, permission);
               }
            } else if (this.isSponge) {
               Class<?> spongeClass = Class.forName("org.spongepowered.api.Sponge");
               Object server = spongeClass.getMethod("server").invoke(null);
               Object player = server.getClass().getMethod("player", UUID.class).invoke(server, uuid);
               if (player != null) {
                  Object subject = player.getClass().getMethod("subject").invoke(player);
                  return (Boolean)subject.getClass().getMethod("hasPermission", String.class).invoke(subject, permission);
               }
            }
         } catch (Exception var7) {
            LOGGER.error("Failed to check Bukkit/Sponge permission", var7);
         }

         return false;
      }
   }

   @Override
   public String getPrefix(UUID uuid) {
      return null;
   }

   @Override
   public String getSuffix(UUID uuid) {
      return null;
   }

   @Override
   public void reload() {
   }

   @Override
   public String getName() {
      return this.isBukkit ? "Bukkit" : (this.isSponge ? "Sponge" : "Unknown");
   }

   @Override
   public boolean isAvailable() {
      return this.available;
   }
}
