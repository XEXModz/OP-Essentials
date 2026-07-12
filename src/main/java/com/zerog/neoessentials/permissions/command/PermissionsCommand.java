package com.zerog.neoessentials.permissions.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.api.permissions.external.ExternalPermissionProvider;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.EconomyPlayerUtil;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionStorage;
import com.zerog.neoessentials.permissions.PermissionSystem;
import com.zerog.neoessentials.permissions.PermissionUser;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionsCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (!ConfigManager.isPermissionsEnabled()) {
         LOGGER.debug("Permissions module is disabled, skipping permissions command registration");
      } else {
         boolean pexEnabled = ConfigManager.getInstance().isCommandEnabled("pex");
         boolean permissionsEnabled = ConfigManager.getInstance().isCommandEnabled("permissions");
         if (!pexEnabled && !permissionsEnabled) {
            LOGGER.debug("Both pex and permissions commands are disabled, skipping registration");
         } else {
            if (pexEnabled) {
               dispatcher.register(createRoot("pex"));
            }

            if (permissionsEnabled) {
               dispatcher.register(createRoot("permissions"));
            }
         }
      }
   }

   private static LiteralArgumentBuilder<CommandSourceStack> createRoot(String root) {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          root
                                       )
                                       .then(Commands.literal("reload").executes(ctx -> reload(ctx))))
                                    .then(
                                       ((LiteralArgumentBuilder)Commands.literal("list").then(Commands.literal("groups").executes(ctx -> listGroups(ctx))))
                                          .then(Commands.literal("users").executes(ctx -> listUsers(ctx)))
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("info")
                                          .then(
                                             Commands.literal("group").then(Commands.argument("group", StringArgumentType.word()).suggests((ctx, builder) -> {
                                                try {
                                                   List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                                                   if (!groups.isEmpty()) {
                                                      return SharedSuggestionProvider.suggest(groups, builder);
                                                   }
                                                } catch (Exception var3) {
                                                   LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                                                }

                                                return SharedSuggestionProvider.suggest(
                                                   Arrays.asList("admin", "moderator", "player", "vip", "default"), builder
                                                );
                                             }).executes(ctx -> showGroupInfo(ctx)))
                                          ))
                                       .then(
                                          Commands.literal("user")
                                             .then(
                                                Commands.argument("player", StringArgumentType.word())
                                                   .suggests(
                                                      (ctx, builder) -> SharedSuggestionProvider.suggest(
                                                            ((CommandSourceStack)ctx.getSource())
                                                               .getServer()
                                                               .getPlayerList()
                                                               .getPlayers()
                                                               .stream()
                                                               .map(p -> p.getGameProfile().getName()),
                                                            builder
                                                         )
                                                   )
                                                   .executes(ctx -> showUserInfo(ctx))
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("check")
                                       .then(
                                          Commands.literal("user")
                                             .then(
                                                Commands.argument("player", StringArgumentType.word())
                                                   .suggests(
                                                      (ctx, builder) -> SharedSuggestionProvider.suggest(
                                                            ((CommandSourceStack)ctx.getSource())
                                                               .getServer()
                                                               .getPlayerList()
                                                               .getPlayers()
                                                               .stream()
                                                               .map(p -> p.getGameProfile().getName()),
                                                            builder
                                                         )
                                                   )
                                                   .then(
                                                      Commands.argument("permission", StringArgumentType.greedyString())
                                                         .executes(ctx -> checkUserPermission(ctx))
                                                   )
                                             )
                                       ))
                                    .then(Commands.literal("group").then(Commands.argument("group", StringArgumentType.word()).suggests((ctx, builder) -> {
                                       try {
                                          List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                                          if (!groups.isEmpty()) {
                                             return SharedSuggestionProvider.suggest(groups, builder);
                                          }
                                       } catch (Exception var3) {
                                          LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                                       }

                                       return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                                    }).then(Commands.argument("permission", StringArgumentType.greedyString()).executes(ctx -> checkGroupPermission(ctx)))))
                              ))
                           .then(
                              Commands.literal("search")
                                 .then(Commands.argument("pattern", StringArgumentType.greedyString()).executes(ctx -> searchPermissions(ctx)))
                           ))
                        .then(
                           Commands.literal("create")
                              .then(Commands.literal("group").then(Commands.argument("group", StringArgumentType.word()).executes(ctx -> createGroup(ctx))))
                        ))
                     .then(
                        Commands.literal("delete")
                           .then(Commands.literal("group").then(Commands.argument("group", StringArgumentType.word()).suggests((ctx, builder) -> {
                              try {
                                 List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                                 if (!groups.isEmpty()) {
                                    return SharedSuggestionProvider.suggest(groups, builder);
                                 }
                              } catch (Exception var3) {
                                 LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                              }

                              return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                           }).executes(ctx -> deleteGroup(ctx))))
                     ))
                  .then(
                     Commands.literal("rename")
                        .then(Commands.literal("group").then(Commands.argument("oldName", StringArgumentType.word()).suggests((ctx, builder) -> {
                           try {
                              List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                              if (!groups.isEmpty()) {
                                 return SharedSuggestionProvider.suggest(groups, builder);
                              }
                           } catch (Exception var3) {
                              LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                           }

                           return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                        }).then(Commands.argument("newName", StringArgumentType.word()).executes(ctx -> renameGroup(ctx)))))
                  ))
               .then(
                  Commands.literal("clone")
                     .then(Commands.literal("group").then(Commands.argument("source", StringArgumentType.word()).suggests((ctx, builder) -> {
                        try {
                           List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                           if (!groups.isEmpty()) {
                              return SharedSuggestionProvider.suggest(groups, builder);
                           }
                        } catch (Exception var3) {
                           LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                        }

                        return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                     }).then(Commands.argument("newGroup", StringArgumentType.word()).executes(ctx -> cloneGroup(ctx)))))
               ))
            .then(
               Commands.literal("group")
                  .then(
                     ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument(
                                          "group", StringArgumentType.word()
                                       )
                                       .suggests((ctx, builder) -> {
                                          try {
                                             List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                                             if (!groups.isEmpty()) {
                                                return SharedSuggestionProvider.suggest(groups, builder);
                                             }
                                          } catch (Exception var3) {
                                          }

                                          return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                                       })
                                       .then(
                                          Commands.literal("setprefix")
                                             .then(Commands.argument("prefix", StringArgumentType.greedyString()).executes(ctx -> setPrefix(ctx)))
                                       ))
                                    .then(
                                       Commands.literal("setsuffix")
                                          .then(Commands.argument("suffix", StringArgumentType.greedyString()).executes(ctx -> setSuffix(ctx)))
                                    ))
                                 .then(
                                    Commands.literal("add")
                                       .then(
                                          Commands.argument("permission", StringArgumentType.greedyString())
                                             .suggests(
                                                (ctx, builder) -> {
                                                   try {
                                                      List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                                                      String input = builder.getRemaining().toLowerCase();
                                                      List<String> filtered = permissions.stream()
                                                         .filter(perm -> perm.toLowerCase().startsWith(input))
                                                         .toList();
                                                      return SharedSuggestionProvider.suggest(filtered, builder);
                                                   } catch (Exception var5) {
                                                      return SharedSuggestionProvider.suggest(
                                                         Arrays.asList(
                                                            "neoessentials.*", "neoessentials.admin.*", "neoessentials.economy.*", "neoessentials.teleport.*"
                                                         ),
                                                         builder
                                                      );
                                                   }
                                                }
                                             )
                                             .executes(ctx -> addGroupPermission(ctx))
                                       )
                                 ))
                              .then(
                                 Commands.literal("remove")
                                    .then(
                                       Commands.argument("permission", StringArgumentType.greedyString())
                                          .suggests(
                                             (ctx, builder) -> {
                                                try {
                                                   List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                                                   String input = builder.getRemaining().toLowerCase();
                                                   List<String> filtered = permissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).toList();
                                                   return SharedSuggestionProvider.suggest(filtered, builder);
                                                } catch (Exception var5) {
                                                   return SharedSuggestionProvider.suggest(
                                                      Arrays.asList(
                                                         "neoessentials.*", "neoessentials.admin.*", "neoessentials.economy.*", "neoessentials.teleport.*"
                                                      ),
                                                      builder
                                                   );
                                                }
                                             }
                                          )
                                          .executes(ctx -> removeGroupPermission(ctx))
                                    )
                              ))
                           .then(Commands.literal("clear").executes(ctx -> clearGroupPermissions(ctx))))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("inherit")
                                 .then(Commands.literal("add").then(Commands.argument("inheritGroup", StringArgumentType.word()).suggests((ctx, builder) -> {
                                    try {
                                       List<String> groups = PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName).toList();
                                       if (!groups.isEmpty()) {
                                          return SharedSuggestionProvider.suggest(groups, builder);
                                       }
                                    } catch (Exception var3) {
                                       LOGGER.error("Error in permission command suggestion: {}", var3.getMessage());
                                    }

                                    return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                                 }).executes(ctx -> addGroupInheritance(ctx)))))
                              .then(Commands.literal("remove").then(Commands.argument("inheritGroup", StringArgumentType.word()).suggests((ctx, builder) -> {
                                 try {
                                    String groupName = StringArgumentType.getString(ctx, "group");
                                    PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
                                    if (group != null && !group.getInherits().isEmpty()) {
                                       return SharedSuggestionProvider.suggest(group.getInherits(), builder);
                                    }
                                 } catch (Exception var4) {
                                    LOGGER.error("Error in permission command suggestion: {}", var4.getMessage());
                                 }

                                 return SharedSuggestionProvider.suggest(Arrays.asList("admin", "moderator", "player", "vip", "default"), builder);
                              }).executes(ctx -> removeGroupInheritance(ctx))))
                        )
                  )
            ))
         .then(
            Commands.literal("user")
               .then(
                  ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                              .suggests(
                                 (ctx, builder) -> SharedSuggestionProvider.suggest(
                                       ((CommandSourceStack)ctx.getSource())
                                          .getServer()
                                          .getPlayerList()
                                          .getPlayers()
                                          .stream()
                                          .map(p -> p.getGameProfile().getName()),
                                       builder
                                    )
                              )
                              .then(
                                 Commands.literal("setgroup")
                                    .then(
                                       Commands.argument("group", StringArgumentType.word())
                                          .suggests(
                                             (ctx, builder) -> SharedSuggestionProvider.suggest(
                                                   PermissionAPI.getManager().getGroups().stream().map(PermissionGroup::getName), builder
                                                )
                                          )
                                          .executes(ctx -> setUserGroup(ctx))
                                    )
                              ))
                           .then(
                              Commands.literal("add")
                                 .then(
                                    Commands.argument("permission", StringArgumentType.greedyString())
                                       .suggests(
                                          (ctx, builder) -> {
                                             try {
                                                List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                                                String input = builder.getRemaining().toLowerCase();
                                                List<String> filtered = permissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).toList();
                                                return SharedSuggestionProvider.suggest(filtered, builder);
                                             } catch (Exception var5) {
                                                return SharedSuggestionProvider.suggest(
                                                   Arrays.asList(
                                                      "neoessentials.*", "neoessentials.admin.*", "neoessentials.economy.*", "neoessentials.teleport.*"
                                                   ),
                                                   builder
                                                );
                                             }
                                          }
                                       )
                                       .executes(ctx -> addUserPermission(ctx))
                                 )
                           ))
                        .then(
                           Commands.literal("remove")
                              .then(
                                 Commands.argument("permission", StringArgumentType.greedyString())
                                    .suggests(
                                       (ctx, builder) -> {
                                          try {
                                             List<String> permissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
                                             String input = builder.getRemaining().toLowerCase();
                                             List<String> filtered = permissions.stream().filter(perm -> perm.toLowerCase().startsWith(input)).toList();
                                             return SharedSuggestionProvider.suggest(filtered, builder);
                                          } catch (Exception var5) {
                                             return SharedSuggestionProvider.suggest(
                                                Arrays.asList("neoessentials.*", "neoessentials.admin.*", "neoessentials.economy.*", "neoessentials.teleport.*"),
                                                builder
                                             );
                                          }
                                       }
                                    )
                                    .executes(ctx -> removeUserPermission(ctx))
                              )
                        ))
                     .then(Commands.literal("clear").executes(ctx -> clearUserPermissions(ctx)))
               )
         );
   }

   private static int reload(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.reload"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         try {
            PermissionSystem.reload();
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.reloaded"), false);
            return 1;
         } catch (Exception var3) {
            LOGGER.error("Failed to reload permissions", var3);
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.reload_failed", var3.getMessage()));
            return 0;
         }
      }
   }

   private static int setPrefix(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.modify"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         String prefix = StringArgumentType.getString(ctx, "prefix");
         if (prefix.length() > 64) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Prefix is too long! Maximum length is 64 characters."));
            return 0;
         } else if (prefix.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Prefix contains invalid control characters!"));
            return 0;
         } else {
            PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
            if (group == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found"));
               return 0;
            } else {
               group.setPrefix(prefix);
               PermissionAPI.getManager().clearCache();

               try {
                  PermissionStorage.save(PermissionAPI.getManager());
                  LOGGER.info("Set prefix '{}' for group '{}'", prefix, groupName);
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.prefix_set", groupName, prefix), false);
                  return 1;
               } catch (Exception var6) {
                  LOGGER.error("Failed to save permissions after setting prefix", var6);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save prefix: " + var6.getMessage()));
                  return 0;
               }
            }
         }
      }
   }

   private static int setSuffix(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.modify"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         String suffix = StringArgumentType.getString(ctx, "suffix");
         if (suffix.length() > 64) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Suffix is too long! Maximum length is 64 characters."));
            return 0;
         } else if (suffix.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Suffix contains invalid control characters!"));
            return 0;
         } else {
            PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
            if (group == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found"));
               return 0;
            } else {
               group.setSuffix(suffix);
               PermissionAPI.getManager().clearCache();

               try {
                  PermissionStorage.save(PermissionAPI.getManager());
                  LOGGER.info("Set suffix '{}' for group '{}'", suffix, groupName);
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.suffix_set", groupName, suffix), false);
                  return 1;
               } catch (Exception var6) {
                  LOGGER.error("Failed to save permissions after setting suffix", var6);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save suffix: " + var6.getMessage()));
                  return 0;
               }
            }
         }
      }
   }

   private static int addGroupPermission(CommandContext<CommandSourceStack> ctx) {
      try {
         PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
            (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.permissions"
         );
         if (!permResult.hasPermission()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
         } else {
            String groupName = StringArgumentType.getString(ctx, "group");
            String perm = StringArgumentType.getString(ctx, "permission").toLowerCase().trim();
            LOGGER.debug("Adding permission '{}' to group '{}'", perm, groupName);
            if (!PermissionManager.isValidPermission(perm)) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.invalid_permission", perm));
               return 0;
            } else {
               PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
               if (group == null) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
                  return 0;
               } else if (group.getPermissions().contains(perm)) {
                  ((CommandSourceStack)ctx.getSource())
                     .sendFailure(MessageUtil.warning("commands.neoessentials.permissions.permission_already_exists", perm, groupName));
                  return 0;
               } else {
                  group.addPermission(perm);
                  PermissionAPI.getManager().clearCache();

                  try {
                     PermissionStorage.save(PermissionAPI.getManager());
                     LOGGER.info("Added permission '{}' to group '{}'", perm, groupName);
                  } catch (Exception var6) {
                     LOGGER.error("Failed to save permissions after adding group permission", var6);
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.save_failed"));
                     return 0;
                  }

                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.permission_added", perm, groupName), false);
                  return 1;
               }
            }
         }
      } catch (Exception var7) {
         LOGGER.error("Unexpected error in addGroupPermission command", var7);
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("§cAn unexpected error occurred: " + var7.getMessage()));
         return 0;
      }
   }

   private static int removeGroupPermission(CommandContext<CommandSourceStack> ctx) {
      try {
         PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
            (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.permissions"
         );
         if (!permResult.hasPermission()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
         } else {
            String groupName = StringArgumentType.getString(ctx, "group");
            String perm = StringArgumentType.getString(ctx, "permission").toLowerCase().trim();
            LOGGER.debug("Removing permission '{}' from group '{}'", perm, groupName);
            PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
            if (group == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
               return 0;
            } else if (!group.getPermissions().contains(perm)) {
               ((CommandSourceStack)ctx.getSource())
                  .sendFailure(MessageUtil.warning("commands.neoessentials.permissions.permission_not_found", perm, groupName));
               return 0;
            } else {
               group.removePermission(perm);
               PermissionAPI.getManager().clearCache();

               try {
                  PermissionStorage.save(PermissionAPI.getManager());
                  LOGGER.info("Removed permission '{}' from group '{}'", perm, groupName);
               } catch (Exception var6) {
                  LOGGER.error("Failed to save permissions after removing group permission", var6);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.save_failed"));
                  return 0;
               }

               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.permission_removed", perm, groupName), false);
               return 1;
            }
         }
      } catch (Exception var7) {
         LOGGER.error(
            "Unexpected error in removeGroupPermission command for group '{}', permission '{}'",
            new Object[]{StringArgumentType.getString(ctx, "group"), StringArgumentType.getString(ctx, "permission"), var7}
         );
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("§cAn unexpected error occurred: " + var7.getMessage()));
         return 0;
      }
   }

   private static int setUserGroup(CommandContext<CommandSourceStack> ctx) {
      try {
         PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
            (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.user.groups"
         );
         if (!permResult.hasPermission()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
            return 0;
         } else {
            String playerName = StringArgumentType.getString(ctx, "player");
            String groupName = StringArgumentType.getString(ctx, "group");
            MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
            LOGGER.debug("Setting group '{}' for user '{}'", groupName, playerName);
            Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
            if (uuidOpt.isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found"));
               return 0;
            } else {
               UUID uuid = uuidOpt.get();
               if (PermissionAPI.getManager() == null) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("§cPermission system not initialized. Run: neoe reload"));
                  return 0;
               } else {
                  PermissionUser user = PermissionAPI.getManager().getUser(uuid);
                  if (user == null) {
                     user = new PermissionUser(uuid, PermissionAPI.getManager().getDefaultGroup());
                     PermissionAPI.getManager().addUser(user);
                  }

                  PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
                  if (group == null) {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
                     return 0;
                  } else if (groupName.equalsIgnoreCase(user.getGroup())) {
                     ((CommandSourceStack)ctx.getSource())
                        .sendFailure(MessageUtil.warning("commands.neoessentials.permissions.user_already_in_group", playerName, groupName));
                     return 0;
                  } else {
                     user.setGroup(groupName);
                     PermissionAPI.getManager().clearCache();

                     try {
                        PermissionStorage.save(PermissionAPI.getManager());
                        LOGGER.info("Set group '{}' for user '{}'", groupName, playerName);
                     } catch (Exception var10) {
                        LOGGER.error("Failed to save permissions after setting user group", var10);
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.save_failed"));
                        return 0;
                     }

                     ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.user_group_set", playerName, groupName), false);
                     return 1;
                  }
               }
            }
         }
      } catch (Exception var11) {
         LOGGER.error(
            "Unexpected error in setUserGroup command for player '{}', group '{}'",
            new Object[]{StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "group"), var11}
         );
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("§cAn unexpected error occurred: " + var11.getMessage()));
         return 0;
      }
   }

   private static int addUserPermission(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.user.permissions"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String playerName = StringArgumentType.getString(ctx, "player");
         String perm = StringArgumentType.getString(ctx, "permission").toLowerCase().trim();
         MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
         if (!PermissionManager.isValidPermission(perm)) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.invalid_permission", perm));
            return 0;
         } else {
            Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
            if (uuidOpt.isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found"));
               return 0;
            } else {
               UUID uuid = uuidOpt.get();
               PermissionUser user = PermissionAPI.getManager().getUser(uuid);
               if (user == null) {
                  String defaultGroup = PermissionAPI.getManager().getDefaultGroup();
                  user = new PermissionUser(uuid, defaultGroup);
                  PermissionAPI.getManager().addUser(user);
               }

               if (user.getPermissions().contains(perm)) {
                  ((CommandSourceStack)ctx.getSource())
                     .sendFailure(MessageUtil.warning("commands.neoessentials.permissions.permission_already_exists_for_user", perm, playerName));
                  return 0;
               } else {
                  user.addPermission(perm);
                  PermissionAPI.getManager().clearCache();

                  try {
                     PermissionStorage.save(PermissionAPI.getManager());
                     LOGGER.info("Added permission '{}' to user '{}'", perm, playerName);
                  } catch (Exception var9) {
                     LOGGER.error("Failed to save permissions after adding user permission", var9);
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.save_failed"));
                     return 0;
                  }

                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.permission_added_to_user", perm, playerName), false);
                  return 1;
               }
            }
         }
      }
   }

   private static int removeUserPermission(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.user.permissions"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String playerName = StringArgumentType.getString(ctx, "player");
         String perm = StringArgumentType.getString(ctx, "permission").toLowerCase().trim();
         MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
         Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
         if (uuidOpt.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found"));
            return 0;
         } else {
            UUID uuid = uuidOpt.get();
            PermissionUser user = PermissionAPI.getManager().getUser(uuid);
            if (user == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.user_not_found"));
               return 0;
            } else if (!user.getPermissions().contains(perm)) {
               ((CommandSourceStack)ctx.getSource())
                  .sendFailure(MessageUtil.warning("commands.neoessentials.permissions.permission_not_found_for_user", perm, playerName));
               return 0;
            } else {
               user.removePermission(perm);
               PermissionAPI.getManager().clearCache();

               try {
                  PermissionStorage.save(PermissionAPI.getManager());
                  LOGGER.info("Removed permission '{}' from user '{}'", perm, playerName);
               } catch (Exception var9) {
                  LOGGER.error("Failed to save permissions after removing user permission", var9);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.save_failed"));
                  return 0;
               }

               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.success("commands.neoessentials.permissions.permission_removed_from_user", perm, playerName), false);
               return 1;
            }
         }
      }
   }

   private static int listGroups(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.list.groups"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         PermissionManager manager = PermissionAPI.getManager();
         if (manager == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.manager_not_available"));
            return 0;
         } else {
            Collection<PermissionGroup> groups = manager.getGroups();
            if (groups.isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.no_groups"), false);
               return 1;
            } else {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.groups_header"), false);

               for (PermissionGroup group : groups) {
                  String prefix = group.getPrefix() != null ? group.getPrefix() : MessageUtil.localize("commands.neoessentials.permissions.none");
                  String suffix = group.getSuffix() != null ? group.getSuffix() : MessageUtil.localize("commands.neoessentials.permissions.none");
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.component("commands.neoessentials.permissions.group_entry", group.getName(), prefix, suffix), false);
               }

               return 1;
            }
         }
      }
   }

   private static int listUsers(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.list.users"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         PermissionManager manager = PermissionAPI.getManager();
         if (manager == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.manager_not_available"));
            return 0;
         } else {
            Collection<PermissionUser> users = manager.getUsers();
            if (users.isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.no_users"), false);
               return 1;
            } else {
               MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.permissions.users_header"), false);

               for (PermissionUser user : users) {
                  UUID uuid = user.getUuid();
                  String displayName = uuid.toString();
                  Optional<ServerPlayer> onlinePlayer = server.getPlayerList().getPlayers().stream().filter(p -> p.getUUID().equals(uuid)).findFirst();
                  if (onlinePlayer.isPresent()) {
                     displayName = onlinePlayer.get().getGameProfile().getName();
                  } else {
                     Optional<GameProfile> profile = server.getProfileCache().get(uuid);
                     if (profile.isPresent()) {
                        displayName = profile.get().getName();
                     }
                  }

                  String userDisplay = displayName.equals(uuid.toString()) ? displayName : displayName + " (" + uuid.toString().substring(0, 8) + "...)";
                  String group = user.getGroup() != null ? user.getGroup() : MessageUtil.localize("commands.neoessentials.permissions.default");
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.component("commands.neoessentials.permissions.user_entry", userDisplay, group), false);
               }

               return 1;
            }
         }
      }
   }

   private static int showGroupInfo(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.info.group"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
         if (group == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
            return 0;
         } else {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("=== Group: " + group.getName() + " ==="), false);
            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(() -> MessageUtil.info("Prefix: " + (group.getPrefix() != null ? group.getPrefix() : "None")), false);
            ((CommandSourceStack)ctx.getSource())
               .sendSuccess(() -> MessageUtil.info("Suffix: " + (group.getSuffix() != null ? group.getSuffix() : "None")), false);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("Permissions (" + group.getPermissions().size() + "):"), false);
            if (group.getPermissions().isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - No permissions"), false);
            } else {
               group.getPermissions()
                  .stream()
                  .limit(10L)
                  .forEach(perm -> ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - " + perm), false));
               if (group.getPermissions().size() > 10) {
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.info("  ... and " + (group.getPermissions().size() - 10) + " more"), false);
               }
            }

            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("Inherits (" + group.getInherits().size() + "):"), false);
            if (group.getInherits().isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - No inheritance"), false);
            } else {
               group.getInherits().forEach(inherit -> ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - " + inherit), false));
            }

            return 1;
         }
      }
   }

   private static int showUserInfo(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.info.user"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String playerName = StringArgumentType.getString(ctx, "player");
         MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
         Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
         if (uuidOpt.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found", playerName));
            return 0;
         } else {
            UUID playerUUID = uuidOpt.get();
            PermissionUser user = PermissionAPI.getManager().getUser(playerUUID);
            if (user == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.user_not_found", playerName));
               return 0;
            } else {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("=== User: " + playerName + " ==="), false);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("UUID: " + playerUUID), false);
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.info("Group: " + (user.getGroup() != null ? user.getGroup() : "default")), false);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("Direct Permissions (" + user.getPermissions().size() + "):"), false);
               if (user.getPermissions().isEmpty()) {
                  ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - No direct permissions"), false);
               } else {
                  user.getPermissions()
                     .stream()
                     .limit(10L)
                     .forEach(perm -> ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - " + perm), false));
                  if (user.getPermissions().size() > 10) {
                     ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(() -> MessageUtil.info("  ... and " + (user.getPermissions().size() - 10) + " more"), false);
                  }
               }

               return 1;
            }
         }
      }
   }

   private static int checkUserPermission(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.check"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String playerName = StringArgumentType.getString(ctx, "player");
         String permission = StringArgumentType.getString(ctx, "permission");
         MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
         Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
         if (uuidOpt.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found", playerName));
            return 0;
         } else {
            UUID playerUUID = uuidOpt.get();
            boolean hasPermission = PermissionAPI.getManager().hasPermission(playerUUID, permission);
            if (hasPermission) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("✓ " + playerName + " has permission: " + permission), false);
            } else {
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.error("✗ " + playerName + " does NOT have permission: " + permission), false);
            }

            return 1;
         }
      }
   }

   private static int checkGroupPermission(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.check"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         String permission = StringArgumentType.getString(ctx, "permission");
         PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
         if (group == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
            return 0;
         } else {
            boolean hasPermission = group.getPermissions().contains(permission.toLowerCase());
            if (hasPermission) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("✓ Group '" + groupName + "' has permission: " + permission), false);
            } else {
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.error("✗ Group '" + groupName + "' does NOT have permission: " + permission), false);
            }

            return 1;
         }
      }
   }

   private static int searchPermissions(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.search"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String pattern = StringArgumentType.getString(ctx, "pattern").toLowerCase();

         try {
            List<String> allPermissions = ExternalPermissionProvider.getAllNeoEssentialsPermissions();
            List<String> matches = allPermissions.stream().filter(perm -> perm.toLowerCase().contains(pattern)).sorted().toList();
            if (matches.isEmpty()) {
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("No permissions found matching: " + pattern), false);
               return 1;
            } else {
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.success("Found " + matches.size() + " permissions matching '" + pattern + "':"), false);
               matches.stream().limit(20L).forEach(perm -> ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  - " + perm), false));
               if (matches.size() > 20) {
                  ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.info("  ... and " + (matches.size() - 20) + " more"), false);
               }

               return 1;
            }
         } catch (Exception var5) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to search permissions: " + var5.getMessage()));
            return 0;
         }
      }
   }

   private static int createGroup(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.create"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         PermissionManager manager = PermissionAPI.getManager();
         if (manager.getGroup(groupName) != null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + groupName + "' already exists!"));
            return 0;
         } else {
            PermissionGroup newGroup = new PermissionGroup(groupName);
            manager.addGroup(newGroup);
            manager.clearCache();

            try {
               PermissionStorage.save(manager);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("Created group: " + groupName), false);
               LOGGER.info("Created new permission group: {}", groupName);
               return 1;
            } catch (Exception var6) {
               LOGGER.error("Failed to save permissions after creating group", var6);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var6.getMessage()));
               return 0;
            }
         }
      }
   }

   private static int deleteGroup(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.delete"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         PermissionManager manager = PermissionAPI.getManager();
         if (manager.getGroup(groupName) == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + groupName + "' does not exist!"));
            return 0;
         } else if (groupName.equalsIgnoreCase(manager.getDefaultGroup())) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Cannot delete the default group!"));
            return 0;
         } else {
            manager.getGroups().removeIf(g -> g.getName().equalsIgnoreCase(groupName));
            manager.clearCache();

            try {
               PermissionStorage.save(manager);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("Deleted group: " + groupName), false);
               LOGGER.info("Deleted permission group: {}", groupName);
               return 1;
            } catch (Exception var5) {
               LOGGER.error("Failed to save permissions after deleting group", var5);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var5.getMessage()));
               return 0;
            }
         }
      }
   }

   private static int renameGroup(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.rename"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String oldName = StringArgumentType.getString(ctx, "oldName");
         String newName = StringArgumentType.getString(ctx, "newName");
         PermissionManager manager = PermissionAPI.getManager();
         PermissionGroup oldGroup = manager.getGroup(oldName);
         if (oldGroup == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + oldName + "' does not exist!"));
            return 0;
         } else if (manager.getGroup(newName) != null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + newName + "' already exists!"));
            return 0;
         } else {
            PermissionGroup newGroup = new PermissionGroup(newName);
            newGroup.setPrefix(oldGroup.getPrefix());
            newGroup.setSuffix(oldGroup.getSuffix());
            oldGroup.getPermissions().forEach(newGroup::addPermission);
            oldGroup.getInherits().forEach(newGroup::addInheritance);
            manager.getGroups().removeIf(g -> g.getName().equalsIgnoreCase(oldName));
            manager.addGroup(newGroup);
            manager.getUsers().stream().filter(u -> oldName.equalsIgnoreCase(u.getGroup())).forEach(u -> u.setGroup(newName));
            manager.clearCache();

            try {
               PermissionStorage.save(manager);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("Renamed group '" + oldName + "' to '" + newName + "'"), false);
               LOGGER.info("Renamed permission group '{}' to '{}'", oldName, newName);
               return 1;
            } catch (Exception var8) {
               LOGGER.error("Failed to save permissions after renaming group", var8);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var8.getMessage()));
               return 0;
            }
         }
      }
   }

   private static int cloneGroup(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.clone"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String sourceName = StringArgumentType.getString(ctx, "source");
         String newName = StringArgumentType.getString(ctx, "newGroup");
         PermissionManager manager = PermissionAPI.getManager();
         PermissionGroup sourceGroup = manager.getGroup(sourceName);
         if (sourceGroup == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + sourceName + "' does not exist!"));
            return 0;
         } else if (manager.getGroup(newName) != null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + newName + "' already exists!"));
            return 0;
         } else {
            PermissionGroup newGroup = new PermissionGroup(newName);
            newGroup.setPrefix(sourceGroup.getPrefix());
            newGroup.setSuffix(sourceGroup.getSuffix());
            sourceGroup.getPermissions().forEach(newGroup::addPermission);
            sourceGroup.getInherits().forEach(newGroup::addInheritance);
            manager.addGroup(newGroup);
            manager.clearCache();

            try {
               PermissionStorage.save(manager);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("Cloned group '" + sourceName + "' to '" + newName + "'"), false);
               LOGGER.info("Cloned permission group '{}' to '{}'", sourceName, newName);
               return 1;
            } catch (Exception var8) {
               LOGGER.error("Failed to save permissions after cloning group", var8);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var8.getMessage()));
               return 0;
            }
         }
      }
   }

   private static int clearGroupPermissions(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.clear"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         PermissionGroup group = PermissionAPI.getManager().getGroup(groupName);
         if (group == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
            return 0;
         } else {
            int count = group.getPermissions().size();
            group.getPermissions().clear();
            PermissionAPI.getManager().clearCache();

            try {
               PermissionStorage.save(PermissionAPI.getManager());
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.success("Cleared " + count + " permissions from group: " + groupName), false);
               LOGGER.info("Cleared all permissions from group '{}'", groupName);
               return 1;
            } catch (Exception var6) {
               LOGGER.error("Failed to save permissions after clearing group", var6);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var6.getMessage()));
               return 0;
            }
         }
      }
   }

   private static int clearUserPermissions(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.user.clear"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String playerName = StringArgumentType.getString(ctx, "player");
         MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
         Optional<UUID> uuidOpt = EconomyPlayerUtil.getUUIDByName(server, playerName);
         if (uuidOpt.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.player_not_found", playerName));
            return 0;
         } else {
            UUID playerUUID = uuidOpt.get();
            PermissionUser user = PermissionAPI.getManager().getUser(playerUUID);
            if (user == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.user_not_found", playerName));
               return 0;
            } else {
               int count = user.getPermissions().size();
               user.getPermissions().clear();
               PermissionAPI.getManager().clearCache();

               try {
                  PermissionStorage.save(PermissionAPI.getManager());
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("Cleared " + count + " permissions from user: " + playerName), false);
                  LOGGER.info("Cleared all permissions from user '{}'", playerName);
                  return 1;
               } catch (Exception var9) {
                  LOGGER.error("Failed to save permissions after clearing user", var9);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var9.getMessage()));
                  return 0;
               }
            }
         }
      }
   }

   private static int addGroupInheritance(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.inherit"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         String inheritGroup = StringArgumentType.getString(ctx, "inheritGroup");
         PermissionManager manager = PermissionAPI.getManager();
         PermissionGroup group = manager.getGroup(groupName);
         if (group == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
            return 0;
         } else {
            PermissionGroup targetGroup = manager.getGroup(inheritGroup);
            if (targetGroup == null) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Inherit group '" + inheritGroup + "' does not exist!"));
               return 0;
            } else if (groupName.equalsIgnoreCase(inheritGroup)) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("A group cannot inherit from itself!"));
               return 0;
            } else if (group.getInherits().contains(inheritGroup)) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + groupName + "' already inherits from '" + inheritGroup + "'!"));
               return 0;
            } else {
               group.addInheritance(inheritGroup);
               manager.clearCache();

               try {
                  PermissionStorage.save(manager);
                  ((CommandSourceStack)ctx.getSource())
                     .sendSuccess(() -> MessageUtil.success("Group '" + groupName + "' now inherits from '" + inheritGroup + "'"), false);
                  LOGGER.info("Added inheritance from '{}' to group '{}'", inheritGroup, groupName);
                  return 1;
               } catch (Exception var8) {
                  LOGGER.error("Failed to save permissions after adding inheritance", var8);
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var8.getMessage()));
                  return 0;
               }
            }
         }
      }
   }

   private static int removeGroupInheritance(CommandContext<CommandSourceStack> ctx) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validateAdminPermission(
         (CommandSourceStack)ctx.getSource(), "neoessentials.permissions.group.inherit"
      );
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         String groupName = StringArgumentType.getString(ctx, "group");
         String inheritGroup = StringArgumentType.getString(ctx, "inheritGroup");
         PermissionManager manager = PermissionAPI.getManager();
         PermissionGroup group = manager.getGroup(groupName);
         if (group == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.permissions.group_not_found", groupName));
            return 0;
         } else if (!group.getInherits().contains(inheritGroup)) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Group '" + groupName + "' does not inherit from '" + inheritGroup + "'!"));
            return 0;
         } else {
            group.removeInheritance(inheritGroup);
            manager.clearCache();

            try {
               PermissionStorage.save(manager);
               ((CommandSourceStack)ctx.getSource())
                  .sendSuccess(() -> MessageUtil.success("Removed inheritance of '" + inheritGroup + "' from group '" + groupName + "'"), false);
               LOGGER.info("Removed inheritance from '{}' from group '{}'", inheritGroup, groupName);
               return 1;
            } catch (Exception var7) {
               LOGGER.error("Failed to save permissions after removing inheritance", var7);
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("Failed to save: " + var7.getMessage()));
               return 0;
            }
         }
      }
   }
}
