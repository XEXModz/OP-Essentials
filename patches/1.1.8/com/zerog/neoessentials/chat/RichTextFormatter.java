package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.ChatComponentUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RichTextFormatter {
   private static final Logger LOGGER = LoggerFactory.getLogger(RichTextFormatter.class);
   private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:([0-9a-fA-F]{6})-([0-9a-fA-F]{6})>(.*?)</gradient>", 2);
   private static final Pattern RAINBOW_PATTERN = Pattern.compile("<rainbow>(.*?)</rainbow>", 2);
   private static final int[] RAINBOW_COLORS = new int[]{16711680, 16744192, 16776960, 65280, 255, 4915330, 9699539};

   public static Component processRichText(String text) {
      try {
         if (isRichTextEnabled()) {
            text = processGradients(text);
            text = processRainbow(text);
         }

         return ChatComponentUtil.parseColorCodes(text);
      } catch (Exception var4) {
         LOGGER.error("Error processing rich text: {}", var4.getMessage(), var4);

         try {
            return ChatComponentUtil.parseColorCodes(text);
         } catch (Exception var3) {
            return Component.literal(text);
         }
      }
   }

   private static String processGradients(String text) {
      Matcher matcher = GRADIENT_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String startHex = matcher.group(1);
         String endHex = matcher.group(2);
         String content = matcher.group(3);
         String gradientText = createGradient(content, startHex, endHex);
         matcher.appendReplacement(result, Matcher.quoteReplacement(gradientText));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static String createGradient(String text, String startHex, String endHex) {
      if (text.isEmpty()) {
         return text;
      } else {
         int startColor = Integer.parseInt(startHex, 16);
         int endColor = Integer.parseInt(endHex, 16);
         int startR = startColor >> 16 & 0xFF;
         int startG = startColor >> 8 & 0xFF;
         int startB = startColor & 0xFF;
         int endR = endColor >> 16 & 0xFF;
         int endG = endColor >> 8 & 0xFF;
         int endB = endColor & 0xFF;
         StringBuilder gradientText = new StringBuilder();
         int length = text.length();

         for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
               gradientText.append(c);
            } else {
               float progress = length > 1 ? (float)i / (float)(length - 1) : 0.0F;
               int r = (int)((float)startR + (float)(endR - startR) * progress);
               int g = (int)((float)startG + (float)(endG - startG) * progress);
               int b = (int)((float)startB + (float)(endB - startB) * progress);
               String hexColor = String.format("%02X%02X%02X", r, g, b);
               gradientText.append("&#").append(hexColor).append(c);
            }
         }

         return gradientText.toString();
      }
   }

   private static String processRainbow(String text) {
      Matcher matcher = RAINBOW_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String content = matcher.group(1);
         String rainbowText = createRainbow(content);
         matcher.appendReplacement(result, Matcher.quoteReplacement(rainbowText));
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static String createRainbow(String text) {
      if (text.isEmpty()) {
         return text;
      } else {
         StringBuilder rainbowText = new StringBuilder();
         int colorIndex = 0;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
               rainbowText.append(c);
            } else {
               int color = RAINBOW_COLORS[colorIndex % RAINBOW_COLORS.length];
               String hexColor = String.format("%06X", color);
               rainbowText.append("&#").append(hexColor).append(c);
               colorIndex++;
            }
         }

         return rainbowText.toString();
      }
   }

   private static boolean isRichTextEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("richText")) {
            return chatConfig.getAsJsonObject("richText").get("enabled").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return false;
   }
}
