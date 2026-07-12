package com.zerog.neoessentials.teleportation;

import com.mojang.brigadier.CommandDispatcher;
import com.zerog.neoessentials.commands.teleportation.HomeCommands;
import com.zerog.neoessentials.commands.teleportation.SpawnCommands;
import com.zerog.neoessentials.commands.teleportation.WarpCommands;
import com.zerog.neoessentials.teleportation.DirectTeleport.DirectTeleportCommands;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportCommands;
import com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestCommands;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeleportationRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger(TeleportationRegistry.class);

   public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      LOGGER.info("Registering teleportation commands...");

      try {
         HomeCommands.register(dispatcher);
         SpawnCommands.register(dispatcher);
         WarpCommands.register(dispatcher);
         TeleportRequestCommands.register(dispatcher);
         DirectTeleportCommands.register(dispatcher);
         MiscTeleportCommands.register(dispatcher);
         LOGGER.info("Successfully registered all teleportation commands!");
      } catch (Exception var2) {
         LOGGER.error("Failed to register teleportation commands", var2);
         throw new RuntimeException("Teleportation system initialization failed", var2);
      }
   }

   public static void initializeManagers() {
      LOGGER.info("Initializing teleportation managers...");

      try {
         LOGGER.info("Successfully initialized all teleportation managers!");
      } catch (Exception var1) {
         LOGGER.error("Failed to initialize teleportation managers", var1);
         throw new RuntimeException("Teleportation manager initialization failed", var1);
      }
   }

   public static void shutdown() {
      LOGGER.info("Shutting down teleportation systems...");

      try {
         LOGGER.info("Successfully shut down all teleportation systems!");
      } catch (Exception var1) {
         LOGGER.error("Error during teleportation system shutdown", var1);
      }
   }
}
