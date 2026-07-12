package com.zerog.neoessentials.teleportation;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class CombatEventHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(CombatEventHandler.class);

   private static boolean isCombatTarget(ServerPlayer attacker, Entity target) {
      if (target == null) {
         return false;
      } else if (!(target instanceof LivingEntity living)) {
         return false;
      } else {
         if (living instanceof TamableAnimal tame && tame.isTame()) {
            UUID ownerId = tame.getOwnerUUID();
            if (ownerId != null && ownerId.equals(attacker.getUUID())) {
               return false;
            }
         }

         String entityType = living.getType().toString();
         return entityType.contains("armor_stand") ? false : living instanceof Mob || living instanceof Player;
      }
   }

   @SubscribeEvent
   public static void onAttackEntity(AttackEntityEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (isCombatTarget(player, event.getTarget())) {
            CombatTracker.markInCombat(player);
            LOGGER.debug("Player {} entered combat by attacking {}", player.getName().getString(), event.getTarget().getName().getString());
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerDamage(Pre event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DamageSource source = event.getContainer().getSource();
         Entity attacker = source.getEntity();
         if (attacker instanceof LivingEntity livingAttacker) {
            if (!attacker.getUUID().equals(player.getUUID())) {
               if (livingAttacker instanceof TamableAnimal tame && tame.isTame()) {
                  UUID ownerId = tame.getOwnerUUID();
                  if (ownerId != null && ownerId.equals(player.getUUID())) {
                     return;
                  }
               }

               if (livingAttacker instanceof Mob || livingAttacker instanceof Player) {
                  CombatTracker.markInCombat(player);
                  LOGGER.debug("Player {} in combat by receiving damage from {}", player.getName().getString(), attacker.getName().getString());
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CombatTracker.clearCombat(player);
         LOGGER.debug("Cleared combat status for logging out player {}", player.getName().getString());
      }
   }
}
