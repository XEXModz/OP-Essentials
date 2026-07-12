package com.zerog.neoessentials.vault.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VaultServiceRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger(VaultServiceRegistry.class);
   private static final VaultServiceRegistry INSTANCE = new VaultServiceRegistry();
   private final List<VaultServiceRegistry.Registration<VaultEconomy>> economyProviders = new ArrayList<>();
   private final List<VaultServiceRegistry.Registration<VaultPermission>> permissionProviders = new ArrayList<>();
   private final List<VaultServiceRegistry.Registration<VaultChat>> chatProviders = new ArrayList<>();

   public static VaultServiceRegistry getInstance() {
      return INSTANCE;
   }

   private VaultServiceRegistry() {
   }

   public void registerEconomy(VaultEconomy economy, VaultServiceRegistry.ServicePriority priority, String registrant) {
      this.economyProviders.add(new VaultServiceRegistry.Registration<>(economy, priority, registrant));
      this.economyProviders.sort(Comparator.<VaultServiceRegistry.Registration<VaultEconomy>>comparingInt(r -> r.priority.value).reversed());
      LOGGER.info("[VaultAPI] Economy provider registered: {} (priority={}, by={})", new Object[]{economy.getName(), priority, registrant});
   }

   public void registerEconomy(VaultEconomy economy) {
      this.registerEconomy(economy, VaultServiceRegistry.ServicePriority.NORMAL, "unknown");
   }

   public Optional<VaultEconomy> getEconomy() {
      return this.economyProviders.stream().filter(r -> r.provider.isEnabled()).map(r -> r.provider).findFirst();
   }

   public List<VaultServiceRegistry.Registration<VaultEconomy>> getEconomyProviders() {
      return List.copyOf(this.economyProviders);
   }

   public void registerPermission(VaultPermission permission, VaultServiceRegistry.ServicePriority priority, String registrant) {
      this.permissionProviders.add(new VaultServiceRegistry.Registration<>(permission, priority, registrant));
      this.permissionProviders.sort(Comparator.<VaultServiceRegistry.Registration<VaultPermission>>comparingInt(r -> r.priority.value).reversed());
      LOGGER.info("[VaultAPI] Permission provider registered: {} (priority={}, by={})", new Object[]{permission.getName(), priority, registrant});
   }

   public void registerPermission(VaultPermission permission) {
      this.registerPermission(permission, VaultServiceRegistry.ServicePriority.NORMAL, "unknown");
   }

   public Optional<VaultPermission> getPermission() {
      return this.permissionProviders.stream().filter(r -> r.provider.isEnabled()).map(r -> r.provider).findFirst();
   }

   public List<VaultServiceRegistry.Registration<VaultPermission>> getPermissionProviders() {
      return List.copyOf(this.permissionProviders);
   }

   public void registerChat(VaultChat chat, VaultServiceRegistry.ServicePriority priority, String registrant) {
      this.chatProviders.add(new VaultServiceRegistry.Registration<>(chat, priority, registrant));
      this.chatProviders.sort(Comparator.<VaultServiceRegistry.Registration<VaultChat>>comparingInt(r -> r.priority.value).reversed());
      LOGGER.info("[VaultAPI] Chat provider registered: {} (priority={}, by={})", new Object[]{chat.getName(), priority, registrant});
   }

   public void registerChat(VaultChat chat) {
      this.registerChat(chat, VaultServiceRegistry.ServicePriority.NORMAL, "unknown");
   }

   public Optional<VaultChat> getChat() {
      return this.chatProviders.stream().filter(r -> r.provider.isEnabled()).map(r -> r.provider).findFirst();
   }

   public List<VaultServiceRegistry.Registration<VaultChat>> getChatProviders() {
      return List.copyOf(this.chatProviders);
   }

   public void clear() {
      this.economyProviders.clear();
      this.permissionProviders.clear();
      this.chatProviders.clear();
      LOGGER.info("[VaultAPI] Service registry cleared.");
   }

   public void logStatus() {
      LOGGER.info("[VaultAPI] === Vault Service Status ===");
      LOGGER.info(
         "[VaultAPI]  Economy:    {} provider(s) - active: {}", this.economyProviders.size(), this.getEconomy().map(VaultEconomy::getName).orElse("none")
      );
      LOGGER.info(
         "[VaultAPI]  Permission: {} provider(s) - active: {}",
         this.permissionProviders.size(),
         this.getPermission().map(VaultPermission::getName).orElse("none")
      );
      LOGGER.info("[VaultAPI]  Chat:       {} provider(s) - active: {}", this.chatProviders.size(), this.getChat().map(VaultChat::getName).orElse("none"));
      LOGGER.info("[VaultAPI] ==============================");
   }

   public static class Registration<T> {
      public final T provider;
      public final VaultServiceRegistry.ServicePriority priority;
      public final String registeredBy;

      Registration(T provider, VaultServiceRegistry.ServicePriority priority, String registeredBy) {
         this.provider = provider;
         this.priority = priority;
         this.registeredBy = registeredBy;
      }
   }

   public static enum ServicePriority {
      LOWEST(0),
      LOW(1),
      NORMAL(2),
      HIGH(3),
      HIGHEST(4);

      final int value;

      private ServicePriority(int value) {
         this.value = value;
      }
   }
}
