package com.zerog.neoessentials.chat;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.PlaceholderAPI;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.ChatComponentUtil;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.network.chat.HoverEvent.ItemStackInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatFormatter {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChatFormatter.class);
   private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
   private static final Pattern AMPERSAND_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");
   private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-f])");
   private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("&([k-or])");
   private static final Pattern URL_PATTERN = Pattern.compile("\\b(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%.]+)\\b", 2);
   private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]{3,16})\\b");
   private static final Pattern ITEM_PATTERN = Pattern.compile("\\[item]", 2);

   public static Component formatMessage(String template, ServerPlayer player, String message) {
      try {
         boolean debugEnabled = ConfigManager.getInstance().isDebugLoggingEnabled();
         if (debugEnabled) {
            LOGGER.info("=== CHAT FORMATTING DEBUG ===");
            LOGGER.info("Player: {}, OP: {}", player.getName().getString(), player.hasPermissions(2));
            LOGGER.info("Original message: [{}]", message);
            LOGGER.info("Template: [{}]", template);
         }

         String normalizedTemplate = normalizePlaceholders(template);
         if (debugEnabled) {
            LOGGER.debug("After normalization: {}", normalizedTemplate);
         }

         normalizedTemplate = BadgeManager.getInstance().applyBadgesAndIcons(player, normalizedTemplate);
         if (debugEnabled) {
            LOGGER.debug("After badges/icons: {}", normalizedTemplate);
         }

         String restrictedMessage = restrictPlayerMessageColors(message, player);
         if (debugEnabled) {
            LOGGER.info("After color restriction: [{}]", restrictedMessage);
         }

         String preFormatted = normalizedTemplate.replace("{MESSAGE}", restrictedMessage);
         if (debugEnabled) {
            LOGGER.info("After message insertion: [{}]", preFormatted);
         }

         String formatted = PlaceholderAPI.setPlaceholders(player, preFormatted);
         if (debugEnabled) {
            LOGGER.info("After placeholder resolution: [{}]", formatted);
         }

         formatted = ConditionalFormatter.processConditionals(player, formatted);
         if (debugEnabled) {
            LOGGER.info("After conditional formatting: [{}]", formatted);
         }

         formatted = cleanupFormatting(formatted);
         if (debugEnabled) {
            LOGGER.info("After cleanup: [{}]", formatted);
         }

         Component richTextResult = RichTextFormatter.processRichText(formatted);
         if (debugEnabled) {
            LOGGER.info("After rich text: [{}]", richTextResult.getString());
         }

         Component result;
         if (isChatEnhancementsEnabled()) {
            String richTextString = componentToFormattedString(richTextResult);
            result = enhanceMessage(richTextString, player, player.getServer());
         } else {
            result = richTextResult;
         }

         if (debugEnabled) {
            LOGGER.info("=== END CHAT FORMATTING DEBUG ===");
         }

         return result;
      } catch (Exception var11) {
         LOGGER.error("Failed to format chat message for player {}: {}", new Object[]{player.getName().getString(), var11.getMessage(), var11});
         return Component.literal(player.getName().getString() + ": " + message);
      }
   }

   private static String restrictPlayerMessageColors(String message, ServerPlayer player) {
      UUID uuid = player.getUUID();
      String result = message;
      boolean debugEnabled = ConfigManager.getInstance().isDebugLoggingEnabled();
      if (debugEnabled) {
         LOGGER.info(">>> Restricting colors for player {} (UUID: {})", player.getName().getString(), uuid);
         LOGGER.info(">>> Original message: [{}]", message);
      }

      boolean colorCodesEnabled = ConfigManager.isColorCodesEnabled();
      if (debugEnabled) {
         LOGGER.info(">>> Config enable-color-codes: {}", colorCodesEnabled);
      }

      if (!colorCodesEnabled) {
         result = HEX_PATTERN.matcher(message).replaceAll("");
         result = AMPERSAND_CODE_PATTERN.matcher(result).replaceAll("");
         if (debugEnabled) {
            LOGGER.info(">>> Color codes DISABLED in config - Stripped all codes: [{}]", result);
         }

         return result;
      } else {
         boolean hasHexPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.color.hex");
         boolean hasColorPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.color");
         boolean hasFormatPerm = PermissionAPI.hasPermission(uuid, "neoessentials.chat.format");
         if (debugEnabled) {
            LOGGER.info(">>> Permission Check Results:");
            LOGGER.info(">>>   - neoessentials.chat.color.hex: {}", hasHexPerm);
            LOGGER.info(">>>   - neoessentials.chat.color: {}", hasColorPerm);
            LOGGER.info(">>>   - neoessentials.chat.format: {}", hasFormatPerm);
         }

         if (!hasHexPerm) {
            if (debugEnabled) {
               result = HEX_PATTERN.matcher(message).replaceAll("");
               LOGGER.info(">>>   Stripped hex codes: [{}] -> [{}]", message, result);
            } else {
               result = HEX_PATTERN.matcher(message).replaceAll("");
            }
         }

         if (!hasColorPerm) {
            if (debugEnabled) {
               String before = result;
               result = COLOR_CODE_PATTERN.matcher(result).replaceAll("");
               LOGGER.info(">>>   Stripped color codes: [{}] -> [{}]", before, result);
            } else {
               result = COLOR_CODE_PATTERN.matcher(result).replaceAll("");
            }
         }

         if (!hasFormatPerm) {
            if (debugEnabled) {
               String before = result;
               result = FORMAT_CODE_PATTERN.matcher(result).replaceAll("");
               LOGGER.info(">>>   Stripped format codes: [{}] -> [{}]", before, result);
            } else {
               result = FORMAT_CODE_PATTERN.matcher(result).replaceAll("");
            }
         }

         if (debugEnabled) {
            LOGGER.info(">>> Final restricted message: [{}]", result);
         }

         return result;
      }
   }

   private static Component parseToComponent(String text) {
      MutableComponent result = Component.empty();
      text = AMPERSAND_CODE_PATTERN.matcher(text).replaceAll("§$1");
      Matcher hexMatcher = HEX_PATTERN.matcher(text);
      StringBuilder sb = new StringBuilder();

      while (hexMatcher.find()) {
         try {
            String hex = hexMatcher.group(1);
            hexMatcher.appendReplacement(sb, "§#" + hex + "§");
         } catch (Exception var12) {
            hexMatcher.appendReplacement(sb, "");
         }
      }

      hexMatcher.appendTail(sb);
      text = sb.toString();
      StringBuilder currentText = new StringBuilder();
      Style currentStyle = Style.EMPTY;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            char code = text.charAt(i + 1);
            if (code == '#' && i + 8 < text.length() && text.charAt(i + 8) == 167) {
               if (!currentText.isEmpty()) {
                  result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                  currentText = new StringBuilder();
               }

               try {
                  String hex = text.substring(i + 2, i + 8);
                  int rgb = Integer.parseInt(hex, 16);
                  currentStyle = currentStyle.withColor(TextColor.fromRgb(rgb));
               } catch (Exception var11) {
               }

               i += 8;
               continue;
            }

            ChatFormatting formatting = ChatFormatting.getByCode(code);
            if (formatting != null) {
               if (!currentText.isEmpty()) {
                  result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                  currentText = new StringBuilder();
               }

               if (formatting == ChatFormatting.RESET) {
                  currentStyle = Style.EMPTY;
               } else if (formatting.isColor()) {
                  currentStyle = Style.EMPTY.applyFormat(formatting);
               } else {
                  currentStyle = currentStyle.applyFormat(formatting);
               }

               i++;
               continue;
            }
         }

         currentText.append(c);
      }

      if (!currentText.isEmpty()) {
         result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
      }

      return result;
   }

   private static String normalizePlaceholders(String template) {
      return template.replace("{DISPLAYNAME}", "{neoessentials_displayname}")
         .replace("{USERNAME}", "{neoessentials_username}")
         .replace("{PREFIX}", "{neoessentials_prefix}")
         .replace("{SUFFIX}", "{neoessentials_suffix}")
         .replace("{WORLD}", "{neoessentials_world}")
         .replace("{X}", "{neoessentials_x}")
         .replace("{Y}", "{neoessentials_y}")
         .replace("{Z}", "{neoessentials_z}")
         .replace("{HEALTH}", "{neoessentials_health}")
         .replace("{LEVEL}", "{neoessentials_level}")
         .replace("{BALANCE}", "{neoessentials_balance}")
         .replace("{GAMEMODE}", "{neoessentials_gamemode}")
         .replace("{BIOME}", "{neoessentials_biome}");
   }

   private static String cleanupFormatting(String formatted) {
      formatted = formatted.replaceAll("\\s+", " ");
      formatted = formatted.replaceAll("< >", "");
      formatted = formatted.replaceAll("<\\s+", "<");
      formatted = formatted.replaceAll("\\s+>", ">");
      return formatted.trim();
   }

   public static boolean isValidTemplate(String template) {
      if (template != null && !template.trim().isEmpty()) {
         int openBraces = 0;

         for (char c : template.toCharArray()) {
            if (c == '{') {
               openBraces++;
            } else if (c == '}') {
               openBraces--;
            }

            if (openBraces < 0) {
               return false;
            }
         }

         return openBraces == 0;
      } else {
         return false;
      }
   }

   public static String getDefaultFormat() {
      return "{neoessentials_prefix}{neoessentials_displayname}{neoessentials_suffix}: {MESSAGE}";
   }

   private static boolean isChatEnhancementsEnabled() {
      try {
         return ConfigManager.getInstance().getConfig("chat.json").get("enableChatEnhancements").getAsBoolean();
      } catch (Exception var1) {
         return true;
      }
   }

   private static Component enhanceMessage(String formattedMessage, ServerPlayer player, MinecraftServer server) {
      try {
         String processed = processItemLinks(formattedMessage, player);
         return buildInteractiveComponent(processed, player, server);
      } catch (Exception var4) {
         LOGGER.error("Error enhancing chat message: {}", var4.getMessage(), var4);
         return ChatComponentUtil.parseColorCodes(formattedMessage);
      }
   }

   private static String processItemLinks(String message, ServerPlayer player) {
      if (!isItemLinksEnabled()) {
         return message;
      } else {
         Matcher matcher = ITEM_PATTERN.matcher(message);
         StringBuilder result = new StringBuilder();

         while (matcher.find()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (!mainHandItem.isEmpty()) {
               String itemName = mainHandItem.getHoverName().getString();
               matcher.appendReplacement(result, "§ITEM§" + itemName + "§/ITEM§");
            } else {
               matcher.appendReplacement(result, "[Empty Hand]");
            }
         }

         matcher.appendTail(result);
         return result.toString();
      }
   }

   private static Component buildInteractiveComponent(String text, ServerPlayer sender, MinecraftServer server) {
      String processed = text;
      if (isUrlDetectionEnabled()) {
         processed = markupUrls(text);
      }

      if (isMentionsEnabled()) {
         processed = markupMentions(processed, sender, server);
      }

      return buildComponentFromMarkup(processed, sender);
   }

   private static String markupUrls(String text) {
      Matcher matcher = URL_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String url = matcher.group(1);
         matcher.appendReplacement(result, "§URL§" + url + "§/URL§");
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static String markupMentions(String text, ServerPlayer sender, MinecraftServer server) {
      Matcher matcher = MENTION_PATTERN.matcher(text);
      StringBuilder result = new StringBuilder();

      while (matcher.find()) {
         String mentionedName = matcher.group(1);
         ServerPlayer mentioned = server.getPlayerList().getPlayerByName(mentionedName);
         if (mentioned != null) {
            matcher.appendReplacement(result, "§MENTION§" + mentionedName + "§/MENTION§");
            if (isMentionSoundEnabled() && !mentioned.getUUID().equals(sender.getUUID())) {
               playMentionSound(mentioned);
            }
         } else {
            matcher.appendReplacement(result, "@" + mentionedName);
         }
      }

      matcher.appendTail(result);
      return result.toString();
   }

   private static Component buildComponentFromMarkup(String markup, ServerPlayer sender) {
      MutableComponent result = Component.empty();
      int index = 0;

      while (index < markup.length()) {
         int itemStart = markup.indexOf("§ITEM§", index);
         if (itemStart == index) {
            int itemEnd = markup.indexOf("§/ITEM§", itemStart);
            if (itemEnd != -1) {
               String itemName = markup.substring(itemStart + 6, itemEnd);
               result.append(createItemComponent(itemName, sender));
               index = itemEnd + 7;
               continue;
            }
         }

         int urlStart = markup.indexOf("§URL§", index);
         if (urlStart == index) {
            int urlEnd = markup.indexOf("§/URL§", urlStart);
            if (urlEnd != -1) {
               String url = markup.substring(urlStart + 5, urlEnd);
               result.append(ChatComponentUtil.createClickableUrl(url, url, "Click to open in browser\n" + url));
               index = urlEnd + 6;
               continue;
            }
         }

         int mentionStart = markup.indexOf("§MENTION§", index);
         if (mentionStart == index) {
            int mentionEnd = markup.indexOf("§/MENTION§", mentionStart);
            if (mentionEnd != -1) {
               String playerName = markup.substring(mentionStart + 9, mentionEnd);
               result.append(createMentionComponent(playerName));
               index = mentionEnd + 10;
               continue;
            }
         }

         int nextMarker = markup.length();
         int[] markers = new int[]{markup.indexOf("§ITEM§", index), markup.indexOf("§URL§", index), markup.indexOf("§MENTION§", index)};

         for (int m : markers) {
            if (m != -1 && m < nextMarker) {
               nextMarker = m;
            }
         }

         if (nextMarker <= index) {
            break;
         }

         String plainText = markup.substring(index, nextMarker);
         result.append(ChatComponentUtil.parseColorCodes(plainText));
         index = nextMarker;
      }

      return result;
   }

   private static Component createItemComponent(String itemName, ServerPlayer player) {
      ItemStack mainHandItem = player.getMainHandItem();
      MutableComponent component = Component.literal("[" + itemName + "]").withStyle(ChatFormatting.AQUA).withStyle(ChatFormatting.UNDERLINE);
      if (!mainHandItem.isEmpty()) {
         component.setStyle(component.getStyle().withHoverEvent(new HoverEvent(Action.SHOW_ITEM, new ItemStackInfo(mainHandItem))));
      }

      return component;
   }

   private static Component createMentionComponent(String playerName) {
      ChatFormatting color = getMentionColor();
      return Component.literal("@" + playerName)
         .withStyle(color)
         .withStyle(ChatFormatting.BOLD)
         .withStyle(
            style -> style.withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " "))
                  .withHoverEvent(new HoverEvent(Action.SHOW_TEXT, Component.literal("Click to message " + playerName).withStyle(ChatFormatting.GRAY)))
         );
   }

   private static void playMentionSound(ServerPlayer player) {
      try {
         float volume = getMentionSoundVolume();
         player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, volume, 1.0F);
      } catch (Exception var2) {
         LOGGER.debug("Failed to play mention sound: {}", var2.getMessage());
      }
   }

   private static boolean isItemLinksEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("allowItemLinks")) {
            return chatConfig.get("allowItemLinks").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return true;
   }

   private static boolean isUrlDetectionEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("autoLinkUrls")) {
            return chatConfig.get("autoLinkUrls").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return true;
   }

   private static boolean isMentionsEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("enabled")) {
            return chatConfig.getAsJsonObject("mentions").get("enabled").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return true;
   }

   private static boolean isMentionSoundEnabled() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("playSound")) {
            return chatConfig.getAsJsonObject("mentions").get("playSound").getAsBoolean();
         }
      } catch (Exception var1) {
      }

      return true;
   }

   private static ChatFormatting getMentionColor() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("highlightColor")) {
            String color = chatConfig.getAsJsonObject("mentions").get("highlightColor").getAsString();
            if (color.startsWith("&") && color.length() == 2) {
               char code = color.charAt(1);
               ChatFormatting formatting = ChatFormatting.getByCode(code);
               if (formatting != null) {
                  return formatting;
               }
            }
         }
      } catch (Exception var4) {
      }

      return ChatFormatting.YELLOW;
   }

   private static float getMentionSoundVolume() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("mentions") && chatConfig.getAsJsonObject("mentions").has("soundVolume")) {
            return chatConfig.getAsJsonObject("mentions").get("soundVolume").getAsFloat();
         }
      } catch (Exception var1) {
      }

      return 1.0F;
   }

   private static String componentToFormattedString(Component component) {
      // BUGFIX 1.1.6: component.getString() drops ALL styling, so when chat
      // enhancements are enabled the message body lost its colors (the prefix
      // survives because it is re-derived downstream). Walk the resolved style
      // runs and re-emit legacy &-codes so enhanceMessage()/parseColorCodes()
      // can rebuild the exact colors.
      StringBuilder sb = new StringBuilder();
      component.visit((style, string) -> {
         if (!string.isEmpty()) {
            sb.append(styleToLegacyCodes(style));
            sb.append(string);
         }

         return java.util.Optional.empty();
      }, Style.EMPTY);
      return sb.toString();
   }

   private static String styleToLegacyCodes(Style style) {
      StringBuilder codes = new StringBuilder("&r");
      TextColor color = style.getColor();
      if (color != null) {
         ChatFormatting named = null;

         for (ChatFormatting cf : ChatFormatting.values()) {
            if (cf.isColor() && color.equals(TextColor.fromLegacyFormat(cf))) {
               named = cf;
               break;
            }
         }

         if (named != null) {
            codes.append('&').append(named.getChar());
         } else {
            codes.append("&#").append(String.format("%06X", color.getValue() & 0xFFFFFF));
         }
      }

      if (style.isBold()) {
         codes.append("&l");
      }

      if (style.isItalic()) {
         codes.append("&o");
      }

      if (style.isUnderlined()) {
         codes.append("&n");
      }

      if (style.isStrikethrough()) {
         codes.append("&m");
      }

      if (style.isObfuscated()) {
         codes.append("&k");
      }

      return codes.toString();
   }
}
