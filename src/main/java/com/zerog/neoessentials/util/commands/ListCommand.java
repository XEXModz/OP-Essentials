package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(ListCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      boolean enabled = ConfigManager.getInstance().isCommandEnabled("list");
      if (!enabled) {
         LOGGER.debug("Skipped registering 'list' and 'who' commands (disabled in config)");
      } else {
         dispatcher.register((LiteralArgumentBuilder)Commands.literal("list").executes(ctx -> {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), "neoessentials.list");
            if (!permResult.hasPermission()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            } else {
               return showOnlinePlayersList((CommandSourceStack)ctx.getSource(), permResult.hasPermission() ? permResult.getPlayer() : null);
            }
         }));
         dispatcher.register((LiteralArgumentBuilder)Commands.literal("who").executes(ctx -> {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), "neoessentials.list");
            if (!permResult.hasPermission()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            } else {
               return showOnlinePlayersList((CommandSourceStack)ctx.getSource(), permResult.hasPermission() ? permResult.getPlayer() : null);
            }
         }));
         dispatcher.register((LiteralArgumentBuilder)Commands.literal("online").executes(ctx -> {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), "neoessentials.list");
            if (!permResult.hasPermission()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            } else {
               return showOnlinePlayersList((CommandSourceStack)ctx.getSource(), permResult.hasPermission() ? permResult.getPlayer() : null);
            }
         }));
         LOGGER.debug("Registered 'list', 'who', and 'online' commands");
      }
   }

   private static int showOnlinePlayersList(CommandSourceStack source, ServerPlayer viewer) {
      PlayerList playerList = source.getServer().getPlayerList();
      List<ServerPlayer> onlinePlayers = new ArrayList<>(playerList.getPlayers());
      boolean canSeeVanished = viewer != null
         && PermissionValidator.validatePermission(viewer.createCommandSourceStack(), "neoessentials.vanish.see").hasPermission();
      if (!canSeeVanished) {
         onlinePlayers = onlinePlayers.stream().filter(player -> !isVanished(player)).collect(Collectors.toList());
      }

      int visibleCount = onlinePlayers.size();
      int totalCount = playerList.getPlayerCount();
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.separator"), false);
      if (canSeeVanished && visibleCount != totalCount) {
         source.sendSuccess(
            () -> MessageUtil.component("commands.neoessentials.list.header_with_visible", visibleCount, totalCount, playerList.getMaxPlayers()), false
         );
      } else {
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.header", visibleCount, playerList.getMaxPlayers()), false);
      }

      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.separator"), false);
      if (onlinePlayers.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.no_players"), false);
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.separator"), false);
         return 1;
      } else {
         boolean useLuckPerms = ModList.get().isLoaded("luckperms") && isLuckPermsAvailable();
         if (useLuckPerms) {
            displayLuckPermsGroupedList(source, onlinePlayers, canSeeVanished);
         } else {
            displaySimpleGroupedList(source, onlinePlayers, canSeeVanished);
         }

         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.list.separator"), false);
         return 1;
      }
   }

   private static void displayLuckPermsGroupedList(CommandSourceStack source, List<ServerPlayer> players, boolean canSeeVanished) {
      try {
         LuckPerms luckPerms = LuckPermsProvider.get();
         Map<String, ListCommand.GroupInfo> groupedPlayers = new LinkedHashMap<>();

         for (ServerPlayer player : players) {
            try {
               User lpUser = luckPerms.getUserManager().getUser(player.getUUID());
               if (lpUser == null) {
                  groupedPlayers.computeIfAbsent("Players", k -> new ListCommand.GroupInfo(0)).players.add(player);
               } else {
                  String primaryGroup = lpUser.getPrimaryGroup();
                  Group lpGroup = luckPerms.getGroupManager().getGroup(primaryGroup);
                  if (lpGroup == null) {
                     groupedPlayers.computeIfAbsent(primaryGroup, k -> new ListCommand.GroupInfo(0)).players.add(player);
                  } else {
                     int weight = lpGroup.getWeight().orElse(0);
                     ListCommand.GroupInfo groupInfo = groupedPlayers.computeIfAbsent(primaryGroup, k -> new ListCommand.GroupInfo(weight));
                     groupInfo.players.add(player);
                  }
               }
            } catch (Exception var16) {
               LOGGER.warn("Error getting LuckPerms group for player {}: {}", player.getName().getString(), var16.getMessage());
               groupedPlayers.computeIfAbsent("Players", k -> new ListCommand.GroupInfo(0)).players.add(player);
            }
         }

         for (Entry<String, ListCommand.GroupInfo> entry : groupedPlayers.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue().weight, a.getValue().weight))
            .toList()) {
            String groupName = entry.getKey();
            ListCommand.GroupInfo groupInfo = entry.getValue();
            List<ServerPlayer> groupPlayers = groupInfo.players;
            groupPlayers.sort(Comparator.comparing(p -> p.getName().getString().toLowerCase()));
            String weightIndicator = groupInfo.weight > 0 ? " §8[" + groupInfo.weight + "]" : "";
            MutableComponent groupHeader = Component.literal("§6" + groupName + weightIndicator + " §7(" + groupPlayers.size() + "): ");
            source.sendSuccess(() -> groupHeader, false);
            MutableComponent playerLine = Component.literal("  §f");

            for (int i = 0; i < groupPlayers.size(); i++) {
               ServerPlayer player = groupPlayers.get(i);
               playerLine.append(createPlayerComponent(player, canSeeVanished));
               if (i < groupPlayers.size() - 1) {
                  playerLine.append(Component.literal("§7, "));
               }
            }

            source.sendSuccess(() -> playerLine, false);
         }

         sendListFooter(source, players, canSeeVanished);
      } catch (Exception var17) {
         LOGGER.error("Error displaying LuckPerms grouped list: {}", var17.getMessage(), var17);
         displaySimpleGroupedList(source, players, canSeeVanished);
      }
   }

   private static void displaySimpleGroupedList(CommandSourceStack source, List<ServerPlayer> players, boolean canSeeVanished) {
      Map<String, List<ServerPlayer>> groupedPlayers = new LinkedHashMap<>();

      for (ServerPlayer player : players) {
         String group = getPlayerGroup(player);
         groupedPlayers.computeIfAbsent(group, k -> new ArrayList<>()).add(player);
      }

      for (Entry<String, List<ServerPlayer>> entry : groupedPlayers.entrySet()) {
         String groupName = entry.getKey();
         List<ServerPlayer> groupPlayers = entry.getValue();
         groupPlayers.sort(Comparator.comparing(p -> p.getName().getString().toLowerCase()));
         MutableComponent groupHeader = Component.literal("§6" + groupName + " §7(" + groupPlayers.size() + "): ");
         source.sendSuccess(() -> groupHeader, false);
         MutableComponent playerLine = Component.literal("  §f");

         for (int i = 0; i < groupPlayers.size(); i++) {
            ServerPlayer player = groupPlayers.get(i);
            playerLine.append(createPlayerComponent(player, canSeeVanished));
            if (i < groupPlayers.size() - 1) {
               playerLine.append(Component.literal("§7, "));
            }
         }

         source.sendSuccess(() -> playerLine, false);
      }

      sendListFooter(source, players, canSeeVanished);
   }

   private static MutableComponent createPlayerComponent(ServerPlayer player, boolean canSeeVanished) {
      String playerName = player.getName().getString();
      MutableComponent component = Component.literal(playerName);
      if (isVanished(player) && canSeeVanished) {
         component = component.withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});
      } else if (isAfk(player)) {
         component = component.withStyle(ChatFormatting.YELLOW);
      } else {
         component = component.withStyle(ChatFormatting.WHITE);
      }

      List<String> statusIndicators = new ArrayList<>();
      if (isAfk(player)) {
         statusIndicators.add("§eAFK");
      }

      if (isVanished(player) && canSeeVanished) {
         statusIndicators.add("§7V");
      }

      if (player.hasPermissions(4)) {
         statusIndicators.add("§cOP");
      }

      if (!statusIndicators.isEmpty()) {
         component.append(Component.literal(" §8[" + String.join("§7,", statusIndicators) + "§8]"));
      }

      List<Component> hoverLines = new ArrayList<>();
      hoverLines.add(Component.literal("§6Player: §f" + playerName));
      String worldName = player.level().dimension().location().getPath();
      hoverLines.add(Component.literal("§6World: §f" + worldName));
      hoverLines.add(Component.literal("§6Location: §f" + (int)player.getX() + ", " + (int)player.getY() + ", " + (int)player.getZ()));
      String groupInfo = getLuckPermsGroupInfo(player);
      if (groupInfo != null) {
         hoverLines.add(Component.literal("§6Group: §f" + groupInfo));
      }

      if (isAfk(player)) {
         String reason = getAfkReason(player);
         if (reason != null && !reason.isEmpty()) {
            hoverLines.add(Component.literal("§6AFK Reason: §f" + reason));
         } else {
            hoverLines.add(Component.literal("§eCurrently AFK"));
         }
      }

      hoverLines.add(Component.literal("§6Ping: §f" + player.connection.latency() + "ms"));
      hoverLines.add(Component.literal(""));
      hoverLines.add(Component.literal("§7Click to message this player"));
      MutableComponent hoverText = Component.empty();

      for (int i = 0; i < hoverLines.size(); i++) {
         if (i > 0) {
            hoverText.append("\n");
         }

         hoverText.append(hoverLines.get(i));
      }

      return component.withStyle(
         style -> style.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, hoverText))
               .withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " "))
      );
   }

   private static void sendListFooter(CommandSourceStack source, List<ServerPlayer> players, boolean canSeeVanished) {
      int afkCount = 0;
      int vanishedCount = 0;
      int opCount = 0;

      for (ServerPlayer player : players) {
         if (isAfk(player)) {
            afkCount++;
         }

         if (isVanished(player)) {
            vanishedCount++;
         }

         if (player.hasPermissions(4)) {
            opCount++;
         }
      }

      List<String> statusSummary = new ArrayList<>();
      if (afkCount > 0) {
         statusSummary.add("§e" + afkCount + " AFK");
      }

      if (vanishedCount > 0 && canSeeVanished) {
         statusSummary.add("§7" + vanishedCount + " Vanished");
      }

      if (opCount > 0) {
         statusSummary.add("§c" + opCount + " OP");
      }

      if (!statusSummary.isEmpty()) {
         MutableComponent footer = Component.literal("§7Status: " + String.join("§7, ", statusSummary));
         source.sendSuccess(() -> footer, false);
      }
   }

   private static String getLuckPermsGroupInfo(ServerPlayer player) {
      try {
         if (ModList.get().isLoaded("luckperms") && isLuckPermsAvailable()) {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User lpUser = luckPerms.getUserManager().getUser(player.getUUID());
            if (lpUser != null) {
               String primaryGroup = lpUser.getPrimaryGroup();
               Group lpGroup = luckPerms.getGroupManager().getGroup(primaryGroup);
               if (lpGroup != null) {
                  int weight = lpGroup.getWeight().orElse(0);
                  return primaryGroup + (weight > 0 ? " [" + weight + "]" : "");
               }

               return primaryGroup;
            }
         }
      } catch (Exception var6) {
         LOGGER.debug("Error getting LuckPerms group info for player {}: {}", player.getName().getString(), var6.getMessage());
      }

      return null;
   }

   private static boolean isLuckPermsAvailable() {
      try {
         LuckPermsProvider.get();
         return true;
      } catch (Exception var1) {
         return false;
      }
   }

   private static String getPlayerGroup(ServerPlayer player) {
      if (player.hasPermissions(4)) {
         return "Operators";
      } else if (isVanished(player)) {
         return "Staff";
      } else if (PermissionValidator.validatePermission(player.createCommandSourceStack(), "neoessentials.staff").hasPermission()) {
         return "Staff";
      } else {
         return isAfk(player) ? "AFK Players" : "Players";
      }
   }

   private static boolean isVanished(ServerPlayer player) {
      if (!ConfigManager.getInstance().isVanishSystemEnabled()) {
         return false;
      } else {
         try {
            VanishManager vanishManager = VanishManager.getInstance();
            return vanishManager.isPlayerVanished(player.getUUID());
         } catch (Exception var2) {
            return false;
         }
      }
   }

   private static boolean isAfk(ServerPlayer player) {
      if (!ConfigManager.isChatEnabled()) {
         return false;
      } else {
         try {
            AfkManager afkManager = AfkManager.getInstance();
            return afkManager.isAfk(player);
         } catch (Exception var2) {
            return false;
         }
      }
   }

   private static String getAfkReason(ServerPlayer player) {
      if (!ConfigManager.isChatEnabled()) {
         return null;
      } else {
         try {
            AfkManager afkManager = AfkManager.getInstance();
            return afkManager.getAfkReason(player.getUUID());
         } catch (Exception var2) {
            return null;
         }
      }
   }

   private static class GroupInfo {
      int weight;
      List<ServerPlayer> players = new ArrayList<>();

      GroupInfo(int weight) {
         this.weight = weight;
      }
   }
}
