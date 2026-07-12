package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.level.ServerPlayer;

public class HelpopCommand {
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("helpop")) {
         registerHelpopCommand(dispatcher, "helpop");
         registerHelpopCommand(dispatcher, "adminhelp");
         registerHelpopCommand(dispatcher, "request");
      }
   }

   private static void registerHelpopCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.argument("message", StringArgumentType.greedyString())
                     .executes(
                        ctx -> {
                           ServerPlayer player = CommandSourceHelper.requirePlayer(
                              (CommandSourceStack)ctx.getSource(), "commands.neoessentials.helpop.player_only"
                           );
                           if (player == null) {
                              return 0;
                           } else {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.helpop"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 String message = StringArgumentType.getString(ctx, "message");
                                 return sendHelpRequest(player, message);
                              }
                           }
                        }
                     )
               ))
            .executes(ctx -> {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.helpop.usage"));
               return 0;
            })
      );
   }

   private static int sendHelpRequest(ServerPlayer sender, String message) {
      String playerName = sender.getName().getString();
      BlockPos pos = sender.blockPosition();
      String worldName = CommandUtil.getWorldName(sender.level());
      String timeStamp = LocalDateTime.now().format(TIME_FORMAT);
      String location = String.format("%s (%d, %d, %d)", worldName, pos.getX(), pos.getY(), pos.getZ());
      List<ServerPlayer> onlinePlayers = sender.getServer().getPlayerList().getPlayers();
      int staffCount = 0;

      for (ServerPlayer staff : onlinePlayers) {
         PermissionValidator.PermissionResult staffPermResult = PermissionValidator.validatePermission(
            staff.createCommandSourceStack(), "neoessentials.helpop.receive"
         );
         if (staffPermResult.hasPermission()) {
            staffCount++;
            sendHelpopToStaff(staff, sender, playerName, message, location, timeStamp);
         }
      }

      if (staffCount > 0) {
         sender.sendSystemMessage(MessageUtil.success("commands.neoessentials.helpop.sent", staffCount));
         sender.getServer().sendSystemMessage(MessageUtil.info("commands.neoessentials.helpop.log", playerName, location, message));
      } else {
         sender.sendSystemMessage(MessageUtil.warning("commands.neoessentials.helpop.no_staff"));
      }

      return 1;
   }

   private static void sendHelpopToStaff(ServerPlayer staff, ServerPlayer sender, String playerName, String message, String location, String timeStamp) {
      Component header = MessageUtil.warning("commands.neoessentials.helpop.staff.header", timeStamp, playerName);
      PermissionValidator.PermissionResult tpPermResult = PermissionValidator.validatePermission(
         staff.createCommandSourceStack(), "neoessentials.teleport.admin.tp"
      );
      Component locationComponent;
      if (tpPermResult.hasPermission()) {
         BlockPos pos = sender.blockPosition();
         String tpCommand = String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
         locationComponent = Component.literal("§e" + location)
            .withStyle(
               style -> style.withClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, tpCommand))
                     .withHoverEvent(
                        new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to teleport to " + playerName))
                     )
            );
      } else {
         locationComponent = Component.literal("§e" + location);
      }

      Component messageComponent = Component.literal("§f" + message);
      String replyCommand = "/msg " + playerName + " ";
      Component replyComponent = Component.literal("§a[Reply]")
         .withStyle(
            style -> style.withClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, replyCommand))
                  .withHoverEvent(
                     new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to reply to " + playerName))
                  )
         );
      staff.sendSystemMessage(MessageUtil.component("commands.neoessentials.helpop.header"));
      staff.sendSystemMessage(header);
      staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.neoessentials.helpop.location")).append(locationComponent));
      staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.neoessentials.helpop.message")).append(messageComponent));
      staff.sendSystemMessage(Component.literal(MessageUtil.localize("commands.neoessentials.helpop.actions")).append(replyComponent));
      staff.sendSystemMessage(MessageUtil.component("commands.neoessentials.helpop.footer"));
   }
}
