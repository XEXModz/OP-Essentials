package com.zerog.neoessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.economy.managers.PayToggleManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayToggleCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(PayToggleCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("paytoggle")
               .requires(
                  src -> src.hasPermission(2)
                        || src.getPlayer() != null && PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.economy.paytoggle")
               ))
            .executes(ctx -> execute(ctx))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pt")
               .requires(
                  src -> src.hasPermission(2)
                        || src.getPlayer() != null && PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.economy.paytoggle")
               ))
            .executes(ctx -> execute(ctx))
      );
   }

   private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         UUID uuid = player.getUUID();
         LOGGER.debug("PayToggle command executed by player: {}", player.getName().getString());
         boolean current = PayToggleManager.getInstance().getPayToggle(uuid);
         boolean newState = !current;
         PayToggleManager.getInstance().setPayToggle(uuid, newState);
         LOGGER.debug("PayToggle state changed from {} to {} for player {}", new Object[]{current, newState, player.getName().getString()});
         if (newState) {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.paytoggle.enabled"), false);
         } else {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.paytoggle.disabled"), false);
         }

         return 1;
      } catch (Exception var5) {
         LOGGER.error("Error executing paytoggle command", var5);
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.error"));
         return 0;
      }
   }
}
