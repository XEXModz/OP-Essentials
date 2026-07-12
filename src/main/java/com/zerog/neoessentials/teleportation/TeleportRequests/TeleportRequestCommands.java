package com.zerog.neoessentials.teleportation.TeleportRequests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeleportRequestCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(TeleportRequestCommands.class);
   private static final String PERMISSION_TPA = "neoessentials.teleport.request.tpa";
   private static final String PERMISSION_TPAHERE = "neoessentials.teleport.request.tpahere";
   private static final String PERMISSION_ACCEPT = "neoessentials.teleport.request.accept";
   private static final String PERMISSION_DENY = "neoessentials.teleport.request.deny";
   private static final String PERMISSION_CANCEL = "neoessentials.teleport.request.cancel";
   private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) -> SharedSuggestionProvider.suggest(
         ((CommandSourceStack)context.getSource()).getServer().getPlayerList().getPlayers().stream().map(player -> player.getName().getString()), builder
      );

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled()) {
         if (config.isCommandEnabled("tpa")) {
            dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpa")
                     .requires(
                        source -> {
                           if (source.getEntity() instanceof ServerPlayer player) {
                              boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.request.tpa");
                              LOGGER.info(
                                 "[TPA] Checking permission {} for {}: {}",
                                 new Object[]{"neoessentials.teleport.request.tpa", player.getName().getString(), hasPerm}
                              );
                              return hasPerm;
                           } else {
                              return false;
                           }
                        }
                     ))
                  .then(Commands.argument("player", EntityArgument.player()).suggests(ONLINE_PLAYERS).executes(context -> executeTpa(context)))
            );
         }

         if (config.isCommandEnabled("tpahere")) {
            dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpahere")
                     .requires(
                        source -> {
                           if (source.getEntity() instanceof ServerPlayer player) {
                              boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.request.tpahere");
                              LOGGER.info(
                                 "[TPAHERE] Checking permission {} for {}: {}",
                                 new Object[]{"neoessentials.teleport.request.tpahere", player.getName().getString(), hasPerm}
                              );
                              return hasPerm;
                           } else {
                              return false;
                           }
                        }
                     ))
                  .then(Commands.argument("player", EntityArgument.player()).suggests(ONLINE_PLAYERS).executes(context -> executeTpaHere(context)))
            );
         }

         if (config.isCommandEnabled("tpaccept")) {
            dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpaccept")
                     .requires(
                        source -> {
                           if (source.getEntity() instanceof ServerPlayer player) {
                              boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.request.accept");
                              LOGGER.info(
                                 "[TPACCEPT] Checking permission {} for {}: {}",
                                 new Object[]{"neoessentials.teleport.request.accept", player.getName().getString(), hasPerm}
                              );
                              return hasPerm;
                           } else {
                              return false;
                           }
                        }
                     ))
                  .executes(context -> executeTpAccept(context))
            );
         }

         if (config.isCommandEnabled("tpdeny")) {
            dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpdeny")
                     .requires(
                        source -> {
                           if (source.getEntity() instanceof ServerPlayer player) {
                              boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.request.deny");
                              LOGGER.info(
                                 "[TPDENY] Checking permission {} for {}: {}",
                                 new Object[]{"neoessentials.teleport.request.deny", player.getName().getString(), hasPerm}
                              );
                              return hasPerm;
                           } else {
                              return false;
                           }
                        }
                     ))
                  .executes(context -> executeTpDeny(context))
            );
         }

         if (config.isCommandEnabled("tpcancel")) {
            dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tpcancel")
                     .requires(
                        source -> {
                           if (source.getEntity() instanceof ServerPlayer player) {
                              boolean hasPerm = PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.request.cancel");
                              LOGGER.info(
                                 "[TPCANCEL] Checking permission {} for {}: {}",
                                 new Object[]{"neoessentials.teleport.request.cancel", player.getName().getString(), hasPerm}
                              );
                              return hasPerm;
                           } else {
                              return false;
                           }
                        }
                     ))
                  .executes(context -> executeTpCancel(context))
            );
         }

         LOGGER.info("Registered enabled teleport request commands");
      }
   }

   private static int executeTpa(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer requester = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         ServerPlayer target = EntityArgument.getPlayer(context, "player");
         if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.self"));
            return 0;
         } else {
            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            boolean success = manager.sendTeleportRequest(requester, target, TeleportRequestType.TPA);
            return success ? 1 : 0;
         }
      } catch (CommandSyntaxException var5) {
         LOGGER.error("Command syntax error in /tpa", var5);
         return 0;
      } catch (Exception var6) {
         LOGGER.error("Error executing /tpa command", var6);
         return 0;
      }
   }

   private static int executeTpaHere(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer requester = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         ServerPlayer target = EntityArgument.getPlayer(context, "player");
         if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.self"));
            return 0;
         } else {
            TeleportRequestManager manager = TeleportRequestManager.getInstance();
            boolean success = manager.sendTeleportRequest(requester, target, TeleportRequestType.TPAHERE);
            return success ? 1 : 0;
         }
      } catch (CommandSyntaxException var5) {
         LOGGER.error("Command syntax error in /tpahere", var5);
         return 0;
      } catch (Exception var6) {
         LOGGER.error("Error executing /tpahere command", var6);
         return 0;
      }
   }

   private static int executeTpAccept(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer teleportedPlayer = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         TeleportRequestManager manager = TeleportRequestManager.getInstance();
         MiscTeleportManager.getInstance().saveBackLocation(teleportedPlayer);
         boolean success = manager.acceptTeleportRequest(teleportedPlayer);
         return success ? 1 : 0;
      } catch (CommandSyntaxException var4) {
         LOGGER.error("Command syntax error in /tpaccept", var4);
         return 0;
      } catch (Exception var5) {
         LOGGER.error("Error executing /tpaccept command", var5);
         return 0;
      }
   }

   private static int executeTpDeny(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         TeleportRequestManager manager = TeleportRequestManager.getInstance();
         boolean success = manager.denyTeleportRequest(player);
         return success ? 1 : 0;
      } catch (CommandSyntaxException var4) {
         LOGGER.error("Command syntax error in /tpdeny", var4);
         return 0;
      } catch (Exception var5) {
         LOGGER.error("Error executing /tpdeny command", var5);
         return 0;
      }
   }

   private static int executeTpCancel(CommandContext<CommandSourceStack> context) {
      try {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         TeleportRequestManager manager = TeleportRequestManager.getInstance();
         boolean success = manager.cancelTeleportRequest(player);
         return success ? 1 : 0;
      } catch (CommandSyntaxException var4) {
         LOGGER.error("Command syntax error in /tpcancel", var4);
         return 0;
      } catch (Exception var5) {
         LOGGER.error("Error executing /tpcancel command", var5);
         return 0;
      }
   }
}
