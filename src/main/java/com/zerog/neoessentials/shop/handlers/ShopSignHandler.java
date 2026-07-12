package com.zerog.neoessentials.shop.handlers;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.shop.ShopManager;
import com.zerog.neoessentials.shop.ShopParser;
import com.zerog.neoessentials.shop.model.ShopData;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class ShopSignHandler {
   private static final Map<String, ShopSignHandler.PendingSign> pending = new ConcurrentHashMap<>();
   private static final long TIMEOUT_MS = 30000L;
   private static final int CHECK_INTERVAL_TICKS = 5;
   private static int tickCounter = 0;

   @SubscribeEvent
   public static void onSignPlaced(EntityPlaceEvent event) {
      if (ConfigManager.isChestShopEnabled()) {
         if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getLevel() instanceof ServerLevel level) {
               BlockState state = event.getState();
               if (state.getBlock() instanceof SignBlock) {
                  BlockPos pos = event.getPos();
                  String dimension = level.dimension().location().toString();
                  String key = dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
                  pending.put(key, new ShopSignHandler.PendingSign(player.getUUID(), dimension, System.currentTimeMillis()));
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(Post event) {
      if (ConfigManager.isChestShopEnabled()) {
         if (++tickCounter % 5 == 0) {
            long now = System.currentTimeMillis();
            Iterator<Entry<String, ShopSignHandler.PendingSign>> iter = pending.entrySet().iterator();

            while (iter.hasNext()) {
               Entry<String, ShopSignHandler.PendingSign> entry = iter.next();
               ShopSignHandler.PendingSign ps = entry.getValue();
               if (now - ps.placedAt() > 30000L) {
                  iter.remove();
               } else {
                  String key = entry.getKey();
                  String[] atSplit = key.split("@", 2);
                  if (atSplit.length < 2) {
                     iter.remove();
                  } else {
                     String[] xyzParts = atSplit[1].split(",");
                     if (xyzParts.length < 3) {
                        iter.remove();
                     } else {
                        try {
                           int x = Integer.parseInt(xyzParts[0]);
                           int y = Integer.parseInt(xyzParts[1]);
                           int z = Integer.parseInt(xyzParts[2]);
                           BlockPos pos = new BlockPos(x, y, z);
                           MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                           if (server != null) {
                              ServerLevel level = (ServerLevel)server.getAllLevels().iterator().next();

                              for (ServerLevel sl : server.getAllLevels()) {
                                 if (sl.dimension().location().toString().equals(ps.dimension())) {
                                    level = sl;
                                    break;
                                 }
                              }

                              if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                                 String[] lines = readSignLines(sign);
                                 String priceLine = lines[2].toUpperCase().trim();
                                 if ((priceLine.contains("B") || priceLine.contains("S")) && !lines[1].isBlank()) {
                                    iter.remove();
                                    ServerPlayer player = server.getPlayerList().getPlayer(ps.playerUUID());
                                    if (player != null) {
                                       tryRegisterShop(player, lines, pos, ps.dimension(), level);
                                    }
                                 }
                              } else {
                                 iter.remove();
                              }
                           }
                        } catch (Exception var20) {
                           iter.remove();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void tryRegisterShop(ServerPlayer player, String[] lines, BlockPos pos, String dimension, ServerLevel level) {
      String ownerLine = lines[0].replaceAll("§[0-9a-fA-FkKlLmMnNoOrRiI]", "").trim();
      if (ownerLine.isEmpty()) {
         ownerLine = player.getName().getString();
         lines[0] = ownerLine;
      }

      boolean wantsAdmin = "Admin Shop".equalsIgnoreCase(ownerLine);
      if (wantsAdmin && !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.shop.create.admin")) {
         player.sendSystemMessage(Component.literal("§cYou don't have permission to create admin shops."));
      } else if (!wantsAdmin && !PermissionAPI.hasPermission(player.getUUID(), "neoessentials.shop.create")) {
         player.sendSystemMessage(Component.literal("§cYou don't have permission to create shops."));
      } else {
         ShopData existing = ShopManager.getInstance().getShopBySign(dimension, pos);
         if (existing != null) {
            player.sendSystemMessage(Component.literal("§cA shop already exists at this sign."));
         } else {
            Optional<ShopData> parsed = ShopParser.parse(lines, pos, dimension, level, player.getUUID(), player.getName().getString());
            if (!parsed.isEmpty()) {
               ShopData shop = parsed.get();
               ShopManager.getInstance().registerShop(shop);
               writeSignLines(level, pos, ShopParser.formatSignLines(shop));
               if (shop.itemPending) {
                  player.sendSystemMessage(Component.literal("§aShop frame created! §eRight-click this sign while holding the item you want to sell/buy."));
               } else {
                  player.sendSystemMessage(Component.literal("§aShop created successfully!"));
                  String currency = EconomyManager.getInstance().getCurrencySymbol();
                  if (!shop.isAdminShop()) {
                     if (shop.buyPrice != null) {
                        player.sendSystemMessage(Component.literal("§eBuy price:  §f" + currency + shop.buyPrice.toPlainString()));
                     }

                     if (shop.sellPrice != null) {
                        player.sendSystemMessage(Component.literal("§eSell price: §f" + currency + shop.sellPrice.toPlainString()));
                     }
                  } else {
                     player.sendSystemMessage(Component.literal("§2[Admin Shop] Unlimited stock."));
                  }
               }
            } else {
               if (!wantsAdmin && ShopParser.findAdjacentChest(pos, level) == null) {
                  player.sendSystemMessage(Component.literal("§cNo chest found next to this sign. Place a chest first."));
               } else {
                  player.sendSystemMessage(Component.literal("§cInvalid shop sign format.  Lines: [name or blank] / [qty] / [B x:S y] / [item or ?]"));
               }
            }
         }
      }
   }

   public static String[] readSignLines(SignBlockEntity sign) {
      String[] lines = new String[4];
      SignText signText = sign.getFrontText();

      for (int i = 0; i < 4; i++) {
         Component msg = signText.getMessage(i, false);
         lines[i] = msg.getString();
      }

      return lines;
   }

   public static void writeSignLines(ServerLevel level, BlockPos pos, String[] lines) {
      if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
         for (int state = 0; state < 4 && state < lines.length; state++) {
            String text = lines[state] != null ? lines[state] : "";
            Component component = Component.literal(text);
            SignText currentText = sign.getFrontText();
            SignText newText = currentText.setMessage(state, component);
            sign.updateText(s -> newText, true);
         }

         sign.setChanged();
         BlockState state = level.getBlockState(pos);
         level.sendBlockUpdated(pos, state, state, 3);
      }
   }

   private static record PendingSign(UUID playerUUID, String dimension, long placedAt) {
   }
}
