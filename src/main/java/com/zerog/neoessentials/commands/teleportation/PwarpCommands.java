package com.zerog.neoessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.Warp.WarpManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class PwarpCommands {
   private static final String PERMISSION_PWARP = "neoessentials.teleport.pwarp";
   private static final String PERMISSION_SETPWARP = "neoessentials.teleport.pwarp.create";
   private static final String PERMISSION_DELPWARP = "neoessentials.teleport.pwarp.delete";
   private static final String PERMISSION_PWARPS = "neoessentials.teleport.pwarp.list";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      WarpManager warpManager = WarpManager.getInstance();
      if (warpManager.isPlayerWarpsEnabled()) {
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pwarp").requires(source -> {
            if (source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.pwarp")) {
               return true;
            }

            return false;
         })).then(Commands.argument("name", StringArgumentType.word()).executes(PwarpCommands::executePwarp)));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("setpwarp").requires(source -> {
            if (source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.pwarp.create")) {
               return true;
            }

            return false;
         })).then(Commands.argument("name", StringArgumentType.word()).executes(PwarpCommands::executeSetPwarp)));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("delpwarp").requires(source -> {
            if (source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.pwarp.delete")) {
               return true;
            }

            return false;
         })).then(Commands.argument("name", StringArgumentType.word()).executes(PwarpCommands::executeDelPwarp)));
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pwarps").requires(source -> {
            if (source.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.pwarp.list")) {
               return true;
            }

            return false;
         })).executes(PwarpCommands::executePwarps));
      }
   }

   private static int executePwarp(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      String warpName = StringArgumentType.getString(context, "name");
      WarpManager warpManager = WarpManager.getInstance();
      ConfigManager config = ConfigManager.getInstance();
      JailManager jailManager = JailManager.getInstance();
      if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.jail.prevent_escape"));
         return 0;
      } else {
         TeleportLocation location = warpManager.getPlayerWarp(player, warpName);
         if (location == null) {
            ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
            return 0;
         } else {
            warpManager.teleportToWarp(player, warpName);
            return 1;
         }
      }
   }

   private static int executeSetPwarp(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      String warpName = StringArgumentType.getString(context, "name");
      WarpManager warpManager = WarpManager.getInstance();
      TeleportLocation location = new TeleportLocation(player);
      return warpManager.createPlayerWarp(player, warpName, location) ? 1 : 0;
   }

   private static int executeDelPwarp(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      String warpName = StringArgumentType.getString(context, "name");
      WarpManager warpManager = WarpManager.getInstance();
      return warpManager.deletePlayerWarp(player, warpName) ? 1 : 0;
   }

   private static int executePwarps(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      WarpManager warpManager = WarpManager.getInstance();
      List<String> names = warpManager.getPlayerWarpNames(player);
      if (names.isEmpty()) {
         player.sendSystemMessage(MessageUtil.component(MessageUtil.localize("commands.neoessentials.teleport.warp.playerwarps_list_empty")));
      } else {
         StringBuilder builder = new StringBuilder();
         builder.append(MessageUtil.localize("commands.neoessentials.teleport.warp.playerwarps_list_header", names.size(), warpManager.getMaxPlayerWarps()));
         names.stream().sorted().forEach(name -> builder.append("\n").append(name));
         player.sendSystemMessage(MessageUtil.component(builder.toString()));
      }

      return 1;
   }
}
