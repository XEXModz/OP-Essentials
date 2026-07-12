package com.zerog.neoessentials.kits.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateKitCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(CreateKitCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isKitSystemEnabled()) {
         registerCreateKitCommand(dispatcher, "createkit");
         registerCreateKitCommand(dispatcher, "makekit");
         registerCreateKitCommand(dispatcher, "addkit");
      }
   }

   private static void registerCreateKitCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer player
                        ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.create")
                        : source.hasPermission(4)
               ))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("kitname", StringArgumentType.word()).executes(CreateKitCommand::createBasicKit))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("displayname", StringArgumentType.string())
                           .executes(CreateKitCommand::createKitWithDisplayName))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("cooldown", IntegerArgumentType.integer(0))
                                 .executes(CreateKitCommand::createKitWithCooldown))
                              .then(Commands.argument("description", StringArgumentType.greedyString()).executes(CreateKitCommand::createFullKit))
                        )
                  )
            )
      );
   }

   private static int createBasicKit(CommandContext<CommandSourceStack> context) {
      return createKit(context, null, 0L, null);
   }

   private static int createKitWithDisplayName(CommandContext<CommandSourceStack> context) {
      String displayName = StringArgumentType.getString(context, "displayname");
      return createKit(context, displayName, 0L, null);
   }

   private static int createKitWithCooldown(CommandContext<CommandSourceStack> context) {
      String displayName = StringArgumentType.getString(context, "displayname");
      int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldown");
      return createKit(context, displayName, (long)cooldownSeconds * 1000L, null);
   }

   private static int createFullKit(CommandContext<CommandSourceStack> context) {
      String displayName = StringArgumentType.getString(context, "displayname");
      int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldown");
      String description = StringArgumentType.getString(context, "description");
      return createKit(context, displayName, (long)cooldownSeconds * 1000L, description);
   }

   private static int createKit(CommandContext<CommandSourceStack> context, String displayName, long cooldownMillis, String description) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      String kitName = StringArgumentType.getString(context, "kitname");
      if (source.getEntity() instanceof ServerPlayer player) {
         try {
            int cost = (int)ConfigManager.getKitCommandCost("createkit");
            if (cost > 0 && EconomyManager.getInstance().isEnabled()) {
               EconomyManager eco = EconomyManager.getInstance();
               BigDecimal bal = eco.getBalance(player.getUUID());
               if (bal.doubleValue() < (double)cost) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.not_enough_money", cost));
                  return 0;
               }

               if (!eco.subtractBalance(player.getUUID(), BigDecimal.valueOf((long)cost))) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.charge_failed"));
                  return 0;
               }
            }

            if (!isValidKitName(kitName)) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.invalid_name", kitName));
               return 0;
            } else {
               List<ItemStack> items = new ArrayList<>();
               Inventory inventory = player.getInventory();

               for (int i = 0; i < inventory.getContainerSize() - 5; i++) {
                  ItemStack item = inventory.getItem(i);
                  if (!item.isEmpty()) {
                     items.add(item.copy());
                  }
               }

               if (items.isEmpty()) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.empty_inventory"));
                  return 0;
               } else {
                  if (displayName == null) {
                     displayName = kitName;
                  }

                  if (description == null) {
                     description = "Kit created by " + player.getName().getString();
                  }

                  KitManager kitManager = KitManager.getInstance();
                  Kit existingKit = kitManager.getKit(kitName);
                  boolean isUpdate = existingKit != null;
                  String permission = "neoessentials.kits." + kitName.toLowerCase();
                  boolean usePastebin = ConfigManager.isPastebinCreatekitEnabled();
                  if (usePastebin) {
                     String kitJson = kitToJsonString(kitName, displayName, description, items, cooldownMillis, permission);
                     String pastebinUrl = uploadToPastebin(kitJson);
                     if (pastebinUrl != null) {
                        source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.createkit.pastebin_success", pastebinUrl), false);
                        LOGGER.info("Kit '{}' exported to Pastebin by {}: {}", new Object[]{kitName, player.getName().getString(), pastebinUrl});
                        return 1;
                     } else {
                        source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.pastebin_failed"));
                        return 0;
                     }
                  } else {
                     boolean success = kitManager.createKit(kitName, displayName, description, items, cooldownMillis, permission);
                     if (success) {
                        if (isUpdate) {
                           source.sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.createkit.updated", kitName, items.size(), formatCooldown(cooldownMillis)),
                              false
                           );
                        } else {
                           source.sendSuccess(
                              () -> MessageUtil.success("commands.neoessentials.createkit.created", kitName, items.size(), formatCooldown(cooldownMillis)),
                              false
                           );
                        }

                        source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.createkit.permission_hint", permission), false);
                        LOGGER.info("Kit '{}' {} by {}", new Object[]{kitName, isUpdate ? "updated" : "created", player.getName().getString()});
                        return 1;
                     } else {
                        source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.failed"));
                        return 0;
                     }
                  }
               }
            }
         } catch (Exception var18) {
            LOGGER.error("Error creating kit '{}' for player {}: {}", new Object[]{kitName, player.getName().getString(), var18.getMessage(), var18});
            source.sendFailure(MessageUtil.error("commands.neoessentials.createkit.error"));
            return 0;
         }
      } else {
         source.sendFailure(MessageUtil.error("neoessentials.error.no_server"));
         return 0;
      }
   }

   private static boolean isValidKitName(String name) {
      if (name != null && !name.trim().isEmpty()) {
         return name.length() > 32 ? false : name.matches("^[a-zA-Z0-9_-]+$");
      } else {
         return false;
      }
   }

   private static String formatCooldown(long milliseconds) {
      if (milliseconds <= 0L) {
         return "None";
      } else {
         long seconds = milliseconds / 1000L;
         long minutes = seconds / 60L;
         long hours = minutes / 60L;
         long days = hours / 24L;
         if (days > 0L) {
            return days + "d " + hours % 24L + "h";
         } else if (hours > 0L) {
            return hours + "h " + minutes % 60L + "m";
         } else {
            return minutes > 0L ? minutes + "m " + seconds % 60L + "s" : seconds + "s";
         }
      }
   }

   private static String kitToJsonString(String kitName, String displayName, String description, List<ItemStack> items, long cooldownMillis, String permission) {
      JsonObject json = new JsonObject();
      json.addProperty("name", kitName);
      json.addProperty("displayName", displayName);
      json.addProperty("description", description);
      json.addProperty("cooldownMillis", cooldownMillis);
      json.addProperty("permission", permission);
      JsonArray itemsArray = new JsonArray();

      for (ItemStack item : items) {
         JsonObject itemJson = new JsonObject();
         itemJson.addProperty("item", item.getItem().toString());
         itemJson.addProperty("count", item.getCount());
         itemsArray.add(itemJson);
      }

      json.add("items", itemsArray);
      return json.toString();
   }

   private static String uploadToPastebin(String content) {
      return "https://pastebin.com/mock/" + Integer.toHexString(content.hashCode());
   }
}
