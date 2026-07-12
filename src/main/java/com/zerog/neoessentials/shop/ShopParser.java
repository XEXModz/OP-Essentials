package com.zerog.neoessentials.shop;

import com.zerog.neoessentials.economy.worth.WorthManager;
import com.zerog.neoessentials.shop.model.ShopData;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShopParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(ShopParser.class);
   public static final int MAX_QUANTITY = 3456;
   public static final String AUTOFILL_CODE = "?";

   private ShopParser() {
   }

   public static Optional<ShopData> parse(String[] lines, BlockPos signPos, String dimension, ServerLevel level, UUID ownerUUID, String ownerName) {
      if (lines != null && lines.length >= 4) {
         ShopData shop = new ShopData();
         String ownerLine = strip(lines[0]);
         if (ownerLine.isEmpty()) {
            if (ownerUUID == null || ownerName == null || ownerName.isBlank()) {
               LOGGER.debug("[ChestShop] Blank owner line but no player context at {}", signPos);
               return Optional.empty();
            }

            ownerLine = ownerName;
         }

         if ("Admin Shop".equalsIgnoreCase(ownerLine)) {
            shop.ownerUUID = null;
         } else {
            shop.ownerUUID = ownerUUID;
         }

         shop.ownerName = ownerLine;
         String qtyStr = strip(lines[1]);

         int quantity;
         try {
            quantity = Integer.parseInt(qtyStr);
         } catch (NumberFormatException var14) {
            LOGGER.debug("[ChestShop] Invalid quantity '{}' at {}", qtyStr, signPos);
            return Optional.empty();
         }

         if (quantity >= 1 && quantity <= 3456) {
            shop.quantity = quantity;
            String priceLine = strip(lines[2]).toUpperCase();
            if (!parsePriceLine(priceLine, shop)) {
               LOGGER.debug("[ChestShop] Invalid price line '{}' at {}", priceLine, signPos);
               return Optional.empty();
            } else if (!shop.canBuy() && !shop.canSell()) {
               return Optional.empty();
            } else {
               String itemRaw = strip(lines[3]);
               if (itemRaw.isEmpty()) {
                  return Optional.empty();
               } else {
                  if ("?".equals(itemRaw)) {
                     shop.itemId = null;
                     shop.itemPending = true;
                  } else {
                     String itemStr = normalizeItemStr(itemRaw);
                     ItemStack resolved = resolveItem(itemStr);
                     if (resolved == null || resolved.isEmpty()) {
                        LOGGER.debug(
                           "[ChestShop] Could not resolve item '{}' (normalised: '{}') at {} — check the item ID is correct (e.g. 'thermal:copper_ingot' or 'diamond')",
                           new Object[]{itemRaw, itemStr, signPos}
                        );
                        return Optional.empty();
                     }

                     shop.itemId = WorthManager.getItemId(resolved);
                     shop.itemPending = false;
                  }

                  shop.signDimension = dimension;
                  shop.signX = signPos.getX();
                  shop.signY = signPos.getY();
                  shop.signZ = signPos.getZ();
                  if (!shop.isAdminShop()) {
                     BlockPos chestPos = findAdjacentChest(signPos, level);
                     if (chestPos == null) {
                        LOGGER.debug("[ChestShop] No chest found adjacent to sign at {}", signPos);
                        return Optional.empty();
                     }

                     shop.hasChest = true;
                     shop.chestDimension = dimension;
                     shop.chestX = chestPos.getX();
                     shop.chestY = chestPos.getY();
                     shop.chestZ = chestPos.getZ();
                  } else {
                     shop.hasChest = false;
                  }

                  return Optional.of(shop);
               }
            }
         } else {
            LOGGER.debug("[ChestShop] Quantity {} out of range at {}", quantity, signPos);
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   public static Optional<ShopData> parse(String[] lines, BlockPos signPos, String dimension, ServerLevel level, UUID ownerUUID) {
      String name = null;
      if (ownerUUID != null) {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(ownerUUID);
            if (p != null) {
               name = p.getName().getString();
            }
         }
      }

      return parse(lines, signPos, dimension, level, ownerUUID, name);
   }

   private static boolean parsePriceLine(String line, ShopData shop) {
      if (line.contains(":")) {
         String[] parts = line.split(":", 2);
         BigDecimal p0 = parsePricePart(parts[0].trim());
         BigDecimal p1 = parsePricePart(parts[1].trim());
         if (p0 != null && p1 != null) {
            String t0 = parts[0].trim();
            String t1 = parts[1].trim();
            if (t0.startsWith("B")) {
               shop.buyPrice = p0;
            } else {
               if (!t0.startsWith("S")) {
                  return false;
               }

               shop.sellPrice = p0;
            }

            if (t1.startsWith("B")) {
               shop.buyPrice = p1;
            } else {
               if (!t1.startsWith("S")) {
                  return false;
               }

               shop.sellPrice = p1;
            }

            return true;
         } else {
            return false;
         }
      } else {
         BigDecimal price = parsePricePart(line);
         if (price == null) {
            return false;
         } else if (line.startsWith("B")) {
            shop.buyPrice = price;
            return true;
         } else if (line.startsWith("S")) {
            shop.sellPrice = price;
            return true;
         } else {
            return false;
         }
      }
   }

   private static BigDecimal parsePricePart(String part) {
      part = part.trim();
      if (part.startsWith("B") || part.startsWith("S")) {
         part = part.substring(1).trim();
      }

      if (!part.equalsIgnoreCase("FREE") && !part.isEmpty()) {
         try {
            if (part.endsWith("K")) {
               return new BigDecimal(part.substring(0, part.length() - 1)).multiply(BigDecimal.valueOf(1000L));
            } else if (part.endsWith("M")) {
               return new BigDecimal(part.substring(0, part.length() - 1)).multiply(BigDecimal.valueOf(1000000L));
            } else {
               BigDecimal val = new BigDecimal(part);
               return val.compareTo(BigDecimal.ZERO) < 0 ? null : val;
            }
         } catch (NumberFormatException var2) {
            return null;
         }
      } else {
         return BigDecimal.ZERO;
      }
   }

   public static ItemStack resolveItem(String itemStr) {
      try {
         ItemStack fromWorth = WorthManager.resolveItem(itemStr);
         if (fromWorth != null && !fromWorth.isEmpty()) {
            return fromWorth;
         }
      } catch (Exception var4) {
      }

      String id = itemStr.toLowerCase().trim();
      if (!id.contains(":")) {
         id = "minecraft:" + id;
      }

      try {
         Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
         if (item.isPresent()) {
            return new ItemStack((ItemLike)item.get());
         }
      } catch (Exception var3) {
      }

      return ItemStack.EMPTY;
   }

   public static BlockPos findAdjacentChest(BlockPos signPos, ServerLevel level) {
      BlockPos[] candidates = new BlockPos[]{signPos.below(), signPos.above(), signPos.north(), signPos.south(), signPos.east(), signPos.west()};

      for (BlockPos candidate : candidates) {
         BlockEntity be = level.getBlockEntity(candidate);
         if (be instanceof ChestBlockEntity) {
            return candidate;
         }
      }

      return null;
   }

   private static String strip(String s) {
      return s == null ? "" : s.replaceAll("§[0-9a-fA-FkKlLmMnNoOrR]", "").trim();
   }

   public static String normalizeItemStr(String raw) {
      if (raw == null) {
         return "";
      } else {
         String s = raw.replaceAll("§[0-9a-fA-FkKlLmMnNoOrR]", "").trim();
         if (!s.endsWith("…") && !s.endsWith("...")) {
            s = s.replace(" ", "_");
            return s.toLowerCase();
         } else {
            return s;
         }
      }
   }

   public static String[] formatSignLines(ShopData shop) {
      String ownerLine = shop.isAdminShop() ? "§2Admin Shop" : "§b" + shop.ownerName;
      StringBuilder priceBuilder = new StringBuilder();
      if (shop.buyPrice != null) {
         priceBuilder.append("§aB ").append(shop.buyPrice.toPlainString());
      }

      if (shop.buyPrice != null && shop.sellPrice != null) {
         priceBuilder.append("§f:");
      }

      if (shop.sellPrice != null) {
         priceBuilder.append("§6S ").append(shop.sellPrice.toPlainString());
      }

      String itemLine;
      if (!shop.itemPending && shop.itemId != null) {
         itemLine = buildItemDisplayName(shop.itemId);
      } else {
         itemLine = "§e§l?";
      }

      return new String[]{ownerLine, String.valueOf(shop.quantity), priceBuilder.toString(), itemLine};
   }

   public static String buildItemDisplayName(String fullId) {
      if (fullId != null && !fullId.isBlank()) {
         String display = fullId.startsWith("minecraft:") ? fullId.substring(10) : fullId;
         display = display.replace("_", " ");
         if (display.length() > 16) {
            display = display.substring(0, 15) + "…";
         }

         return display;
      } else {
         return "?";
      }
   }
}
