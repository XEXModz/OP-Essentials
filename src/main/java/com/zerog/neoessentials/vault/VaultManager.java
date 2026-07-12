package com.zerog.neoessentials.vault;

import com.zerog.neoessentials.vault.api.VaultChat;
import com.zerog.neoessentials.vault.api.VaultEconomy;
import com.zerog.neoessentials.vault.api.VaultPermission;
import com.zerog.neoessentials.vault.api.VaultServiceRegistry;
import com.zerog.neoessentials.vault.impl.NeoEssentialsChat;
import com.zerog.neoessentials.vault.impl.NeoEssentialsEconomy;
import com.zerog.neoessentials.vault.impl.NeoEssentialsPermission;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(VaultManager.class);
   private static boolean initialised = false;

   public static void initialize() {
      if (initialised) {
         LOGGER.warn("[VaultAPI] VaultManager.initialize() called more than once — skipping");
      } else {
         LOGGER.info("[VaultAPI] Initialising NeoEssentials Vault API...");
         VaultServiceRegistry registry = VaultServiceRegistry.getInstance();

         try {
            NeoEssentialsEconomy economy = new NeoEssentialsEconomy();
            registry.registerEconomy(economy, VaultServiceRegistry.ServicePriority.NORMAL, "neoessentials");
         } catch (Exception var4) {
            LOGGER.error("[VaultAPI] Failed to register Economy provider: {}", var4.getMessage(), var4);
         }

         try {
            NeoEssentialsPermission permission = new NeoEssentialsPermission();
            registry.registerPermission(permission, VaultServiceRegistry.ServicePriority.NORMAL, "neoessentials");
         } catch (Exception var3) {
            LOGGER.error("[VaultAPI] Failed to register Permission provider: {}", var3.getMessage(), var3);
         }

         try {
            NeoEssentialsChat chat = new NeoEssentialsChat();
            registry.registerChat(chat, VaultServiceRegistry.ServicePriority.NORMAL, "neoessentials");
         } catch (Exception var2) {
            LOGGER.error("[VaultAPI] Failed to register Chat provider: {}", var2.getMessage(), var2);
         }

         registry.logStatus();
         initialised = true;
         LOGGER.info("[VaultAPI] Vault API ready.");
      }
   }

   public static void shutdown() {
      VaultServiceRegistry.getInstance().clear();
      initialised = false;
      LOGGER.info("[VaultAPI] Vault API shut down.");
   }

   public static Optional<VaultEconomy> getEconomy() {
      return VaultServiceRegistry.getInstance().getEconomy();
   }

   public static Optional<VaultPermission> getPermission() {
      return VaultServiceRegistry.getInstance().getPermission();
   }

   public static Optional<VaultChat> getChat() {
      return VaultServiceRegistry.getInstance().getChat();
   }

   private VaultManager() {
   }
}
