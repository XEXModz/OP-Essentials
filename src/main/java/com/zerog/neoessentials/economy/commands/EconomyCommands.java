package com.zerog.neoessentials.economy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.zerog.neoessentials.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EconomyCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(EconomyCommands.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      ConfigManager config = ConfigManager.getInstance();
      if (!ConfigManager.isEconomyEnabled()) {
         LOGGER.info("Economy module is disabled, skipping economy command registration");
      } else {
         LOGGER.info("Registering economy commands...");
         if (config.isCommandEnabled("balance")) {
            try {
               BalanceCommand.register(dispatcher);
               LOGGER.info("Balance command registered");
            } catch (Exception var7) {
               LOGGER.error("Failed to register balance command", var7);
            }
         }

         if (config.isCommandEnabled("pay")) {
            try {
               PayCommand.register(dispatcher);
               LOGGER.info("Pay command registered");
            } catch (Exception var6) {
               LOGGER.error("Failed to register pay command", var6);
            }
         }

         if (config.isCommandEnabled("paytoggle")) {
            try {
               PayToggleCommand.register(dispatcher);
               LOGGER.info("PayToggle command registered");
            } catch (Exception var5) {
               LOGGER.error("Failed to register paytoggle command", var5);
            }
         }

         if (config.isCommandEnabled("eco")) {
            try {
               EcoCommand.register(dispatcher);
               LOGGER.info("Eco command registered");
            } catch (Exception var4) {
               LOGGER.error("Failed to register eco command", var4);
            }
         }

         if (config.isCommandEnabled("baltop")) {
            try {
               BaltopCommand.register(dispatcher);
               LOGGER.info("Baltop command registered");
            } catch (Exception var3) {
               LOGGER.error("Failed to register baltop command", var3);
            }
         }

         LOGGER.info("All economy commands registration completed");
      }
   }
}
