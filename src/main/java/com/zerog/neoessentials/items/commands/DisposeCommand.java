package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisposeCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(DisposeCommand.class);
   private static final Map<UUID, SimpleContainer> pendingDisposals = new HashMap<>();

   public static void restorePendingItems(ServerPlayer player) {
      SimpleContainer container = pendingDisposals.remove(player.getUUID());
      if (container != null) {
         int itemCount = 0;

         for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
               player.getInventory().placeItemBackInInventory(container.getItem(i));
               itemCount++;
            }
         }

         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.dispose.restored"));
         LOGGER.debug("Restored {} pending disposal items for player {} (disconnect/cancel)", itemCount, player.getName().getString());
      }
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("dispose")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dispose")
                        .requires(cs -> cs.getEntity() instanceof ServerPlayer))
                     .then(
                        Commands.literal("confirm")
                           .executes(
                              ctx -> {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.item.dispose"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    ServerPlayer player = permResult.getPlayer();
                                    SimpleContainer container = pendingDisposals.remove(player.getUUID());
                                    if (container != null) {
                                       int itemCount = 0;

                                       for (int i = 0; i < container.getContainerSize(); i++) {
                                          if (!container.getItem(i).isEmpty()) {
                                             itemCount++;
                                          }
                                       }

                                       LOGGER.info("Player {} confirmed disposal of {} items", player.getName().getString(), itemCount);
                                       ((CommandSourceStack)ctx.getSource())
                                          .sendSuccess(() -> MessageUtil.success("commands.neoessentials.dispose.confirmed"), false);
                                    } else {
                                       ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.dispose.nothing_pending"));
                                    }

                                    return 1;
                                 }
                              }
                           )
                     ))
                  .then(
                     Commands.literal("cancel")
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.item.dispose"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 ServerPlayer player = permResult.getPlayer();
                                 SimpleContainer container = pendingDisposals.remove(player.getUUID());
                                 if (container != null) {
                                    int itemCount = 0;

                                    for (int i = 0; i < container.getContainerSize(); i++) {
                                       if (!container.getItem(i).isEmpty()) {
                                          player.getInventory().placeItemBackInInventory(container.getItem(i));
                                          itemCount++;
                                       }
                                    }

                                    LOGGER.debug("Player {} cancelled disposal and restored {} items", player.getName().getString(), itemCount);
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> MessageUtil.success("commands.neoessentials.dispose.restored"), false);
                                 } else {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.dispose.nothing_pending"));
                                 }

                                 return 1;
                              }
                           }
                        )
                  ))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.item.dispose"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        disposeItem(permResult.getPlayer());
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.dispose.opened"), false);
                        return 1;
                     }
                  }
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("trash").requires(cs -> cs.getEntity() instanceof ServerPlayer))
               .executes(
                  ctx -> {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.item.dispose"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        disposeItem(permResult.getPlayer());
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.dispose.opened"), false);
                        return 1;
                     }
                  }
               )
         );
      }
   }

   public static void disposeItem(ServerPlayer player) {
      final SimpleContainer container = new SimpleContainer(27);
      pendingDisposals.put(player.getUUID(), container);
      MenuProvider provider = new MenuProvider() {
         public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player playerEntity) {
            return ChestMenu.threeRows(windowId, playerInv, container);
         }

         public Component getDisplayName() {
            return MessageUtil.component("commands.neoessentials.dispose.title");
         }
      };
      player.openMenu(provider);
   }
}
