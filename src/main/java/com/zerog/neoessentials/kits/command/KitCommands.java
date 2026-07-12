package com.zerog.neoessentials.kits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.kits.KitManager;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(KitCommands.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (!ConfigManager.isKitModuleEnabled()) {
         LOGGER.info("Kits module is disabled, skipping kit command registration");
      } else {
         try {
            KitManager.getInstance().initialize();
            LOGGER.info("KitManager initialized successfully");
         } catch (Exception var8) {
            LOGGER.error("Failed to initialize KitManager: {}", var8.getMessage(), var8);
         }

         LOGGER.info("Registering kit commands...");
         if (config.isCommandEnabled("kit")) {
            try {
               KitCommand.register(dispatcher);
               LOGGER.info("Kit command registered");
            } catch (Exception var7) {
               LOGGER.error("Failed to register kit command", var7);
            }
         }

         if (config.isCommandEnabled("createkit")) {
            try {
               CreateKitCommand.register(dispatcher);
               LOGGER.info("CreateKit command registered");
            } catch (Exception var6) {
               LOGGER.error("Failed to register createkit command", var6);
            }
         }

         if (config.isCommandEnabled("delkit")) {
            try {
               DelKitCommand.register(dispatcher);
               LOGGER.info("DelKit command registered");
            } catch (Exception var5) {
               LOGGER.error("Failed to register delkit command", var5);
            }
         }

         if (config.isCommandEnabled("listkits")) {
            try {
               ListKitsCommand.register(dispatcher);
               LOGGER.info("ListKits command registered");
            } catch (Exception var4) {
               LOGGER.error("Failed to register listkits command", var4);
            }
         }

         if (config.isCommandEnabled("kitreset")) {
            try {
               KitResetCommand.register(dispatcher);
               LOGGER.info("KitReset command registered");
            } catch (Exception var3) {
               LOGGER.error("Failed to register kitreset command", var3);
            }
         }

         LOGGER.info("All kit commands registration completed");
      }
   }
}
