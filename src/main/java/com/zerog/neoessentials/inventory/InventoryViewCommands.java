package com.zerog.neoessentials.inventory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryViewCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(InventoryViewCommands.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("invsee").requires(source -> hasPermission(source, "neoessentials.invsee")))
            .then(Commands.argument("target", EntityArgument.player()).executes(ctx -> viewInventory(ctx, false)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("invseeedit").requires(source -> hasPermission(source, "neoessentials.invsee.edit")))
            .then(Commands.argument("target", EntityArgument.player()).executes(ctx -> viewInventory(ctx, true)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("enderchest").requires(source -> hasPermission(source, "neoessentials.enderchest")))
            .then(Commands.argument("target", EntityArgument.player()).executes(ctx -> viewEnderChest(ctx, false)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("enderchestedit")
               .requires(source -> hasPermission(source, "neoessentials.enderchest.edit")))
            .then(Commands.argument("target", EntityArgument.player()).executes(ctx -> viewEnderChest(ctx, true)))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("inv").requires(source -> hasPermission(source, "neoessentials.invsee")))
            .redirect(dispatcher.getRoot().getChild("invsee"))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ec").requires(source -> hasPermission(source, "neoessentials.enderchest")))
            .redirect(dispatcher.getRoot().getChild("enderchest"))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ecedit").requires(source -> hasPermission(source, "neoessentials.enderchest.edit")))
            .redirect(dispatcher.getRoot().getChild("enderchestedit"))
      );
      LOGGER.info("Registered inventory view commands: /invsee, /invseeedit, /enderchest, /enderchest");
   }

   private static boolean hasPermission(CommandSourceStack source, String permission) {
      return source.getEntity() instanceof ServerPlayer player ? PermissionAPI.hasPermission(player.getUUID(), permission) : true;
   }

   private static int viewInventory(CommandContext<CommandSourceStack> ctx, boolean editable) throws CommandSyntaxException {
      ServerPlayer viewer = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
      if (viewer.getUUID().equals(target.getUUID())) {
         viewer.sendSystemMessage(MessageUtil.error("You cannot view your own inventory with this command!"));
         return 0;
      } else {
         if (editable) {
            openEditableInventory(viewer, target);
            viewer.sendSystemMessage(MessageUtil.success("Opening editable inventory of " + target.getName().getString()));
            LOGGER.info("{} is viewing and editing {}'s inventory", viewer.getName().getString(), target.getName().getString());
         } else {
            openReadOnlyInventory(viewer, target);
            viewer.sendSystemMessage(MessageUtil.success("Viewing inventory of " + target.getName().getString()));
            LOGGER.info("{} is viewing {}'s inventory (read-only)", viewer.getName().getString(), target.getName().getString());
         }

         return 1;
      }
   }

   private static int viewEnderChest(CommandContext<CommandSourceStack> ctx, boolean editable) throws CommandSyntaxException {
      ServerPlayer viewer = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
      if (editable) {
         viewer.openMenu(
            new SimpleMenuProvider(
               (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, target.getEnderChestInventory()),
               Component.literal(target.getName().getString() + "'s Ender Chest (Editable)")
            )
         );
         viewer.sendSystemMessage(MessageUtil.success("Opening editable ender chest of " + target.getName().getString()));
         LOGGER.info("{} is viewing and editing {}'s ender chest", viewer.getName().getString(), target.getName().getString());
      } else {
         openReadOnlyEnderChest(viewer, target);
         viewer.sendSystemMessage(MessageUtil.success("Viewing ender chest of " + target.getName().getString()));
         LOGGER.info("{} is viewing {}'s ender chest (read-only)", viewer.getName().getString(), target.getName().getString());
      }

      return 1;
   }

   private static void openReadOnlyInventory(ServerPlayer viewer, ServerPlayer target) {
      int invSize = target.getInventory().getContainerSize();
      if (invSize <= 27) {
         Container inventoryCopy = new SimpleContainer(invSize);

         for (int i = 0; i < invSize; i++) {
            inventoryCopy.setItem(i, target.getInventory().getItem(i).copy());
         }

         viewer.openMenu(
            new SimpleMenuProvider(
               (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, inventoryCopy),
               Component.literal(target.getName().getString() + "'s Inventory (Read-Only)")
            )
         );
      } else {
         SimpleContainer inventoryCopy = new SimpleContainer(54);

         for (int i = 0; i < invSize; i++) {
            inventoryCopy.setItem(i, target.getInventory().getItem(i).copy());
         }

         for (int i = invSize; i < 54; i++) {
            inventoryCopy.setItem(i, ItemStack.EMPTY.copy());
         }

         viewer.openMenu(
            new SimpleMenuProvider(
               (id, playerInventory, player) -> ChestMenu.sixRows(id, playerInventory, inventoryCopy),
               Component.literal(target.getName().getString() + "'s Inventory (Read-Only)")
            )
         );
      }
   }

   private static void openEditableInventory(ServerPlayer viewer, ServerPlayer target) {
      int invSize = target.getInventory().getContainerSize();
      viewer.openMenu(
         new SimpleMenuProvider(
            (id, playerInventory, player) -> new PlayerInventoryContainerMenu(id, playerInventory, target), PlayerInventoryContainerMenu.getTitle(target)
         )
      );
   }

   private static void openReadOnlyEnderChest(ServerPlayer viewer, ServerPlayer target) {
      Container enderChestCopy = new SimpleContainer(27);

      for (int i = 0; i < 27; i++) {
         enderChestCopy.setItem(i, target.getEnderChestInventory().getItem(i).copy());
      }

      viewer.openMenu(
         new SimpleMenuProvider(
            (id, playerInventory, player) -> ChestMenu.threeRows(id, playerInventory, enderChestCopy),
            Component.literal(target.getName().getString() + "'s Ender Chest (Read-Only)")
         )
      );
   }
}
