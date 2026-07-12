package com.zerog.neoessentials.api.permissions.external;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakePermissionsEXCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(FakePermissionsEXCommand.class);

   public static void registerFakePexCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      LOGGER.info("Registering fake /pex command for NeoEssentials permission tab completion...");

      try {
         SuggestionProvider<CommandSourceStack> permissionSuggestions = (context, builder) -> {
            List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
            String input = builder.getRemaining().toLowerCase();
            List<String> filtered = permissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).toList();
            return SharedSuggestionProvider.suggest(filtered, builder);
         };
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pex")
                        .requires(source -> source.hasPermission(2)))
                     .then(
                        Commands.literal("group")
                           .then(
                              ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("groupname", StringArgumentType.word())
                                       .suggests(
                                          (context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("admin", "moderator", "player", "vip", "default"), builder
                                             )
                                       )
                                       .then(
                                          Commands.literal("add")
                                             .then(
                                                Commands.argument("permission", StringArgumentType.greedyString())
                                                   .suggests(permissionSuggestions)
                                                   .executes(
                                                      ctx -> {
                                                         String group = StringArgumentType.getString(ctx, "groupname");
                                                         String permission = StringArgumentType.getString(ctx, "permission");
                                                         ((CommandSourceStack)ctx.getSource())
                                                            .sendSuccess(
                                                               () -> Component.literal(
                                                                     "§c[FAKE PEX] §6Would add permission §f"
                                                                        + permission
                                                                        + " §6to group §f"
                                                                        + group
                                                                        + "§6.\n§7This is a NeoEssentials simulation. Install actual PermissionsEX for real functionality.\n§7Permission is valid: §a"
                                                                        + ExternalPermissionProvider.hasPermission(permission)
                                                                  ),
                                                               false
                                                            );
                                                         LOGGER.info("Fake PEX: Would add {} to group {}", permission, group);
                                                         return 1;
                                                      }
                                                   )
                                             )
                                       ))
                                    .then(
                                       Commands.literal("remove")
                                          .then(
                                             Commands.argument("permission", StringArgumentType.greedyString())
                                                .suggests(permissionSuggestions)
                                                .executes(
                                                   ctx -> {
                                                      String group = StringArgumentType.getString(ctx, "groupname");
                                                      String permission = StringArgumentType.getString(ctx, "permission");
                                                      ((CommandSourceStack)ctx.getSource())
                                                         .sendSuccess(
                                                            () -> Component.literal(
                                                                  "§c[FAKE PEX] §6Would remove permission §f"
                                                                     + permission
                                                                     + " §6from group §f"
                                                                     + group
                                                                     + "§6.\n§7This is a NeoEssentials simulation. Install actual PermissionsEX for real functionality."
                                                               ),
                                                            false
                                                         );
                                                      return 1;
                                                   }
                                                )
                                          )
                                    ))
                                 .then(
                                    Commands.literal("list")
                                       .executes(
                                          ctx -> {
                                             String group = StringArgumentType.getString(ctx, "groupname");
                                             ((CommandSourceStack)ctx.getSource())
                                                .sendSuccess(
                                                   () -> Component.literal(
                                                         "§c[FAKE PEX] §6Group §f"
                                                            + group
                                                            + " §6permissions:\n§7This is a simulation. Install actual PermissionsEX to manage real permissions.\n§7Available NeoEssentials permissions: §f"
                                                            + ExternalPermissionProvider.getAllNeoEssentialsPermissions().size()
                                                      ),
                                                   false
                                                );
                                             return 1;
                                          }
                                       )
                                 )
                           )
                     ))
                  .then(
                     Commands.literal("user")
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("username", StringArgumentType.word())
                                 .suggests(
                                    (context, builder) -> SharedSuggestionProvider.suggest(
                                          ((CommandSourceStack)context.getSource()).getServer().getPlayerNames(), builder
                                       )
                                 )
                                 .then(
                                    Commands.literal("add")
                                       .then(
                                          Commands.argument("permission", StringArgumentType.greedyString())
                                             .suggests(permissionSuggestions)
                                             .executes(
                                                ctx -> {
                                                   String user = StringArgumentType.getString(ctx, "username");
                                                   String permission = StringArgumentType.getString(ctx, "permission");
                                                   ((CommandSourceStack)ctx.getSource())
                                                      .sendSuccess(
                                                         () -> Component.literal(
                                                               "§c[FAKE PEX] §6Would add permission §f"
                                                                  + permission
                                                                  + " §6to user §f"
                                                                  + user
                                                                  + "§6.\n§7This is a NeoEssentials simulation. Install actual PermissionsEX for real functionality.\n§7Permission is valid: §a"
                                                                  + ExternalPermissionProvider.hasPermission(permission)
                                                            ),
                                                         false
                                                      );
                                                   LOGGER.info("Fake PEX: Would add {} to user {}", permission, user);
                                                   return 1;
                                                }
                                             )
                                       )
                                 ))
                              .then(
                                 Commands.literal("remove")
                                    .then(
                                       Commands.argument("permission", StringArgumentType.greedyString())
                                          .suggests(permissionSuggestions)
                                          .executes(
                                             ctx -> {
                                                String user = StringArgumentType.getString(ctx, "username");
                                                String permission = StringArgumentType.getString(ctx, "permission");
                                                ((CommandSourceStack)ctx.getSource())
                                                   .sendSuccess(
                                                      () -> Component.literal(
                                                            "§c[FAKE PEX] §6Would remove permission §f"
                                                               + permission
                                                               + " §6from user §f"
                                                               + user
                                                               + "§6.\n§7This is a NeoEssentials simulation. Install actual PermissionsEX for real functionality."
                                                         ),
                                                      false
                                                   );
                                                return 1;
                                             }
                                          )
                                    )
                              )
                        )
                  ))
               .executes(
                  ctx -> {
                     List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                     ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(
                           () -> Component.literal(
                                 "§c[FAKE PermissionsEX] §6NeoEssentials Permission Bridge\n§7This is a simulation providing tab completion for NeoEssentials permissions.\n§7Install actual PermissionsEX for real permission management.\n\n§eAvailable commands:\n§f/pex group <name> add <permission> §7- Add permission to group\n§f/pex user <name> add <permission> §7- Add permission to user\n§f/pex group <name> remove <permission> §7- Remove permission from group\n§f/pex user <name> remove <permission> §7- Remove permission from user\n\n§7NeoEssentials permissions available: §f"
                                    + permissions.size()
                                    + "\n§7Try typing: §f/pex group admin add neoessentials.§7 and press TAB"
                              ),
                           false
                        );
                     return 1;
                  }
               )
         );
         LOGGER.info(
            "Fake /pex command registered - {} permissions available for tab completion", ExternalPermissionProvider.getAllNeoEssentialsPermissions().size()
         );
      } catch (Exception var2) {
         LOGGER.error("Failed to register fake /pex command", var2);
      }
   }

   public static void checkForRealPermissionsEX(CommandDispatcher<CommandSourceStack> dispatcher) {
      try {
         CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild("pex");
         if (existing != null) {
            LOGGER.info("Real /pex command detected - our fake command may be overridden");
         }
      } catch (Exception var2) {
         LOGGER.debug("Could not check for real PermissionsEX", var2);
      }
   }
}
