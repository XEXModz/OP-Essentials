package com.zerog.neoessentials.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceUtil {
   public static final String CONFIG_DIR = "config/neoessentials/";
   public static final String DATA_DIR = "neoessentials/";
   public static final String JAR_CONFIG_PATH = "/data/config/neoessentials/";
   public static final String JAR_LANG_PATH = "/data/lang/";
   public static final String JAR_ASSETS_PATH = "/assets/neoessentials/";

   public static File getConfigFile(String filename) {
      return new File("config/neoessentials/" + filename);
   }

   public static File getDataFile(String filename) {
      return new File("neoessentials/" + filename);
   }

   public static Path getConfigPath(String filename) {
      return Paths.get("config/neoessentials/" + filename);
   }

   public static Path getDataPath(String filename) {
      return Paths.get("neoessentials/" + filename);
   }

   public static InputStream getJarConfigResource(String filename) {
      return ResourceUtil.class.getResourceAsStream("/data/config/neoessentials/" + filename);
   }

   public static InputStream getJarLangResource(String filename) {
      return ResourceUtil.class.getResourceAsStream("/data/lang/" + filename);
   }

   public static InputStream getJarAssetResource(String filename) {
      return ResourceUtil.class.getResourceAsStream("/assets/neoessentials/" + filename);
   }

   public static void ensureDirectoryExists(String dirPath) {
      File dir = new File(dirPath);
      if (!dir.exists() && !dir.mkdirs()) {
         System.err.println("Failed to create directory: " + dirPath);
      }
   }

   public static void ensureConfigDirectory() {
      ensureDirectoryExists("config/neoessentials/");
   }

   public static void ensureDataDirectory() {
      ensureDirectoryExists("neoessentials/");
   }

   public static File getLanguageFile(String locale) {
      return getDataFile("lang/" + locale + ".json");
   }

   public static InputStream getJarLanguageResource(String locale) {
      return getJarLangResource(locale + ".json");
   }
}
