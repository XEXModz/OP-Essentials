package com.zerog.neoessentials.i18n;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomLanguageManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(CustomLanguageManager.class);
   private static CustomLanguageManager INSTANCE;
   private static final String LANG_DIR = "neoessentials/languages/custom/";
   private static final String LANG_FILE = "en_us.json";
   private final Path customLangDir;
   private final Path templatesDir;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final Set<String> missingKeys = ConcurrentHashMap.newKeySet();
   private final Map<String, Map<String, String>> customTranslations = new ConcurrentHashMap<>();
   private final Map<String, CustomLanguageManager.LanguageFileInfo> languageFiles = new ConcurrentHashMap<>();

   private CustomLanguageManager() {
      this.customLangDir = resolveModDataPath("languages", "custom");
      this.templatesDir = resolveModDataPath("languages", "templates");
      LOGGER.info("[LANG] Custom language directory set to: {}", this.customLangDir.toAbsolutePath());
      LOGGER.info("[LANG] Template directory set to: {}", this.templatesDir.toAbsolutePath());
   }

   public static CustomLanguageManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new CustomLanguageManager();
      }

      return INSTANCE;
   }

   public void initialize() {
      try {
         LOGGER.info("Custom language directory resolved to: {}", this.customLangDir.toAbsolutePath());
         LOGGER.info("Template directory resolved to: {}", this.templatesDir.toAbsolutePath());
         Files.createDirectories(this.customLangDir);
         Files.createDirectories(this.templatesDir);
         Path langFile = this.customLangDir.resolve("en_us.json");
         boolean needsCopy = false;
         if (!Files.exists(langFile)) {
            needsCopy = true;
         } else {
            try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               Map<String, String> test = (Map<String, String>)this.gson.fromJson(reader, type);
               if (test == null || test.isEmpty()) {
                  needsCopy = true;
               }
            } catch (Exception var18) {
               needsCopy = true;
            }
         }

         if (needsCopy) {
            try (InputStream in = this.findLangResource("en_us.json")) {
               if (in != null) {
                  Files.copy(in, langFile, StandardCopyOption.REPLACE_EXISTING);
                  LOGGER.info("Copied language file from JAR: {}", langFile.toAbsolutePath());
               } else {
                  LOGGER.error("Failed to copy language file: Resource not found for {}!", "en_us.json");
               }
            } catch (Exception var16) {
               LOGGER.error("Exception while copying language file from JAR: {}", var16.getMessage(), var16);
            }
         } else {
            Map<String, String> jarLang = this.loadBaseTranslations();

            Map<String, String> fileLang;
            try (Reader readerx = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               fileLang = (Map<String, String>)this.gson.fromJson(readerx, type);
            }

            boolean updated = false;
            if (fileLang != null && jarLang != null) {
               for (Entry<String, String> entry : jarLang.entrySet()) {
                  if (!fileLang.containsKey(entry.getKey())) {
                     fileLang.put(entry.getKey(), entry.getValue());
                     updated = true;
                  }
               }

               if (updated) {
                  try (Writer writer = Files.newBufferedWriter(langFile, StandardCharsets.UTF_8)) {
                     this.gson.toJson(fileLang, writer);
                     LOGGER.info("Merged missing keys from JAR into language file: {}", langFile.toAbsolutePath());
                  }
               }
            }
         }

         if (!Files.exists(langFile)) {
            LOGGER.error(
               "Critical: Language file {} still does not exist after copy attempt! Translations will not be loaded from disk.", langFile.toAbsolutePath()
            );
         }

         this.deployBundledLanguageFiles();
         this.scanCustomLanguageFiles();
         this.generateTemplatesIfNeeded();
         LOGGER.info("Custom Language Manager initialized");
         LOGGER.info("  Custom languages found: {}", this.languageFiles.keySet());
         LOGGER.info("  Custom language directory: {}", this.customLangDir.toAbsolutePath());
         LOGGER.info("  Template directory: {}", this.templatesDir.toAbsolutePath());
      } catch (Exception var19) {
         LOGGER.error("Failed to initialize custom language manager", var19);
      }
   }

   private void scanCustomLanguageFiles() {
      try {
         if (!Files.exists(this.customLangDir)) {
            return;
         }

         try (Stream<Path> stream = Files.list(this.customLangDir)) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(this::loadCustomLanguageFile);
         }
      } catch (IOException var6) {
         LOGGER.error("Failed to scan custom language files", var6);
      }
   }

   private void loadCustomLanguageFile(Path filePath) {
      try {
         String fileName = filePath.getFileName().toString();
         String langCode = fileName.replace(".json", "");
         String content = Files.readString(filePath, StandardCharsets.UTF_8);
         Type type = (new TypeToken<Map<String, String>>() {
         }).getType();
         Map<String, String> translations = (Map<String, String>)this.gson.fromJson(content, type);
         if (translations != null && !translations.isEmpty()) {
            this.customTranslations.put(langCode, translations);
            String nativeName = translations.getOrDefault("_nativeName", langCode);
            String englishName = translations.getOrDefault("_englishName", langCode);
            String author = translations.getOrDefault("_author", "Unknown");
            String version = translations.getOrDefault("_version", "1.0");
            this.languageFiles.put(langCode, new CustomLanguageManager.LanguageFileInfo(langCode, nativeName, englishName, author, version, filePath));
            LOGGER.info("Loaded custom language: {} ({}) - {} keys", new Object[]{langCode, nativeName, translations.size()});
         }
      } catch (Exception var11) {
         LOGGER.error("Failed to load custom language file: {}", filePath, var11);
      }
   }

   public String getTranslation(String key, String languageCode) {
      Map<String, String> customLang = this.customTranslations.get(languageCode);
      if (customLang != null && customLang.containsKey(key)) {
         return customLang.get(key);
      } else if (MessageUtil.hasTranslation(key)) {
         return MessageUtil.localize(key);
      } else {
         this.missingKeys.add(key);
         return key;
      }
   }

   public boolean hasCustomLanguage(String languageCode) {
      return this.customTranslations.containsKey(languageCode);
   }

   public List<CustomLanguageManager.LanguageFileInfo> getCustomLanguages() {
      return new ArrayList<>(this.languageFiles.values());
   }

   private void generateTemplatesIfNeeded() {
      String[] languagesToGenerate = new String[]{"es_es", "fr_fr", "de_de", "it_it", "pt_br", "ru_ru", "ja_jp", "ko_kr", "zh_cn", "nl_nl"};

      for (String langCode : languagesToGenerate) {
         Path templatePath = this.templatesDir.resolve(langCode + "_template.json");
         if (!Files.exists(templatePath)) {
            this.generateTemplate(langCode, templatePath);
         }
      }
   }

   public void generateTemplate(String languageCode, Path outputPath) {
      try {
         Map<String, String> baseTranslations = this.loadBaseTranslations();
         Map<String, String> template = new LinkedHashMap<>();
         template.put("_comment", "NeoEssentials Custom Language File");
         template.put("_nativeName", "Language Name (in native language)");
         template.put("_englishName", "Language Name (in English)");
         template.put("_languageCode", languageCode);
         template.put("_author", "Your Name");
         template.put("_version", "1.0");
         template.put("_minModVersion", "1.0.2.4");
         template.put("_lastUpdated", new Date().toString());
         template.put("_rtl", "false");
         template.put("", "");
         template.put(
            "_instructions",
            "To translate: Replace the English text on the right side of each line with your language. Keep the {0}, {1} placeholders exactly as they are."
         );
         template.put("__example", "For example: \"commands.example\": \"Your translation here with {0} placeholder\"");
         template.put("___", "");

         for (Entry<String, String> entry : baseTranslations.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("_")) {
               template.put(key, "[TRANSLATE] " + entry.getValue());
            }
         }

         Files.createDirectories(outputPath.getParent());

         try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputPath.toFile()), StandardCharsets.UTF_8)) {
            this.gson.toJson(template, writer);
         }

         LOGGER.info("Generated language template: {} ({} keys)", outputPath, template.size());
      } catch (Exception var10) {
         LOGGER.error("Failed to generate language template for {}", languageCode, var10);
      }
   }

   public void exportMissingKeys(Path outputPath) {
      try {
         Map<String, String> missingTemplate = new LinkedHashMap<>();
         missingTemplate.put("_comment", "Missing Translation Keys - Add these to your language file");
         missingTemplate.put("_generated", new Date().toString());
         missingTemplate.put("_count", String.valueOf(this.missingKeys.size()));
         missingTemplate.put("", "");

         for (String key : this.missingKeys) {
            missingTemplate.put(key, "[TRANSLATE] " + key);
         }

         try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputPath.toFile()), StandardCharsets.UTF_8)) {
            this.gson.toJson(missingTemplate, writer);
         }

         LOGGER.info("Exported {} missing keys to {}", this.missingKeys.size(), outputPath);
      } catch (Exception var8) {
         LOGGER.error("Failed to export missing keys", var8);
      }
   }

   private Map<String, String> loadBaseTranslations() {
      try {
         InputStream is = this.getClass().getClassLoader().getResourceAsStream("data/lang/en_us.json");
         if (is != null) {
            Map var4;
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               var4 = (Map)this.gson.fromJson(reader, type);
            }

            return var4;
         }
      } catch (Exception var7) {
         LOGGER.error("Failed to load base translations", var7);
      }

      return new HashMap<>();
   }

   public void reload() {
      this.customTranslations.clear();
      this.languageFiles.clear();
      this.scanCustomLanguageFiles();
      LOGGER.info("Reloaded custom languages: {}", this.languageFiles.keySet());
   }

   public Map<String, Object> getStatistics() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("customLanguagesLoaded", this.customTranslations.size());
      stats.put("languageCodes", new ArrayList<>(this.customTranslations.keySet()));
      stats.put("missingKeysTracked", this.missingKeys.size());
      stats.put("customLanguageDirectory", this.customLangDir.toAbsolutePath().toString());
      stats.put("templateDirectory", this.templatesDir.toAbsolutePath().toString());
      return stats;
   }

   public void clearMissingKeys() {
      this.missingKeys.clear();
   }

   public Set<String> getMissingKeys() {
      return new HashSet<>(this.missingKeys);
   }

   private void deployBundledLanguageFiles() {
      String[] bundledLangs = new String[]{"fr_fr", "de_de", "es_es", "pt_br", "zh_cn", "nl_nl", "pl_pl", "ru_ru"};
      int deployed = 0;
      int merged = 0;

      for (String langCode : bundledLangs) {
         String fileName = langCode + ".json";
         Path target = this.customLangDir.resolve(fileName);

         try (InputStream in = this.findLangResource(fileName)) {
            if (in == null) {
               LOGGER.debug("No bundled lang file found in JAR for: {}", langCode);
            } else if (!Files.exists(target)) {
               Files.copy(in, target);
               LOGGER.info("Deployed bundled language file: {}", fileName);
               deployed++;
            } else {
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();

               Map<String, String> jarLang;
               try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                  jarLang = (Map<String, String>)this.gson.fromJson(reader, type);
               }

               Map<String, String> diskLang;
               try (BufferedReader updated = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                  diskLang = (Map<String, String>)this.gson.fromJson(updated, type);
               }

               if (jarLang != null && diskLang != null) {
                  boolean updated = false;

                  for (Entry<String, String> entry : jarLang.entrySet()) {
                     if (!diskLang.containsKey(entry.getKey())) {
                        diskLang.put(entry.getKey(), entry.getValue());
                        updated = true;
                     }
                  }

                  if (updated) {
                     try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                        this.gson.toJson(diskLang, writer);
                     }

                     LOGGER.info("Merged new keys from JAR into {}", fileName);
                     merged++;
                  }
               }
            }
         } catch (Exception var26) {
            LOGGER.error("Failed to deploy/merge bundled language file {}: {}", fileName, var26.getMessage());
         }
      }

      if (deployed > 0 || merged > 0) {
         LOGGER.info("Language deployment complete: {} deployed, {} merged", deployed, merged);
      }
   }

   private InputStream findLangResource(String filename) {
      String[] paths = new String[]{"/data/lang/" + filename, "data/lang/" + filename};

      for (String path : paths) {
         InputStream in = this.getClass().getResourceAsStream(path);
         if (in != null) {
            LOGGER.info("Found language resource at: {}", path);
            return in;
         }

         LOGGER.warn("Language resource not found at: {}", path);
      }

      for (String path : paths) {
         InputStream in = this.getClass().getClassLoader().getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
         if (in != null) {
            LOGGER.info("Found language resource via classloader at: {}", path);
            return in;
         }

         LOGGER.warn("ClassLoader: Language resource not found at: {}", path);
      }

      try {
         LOGGER.error("Language resource not found. Listing all resources in JAR for debugging:");
         URL jar = this.getClass().getProtectionDomain().getCodeSource().getLocation();

         try (JarFile jarFile = new JarFile(jar.getPath())) {
            jarFile.stream().forEach(entry -> {
               if (entry.getName().contains("lang")) {
                  LOGGER.error("  JAR entry: {}", entry.getName());
               }
            });
         }
      } catch (Exception var10) {
         LOGGER.error("Failed to list JAR resources: {}", var10.getMessage());
      }

      return null;
   }

   private static Path resolveModDataPath(String... parts) {
      Path serverRoot = FMLPaths.GAMEDIR.get();
      Path neoEssentialsDir = serverRoot.resolve("neoessentials");

      for (String part : parts) {
         neoEssentialsDir = neoEssentialsDir.resolve(part);
      }

      LOGGER.info("[LANG] Resolved mod data path: {}", neoEssentialsDir.toAbsolutePath());
      return neoEssentialsDir;
   }

   public static class LanguageFileInfo {
      private final String languageCode;
      private final String nativeName;
      private final String englishName;
      private final String author;
      private final String version;
      private final Path filePath;

      public LanguageFileInfo(String languageCode, String nativeName, String englishName, String author, String version, Path filePath) {
         this.languageCode = languageCode;
         this.nativeName = nativeName;
         this.englishName = englishName;
         this.author = author;
         this.version = version;
         this.filePath = filePath;
      }

      public String getLanguageCode() {
         return this.languageCode;
      }

      public String getNativeName() {
         return this.nativeName;
      }

      public String getEnglishName() {
         return this.englishName;
      }

      public String getAuthor() {
         return this.author;
      }

      public String getVersion() {
         return this.version;
      }

      public Path getFilePath() {
         return this.filePath;
      }
   }
}
