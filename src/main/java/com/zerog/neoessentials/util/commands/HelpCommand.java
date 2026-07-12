package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.commands.CommandRegistry;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelpCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(HelpCommand.class);
   private static final int CMDS_PER_PAGE = 10;
   private static final String PERMISSION = "neoessentials.help";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("help")) {
         try {
            Field childrenField = CommandNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            Map<String, ?> children = (Map<String, ?>)childrenField.get(dispatcher.getRoot());
            children.remove("help");
            children.remove("?");
         } catch (Exception var3) {
            LOGGER.warn("Could not remove vanilla /help node: {}", var3.getMessage());
         }

         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("help").requires(src -> {
                     ServerPlayer p = src.getPlayer();
                     return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.help");
                  })).executes(ctx -> executeHelp(ctx, null, 1)))
                  .then(
                     Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeHelp(ctx, null, IntegerArgumentType.getInteger(ctx, "page")))
                  ))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("command", StringArgumentType.word())
                        .executes(ctx -> executeHelp(ctx, StringArgumentType.getString(ctx, "command"), 1)))
                     .then(
                        Commands.argument("page", IntegerArgumentType.integer(1))
                           .executes(ctx -> executeHelp(ctx, StringArgumentType.getString(ctx, "command"), IntegerArgumentType.getInteger(ctx, "page")))
                     )
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("?").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.help");
               })).executes(ctx -> executeHelp(ctx, null, 1)))
               .then(
                  Commands.argument("page", IntegerArgumentType.integer(1))
                     .executes(ctx -> executeHelp(ctx, null, IntegerArgumentType.getInteger(ctx, "page")))
               )
         );
      }
   }

   private static int executeHelp(CommandContext<CommandSourceStack> ctx, String search, int page) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      UUID uuid = player != null ? player.getUUID() : null;
      CommandRegistry registry = CommandRegistry.getInstance();
      List<CommandRegistry.CommandInfo> allCommands = registry.getAllCommandsSorted();
      List<CommandRegistry.CommandInfo> accessible = allCommands.stream()
         .filter(
            cmdx -> {
               if (uuid == null) {
                  return true;
               } else {
                  String perm = "neoessentials." + cmdx.getName().toLowerCase();
                  return PermissionAPI.hasPermission(uuid, "neoessentials.admin")
                     ? true
                     : PermissionAPI.hasPermission(uuid, perm) || PermissionAPI.hasPermission(uuid, "neoessentials.*");
               }
            }
         )
         .sorted(Comparator.comparing(CommandRegistry.CommandInfo::getName))
         .collect(Collectors.toList());
      if (search != null && !search.isEmpty()) {
         String query = search.toLowerCase();
         Optional<CommandRegistry.CommandInfo> exact = accessible.stream().filter(c -> c.getName().equalsIgnoreCase(query)).findFirst();
         if (exact.isPresent()) {
            showCommandDetail(src, exact.get());
            return 1;
         }

         accessible = accessible.stream()
            .filter(c -> c.getName().toLowerCase().contains(query) || c.getDescription() != null && c.getDescription().toLowerCase().contains(query))
            .collect(Collectors.toList());
         if (accessible.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.help.not_found", search));
            return 0;
         }
      }

      int totalPages = Math.max(1, (int)Math.ceil((double)accessible.size() / 10.0));
      int p = Math.max(1, Math.min(page, totalPages));
      int start = (p - 1) * 10;
      int end = Math.min(start + 10, accessible.size());
      src.sendSuccess(() -> Component.literal("§6════ §eNeoEssentials Help §7(Page " + p + "/" + totalPages + ") §6════"), false);

      for (int i = start; i < end; i++) {
         CommandRegistry.CommandInfo cmd = accessible.get(i);
         String desc = cmd.getDescription() != null ? cmd.getDescription() : "No description";
         src.sendSuccess(() -> Component.literal("  §e/" + cmd.getName() + " §7- " + desc), false);
      }

      if (totalPages > 1) {
         src.sendSuccess(
            () -> Component.literal("§7Use §e/help " + (p < totalPages ? p + 1 : 1) + "§7 for the next page, or §e/help <command>§7 for details."), false
         );
      } else {
         src.sendSuccess(() -> Component.literal("§7Use §e/help <command>§7 for details on a specific command."), false);
      }

      return 1;
   }

   private static void showCommandDetail(CommandSourceStack src, CommandRegistry.CommandInfo cmd) {
      src.sendSuccess(() -> Component.literal("§6════ §e/" + cmd.getName() + " §6════"), false);
      String desc = cmd.getDescription() != null ? cmd.getDescription() : "No description available.";
      src.sendSuccess(() -> Component.literal("§7" + desc), false);
      src.sendSuccess(() -> Component.literal("§7Permission: §eneoessentials." + cmd.getName()), false);
      List<String> aliases = cmd.getAliases();
      if (aliases != null && !aliases.isEmpty()) {
         src.sendSuccess(() -> Component.literal("§7Aliases: §e" + String.join("§7, §e", aliases)), false);
      }
   }
}
