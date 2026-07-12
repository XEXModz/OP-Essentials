package com.zerog.neoessentials.kits;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.util.ResourceLocationHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class Kit {
   private final String name;
   private final String displayName;
   private final String description;
   private final List<ItemStack> items;
   private final long cooldownMillis;
   private final String permission;
   private final int maxUses;
   private final boolean enabled;

   public Kit(String name, String displayName, String description, List<ItemStack> items, long cooldownMillis, String permission, int maxUses, boolean enabled) {
      this.name = name.toLowerCase().replaceAll("[^a-z0-9_]", "");
      this.displayName = displayName != null ? displayName : name;
      this.description = description != null ? description : "";
      this.items = new ArrayList<>(items != null ? items : Collections.emptyList());
      this.cooldownMillis = Math.max(0L, cooldownMillis);
      this.permission = permission;
      this.maxUses = maxUses;
      this.enabled = enabled;
   }

   public String getName() {
      return this.name;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public String getDescription() {
      return this.description;
   }

   public List<ItemStack> getItems() {
      return new ArrayList<>(this.items);
   }

   public long getCooldownMillis() {
      return this.cooldownMillis;
   }

   public String getPermission() {
      return this.permission;
   }

   public int getMaxUses() {
      return this.maxUses;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public Map<String, Object> getMetadata() {
      return new HashMap<>();
   }

   public String getCooldownDisplay() {
      if (this.cooldownMillis == 0L) {
         return "No cooldown";
      } else {
         long seconds = TimeUnit.MILLISECONDS.toSeconds(this.cooldownMillis);
         long minutes = TimeUnit.MILLISECONDS.toMinutes(this.cooldownMillis);
         long hours = TimeUnit.MILLISECONDS.toHours(this.cooldownMillis);
         if (hours > 0L) {
            return hours + "h " + minutes % 60L + "m";
         } else {
            return minutes > 0L ? minutes + "m " + seconds % 60L + "s" : seconds + "s";
         }
      }
   }

   public boolean hasRestrictions() {
      return this.cooldownMillis > 0L || this.permission != null || this.maxUses > 0;
   }

   public Kit withEnabled(boolean enabled) {
      return new Kit(this.name, this.displayName, this.description, this.items, this.cooldownMillis, this.permission, this.maxUses, enabled);
   }

   public Kit withCooldown(long cooldownMillis) {
      return new Kit(this.name, this.displayName, this.description, this.items, cooldownMillis, this.permission, this.maxUses, this.enabled);
   }

   public Kit withPermission(String permission) {
      return new Kit(this.name, this.displayName, this.description, this.items, this.cooldownMillis, permission, this.maxUses, this.enabled);
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("name", this.name);
      json.addProperty("displayName", this.displayName);
      json.addProperty("description", this.description);
      json.addProperty("cooldownMillis", this.cooldownMillis);
      json.addProperty("permission", this.permission);
      json.addProperty("maxUses", this.maxUses);
      json.addProperty("enabled", this.enabled);
      JsonArray itemsArray = new JsonArray();

      for (ItemStack item : this.items) {
         if (!item.isEmpty()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            itemJson.addProperty("count", item.getCount());
            if (item.has(DataComponents.CUSTOM_DATA)) {
               CustomData customData = (CustomData)item.get(DataComponents.CUSTOM_DATA);
               if (customData != null) {
                  itemJson.addProperty("nbt", customData.toString());
               }
            }

            itemsArray.add(itemJson);
         }
      }

      json.add("items", itemsArray);
      return json;
   }

   public static Kit fromJson(JsonObject json) {
      String name = json.get("name").getAsString();
      String displayName = json.has("displayName") ? json.get("displayName").getAsString() : name;
      String description = json.has("description") ? json.get("description").getAsString() : "";
      long cooldownMillis = 0L;
      if (json.has("cooldownMillis")) {
         cooldownMillis = json.get("cooldownMillis").getAsLong();
      } else if (json.has("cooldown")) {
         long cooldownSeconds = json.get("cooldown").getAsLong();
         cooldownMillis = cooldownSeconds * 1000L;
      }

      String permission = json.has("permission") && !json.get("permission").getAsString().isEmpty()
         ? json.get("permission").getAsString()
         : "neoessentials.kits." + name.toLowerCase();
      int maxUses = json.has("maxUses") ? json.get("maxUses").getAsInt() : -1;
      boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
      List<ItemStack> items = new ArrayList<>();
      if (json.has("items")) {
         for (JsonElement element : json.getAsJsonArray("items")) {
            JsonObject itemJson = element.getAsJsonObject();

            try {
               String itemString = itemJson.get("item").getAsString();
               ResourceLocation itemId = ResourceLocationHelper.parse(itemString);
               Item item = (Item)BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
               if (item != null) {
                  int count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
                  ItemStack stack = new ItemStack(item, count);
                  if (itemJson.has("nbt")) {
                     try {
                        CompoundTag nbt = TagParser.parseTag(itemJson.get("nbt").getAsString());
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                     } catch (Exception var20) {
                     }
                  }

                  items.add(stack);
               }
            } catch (Exception var21) {
            }
         }
      }

      return new Kit(name, displayName, description, items, cooldownMillis, permission, maxUses, enabled);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj instanceof Kit other ? Objects.equals(this.name, other.name) : false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name);
   }

   @Override
   public String toString() {
      return String.format("Kit{name='%s', displayName='%s', items=%d, enabled=%s}", this.name, this.displayName, this.items.size(), this.enabled);
   }
}
