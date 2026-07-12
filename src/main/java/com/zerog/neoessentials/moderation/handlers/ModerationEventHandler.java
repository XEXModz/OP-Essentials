package com.zerog.neoessentials.moderation.handlers;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.FreezeManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class ModerationEventHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(ModerationEventHandler.class);
   private static int tickCounter = 0;

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            if (!JailManager.isJailSystemEnabled()) {
               return;
            }

            JailManager jailManager = JailManager.getInstance();
            UUID playerId = player.getUUID();
            if (jailManager.checkJailTimeout(playerId)) {
               player.sendSystemMessage(MessageUtil.success("commands.neoessentials.jail.released_expired"));
               return;
            }

            jailManager.onPlayerJoin(player);
         } catch (Exception var4) {
            LOGGER.error("Error handling jail on player login", var4);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPlayerMove(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         redirectJailedPlayer(player, "dimension change");
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            if (!JailManager.isJailSystemEnabled()) {
               return;
            }

            JailManager jailManager = JailManager.getInstance();
            UUID playerId = player.getUUID();
            if (!jailManager.isPlayerJailed(playerId)) {
               return;
            }

            JailManager.JailEntry jailEntry = jailManager.getJailEntry(playerId);
            JailManager.JailLocation jailLoc = jailManager.getJailLocation(jailEntry.jailName);
            if (jailLoc == null) {
               return;
            }

            MinecraftServer server = player.getServer();
            if (server == null) {
               return;
            }

            ServerLevel jailLevel = server.getLevel(
               ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(jailLoc.dimension != null ? jailLoc.dimension : "minecraft:overworld"))
            );
            if (jailLevel == null) {
               return;
            }

            server.tell(
               new TickTask(
                  server.getTickCount() + 1,
                  () -> {
                     if (player.isAlive()) {
                        player.teleportTo(
                           jailLevel,
                           (double)jailLoc.position.getX() + 0.5,
                           (double)(jailLoc.position.getY() + 1),
                           (double)jailLoc.position.getZ() + 0.5,
                           player.getYRot(),
                           player.getXRot()
                        );
                        player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.jail.message"));
                     }
                  }
               )
            );
         } catch (Exception var8) {
            LOGGER.error("Error redirecting jailed player respawn", var8);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPlayerRightClick(RightClickItem event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            UUID playerId = player.getUUID();
            if (ConfigManager.isFreezeSystemEnabled() && FreezeManager.getInstance().isPlayerFrozen(playerId)) {
               event.setCanceled(true);
               return;
            }

            if (JailManager.isJailSystemEnabled()
               && JailManager.getInstance().isPlayerJailed(playerId)
               && !PermissionAPI.hasPermission(playerId, "neoessentials.jail.allow-interact")) {
               event.setCanceled(true);
               return;
            }

            if (ConfigManager.isVanishPreventInteractionEnabled()) {
               VanishManager vanishManager = VanishManager.getInstance();
               if (vanishManager.isPlayerVanished(playerId)) {
                  String seePerm = ConfigManager.getInstance().getSeeVanishedPermission();
                  if (!PermissionAPI.hasPermission(playerId, seePerm)) {
                     event.setCanceled(true);
                  }
               }
            }
         } catch (Exception var5) {
            LOGGER.error("Error handling player interaction", var5);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPlayerRightClickBlock(RightClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            UUID playerId = player.getUUID();
            if (ConfigManager.isFreezeSystemEnabled() && FreezeManager.getInstance().isPlayerFrozen(playerId)) {
               event.setCanceled(true);
               return;
            }

            if (JailManager.isJailSystemEnabled()
               && JailManager.getInstance().isPlayerJailed(playerId)
               && !PermissionAPI.hasPermission(playerId, "neoessentials.jail.allow-interact")) {
               event.setCanceled(true);
            }
         } catch (Exception var3) {
            LOGGER.error("Error handling right-click block for jail/freeze", var3);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onLivingAttack(Pre event) {
      if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
         try {
            if (!JailManager.isJailSystemEnabled()) {
               return;
            }

            if (JailManager.getInstance().isPlayerJailed(attacker.getUUID())
               && !PermissionAPI.hasPermission(attacker.getUUID(), "neoessentials.jail.allow-attack")) {
               event.setNewDamage(0.0F);
            }
         } catch (Exception var3) {
            LOGGER.error("Error handling attack for jailed player", var3);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onBlockBreak(BreakEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         try {
            UUID playerId = player.getUUID();
            if (ConfigManager.isFreezeSystemEnabled() && FreezeManager.getInstance().isPlayerFrozen(playerId)) {
               event.setCanceled(true);
               return;
            }

            if (JailManager.isJailSystemEnabled()
               && JailManager.getInstance().isPlayerJailed(playerId)
               && !PermissionAPI.hasPermission(playerId, "neoessentials.jail.allow-break")) {
               event.setCanceled(true);
               return;
            }

            if (ConfigManager.isVanishPreventInteractionEnabled()) {
               VanishManager vanishManager = VanishManager.getInstance();
               if (vanishManager.isPlayerVanished(playerId)) {
                  String seePerm = ConfigManager.getInstance().getSeeVanishedPermission();
                  if (!PermissionAPI.hasPermission(playerId, seePerm)) {
                     event.setCanceled(true);
                  }
               }
            }
         } catch (Exception var5) {
            LOGGER.error("Error handling block break for moderation", var5);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onBlockPlace(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         try {
            UUID playerId = player.getUUID();
            if (ConfigManager.isFreezeSystemEnabled() && FreezeManager.getInstance().isPlayerFrozen(playerId)) {
               event.setCanceled(true);
               return;
            }

            if (JailManager.isJailSystemEnabled()
               && JailManager.getInstance().isPlayerJailed(playerId)
               && !PermissionAPI.hasPermission(playerId, "neoessentials.jail.allow-place")) {
               event.setCanceled(true);
               return;
            }

            if (ConfigManager.isVanishPreventInteractionEnabled()) {
               VanishManager vanishManager = VanishManager.getInstance();
               if (vanishManager.isPlayerVanished(playerId)) {
                  String seePerm = ConfigManager.getInstance().getSeeVanishedPermission();
                  if (!PermissionAPI.hasPermission(playerId, seePerm)) {
                     event.setCanceled(true);
                  }
               }
            }
         } catch (Exception var5) {
            LOGGER.error("Error handling block place for moderation", var5);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.NORMAL
   )
   public static void onServerTick(Post event) {
      if (++tickCounter >= 20) {
         tickCounter = 0;
         if (JailManager.isJailSystemEnabled()) {
            JailManager jailManager = JailManager.getInstance();

            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
               try {
                  UUID playerId = player.getUUID();
                  if (jailManager.isPlayerJailed(playerId)) {
                     if (jailManager.checkJailTimeout(playerId)) {
                        player.sendSystemMessage(MessageUtil.success("commands.neoessentials.jail.released_expired"));
                     } else {
                        BlockPos currentPos = player.blockPosition();
                        if (!jailManager.canPlayerMove(player, currentPos)) {
                           redirectJailedPlayer(player, "movement");
                        }
                     }
                  }
               } catch (Exception var6) {
                  LOGGER.error("Error enforcing jail for player {}", player.getName().getString(), var6);
               }
            }
         }
      }
   }

   private static void redirectJailedPlayer(ServerPlayer player, String reason) {
      try {
         if (!JailManager.isJailSystemEnabled()) {
            return;
         }

         JailManager jailManager = JailManager.getInstance();
         UUID playerId = player.getUUID();
         if (!jailManager.isPlayerJailed(playerId)) {
            return;
         }

         JailManager.JailEntry jailEntry = jailManager.getJailEntry(playerId);
         if (jailEntry == null) {
            return;
         }

         JailManager.JailLocation jailLoc = jailManager.getJailLocation(jailEntry.jailName);
         if (jailLoc == null) {
            return;
         }

         MinecraftServer server = player.getServer();
         if (server == null) {
            return;
         }

         ResourceLocation dimId = ResourceLocation.tryParse(jailLoc.dimension != null ? jailLoc.dimension : "minecraft:overworld");
         ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
         if (level == null) {
            level = server.overworld();
         }

         player.teleportTo(
            level,
            (double)jailLoc.position.getX() + 0.5,
            (double)(jailLoc.position.getY() + 1),
            (double)jailLoc.position.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
         );
         player.sendSystemMessage(MessageUtil.warning("commands.neoessentials.jail.escape_prevented"));
         LOGGER.debug("Jailed player {} redirected back to jail ({}).", player.getName().getString(), reason);
      } catch (Exception var9) {
         LOGGER.error("Error redirecting jailed player", var9);
      }
   }
}
