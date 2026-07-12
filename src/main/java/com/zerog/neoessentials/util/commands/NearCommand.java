package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class NearCommand {
   private static final int DEFAULT_RADIUS = 100;
   private static final int MAX_RADIUS = 500;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("near")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("near")
                  .executes(
                     ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.near.player_only");
                        if (player == null) {
                           return 0;
                        } else {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.near"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              return showNearbyPlayers(player, 100);
                           }
                        }
                     }
                  ))
               .then(
                  Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                     .executes(
                        ctx -> {
                           ServerPlayer player = CommandSourceHelper.requirePlayer(
                              (CommandSourceStack)ctx.getSource(), "commands.neoessentials.near.player_only"
                           );
                           if (player == null) {
                              return 0;
                           } else {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.near"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                 return showNearbyPlayers(player, radius);
                              }
                           }
                        }
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("nearby")
                  .executes(
                     ctx -> {
                        ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.near.player_only");
                        if (player == null) {
                           return 0;
                        } else {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.near"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              return showNearbyPlayers(player, 100);
                           }
                        }
                     }
                  ))
               .then(
                  Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                     .executes(
                        ctx -> {
                           ServerPlayer player = CommandSourceHelper.requirePlayer(
                              (CommandSourceStack)ctx.getSource(), "commands.neoessentials.near.player_only"
                           );
                           if (player == null) {
                              return 0;
                           } else {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.near"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                 return showNearbyPlayers(player, radius);
                              }
                           }
                        }
                     )
               )
         );
      }
   }

   private static int showNearbyPlayers(ServerPlayer player, int radius) {
      Vec3 playerPos = player.position();
      if (player.getServer() == null) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.near.server_error"));
         return 0;
      } else {
         List<NearCommand.NearbyPlayerInfo> nearbyPlayers = player.getServer()
            .getPlayerList()
            .getPlayers()
            .stream()
            .filter(p -> !p.equals(player))
            .filter(p -> p.level() == player.level())
            .filter(p -> !isVanished(p) || canSeeVanished(player))
            .map(p -> new NearCommand.NearbyPlayerInfo(p, playerPos))
            .filter(info -> info.distance <= (double)radius)
            .sorted(Comparator.comparingDouble(info -> info.distance))
            .toList();
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.near.header", nearbyPlayers.size(), radius));
         if (nearbyPlayers.isEmpty()) {
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.near.no_players"));
            return 1;
         } else {
            for (NearCommand.NearbyPlayerInfo info : nearbyPlayers) {
               MutableComponent message = createPlayerEntry(info, player);
               player.sendSystemMessage(message);
            }

            if (nearbyPlayers.size() > 1) {
               NearCommand.NearbyPlayerInfo closest = nearbyPlayers.getFirst();
               NearCommand.NearbyPlayerInfo farthest = nearbyPlayers.getLast();
               player.sendSystemMessage(
                  MessageUtil.info(
                     "commands.neoessentials.near.stats",
                     closest.player.getName().getString(),
                     String.format("%.1f", closest.distance),
                     farthest.player.getName().getString(),
                     String.format("%.1f", farthest.distance)
                  )
               );
            }

            return 1;
         }
      }
   }

   private static MutableComponent createPlayerEntry(NearCommand.NearbyPlayerInfo info, ServerPlayer viewer) {
      String distanceStr = CommandUtil.formatDistance(info.distance, 1);
      String direction = CommandUtil.getSimpleDirection(info.relativePos.x, info.relativePos.z);
      MutableComponent message = Component.literal(String.format("§7- §f%s §7(§e%sm §7%s)", info.player.getName().getString(), distanceStr, direction));
      List<String> statusList = new ArrayList<>();
      if (isAfk(info.player)) {
         statusList.add("§eAFK");
      }

      if (isVanished(info.player)) {
         statusList.add("§7Vanished");
      }

      if (info.player.hasPermissions(4)) {
         statusList.add("§cOP");
      }

      if (!statusList.isEmpty()) {
         message.append(Component.literal(" §7[" + String.join("§7,", statusList) + "§7]"));
      }

      MutableComponent hoverText = Component.literal("")
         .append(Component.literal("§6Player: §f" + info.player.getName().getString() + "\n"))
         .append(Component.literal("§6Distance: §f" + distanceStr + " blocks\n"))
         .append(Component.literal("§6Direction: §f" + direction + "\n"))
         .append(Component.literal("§6World: §f" + info.player.level().dimension().location() + "\n"))
         .append(Component.literal("§6Coordinates: §f" + (int)info.player.getX() + ", " + (int)info.player.getY() + ", " + (int)info.player.getZ() + "\n"))
         .append(
            Component.literal("§6Health: §f" + String.format("%.1f", info.player.getHealth()) + "/" + String.format("%.1f", info.player.getMaxHealth()) + "\n")
         );
      if (isAfk(info.player)) {
         hoverText.append(Component.literal("§eCurrently AFK\n"));
      }

      hoverText.append(Component.literal("\n§7Click to teleport to this player"));
      PermissionValidator.PermissionResult tpResult = PermissionValidator.validatePermission(viewer.createCommandSourceStack(), "neoessentials.teleport.tp");
      if (tpResult.hasPermission()) {
         message = message.withStyle(
            style -> style.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, hoverText))
                  .withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/tp " + info.player.getName().getString()))
         );
      } else {
         message = message.withStyle(style -> style.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, hoverText)));
      }

      return message;
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

   private static boolean canSeeVanished(ServerPlayer viewer) {
      if (!ConfigManager.getInstance().isVanishSystemEnabled()) {
         return false;
      } else {
         try {
            VanishManager vanishManager = VanishManager.getInstance();
            return vanishManager.canPlayerSeeVanished(viewer.getUUID());
         } catch (Exception var2) {
            return PermissionValidator.validatePermission(viewer.createCommandSourceStack(), "neoessentials.vanish.see").hasPermission();
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

   private static class NearbyPlayerInfo {
      final ServerPlayer player;
      final double distance;
      final Vec3 relativePos;

      NearbyPlayerInfo(ServerPlayer player, Vec3 viewerPos) {
         this.player = player;
         Vec3 playerPos = player.position();
         this.relativePos = playerPos.subtract(viewerPos);
         this.distance = viewerPos.distanceTo(playerPos);
      }
   }
}
