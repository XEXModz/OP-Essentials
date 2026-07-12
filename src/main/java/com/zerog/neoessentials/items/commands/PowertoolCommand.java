package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowertoolCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(PowertoolCommand.class);
   private static final Map<UUID, Map<String, String>> POWERS = new HashMap<>();
   private static final Set<UUID> ptDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());

   public static boolean isPowertoolEnabled(UUID uuid) {
      return !ptDisabled.contains(uuid);
   }

   public static Map<String, String> getPlayerPowertools(UUID playerUUID) {
      Map<String, String> powers = POWERS.get(playerUUID);
      return powers != null ? Collections.unmodifiableMap(powers) : Collections.emptyMap();
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("powertool")) {
         registerPowertoolCommand(dispatcher, "powertool");
         registerPowertoolCommand(dispatcher, "ptool");
         registerPowertoolToggle(dispatcher);
      }
   }

   private static void registerPowertoolToggle(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("powertooltoggle").requires(cs -> cs.getEntity() instanceof ServerPlayer))
            .executes(
               ctx -> {
                  ServerPlayer player = (ServerPlayer)((CommandSourceStack)ctx.getSource()).getEntity();
                  if (player == null) {
                     return 0;
                  } else {
                     UUID uuid = player.getUUID();
                     boolean nowEnabled;
                     if (ptDisabled.contains(uuid)) {
                        ptDisabled.remove(uuid);
                        nowEnabled = true;
                     } else {
                        if (!POWERS.containsKey(uuid) || POWERS.get(uuid).isEmpty()) {
                           player.sendSystemMessage(MessageUtil.error("commands.neoessentials.powertooltoggle.none"));
                           return 0;
                        }

                        ptDisabled.add(uuid);
                        nowEnabled = false;
                     }

                     player.sendSystemMessage(
                        MessageUtil.success(nowEnabled ? "commands.neoessentials.powertooltoggle.enabled" : "commands.neoessentials.powertooltoggle.disabled")
                     );
                     LOGGER.info("Player {} {} all powertools via /powertooltoggle", player.getName().getString(), nowEnabled ? "enabled" : "disabled");
                     return 1;
                  }
               }
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ptt").requires(cs -> cs.getEntity() instanceof ServerPlayer))
            .executes(
               ctx -> {
                  CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                  ServerPlayer player = (ServerPlayer)src.getEntity();
                  if (player == null) {
                     return 0;
                  } else {
                     UUID uuid = player.getUUID();
                     boolean nowEnabled;
                     if (ptDisabled.contains(uuid)) {
                        ptDisabled.remove(uuid);
                        nowEnabled = true;
                     } else {
                        ptDisabled.add(uuid);
                        nowEnabled = false;
                     }

                     player.sendSystemMessage(
                        MessageUtil.success(nowEnabled ? "commands.neoessentials.powertooltoggle.enabled" : "commands.neoessentials.powertooltoggle.disabled")
                     );
                     return 1;
                  }
               }
            )
      );
   }

   private static void registerPowertoolCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
                     .requires(cs -> cs.getEntity() instanceof ServerPlayer))
                  .then(
                     Commands.literal("list")
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 ServerPlayer player = permResult.getPlayer();
                                 return listPowertools(player);
                              }
                           }
                        )
                  ))
               .then(
                  Commands.literal("remove")
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              ServerPlayer player = permResult.getPlayer();
                              ItemStack heldItem = player.getMainHandItem();
                              if (heldItem.isEmpty()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.no_item"));
                                 return 0;
                              } else {
                                 String itemId = getItemId(heldItem);
                                 if (removePowertool(player.getUUID(), itemId)) {
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> MessageUtil.success("commands.neoessentials.powertool.remove.success"), false);
                                    return 1;
                                 } else {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.remove.not_found"));
                                    return 0;
                                 }
                              }
                           }
                        }
                     )
               ))
            .then(
               Commands.argument("command", StringArgumentType.greedyString())
                  .executes(
                     ctx -> {
                        PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                           (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                        );
                        if (!permResult.hasPermission()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                           return 0;
                        } else {
                           ServerPlayer player = permResult.getPlayer();
                           String cmd = StringArgumentType.getString(ctx, "command");
                           if (cmd.startsWith("@p ")) {
                              return executeTargetCommand((CommandSourceStack)ctx.getSource(), player, cmd.substring(3));
                           } else {
                              ItemStack heldItem = player.getMainHandItem();
                              if (heldItem.isEmpty()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.no_item"));
                                 return 0;
                              } else {
                                 InputValidator.ValidationResult cmdValidation = InputValidator.validateCommand(cmd);
                                 if (!cmdValidation.isValid()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(cmdValidation.getErrorMessage()));
                                    return 0;
                                 } else {
                                    String validCommand = cmdValidation.getValue(String.class);
                                    assign(player, validCommand);
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> MessageUtil.success("commands.neoessentials.powertool.assign.success"), false);
                                    return 1;
                                 }
                              }
                           }
                        }
                     }
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("pt")
                     .requires(cs -> cs.getEntity() instanceof ServerPlayer))
                  .then(
                     Commands.literal("list")
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 ServerPlayer player = permResult.getPlayer();
                                 return listPowertools(player);
                              }
                           }
                        )
                  ))
               .then(
                  Commands.literal("remove")
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              ServerPlayer player = permResult.getPlayer();
                              ItemStack heldItem = player.getMainHandItem();
                              if (heldItem.isEmpty()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.no_item"));
                                 return 0;
                              } else {
                                 String itemId = getItemId(heldItem);
                                 if (removePowertool(player.getUUID(), itemId)) {
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> MessageUtil.success("commands.neoessentials.powertool.remove.success"), false);
                                    return 1;
                                 } else {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.remove.not_found"));
                                    return 0;
                                 }
                              }
                           }
                        }
                     )
               ))
            .then(
               Commands.argument("command", StringArgumentType.greedyString())
                  .executes(
                     ctx -> {
                        PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                           (CommandSourceStack)ctx.getSource(), "neoessentials.item.powertool"
                        );
                        if (!permResult.hasPermission()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                           return 0;
                        } else {
                           ServerPlayer player = permResult.getPlayer();
                           String cmd = StringArgumentType.getString(ctx, "command");
                           if (cmd.startsWith("@p ")) {
                              return executeTargetCommand((CommandSourceStack)ctx.getSource(), player, cmd.substring(3));
                           } else {
                              ItemStack heldItem = player.getMainHandItem();
                              if (heldItem.isEmpty()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.powertool.no_item"));
                                 return 0;
                              } else {
                                 InputValidator.ValidationResult cmdValidation = InputValidator.validateCommand(cmd);
                                 if (!cmdValidation.isValid()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(cmdValidation.getErrorMessage()));
                                    return 0;
                                 } else {
                                    String validCommand = cmdValidation.getValue(String.class);
                                    assign(player, validCommand);
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> MessageUtil.success("commands.neoessentials.powertool.assign.success"), false);
                                    return 1;
                                 }
                              }
                           }
                        }
                     }
                  )
            )
      );
   }

   private static String getItemId(ItemStack itemStack) {
      ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
      return itemKey.toString();
   }

   public static void assign(ServerPlayer player, String command) {
      ItemStack heldItem = player.getMainHandItem();
      String itemId = getItemId(heldItem);
      POWERS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(itemId, command);
      LOGGER.info("Player {} assigned powertool command '{}' to item {}", new Object[]{player.getName().getString(), command, itemId});
   }

   private static int listPowertools(ServerPlayer player) {
      Map<String, String> playerPowers = POWERS.get(player.getUUID());
      if (playerPowers != null && !playerPowers.isEmpty()) {
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.powertool.list.header"));

         for (Entry<String, String> entry : playerPowers.entrySet()) {
            String itemId = entry.getKey();
            String command = entry.getValue();
            String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(":") + 1) : itemId;
            player.sendSystemMessage(Component.literal("  §e" + itemName + "§r: §7" + command));
         }

         return 1;
      } else {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.powertool.list.empty"));
         return 0;
      }
   }

   public static boolean hasPowertoolData(UUID playerUUID) {
      return POWERS.containsKey(playerUUID) && !POWERS.get(playerUUID).isEmpty();
   }

   public static String getPowertoolCommand(UUID playerUUID, String itemId) {
      Map<String, String> playerPowers = POWERS.get(playerUUID);
      return playerPowers != null ? playerPowers.get(itemId) : null;
   }

   public static boolean removePowertool(UUID playerUUID, String itemId) {
      Map<String, String> playerPowers = POWERS.get(playerUUID);
      if (playerPowers != null) {
         String removed = playerPowers.remove(itemId);
         if (playerPowers.isEmpty()) {
            POWERS.remove(playerUUID);
         }

         return removed != null;
      } else {
         return false;
      }
   }

   private static int executeTargetCommand(CommandSourceStack source, ServerPlayer executor, String command) {
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(source, "neoessentials.command.target");
      if (!permResult.hasPermission()) {
         source.sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         InputValidator.ValidationResult cmdValidation = InputValidator.validateCommand(command);
         if (!cmdValidation.isValid()) {
            source.sendFailure(MessageUtil.error(cmdValidation.getErrorMessage()));
            return 0;
         } else {
            String validCommand = cmdValidation.getValue(String.class);
            List<ServerPlayer> targets = new ArrayList<>();

            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
               if (!player.getUUID().equals(executor.getUUID())) {
                  targets.add(player);
               }
            }

            if (targets.isEmpty()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.pt.target.no_targets"));
               return 0;
            } else {
               int[] successCount = new int[]{0};

               for (ServerPlayer target : targets) {
                  try {
                     String targetCommand = validCommand.replace("{player}", target.getName().getString());
                     source.getServer().getCommands().performPrefixedCommand(source.getServer().createCommandSourceStack(), targetCommand);
                     successCount[0]++;
                  } catch (Exception var11) {
                     LOGGER.warn(
                        "Failed to execute target command '{}' for player {}: {}", new Object[]{validCommand, target.getName().getString(), var11.getMessage()}
                     );
                  }
               }

               if (successCount[0] > 0) {
                  LOGGER.info(
                     "Player {} executed target command '{}' on {}/{} players successfully",
                     new Object[]{executor.getName().getString(), validCommand, successCount[0], targets.size()}
                  );
                  source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.pt.target.success", successCount[0], targets.size()), false);
                  return 1;
               } else {
                  source.sendFailure(MessageUtil.error("commands.neoessentials.pt.target.failed"));
                  return 0;
               }
            }
         }
      }
   }
}
