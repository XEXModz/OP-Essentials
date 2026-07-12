package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.kits.Kit;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListKitsCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(ListKitsCommand.class);
   private static final int KITS_PER_PAGE = 10;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.isKitSystemEnabled()) {
         registerListKitsCommand(dispatcher, "listkits");
         registerListKitsCommand(dispatcher, "kits");
      }
   }

   private static void registerListKitsCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
                  .requires(
                     source -> source.getEntity() instanceof ServerPlayer player
                           ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.kits.list")
                           : source.hasPermission(4)
                  ))
               .executes(ListKitsCommand::listFirstPage))
            .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ListKitsCommand::listSpecificPage))
      );
   }

   private static int listFirstPage(CommandContext<CommandSourceStack> context) {
      return listKits(context, 1);
   }

   private static int listSpecificPage(CommandContext<CommandSourceStack> context) {
      int page = IntegerArgumentType.getInteger(context, "page");
      return listKits(context, page);
   }

   private static int listKits(CommandContext<CommandSourceStack> context, int page) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();

      try {
         if (source.getEntity() instanceof ServerPlayer player) {
            int cost = (int)ConfigManager.getKitCommandCost("listkits");
            if (cost > 0 && EconomyManager.getInstance().isEnabled()) {
               EconomyManager eco = EconomyManager.getInstance();
               BigDecimal bal = eco.getBalance(player.getUUID());
               if (bal.doubleValue() < (double)cost) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.listkits.not_enough_money", cost));
                  return 0;
               }

               if (!eco.subtractBalance(player.getUUID(), BigDecimal.valueOf((long)cost))) {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.listkits.charge_failed"));
                  return 0;
               }
            }
         }

         KitManager kitManager = KitManager.getInstance();
         Set<String> kitNames = kitManager.getAllKitNames();
         boolean skipUsedOneTime = ConfigManager.isSkipUsedOneTimeKitsFromKitList();
         if (skipUsedOneTime && source.getEntity() instanceof ServerPlayer playerx) {
            kitNames = kitNames.stream().filter(kitNamex -> {
               Kit kitx = kitManager.getKit(kitNamex);
               if (kitx == null) {
                  return false;
               } else {
                  int maxUses = kitx.getMaxUses();
                  if (maxUses != 1 && maxUses >= 0) {
                     return true;
                  } else {
                     int usageCount = kitManager.getUsageCount(player.getUUID(), kitNamex);
                     return usageCount < 1;
                  }
               }
            }).collect(Collectors.toSet());
         }

         if (kitNames.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.listkits.empty"), false);
            return 1;
         } else {
            List<String> sortedKitNames = kitNames.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            int totalKits = sortedKitNames.size();
            int maxPages = (int)Math.ceil((double)totalKits / 10.0);
            if (page >= 1 && page <= maxPages) {
               int startIndex = (page - 1) * 10;
               int endIndex = Math.min(startIndex + 10, totalKits);
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.listkits.header", page, maxPages, totalKits), false);
               source.sendSuccess(() -> MessageUtil.coloredText("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"), false);

               for (int i = startIndex; i < endIndex; i++) {
                  String kitName = sortedKitNames.get(i);
                  Kit kit = kitManager.getKit(kitName);
                  if (kit != null) {
                     displayKitInfo(source, kit, i + 1);
                  }
               }

               source.sendSuccess(() -> MessageUtil.coloredText("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"), false);
               if (maxPages > 1) {
                  if (page < maxPages) {
                     source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.listkits.next_page", page + 1), false);
                  }

                  if (page > 1) {
                     source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.listkits.prev_page", page - 1), false);
                  }
               }

               return 1;
            } else {
               source.sendFailure(MessageUtil.error("commands.neoessentials.listkits.invalid_page", page, maxPages));
               return 0;
            }
         }
      } catch (Exception var15) {
         LOGGER.error("Error listing kits (page {}): {}", new Object[]{page, var15.getMessage(), var15});
         source.sendFailure(MessageUtil.error("commands.neoessentials.listkits.error"));
         return 0;
      }
   }

   private static void displayKitInfo(CommandSourceStack source, Kit kit, int index) {
      String name = kit.getName();
      String displayName = kit.getDisplayName();
      int itemCount = kit.getItems().size();
      String cooldown = formatCooldown(kit.getCooldownMillis());
      String permission = kit.getPermission();
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.listkits.kit_entry", index, name, displayName, itemCount), false);
      if (!cooldown.equals("none")) {
         source.sendSuccess(() -> MessageUtil.coloredText("§8  └ Cooldown: " + cooldown), false);
      }

      if (permission != null && !permission.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.coloredText("§8  └ Permission: " + permission), false);
      }

      if (kit.getDescription() != null && !kit.getDescription().isEmpty()) {
         String desc = kit.getDescription().length() > 60 ? kit.getDescription().substring(0, 57) + "..." : kit.getDescription();
         source.sendSuccess(() -> MessageUtil.coloredText("§8  └ Description: " + desc), false);
      }
   }

   private static String formatCooldown(long millis) {
      if (millis == 0L) {
         return "none";
      } else {
         long seconds = millis / 1000L;
         if (seconds < 60L) {
            return seconds + "s";
         } else {
            long minutes = seconds / 60L;
            if (minutes < 60L) {
               return minutes + "m";
            } else {
               long hours = minutes / 60L;
               if (hours < 24L) {
                  long remainingMinutes = minutes % 60L;
                  return remainingMinutes > 0L ? hours + "h " + remainingMinutes + "m" : hours + "h";
               } else {
                  long days = hours / 24L;
                  long remainingHours = hours % 24L;
                  return remainingHours > 0L ? days + "d " + remainingHours + "h" : days + "d";
               }
            }
         }
      }
   }
}
