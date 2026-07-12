package com.zerog.neoessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigSplitter {
   private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSplitter.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Map<String, String> CONFIG_FILE_MAP = new LinkedHashMap<String, String>() {
      {
         this.put("modules", "modules.json");
         this.put("logging", "main.json");
         this.put("permissions", "main.json");
         this.put("security", "security.json");
         this.put("commands", "commands.json");
         this.put("webDashboard", "webdashboard.json");
         this.put("items", "items.json");
         this.put("afk", "afk.json");
         this.put("kits", "kits.json");
         this.put("teleportation", "teleportation.json");
         this.put("moderation", "moderation.json");
         this.put("chat", "chat.json");
         this.put("tablist", "tablist.json");
      }
   };
   private static final Map<String, Integer> SPLIT_CONFIG_VERSIONS = new HashMap<String, Integer>() {
      {
         this.put("main.json", Integer.valueOf(1));
         this.put("commands.json", Integer.valueOf(1));
         this.put("chat.json", Integer.valueOf(1));
         this.put("teleportation.json", Integer.valueOf(1));
         this.put("moderation.json", Integer.valueOf(1));
         this.put("webdashboard.json", Integer.valueOf(1));
         this.put("items.json", Integer.valueOf(1));
         this.put("afk.json", Integer.valueOf(1));
         this.put("security.json", Integer.valueOf(1));
         this.put("modules.json", Integer.valueOf(1));
         this.put("tablist.json", Integer.valueOf(1));
      }
   };
   private static boolean shouldNotifyAdmins = false;

   public static boolean isSplittingEnabled() {
      File configDir = new File("config/neoessentials/");
      File marker = new File(configDir, ".split_configs");
      return marker.exists();
   }

   public static void ensureSplitConfigsUpToDate() {
      if (isSplittingEnabled()) {
         File configFile = ResourceUtil.getConfigFile("config.json");
         ConfigSplitter splitter = new ConfigSplitter();
         if (!splitter.ensureUnifiedConfigExists(configFile)) {
            LOGGER.error("config.json is missing and could not be generated. Split config update aborted.");
         } else {
            LOGGER.debug("Checking split config file versions...");
            boolean needsResplit = false;
            if (configFile.exists()) {
               long configJsonLastModified = configFile.lastModified();

               for (String fileName : SPLIT_CONFIG_VERSIONS.keySet()) {
                  File splitFile = ResourceUtil.getConfigFile(fileName);
                  if (!splitFile.exists() || configJsonLastModified > splitFile.lastModified()) {
                     needsResplit = true;
                     break;
                  }
               }
            }

            if (needsResplit) {
               LOGGER.info("config.json is newer than split configs or split file missing. Re-splitting config.json into split files...");
               migrateToSplitConfigs();
            } else {
               for (Entry<String, Integer> entry : SPLIT_CONFIG_VERSIONS.entrySet()) {
                  String fileNamex = entry.getKey();
                  int expectedVersion = entry.getValue();
                  File splitFile = ResourceUtil.getConfigFile(fileNamex);
                  if (!splitFile.exists()) {
                     File unifiedConfig = ResourceUtil.getConfigFile("config.json");
                     boolean generated = false;
                     if (unifiedConfig.exists()) {
                        try (FileReader reader = new FileReader(unifiedConfig, StandardCharsets.UTF_8)) {
                           JsonObject config = (JsonObject)GSON.fromJson(reader, JsonObject.class);
                           String sectionName = null;

                           for (Entry<String, String> mapEntry : CONFIG_FILE_MAP.entrySet()) {
                              if (mapEntry.getValue().equals(fileNamex)) {
                                 sectionName = mapEntry.getKey();
                                 break;
                              }
                           }

                           if (sectionName != null && config.has(sectionName)) {
                              JsonObject section = extractSection(config, sectionName, fileNamex);

                              try (FileWriter writer = new FileWriter(splitFile, StandardCharsets.UTF_8)) {
                                 GSON.toJson(section, writer);
                                 LOGGER.info("  ✓ Generated {} from unified config.json", fileNamex);
                                 generated = true;
                              }
                           } else {
                              LOGGER.warn("Section '{}' not found in config.json for split config {}", sectionName, fileNamex);
                           }
                        } catch (Exception var24) {
                           LOGGER.error("Failed to generate split config {}: {}", fileNamex, var24.getMessage());
                        }
                     }

                     if (!splitFile.exists() && !generated) {
                        LOGGER.warn("Split config file {} could not be generated from config.json and will remain missing.", fileNamex);
                     }
                  } else {
                     checkSplitConfigVersion(fileNamex, splitFile, expectedVersion);
                  }
               }

               File unifiedConfigx = ResourceUtil.getConfigFile("config.json");
               if (unifiedConfigx.exists()) {
                  try (FileReader reader = new FileReader(unifiedConfigx, StandardCharsets.UTF_8)) {
                     JsonObject unified = JsonParser.parseReader(reader).getAsJsonObject();

                     for (Entry<String, String> entryx : CONFIG_FILE_MAP.entrySet()) {
                        String sectionName = entryx.getKey();
                        String fileNamex = entryx.getValue();
                        File splitFile = ResourceUtil.getConfigFile(fileNamex);
                        if (splitFile.exists() && unified.has(sectionName)) {
                           mergeSectionIntoSplitFile(sectionName, fileNamex, unified.getAsJsonObject(sectionName));
                        }
                     }
                  } catch (Exception var22) {
                     LOGGER.error("Failed to merge unified config into split files: {}", var22.getMessage());
                  }
               }
            }
         }
      }
   }

   public static boolean migrateToSplitConfigs() {
      try {
         File configFile = ResourceUtil.getConfigFile("config.json");
         if (!configFile.exists()) {
            LOGGER.warn("config.json not found, cannot migrate to split configs");
            return false;
         } else {
            LOGGER.info("========================================");
            LOGGER.info("Migrating to split configuration files...");
            LOGGER.info("========================================");

            JsonObject config;
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
               config = JsonParser.parseReader(reader).getAsJsonObject();
            }

            File var18 = new File(configFile.getParentFile(), "config.json.backup");
            Files.copy(configFile.toPath(), var18.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Created backup: config.json.backup");
            int filesCreated = 0;

            for (Entry<String, String> entry : CONFIG_FILE_MAP.entrySet()) {
               String sectionName = entry.getKey();
               String fileName = entry.getValue();
               if (config.has(sectionName)) {
                  JsonObject section = extractSection(config, sectionName, fileName);
                  File targetFile = ResourceUtil.getConfigFile(fileName);
                  if (!targetFile.exists() || sectionName.equals("modules") || sectionName.equals("logging") || sectionName.equals("permissions")) {
                     try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
                        GSON.toJson(section, writer);
                        filesCreated++;
                        LOGGER.info("  ✓ Created {}", fileName);
                     }
                  }
               }
            }

            File configDir = new File("config/neoessentials/");
            File marker = new File(configDir, ".split_configs");
            if (marker.createNewFile()) {
               LOGGER.info("Created split configs marker file");
            }

            replaceWithStubFile(configFile);
            LOGGER.info("Replaced config.json with minimal stub file");
            LOGGER.info("========================================");
            LOGGER.info("Migration complete! Created {} config files", filesCreated);
            LOGGER.info("Original config backed up to: config.json.backup");
            LOGGER.info("You can now edit smaller, focused config files!");
            LOGGER.info("========================================");
            return true;
         }
      } catch (Exception var17) {
         LOGGER.error("Failed to migrate to split configs: {}", var17.getMessage(), var17);
         return false;
      }
   }

   private static JsonObject extractSection(JsonObject mainConfig, String sectionName, String targetFile) {
      JsonObject result = new JsonObject();
      Integer version = SPLIT_CONFIG_VERSIONS.get(targetFile);
      if (version != null) {
         result.addProperty("_configVersion", version);
         result.addProperty("_configVersion_comment", "DO NOT MODIFY: This field is used by NeoEssentials for automatic config updates.");
      }

      if (targetFile.equals("main.json")) {
         if (mainConfig.has("modules")) {
            result.add("modules", mainConfig.get("modules"));
         }

         if (mainConfig.has("logging")) {
            result.add("logging", mainConfig.get("logging"));
         }

         if (mainConfig.has("permissions")) {
            result.add("permissions", mainConfig.get("permissions"));
         }
      } else if (mainConfig.has(sectionName)) {
         result.add(sectionName, mainConfig.get(sectionName));
      }

      return result;
   }

   public static JsonObject mergeSplitConfigs() {
      JsonObject merged = new JsonObject();
      merged.addProperty("_configVersion", 13);
      merged.addProperty("_configVersion_comment", "NOTE: This is a virtual merged view. Edit individual config files instead.");

      for (Entry<String, String> entry : CONFIG_FILE_MAP.entrySet()) {
         String sectionName = entry.getKey();
         String fileName = entry.getValue();
         File configFile = ResourceUtil.getConfigFile(fileName);
         if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
               JsonObject fileConfig = JsonParser.parseReader(reader).getAsJsonObject();
               if (fileName.equals("main.json")) {
                  if (fileConfig.has("modules")) {
                     merged.add("modules", fileConfig.get("modules"));
                  }

                  if (fileConfig.has("logging")) {
                     merged.add("logging", fileConfig.get("logging"));
                  }

                  if (fileConfig.has("permissions")) {
                     merged.add("permissions", fileConfig.get("permissions"));
                  }
               } else if (fileConfig.has(sectionName)) {
                  merged.add(sectionName, fileConfig.get(sectionName));
               }
            } catch (Exception var11) {
               LOGGER.error("Failed to load split config {}: {}", fileName, var11.getMessage());
            }
         }
      }

      return merged;
   }

   public static boolean isFreshInstall() {
      File configFile = ResourceUtil.getConfigFile("config.json");
      File configDir = new File("config/neoessentials/");
      return configDir.exists() && configFile.exists() ? !isSplittingEnabled() : true;
   }

   public static boolean autoSplitForFreshInstall() {
      File configFile = ResourceUtil.getConfigFile("config.json");
      if (!configFile.exists()) {
         LOGGER.info("========================================");
         LOGGER.info("Fresh NeoEssentials installation detected!");
         LOGGER.info("Automatically creating split configuration files...");
         LOGGER.info("========================================");
         return createSplitConfigsFromJar();
      } else {
         return false;
      }
   }

   private static boolean createSplitConfigsFromJar() {
      try {
         Map<String, String> splitFiles = new LinkedHashMap<String, String>() {
            {
               this.put("main.json", "main.json");
               this.put("commands.json", "commands.json");
               this.put("chat.json", "chat.json");
               this.put("security.json", "security.json");
               this.put("items.json", "items.json");
               this.put("afk.json", "afk.json");
               this.put("moderation.json", "moderation.json");
               this.put("teleportation.json", "teleportation.json");
               this.put("webdashboard.json", "webdashboard.json");
            }
         };
         int successCount = 0;

         for (Entry<String, String> entry : splitFiles.entrySet()) {
            String fileName = entry.getValue();
            File targetFile = ResourceUtil.getConfigFile(fileName);

            try (InputStream in = ResourceUtil.getJarConfigResource(fileName)) {
               if (in != null) {
                  File parentDir = targetFile.getParentFile();
                  if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                     LOGGER.warn("Could not create parent directory for {}", fileName);
                  }

                  try (FileOutputStream out = new FileOutputStream(targetFile)) {
                     byte[] buffer = new byte[8192];

                     int len;
                     while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                     }
                  }

                  successCount++;
                  LOGGER.info("  ✓ Created {}", fileName);
               }
            } catch (Exception var15) {
               LOGGER.debug("Could not load {} from JAR, will be created later", fileName);
            }
         }

         File configDir = new File("config/neoessentials/");
         File marker = new File(configDir, ".split_configs");
         if (marker.createNewFile()) {
            LOGGER.info("✓ Enabled split configs mode");
         }

         LOGGER.info("========================================");
         LOGGER.info("Split configuration files created successfully! ({} files)", successCount);
         LOGGER.info("Your server is configured with easier-to-manage config files.");
         LOGGER.info("========================================");
         return true;
      } catch (Exception var16) {
         LOGGER.error("Failed to create split configs: {}", var16.getMessage(), var16);
         return false;
      }
   }

   public static void checkAndPromptMigration() {
      if (!isSplittingEnabled()) {
         File configFile = ResourceUtil.getConfigFile("config.json");
         if (configFile.exists()) {
            LOGGER.info("========================================");
            LOGGER.info("NOTICE: Large config.json detected!");
            LOGGER.info("NeoEssentials now supports split configuration files for easier editing.");
            LOGGER.info("To enable, run: /neoessentials config split");
            LOGGER.info("This will split config.json into smaller, focused files.");
            LOGGER.info("========================================");
            shouldNotifyAdmins = true;
         }
      }
   }

   public static boolean shouldNotifyAdmins() {
      return shouldNotifyAdmins;
   }

   public static void markAdminsNotified() {
      shouldNotifyAdmins = false;
   }

   private static void replaceWithStubFile(File configFile) throws IOException {
      JsonObject stub = new JsonObject();
      stub.addProperty("_configVersion", 13);
      stub.addProperty("_configVersion_comment", "DO NOT MODIFY: This field is used by NeoEssentials for automatic config updates.");
      stub.addProperty("_notice", "This server is using SPLIT CONFIGURATION FILES for easier management.");
      stub.addProperty("_notice_info", "Configuration has been split into smaller, focused files in the config/neoessentials/ directory.");
      JsonObject guide = new JsonObject();
      guide.addProperty("main.json", "Core settings: modules, logging, permissions");
      guide.addProperty("commands.json", "Command settings and toggles");
      guide.addProperty("chat.json", "Chat formatting, channels, badges, anti-spam");
      guide.addProperty("teleportation.json", "Teleport settings, homes, warps, spawn");
      guide.addProperty("moderation.json", "Ban, kick, mute, freeze, jail settings");
      guide.addProperty("webdashboard.json", "Web dashboard configuration");
      guide.addProperty("items.json", "Item management and repair settings");
      guide.addProperty("afk.json", "AFK system configuration");
      guide.addProperty("security.json", "Security and validation settings");
      guide.addProperty("kits.json", "Kit definitions (separate file)");
      stub.add("_split_config_files", guide);
      stub.addProperty(
         "_restore_instructions", "To restore the monolithic config.json, delete the .split_configs marker file and restore from config.json.backup"
      );

      try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
         GSON.toJson(stub, writer);
      }
   }

   private static void mergeSectionIntoSplitFile(String sectionName, String fileName, JsonObject unifiedSection) {
      File splitFile = ResourceUtil.getConfigFile(fileName);

      try (FileReader reader = new FileReader(splitFile, StandardCharsets.UTF_8)) {
         JsonObject splitConfig = JsonParser.parseReader(reader).getAsJsonObject();
         boolean changed = false;
         if (fileName.equals("main.json")) {
            if (!splitConfig.has(sectionName)) {
               splitConfig.add(sectionName, unifiedSection);
               changed = true;
            } else {
               JsonObject splitSection = splitConfig.getAsJsonObject(sectionName);
               changed |= mergeJsonObjects(splitSection, unifiedSection);
            }
         } else if (!splitConfig.has(sectionName)) {
            splitConfig.add(sectionName, unifiedSection);
            changed = true;
         } else {
            JsonObject splitSection = splitConfig.getAsJsonObject(sectionName);
            changed |= mergeJsonObjects(splitSection, unifiedSection);
         }

         if (changed) {
            try (FileWriter writer = new FileWriter(splitFile, StandardCharsets.UTF_8)) {
               GSON.toJson(splitConfig, writer);
            }

            LOGGER.info("Updated split config {} with new keys from unified config", fileName);
         }
      } catch (Exception var14) {
         LOGGER.error("Failed to merge section {} into split file {}: {}", new Object[]{sectionName, fileName, var14.getMessage()});
      }
   }

   private static boolean mergeJsonObjects(JsonObject target, JsonObject source) {
      boolean changed = false;

      for (Entry<String, JsonElement> entry : source.entrySet()) {
         String key = entry.getKey();
         JsonElement value = entry.getValue();
         if (!target.has(key)) {
            target.add(key, value);
            changed = true;
         } else if (value.isJsonObject() && target.get(key).isJsonObject()) {
            changed |= mergeJsonObjects(target.getAsJsonObject(key), value.getAsJsonObject());
         }
      }

      return changed;
   }

   private static void checkSplitConfigVersion(String fileName, File configFile, int expectedVersion) {
      try {
         try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject onDisk = JsonParser.parseReader(reader).getAsJsonObject();
            int currentVersion = 0;
            if (onDisk.has("_configVersion")) {
               currentVersion = onDisk.get("_configVersion").getAsInt();
            }

            if (currentVersion >= expectedVersion) {
               if (currentVersion > expectedVersion) {
                  LOGGER.warn("Split config {} has newer version ({}) than expected ({})", new Object[]{fileName, currentVersion, expectedVersion});
               } else {
                  LOGGER.debug("Split config {} is up to date (version {})", fileName, currentVersion);
               }

               return;
            }

            LOGGER.warn(
               "Split config {} is outdated (version {} < {}). Merging new keys from JAR template (user values preserved)...",
               new Object[]{fileName, currentVersion, expectedVersion}
            );
            JsonObject jarTemplate = null;

            try (InputStream in = ResourceUtil.getJarConfigResource(fileName)) {
               if (in != null) {
                  jarTemplate = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
               }
            } catch (Exception var18) {
               LOGGER.error("Could not load JAR template for {}: {}", fileName, var18.getMessage());
            }

            if (jarTemplate != null) {
               String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
               String backupName = fileName.replace(".json", String.format("_v%d_backup_%s.json", currentVersion, timestamp));
               File backupFile = new File(configFile.getParentFile(), backupName);
               Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               LOGGER.info("Created backup: {}", backupName);
               mergeJsonObjects(onDisk, jarTemplate);
               onDisk.addProperty("_configVersion", expectedVersion);

               try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                  GSON.toJson(onDisk, writer);
               }

               LOGGER.info("Merged split config {} to version {}", fileName, expectedVersion);
               return;
            }

            LOGGER.warn("JAR template not found for {}. Skipping update.", fileName);
         }
      } catch (Exception var20) {
         LOGGER.error("Failed to check version for split config {}: {}", fileName, var20.getMessage());
      }
   }

   private static void copyDefaultSplitConfig(String fileName) {
      try (InputStream in = ResourceUtil.getJarConfigResource(fileName)) {
         if (in == null) {
            LOGGER.warn("Default split config not found in JAR: {}", fileName);
         } else {
            File targetFile = ResourceUtil.getConfigFile(fileName);
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
               LOGGER.warn("Could not create parent directory for {}", fileName);
            }

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
               byte[] buffer = new byte[8192];

               int len;
               while ((len = in.read(buffer)) > 0) {
                  out.write(buffer, 0, len);
               }
            }

            LOGGER.debug("Copied default split config: {}", fileName);
         }
      } catch (Exception var11) {
         LOGGER.error("Failed to copy default split config {}: {}", fileName, var11.getMessage());
      }
   }

   private boolean ensureUnifiedConfigExists(File configFile) {
      if (configFile.exists()) {
         return true;
      } else {
         File parentDir = configFile.getParentFile();
         if (!parentDir.exists() && !parentDir.mkdirs()) {
            LOGGER.error("Failed to create config directory: {}", parentDir.getAbsolutePath());
            return false;
         } else {
            try {
               boolean var4;
               try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("data/config/neoessentials/config.json")) {
                  if (in != null) {
                     Files.copy(in, configFile.toPath());
                     LOGGER.info("Generated missing config.json from JAR default");
                     return true;
                  }

                  LOGGER.error("Could not find default config.json in JAR (data/config/neoessentials/config.json)");
                  var4 = false;
               }

               return var4;
            } catch (IOException var8) {
               LOGGER.error("Failed to generate config.json from JAR default", var8);
               return false;
            }
         }
      }
   }
}
