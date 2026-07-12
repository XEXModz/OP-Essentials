package com.zerog.neoessentials.economy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EconomyCache {
   private static final ConcurrentHashMap<UUID, Object> cache = new ConcurrentHashMap<>();

   public static <T> T getOrLoad(UUID key, Function<UUID, T> loader) {
      return (T)cache.computeIfAbsent(key, loader);
   }

   public static void invalidate(UUID key) {
      cache.remove(key);
   }

   public static void clear() {
      cache.clear();
   }
}
