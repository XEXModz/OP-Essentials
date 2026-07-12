package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public class SuicideCommand {
   private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, Long> CONFIRMATIONS = new HashMap<>();
   private static final long CONFIRMATION_TIMEOUT = 10000L;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("suicide")) {
         registerSuicideCommand(dispatcher, "suicide");
         registerSuicideCommand(dispatcher, "kill");
         registerSuicideCommand(dispatcher, "die");
      }
   }

   private static void registerSuicideCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .then(
                  Commands.literal("confirm")
                     .executes(
                        ctx -> {
                           ServerPlayer player = CommandSourceHelper.requirePlayer(
                              (CommandSourceStack)ctx.getSource(), "commands.neoessentials.suicide.player_only"
                           );
                           if (player == null) {
                              return 0;
                           } else {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.suicide"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 return confirmSuicide(player);
                              }
                           }
                        }
                     )
               ))
            .executes(
               ctx -> {
                  ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.suicide.player_only");
                  if (player == null) {
                     return 0;
                  } else {
                     PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                        (CommandSourceStack)ctx.getSource(), "neoessentials.suicide"
                     );
                     if (!permResult.hasPermission()) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                     } else {
                        return initiateSuicide(player);
                     }
                  }
               }
            )
      );
   }

   private static int initiateSuicide(ServerPlayer player) {
      UUID playerId = player.getUUID();
      if (COOLDOWNS.containsKey(playerId)) {
         long timeLeft = (COOLDOWNS.get(playerId) - System.currentTimeMillis()) / 1000L;
         if (timeLeft > 0L) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.suicide.cooldown", timeLeft));
            return 0;
         }

         COOLDOWNS.remove(playerId);
      }

      if (!player.isCreative() && !player.isSpectator()) {
         CONFIRMATIONS.put(playerId, System.currentTimeMillis() + 10000L);
         player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.suicide.confirmation"));
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.suicide.confirm_instructions"));
         return 1;
      } else {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.suicide.invalid_gamemode"));
         return 0;
      }
   }

   private static int confirmSuicide(ServerPlayer player) {
      UUID playerId = player.getUUID();
      if (!CONFIRMATIONS.containsKey(playerId)) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.suicide.no_confirmation"));
         return 0;
      } else {
         long confirmationTime = CONFIRMATIONS.get(playerId);
         if (System.currentTimeMillis() > confirmationTime) {
            CONFIRMATIONS.remove(playerId);
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.suicide.confirmation_expired"));
            return 0;
         } else {
            CONFIRMATIONS.remove(playerId);
            long cooldownTime = 30000L;
            COOLDOWNS.put(playerId, System.currentTimeMillis() + cooldownTime);
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.suicide.executing"));

            try {
               DamageSource suicideDamage = new DamageSource(
                  player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL)
               );
               player.hurt(suicideDamage, Float.MAX_VALUE);
               if (player.isAlive()) {
                  player.setHealth(0.0F);
               }

               return 1;
            } catch (Exception var7) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.suicide.failed"));
               return 0;
            }
         }
      }
   }

   public static void cleanupExpiredConfirmations() {
      long currentTime = System.currentTimeMillis();
      CONFIRMATIONS.entrySet().removeIf(entry -> currentTime > entry.getValue());
      COOLDOWNS.entrySet().removeIf(entry -> currentTime > entry.getValue());
   }
}
