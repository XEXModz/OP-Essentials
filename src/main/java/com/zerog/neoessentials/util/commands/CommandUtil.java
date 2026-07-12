package com.zerog.neoessentials.util.commands;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class CommandUtil {
   public static String getWorldName(Level level) {
      return getWorldName(level.dimension().location().toString());
   }

   public static String getWorldName(String dimensionKey) {
      return switch (dimensionKey) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "The Nether";
         case "minecraft:the_end" -> "The End";
         default -> dimensionKey;
      };
   }

   public static String getDimensionName(Level level) {
      return level.dimension().location().toString();
   }

   public static String getBiomeName(Biome biome, Level level, BlockPos pos) {
      ResourceLocation biomeKey = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
      return biomeKey != null ? formatBiomeName(biomeKey.toString()) : "Unknown";
   }

   public static String formatBiomeName(String biomeName) {
      return biomeName.replaceAll("minecraft:", "").replaceAll("_", " ").trim();
   }

   public static String getCardinalDirection(float yaw) {
      yaw = (yaw % 360.0F + 360.0F) % 360.0F;
      if (yaw >= 315.0F || yaw < 45.0F) {
         return "South";
      } else if (yaw >= 45.0F && yaw < 135.0F) {
         return "West";
      } else if (yaw >= 135.0F && yaw < 225.0F) {
         return "North";
      } else {
         return yaw >= 225.0F && yaw < 315.0F ? "East" : "South";
      }
   }

   public static String getDirectionFromOffset(double deltaX, double deltaZ) {
      double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX));
      angle = (angle % 360.0 + 360.0) % 360.0;
      angle = (angle + 90.0) % 360.0;
      if (angle >= 315.0 || angle < 45.0) {
         return "South";
      } else if (angle >= 45.0 && angle < 135.0) {
         return "West";
      } else if (angle >= 135.0 && angle < 225.0) {
         return "North";
      } else {
         return angle >= 225.0 && angle < 315.0 ? "East" : "South";
      }
   }

   public static String getSimpleDirection(double relativeX, double relativeZ) {
      double angle = Math.atan2(relativeZ, relativeX) * 180.0 / Math.PI;
      if (angle < 0.0) {
         angle += 360.0;
      }

      if (angle >= 337.5 || angle < 22.5) {
         return "E";
      } else if (angle >= 22.5 && angle < 67.5) {
         return "SE";
      } else if (angle >= 67.5 && angle < 112.5) {
         return "S";
      } else if (angle >= 112.5 && angle < 157.5) {
         return "SW";
      } else if (angle >= 157.5 && angle < 202.5) {
         return "W";
      } else if (angle >= 202.5 && angle < 247.5) {
         return "NW";
      } else {
         return angle >= 247.5 && angle < 292.5 ? "N" : "NE";
      }
   }

   public static String formatDistance(double distance, int decimals) {
      String format = "%." + decimals + "f";
      return String.format(format, distance);
   }

   public static boolean isOverworld(Level level) {
      return level.dimension() == Level.OVERWORLD;
   }

   public static boolean isNether(Level level) {
      return level.dimension() == Level.NETHER;
   }

   public static boolean isEnd(Level level) {
      return level.dimension() == Level.END;
   }

   public static int overworldToNether(int overworldCoord) {
      return overworldCoord / 8;
   }

   public static int netherToOverworld(int netherCoord) {
      return netherCoord * 8;
   }

   private CommandUtil() {
      throw new UnsupportedOperationException("Utility class");
   }
}
