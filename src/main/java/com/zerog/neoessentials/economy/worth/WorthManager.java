package com.zerog.neoessentials.economy.worth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorthManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(WorthManager.class);
   private static final WorthManager INSTANCE = new WorthManager();
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final String WORTH_FILE = "worth.json";
   private final Map<String, BigDecimal> worthMap = new ConcurrentHashMap<>();
   private volatile boolean loaded = false;

   private WorthManager() {
   }

   public static WorthManager getInstance() {
      return INSTANCE;
   }

   public synchronized void initialize() {
      if (!this.loaded) {
         this.load();
         this.loaded = true;
      }
   }

   private void load() {
      try {
         File f = ResourceUtil.getConfigFile("worth.json");
         if (!f.exists()) {
            LOGGER.info("No worth.json found — starting with empty price list");
            this.save();
         } else {
            try (Reader r = new FileReader(f)) {
               JsonObject root = (JsonObject)GSON.fromJson(r, JsonObject.class);
               if (root != null) {
                  JsonObject worth = root.has("worth") ? root.getAsJsonObject("worth") : root;
                  this.worthMap.clear();

                  for (Entry<String, JsonElement> e : worth.entrySet()) {
                     try {
                        this.worthMap.put(normalizeId(e.getKey()), new BigDecimal(e.getValue().getAsString()));
                     } catch (Exception var9) {
                        LOGGER.warn("Invalid worth entry '{}': {}", e.getKey(), var9.getMessage());
                     }
                  }

                  LOGGER.info("Loaded {} item prices from worth.json", this.worthMap.size());
                  return;
               }
            }
         }
      } catch (Exception var11) {
         LOGGER.error("Failed to load worth.json: {}", var11.getMessage(), var11);
      }
   }

   private void save() {
      try {
         File f = ResourceUtil.getConfigFile("worth.json");
         File parent = f.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         JsonObject root = new JsonObject();
         root.addProperty("_comment", "Item sell prices. Keys are item registry IDs (e.g. minecraft:diamond).");
         JsonObject worth = new JsonObject();
         new TreeMap<>(this.worthMap).forEach((k, v) -> worth.addProperty(k, v.toPlainString()));
         root.add("worth", worth);

         try (Writer w = new FileWriter(f)) {
            GSON.toJson(root, w);
         }
      } catch (Exception var10) {
         LOGGER.error("Failed to save worth.json: {}", var10.getMessage(), var10);
      }
   }

   public void reload() {
      this.worthMap.clear();
      this.loaded = false;
      this.initialize();
   }

   public BigDecimal getPrice(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String id = getItemId(stack);
         BigDecimal price = this.worthMap.get(id);
         if (price != null) {
            return price;
         } else {
            String shortName = id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
            return this.worthMap.get(shortName);
         }
      } else {
         return null;
      }
   }

   public BigDecimal getPrice(String itemId) {
      return this.worthMap.get(normalizeId(itemId));
   }

   public void setPrice(ItemStack stack, double price) {
      String id = getItemId(stack);
      if (price <= 0.0) {
         this.worthMap.remove(id);
      } else {
         this.worthMap.put(id, BigDecimal.valueOf(price));
      }

      this.save();
   }

   public void setPrice(String itemId, double price) {
      String id = normalizeId(itemId);
      if (price <= 0.0) {
         this.worthMap.remove(id);
      } else {
         this.worthMap.put(id, BigDecimal.valueOf(price));
      }

      this.save();
   }

   public boolean removePrice(ItemStack stack) {
      String id = getItemId(stack);
      boolean removed = this.worthMap.remove(id) != null;
      if (removed) {
         this.save();
      }

      return removed;
   }

   public Map<String, BigDecimal> getAllPrices() {
      return new TreeMap<>(this.worthMap);
   }

   public BigDecimal getSellMultiplier() {
      try {
         JsonObject cfg = ConfigManager.getInstance().getConfig("config.json");
         if (cfg.has("economy")) {
            JsonObject eco = cfg.getAsJsonObject("economy");
            if (eco.has("sellMultiplier")) {
               return BigDecimal.valueOf(eco.get("sellMultiplier").getAsDouble());
            }
         }
      } catch (Exception var3) {
      }

      return BigDecimal.ONE;
   }

   public boolean isAllowSellNamedItems() {
      try {
         JsonObject cfg = ConfigManager.getInstance().getConfig("config.json");
         if (cfg.has("economy")) {
            JsonObject eco = cfg.getAsJsonObject("economy");
            if (eco.has("allowSellNamedItems")) {
               return eco.get("allowSellNamedItems").getAsBoolean();
            }
         }
      } catch (Exception var3) {
      }

      return false;
   }

   public static String getItemId(ItemStack stack) {
      ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return key.toString();
   }

   public static String getItemDisplayName(ItemStack stack) {
      return stack.getDisplayName().getString();
   }

   public static ItemStack resolveItem(String name) {
      if (name != null && !name.isBlank()) {
         String trimmed = name.trim().toLowerCase();
         if (trimmed.contains(":")) {
            ResourceLocation loc = ResourceLocation.tryParse(trimmed);
            if (loc != null) {
               Item item = (Item)BuiltInRegistries.ITEM.get(loc);
               if (item != Items.AIR) {
                  return new ItemStack(item);
               }
            }

            return null;
         } else {
            ResourceLocation vanillaLoc = ResourceLocation.tryParse("minecraft:" + trimmed);
            if (vanillaLoc != null) {
               Item item = (Item)BuiltInRegistries.ITEM.get(vanillaLoc);
               if (item != Items.AIR) {
                  return new ItemStack(item);
               }
            }

            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
               if (key.getPath().equals(trimmed)) {
                  Item item = (Item)BuiltInRegistries.ITEM.get(key);
                  if (item != Items.AIR) {
                     return new ItemStack(item);
                  }
               }
            }

            for (ResourceLocation keyx : BuiltInRegistries.ITEM.keySet()) {
               if (keyx.getPath().contains(trimmed)) {
                  Item item = (Item)BuiltInRegistries.ITEM.get(keyx);
                  if (item != Items.AIR) {
                     return new ItemStack(item);
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static String normalizeId(String id) {
      if (id == null) {
         return "";
      } else {
         id = id.trim().toLowerCase();
         return id.contains(":") ? id : "minecraft:" + id;
      }
   }
}
