package com.zerog.neoessentials.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.config.ConfigManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageUtil {
   private static final Logger LOGGER = LoggerFactory.getLogger(MessageUtil.class);
   private static final Map<String, String> translations = new HashMap<>();
   private static boolean loaded = false;
   private static boolean debugMode = false;
   private static final String LANG_VERSION_KEY = "_langVersion";
   private static final int CURRENT_LANG_VERSION = 10;

   public static boolean isDebugMode() {
      return debugMode;
   }

   public static void syncDebugModeFromConfig() {
      debugMode = ConfigManager.isDebugModeEnabled();
      LOGGER.debug("Debug mode set to: {} (from config)", debugMode);
   }

   private static void loadTranslations() {
      if (!loaded) {
         loaded = true;
         LOGGER.debug("=== LOADING NEOESSENTIALS TRANSLATIONS ===");
         File customLangDir = getNeoEssentialsLangCustomDir();
         if (!customLangDir.exists()) {
            boolean dirCreated = customLangDir.mkdirs();
            if (!dirCreated) {
               LOGGER.error("Failed to create custom language directory: {}", customLangDir.getAbsolutePath());
            } else {
               LOGGER.debug("Created custom language directory: {}", customLangDir.getAbsolutePath());
            }
         }

         File serverLangFile = new File(customLangDir, "en_us.json");
         LOGGER.debug("Server language file path: {}", serverLangFile.getAbsolutePath());
         Map<String, String> finalTranslations = null;
         if (serverLangFile.exists() && serverLangFile.length() > 0L) {
            finalTranslations = loadServerTranslations(serverLangFile);
            if (finalTranslations != null) {
               int deployedVersion = 0;

               try {
                  deployedVersion = Integer.parseInt(finalTranslations.getOrDefault("_langVersion", "0"));
               } catch (NumberFormatException var14) {
               }

               if (deployedVersion < 10) {
                  LOGGER.info("NeoEssentials: lang file is v{} (current v{}) — merging new keys...", deployedVersion, 10);
                  Map<String, String> jarTranslations = loadJarTranslations();
                  if (jarTranslations != null) {
                     int added = 0;

                     for (Entry<String, String> e : jarTranslations.entrySet()) {
                        if (!finalTranslations.containsKey(e.getKey())) {
                           finalTranslations.put(e.getKey(), e.getValue());
                           added++;
                        }
                     }

                     finalTranslations.put("_langVersion", String.valueOf(10));

                     try (FileWriter fw = new FileWriter(serverLangFile)) {
                        new GsonBuilder().setPrettyPrinting().create().toJson(finalTranslations, fw);
                     } catch (Exception var13) {
                        LOGGER.warn("NeoEssentials: could not save merged lang file: {}", var13.getMessage());
                     }

                     LOGGER.info("NeoEssentials: merged {} new translation keys (total: {})", added, finalTranslations.size());
                  }
               }

               translations.putAll(finalTranslations);
               LOGGER.info("NeoEssentials: loaded {} translations", translations.size());
            } else {
               LOGGER.error("Failed to load custom language file, will attempt to update from JAR");
            }
         }

         if (translations.isEmpty()) {
            Map<String, String> jarTranslations = loadJarTranslations();
            if (jarTranslations == null || jarTranslations.isEmpty()) {
               LOGGER.error("Failed to load JAR translations - cannot proceed");

               try (InputStream testIn = ResourceUtil.getJarLangResource("en_us.json")) {
                  if (testIn == null) {
                     LOGGER.error("JAR resource 'en_us.json' is missing or not found in /data/lang/");
                  } else {
                     LOGGER.debug("JAR resource 'en_us.json' is present but failed to load as translations.");
                  }
               } catch (Exception var16) {
                  LOGGER.error("Exception when testing JAR resource existence: {}", var16.getMessage(), var16);
               }

               return;
            }

            LOGGER.debug("JAR contains {} translation keys", jarTranslations.size());

            try {
               updateServerLanguageFile(serverLangFile, jarTranslations);
               if (serverLangFile.exists()) {
                  LOGGER.debug("Language file successfully created: {}", serverLangFile.getAbsolutePath());
                  finalTranslations = loadServerTranslations(serverLangFile);
                  if (finalTranslations != null) {
                     translations.putAll(finalTranslations);
                     LOGGER.info("NeoEssentials: loaded {} translations (updated from JAR)", translations.size());
                  } else {
                     LOGGER.error("Failed to load custom language file after update, using JAR translations directly");
                     translations.putAll(jarTranslations);
                  }
               } else {
                  LOGGER.error("Language file was not created: {}", serverLangFile.getAbsolutePath());
                  translations.putAll(jarTranslations);
               }
            } catch (Exception var10) {
               LOGGER.error("Exception during language file update: {}", var10.getMessage(), var10);
               translations.putAll(jarTranslations);
            }
         }

         LOGGER.debug("Translation loading complete. Total keys: {}", translations.size());
         if (serverLangFile.length() == 0L) {
            LOGGER.error("Server language file is empty after creation! Check file permissions and JAR resource.");
         }
      }
   }

   private static Map<String, String> loadJarTranslations() {
      try {
         Map var5;
         try (InputStream in = ResourceUtil.getJarLangResource("en_us.json")) {
            if (in == null) {
               LOGGER.error("JAR language resource 'en_us.json' not found.");
               return null;
            }

            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8).useDelimiter("\\A")) {
               String json = scanner.hasNext() ? scanner.next() : "";
               Gson gson = new Gson();
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               var5 = (Map)gson.fromJson(json, type);
            }
         }

         return var5;
      } catch (Exception var10) {
         LOGGER.error("Failed to load JAR translations: {}", var10.getMessage(), var10);
         return null;
      }
   }

   private static Map<String, String> loadServerTranslations(File serverFile) {
      if (!serverFile.exists()) {
         return null;
      } else {
         try {
            Map var4;
            try (FileReader reader = new FileReader(serverFile)) {
               Gson gson = new Gson();
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               var4 = (Map)gson.fromJson(reader, type);
            }

            return var4;
         } catch (Exception var7) {
            LOGGER.warn("Failed to load server translations from {}: {}", serverFile.getAbsolutePath(), var7.getMessage());
            return null;
         }
      }
   }

   private static void updateServerLanguageFile(File serverFile, Map<String, String> jarTranslations) {
      try {
         File parentDir = serverFile.getParentFile();
         if (!parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs();
            if (!dirCreated) {
               LOGGER.error("Failed to create language directory: {}", parentDir.getAbsolutePath());
            } else {
               LOGGER.debug("Created language directory: {}", parentDir.getAbsolutePath());
            }
         }

         Map<String, String> translationsWithVersion = new HashMap<>(jarTranslations);
         translationsWithVersion.put("_langVersion", String.valueOf(10));

         try (FileWriter writer = new FileWriter(serverFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(translationsWithVersion, writer);
            LOGGER.debug("Updated server language file with {} keys (version {})", translationsWithVersion.size(), 10);
         }
      } catch (Exception var9) {
         LOGGER.error(
            "Failed to update server language file: {} ({}): {}",
            new Object[]{serverFile.getAbsolutePath(), serverFile.getParentFile(), var9.getMessage(), var9}
         );
      }
   }

   public static String localize(String key, Object... args) {
      loadTranslations();
      String template = translations.getOrDefault(key, key);
      if (debugMode && !translations.containsKey(key)) {
         LOGGER.warn("Missing translation key: {} (total keys loaded: {})", key, translations.size());
      }

      try {
         String result = MessageFormat.format(template.replace("%s", "{0}"), args);
         if (debugMode) {
            LOGGER.info("MessageFormat success - Key: {}, Template: '{}', Args: {}, Result: '{}'", new Object[]{key, template, Arrays.toString(args), result});
         }

         return result;
      } catch (Exception var4) {
         LOGGER.error(
            "Failed to format message - Key: {}, Template: '{}', Args: {}, Error: {}",
            new Object[]{key, template, Arrays.toString(args), var4.getMessage(), var4}
         );
         return template;
      }
   }

   public static Component component(String key, Object... args) {
      String message = localize(key, args);
      if (debugMode) {
         LOGGER.debug("Component created - Key: {}, Message: '{}'", key, message);
      }

      return Component.literal(message);
   }

   public static Component success(String key, Object... args) {
      return Component.literal(localize(key, args)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(65280)));
   }

   public static Component error(String key, Object... args) {
      return Component.literal(localize(key, args)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(16711680)));
   }

   public static Component warning(String key, Object... args) {
      return Component.literal(localize(key, args)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(16776960)));
   }

   public static Component info(String key, Object... args) {
      return Component.literal(localize(key, args)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(65535)));
   }

   public static String getDebugInfo() {
      loadTranslations();
      syncDebugModeFromConfig();
      return String.format("Translations loaded: %d, Debug mode: %s", translations.size(), debugMode);
   }

   public static void debugKey(String key) {
      loadTranslations();
      LOGGER.info("Debug key '{}': exists={}, value='{}'", new Object[]{key, translations.containsKey(key), translations.get(key)});
      LOGGER.info("Total translations loaded: {}, Sample keys: {}", translations.size(), translations.keySet().stream().limit(3L).toArray());
   }

   public static boolean hasTranslation(String key) {
      loadTranslations();
      return translations.containsKey(key);
   }

   public static void reloadTranslations() {
      loaded = false;
      translations.clear();
      loadTranslations();
      LOGGER.info("Forced translation reload completed, {} keys loaded", translations.size());
   }

   public static void ensureLanguageFileUpToDate() {
      File serverLangFile = ResourceUtil.getLanguageFile("en_us");
      Map<String, String> jarTranslations = loadJarTranslations();
      Map<String, String> serverTranslations = loadServerTranslations(serverLangFile);
      boolean needsUpdate = false;
      if (jarTranslations == null) {
         LOGGER.error("JAR translations are null, cannot update language file.");
      } else {
         if (serverTranslations == null) {
            needsUpdate = true;
         } else {
            for (String key : jarTranslations.keySet()) {
               if (!serverTranslations.containsKey(key)) {
                  needsUpdate = true;
                  break;
               }
            }
         }

         if (needsUpdate) {
            updateServerLanguageFile(serverLangFile, jarTranslations);
            translations.clear();
            loaded = false;
            loadTranslations();
            LOGGER.info("Language file updated/merged due to config version update.");
         }
      }
   }

   private static File getNeoEssentialsConfigRoot() {
      String configDir = System.getProperty("neoessentials.config.dir");
      return configDir != null && !configDir.isEmpty() ? new File(configDir) : new File(System.getProperty("user.dir"));
   }

   public static void ensureCustomLanguageFile() {
      File configRoot = getNeoEssentialsConfigRoot();
      File langDir = new File(configRoot, "neoessentials/languages/custom");
      File langFile = new File(langDir, "en_us.json");
      logInfo("[Lang] Working directory: " + System.getProperty("user.dir"));
      logInfo("[Lang] Resolved language file path: " + langFile.getAbsolutePath());
      if (langFile.exists() && langFile.length() != 0L) {
         logInfo("Custom language file exists: " + langFile.getAbsolutePath());
      } else {
         logInfo("Custom language file not found or empty: " + langFile.getAbsolutePath());

         try {
            try (InputStream in = ResourceUtil.getJarLangResource("en_us.json")) {
               if (in != null) {
                  Files.createDirectories(langFile.getParentFile().toPath());
                  Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                  logInfo("Generated custom language file from JAR resource: " + langFile.getAbsolutePath());
                  return;
               }

               logError("Default language resource not found in JAR: data/lang/en_us.json");
            }

            return;
         } catch (Exception var8) {
            logError("Failed to generate custom language file: " + var8.getMessage());
         }
      }
   }

   private static void logInfo(String msg) {
      System.out.println("[NeoEssentials-Lang] INFO: " + msg);
   }

   private static void logError(String msg) {
      System.err.println("[NeoEssentials-Lang] ERROR: " + msg);
   }

   public static Component clickableCommand(String text, String command, String hoverText) {
      return ChatComponentUtil.createClickableCommand(text, command, hoverText);
   }

   public static Component clickableSuggestion(String text, String command, String hoverText) {
      return ChatComponentUtil.createClickableSuggestion(text, command, hoverText);
   }

   public static Component balanceComponent(String playerName, double balance, String currency) {
      return ChatComponentUtil.createBalanceComponent(playerName, balance, currency);
   }

   public static Component playerComponent(String playerName) {
      return ChatComponentUtil.createPlayerComponent(playerName);
   }

   public static Component permissionComponent(String permission) {
      return ChatComponentUtil.createPermissionComponent(permission);
   }

   public static Component coloredText(String text) {
      if (!ConfigManager.isColorCodesEnabled()) {
         if (text == null) {
            return Component.empty();
         } else {
            String noCodes = text.replaceAll("[§&][0-9a-fk-or]", "");
            noCodes = noCodes.replaceAll("#[0-9a-fA-F]{6}", "");
            return Component.literal(noCodes);
         }
      } else {
         return ChatComponentUtil.parseColorCodes(text);
      }
   }

   public static Component separator(int length, char character, ChatFormatting color) {
      return ChatComponentUtil.createSeparator(length, character, color);
   }

   public static Component progressBar(double current, double max, int width) {
      return ChatComponentUtil.createProgressBar(current, max, width);
   }

   private static int getLanguageVersion(Map<String, String> translations) {
      if (translations != null && translations.containsKey("_langVersion")) {
         try {
            return Integer.parseInt(translations.get("_langVersion"));
         } catch (NumberFormatException var2) {
            LOGGER.warn("Invalid language version format, defaulting to 0");
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static MutableComponent homeConfirmComponent(String homeName, String action, String commandConfirm, String commandDeny) {
      MutableComponent confirm = Component.literal("[Confirm]")
         .withStyle(style -> style.withColor(TextColor.fromRgb(5025616)))
         .withStyle(style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, commandConfirm)))
         .withStyle(
            style -> style.withHoverEvent(
                  new HoverEvent(
                     net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm " + action + " of home '" + homeName + "'")
                  )
               )
         );
      MutableComponent deny = Component.literal("[Deny]")
         .withStyle(style -> style.withColor(TextColor.fromRgb(16007990)))
         .withStyle(style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, commandDeny)))
         .withStyle(
            style -> style.withHoverEvent(
                  new HoverEvent(
                     net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to cancel " + action + " of home '" + homeName + "'")
                  )
               )
         );
      return Component.literal("")
         .append(Component.literal("Are you sure you want to " + action + " home '").withStyle(style -> style.withColor(TextColor.fromRgb(16766464))))
         .append(Component.literal(homeName).withStyle(style -> style.withColor(TextColor.fromRgb(16750592))))
         .append(Component.literal("'? "))
         .append(confirm)
         .append(Component.literal(" "))
         .append(deny);
   }

   private static File getNeoEssentialsLangCustomDir() {
      File langDir;
      try {
         Class<?> fmlPathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
         Method gamedirMethod = fmlPathsClass.getMethod("GAMEDIR");
         Object gamedirPath = gamedirMethod.invoke(null);
         Path serverRoot = (Path)gamedirPath.getClass().getMethod("get").invoke(gamedirPath);
         langDir = serverRoot.resolve("neoessentials").resolve("languages").resolve("custom").toFile();
         File legacyLangDir = serverRoot.resolve("neoessentials").resolve("lang").toFile();
         if (legacyLangDir.exists() && legacyLangDir.isDirectory()) {
            deleteDirectoryRecursively(legacyLangDir);
            LOGGER.info("Removed legacy language directory: {}", legacyLangDir.getAbsolutePath());
         }
      } catch (Exception var6) {
         File fallbackRoot = new File(System.getProperty("user.dir"), "neoessentials");
         langDir = new File(fallbackRoot, "languages/custom");
         File legacyLangDirx = new File(fallbackRoot, "lang");
         if (legacyLangDirx.exists() && legacyLangDirx.isDirectory()) {
            deleteDirectoryRecursively(legacyLangDirx);
            LOGGER.info("Removed legacy language directory: {}", legacyLangDirx.getAbsolutePath());
         }
      }

      return langDir;
   }

   private static void deleteDirectoryRecursively(File dir) {
      if (dir.isDirectory()) {
         File[] files = dir.listFiles();
         if (files != null) {
            for (File file : files) {
               deleteDirectoryRecursively(file);
            }
         }
      }

      dir.delete();
   }

   public static Map<String, String> loadCustomLanguageFile(String languageCode) {
      File customLangFile = new File("neoessentials/languages/custom/" + languageCode + ".json");
      if (!customLangFile.exists()) {
         return null;
      } else {
         try {
            Map var5;
            try (FileReader reader = new FileReader(customLangFile)) {
               Gson gson = new Gson();
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               var5 = (Map)gson.fromJson(reader, type);
            }

            return var5;
         } catch (FileNotFoundException var8) {
            var8.printStackTrace();
            return null;
         } catch (Exception var9) {
            var9.printStackTrace();
            return null;
         }
      }
   }

   public static Map<String, Map<String, String>> loadAllCustomLanguages() {
      Map<String, Map<String, String>> languages = new HashMap<>();
      File langDir = new File("neoessentials/languages/custom");
      if (langDir.exists() && langDir.isDirectory()) {
         File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
         if (files != null) {
            for (File file : files) {
               String langCode = file.getName().replace(".json", "");
               Map<String, String> langMap = loadCustomLanguageFile(langCode);
               if (langMap != null) {
                  languages.put(langCode, langMap);
               }
            }
         }
      }

      return languages;
   }
}
