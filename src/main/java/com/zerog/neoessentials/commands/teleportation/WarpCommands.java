package com.zerog.neoessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.Warp.WarpManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class WarpCommands {
   private static final String PERMISSION_WARP = "neoessentials.teleport.warp";
   private static final String PERMISSION_WARP_LIST = "neoessentials.teleport.warp.list";
   private static final String PERMISSION_WARP_OTHERS = "neoessentials.teleport.warp.others";
   private static final String PERMISSION_SETWARP = "neoessentials.teleport.warp.create";
   private static final String PERMISSION_DELWARP = "neoessentials.teleport.warp.delete";
   private static final String PERMISSION_WARPINFO = "neoessentials.warpinfo";
   private static final int WARPS_PER_PAGE = 20;
   private static final SuggestionProvider<CommandSourceStack> WARP_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         WarpManager.getInstance().getWarpNames(), builder
      );
   private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         ((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), builder
      );

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled()) {
         if (config.isCommandEnabled("warp")) {
            registerWarpCommand(dispatcher);
         }

         if (config.isCommandEnabled("setwarp")) {
            registerSetWarpCommand(dispatcher);
         }

         if (config.isCommandEnabled("delwarp")) {
            registerDelWarpCommand(dispatcher);
         }

         if (config.isCommandEnabled("listwarps")) {
            registerWarpsCommand(dispatcher);
         }
      }
   }

   private static void registerWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("warp").requires(src -> hasAnyWarpPerm(src)))
               .executes(ctx -> executeWarpList((CommandSourceStack)ctx.getSource(), 1)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word())
                     .suggests(WARP_SUGGESTIONS)
                     .executes(ctx -> executeWarp(ctx, StringArgumentType.getString(ctx, "name"), null)))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests(PLAYER_SUGGESTIONS)
                           .requires(
                              src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.teleport.warp.others")
                           ))
                        .executes(ctx -> executeWarp(ctx, StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "target")))
                  )
            )
      );
   }

   private static boolean hasAnyWarpPerm(CommandSourceStack src) {
      if (src.getPlayer() == null) {
         return src.hasPermission(2);
      } else {
         UUID id = src.getPlayer().getUUID();
         return PermissionAPI.hasPermission(id, "neoessentials.teleport.warp") || PermissionAPI.hasPermission(id, "neoessentials.teleport.warp.list");
      }
   }

   private static int executeWarpList(CommandSourceStack source, int page) {
      WarpManager wm = WarpManager.getInstance();
      ServerPlayer player = source.getPlayer();
      List<String> allWarps = new ArrayList<>(wm.getWarpNames());
      Collections.sort(allWarps, String.CASE_INSENSITIVE_ORDER);
      boolean perWarpPerms = ConfigManager.getInstance().isPerWarpPermissionEnabled();
      List<String> available = new ArrayList<>();

      for (String name : allWarps) {
         if (!perWarpPerms
            || player == null
            || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.warps." + name)
            || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.warp")) {
            available.add(name);
         }
      }

      if (available.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.teleport.warp.list_empty"), false);
         return 1;
      } else {
         int totalPages = (int)Math.ceil((double)available.size() / 20.0);
         int clampedPage = Math.max(1, Math.min(page, totalPages));
         int start = (clampedPage - 1) * 20;
         int end = Math.min(start + 20, available.size());
         String warpList = String.join("§7, §f", available.subList(start, end));
         if (available.size() > 20) {
            int tot = available.size();
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.teleport.warp.list_count", tot, clampedPage, totalPages), false);
         }

         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.teleport.warp.list", warpList), false);
         return 1;
      }
   }

   private static int executeWarp(CommandContext<CommandSourceStack> ctx, String warpName, String targetName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer sender = source.getPlayer();
      if (sender == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else if (ConfigManager.getInstance().isPreventJailEscapeEnabled() && JailManager.getInstance().isPlayerJailed(sender.getUUID())) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.jail.prevent_escape"));
         return 0;
      } else if (!PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.teleport.warp")) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.no_permission"));
         return 0;
      } else if (ConfigManager.getInstance().isPerWarpPermissionEnabled() && !PermissionAPI.hasPermission(sender.getUUID(), "neoessentials.warps." + warpName)) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.no_per_warp_permission", warpName));
         return 0;
      } else {
         WarpManager wm = WarpManager.getInstance();
         if (!wm.hasWarp(warpName)) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
            return 0;
         } else if (targetName != null) {
            ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
               return 0;
            } else {
               wm.teleportToWarp(target, warpName);
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.warp.warped_other", target.getName().getString(), warpName), true);
               return 1;
            }
         } else {
            wm.teleportToWarp(sender, warpName);
            return 1;
         }
      }
   }

   private static void registerSetWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (String alias : new String[]{"setwarp", "createwarp", "addwarp"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias)
                  .requires(
                     src -> src.getPlayer() == null
                           ? src.hasPermission(3)
                           : PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.teleport.warp.create")
                  ))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word()).executes(WarpCommands::executeSetWarpHere))
                     .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(WarpCommands::executeSetWarpAt))
               )
         );
      }
   }

   private static int executeSetWarpHere(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         String name = StringArgumentType.getString(ctx, "name");
         return WarpManager.getInstance().createWarp(player, name) ? 1 : 0;
      }
   }

   private static int executeSetWarpAt(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         String name = StringArgumentType.getString(ctx, "name");

         try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
            ServerLevel level = player.serverLevel();
            return WarpManager.getInstance().createWarp(player, name, level, pos) ? 1 : 0;
         } catch (Exception var5) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("teleport.warp.invalid_coordinates"));
            return 0;
         }
      }
   }

   private static void registerDelWarpCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (String alias : new String[]{"delwarp", "deletewarp", "removewarp", "rwarp"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias)
                  .requires(
                     src -> src.getPlayer() == null
                           ? src.hasPermission(3)
                           : PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.teleport.warp.delete")
                  ))
               .then(
                  Commands.argument("name", StringArgumentType.word())
                     .suggests(WARP_SUGGESTIONS)
                     .executes(ctx -> executeDelWarp(ctx, StringArgumentType.getString(ctx, "name")))
               )
         );
      }
   }

   private static int executeDelWarp(CommandContext<CommandSourceStack> ctx, String warpName) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = source.getPlayer();
      if (player == null) {
         if (WarpManager.getInstance().hasWarp(warpName)) {
            boolean removed = WarpManager.getInstance().deleteWarpByAdmin(warpName, source.getTextName());
            if (removed) {
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.teleport.warp.deleted", warpName), true);
            } else {
               source.sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
            }

            return removed ? 1 : 0;
         } else {
            source.sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", warpName));
            return 0;
         }
      } else if (!PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.warp.delete")) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.general.no_permission"));
         return 0;
      } else {
         return WarpManager.getInstance().deleteWarp(player, warpName) ? 1 : 0;
      }
   }

   private static void registerWarpsCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (String alias : new String[]{"warps", "warplist", "listwarps"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(alias)
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.teleport.warp.list")))
                  .executes(ctx -> executeWarpList((CommandSourceStack)ctx.getSource(), 1)))
               .then(
                  Commands.argument("page", IntegerArgumentType.integer(1))
                     .executes(ctx -> executeWarpList((CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))
               )
         );
      }
   }

   public static void registerWarpInfoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("warpinfo")
               .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.warpinfo")))
            .then(
               Commands.argument("warp", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(WarpManager.getInstance().getWarpNames(), b))
                  .executes(ctx -> {
                     CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                     String name = StringArgumentType.getString(ctx, "warp");
                     TeleportLocation loc = WarpManager.getInstance().getWarp(name);
                     if (loc == null) {
                        src.sendFailure(MessageUtil.error("commands.neoessentials.teleport.warp.not_found", name));
                        return 0;
                     } else {
                        src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.warpinfo.info", name, loc.getLocationString()), false);
                        return 1;
                     }
                  })
            )
      );
   }
}
