package com.zerog.neoessentials.util;

import net.minecraft.resources.ResourceLocation;

public class ResourceLocationHelper {
   public static ResourceLocation create(String namespace, String path) {
      return ResourceLocation.fromNamespaceAndPath(namespace, path);
   }

   public static ResourceLocation parse(String locationString) {
      if (locationString.contains(":")) {
         String[] parts = locationString.split(":", 2);
         return create(parts[0], parts[1]);
      } else {
         return create("minecraft", locationString);
      }
   }
}
