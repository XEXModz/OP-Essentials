package com.zerog.neoessentials.util;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentUtils {
   public static Holder<Enchantment> getEnchantment(MinecraftServer server, String namespace, String path) {
      Registry<Enchantment> registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
      ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
      return registry.getHolderOrThrow(key);
   }

   public static Optional<Holder<Enchantment>> getEnchantmentSafely(MinecraftServer server, String namespace, String path) {
      try {
         Registry<Enchantment> registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
         ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
         ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
         return registry.getHolder(key).map(holder -> (Holder<Enchantment>)holder);
      } catch (Exception var6) {
         return Optional.empty();
      }
   }

   public static Optional<Holder<Enchantment>> getEnchantment(MinecraftServer server, ResourceLocation location) {
      try {
         Registry<Enchantment> registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
         ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, location);
         return registry.getHolder(key).map(holder -> (Holder<Enchantment>)holder);
      } catch (Exception var4) {
         return Optional.empty();
      }
   }
}
