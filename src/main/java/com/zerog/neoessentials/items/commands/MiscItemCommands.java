package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiscItemCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(MiscItemCommands.class);
   private static final Set<UUID> payConfirmDisabled = ConcurrentHashMap.newKeySet();
   private static final Set<UUID> ciConfirmDisabled = ConcurrentHashMap.newKeySet();
   private static final Map<String, MiscItemCommands.CondenseRecipe> CONDENSE_MAP = new LinkedHashMap<>();
   private static final int LINES_PER_PAGE = 10;
   private static final Set<UUID> rToggleDisabled = ConcurrentHashMap.newKeySet();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerCondense(dispatcher);
      registerShowKit(dispatcher);
      registerPowertoolList(dispatcher);
      registerCustomText(dispatcher);
      registerPayConfirmToggle(dispatcher);
      registerCiConfirmToggle(dispatcher);
      registerItem(dispatcher);
      registerRToggle(dispatcher);
   }

   private static void registerCondense(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("condense").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.condense");
            })).executes(ctx -> executeCondense(ctx, null)))
            .then(
               Commands.argument("item", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::getPath), b))
                  .executes(ctx -> executeCondense(ctx, StringArgumentType.getString(ctx, "item")))
            )
      );
   }

   private static int executeCondense(CommandContext<CommandSourceStack> ctx, String filterItem) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         Inventory inv = player.getInventory();
         int convertCount = 0;
         Iterator fc = CONDENSE_MAP.entrySet().iterator();

         while (true) {
            String inputId;
            MiscItemCommands.CondenseRecipe recipe;
            String filter;
            do {
               if (!fc.hasNext()) {
                  if (convertCount > 0) {
                     player.inventoryMenu.sendAllDataToRemote();
                     int fcx = convertCount;
                     src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.condense.success", fc), false);
                     LOGGER.info("{} condensed {} item type(s)", player.getName().getString(), convertCount);
                  } else {
                     src.sendFailure(MessageUtil.error("commands.neoessentials.condense.nothing"));
                  }

                  return convertCount > 0 ? 1 : 0;
               }

               Entry<String, MiscItemCommands.CondenseRecipe> entry = (Entry<String, MiscItemCommands.CondenseRecipe>)fc.next();
               inputId = entry.getKey();
               recipe = entry.getValue();
               if (filterItem == null) {
                  break;
               }

               filter = filterItem.contains(":") ? filterItem : "minecraft:" + filterItem;
            } while (!inputId.equals(filter));

            ResourceLocation inputLoc = ResourceLocation.tryParse(inputId);
            ResourceLocation outputLoc = ResourceLocation.tryParse(recipe.outputItemId());
            if (inputLoc != null && outputLoc != null) {
               Item inputItem = (Item)BuiltInRegistries.ITEM.get(inputLoc);
               Item outputItem = (Item)BuiltInRegistries.ITEM.get(outputLoc);
               if (inputItem != null && outputItem != null) {
                  int totalInput = 0;

                  for (int i = 0; i < inv.getContainerSize(); i++) {
                     ItemStack s = inv.getItem(i);
                     if (!s.isEmpty() && s.getItem() == inputItem) {
                        totalInput += s.getCount();
                     }
                  }

                  int sets = totalInput / recipe.inputCount();
                  if (sets != 0) {
                     int toRemove = sets * recipe.inputCount();
                     int remaining = toRemove;

                     for (int ix = 0; ix < inv.getContainerSize() && remaining > 0; ix++) {
                        ItemStack s = inv.getItem(ix);
                        if (!s.isEmpty() && s.getItem() == inputItem) {
                           int take = Math.min(remaining, s.getCount());
                           s.shrink(take);
                           remaining -= take;
                           if (s.isEmpty()) {
                              inv.setItem(ix, ItemStack.EMPTY);
                           }
                        }
                     }

                     ItemStack output = new ItemStack(outputItem, sets);
                     if (!player.getInventory().add(output)) {
                        player.drop(output, false);
                     }

                     convertCount++;
                  }
               }
            }
         }
      }
   }

   private static void registerShowKit(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("showkit").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.showkit");
            }))
            .then(
               Commands.argument("kit", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(KitManager.getInstance().getKitNames(), b))
                  .executes(ctx -> executeShowKit(ctx, StringArgumentType.getString(ctx, "kit")))
            )
      );
   }

   private static int executeShowKit(CommandContext<CommandSourceStack> ctx, String kitNames) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      int shown = 0;

      for (String kitName : kitNames.split(",")) {
         kitName = kitName.trim().toLowerCase();
         Kit kit = KitManager.getInstance().getKit(kitName);
         if (kit == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.kits.not_found", kitName));
         } else {
            src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.showkit.header", kit.getDisplayName()), false);
            if (!kit.getDescription().isEmpty()) {
               src.sendSuccess(() -> Component.literal("§7" + kit.getDescription()), false);
            }

            long cdMs = kit.getCooldownMillis();
            String cdStr = cdMs <= 0L ? "No cooldown" : formatDuration(cdMs);
            src.sendSuccess(() -> Component.literal("§7Cooldown: §e" + cdStr), false);

            for (ItemStack stack : kit.getItems()) {
               String itemName = stack.getItem().getDescription().getString();
               int count = stack.getCount();
               src.sendSuccess(() -> Component.literal("  §a- §f" + count + "x §e" + itemName), false);
            }

            shown++;
         }
      }

      return shown > 0 ? 1 : 0;
   }

   private static void registerPowertoolList(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("powertoollist").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.powertoollist");
      })).executes(ctx -> executePowertoolList(ctx)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ptlist").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.powertoollist");
      })).executes(ctx -> executePowertoolList(ctx)));
   }

   private static int executePowertoolList(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         Map<String, String> powers = PowertoolCommand.getPlayerPowertools(player.getUUID());
         if (powers.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.powertoollist.empty"));
            return 0;
         } else {
            src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.powertoollist.header", powers.size()), false);

            for (Entry<String, String> entry : powers.entrySet()) {
               String itemName = entry.getKey().contains(":") ? entry.getKey().substring(entry.getKey().indexOf(58) + 1) : entry.getKey();
               String cmd = entry.getValue();
               src.sendSuccess(() -> Component.literal("  §e" + itemName + " §8→ §7" + cmd), false);
            }

            return 1;
         }
      }
   }

   private static void registerCustomText(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("customtext").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.customtext");
            })).executes(ctx -> executeCustomText(ctx, "info", 1)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("chapter", StringArgumentType.word())
                     .executes(ctx -> executeCustomText(ctx, StringArgumentType.getString(ctx, "chapter"), 1)))
                  .then(
                     Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeCustomText(ctx, StringArgumentType.getString(ctx, "chapter"), IntegerArgumentType.getInteger(ctx, "page")))
                  )
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ctext").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.customtext");
            })).executes(ctx -> executeCustomText(ctx, "info", 1)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("chapter", StringArgumentType.word())
                     .executes(ctx -> executeCustomText(ctx, StringArgumentType.getString(ctx, "chapter"), 1)))
                  .then(
                     Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeCustomText(ctx, StringArgumentType.getString(ctx, "chapter"), IntegerArgumentType.getInteger(ctx, "page")))
                  )
            )
      );
   }

   private static int executeCustomText(CommandContext<CommandSourceStack> ctx, String chapter, int page) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      String safeChapter = chapter.replaceAll("[^a-zA-Z0-9_\\-]", "");
      if (safeChapter.isEmpty()) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.customtext.invalid_chapter"));
         return 0;
      } else {
         Path textDir = Paths.get("config", "neoessentials", "text");
         Path file = textDir.resolve(safeChapter + ".txt");
         if (!Files.exists(file)) {
            try {
               Files.createDirectories(textDir);
               if (safeChapter.equals("info")) {
                  Files.writeString(
                     file, "§6Welcome to the server!\n§7Edit this file at: config/neoessentials/text/info.txt\n§7You can use §r&§7 colour codes.\n"
                  );
               }
            } catch (IOException var18) {
               LOGGER.warn("Could not create text dir: {}", var18.getMessage());
            }

            if (!Files.exists(file)) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.customtext.not_found", safeChapter));
               return 0;
            }
         }

         List<String> lines;
         try {
            lines = Files.readAllLines(file);
         } catch (IOException var17) {
            LOGGER.error("Failed to read custom text file '{}': {}", file, var17.getMessage());
            src.sendFailure(MessageUtil.error("commands.neoessentials.customtext.read_error"));
            return 0;
         }

         String playerName = src.getPlayer() != null ? src.getPlayer().getName().getString() : "Server";
         List<String> formatted = new ArrayList<>();

         for (String line : lines) {
            formatted.add(line.replace("&", "§").replace("{player}", playerName));
         }

         int totalPages = Math.max(1, (int)Math.ceil((double)formatted.size() / 10.0));
         int p = Math.max(1, Math.min(page, totalPages));
         int start = (p - 1) * 10;
         int end = Math.min(start + 10, formatted.size());
         String header = "§6══ §e" + safeChapter + " §7(Page " + p + "/" + totalPages + ") §6══";
         src.sendSuccess(() -> Component.literal(header), false);

         for (int i = start; i < end; i++) {
            String line = formatted.get(i);
            src.sendSuccess(() -> Component.literal(line), false);
         }

         if (totalPages > 1) {
            src.sendSuccess(() -> Component.literal("§7Use §e/customtext " + safeChapter + " <page>§7 to view other pages."), false);
         }

         return 1;
      }
   }

   private static void registerPayConfirmToggle(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("payconfirmtoggle").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.payconfirmtoggle");
      })).executes(ctx -> executePayConfirmToggle(ctx)));
   }

   private static int executePayConfirmToggle(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         UUID uuid = player.getUUID();
         boolean nowDisabled;
         if (payConfirmDisabled.contains(uuid)) {
            payConfirmDisabled.remove(uuid);
            nowDisabled = false;
         } else {
            payConfirmDisabled.add(uuid);
            nowDisabled = true;
         }

         String state = nowDisabled ? "§cdisabled" : "§aenabled";
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.payconfirmtoggle.toggled", state), false);
         return 1;
      }
   }

   public static boolean isPayConfirmDisabled(UUID uuid) {
      return payConfirmDisabled.contains(uuid);
   }

   private static void registerCiConfirmToggle(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ciconfirmtoggle").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.ciconfirmtoggle");
      })).executes(ctx -> executeCiConfirmToggle(ctx)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("clearinventoryconfirmtoggle").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.ciconfirmtoggle");
      })).executes(ctx -> executeCiConfirmToggle(ctx)));
   }

   private static int executeCiConfirmToggle(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         UUID uuid = player.getUUID();
         boolean nowDisabled;
         if (ciConfirmDisabled.contains(uuid)) {
            ciConfirmDisabled.remove(uuid);
            nowDisabled = false;
         } else {
            ciConfirmDisabled.add(uuid);
            nowDisabled = true;
         }

         String state = nowDisabled ? "§cdisabled" : "§aenabled";
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ciconfirmtoggle.toggled", state), false);
         return 1;
      }
   }

   public static boolean isCiConfirmDisabled(UUID uuid) {
      return ciConfirmDisabled.contains(uuid);
   }

   private static void registerItem(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("item").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.item");
            }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), b))
                     .executes(ctx -> executeItem(ctx, StringArgumentType.getString(ctx, "item"), -1)))
                  .then(
                     Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> executeItem(ctx, StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "amount")))
                  )
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("i").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.item");
            }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), b))
                     .executes(ctx -> executeItem(ctx, StringArgumentType.getString(ctx, "item"), -1)))
                  .then(
                     Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> executeItem(ctx, StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "amount")))
                  )
            )
      );
   }

   private static int executeItem(CommandContext<CommandSourceStack> ctx, String itemId, int amount) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         String id = itemId.contains(":") ? itemId : "minecraft:" + itemId;
         ResourceLocation loc = ResourceLocation.tryParse(id);
         if (loc == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.item.unknown", itemId));
            return 0;
         } else {
            Item item = (Item)BuiltInRegistries.ITEM.get(loc);
            if (item == Items.AIR && !itemId.equalsIgnoreCase("air")) {
               item = BuiltInRegistries.ITEM
                  .entrySet()
                  .stream()
                  .filter(e -> ((ResourceKey)e.getKey()).location().getPath().equals(itemId.toLowerCase()))
                  .map(Entry::getValue)
                  .findFirst()
                  .orElse(null);
            }

            if (item != null && (item != Items.AIR || itemId.equalsIgnoreCase("air"))) {
               int qty = amount > 0 ? amount : item.getDefaultMaxStackSize();
               ItemStack stack = new ItemStack(item, qty);
               if (!player.getInventory().add(stack)) {
                  player.drop(stack, false);
               }

               String fname = item.getDescription().getString();
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.item.given", qty, fname), false);
               return 1;
            } else {
               src.sendFailure(MessageUtil.error("commands.neoessentials.item.unknown", itemId));
               return 0;
            }
         }
      }
   }

   private static void registerRToggle(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rtoggle")
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.rtoggle");
                        }))
                     .executes(ctx -> executeRToggle(ctx, null, null)))
                  .then(Commands.literal("on").executes(ctx -> executeRToggle(ctx, null, true))))
               .then(Commands.literal("off").executes(ctx -> executeRToggle(ctx, null, false))))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.rtoggle.others")))
                        .executes(ctx -> executeRToggle(ctx, StringArgumentType.getString(ctx, "target"), null)))
                     .then(Commands.literal("on").executes(ctx -> executeRToggle(ctx, StringArgumentType.getString(ctx, "target"), true))))
                  .then(Commands.literal("off").executes(ctx -> executeRToggle(ctx, StringArgumentType.getString(ctx, "target"), false)))
            )
      );
   }

   private static int executeRToggle(CommandContext<CommandSourceStack> ctx, String targetName, Boolean enable) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = targetName != null ? src.getServer().getPlayerList().getPlayerByName(targetName) : src.getPlayer();
      if (target == null) {
         if (targetName != null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         } else {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return 0;
      } else {
         UUID uuid = target.getUUID();
         boolean curDisabled = rToggleDisabled.contains(uuid);
         boolean newDisabled = enable != null ? !enable : !curDisabled;
         if (newDisabled) {
            rToggleDisabled.add(uuid);
         } else {
            rToggleDisabled.remove(uuid);
         }

         String label = newDisabled ? "§cdisabled" : "§aenabled";
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(uuid);
         if (isOther) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rtoggle.other", target.getName().getString(), label), false);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.rtoggle.self", label));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rtoggle.self", label), false);
         }

         return 1;
      }
   }

   public static boolean isRToggleEnabled(UUID uuid) {
      return !rToggleDisabled.contains(uuid);
   }

   private static String formatDuration(long ms) {
      long secs = ms / 1000L;
      if (secs < 60L) {
         return secs + "s";
      } else {
         long mins = secs / 60L;
         secs %= 60L;
         if (mins < 60L) {
            return mins + "m" + (secs > 0L ? secs + "s" : "");
         } else {
            long hours = mins / 60L;
            mins %= 60L;
            if (hours < 24L) {
               return hours + "h" + (mins > 0L ? mins + "m" : "");
            } else {
               long days = hours / 24L;
               hours %= 24L;
               return days + "d" + (hours > 0L ? hours + "h" : "");
            }
         }
      }
   }

   static {
      CONDENSE_MAP.put("minecraft:gold_nugget", new MiscItemCommands.CondenseRecipe(9, "minecraft:gold_ingot"));
      CONDENSE_MAP.put("minecraft:iron_nugget", new MiscItemCommands.CondenseRecipe(9, "minecraft:iron_ingot"));
      CONDENSE_MAP.put("minecraft:iron_ingot", new MiscItemCommands.CondenseRecipe(9, "minecraft:iron_block"));
      CONDENSE_MAP.put("minecraft:gold_ingot", new MiscItemCommands.CondenseRecipe(9, "minecraft:gold_block"));
      CONDENSE_MAP.put("minecraft:copper_ingot", new MiscItemCommands.CondenseRecipe(9, "minecraft:copper_block"));
      CONDENSE_MAP.put("minecraft:netherite_ingot", new MiscItemCommands.CondenseRecipe(9, "minecraft:netherite_block"));
      CONDENSE_MAP.put("minecraft:diamond", new MiscItemCommands.CondenseRecipe(9, "minecraft:diamond_block"));
      CONDENSE_MAP.put("minecraft:emerald", new MiscItemCommands.CondenseRecipe(9, "minecraft:emerald_block"));
      CONDENSE_MAP.put("minecraft:lapis_lazuli", new MiscItemCommands.CondenseRecipe(9, "minecraft:lapis_block"));
      CONDENSE_MAP.put("minecraft:redstone", new MiscItemCommands.CondenseRecipe(9, "minecraft:redstone_block"));
      CONDENSE_MAP.put("minecraft:coal", new MiscItemCommands.CondenseRecipe(9, "minecraft:coal_block"));
      CONDENSE_MAP.put("minecraft:quartz", new MiscItemCommands.CondenseRecipe(4, "minecraft:quartz_block"));
      CONDENSE_MAP.put("minecraft:wheat", new MiscItemCommands.CondenseRecipe(9, "minecraft:hay_block"));
      CONDENSE_MAP.put("minecraft:snowball", new MiscItemCommands.CondenseRecipe(4, "minecraft:snow_block"));
      CONDENSE_MAP.put("minecraft:ice", new MiscItemCommands.CondenseRecipe(9, "minecraft:packed_ice"));
      CONDENSE_MAP.put("minecraft:packed_ice", new MiscItemCommands.CondenseRecipe(9, "minecraft:blue_ice"));
      CONDENSE_MAP.put("minecraft:bone_meal", new MiscItemCommands.CondenseRecipe(9, "minecraft:bone_block"));
      CONDENSE_MAP.put("minecraft:slime_ball", new MiscItemCommands.CondenseRecipe(9, "minecraft:slime_block"));
      CONDENSE_MAP.put("minecraft:honeycomb", new MiscItemCommands.CondenseRecipe(4, "minecraft:honeycomb_block"));
      CONDENSE_MAP.put("minecraft:amethyst_shard", new MiscItemCommands.CondenseRecipe(4, "minecraft:amethyst_block"));
      CONDENSE_MAP.put("minecraft:dried_kelp", new MiscItemCommands.CondenseRecipe(9, "minecraft:dried_kelp_block"));
      CONDENSE_MAP.put("minecraft:melon_slice", new MiscItemCommands.CondenseRecipe(9, "minecraft:melon"));
      CONDENSE_MAP.put("minecraft:netherite_scrap", new MiscItemCommands.CondenseRecipe(4, "minecraft:netherite_ingot"));
   }

   private static record CondenseRecipe(int inputCount, String outputItemId) {
   }
}
