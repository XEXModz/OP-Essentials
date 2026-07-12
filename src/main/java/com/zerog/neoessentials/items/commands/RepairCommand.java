package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepairCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(RepairCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("repair")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("repair").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.item.repair"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        repairItem(permResult.getPlayer());
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.repair.success"), false);
                        return 1;
                     }
                  }
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fix").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.item.repair"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        repairItem(permResult.getPlayer());
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.repair.success"), false);
                        return 1;
                     }
                  }
               )
         );
      }
   }

   public static void repairItem(ServerPlayer player) {
      ItemStack stack = player.getMainHandItem();
      if (!stack.isEmpty() && stack.isDamageableItem()) {
         String itemName = stack.getDisplayName().getString();
         int damageBefore = stack.getDamageValue();
         stack.setDamageValue(0);
         LOGGER.info("Player {} repaired item: {} (damage: {} -> 0)", new Object[]{player.getName().getString(), itemName, damageBefore});
      }
   }
}
