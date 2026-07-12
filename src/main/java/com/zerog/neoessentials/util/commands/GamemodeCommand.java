package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GamemodeCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(GamemodeCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("gamemode")) {
         registerGamemodeShortcut(dispatcher, "gms", GameType.SURVIVAL, "survival");
         registerGamemodeShortcut(dispatcher, "gmc", GameType.CREATIVE, "creative");
         registerGamemodeShortcut(dispatcher, "gmsp", GameType.SPECTATOR, "spectator");
         registerGamemodeShortcut(dispatcher, "gma", GameType.ADVENTURE, "adventure");
      }
   }

   private static void registerGamemodeShortcut(CommandDispatcher<CommandSourceStack> dispatcher, String commandName, GameType gameType, String gameTypeName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.argument("player", EntityArgument.player())
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.gamemode.others"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                              return setGamemode((CommandSourceStack)ctx.getSource(), target, gameType, gameTypeName);
                           }
                        }
                     )
               ))
            .executes(
               ctx -> {
                  if (((CommandSourceStack)ctx.getSource()).getEntity() instanceof ServerPlayer player) {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.gamemode"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        return setGamemode((CommandSourceStack)ctx.getSource(), player, gameType, gameTypeName);
                     }
                  } else {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.gamemode.player_only"));
                     return 0;
                  }
               }
            )
      );
   }

   private static int setGamemode(CommandSourceStack source, ServerPlayer target, GameType gameType, String gameTypeName) {
      if (target.gameMode.getGameModeForPlayer() == gameType) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.gamemode.already_in_mode", target.getName().getString(), gameTypeName));
         return 0;
      } else {
         target.setGameMode(gameType);
         String sourceName = source.getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer.getName().getString() : "Console";
         LOGGER.info("Player {} changed {}'s gamemode to {}", new Object[]{sourceName, target.getName().getString(), gameTypeName});
         if (source.getEntity() instanceof ServerPlayer sourcePlayerx && sourcePlayerx.getUUID().equals(target.getUUID())) {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.gamemode.changed_self", gameTypeName), true);
            return 1;
         }

         String changerName = source.getEntity() != null ? source.getEntity().getName().getString() : "Console";
         target.sendSystemMessage(MessageUtil.success("commands.neoessentials.gamemode.changed_by_other", changerName, gameTypeName));
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.gamemode.changed_other", target.getName().getString(), gameTypeName), true);
         return 1;
      }
   }
}
