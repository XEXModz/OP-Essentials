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

public class PermissionCommandInjector {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCommandInjector.class);

   public static void injectPermissionCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      LOGGER.info("Injecting NeoEssentials permissions as discoverable commands for PermissionsEX...");

      try {
         List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
         SuggestionProvider<CommandSourceStack> permissionSuggestions = (context, builder) -> SharedSuggestionProvider.suggest(permissions, builder);
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("neoessentials-pex-bridge")
                        .requires(source -> source.hasPermission(4)))
                     .then(
                        Commands.literal("group")
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("groupname", StringArgumentType.word())
                                    .then(
                                       Commands.literal("add")
                                          .then(
                                             Commands.argument("permission", StringArgumentType.greedyString())
                                                .suggests(permissionSuggestions)
                                                .executes(
                                                   ctx -> {
                                                      String permission = StringArgumentType.getString(ctx, "permission");
                                                      String group = StringArgumentType.getString(ctx, "groupname");
                                                      ((CommandSourceStack)ctx.getSource())
                                                         .sendSuccess(
                                                            () -> Component.literal(
                                                                  "§6NeoEssentials Permission Bridge: §7Use §f/pex group "
                                                                     + group
                                                                     + " add "
                                                                     + permission
                                                                     + " §7in your actual PermissionsEX plugin"
                                                               ),
                                                            false
                                                         );
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
                                                   String permission = StringArgumentType.getString(ctx, "permission");
                                                   String group = StringArgumentType.getString(ctx, "groupname");
                                                   ((CommandSourceStack)ctx.getSource())
                                                      .sendSuccess(
                                                         () -> Component.literal(
                                                               "§6NeoEssentials Permission Bridge: §7Use §f/pex group "
                                                                  + group
                                                                  + " remove "
                                                                  + permission
                                                                  + " §7in your actual PermissionsEX plugin"
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
                  .then(
                     Commands.literal("user")
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("username", StringArgumentType.word())
                                 .then(
                                    Commands.literal("add")
                                       .then(
                                          Commands.argument("permission", StringArgumentType.greedyString())
                                             .suggests(permissionSuggestions)
                                             .executes(
                                                ctx -> {
                                                   String permission = StringArgumentType.getString(ctx, "permission");
                                                   String user = StringArgumentType.getString(ctx, "username");
                                                   ((CommandSourceStack)ctx.getSource())
                                                      .sendSuccess(
                                                         () -> Component.literal(
                                                               "§6NeoEssentials Permission Bridge: §7Use §f/pex user "
                                                                  + user
                                                                  + " add "
                                                                  + permission
                                                                  + " §7in your actual PermissionsEX plugin"
                                                            ),
                                                         false
                                                      );
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
                                                String permission = StringArgumentType.getString(ctx, "permission");
                                                String user = StringArgumentType.getString(ctx, "username");
                                                ((CommandSourceStack)ctx.getSource())
                                                   .sendSuccess(
                                                      () -> Component.literal(
                                                            "§6NeoEssentials Permission Bridge: §7Use §f/pex user "
                                                               + user
                                                               + " remove "
                                                               + permission
                                                               + " §7in your actual PermissionsEX plugin"
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
                     ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(
                           () -> Component.literal(
                                 "§6=== NeoEssentials PermissionsEX Bridge ===\n§7This command provides tab completion for NeoEssentials permissions.\n§7Use: §f/neoessentials-pex-bridge group <name> add <permission>\n§7Use: §f/neoessentials-pex-bridge user <name> add <permission>\n§7Total permissions available: §f"
                                    + permissions.size()
                                    + "\n§7For actual permission management, use your PermissionsEX plugin."
                              ),
                           false
                        );
                     return 1;
                  }
               )
         );
         injectIntoExistingCommands(dispatcher, permissions);
         LOGGER.info("Permission command injection completed - {} permissions now discoverable", permissions.size());
      } catch (Exception var3) {
         LOGGER.error("Failed to inject permission commands", var3);
      }
   }

   private static void injectIntoExistingCommands(CommandDispatcher<CommandSourceStack> dispatcher, List<String> permissions) {
      try {
         for (CommandNode<CommandSourceStack> command : dispatcher.getRoot().getChildren()) {
            String commandName = command.getName();
            if (commandName.contains("pex")
               || commandName.contains("permission")
               || commandName.contains("perm")
               || commandName.contains("group")
               || commandName.contains("user")) {
               LOGGER.debug("Found potential permission command to enhance: {}", commandName);
            }
         }
      } catch (Exception var6) {
         LOGGER.debug("Could not analyze existing commands for permission injection", var6);
      }
   }

   public static void registerTestCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("test-pex-integration").requires(source -> source.hasPermission(4)))
            .executes(
               ctx -> {
                  List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(
                        () -> Component.literal(
                              "§6=== PermissionsEX Integration Test ===\n§7Available NeoEssentials permissions: §f"
                                 + permissions.size()
                                 + "\n§7Sample permissions:\n§f"
                                 + String.join("\n", permissions.subList(0, Math.min(10, permissions.size())))
                                 + (permissions.size() > 10 ? "\n§7... and " + (permissions.size() - 10) + " more" : "")
                           ),
                        false
                     );
                  return 1;
               }
            )
      );
   }
}
