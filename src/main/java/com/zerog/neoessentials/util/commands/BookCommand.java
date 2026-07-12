package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(BookCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("book")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("book")
                        .executes(
                           ctx -> {
                              ServerPlayer player = CommandSourceHelper.requirePlayer(
                                 (CommandSourceStack)ctx.getSource(), "commands.neoessentials.book.player_only"
                              );
                              if (player == null) {
                                 return 0;
                              } else {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.book"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    return giveWritableBook(player);
                                 }
                              }
                           }
                        ))
                     .then(
                        Commands.literal("unlock")
                           .executes(
                              ctx -> {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.book.unlock"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    ServerPlayer player = permResult.getPlayer();
                                    return unlockBook(player);
                                 }
                              }
                           )
                     ))
                  .then(
                     Commands.literal("title")
                        .then(
                           Commands.argument("title", StringArgumentType.greedyString())
                              .executes(
                                 ctx -> {
                                    PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                       (CommandSourceStack)ctx.getSource(), "neoessentials.book.title"
                                    );
                                    if (!permResult.hasPermission()) {
                                       ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                       return 0;
                                    } else {
                                       ServerPlayer player = permResult.getPlayer();
                                       String title = StringArgumentType.getString(ctx, "title");
                                       return setBookTitle(player, title);
                                    }
                                 }
                              )
                        )
                  ))
               .then(
                  Commands.literal("author")
                     .then(
                        Commands.argument("author", StringArgumentType.greedyString())
                           .executes(
                              ctx -> {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.book.author"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    ServerPlayer player = permResult.getPlayer();
                                    String author = StringArgumentType.getString(ctx, "author");
                                    return setBookAuthor(player, author);
                                 }
                              }
                           )
                     )
               )
         );
      }
   }

   private static int giveWritableBook(ServerPlayer player) {
      ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
      if (!player.getInventory().add(book)) {
         player.drop(book, false);
         player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.book.inventory_full"));
      } else {
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.book.given"));
      }

      return 1;
   }

   private static int unlockBook(ServerPlayer player) {
      ItemStack heldItem = player.getMainHandItem();
      if (heldItem.getItem() != Items.WRITTEN_BOOK) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.not_written_book"));
         return 0;
      } else {
         WrittenBookContent writtenContent = (WrittenBookContent)heldItem.get(DataComponents.WRITTEN_BOOK_CONTENT);
         if (writtenContent == null) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.invalid_book"));
            return 0;
         } else {
            ItemStack writableBook = new ItemStack(Items.WRITABLE_BOOK);
            List<Filterable<String>> pages = new ArrayList<>();

            for (Filterable<Component> page : writtenContent.pages()) {
               String pageText = ((Component)page.raw()).getString();
               pages.add(Filterable.passThrough(pageText));
            }

            WritableBookContent writableContent = new WritableBookContent(pages);
            writableBook.set(DataComponents.WRITABLE_BOOK_CONTENT, writableContent);
            player.setItemInHand(InteractionHand.MAIN_HAND, writableBook);
            LOGGER.info("Player {} unlocked written book with {} pages", player.getName().getString(), pages.size());
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.book.unlocked"));
            return 1;
         }
      }
   }

   private static int setBookTitle(ServerPlayer player, String title) {
      ItemStack heldItem = player.getMainHandItem();
      if (heldItem.getItem() != Items.WRITABLE_BOOK) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.not_writable_book"));
         return 0;
      } else if (title.length() > 32) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.title_too_long"));
         return 0;
      } else {
         CustomData customData = (CustomData)heldItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
         CompoundTag tag = customData.copyTag();
         tag.putString("PresetTitle", title);
         heldItem.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
         LOGGER.info("Player {} set book title to: {}", player.getName().getString(), title);
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.book.title_set", title));
         return 1;
      }
   }

   private static int setBookAuthor(ServerPlayer player, String author) {
      ItemStack heldItem = player.getMainHandItem();
      if (heldItem.getItem() != Items.WRITABLE_BOOK) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.not_writable_book"));
         return 0;
      } else if (author.length() > 16) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.book.author_too_long"));
         return 0;
      } else {
         CustomData customData = (CustomData)heldItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
         CompoundTag tag = customData.copyTag();
         tag.putString("PresetAuthor", author);
         heldItem.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
         LOGGER.info("Player {} set book author to: {}", player.getName().getString(), author);
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.book.author_set", author));
         return 1;
      }
   }
}
