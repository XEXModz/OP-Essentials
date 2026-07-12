package com.zerog.neoessentials.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.shop.model.ShopData;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShopManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ShopManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().disableHtmlEscaping().create();
   private static ShopManager instance;
   private final ConcurrentHashMap<String, ShopData> shopsBySign = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, ShopData> shopsByChest = new ConcurrentHashMap<>();

   public static ShopManager getInstance() {
      if (instance == null) {
         instance = new ShopManager();
      }

      return instance;
   }

   private ShopManager() {
   }

   public void initialize() {
      try {
         this.load();
         LOGGER.info("[ChestShop] Loaded {} shop(s) from shops.json", this.shopsBySign.size());
      } catch (Exception var2) {
         LOGGER.error("[ChestShop] Failed to load shops.json — shops disabled until reload", var2);
      }
   }

   public void shutdown() {
      try {
         this.save();
         LOGGER.info("[ChestShop] shops.json saved on shutdown.");
      } catch (Exception var2) {
         LOGGER.error("[ChestShop] Failed to save shops.json on shutdown", var2);
      }
   }

   public void registerShop(ShopData shop) {
      this.shopsBySign.put(shop.toKey(), shop);
      if (shop.hasChest) {
         String chestKey = shop.chestDimension + "@" + shop.chestX + "," + shop.chestY + "," + shop.chestZ;
         this.shopsByChest.put(chestKey, shop);
      }

      this.trySave();
   }

   public ShopData removeShop(String dimension, BlockPos signPos) {
      String key = dimension + "@" + signPos.getX() + "," + signPos.getY() + "," + signPos.getZ();
      ShopData removed = this.shopsBySign.remove(key);
      if (removed != null && removed.hasChest) {
         String ck = removed.chestDimension + "@" + removed.chestX + "," + removed.chestY + "," + removed.chestZ;
         this.shopsByChest.remove(ck);
      }

      if (removed != null) {
         this.trySave();
      }

      return removed;
   }

   public int removeShopsByOwner(UUID ownerUUID) {
      List<ShopData> toRemove = this.shopsBySign.values().stream().filter(s -> ownerUUID.equals(s.ownerUUID)).collect(Collectors.toList());
      toRemove.forEach(s -> this.removeShop(s.signDimension, s.getSignPos()));
      return toRemove.size();
   }

   public ShopData getShopBySign(String dimension, BlockPos pos) {
      return this.shopsBySign.get(dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
   }

   public ShopData getShopByChest(String dimension, BlockPos pos) {
      return this.shopsByChest.get(dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
   }

   public List<ShopData> getShopsByOwner(UUID ownerUUID) {
      return this.shopsBySign.values().stream().filter(s -> ownerUUID.equals(s.ownerUUID)).collect(Collectors.toList());
   }

   public Collection<ShopData> getAllShops() {
      return Collections.unmodifiableCollection(this.shopsBySign.values());
   }

   public int getShopCount() {
      return this.shopsBySign.size();
   }

   private Path getDataFile() {
      return ResourceUtil.getConfigPath("shops.json");
   }

   private void load() throws IOException {
      Path file = this.getDataFile();
      if (Files.exists(file)) {
         try (Reader r = Files.newBufferedReader(file)) {
            Type listType = (new TypeToken<List<ShopData>>() {
            }).getType();
            List<ShopData> shops = (List<ShopData>)GSON.fromJson(r, listType);
            if (shops != null) {
               for (ShopData s : shops) {
                  this.shopsBySign.put(s.toKey(), s);
                  if (s.hasChest) {
                     String ck = s.chestDimension + "@" + s.chestX + "," + s.chestY + "," + s.chestZ;
                     this.shopsByChest.put(ck, s);
                  }
               }
            }
         }
      }
   }

   private void save() throws IOException {
      Path file = this.getDataFile();
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

      try (Writer w = Files.newBufferedWriter(tmp)) {
         GSON.toJson(new ArrayList<>(this.shopsBySign.values()), w);
      }

      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }

   private void trySave() {
      try {
         this.save();
      } catch (Exception var2) {
         LOGGER.error("[ChestShop] Failed to save shops.json", var2);
      }
   }

   public void reload() {
      this.shopsBySign.clear();
      this.shopsByChest.clear();

      try {
         this.load();
         LOGGER.info("[ChestShop] Reloaded {} shop(s).", this.shopsBySign.size());
      } catch (Exception var2) {
         LOGGER.error("[ChestShop] Reload failed", var2);
      }
   }
}
