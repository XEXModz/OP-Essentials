package com.zerog.neoessentials.commands.teleportation;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.teleportation.Spawn.SpawnManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SpawnCommands {
   private static final String PERMISSION_SPAWN = "neoessentials.teleport.spawn";
   private static final String PERMISSION_SETSPAWN = "neoessentials.teleport.spawn.set";
   private static final String PERMISSION_SPAWNINFO = "neoessentials.teleport.spawn.info";
   private static final Map<UUID, Long> lastSpawnUsage = new HashMap<>();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (config.isTeleportationEnabled()) {
         if (config.isCommandEnabled("spawn")) {
            registerSpawnCommand(dispatcher);
            registerSpawnInfoCommand(dispatcher);
         }

         if (config.isCommandEnabled("setspawn")) {
            boolean allowSpawnSet = true;

            try {
               JsonObject mainConfig = config.getConfig("config.json");
               if (mainConfig.has("teleportation")) {
                  JsonObject tp = mainConfig.getAsJsonObject("teleportation");
                  if (tp.has("spawnSettings")) {
                     JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                     if (spawnSettings.has("allowSpawnSet")) {
                        allowSpawnSet = spawnSettings.get("allowSpawnSet").getAsBoolean();
                     }
                  }
               }
            } catch (Exception var6) {
               allowSpawnSet = true;
            }

            if (allowSpawnSet) {
               registerSetSpawnCommand(dispatcher);
            }
         }
      }
   }

   private static void registerSpawnCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerSpawnCommandWithName(dispatcher, "spawn");
      registerSpawnCommandWithName(dispatcher, "hub");
   }

   private static void registerSpawnCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer player
                        ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.spawn")
                        : source.hasPermission(2)
               ))
            .executes(SpawnCommands::executeSpawn)
      );
   }

   private static void registerSetSpawnCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("setspawn")
                  .requires(
                     source -> source.getEntity() instanceof ServerPlayer player
                           ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.spawn.set")
                           : source.hasPermission(3)
                  ))
               .executes(SpawnCommands::executeSetSpawnHere))
            .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(SpawnCommands::executeSetSpawnAt))
      );
   }

   private static void registerSpawnInfoCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerSpawnInfoCommandWithName(dispatcher, "spawninfo");
      registerSpawnInfoCommandWithName(dispatcher, "spawni");
   }

   private static void registerSpawnInfoCommandWithName(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName)
               .requires(
                  source -> source.getEntity() instanceof ServerPlayer player
                        ? PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.spawn.info")
                        : source.hasPermission(2)
               ))
            .executes(SpawnCommands::executeSpawnInfo)
      );
   }

   private static int executeSpawn(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      SpawnManager spawnManager = SpawnManager.getInstance();
      ConfigManager config = ConfigManager.getInstance();
      JailManager jailManager = JailManager.getInstance();
      if (config.isPreventJailEscapeEnabled() && jailManager.isPlayerJailed(player.getUUID())) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.jail.prevent_escape"));
         return 0;
      } else {
         int cooldown = 0;

         try {
            JsonObject mainConfig = config.getConfig("config.json");
            if (mainConfig.has("teleportation")) {
               JsonObject tp = mainConfig.getAsJsonObject("teleportation");
               if (tp.has("spawnSettings")) {
                  JsonObject spawnSettings = tp.getAsJsonObject("spawnSettings");
                  if (spawnSettings.has("spawnCooldown")) {
                     cooldown = spawnSettings.get("spawnCooldown").getAsInt();
                  }
               }
            }
         } catch (Exception var12) {
            cooldown = 0;
         }

         if (cooldown > 0) {
            long now = System.currentTimeMillis() / 1000L;
            long last = lastSpawnUsage.getOrDefault(player.getUUID(), 0L);
            long remaining = (long)cooldown - (now - last);
            if (remaining > 0L) {
               player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.spawn.cooldown", String.valueOf(remaining)));
               return 0;
            }

            lastSpawnUsage.put(player.getUUID(), now);
         }

         spawnManager.teleportToSpawn(player);
         return 1;
      }
   }

   private static int executeSetSpawnHere(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      SpawnManager spawnManager = SpawnManager.getInstance();
      if (!hasSetSpawnPermission(player)) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("teleport.spawn.no_permission"));
         return 0;
      } else {
         return spawnManager.setSpawn(player) ? 1 : 0;
      }
   }

   private static int executeSetSpawnAt(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      SpawnManager spawnManager = SpawnManager.getInstance();
      if (!hasSetSpawnPermission(player)) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("teleport.spawn.no_permission"));
         return 0;
      } else {
         try {
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            ServerLevel level = player.serverLevel();
            if (spawnManager.setSpawn(player, level, pos)) {
               return 1;
            }
         } catch (Exception var5) {
            ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("teleport.spawn.invalid_coordinates"));
         }

         return 0;
      }
   }

   private static int executeSpawnInfo(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = (ServerPlayer)((CommandSourceStack)context.getSource()).getEntity();
      SpawnManager spawnManager = SpawnManager.getInstance();
      String info = spawnManager.getSpawnInfo();
      player.sendSystemMessage(MessageUtil.info(info));
      if (hasSetSpawnPermission(player)) {
         String stats = spawnManager.getStatistics();
         player.sendSystemMessage(MessageUtil.component(stats));
      }

      return 1;
   }

   private static boolean hasSetSpawnPermission(ServerPlayer player) {
      return PermissionAPI.hasPermission(player.getUUID(), "neoessentials.teleport.spawn.set");
   }
}
