package com.zerog.neoessentials.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent.Action;

public class ChatComponentUtil {
   private static final Pattern AMPERSAND_CODE_PATTERN = Pattern.compile("&([0-9a-fk-or])");
   private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

   public static Component createClickableCommand(String text, String command, String hoverText) {
      MutableComponent component = Component.literal(text);
      component.setStyle(
         Style.EMPTY
            .withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/" + command))
            .withHoverEvent(hoverText != null ? new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)) : null)
            .withColor(ChatFormatting.YELLOW)
            .withUnderlined(true)
      );
      return component;
   }

   public static Component createClickableSuggestion(String text, String command, String hoverText) {
      MutableComponent component = Component.literal(text);
      component.setStyle(
         Style.EMPTY
            .withClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, "/" + command))
            .withHoverEvent(hoverText != null ? new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)) : null)
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
      );
      return component;
   }

   public static Component createClickableUrl(String text, String url, String hoverText) {
      MutableComponent component = Component.literal(text);
      component.setStyle(
         Style.EMPTY
            .withClickEvent(new ClickEvent(Action.OPEN_URL, url))
            .withHoverEvent(hoverText != null ? new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)) : null)
            .withColor(ChatFormatting.BLUE)
            .withUnderlined(true)
      );
      return component;
   }

   public static Component createHoverText(String text, String hoverText, ChatFormatting color) {
      MutableComponent component = Component.literal(text);
      component.setStyle(
         Style.EMPTY
            .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            .withColor(color != null ? color : ChatFormatting.WHITE)
      );
      return component;
   }

   public static Component createBalanceComponent(String playerName, double balance, String currency) {
      String balanceText = String.format("%s%,.2f", currency, balance);
      String hoverText = String.format("Player: %s\nBalance: %s\nClick to pay this player", playerName, balanceText);
      return createClickableSuggestion(balanceText, "pay " + playerName + " ", hoverText);
   }

   public static Component createPlayerComponent(String playerName) {
      String hoverText = String.format("Player: %s\nClick to message\nShift+Click to view profile", playerName);
      return createClickableSuggestion(playerName, "msg " + playerName + " ", hoverText);
   }

   public static Component createPermissionComponent(String permission) {
      String hoverText = String.format("Permission: %s\nClick to copy to clipboard", permission);
      MutableComponent component = Component.literal(permission);
      component.setStyle(
         Style.EMPTY
            .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, permission))
            .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
            .withColor(ChatFormatting.LIGHT_PURPLE)
      );
      return component;
   }

   public static Component parseColorCodes(String text) {
      if (text != null && !text.isEmpty()) {
         MutableComponent result = Component.empty();
         text = AMPERSAND_CODE_PATTERN.matcher(text).replaceAll("§$1");
         Matcher hexMatcher = HEX_PATTERN.matcher(text);
         StringBuffer sb = new StringBuffer();

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
                  if (currentText.length() > 0) {
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
                  if (currentText.length() > 0) {
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

         if (currentText.length() > 0) {
            result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
         }

         return result;
      } else {
         return Component.empty();
      }
   }

   public static Component createSeparator(int length, char character, ChatFormatting color) {
      String separator = String.valueOf(character).repeat(length);
      return Component.literal(separator).withStyle(color != null ? color : ChatFormatting.GRAY);
   }

   public static Component createProgressBar(double current, double max, int width) {
      double percentage = Math.max(0.0, Math.min(1.0, current / max));
      int filled = (int)(percentage * (double)width);
      int empty = width - filled;
      MutableComponent bar = Component.empty();
      if (filled > 0) {
         bar.append(Component.literal("█".repeat(filled)).withStyle(ChatFormatting.GREEN));
      }

      if (empty > 0) {
         bar.append(Component.literal("█".repeat(empty)).withStyle(ChatFormatting.GRAY));
      }

      String percentText = String.format(" %.1f%%", percentage * 100.0);
      bar.append(Component.literal(percentText).withStyle(ChatFormatting.WHITE));
      return bar;
   }
}
