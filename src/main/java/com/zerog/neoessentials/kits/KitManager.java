package com.zerog.neoessentials.kits;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.api.permissions.PermissionRegistry;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.items.ItemSpawnHelper;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(KitManager.class);
   private static final KitManager INSTANCE = new KitManager();
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final Map<String, Kit> kits = new ConcurrentHashMap<>();
   private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
   private final Map<UUID, Map<String, Integer>> playerUsages = new ConcurrentHashMap<>();
   private final File playerDataFile = ResourceUtil.getDataFile("kit_player_data.json");
   private volatile boolean initialized = false;

   private KitManager() {
   }

   public static KitManager getInstance() {
      return INSTANCE;
   }

   public synchronized void initialize() {
      if (!this.initialized) {
         try {
            LOGGER.info("Initializing Kit Manager...");
            this.loadKits();
            this.loadPlayerData();
            this.initialized = true;
            LOGGER.info("Kit Manager initialized with {} kits", this.kits.size());
         } catch (Exception var2) {
            LOGGER.error("Failed to initialize Kit Manager: {}", var2.getMessage(), var2);
         }
      }
   }

   private void loadKits() {
      try {
         File kitsFile = ResourceUtil.getConfigFile("kits.json");
         if (kitsFile.exists()) {
            try (Reader reader = new FileReader(kitsFile)) {
               JsonObject config = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               if (config != null && config.has("kits")) {
                  JsonElement kitsElement = config.get("kits");
                  if (kitsElement != null && kitsElement.isJsonArray()) {
                     JsonArray kitsArray = kitsElement.getAsJsonArray();
                     int loadedCount = 0;

                     for (JsonElement element : kitsArray) {
                        if (element.isJsonObject()) {
                           try {
                              Kit kit = Kit.fromJson(element.getAsJsonObject());
                              this.kits.put(kit.getName(), kit);

                              try {
                                 PermissionRegistry.getInstance().registerKitPermission(kit.getName());
                              } catch (Exception var12) {
                                 LOGGER.warn("Failed to register kit permission for '{}': {}", kit.getName(), var12.getMessage());
                              }

                              loadedCount++;
                           } catch (Exception var13) {
                              LOGGER.warn("Failed to load kit from config: {}", var13.getMessage());
                           }
                        }
                     }

                     LOGGER.info("Loaded {} kits from configuration", loadedCount);
                  }
               }
            }
         } else {
            LOGGER.info("No kits configuration found, starting with empty kit list");
            this.saveKits();
         }
      } catch (Exception var15) {
         LOGGER.error("Failed to load kits from configuration: {}", var15.getMessage(), var15);
      }
   }

   private void saveKits() {
      try {
         File kitsFile = ResourceUtil.getConfigFile("kits.json");
         File parentDir = kitsFile.getParentFile();
         if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
         }

         JsonObject config = new JsonObject();

         try {
            if (kitsFile.exists()) {
               try (Reader r = new FileReader(kitsFile)) {
                  JsonObject old = (JsonObject)GSON.fromJson(r, JsonObject.class);
                  if (old != null && old.has("settings") && old.get("settings").isJsonObject()) {
                     config.add("settings", old.getAsJsonObject("settings"));
                  }
               }
            }
         } catch (Exception var20) {
         }

         config.addProperty("_configVersion", 1);
         config.addProperty("_configVersion_comment", "DO NOT MODIFY: This field is used by NeoEssentials for automatic config updates.");
         JsonArray kitsArray = new JsonArray();

         for (Kit kit : this.kits.values()) {
            kitsArray.add(kit.toJson());
         }

         config.add("kits", kitsArray);

         try (Writer writer = new FileWriter(kitsFile)) {
            GSON.toJson(config, writer);
         }

         LOGGER.debug("Saved {} kits to configuration", this.kits.size());
      } catch (Exception var10) {
         LOGGER.error("Failed to save kits to configuration: {}", var10.getMessage(), var10);
      }
   }

   private void loadPlayerData() {
      try {
         if (!this.playerDataFile.exists()) {
            LOGGER.debug("No kit player data file found, starting fresh");
            return;
         }

         try (Reader reader = new FileReader(this.playerDataFile)) {
            JsonObject data = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            if (data != null) {
               if (data.has("cooldowns")) {
                  JsonObject cooldownsJson = data.getAsJsonObject("cooldowns");

                  for (Entry<String, JsonElement> playerEntry : cooldownsJson.entrySet()) {
                     try {
                        UUID playerId = UUID.fromString(playerEntry.getKey());
                        JsonObject playerCooldowns = playerEntry.getValue().getAsJsonObject();
                        Map<String, Long> cooldowns = new HashMap<>();

                        for (Entry<String, JsonElement> kitEntry : playerCooldowns.entrySet()) {
                           cooldowns.put(kitEntry.getKey(), kitEntry.getValue().getAsLong());
                        }

                        this.playerCooldowns.put(playerId, cooldowns);
                     } catch (Exception var13) {
                        LOGGER.warn("Failed to load cooldown data for player: {}", var13.getMessage());
                     }
                  }
               }

               if (data.has("usages")) {
                  JsonObject usagesJson = data.getAsJsonObject("usages");

                  for (Entry<String, JsonElement> playerEntry : usagesJson.entrySet()) {
                     try {
                        UUID playerId = UUID.fromString(playerEntry.getKey());
                        JsonObject playerUsages = playerEntry.getValue().getAsJsonObject();
                        Map<String, Integer> usages = new HashMap<>();

                        for (Entry<String, JsonElement> kitEntry : playerUsages.entrySet()) {
                           usages.put(kitEntry.getKey(), kitEntry.getValue().getAsInt());
                        }

                        this.playerUsages.put(playerId, usages);
                     } catch (Exception var12) {
                        LOGGER.warn("Failed to load usage data for player: {}", var12.getMessage());
                     }
                  }
               }

               LOGGER.debug("Loaded player data for {} players", Math.max(this.playerCooldowns.size(), this.playerUsages.size()));
            }
         }
      } catch (Exception var15) {
         LOGGER.error("Failed to load player kit data: {}", var15.getMessage(), var15);
      }
   }

   private void savePlayerData() {
      try {
         File parentDir = this.playerDataFile.getParentFile();
         if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
         }

         JsonObject data = new JsonObject();
         JsonObject cooldownsJson = new JsonObject();

         for (Entry<UUID, Map<String, Long>> playerEntry : this.playerCooldowns.entrySet()) {
            JsonObject playerCooldowns = new JsonObject();

            for (Entry<String, Long> kitEntry : playerEntry.getValue().entrySet()) {
               playerCooldowns.addProperty(kitEntry.getKey(), kitEntry.getValue());
            }

            cooldownsJson.add(playerEntry.getKey().toString(), playerCooldowns);
         }

         data.add("cooldowns", cooldownsJson);
         JsonObject usagesJson = new JsonObject();

         for (Entry<UUID, Map<String, Integer>> playerEntry : this.playerUsages.entrySet()) {
            JsonObject playerUsages = new JsonObject();

            for (Entry<String, Integer> kitEntry : playerEntry.getValue().entrySet()) {
               playerUsages.addProperty(kitEntry.getKey(), kitEntry.getValue());
            }

            usagesJson.add(playerEntry.getKey().toString(), playerUsages);
         }

         data.add("usages", usagesJson);

         try (Writer writer = new FileWriter(this.playerDataFile)) {
            GSON.toJson(data, writer);
         }

         LOGGER.debug("Saved player kit data");
      } catch (Exception var12) {
         LOGGER.error("Failed to save player kit data: {}", var12.getMessage(), var12);
      }
   }

   public boolean createKit(String name, String displayName, String description, List<ItemStack> items, long cooldownMillis, String permission) {
      try {
         Kit kit = new Kit(name, displayName, description, items, cooldownMillis, permission, -1, true);
         this.kits.put(kit.getName(), kit);
         this.saveKits();

         try {
            PermissionRegistry.getInstance().registerKitPermission(kit.getName());
         } catch (Exception var10) {
            LOGGER.warn("Failed to register kit permission for '{}': {}", kit.getName(), var10.getMessage());
         }

         LOGGER.info("Created/updated kit: {}", kit.getName());
         return true;
      } catch (Exception var11) {
         LOGGER.error("Failed to create kit '{}': {}", new Object[]{name, var11.getMessage(), var11});
         return false;
      }
   }

   public boolean deleteKit(String name) {
      String normalizedName = name.toLowerCase();
      if (this.kits.remove(normalizedName) != null) {
         this.saveKits();

         try {
            PermissionRegistry.getInstance().unregisterKitPermission(normalizedName);
         } catch (Exception var4) {
            LOGGER.warn("Failed to unregister kit permission for '{}': {}", normalizedName, var4.getMessage());
         }

         LOGGER.info("Deleted kit: {}", normalizedName);
         return true;
      } else {
         return false;
      }
   }

   public Kit getKit(String name) {
      if (!this.initialized) {
         LOGGER.warn("KitManager accessed before initialization - performing lazy init");
         this.initialize();
      }

      return this.kits.get(name.toLowerCase());
   }

   public Set<String> getKitNames() {
      return new HashSet<>(this.kits.keySet());
   }

   public Set<String> getAllKitNames() {
      return this.getKitNames();
   }

   public Collection<Kit> getAllKits() {
      return new ArrayList<>(this.kits.values());
   }

   public List<Kit> getAvailableKits(ServerPlayer player) {
      if (!this.initialized) {
         LOGGER.warn("KitManager accessed before initialization - performing lazy init");
         this.initialize();
      }

      return this.kits
         .values()
         .stream()
         .filter(kit -> kit.isEnabled())
         .filter(kit -> kit.getPermission() == null || PermissionAPI.hasPermission(player.getUUID(), kit.getPermission()))
         .collect(Collectors.toList());
   }

   public KitManager.KitUsageResult canUseKit(ServerPlayer player, String kitName) {
      if (ConfigManager.getInstance().isAllowKitOverrideEnabled() && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.override")) {
         return new KitManager.KitUsageResult(true, "Kit can be used (override)");
      } else {
         Kit kit = this.getKit(kitName);
         if (kit == null) {
            return new KitManager.KitUsageResult(false, "Kit not found");
         } else if (!kit.isEnabled()) {
            return new KitManager.KitUsageResult(false, "Kit is currently disabled");
         } else if (kit.getPermission() != null && !PermissionAPI.hasPermission(player.getUUID(), kit.getPermission())) {
            return new KitManager.KitUsageResult(false, "You don't have permission to use this kit");
         } else {
            if (!this.hasCooldownExemption(player, kitName)) {
               long remainingCooldown = this.getRemainingCooldown(player.getUUID(), kitName);
               if (remainingCooldown > 0L) {
                  return new KitManager.KitUsageResult(false, "Kit is still on cooldown for " + this.formatTime(remainingCooldown));
               }
            }

            if (kit.getMaxUses() > 0) {
               int usageCount = this.getUsageCount(player.getUUID(), kitName);
               if (usageCount >= kit.getMaxUses()) {
                  return new KitManager.KitUsageResult(false, "You have reached the maximum uses for this kit");
               }
            }

            int maxKits = ConfigManager.getInstance().getMaxKitsPerPlayer();
            if (maxKits > 0 && !this.hasCooldownExemption(player, kitName)) {
               int activeCooldowns = 0;
               Map<String, Long> cooldownMap = this.playerCooldowns.get(player.getUUID());
               if (cooldownMap != null) {
                  long now = System.currentTimeMillis();

                  for (Long cooldownEnd : cooldownMap.values()) {
                     if (cooldownEnd != null && cooldownEnd > now) {
                        activeCooldowns++;
                     }
                  }
               }

               boolean alreadyOnCooldown = false;
               if (cooldownMap != null && cooldownMap.containsKey(kitName.toLowerCase())) {
                  long cooldownEndx = cooldownMap.get(kitName.toLowerCase());
                  if (cooldownEndx > System.currentTimeMillis()) {
                     alreadyOnCooldown = true;
                  }
               }

               if (!alreadyOnCooldown && activeCooldowns >= maxKits) {
                  return new KitManager.KitUsageResult(false, "You have reached the maximum number of kits on cooldown (" + maxKits + ")");
               }
            }

            return new KitManager.KitUsageResult(true, "Kit can be used");
         }
      }
   }

   public KitManager.KitUsageResult giveKit(ServerPlayer player, String kitName) {
      KitManager.KitUsageResult canUse = this.canUseKit(player, kitName);
      if (!canUse.isAllowed()) {
         return canUse;
      } else {
         if (!ConfigManager.getInstance().isAllowKitOverrideEnabled() || !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.override")) {
            int maxKits = ConfigManager.getInstance().getMaxKitsPerPlayer();
            if (maxKits > 0 && !this.hasCooldownExemption(player, kitName)) {
               Map<String, Long> cooldownMap = this.playerCooldowns.get(player.getUUID());
               int activeCooldowns = 0;
               long now = System.currentTimeMillis();
               if (cooldownMap != null) {
                  for (Long cooldownEnd : cooldownMap.values()) {
                     if (cooldownEnd != null && cooldownEnd > now) {
                        activeCooldowns++;
                     }
                  }
               }

               boolean alreadyOnCooldown = false;
               if (cooldownMap != null && cooldownMap.containsKey(kitName.toLowerCase())) {
                  long cooldownEndx = cooldownMap.get(kitName.toLowerCase());
                  if (cooldownEndx > now) {
                     alreadyOnCooldown = true;
                  }
               }

               if (!alreadyOnCooldown && activeCooldowns >= maxKits) {
                  return new KitManager.KitUsageResult(false, "You have reached the maximum number of kits on cooldown (" + maxKits + ")");
               }
            }
         }

         Kit kit = this.getKit(kitName);
         if (kit == null) {
            return new KitManager.KitUsageResult(false, "Kit not found");
         } else {
            try {
               Inventory inventory = player.getInventory();
               List<ItemStack> itemsGiven = new ArrayList<>();
               List<ItemStack> itemsDropped = new ArrayList<>();
               List<String> deniedItems = new ArrayList<>();
               boolean autoEquip = ConfigManager.isKitAutoEquipEnabled();
               boolean armorSlotsEmpty = inventory.armor.stream().allMatch(ItemStack::isEmpty);
               List<ItemStack> armorItems = new ArrayList<>();
               List<ItemStack> otherItems = new ArrayList<>();

               for (ItemStack item : kit.getItems()) {
                  if (!item.isEmpty()) {
                     if (!item.getItem().getDescriptionId().contains("helmet")
                        && !item.getItem().getDescriptionId().contains("chestplate")
                        && !item.getItem().getDescriptionId().contains("leggings")
                        && !item.getItem().getDescriptionId().contains("boots")) {
                        otherItems.add(item);
                     } else {
                        armorItems.add(item);
                     }
                  }
               }

               if (autoEquip && armorSlotsEmpty && !armorItems.isEmpty()) {
                  for (ItemStack armor : armorItems) {
                     boolean equipped = false;

                     for (int i = 0; i < inventory.armor.size(); i++) {
                        if (((ItemStack)inventory.armor.get(i)).isEmpty()) {
                           inventory.armor.set(i, armor.copy());
                           itemsGiven.add(armor.copy());
                           equipped = true;
                           break;
                        }
                     }

                     if (!equipped) {
                        if (inventory.add(armor.copy())) {
                           itemsGiven.add(armor.copy());
                        } else {
                           player.drop(armor.copy(), false);
                           itemsDropped.add(armor.copy());
                        }
                     }
                  }
               } else {
                  for (ItemStack armor : armorItems) {
                     if (inventory.add(armor.copy())) {
                        itemsGiven.add(armor.copy());
                     } else {
                        player.drop(armor.copy(), false);
                        itemsDropped.add(armor.copy());
                     }
                  }
               }

               for (ItemStack itemx : otherItems) {
                  ItemSpawnHelper.SpawnResult spawnResult = ItemSpawnHelper.canSpawnItem(player, itemx);
                  if (!spawnResult.isSuccess()) {
                     deniedItems.add(itemx.getDisplayName().getString() + ": " + spawnResult.getErrorMessage());
                     LOGGER.warn(
                        "Denied item '{}' from kit '{}' for player '{}': {}",
                        new Object[]{itemx.getDisplayName().getString(), kitName, player.getName().getString(), spawnResult.getErrorMessage()}
                     );
                  } else if (inventory.add(itemx.copy())) {
                     itemsGiven.add(itemx.copy());
                  } else {
                     player.drop(itemx.copy(), false);
                     itemsDropped.add(itemx.copy());
                  }
               }

               if (!this.hasCooldownExemption(player, kitName)) {
                  this.setCooldown(player.getUUID(), kitName, System.currentTimeMillis() + kit.getCooldownMillis());
               }

               this.incrementUsage(player.getUUID(), kitName);
               this.savePlayerData();
               String result = String.format("Given kit '%s' (%d items)", kit.getDisplayName(), itemsGiven.size());
               if (!itemsDropped.isEmpty()) {
                  result = result + String.format(" (%d items dropped)", itemsDropped.size());
               }

               if (!deniedItems.isEmpty()) {
                  result = result + String.format(" (%d items denied: %s)", deniedItems.size(), String.join(", ", deniedItems));
               }

               if (ConfigManager.isLogKitUsageEnabled()) {
                  LOGGER.info("Player {} used kit {}", player.getName().getString(), kitName);
               }

               return new KitManager.KitUsageResult(true, result);
            } catch (Exception var17) {
               LOGGER.error("Failed to give kit '{}' to player {}: {}", new Object[]{kitName, player.getName().getString(), var17.getMessage(), var17});
               return new KitManager.KitUsageResult(false, "An error occurred while giving the kit");
            }
         }
      }
   }

   private long getRemainingCooldown(UUID playerId, String kitName) {
      Map<String, Long> playerCooldownMap = this.playerCooldowns.get(playerId);
      if (playerCooldownMap == null) {
         return 0L;
      } else {
         Long cooldownEnd = playerCooldownMap.get(kitName.toLowerCase());
         if (cooldownEnd == null) {
            return 0L;
         } else {
            long remaining = cooldownEnd - System.currentTimeMillis();
            return Math.max(0L, remaining);
         }
      }
   }

   public long getRemainingCooldownPublic(UUID playerId, String kitName) {
      return this.getRemainingCooldown(playerId, kitName);
   }

   public void resetCooldown(UUID playerId, String kitName) {
      Map<String, Long> map = this.playerCooldowns.get(playerId);
      if (map != null) {
         map.remove(kitName.toLowerCase());
      }

      this.savePlayerData();
   }

   public void resetAllCooldowns(UUID playerId) {
      this.playerCooldowns.remove(playerId);
      this.savePlayerData();
   }

   private void setCooldown(UUID playerId, String kitName, long cooldownEnd) {
      this.playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(kitName.toLowerCase(), cooldownEnd);
   }

   public int getUsageCount(UUID playerId, String kitName) {
      Map<String, Integer> playerUsageMap = this.playerUsages.get(playerId);
      return playerUsageMap == null ? 0 : playerUsageMap.getOrDefault(kitName.toLowerCase(), 0);
   }

   private void incrementUsage(UUID playerId, String kitName) {
      this.playerUsages.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).merge(kitName.toLowerCase(), 1, Integer::sum);
   }

   private boolean hasCooldownExemption(ServerPlayer player, String kitName) {
      UUID playerId = player.getUUID();
      if (ConfigManager.getInstance().isAllowKitOverrideEnabled() && PermissionAPI.hasPermission(playerId, "neoessentials.kits.override")) {
         return true;
      } else if (PermissionAPI.hasPermission(playerId, "neoessentials.kits.nocooldown")) {
         return true;
      } else {
         String kitNocooldownPermission = "neoessentials.kits." + kitName.toLowerCase() + ".nocooldown";
         return PermissionAPI.hasPermission(playerId, kitNocooldownPermission);
      }
   }

   private String formatTime(long millis) {
      long seconds = millis / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      if (hours > 0L) {
         return String.format("%dh %dm", hours, minutes % 60L);
      } else {
         return minutes > 0L ? String.format("%dm %ds", minutes, seconds % 60L) : String.format("%ds", seconds);
      }
   }

   public void reload() {
      this.kits.clear();
      this.playerCooldowns.clear();
      this.playerUsages.clear();
      this.initialized = false;
      this.initialize();
   }

   public static class KitUsageResult {
      private final boolean allowed;
      private final String message;

      public KitUsageResult(boolean allowed, String message) {
         this.allowed = allowed;
         this.message = message;
      }

      public boolean isAllowed() {
         return this.allowed;
      }

      public String getMessage() {
         return this.message;
      }
   }
}
