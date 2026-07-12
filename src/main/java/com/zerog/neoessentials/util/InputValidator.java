package com.zerog.neoessentials.util;

import com.zerog.neoessentials.config.ConfigManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputValidator {
   private static final Logger LOGGER = LoggerFactory.getLogger(InputValidator.class);
   private static final Pattern VALID_PLAYER_NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
   private static final Pattern SAFE_COMMAND = Pattern.compile("^[a-zA-Z0-9_\\-/\\s:.&#~]+$");
   private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+$");

   private static int getMaxCommandLength() {
      return ConfigManager.getInstance().getMaxCommandLength();
   }

   private static int getMaxReasonLength() {
      return ConfigManager.getInstance().getMaxReasonLength();
   }

   private static BigDecimal getMaxEconomyAmount() {
      return ConfigManager.getInstance().getMaxEconomyAmount();
   }

   private static BigDecimal getMinEconomyAmount() {
      return ConfigManager.getInstance().getMinEconomyAmount();
   }

   public static InputValidator.ValidationResult validatePlayerName(String playerName) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(playerName);
      } else if (playerName != null && !playerName.trim().isEmpty()) {
         String trimmed = playerName.trim();
         if (trimmed.length() > 16) {
            return InputValidator.ValidationResult.failure("Player name too long (max 16 characters)");
         } else {
            return !VALID_PLAYER_NAME.matcher(trimmed).matches()
               ? InputValidator.ValidationResult.failure("Player name contains invalid characters")
               : InputValidator.ValidationResult.success(trimmed);
         }
      } else {
         return InputValidator.ValidationResult.failure("Player name cannot be empty");
      }
   }

   public static InputValidator.ValidationResult validateEconomyAmount(double amount) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(amount);
      } else if (Double.isNaN(amount) || Double.isInfinite(amount)) {
         return InputValidator.ValidationResult.failure("Invalid amount: not a valid number");
      } else if (amount <= 0.0) {
         return InputValidator.ValidationResult.failure("Amount must be positive");
      } else {
         BigDecimal bd = BigDecimal.valueOf(amount);
         BigDecimal maxAmount = getMaxEconomyAmount();
         BigDecimal minAmount = getMinEconomyAmount();
         if (bd.compareTo(maxAmount) > 0) {
            return InputValidator.ValidationResult.failure("Amount too large (max " + maxAmount + ")");
         } else {
            return bd.compareTo(minAmount) < 0
               ? InputValidator.ValidationResult.failure("Amount too small (min " + minAmount + ")")
               : InputValidator.ValidationResult.success(bd);
         }
      }
   }

   public static InputValidator.ValidationResult validateCommand(String command) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(command);
      } else if (command != null && !command.trim().isEmpty()) {
         String trimmed = command.trim();
         int maxLength = getMaxCommandLength();
         if (trimmed.length() > maxLength) {
            return InputValidator.ValidationResult.failure("Command too long (max " + maxLength + " characters)");
         } else {
            if (trimmed.startsWith("/")) {
               trimmed = trimmed.substring(1);
            }

            boolean allowUnsafeCommands = ConfigManager.getInstance().isUnsafeCommandsAllowed();
            if (!allowUnsafeCommands) {
               String lowerCommand = trimmed.toLowerCase();
               if (containsDangerousCommand(lowerCommand)) {
                  return InputValidator.ValidationResult.failure(
                     "Command contains potentially dangerous operations. Enable 'allowUnsafeCommands' in config to use this command."
                  );
               }

               if (!SAFE_COMMAND.matcher(trimmed).matches()) {
                  return InputValidator.ValidationResult.failure(
                     "Command contains unsafe characters. Enable 'allowUnsafeCommands' in config to use special characters."
                  );
               }
            }

            return InputValidator.ValidationResult.success(trimmed);
         }
      } else {
         return InputValidator.ValidationResult.failure("Command cannot be empty");
      }
   }

   public static InputValidator.ValidationResult validateFilePath(String filePath, String allowedBasePath) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(filePath);
      } else if (filePath != null && !filePath.trim().isEmpty()) {
         try {
            Path normalizedPath = Paths.get(filePath).normalize();
            Path basePath = Paths.get(allowedBasePath).normalize();
            if (!normalizedPath.startsWith(basePath)) {
               return InputValidator.ValidationResult.failure("File path outside allowed directory");
            } else {
               for (Path component : normalizedPath) {
                  String name = component.getFileName().toString();
                  if (name.contains("..") || name.contains("~") || !SAFE_FILENAME.matcher(name).matches()) {
                     return InputValidator.ValidationResult.failure("File path contains unsafe components");
                  }
               }

               return InputValidator.ValidationResult.success(normalizedPath.toString());
            }
         } catch (Exception var7) {
            LOGGER.debug("Path validation error: {}", var7.getMessage());
            return InputValidator.ValidationResult.failure("Invalid file path format");
         }
      } else {
         return InputValidator.ValidationResult.failure("File path cannot be empty");
      }
   }

   public static InputValidator.ValidationResult validateReason(String reason) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(reason);
      } else if (reason == null) {
         return InputValidator.ValidationResult.success("");
      } else {
         String trimmed = reason.trim();
         int maxReasonLength = getMaxReasonLength();
         if (trimmed.length() > maxReasonLength) {
            return InputValidator.ValidationResult.failure("Reason too long (max " + maxReasonLength + " characters)");
         } else {
            return containsUnsafeContent(trimmed)
               ? InputValidator.ValidationResult.failure("Reason contains unsafe content")
               : InputValidator.ValidationResult.success(trimmed);
         }
      }
   }

   public static InputValidator.ValidationResult validateOnlinePlayer(String playerName, MinecraftServer server) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         if (playerName == null) {
            return InputValidator.ValidationResult.success(null);
         } else {
            ServerPlayer player = server.getPlayerList()
               .getPlayers()
               .stream()
               .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(playerName))
               .findFirst()
               .orElse(null);
            return InputValidator.ValidationResult.success(player);
         }
      } else {
         InputValidator.ValidationResult nameValidation = validatePlayerName(playerName);
         if (!nameValidation.isValid()) {
            return nameValidation;
         } else {
            String validName = (String)nameValidation.getValue();
            ServerPlayer player = server.getPlayerList()
               .getPlayers()
               .stream()
               .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(validName))
               .findFirst()
               .orElse(null);
            return player == null
               ? InputValidator.ValidationResult.failure("Player '" + validName + "' not found or not online")
               : InputValidator.ValidationResult.success(player);
         }
      }
   }

   public static InputValidator.ValidationResult validateEnchantmentLevel(int level, boolean allowUnsafeEnchants) {
      if (!ConfigManager.getInstance().isInputValidationEnabled()) {
         return InputValidator.ValidationResult.success(level);
      } else if (level < 1) {
         return InputValidator.ValidationResult.failure("Enchantment level must be at least 1");
      } else if (!allowUnsafeEnchants && level > 255) {
         return InputValidator.ValidationResult.failure("Enchantment level too high (max 255). Enable unsafe enchantments to use higher levels.");
      } else {
         if (allowUnsafeEnchants) {
            int maxUnsafeLevel = ConfigManager.getInstance().getMaxUnsafeEnchantmentLevel();
            if (level > maxUnsafeLevel) {
               return InputValidator.ValidationResult.failure("Enchantment level exceeds configured maximum (" + maxUnsafeLevel + ")");
            }
         }

         return InputValidator.ValidationResult.success(level);
      }
   }

   private static boolean containsDangerousCommand(String command) {
      String[] dangerousPatterns = new String[]{
         "rm ",
         "del ",
         "delete ",
         "format",
         "shutdown",
         "reboot",
         "eval",
         "exec",
         "system",
         "runtime",
         "process",
         "../",
         "..\\",
         "~",
         "$",
         "`",
         "&&",
         "||",
         ";",
         "file:",
         "http:",
         "https:",
         "ftp:",
         "jar:",
         "class.forname",
         "reflection",
         "unsafe"
      };

      for (String pattern : dangerousPatterns) {
         if (command.contains(pattern)) {
            return true;
         }
      }

      return false;
   }

   private static boolean containsUnsafeContent(String text) {
      String lower = text.toLowerCase();
      String[] unsafePatterns = new String[]{
         "<script", "javascript:", "vbscript:", "data:", "\\x", "\\u", "%", "&lt;", "&gt;", "eval(", "alert(", "confirm(", "prompt("
      };

      for (String pattern : unsafePatterns) {
         if (lower.contains(pattern)) {
            return true;
         }
      }

      return false;
   }

   public static String escapeHtml(String input) {
      if (input == null) {
         return null;
      } else {
         StringBuilder sb = new StringBuilder();

         for (char c : input.toCharArray()) {
            switch (c) {
               case '"':
                  sb.append("&quot;");
                  break;
               case '&':
                  sb.append("&amp;");
                  break;
               case '\'':
                  sb.append("&#39;");
                  break;
               case '<':
                  sb.append("&lt;");
                  break;
               case '>':
                  sb.append("&gt;");
                  break;
               default:
                  sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   public static class ValidationResult {
      private final boolean valid;
      private final String errorMessage;
      private final Object value;

      private ValidationResult(boolean valid, String errorMessage, Object value) {
         this.valid = valid;
         this.errorMessage = errorMessage;
         this.value = value;
      }

      public static InputValidator.ValidationResult success(Object value) {
         return new InputValidator.ValidationResult(true, null, value);
      }

      public static InputValidator.ValidationResult failure(String errorMessage) {
         return new InputValidator.ValidationResult(false, errorMessage, null);
      }

      public boolean isValid() {
         return this.valid;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public Object getValue() {
         return this.value;
      }

      public <T> T getValue(Class<T> type) {
         return type.cast(this.value);
      }
   }
}
