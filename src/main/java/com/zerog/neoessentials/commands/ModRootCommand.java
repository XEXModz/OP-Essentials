package com.zerog.neoessentials.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.config.ConfigSplitter;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.teleportation.HomeManager;
import com.zerog.neoessentials.teleportation.Spawn.SpawnManager;
import com.zerog.neoessentials.teleportation.Warp.WarpManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModRootCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(ModRootCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LOGGER.info("Registering /neoe and /neoessentials root commands");
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("neoe")
                        .requires(source -> {
                           boolean result = hasBaseCommandPermission(source);
                           LOGGER.debug("/neoe permission check for {}: {}", source.getTextName(), result);
                           return result;
                        }))
                     .then(((LiteralArgumentBuilder)Commands.literal("reload").requires(source -> {
                        boolean result = hasAdminPermission(source);
                        LOGGER.debug("/neoe reload admin permission for {}: {}", source.getTextName(), result);
                        return result;
                     })).executes(ModRootCommand::reloadConfiguration)))
                  .then(((LiteralArgumentBuilder)Commands.literal("config").requires(source -> {
                     boolean result = hasAdminPermission(source);
                     LOGGER.debug("/neoe config admin permission for {}: {}", source.getTextName(), result);
                     return result;
                  })).then(Commands.literal("split").executes(ModRootCommand::splitConfiguration))))
               .then(
                  Commands.argument("command", StringArgumentType.greedyString())
                     .suggests(ModRootCommand::suggestModCommands)
                     .executes(ModRootCommand::dispatchToModCommand)
               ))
            .executes(ModRootCommand::showAvailableCommands)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "neoessentials"
                        )
                        .requires(source -> {
                           boolean result = hasBaseCommandPermission(source);
                           LOGGER.debug("/neoessentials permission check for {}: {}", source.getTextName(), result);
                           return result;
                        }))
                     .then(((LiteralArgumentBuilder)Commands.literal("reload").requires(source -> {
                        boolean result = hasAdminPermission(source);
                        LOGGER.debug("/neoessentials reload admin permission for {}: {}", source.getTextName(), result);
                        return result;
                     })).executes(ModRootCommand::reloadConfiguration)))
                  .then(((LiteralArgumentBuilder)Commands.literal("config").requires(source -> {
                     boolean result = hasAdminPermission(source);
                     LOGGER.debug("/neoessentials config admin permission for {}: {}", source.getTextName(), result);
                     return result;
                  })).then(Commands.literal("split").executes(ModRootCommand::splitConfiguration))))
               .then(
                  Commands.argument("command", StringArgumentType.greedyString())
                     .suggests(ModRootCommand::suggestModCommands)
                     .executes(ModRootCommand::dispatchToModCommand)
               ))
            .executes(ModRootCommand::showAvailableCommands)
      );
   }

   private static boolean hasBaseCommandPermission(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.use") : true;
   }

   private static boolean hasAdminPermission(CommandSourceStack source) {
      return source.getEntity() instanceof ServerPlayer player ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.admin.reload") : true;
   }

   private static CompletableFuture<Suggestions> suggestModCommands(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      CommandRegistry registry = CommandRegistry.getInstance();
      List<String> commandNames = registry.getAllCommandNames().stream().sorted().collect(Collectors.toList());
      return SharedSuggestionProvider.suggest(commandNames, builder);
   }

   private static int reloadConfiguration(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         source.sendSuccess(() -> MessageUtil.info("Reloading NeoEssentials configuration..."), false);
         int successCount = 0;
         int totalCount = 0;
         totalCount++;

         try {
            ConfigManager.loadAll();
            LOGGER.info("✓ Configuration files reloaded");
            successCount++;
         } catch (Exception var18) {
            LOGGER.error("✗ Failed to reload configuration files: {}", var18.getMessage(), var18);
            source.sendFailure(MessageUtil.error("Failed to reload configuration: " + var18.getMessage()));
         }

         totalCount++;

         try {
            MessageUtil.reloadTranslations();
            LOGGER.info("✓ Translations reloaded");
            successCount++;
         } catch (Exception var17) {
            LOGGER.error("✗ Failed to reload translations: {}", var17.getMessage(), var17);
            source.sendFailure(MessageUtil.warning("Failed to reload translations: " + var17.getMessage()));
         }

         totalCount++;

         try {
            PermissionAPI.reload();
            LOGGER.info("✓ Permission system reloaded");
            successCount++;
         } catch (Exception var16) {
            LOGGER.error("✗ Failed to reload permissions: {}", var16.getMessage(), var16);
            source.sendFailure(MessageUtil.warning("Failed to reload permissions: " + var16.getMessage()));
         }

         totalCount++;

         try {
            KitManager.getInstance().reload();
            LOGGER.info("✓ Kit system reloaded");
            successCount++;
         } catch (Exception var15) {
            LOGGER.error("✗ Failed to reload kit system: {}", var15.getMessage(), var15);
            source.sendFailure(MessageUtil.warning("Failed to reload kits: " + var15.getMessage()));
         }

         totalCount++;

         try {
            HomeManager.getInstance().reload();
            LOGGER.info("✓ Home system reloaded");
            successCount++;
         } catch (Exception var14) {
            LOGGER.error("✗ Failed to reload home system: {}", var14.getMessage(), var14);
            source.sendFailure(MessageUtil.warning("Failed to reload homes: " + var14.getMessage()));
         }

         totalCount++;

         try {
            WarpManager.getInstance().reload();
            LOGGER.info("✓ Warp system reloaded");
            successCount++;
         } catch (Exception var13) {
            LOGGER.error("✗ Failed to reload warp system: {}", var13.getMessage(), var13);
            source.sendFailure(MessageUtil.warning("Failed to reload warps: " + var13.getMessage()));
         }

         totalCount++;

         try {
            SpawnManager.getInstance().reload();
            LOGGER.info("✓ Spawn system reloaded");
            successCount++;
         } catch (Exception var12) {
            LOGGER.error("✗ Failed to reload spawn system: {}", var12.getMessage(), var12);
            source.sendFailure(MessageUtil.warning("Failed to reload spawn: " + var12.getMessage()));
         }

         totalCount++;

         try {
            ConfigManager configManager = ConfigManager.getInstance();
            JsonObject config = configManager.getConfig("config.json");
            JsonObject chatObj = config.has("chat") ? config.getAsJsonObject("chat") : new JsonObject();
            JsonObject commandsObj = config.has("commands") ? config.getAsJsonObject("commands") : new JsonObject();
            ChatManager chatManager = new ChatManager(chatObj, commandsObj);
            ChatAPI.setChatManager(chatManager);
            LOGGER.info("✓ Chat system reloaded");
            successCount++;
         } catch (Exception var11) {
            LOGGER.error("✗ Failed to reload chat system: {}", var11.getMessage(), var11);
            source.sendFailure(MessageUtil.warning("Failed to reload chat configuration: " + var11.getMessage()));
         }

         totalCount++;

         try {
            AfkManager.getInstance().reload();
            LOGGER.info("✓ AFK system reloaded");
            successCount++;
         } catch (Exception var10) {
            LOGGER.error("✗ Failed to reload AFK system: {}", var10.getMessage(), var10);
            source.sendFailure(MessageUtil.warning("Failed to reload AFK system: " + var10.getMessage()));
         }

         totalCount++;

         try {
            JailManager.getInstance().reload();
            LOGGER.info("✓ Jail system reloaded");
            successCount++;
         } catch (Exception var9) {
            LOGGER.error("✗ Failed to reload jail system: {}", var9.getMessage(), var9);
            source.sendFailure(MessageUtil.warning("Failed to reload jail system: " + var9.getMessage()));
         }

         totalCount++;

         try {
            // BUGFIX 1.1.9: the tablist was the ONLY system neoe reload skipped —
            // tablist.json edits silently never applied without a full restart.
            com.zerog.neoessentials.tablist.TablistManager.getInstance().loadConfig();
            com.zerog.neoessentials.tablist.TablistManager.getInstance().updateAll(source.getServer());
            LOGGER.info("\u2713 Tablist reloaded");
            successCount++;
         } catch (Exception var8x) {
            LOGGER.error("\u2717 Failed to reload tablist: {}", var8x.getMessage(), var8x);
            source.sendFailure(MessageUtil.warning("Failed to reload tablist: " + var8x.getMessage()));
         }

         String resultMessage = String.format("NeoEssentials reload complete: %d/%d systems reloaded successfully", successCount, totalCount);
         if (successCount == totalCount) {
            source.sendSuccess(() -> MessageUtil.success(resultMessage), true);
         } else {
            source.sendSuccess(() -> MessageUtil.warning(resultMessage + " (check console for errors)"), true);
         }

         LOGGER.info("Configuration reload completed: {}/{} systems reloaded successfully by {}", new Object[]{successCount, totalCount, source.getTextName()});
         return 1;
      } catch (Exception var19) {
         LOGGER.error("CRITICAL: Failed to reload configuration: {}", var19.getMessage(), var19);
         source.sendFailure(MessageUtil.error("Failed to reload configuration: " + var19.getMessage()));
         return 0;
      }
   }

   private static int splitConfiguration(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();

      try {
         if (ConfigSplitter.isSplittingEnabled()) {
            source.sendSuccess(() -> MessageUtil.warning("Split configs are already enabled!"), false);
            source.sendSuccess(() -> MessageUtil.info("Config files are already split into smaller files."), false);
            return 0;
         } else {
            source.sendSuccess(() -> MessageUtil.info("§6" + "─".repeat(40)), false);
            source.sendSuccess(() -> MessageUtil.info("§eMigrating to split configuration files..."), false);
            source.sendSuccess(() -> MessageUtil.info("§6" + "─".repeat(40)), false);
            boolean success = ConfigSplitter.migrateToSplitConfigs();
            if (success) {
               source.sendSuccess(() -> MessageUtil.success("✓ Successfully migrated to split configs!"), false);
               source.sendSuccess(() -> MessageUtil.info("§aYour config.json has been split into smaller files:"), false);
               source.sendSuccess(() -> MessageUtil.info("  - main.json (modules, logging, permissions)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - commands.json (command enable/disable)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - chat.json (chat system settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - teleportation.json (teleport settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - moderation.json (ban, jail, freeze, etc.)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - webdashboard.json (web interface settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - items.json (item spawn settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - afk.json (AFK system settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("  - security.json (security settings)"), false);
               source.sendSuccess(() -> MessageUtil.info("§eOriginal config backed up to: config.json.backup"), false);
               source.sendSuccess(() -> MessageUtil.info("§aReload configs with: /neoessentials reload"), false);
               LOGGER.info("Configuration split completed successfully by {}", source.getTextName());
               return 1;
            } else {
               source.sendFailure(MessageUtil.error("Failed to split configuration. Check console for details."));
               return 0;
            }
         }
      } catch (Exception var3) {
         LOGGER.error("Failed to split configuration: {}", var3.getMessage(), var3);
         source.sendFailure(MessageUtil.error("An error occurred while splitting configs: " + var3.getMessage()));
         return 0;
      }
   }

   private static int dispatchToModCommand(CommandContext<CommandSourceStack> ctx) {
      String commandString = StringArgumentType.getString(ctx, "command");
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      String commandName = commandString.split("\\s+")[0];
      CommandRegistry registry = CommandRegistry.getInstance();
      CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
      if (!registry.isCommandRegistered(commandName)) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.root.unknown_command", commandName));
         source.sendFailure(MessageUtil.info("commands.neoessentials.root.help_hint"));
         return 0;
      } else if (!registry.isCommandActuallyRegistered(commandName, dispatcher)) {
         LOGGER.warn("Command '{}' is in registry but not in dispatcher - possible registration issue", commandName);
         source.sendFailure(MessageUtil.error("commands.neoessentials.root.unknown_command", commandName));
         source.sendFailure(MessageUtil.info("commands.neoessentials.root.help_hint"));
         return 0;
      } else {
         try {
            ParseResults<CommandSourceStack> parseResults = dispatcher.parse(commandString, source);
            if (parseResults.getReader().canRead()) {
               LOGGER.warn("Command '{}' has unconsumed arguments: '{}'", commandString, parseResults.getReader().getRemaining());
            }

            int result = dispatcher.execute(parseResults);
            LOGGER.debug("Successfully executed command '{}' with result: {}", commandString, result);
            return result;
         } catch (CommandSyntaxException var8) {
            LOGGER.warn("Command syntax error for '{}': {}", commandString, var8.getMessage());
            source.sendFailure(MessageUtil.error("commands.neoessentials.root.syntax_error", commandString, var8.getMessage()));
            return 0;
         } catch (Exception var9) {
            LOGGER.error("Failed to execute command '{}': {}", new Object[]{commandString, var9.getMessage(), var9});
            source.sendFailure(MessageUtil.error("commands.neoessentials.root.execution_failed", commandString));
            return 0;
         }
      }
   }

   private static int showAvailableCommands(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = (CommandSourceStack)ctx.getSource();
      CommandRegistry registry = CommandRegistry.getInstance();
      List<CommandRegistry.CommandInfo> commands = registry.getAllCommandsSorted();
      if (commands.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.warning("commands.neoessentials.root.no_commands"), false);
         return 1;
      } else {
         boolean isConsole = !(source.getEntity() instanceof ServerPlayer);
         String headerKey = isConsole ? "commands.neoessentials.root.help_header_console" : "commands.neoessentials.root.help_header";
         source.sendSuccess(() -> MessageUtil.info(headerKey), false);
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.root.help_count", commands.size()), false);
         List<CommandRegistry.CommandInfo> availableCommands = commands;
         if (!isConsole) {
            ServerPlayer player = (ServerPlayer)source.getEntity();
            availableCommands = commands.stream().filter(infox -> hasCommandPermission(player, infox.getName())).toList();
         }

         if (availableCommands.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.warning("commands.neoessentials.root.no_permission_commands"), false);
            return 1;
         } else {
            for (CommandRegistry.CommandInfo info : availableCommands) {
               if (info.hasAliases()) {
                  String aliases = String.join(", /", info.getAliases());
                  source.sendSuccess(
                     () -> MessageUtil.component("commands.neoessentials.root.command_with_aliases", info.getName(), aliases, info.getDescription()), false
                  );
               } else {
                  source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.root.command_simple", info.getName(), info.getDescription()), false);
               }
            }

            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.root.help_footer"), false);
            return 1;
         }
      }
   }

   private static boolean hasCommandPermission(ServerPlayer player, String commandName) {
      if (commandName.equals("balance")
         || commandName.equals("pay")
         || commandName.equals("paytoggle")
         || commandName.equals("eco")
         || commandName.equals("baltop")) {
         return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.economy." + commandName);
      } else if (commandName.equals("msg")
         || commandName.equals("reply")
         || commandName.equals("socialspy")
         || commandName.equals("ignore")
         || commandName.equals("unignore")
         || commandName.equals("mute")
         || commandName.equals("unmute")
         || commandName.equals("mutelist")) {
         return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.chat." + commandName);
      } else if (commandName.equals("repair")
         || commandName.equals("dispose")
         || commandName.equals("powertool")
         || commandName.equals("enchant")
         || commandName.equals("clearinventory")) {
         return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item." + commandName);
      } else if (commandName.equals("pex") || commandName.equals("permissions")) {
         return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.admin.permissions");
      } else {
         return commandName.equals("afk")
            ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.afk")
            : PermissionAPI.hasPermission(player.getUUID(), "neoessentials.use");
      }
   }
}
