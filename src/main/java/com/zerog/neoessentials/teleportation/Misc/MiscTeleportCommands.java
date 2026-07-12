package com.zerog.neoessentials.teleportation.Misc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.commands.ItemCustomisationCommands;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiscTeleportCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(MiscTeleportCommands.class);
   private static final String PERMISSION_BACK = "neoessentials.teleport.back";
   private static final String PERMISSION_TPAUTO = "neoessentials.tpauto";
   private static final String PERMISSION_TPAUTO_OTHERS = "neoessentials.tpauto.others";
   private static final Map<UUID, Boolean> tpAutoState = new ConcurrentHashMap<>();

   public static boolean isTpAutoEnabled(UUID uuid) {
      return tpAutoState.getOrDefault(uuid, false);
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled() && config.isCommandEnabled("back")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("back")
                  .requires(
                     source -> source.getEntity() instanceof ServerPlayer player
                           ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.back")
                           : false
                  ))
               .executes(context -> executeBack(context))
         );
         LOGGER.info("Registered misc teleport commands: /back");
      }

      if (config.isCommandEnabled("tpauto")) {
         registerTpAuto(dispatcher);
         LOGGER.info("Registered misc teleport commands: /tpauto");
      }
   }

   private static void registerTpAuto(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpauto")
                        .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tpauto")))
                     .executes(ctx -> executeTpAuto(ctx, null, null)))
                  .then(Commands.literal("on").executes(ctx -> executeTpAuto(ctx, null, true))))
               .then(Commands.literal("off").executes(ctx -> executeTpAuto(ctx, null, false))))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tpauto.others")))
                        .executes(ctx -> executeTpAuto(ctx, StringArgumentType.getString(ctx, "target"), null)))
                     .then(Commands.literal("on").executes(ctx -> executeTpAuto(ctx, StringArgumentType.getString(ctx, "target"), true))))
                  .then(Commands.literal("off").executes(ctx -> executeTpAuto(ctx, StringArgumentType.getString(ctx, "target"), false)))
            )
      );
   }

   private static int executeTpAuto(CommandContext<CommandSourceStack> ctx, String targetName, Boolean enable) {
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
         boolean cur = tpAutoState.getOrDefault(target.getUUID(), false);
         boolean newState = enable != null ? enable : !cur;
         tpAutoState.put(target.getUUID(), newState);
         String label = newState ? "§aenabled" : "§cdisabled";
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
         if (isOther) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tpauto.other", target.getName().getString(), label), false);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.tpauto.self", label));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tpauto.self", label), false);
         }

         if (newState && !ItemCustomisationCommands.isTpToggleAllowed(target.getUUID())) {
            target.sendSystemMessage(MessageUtil.warning("commands.neoessentials.tpauto.toggle_warning"));
         }

         return 1;
      }
   }

   private static int executeBack(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         MiscTeleportManager manager = MiscTeleportManager.getInstance();
         boolean success = manager.teleportBack(player);
         return success ? 1 : 0;
      } catch (CommandSyntaxException var4) {
         LOGGER.error("Command syntax error in /back", var4);
         return 0;
      } catch (Exception var5) {
         LOGGER.error("Error executing /back command", var5);
         return 0;
      }
   }
}
