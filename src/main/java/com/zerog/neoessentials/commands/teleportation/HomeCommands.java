package com.zerog.neoessentials.commands.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.teleportation.HomeManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public class HomeCommands {
   private static final Map<UUID, String> pendingDeleteConfirmations = new ConcurrentHashMap<>();
   private static final Map<UUID, String> pendingSetHomeConfirmations = new ConcurrentHashMap<>();
   private static final String PERMISSION_HOME = "neoessentials.teleport.home";
   private static final String PERMISSION_SETHOME = "neoessentials.teleport.home.set";
   private static final String PERMISSION_DELHOME = "neoessentials.teleport.home.delete";
   private static final String PERMISSION_HOMES = "neoessentials.teleport.home.list";
   private static final String PERMISSION_RENAMEHOME = "neoessentials.renamehome";
   private static final String PERMISSION_RENAMEHOME_OTHERS = "neoessentials.renamehome.others";
   private static final SuggestionProvider<CommandSourceStack> HOME_SUGGESTIONS = (context, builder) -> {
      if (((CommandSourceStack)context.getSource()).getEntity() instanceof ServerPlayer player) {
         HomeManager homeManager = HomeManager.getInstance();
         List<String> homeNames = homeManager.getHomeNames(player);
         if (ConfigManager.isDebugModeEnabled()) {
            System.out.println("[DEBUG] Home suggestions for " + player.getName().getString() + ": " + homeNames);
         }

         return SharedSuggestionProvider.suggest(homeNames, builder);
      } else {
         return builder.buildFuture();
      }
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled()) {
         if (config.isCommandEnabled("home")) {
            registerHomeCommand(dispatcher);
         }

         if (config.isCommandEnabled("sethome")) {
            registerSetHomeCommand(dispatcher);
         }

         if (config.isCommandEnabled("delhome")) {
            registerDelHomeCommand(dispatcher);
         }

         if (config.isCommandEnabled("listhomes")) {
            registerHomesCommand(dispatcher);
         }

         if (config.isCommandEnabled("renamehome")) {
            registerRenameHomeCommand(dispatcher);
         }
      }
   }

   private static void registerHomeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerHomeCommandWithName(dispatcher, "home");
      registerHomeCommandWithName(dispatcher, "h");
   }

   private static void registerHomeCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires(source -> {
               if (source.getEntity() instanceof ServerPlayer player) {
                  boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.home");
                  return !hasPerm ? false : hasPerm;
               } else {
                  return false;
               }
            })).executes(HomeCommands::executeHomeDefault))
            .then(Commands.argument("name", StringArgumentType.word()).suggests(HOME_SUGGESTIONS).executes(HomeCommands::executeHome))
      );
   }

   private static void registerSetHomeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerSetHomeCommandWithName(dispatcher, "sethome");
      registerSetHomeCommandWithName(dispatcher, "createhome");
   }

   private static void registerSetHomeCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires(source -> {
               if (source.getEntity() instanceof ServerPlayer player) {
                  boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.home.set");
                  return !hasPerm ? false : hasPerm;
               } else {
                  return false;
               }
            }))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word()).executes(HomeCommands::executeSetHome))
                     .then(Commands.literal("confirm").executes(HomeCommands::executeSetHomeConfirm)))
                  .then(Commands.literal("deny").executes(HomeCommands::executeSetHomeDeny))
            )
      );
   }

   private static void registerDelHomeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerDelHomeCommandWithName(dispatcher, "delhome");
      registerDelHomeCommandWithName(dispatcher, "deletehome");
      registerDelHomeCommandWithName(dispatcher, "removehome");
      registerDelHomeCommandWithName(dispatcher, "rhome");
   }

   private static void registerDelHomeCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires(source -> {
               if (source.getEntity() instanceof ServerPlayer player) {
                  boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.home.delete");
                  return !hasPerm ? false : hasPerm;
               } else {
                  return false;
               }
            }))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("name", StringArgumentType.word())
                        .suggests(HOME_SUGGESTIONS)
                        .executes(HomeCommands::executeDelHome))
                     .then(Commands.literal("confirm").executes(HomeCommands::executeDelHomeConfirm)))
                  .then(Commands.literal("deny").executes(HomeCommands::executeDelHomeDeny))
            )
      );
   }

   private static void registerHomesCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerHomesCommandWithName(dispatcher, "homes");
      registerHomesCommandWithName(dispatcher, "listhomes");
      registerHomesCommandWithName(dispatcher, "homelist");
   }

   private static void registerHomesCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires(source -> {
         if (source.getEntity() instanceof ServerPlayer player) {
            boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.home.list");
            return !hasPerm ? false : hasPerm;
         } else {
            return false;
         }
      })).executes(HomeCommands::executeHomes));
   }

   private static int executeHomeDefault(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("This command can only be used by players."));
         return 0;
      } else {
         HomeManager homeManager = HomeManager.getInstance();
         ConfigManager config = ConfigManager.getInstance();
         JailManager jailManager = JailManager.getInstance();
         if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
            ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.jail.prevent_escape"));
            return 0;
         } else if (!homeManager.hasHomes(player)) {
            ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.teleport.home.none_set"));
            return 0;
         } else {
            homeManager.teleportToDefaultHome(player);
            return 1;
         }
      }
   }

   private static int executeHome(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("This command can only be used by players."));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         HomeManager homeManager = HomeManager.getInstance();
         ConfigManager config = ConfigManager.getInstance();
         JailManager jailManager = JailManager.getInstance();
         if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
            ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.jail.prevent_escape"));
            return 0;
         } else {
            homeManager.teleportToHome(player, homeName);
            return 1;
         }
      }
   }

   private static int executeSetHome(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         HomeManager homeManager = HomeManager.getInstance();
         int maxHomes = homeManager.getMaxHomesForPlayer(player);
         int currentHomes = homeManager.getHomeNames(player).size();
         if (homeManager.getHome(player, homeName) == null && currentHomes >= maxHomes) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.limit_exceeded", maxHomes));
            return 0;
         } else {
            if (homeManager.getHome(player, homeName) != null) {
               String pending = pendingSetHomeConfirmations.get(player.getUUID());
               if (pending == null || !pending.equals(homeName)) {
                  pendingSetHomeConfirmations.put(player.getUUID(), homeName);
                  player.sendSystemMessage(MessageUtil.homeConfirmComponent(homeName, "overwrite", "/sethome " + homeName, "/sethome " + homeName + " deny"));
                  return 1;
               }

               pendingSetHomeConfirmations.remove(player.getUUID());
            }

            pendingSetHomeConfirmations.remove(player.getUUID());
            if (homeManager.setHome(player, homeName)) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.set", homeName, player.blockPosition().toShortString()));
               return 1;
            } else {
               return 0;
            }
         }
      }
   }

   private static int executeSetHomeConfirm(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         HomeManager homeManager = HomeManager.getInstance();
         String pending = pendingSetHomeConfirmations.get(player.getUUID());
         if (pending != null && pending.equals(homeName)) {
            pendingSetHomeConfirmations.remove(player.getUUID());
            boolean success = homeManager.setHome(player, homeName);
            if (success) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.overwrite_success", homeName));
               return 1;
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.overwrite_failed", homeName));
               return 0;
            }
         } else {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.no_pending_overwrite", homeName));
            return 0;
         }
      }
   }

   private static int executeSetHomeDeny(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         String pending = pendingSetHomeConfirmations.get(player.getUUID());
         if (pending != null && pending.equals(homeName)) {
            pendingSetHomeConfirmations.remove(player.getUUID());
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.home.overwrite_cancelled", homeName));
            return 1;
         } else {
            player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.home.no_pending_overwrite", homeName));
            return 0;
         }
      }
   }

   private static int executeDelHome(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         HomeManager homeManager = HomeManager.getInstance();
         ConfigManager config = ConfigManager.getInstance();
         if (config.isRequireConfirmationForDeleteEnabled()) {
            String pending = pendingDeleteConfirmations.get(player.getUUID());
            if (pending == null || !pending.equals(homeName)) {
               pendingDeleteConfirmations.put(player.getUUID(), homeName);
               player.sendSystemMessage(MessageUtil.homeConfirmComponent(homeName, "delete", "/delhome " + homeName, "/delhome " + homeName + " deny"));
               return 1;
            }

            pendingDeleteConfirmations.remove(player.getUUID());
         }

         if (homeManager.deleteHome(player, homeName)) {
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.delete_success", homeName));
            return 1;
         } else {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.delete_failed", homeName));
            return 0;
         }
      }
   }

   private static int executeDelHomeConfirm(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         HomeManager homeManager = HomeManager.getInstance();
         ConfigManager config = ConfigManager.getInstance();
         String pending = pendingDeleteConfirmations.get(player.getUUID());
         if (!config.isRequireConfirmationForDeleteEnabled()) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.delete_no_confirm_required"));
            return 0;
         } else if (pending != null && pending.equals(homeName)) {
            pendingDeleteConfirmations.remove(player.getUUID());
            boolean success = homeManager.deleteHome(player, homeName);
            if (success) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.home.delete_success", homeName));
               return 1;
            } else {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.delete_failed", homeName));
               return 0;
            }
         } else {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.home.no_pending_delete", homeName));
            pendingDeleteConfirmations.remove(player.getUUID());
            return 0;
         }
      }
   }

   private static int executeHomes(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("This command can only be used by players."));
         return 0;
      } else {
         HomeManager homeManager = HomeManager.getInstance();
         String homesList = homeManager.getFormattedHomesList(player);
         player.sendSystemMessage(MessageUtil.component(homesList));
         return 1;
      }
   }

   private static int executeDelHomeDeny(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      if (player == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.command.player_only"));
         return 0;
      } else {
         String homeName = StringArgumentType.getString(context, "name");
         String pending = pendingDeleteConfirmations.get(player.getUUID());
         if (pending != null && pending.equals(homeName)) {
            pendingDeleteConfirmations.remove(player.getUUID());
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.home.delete_cancelled", homeName));
            return 1;
         } else {
            player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.home.no_pending_delete", homeName));
            return 0;
         }
      }
   }

   private static void registerRenameHomeCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("renamehome")
                  .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.renamehome")))
               .then(
                  Commands.argument("oldname", StringArgumentType.word())
                     .then(
                        Commands.argument("newname", StringArgumentType.word())
                           .executes(
                              ctx -> executeRenameHome(ctx, StringArgumentType.getString(ctx, "oldname"), StringArgumentType.getString(ctx, "newname"), null)
                           )
                     )
               ))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playercolon", StringArgumentType.word())
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.renamehome.others")))
                  .then(Commands.argument("newname2", StringArgumentType.word()).executes(ctx -> {
                     String arg = StringArgumentType.getString(ctx, "playercolon");
                     String newName = StringArgumentType.getString(ctx, "newname2");
                     if (arg.contains(":")) {
                        String[] parts = arg.split(":", 2);
                        return executeRenameHome(ctx, parts[1], newName, parts[0]);
                     } else {
                        return executeRenameHome(ctx, arg, newName, null);
                     }
                  }))
            )
      );
   }

   private static int executeRenameHome(CommandContext<CommandSourceStack> ctx, String oldName, String newName, String targetPlayerName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target;
      if (targetPlayerName != null) {
         target = src.getServer().getPlayerList().getPlayerByName(targetPlayerName);
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetPlayerName));
            return 0;
         }
      } else {
         target = src.getPlayer();
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         }
      }

      return HomeManager.getInstance().renameHome(target, oldName.toLowerCase(), newName.toLowerCase()) ? 1 : 0;
   }
}
