package com.zerog.neoessentials.items;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemSpawnHelper {
   private static final Logger LOGGER = LoggerFactory.getLogger(ItemSpawnHelper.class);

   public static ItemSpawnHelper.SpawnResult canSpawnItem(ServerPlayer player, Item item) {
      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
      String itemIdString = itemId.toString();
      List<String> blacklist = ConfigManager.getItemSpawnBlacklist();
      if (blacklist.contains(itemIdString)) {
         return ItemSpawnHelper.SpawnResult.failure("Item '" + itemIdString + "' is blacklisted and cannot be spawned");
      } else {
         if (ConfigManager.isPermissionBasedItemSpawn()) {
            UUID playerUuid = player.getUUID();
            if (!PermissionAPI.hasPermission(playerUuid, "neoessentials.item.spawn")) {
               return ItemSpawnHelper.SpawnResult.failure("You don't have permission to spawn items");
            }

            String specificPerm = "neoessentials.item.spawn." + itemIdString.replace(":", ".");
            if (!PermissionAPI.hasPermission(playerUuid, specificPerm)) {
               String namespacePerm = "neoessentials.item.spawn." + itemId.getNamespace() + ".*";
               if (!PermissionAPI.hasPermission(playerUuid, namespacePerm)) {
                  return ItemSpawnHelper.SpawnResult.failure("You don't have permission to spawn '" + itemIdString + "'");
               }
            }
         }

         return ItemSpawnHelper.SpawnResult.success();
      }
   }

   public static ItemSpawnHelper.SpawnResult canSpawnItem(ServerPlayer player, ItemStack stack) {
      return stack.isEmpty() ? ItemSpawnHelper.SpawnResult.failure("Cannot spawn empty item stack") : canSpawnItem(player, stack.getItem());
   }

   public static boolean isBlacklisted(Item item) {
      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
      String itemIdString = itemId.toString();
      List<String> blacklist = ConfigManager.getItemSpawnBlacklist();
      return blacklist.contains(itemIdString);
   }

   public static boolean isBlacklisted(String itemId) {
      List<String> blacklist = ConfigManager.getItemSpawnBlacklist();
      return blacklist.contains(itemId);
   }

   public static class SpawnResult {
      private final boolean success;
      private final String errorMessage;

      private SpawnResult(boolean success, String errorMessage) {
         this.success = success;
         this.errorMessage = errorMessage;
      }

      public static ItemSpawnHelper.SpawnResult success() {
         return new ItemSpawnHelper.SpawnResult(true, null);
      }

      public static ItemSpawnHelper.SpawnResult failure(String message) {
         return new ItemSpawnHelper.SpawnResult(false, message);
      }

      public boolean isSuccess() {
         return this.success;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }
   }
}
