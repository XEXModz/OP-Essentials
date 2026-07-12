package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerAssetCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(ServerAssetCollector.class);
   private final MinecraftServer server;
   private JsonObject cachedAssets = null;
   private long lastCacheTime = 0L;
   private static final long CACHE_DURATION = 300000L;

   public ServerAssetCollector(MinecraftServer server) {
      this.server = server;
   }

   public JsonObject getAllAssets() {
      long currentTime = System.currentTimeMillis();
      if (this.cachedAssets != null && currentTime - this.lastCacheTime < 300000L) {
         return this.cachedAssets;
      } else {
         LOGGER.info("Collecting server assets...");
         JsonObject assets = new JsonObject();
         JsonArray items = new JsonArray();
         JsonObject itemsByNamespace = new JsonObject();
         Map<String, Integer> namespaceCount = new HashMap<>();

         for (Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation itemId = entry.getKey().location();
            JsonObject itemData = new JsonObject();
            itemData.addProperty("id", itemId.toString());
            itemData.addProperty("namespace", itemId.getNamespace());
            itemData.addProperty("path", itemId.getPath());
            itemData.addProperty("modded", !itemId.getNamespace().equals("minecraft"));
            if (!itemId.getNamespace().equals("minecraft")) {
               try {
                  Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(itemId.getNamespace());
                  if (modContainer.isPresent()) {
                     itemData.addProperty("modName", modContainer.get().getModInfo().getDisplayName());
                  }
               } catch (Exception var12) {
               }
            }

            items.add(itemData);
            String namespace = itemId.getNamespace();
            namespaceCount.put(namespace, namespaceCount.getOrDefault(namespace, 0) + 1);
            if (!itemsByNamespace.has(namespace)) {
               itemsByNamespace.add(namespace, new JsonArray());
            }

            itemsByNamespace.getAsJsonArray(namespace).add(itemData);
         }

         assets.add("items", items);
         assets.add("itemsByNamespace", itemsByNamespace);
         assets.addProperty("totalItems", items.size());
         JsonObject stats = new JsonObject();

         for (Entry<String, Integer> entry : namespaceCount.entrySet()) {
            stats.addProperty(entry.getKey(), entry.getValue());
         }

         assets.add("namespaceStats", stats);
         assets.addProperty("moddedNamespaces", namespaceCount.size() - 1);
         JsonObject textureApis = new JsonObject();
         textureApis.addProperty("vanilla", "https://mc-heads.net/minecraft/item/{item_path}");
         textureApis.addProperty("fallback", "placeholder");
         textureApis.addProperty("note", "For modded items, configure custom texture server or use placeholders");
         assets.add("textureApis", textureApis);
         this.cachedAssets = assets;
         this.lastCacheTime = currentTime;
         LOGGER.info("Collected {} items from {} namespaces ({} modded)", new Object[]{items.size(), namespaceCount.size(), namespaceCount.size() - 1});
         return assets;
      }
   }

   public JsonObject getNamespaceAssets(String namespace) {
      JsonObject allAssets = this.getAllAssets();
      if (!allAssets.has("itemsByNamespace")) {
         return new JsonObject();
      } else {
         JsonObject itemsByNamespace = allAssets.getAsJsonObject("itemsByNamespace");
         if (!itemsByNamespace.has(namespace)) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Namespace not found: " + namespace);
            error.addProperty("namespace", namespace);
            return error;
         } else {
            JsonObject result = new JsonObject();
            result.addProperty("namespace", namespace);
            result.add("items", itemsByNamespace.get(namespace));
            result.addProperty("count", itemsByNamespace.getAsJsonArray(namespace).size());
            if (!namespace.equals("minecraft")) {
               try {
                  Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(namespace);
                  if (modContainer.isPresent()) {
                     result.addProperty("modName", modContainer.get().getModInfo().getDisplayName());
                     result.addProperty("modVersion", modContainer.get().getModInfo().getVersion().toString());
                  }
               } catch (Exception var6) {
                  LOGGER.debug("Could not get mod info for namespace: {}", namespace);
               }
            }

            return result;
         }
      }
   }

   public void clearCache() {
      this.cachedAssets = null;
      this.lastCacheTime = 0L;
      LOGGER.info("Server asset cache cleared");
   }
}
