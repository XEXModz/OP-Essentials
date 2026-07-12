package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.VanishManager;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConditionalFormatter {
   private static final Logger LOGGER = LoggerFactory.getLogger(ConditionalFormatter.class);
   private static final Pattern IF_TIME_PATTERN = Pattern.compile("<if:time=([a-z]+)>(.*?)</if>", 2);
   private static final Pattern IF_STAT_PATTERN = Pattern.compile("<if:([a-z]+)([<>=!]+)([0-9.]+)>(.*?)</if>", 2);
   private static final Pattern IF_CONDITION_PATTERN = Pattern.compile("<if:([a-z]+)>(.*?)</if>", 2);

   public static String processConditionals(ServerPlayer player, String text) {
      if (!isConditionalFormattingEnabled()) {
         return text;
      } else {
         try {
            text = processTimeConditionals(text);
            text = processStatConditionals(player, text);
            return processStateConditionals(player, text);
         } catch (Exception var3) {
            LOGGER.error("Error processing conditional formatting: {}", var3.getMessage(), var3);
            return text;
         }
      }
   }

   private static String processTimeConditionals(String text) {
      Matcher matcher = IF_TIME_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String timeCondition = matcher.group(1).toLowerCase();
         String content = matcher.group(2);
         boolean shouldShow = checkTimeCondition(timeCondition);
         String replacement = shouldShow ? content : "";
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static boolean checkTimeCondition(String condition) {
      LocalDateTime now = LocalDateTime.now();
      int hour = now.getHour();

      return switch (condition) {
         case "morning" -> hour >= 6 && hour < 12;
         case "afternoon" -> hour >= 12 && hour < 18;
         case "evening" -> hour >= 18 && hour < 22;
         case "night" -> hour >= 22 || hour < 6;
         case "day" -> hour >= 6 && hour < 18;
         case "weekday" -> now.getDayOfWeek().getValue() <= 5;
         case "weekend" -> now.getDayOfWeek().getValue() >= 6;
         default -> false;
      };
   }

   private static String processStatConditionals(ServerPlayer player, String text) {
      Matcher matcher = IF_STAT_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String stat = matcher.group(1).toLowerCase();
         String operator = matcher.group(2);
         double threshold = Double.parseDouble(matcher.group(3));
         String content = matcher.group(4);
         boolean shouldShow = checkStatCondition(player, stat, operator, threshold);
         String replacement = shouldShow ? content : "";
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static boolean checkStatCondition(ServerPlayer player, String stat, String operator, double threshold) {
      double value = switch (stat) {
         case "health" -> (double)player.getHealth();
         case "level" -> (double)player.experienceLevel;
         case "food" -> (double)player.getFoodData().getFoodLevel();
         case "armor" -> (double)player.getArmorValue();
         case "xp" -> (double)player.totalExperience;
         default -> 0.0;
      };

      return switch (operator) {
         case "<" -> value < threshold;
         case ">" -> value > threshold;
         case "<=" -> value <= threshold;
         case ">=" -> value >= threshold;
         case "=", "==" -> Math.abs(value - threshold) < 0.001;
         case "!=" -> Math.abs(value - threshold) >= 0.001;
         default -> false;
      };
   }

   private static String processStateConditionals(ServerPlayer player, String text) {
      Matcher matcher = IF_CONDITION_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String condition = matcher.group(1).toLowerCase();
         String content = matcher.group(2);
         boolean shouldShow = checkStateCondition(player, condition);
         String replacement = shouldShow ? content : "";
         matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static boolean checkStateCondition(ServerPlayer player, String condition) {
      return switch (condition) {
         case "afk" -> isPlayerAfk(player);
         case "vanished" -> isPlayerVanished(player);
         case "flying" -> player.getAbilities().flying;
         case "creative" -> player.isCreative();
         case "survival" -> player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
         case "spectator" -> player.isSpectator();
         case "op" -> player.hasPermissions(2);
         case "sneaking" -> player.isCrouching();
         case "sprinting" -> player.isSprinting();
         case "swimming" -> player.isSwimming();
         case "onfire" -> player.isOnFire();
         case "wet" -> player.isInWaterOrRain();
         case "underground" -> player.getY() < 62.0;
         case "nether" -> player.level().dimension().location().getPath().equals("the_nether");
         case "end" -> player.level().dimension().location().getPath().equals("the_end");
         case "overworld" -> player.level().dimension().location().getPath().equals("overworld");
         default -> false;
      };
   }

   private static boolean isPlayerAfk(ServerPlayer player) {
      try {
         AfkManager afkManager = AfkManager.getInstance();
         return afkManager.isAfk(player);
      } catch (Exception var2) {
         return false;
      }
   }

   private static boolean isPlayerVanished(ServerPlayer player) {
      try {
         VanishManager vanishManager = VanishManager.getInstance();
         return vanishManager.isPlayerVanished(player.getUUID());
      } catch (Exception var2) {
         return false;
      }
   }

   private static boolean isConditionalFormattingEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("conditionalFormatting")) {
            return chatConfig.getAsJsonObject("conditionalFormatting").get("enabled").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return false;
   }
}
