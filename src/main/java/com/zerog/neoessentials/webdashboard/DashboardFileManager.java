package com.zerog.neoessentials.webdashboard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardFileManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DashboardFileManager.class);
   private static final String DASHBOARD_DIR = "neoessentials/webdashboard/";
   private static final String VERSION_FILE = "neoessentials/webdashboard/.version";
   private static final List<String> DASHBOARD_FILES = Arrays.asList(
      "index.html", "permissions.html", "admin.html", "dashboard.js", "permissions.js", "styles.css"
   );

   public static void ensureDashboardFiles() {
      try {
         File dashboardDir = new File("neoessentials/webdashboard/");
         if (!dashboardDir.exists()) {
            if (!dashboardDir.mkdirs()) {
               LOGGER.error("Failed to create dashboard directory: {}", "neoessentials/webdashboard/");
               return;
            }

            LOGGER.info("Created dashboard directory: {}", "neoessentials/webdashboard/");
         }

         String currentVersion = getCurrentModVersion();
         String installedVersion = getInstalledDashboardVersion();
         LOGGER.debug("Dashboard version check - Current: {}, Installed: {}", currentVersion, installedVersion);
         boolean needsUpdate = shouldUpdateDashboard(currentVersion, installedVersion);
         if (needsUpdate) {
            LOGGER.info("Dashboard files need update. Extracting from JAR...");
            extractDashboardFiles();
            saveInstalledVersion(currentVersion);
            LOGGER.info("Dashboard files updated to version {}", currentVersion);
         } else {
            boolean allFilesExist = verifyDashboardFiles();
            if (!allFilesExist) {
               LOGGER.info("Some dashboard files are missing. Re-extracting...");
               extractDashboardFiles();
               saveInstalledVersion(currentVersion);
            }
         }
      } catch (Exception var5) {
         LOGGER.error("Error ensuring dashboard files are up to date", var5);
      }
   }

   private static String getCurrentModVersion() {
      try (InputStream in = DashboardFileManager.class.getResourceAsStream("/build_number.txt")) {
         return in != null ? new String(in.readAllBytes(), StandardCharsets.UTF_8).trim() : "unknown";
      } catch (Exception var5) {
         LOGGER.debug("Could not read build number: {}", var5.getMessage());
         return "unknown";
      }
   }

   private static String getInstalledDashboardVersion() {
      File versionFile = new File("neoessentials/webdashboard/.version");
      if (!versionFile.exists()) {
         return "none";
      } else {
         try {
            return Files.readString(versionFile.toPath(), StandardCharsets.UTF_8).trim();
         } catch (IOException var2) {
            LOGGER.debug("Could not read dashboard version file: {}", var2.getMessage());
            return "unknown";
         }
      }
   }

   private static void saveInstalledVersion(String version) {
      try {
         File versionFile = new File("neoessentials/webdashboard/.version");
         Files.writeString(versionFile.toPath(), version, StandardCharsets.UTF_8);
      } catch (IOException var2) {
         LOGGER.error("Could not save dashboard version file: {}", var2.getMessage());
      }
   }

   private static boolean shouldUpdateDashboard(String currentVersion, String installedVersion) {
      return "none".equals(installedVersion) || "unknown".equals(installedVersion) ? true : !currentVersion.equals(installedVersion);
   }

   private static boolean verifyDashboardFiles() {
      for (String fileName : DASHBOARD_FILES) {
         File file = new File("neoessentials/webdashboard/" + fileName);
         if (!file.exists()) {
            LOGGER.debug("Dashboard file missing: {}", fileName);
            return false;
         }
      }

      return true;
   }

   private static void extractDashboardFiles() {
      int successCount = 0;
      int failCount = 0;

      for (String fileName : DASHBOARD_FILES) {
         if (extractFile(fileName)) {
            successCount++;
         } else {
            failCount++;
         }
      }

      if (successCount > 0) {
         LOGGER.info("Extracted {} dashboard file(s) successfully", successCount);
      }

      if (failCount > 0) {
         LOGGER.warn("Failed to extract {} dashboard file(s)", failCount);
      }
   }

   private static boolean extractFile(String fileName) {
      String jarPath = "/webdashboard/" + fileName;
      File targetFile = new File("neoessentials/webdashboard/" + fileName);

      try {
         boolean var14;
         try (InputStream in = DashboardFileManager.class.getResourceAsStream(jarPath)) {
            if (in == null) {
               LOGGER.warn("Dashboard file not found in JAR: {}", jarPath);
               return false;
            }

            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
               LOGGER.error("Failed to create parent directory for {}", fileName);
               return false;
            }

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
               byte[] buffer = new byte[8192];

               int len;
               while ((len = in.read(buffer)) > 0) {
                  out.write(buffer, 0, len);
               }
            }

            LOGGER.debug("Extracted dashboard file: {}", fileName);
            var14 = true;
         }

         return var14;
      } catch (IOException var12) {
         LOGGER.error("Failed to extract dashboard file {}: {}", fileName, var12.getMessage());
         return false;
      }
   }

   public static Path getExternalDashboardFile(String fileName) {
      File file = new File("neoessentials/webdashboard/" + fileName);
      return file.exists() && file.isFile() ? file.toPath() : null;
   }

   public static InputStream getDashboardFileStream(String fileName) throws IOException {
      Path externalFile = getExternalDashboardFile(fileName);
      if (externalFile != null) {
         LOGGER.debug("Serving dashboard file from external directory: {}", fileName);
         return Files.newInputStream(externalFile);
      } else {
         String jarPath = "/webdashboard/" + fileName;
         InputStream jarStream = DashboardFileManager.class.getResourceAsStream(jarPath);
         if (jarStream != null) {
            LOGGER.debug("Serving dashboard file from JAR: {}", fileName);
            return jarStream;
         } else {
            throw new FileNotFoundException("Dashboard file not found: " + fileName);
         }
      }
   }

   public static boolean isUsingExternalFiles() {
      return new File("neoessentials/webdashboard/").exists() && verifyDashboardFiles();
   }

   public static void forceUpdateDashboardFiles() {
      LOGGER.info("Forcing dashboard files update...");
      extractDashboardFiles();
      String currentVersion = getCurrentModVersion();
      saveInstalledVersion(currentVersion);
      LOGGER.info("Dashboard files force-updated to version {}", currentVersion);
   }
}
