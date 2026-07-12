package com.zerog.neoessentials.api.permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionBridge {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionBridge.class);

   private static MutableComponent styled(String text, ChatFormatting color) {
      return Component.literal(text).withStyle(color);
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                "neoessentials-permissions"
                                             )
                                             .requires(source -> source.hasPermission(4)))
                                          .executes(ctx -> {
                                             listAllPermissions((CommandSourceStack)ctx.getSource());
                                             return 1;
                                          }))
                                       .then(
                                          Commands.literal("export")
                                             .then(Commands.argument("format", StringArgumentType.word()).suggests(getFormatSuggestions()).executes(ctx -> {
                                                String format = StringArgumentType.getString(ctx, "format");
                                                exportPermissions((CommandSourceStack)ctx.getSource(), format);
                                                return 1;
                                             }))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)Commands.literal("search").requires(source -> source.hasPermission(4)))
                                          .then(
                                             Commands.argument("query", StringArgumentType.greedyString())
                                                .suggests(PermissionTabCompleter.NEOESSENTIALS_PERMISSIONS)
                                                .executes(ctx -> {
                                                   String query = StringArgumentType.getString(ctx, "query");
                                                   searchPermissions((CommandSourceStack)ctx.getSource(), query);
                                                   return 1;
                                                })
                                          )
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("category").requires(source -> source.hasPermission(4)))
                                       .then(Commands.argument("category", StringArgumentType.word()).suggests(getCategorySuggestions()).executes(ctx -> {
                                          String categoryName = StringArgumentType.getString(ctx, "category");
                                          showCategoryPermissions((CommandSourceStack)ctx.getSource(), categoryName);
                                          return 1;
                                       }))
                                 ))
                              .then(((LiteralArgumentBuilder)Commands.literal("refresh").requires(source -> source.hasPermission(4))).executes(ctx -> {
                                 refreshPermissions((CommandSourceStack)ctx.getSource());
                                 return 1;
                              })))
                           .then(((LiteralArgumentBuilder)Commands.literal("scan").requires(source -> source.hasPermission(4))).executes(ctx -> {
                              scanPermissions((CommandSourceStack)ctx.getSource());
                              return 1;
                           })))
                        .then(((LiteralArgumentBuilder)Commands.literal("discovered").requires(source -> source.hasPermission(4))).executes(ctx -> {
                           showDiscoveredPermissions((CommandSourceStack)ctx.getSource());
                           return 1;
                        })))
                     .then(((LiteralArgumentBuilder)Commands.literal("pex-help").requires(source -> source.hasPermission(4))).executes(ctx -> {
                        showPermissionsEXHelp((CommandSourceStack)ctx.getSource());
                        return 1;
                     })))
                  .then(((LiteralArgumentBuilder)Commands.literal("list-all").requires(source -> source.hasPermission(4))).executes(ctx -> {
                     listAllPermissionsForTabCompletion((CommandSourceStack)ctx.getSource());
                     return 1;
                  })))
               .then(((LiteralArgumentBuilder)Commands.literal("group-examples").requires(source -> source.hasPermission(4))).executes(ctx -> {
                  showGroupExamples((CommandSourceStack)ctx.getSource());
                  return 1;
               })))
            .then(((LiteralArgumentBuilder)Commands.literal("user-examples").requires(source -> source.hasPermission(4))).executes(ctx -> {
               showUserExamples((CommandSourceStack)ctx.getSource());
               return 1;
            }))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("neoe-perms").requires(source -> source.hasPermission(4))).executes(ctx -> {
            listAllPermissions((CommandSourceStack)ctx.getSource());
            return 1;
         })
      );
   }

   private static SuggestionProvider<CommandSourceStack> getFormatSuggestions() {
      return (ctx, builder) -> SharedSuggestionProvider.suggest(List.of("yaml", "json", "txt", "pex", "luckperms"), builder);
   }

   private static SuggestionProvider<CommandSourceStack> getCategorySuggestions() {
      return (ctx, builder) -> {
         List<String> categories = List.of();

         for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
            categories = new ArrayList<>(categories);
            categories.add(category.getKey());
         }

         return SharedSuggestionProvider.suggest(categories, builder);
      };
   }

   private static void listAllPermissions(CommandSourceStack source) {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.list_header"), false);
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.list_total", registry.getAllPermissions().size()), false);
      source.sendSuccess(() -> Component.literal(""), false);

      for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
         Set<String> categoryPerms = registry.getPermissionsByCategory(category);
         if (!categoryPerms.isEmpty()) {
            source.sendSuccess(
               () -> MessageUtil.warning("commands.neoessentials.permissions.category_header", category.getDescription(), categoryPerms.size()), false
            );
            categoryPerms.stream().sorted().limit(5L).forEach(perm -> {
               PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(perm);
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.permission_entry", perm, info.getDescription()), false);
            });
            if (categoryPerms.size() > 5) {
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.more_permissions", categoryPerms.size() - 5), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);
         }
      }

      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.export_help"), false);
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.search_help"), false);
   }

   private static void exportPermissions(CommandSourceStack source, String format) {
      try {
         String filename = "neoessentials-permissions." + format.toLowerCase();
         File file = new File(filename);
         PermissionRegistry registry = PermissionRegistry.getInstance();
         String var5 = format.toLowerCase();
         switch (var5) {
            case "yaml":
            case "yml":
               exportAsYaml(file, registry);
               break;
            case "json":
               exportAsJson(file, registry);
               break;
            case "txt":
            case "text":
               exportAsText(file, registry);
               break;
            case "pex":
            case "permissionsex":
               exportAsPEX(file, registry);
               break;
            case "luckperms":
            case "lp":
               exportAsLuckPerms(file, registry);
               break;
            default:
               source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.unsupported_format", format));
               return;
         }

         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.export_success", file.getAbsolutePath()), false);
      } catch (IOException var7) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.export_failed", var7.getMessage()));
         LOGGER.error("Failed to export permissions", var7);
      }
   }

   private static void searchPermissions(CommandSourceStack source, String query) {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      List<String> matches = registry.getPermissionsStartingWith(query.toLowerCase());
      if (matches.isEmpty()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.no_matches", query));
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.search_results", query, matches.size()), false);
         matches.stream().limit(20L).forEach(perm -> {
            PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(perm);
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.search_entry", perm, info.getDescription()), false);
         });
         if (matches.size() > 20) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.more_matches", matches.size() - 20), false);
         }
      }
   }

   private static void refreshPermissions(CommandSourceStack source) {
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.refreshing"), false);

      try {
         PermissionRegistry registry = PermissionRegistry.getInstance();
         int beforeCount = registry.getAllPermissions().size();
         registry.refreshPermissions();
         int afterCount = registry.getAllPermissions().size();
         int newCount = afterCount - beforeCount;
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.refresh_completed"), false);
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.refresh_stats", beforeCount, afterCount, newCount), false);
         PermissionTabCompleter.initialize();
      } catch (Exception var5) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.refresh_error", var5.getMessage()));
         LOGGER.error("Error refreshing permissions", var5);
      }
   }

   private static void scanPermissions(CommandSourceStack source) {
      source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.scanning"), false);

      try {
         PermissionScanner scanner = PermissionScanner.getInstance();
         scanner.scanForPermissions();
         Set<String> discovered = scanner.getDiscoveredPermissions();
         Set<String> dynamicPrefixes = scanner.getDynamicPermissionPrefixes();
         Map<String, Set<String>> byCategory = scanner.getPermissionsByCategory();
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.scan_completed"), false);
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.scan_stats", discovered.size(), dynamicPrefixes.size()), false);
         source.sendSuccess(() -> Component.literal(""), false);

         for (Entry<String, Set<String>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            Set<String> perms = entry.getValue();
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.scan.category_summary", category.toUpperCase(), perms.size()), false);
         }

         if (!dynamicPrefixes.isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.scan.dynamic_prefixes_header"), false);
            dynamicPrefixes.stream()
               .sorted()
               .limit(10L)
               .forEach(prefix -> source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.scan.dynamic_prefix", prefix), false));
         }
      } catch (Exception var9) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.scan.error", var9.getMessage()));
         LOGGER.error("Error scanning permissions", var9);
      }
   }

   private static void showDiscoveredPermissions(CommandSourceStack source) {
      try {
         PermissionRegistry registry = PermissionRegistry.getInstance();
         Set<String> discovered = registry.getAutoDiscoveredPermissions();
         if (discovered.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.discovered.none_found"));
            return;
         }

         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.discovered.header", discovered.size()), false);
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.discovered.description"), false);
         source.sendSuccess(() -> Component.literal(""), false);
         Map<String, List<String>> byCategory = new HashMap<>();

         for (String perm : discovered) {
            String[] parts = perm.split("\\.");
            String category = parts.length >= 2 ? parts[1] : "unknown";
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(perm);
         }

         for (Entry<String, List<String>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<String> perms = entry.getValue();
            source.sendSuccess(
               () -> MessageUtil.info("commands.neoessentials.permissions.discovered.category_header", category.toUpperCase(), perms.size()), false
            );
            perms.stream()
               .sorted()
               .limit(8L)
               .forEach(perm -> source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.discovered.permission_entry", perm), false));
            if (perms.size() > 8) {
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.discovered.more_count", perms.size() - 8), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);
         }
      } catch (Exception var8) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.discovered.error", var8.getMessage()));
         LOGGER.error("Error getting discovered permissions", var8);
      }
   }

   private static void showCategoryPermissions(CommandSourceStack source, String categoryName) {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionRegistry.PermissionCategory foundCategory = null;

      for (PermissionRegistry.PermissionCategory cat : PermissionRegistry.PermissionCategory.values()) {
         if (cat.getKey().equalsIgnoreCase(categoryName)) {
            foundCategory = cat;
            break;
         }
      }

      if (foundCategory == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.permissions.category.unknown", categoryName));
      } else {
         PermissionRegistry.PermissionCategory category = foundCategory;
         Set<String> categoryPerms = registry.getPermissionsByCategory(category);
         source.sendSuccess(
            () -> MessageUtil.success("commands.neoessentials.permissions.category.header", category.getDescription(), categoryPerms.size()), false
         );
         source.sendSuccess(() -> Component.literal(""), false);
         categoryPerms.stream()
            .sorted()
            .forEach(
               perm -> {
                  PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(perm);
                  String defaultStr = info.getDefaultValue() ? ChatFormatting.GREEN + "✓" : ChatFormatting.RED + "✗";
                  source.sendSuccess(
                     () -> MessageUtil.info("commands.neoessentials.permissions.category.permission_details", perm, defaultStr, info.getDescription()), false
                  );
               }
            );
      }
   }

   private static void exportAsYaml(File file, PermissionRegistry registry) throws IOException {
      try (FileWriter writer = new FileWriter(file)) {
         writer.write("# NeoEssentials Permission Nodes\n");
         writer.write("# Generated automatically - " + new Date() + "\n");
         writer.write("# Total permissions: " + registry.getAllPermissions().size() + "\n\n");
         writer.write("permissions:\n");

         for (String permission : registry.getAllPermissions().stream().sorted().toList()) {
            PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(permission);
            writer.write("  \"" + permission + "\":\n");
            writer.write("    description: \"" + info.getDescription() + "\"\n");
            writer.write("    default: " + info.getDefaultValue() + "\n");
            writer.write("    category: \"" + info.getCategory().getKey() + "\"\n");
         }
      }
   }

   private static void exportAsJson(File file, PermissionRegistry registry) throws IOException {
      try (FileWriter writer = new FileWriter(file)) {
         writer.write("{\n");
         writer.write("  \"_metadata\": {\n");
         writer.write("    \"generated\": \"" + new Date() + "\",\n");
         writer.write("    \"total\": " + registry.getAllPermissions().size() + ",\n");
         writer.write("    \"mod\": \"NeoEssentials\"\n");
         writer.write("  },\n");
         writer.write("  \"permissions\": {\n");
         List<String> permissions = registry.getAllPermissions().stream().sorted().toList();

         for (int i = 0; i < permissions.size(); i++) {
            String permission = permissions.get(i);
            PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(permission);
            writer.write("    \"" + permission + "\": {\n");
            writer.write("      \"description\": \"" + info.getDescription() + "\",\n");
            writer.write("      \"default\": " + info.getDefaultValue() + ",\n");
            writer.write("      \"category\": \"" + info.getCategory().getKey() + "\"\n");
            writer.write("    }");
            if (i < permissions.size() - 1) {
               writer.write(",");
            }

            writer.write("\n");
         }

         writer.write("  }\n");
         writer.write("}\n");
      }
   }

   private static void exportAsText(File file, PermissionRegistry registry) throws IOException {
      try (FileWriter writer = new FileWriter(file)) {
         for (String line : registry.exportPermissions()) {
            writer.write(line + "\n");
         }
      }
   }

   private static void exportAsPEX(File file, PermissionRegistry registry) throws IOException {
      try (FileWriter writer = new FileWriter(file)) {
         writer.write("# PermissionsEX configuration for NeoEssentials\n");
         writer.write("# This file contains ALL NeoEssentials permissions for tab completion\n");
         writer.write("# Import this into your PermissionsEX configuration\n\n");
         PermissionScanner scanner = PermissionScanner.getInstance();
         scanner.scanForPermissions();
         Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
         allPermissions.addAll(scanner.getDiscoveredPermissions());
         writer.write("# ============================================\n");
         writer.write("# INDIVIDUAL PERMISSIONS FOR TAB COMPLETION\n");
         writer.write("# ============================================\n");
         writer.write("# Copy these to your permissions.yml or use them with /pex commands\n");
         writer.write("# Both USER and GROUP commands are supported:\n");
         writer.write("# - /pex group <group> add <permission>\n");
         writer.write("# - /pex user <user> add <permission>\n\n");

         for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
            List<String> categoryPerms = allPermissions.stream().filter(perm -> categorizePermission(perm) == category).sorted().toList();
            if (!categoryPerms.isEmpty()) {
               writer.write("# " + category.getDescription() + " Permissions (" + categoryPerms.size() + " total)\n");

               for (String permission : categoryPerms) {
                  PermissionRegistry.PermissionInfo info = registry.getPermissionInfo(permission);
                  String description = info != null ? info.getDescription() : "Auto-discovered permission";
                  writer.write(permission + " # " + description + "\n");
               }

               writer.write("\n");
            }
         }

         writer.write("# ===============================\n");
         writer.write("# WILDCARD PERMISSIONS\n");
         writer.write("# ===============================\n");
         writer.write("# Use these for broader permission grants\n");
         writer.write("neoessentials.* # All NeoEssentials permissions\n");

         for (PermissionRegistry.PermissionCategory categoryx : PermissionRegistry.PermissionCategory.values()) {
            writer.write("neoessentials." + categoryx.getKey() + ".* # All " + categoryx.getDescription() + " permissions\n");
         }

         writer.write("\n# ===============================\n");
         writer.write("# GROUP USAGE EXAMPLES\n");
         writer.write("# ===============================\n");
         writer.write("# Full admin access:\n");
         writer.write("# /pex group admin add neoessentials.*\n");
         writer.write("\n# Moderator permissions:\n");
         writer.write("# /pex group moderator add neoessentials.teleport.*\n");
         writer.write("# /pex group moderator add neoessentials.chat.*\n");
         writer.write("# /pex group moderator add neoessentials.admin.permissions\n");
         writer.write("\n# Basic player permissions:\n");
         writer.write("# /pex group player add neoessentials.teleport.home.home\n");
         writer.write("# /pex group player add neoessentials.teleport.home.set\n");
         writer.write("# /pex group player add neoessentials.teleport.home.delete\n");
         writer.write("# /pex group player add neoessentials.teleport.home.list\n");
         writer.write("# /pex group player add neoessentials.economy.balance\n");
         writer.write("# /pex group player add neoessentials.economy.pay\n");
         writer.write("# /pex group player add neoessentials.kits.starter\n");
         writer.write("# /pex group player add neoessentials.chat.msg\n");
         writer.write("# /pex group player add neoessentials.chat.reply\n");
         writer.write("\n# VIP player permissions:\n");
         writer.write("# /pex group vip add neoessentials.teleport.spawn.*\n");
         writer.write("# /pex group vip add neoessentials.teleport.warp.*\n");
         writer.write("# /pex group vip add neoessentials.kits.*\n");
         writer.write("# /pex group vip add neoessentials.utility.*\n");
         writer.write("\n# ===============================\n");
         writer.write("# USER USAGE EXAMPLES\n");
         writer.write("# ===============================\n");
         writer.write("# Grant specific permissions to individual users:\n");
         writer.write("# /pex user PlayerName add neoessentials.teleport.admin.tp\n");
         writer.write("# /pex user PlayerName add neoessentials.teleport.admin.tphere\n");
         writer.write("# /pex user PlayerName add neoessentials.economy.eco.give\n");
         writer.write("# /pex user PlayerName add neoessentials.admin.reload\n");
         writer.write("\n# ===============================\n");
         writer.write("# QUICK GROUP SETUP COMMANDS\n");
         writer.write("# ===============================\n");
         writer.write("# Copy and paste these command blocks:\n\n");
         writer.write("# Create Admin Group:\n");
         writer.write("# /pex group admin create\n");
         writer.write("# /pex group admin add neoessentials.*\n\n");
         writer.write("# Create Moderator Group:\n");
         writer.write("# /pex group moderator create\n");
         writer.write("# /pex group moderator add neoessentials.teleport.*\n");
         writer.write("# /pex group moderator add neoessentials.chat.*\n");
         writer.write("# /pex group moderator add neoessentials.admin.permissions\n\n");
         writer.write("# Create Player Group:\n");
         writer.write("# /pex group player create\n");
         writer.write("# /pex group player add neoessentials.teleport.home.*\n");
         writer.write("# /pex group player add neoessentials.teleport.spawn.spawn\n");
         writer.write("# /pex group player add neoessentials.economy.balance\n");
         writer.write("# /pex group player add neoessentials.economy.pay\n");
         writer.write("# /pex group player add neoessentials.kits.starter\n");
         writer.write("# /pex group player add neoessentials.chat.msg\n");
         writer.write("# /pex group player add neoessentials.chat.reply\n\n");
         writer.write("# Create VIP Group:\n");
         writer.write("# /pex group vip create\n");
         writer.write("# /pex group vip add neoessentials.teleport.*\n");
         writer.write("# /pex group vip add neoessentials.economy.*\n");
         writer.write("# /pex group vip add neoessentials.kits.*\n");
         writer.write("# /pex group vip add neoessentials.utility.*\n");
         writer.write("\n# ===============================\n");
         writer.write("# FOR PERMISSIONSEX TAB COMPLETION\n");
         writer.write("# ===============================\n");
         writer.write("# To enable tab completion for both user and group commands:\n");
         writer.write("# 1. Ensure permissions are registered in your PermissionsEX configuration\n");
         writer.write("# 2. Add these permissions to at least one group\n");
         writer.write("# 3. Use the commands above to pre-register permissions\n");
         writer.write("# 4. Tab completion should work for:\n");
         writer.write("#    - /pex group <tab> add neoessentials.<tab>\n");
         writer.write("#    - /pex user <tab> add neoessentials.<tab>\n");
         writer.write("# 5. If still not working, try /pex reload after adding permissions\n");
      }
   }

   private static PermissionRegistry.PermissionCategory categorizePermission(String permission) {
      String[] parts = permission.split("\\.");
      if (parts.length >= 2) {
         String category = parts[1].toLowerCase();
         switch (category) {
            case "economy":
            case "eco":
            case "balance":
               return PermissionRegistry.PermissionCategory.ECONOMY;
            case "teleport":
            case "tp":
            case "spawn":
            case "home":
            case "warp":
               return PermissionRegistry.PermissionCategory.TELEPORT;
            case "chat":
            case "msg":
            case "message":
            case "social":
               return PermissionRegistry.PermissionCategory.CHAT;
            case "kits":
            case "kit":
               return PermissionRegistry.PermissionCategory.KITS;
            case "moderation":
            case "mod":
            case "mute":
            case "ban":
            case "kick":
            case "freeze":
            case "jail":
            case "vanish":
               return PermissionRegistry.PermissionCategory.MODERATION;
            case "admin":
            case "administration":
               return PermissionRegistry.PermissionCategory.ADMIN;
            case "utility":
            case "utilities":
            case "util":
               return PermissionRegistry.PermissionCategory.MISC;
            default:
               return PermissionRegistry.PermissionCategory.MISC;
         }
      } else {
         return PermissionRegistry.PermissionCategory.MISC;
      }
   }

   private static void exportAsLuckPerms(File file, PermissionRegistry registry) throws IOException {
      try (FileWriter writer = new FileWriter(file)) {
         writer.write("# LuckPerms commands for NeoEssentials permissions\n");
         writer.write("# Run these commands in your server console\n\n");
         writer.write("# Create NeoEssentials permission groups\n");

         for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
            Set<String> categoryPerms = registry.getPermissionsByCategory(category);
            if (!categoryPerms.isEmpty()) {
               String groupName = "neoessentials_" + category.getKey();
               writer.write("/lp creategroup " + groupName + "\n");
               writer.write("/lp group " + groupName + " meta setdisplayname \"&6NeoEssentials " + category.getDescription() + "\"\n");

               for (String permission : categoryPerms.stream().sorted().toList()) {
                  writer.write("/lp group " + groupName + " permission set " + permission + " true\n");
               }

               writer.write("\n");
            }
         }
      }
   }

   public static List<String> getAllPermissions() {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
      allPermissions.addAll(scanner.getDiscoveredPermissions());
      return allPermissions.stream().sorted().toList();
   }

   private static void showPermissionsEXHelp(CommandSourceStack source) {
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.help.pex.title"), false);
      source.sendSuccess(() -> styled("The issue you're experiencing is that PermissionsEX only shows", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> styled("wildcard permissions (*.teleport.*) in tab completion, not", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> styled("individual permissions. Here's how to fix it:", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("1. Export permissions for PEX:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("   /neoessentials-permissions export pex", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("2. List all individual permissions:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("   /neoessentials-permissions list-all", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("3. Use the exported file or copy permissions manually", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("   Check: neoessentials-permissions.pex", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("4. PermissionsEX Group Commands:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("   /pex group admin add neoessentials.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   /pex group moderator add neoessentials.teleport.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   /pex group player add neoessentials.teleport.home.home", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   /pex group player add neoessentials.economy.balance", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("5. PermissionsEX User Commands:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("   /pex user [username] add neoessentials.teleport.admin.tp", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   /pex user [username] add neoessentials.kits.starter", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("6. Typical issue: PermissionsEX tab completion only shows", ChatFormatting.RED), false);
      source.sendSuccess(() -> styled("   permissions it knows about. Individual permissions need", ChatFormatting.RED), false);
      source.sendSuccess(() -> styled("   to be registered with the permission system first.", ChatFormatting.RED), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("7. Recommended Permission Groups:", ChatFormatting.AQUA), false);
      source.sendSuccess(() -> styled("   - Admin: neoessentials.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   - Moderator: neoessentials.teleport.*, neoessentials.chat.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("   - Player: neoessentials.teleport.home.*, neoessentials.economy.*", ChatFormatting.WHITE), false);
   }

   private static void listAllPermissionsForTabCompletion(CommandSourceStack source) {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
      allPermissions.addAll(scanner.getDiscoveredPermissions());
      source.sendSuccess(() -> styled("┌─ ALL NEOESSENTIALS PERMISSIONS ─┐", ChatFormatting.GOLD), false);
      source.sendSuccess(() -> styled("Total: " + allPermissions.size() + " permissions", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> styled("Copy these for PermissionsEX commands:", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> Component.literal(""), false);
      Map<PermissionRegistry.PermissionCategory, List<String>> grouped = new HashMap<>();

      for (String permission : allPermissions) {
         PermissionRegistry.PermissionCategory category = categorizePermission(permission);
         grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(permission);
      }

      for (PermissionRegistry.PermissionCategory category : PermissionRegistry.PermissionCategory.values()) {
         List<String> categoryPerms = grouped.get(category);
         if (categoryPerms != null && !categoryPerms.isEmpty()) {
            categoryPerms.sort(String::compareTo);
            source.sendSuccess(() -> styled(category.getDescription() + ":", ChatFormatting.GREEN), false);

            for (String permission : categoryPerms) {
               source.sendSuccess(() -> styled("  " + permission, ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);
         }
      }

      source.sendSuccess(() -> styled("=== WILDCARD PERMISSIONS ===", ChatFormatting.GOLD), false);
      source.sendSuccess(() -> styled("  neoessentials.*", ChatFormatting.GRAY), false);

      for (PermissionRegistry.PermissionCategory categoryx : PermissionRegistry.PermissionCategory.values()) {
         source.sendSuccess(() -> styled("  neoessentials." + category.getKey() + ".*", ChatFormatting.GRAY), false);
      }

      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("Use: ", ChatFormatting.YELLOW).append(styled("/pex group <group> add <permission>", ChatFormatting.WHITE)), false);
      source.sendSuccess(() -> styled("Export: ", ChatFormatting.YELLOW).append(styled("/neoessentials-permissions export pex", ChatFormatting.WHITE)), false);
   }

   private static void showGroupExamples(CommandSourceStack source) {
      source.sendSuccess(() -> styled("┌─ PermissionsEX Group Examples ─┐", ChatFormatting.GOLD), false);
      source.sendSuccess(() -> styled("Use these commands to set up permission groups:", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Admin Group (Full Access):", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex group admin create", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group admin add neoessentials.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Moderator Group:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex group moderator create", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group moderator add neoessentials.teleport.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group moderator add neoessentials.chat.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group moderator add neoessentials.admin.permissions", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Player Group (Basic):", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex group player create", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.teleport.home.home", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.teleport.home.set", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.teleport.spawn.spawn", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.economy.balance", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.economy.pay", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.chat.msg", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group player add neoessentials.chat.reply", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ VIP Group:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex group vip create", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group vip add neoessentials.teleport.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group vip add neoessentials.economy.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group vip add neoessentials.kits.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group vip add neoessentials.utility.*", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Tab completion should work for:", ChatFormatting.AQUA), false);
      source.sendSuccess(() -> styled("/pex group <groupname> add neoessentials.<TAB>", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex group <groupname> remove neoessentials.<TAB>", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ If tab completion doesn't work:", ChatFormatting.RED), false);
      source.sendSuccess(() -> styled("1. Run: /neoessentials-permissions export pex", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("2. Add at least one permission to any group", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("3. Run: /pex reload", ChatFormatting.WHITE), false);
   }

   private static void showUserExamples(CommandSourceStack source) {
      source.sendSuccess(() -> styled("┌─ PermissionsEX User Examples ─┐", ChatFormatting.GOLD), false);
      source.sendSuccess(() -> styled("Use these commands to grant permissions to specific users:", ChatFormatting.YELLOW), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Admin Permissions for Users:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.teleport.admin.tp", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.teleport.admin.tphere", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.teleport.admin.tpall", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.economy.eco.give", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.admin.reload", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Moderator Permissions for Users:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.teleport.admin.tpo", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.chat.socialspy", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.chat.mute", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Special Permissions for Users:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.teleport.home.others", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.economy.balance.others", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.kits.starter.nocooldown", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Utility Permissions for Users:", ChatFormatting.GREEN), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.utility.repair", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.utility.afk", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName add neoessentials.utility.dispose", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Tab completion should work for:", ChatFormatting.AQUA), false);
      source.sendSuccess(() -> styled("/pex user <username> add neoessentials.<TAB>", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user <username> remove neoessentials.<TAB>", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> Component.literal(""), false);
      source.sendSuccess(() -> styled("▶ Remove permissions from users:", ChatFormatting.RED), false);
      source.sendSuccess(() -> styled("/pex user PlayerName remove neoessentials.teleport.admin.tp", ChatFormatting.WHITE), false);
      source.sendSuccess(() -> styled("/pex user PlayerName remove neoessentials.*", ChatFormatting.WHITE), false);
   }

   public static void initialize() {
      LOGGER.info("Initializing NeoEssentials Permission Bridge...");

      try {
         PermissionTabCompleter.initialize();
      } catch (Exception var1) {
         LOGGER.error("Failed to initialize tab completer", var1);
      }

      LOGGER.info("Permission Bridge initialized with {} permissions available for tab completion", PermissionRegistry.getInstance().getAllPermissions().size());
   }
}
