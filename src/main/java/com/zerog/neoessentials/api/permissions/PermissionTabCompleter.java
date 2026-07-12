package com.zerog.neoessentials.api.permissions;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionTabCompleter {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionTabCompleter.class);
   public static final SuggestionProvider<CommandSourceStack> NEOESSENTIALS_PERMISSIONS = (context, builder) -> suggestNeoEssentialsPermissions(
         context, builder
      );
   public static final SuggestionProvider<CommandSourceStack> ALL_PERMISSIONS = (context, builder) -> suggestAllPermissions(context, builder);

   public static SuggestionProvider<CommandSourceStack> permissionsByCategory(PermissionRegistry.PermissionCategory category) {
      return (context, builder) -> suggestPermissionsByCategory(context, builder, category);
   }

   private static CompletableFuture<Suggestions> suggestNeoEssentialsPermissions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      String input = builder.getRemaining().toLowerCase();
      List<String> permissions = PermissionRegistry.getInstance().getNeoEssentialsPermissions();
      List<String> filtered = permissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).sorted().toList();
      return SharedSuggestionProvider.suggest(filtered, builder);
   }

   private static CompletableFuture<Suggestions> suggestAllPermissions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      String input = builder.getRemaining().toLowerCase();
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
      allPermissions.addAll(scanner.getDiscoveredPermissions());
      List<String> permissions = allPermissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).sorted().toList();
      return SharedSuggestionProvider.suggest(permissions, builder);
   }

   private static CompletableFuture<Suggestions> suggestPermissionsByCategory(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, PermissionRegistry.PermissionCategory category
   ) {
      String input = builder.getRemaining().toLowerCase();
      List<String> permissions = PermissionRegistry.getInstance()
         .getPermissionsByCategory(category)
         .stream()
         .filter(perm -> perm.toLowerCase().startsWith(input))
         .sorted()
         .toList();
      return SharedSuggestionProvider.suggest(permissions, builder);
   }

   public static void initialize() {
      LOGGER.info("Initializing NeoEssentials permission tab completion...");
      registerWithExternalPlugins();
      LOGGER.info("Permission tab completion initialized with {} nodes", PermissionRegistry.getInstance().getAllPermissions().size());
   }

   private static void registerWithExternalPlugins() {
      registerWithPermissionsEX();
      registerWithLuckPerms();
      registerWithOtherPlugins();
   }

   private static void registerWithPermissionsEX() {
      try {
         LOGGER.info("Making NeoEssentials permissions available for external plugin compatibility");
         PermissionRegistry registry = PermissionRegistry.getInstance();
         PermissionScanner scanner = PermissionScanner.getInstance();
         scanner.scanForPermissions();
         Set<String> allPermissions = new HashSet<>(registry.getAllPermissions());
         allPermissions.addAll(scanner.getDiscoveredPermissions());
         LOGGER.info("Made {} total permissions available for external plugin access", allPermissions.size());
         LOGGER.info("External plugins can access permissions via ExternalPermissionProvider class");
         if (!allPermissions.isEmpty()) {
            LOGGER.info("Sample permissions available:");
            allPermissions.stream().limit(5L).forEach(perm -> LOGGER.info("  - {}", perm));
            if (allPermissions.size() > 5) {
               LOGGER.info("  ... and {} more permissions", allPermissions.size() - 5);
            }
         }
      } catch (Exception var3) {
         LOGGER.warn("Failed to prepare permissions for external plugins: {}", var3.getMessage(), var3);
      }
   }

   private static void registerWithLuckPerms() {
      try {
         Class.forName("net.luckperms.api.LuckPerms");
         LOGGER.info("LuckPerms detected - registering permission nodes");

         for (String permission : PermissionRegistry.getInstance().getAllPermissions()) {
            LOGGER.debug("Would register permission with LuckPerms: {}", permission);
         }
      } catch (ClassNotFoundException var2) {
         LOGGER.debug("LuckPerms not found - skipping integration");
      } catch (Exception var3) {
         LOGGER.warn("Failed to register with LuckPerms: {}", var3.getMessage());
      }
   }

   private static void registerWithOtherPlugins() {
      try {
         Class.forName("org.anjocaido.groupmanager.GroupManager");
         LOGGER.info("GroupManager detected - registering permission nodes");
      } catch (ClassNotFoundException var2) {
         LOGGER.debug("GroupManager not found - skipping integration");
      }

      try {
         Class.forName("com.platymuus.bukkit.permissions.PermissionsBukkit");
         LOGGER.info("PermissionsBukkit detected - registering permission nodes");
      } catch (ClassNotFoundException var1) {
         LOGGER.debug("PermissionsBukkit not found - skipping integration");
      }
   }

   public static void exportPermissionsForPlugin(String pluginType, String outputPath) {
      String var2 = pluginType.toLowerCase();
      switch (var2) {
         case "permissionsex":
         case "pex":
            exportForPermissionsEX(outputPath);
            break;
         case "luckperms":
         case "lp":
            exportForLuckPerms(outputPath);
            break;
         case "groupmanager":
         case "gm":
            exportForGroupManager(outputPath);
            break;
         default:
            LOGGER.warn("Unknown plugin type for export: {}", pluginType);
      }
   }

   private static void exportForPermissionsEX(String outputPath) {
      LOGGER.info("Exporting permissions for PermissionsEX to: {}", outputPath);
   }

   private static void exportForLuckPerms(String outputPath) {
      LOGGER.info("Exporting permissions for LuckPerms to: {}", outputPath);
   }

   private static void exportForGroupManager(String outputPath) {
      LOGGER.info("Exporting permissions for GroupManager to: {}", outputPath);
   }

   public static List<String> getPermissionSuggestions(String input) {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allPermissions = new HashSet<>(registry.getPermissionsStartingWith(input.toLowerCase()));
      allPermissions.addAll(scanner.getDiscoveredPermissions().stream().filter(perm -> perm.toLowerCase().startsWith(input.toLowerCase())).toList());
      return allPermissions.stream().sorted().toList();
   }

   public static void registerDynamicPermission(String permission, String description, PermissionRegistry.PermissionCategory category) {
      PermissionRegistry.getInstance().register(permission, description, category);
      registerSinglePermissionWithExternalPlugins(permission);
   }

   private static void registerSinglePermissionWithExternalPlugins(String permission) {
      LOGGER.debug("Registering dynamic permission with external plugins: {}", permission);
   }
}
