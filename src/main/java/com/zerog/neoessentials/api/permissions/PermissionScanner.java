package com.zerog.neoessentials.api.permissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionScanner {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionScanner.class);
   private static final List<Pattern> PERMISSION_PATTERNS = Arrays.asList(
      Pattern.compile("\"(neoessentials\\.[a-z0-9._-]+)\"", 2),
      Pattern.compile("PERMISSION_[A-Z_]+\\s*=\\s*\"(neoessentials\\.[a-z0-9._-]+)\"", 2),
      Pattern.compile("hasPermission\\([^,]+,\\s*\"(neoessentials\\.[a-z0-9._-]+)\"\\)", 2),
      Pattern.compile("PermissionAPI\\.hasPermission\\([^,]+,\\s*\"(neoessentials\\.[a-z0-9._-]+)\"\\)", 2),
      Pattern.compile("validatePermission\\([^,]+,\\s*\"(neoessentials\\.[a-z0-9._-]+)\"\\)", 2),
      Pattern.compile("register\\(\\s*\"(neoessentials\\.[a-z0-9._-]+)\"", 2)
   );
   private static final List<Pattern> DYNAMIC_PATTERNS = Arrays.asList(
      Pattern.compile("\"neoessentials\\.kits\\.\"\\s*\\+\\s*([a-zA-Z0-9_]+)", 2), Pattern.compile("\"(neoessentials\\.[a-z0-9._-]+)\\.\"\\s*\\+", 2)
   );
   private final Set<String> discoveredPermissions = ConcurrentHashMap.newKeySet();
   private final Set<String> dynamicPermissionPrefixes = ConcurrentHashMap.newKeySet();
   private final Map<String, Set<String>> filePermissionMap = new ConcurrentHashMap<>();

   public static PermissionScanner getInstance() {
      return PermissionScanner.SingletonHolder.INSTANCE;
   }

   private PermissionScanner() {
   }

   public void scanForPermissions() {
      LOGGER.info("Starting automatic permission discovery...");
      this.discoveredPermissions.clear();
      this.dynamicPermissionPrefixes.clear();
      this.filePermissionMap.clear();

      try {
         URI sourceUri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
         if (sourceUri.toString().endsWith(".jar")) {
            LOGGER.debug("Detected JAR execution: {}", sourceUri);

            try {
               this.scanJarFile(sourceUri);
            } catch (Exception var4) {
               LOGGER.debug("JAR scanning failed (this is normal): {}", var4.getMessage());
               this.generateKnownPermissions();
            }
         } else {
            Path sourcePath = Paths.get(sourceUri);
            if (sourcePath != null) {
               Path rootPath = sourcePath.getParent();
               if (rootPath != null) {
                  LOGGER.debug("Detected development environment: {}", rootPath);
                  this.scanSourceDirectory(rootPath);
               } else {
                  LOGGER.debug("Could not determine root path, using fallback discovery");
                  this.generateKnownPermissions();
               }
            } else {
               LOGGER.debug("Source path is null, using fallback discovery");
               this.generateKnownPermissions();
            }
         }

         LOGGER.info("Permission discovery completed. Found {} permissions across {} files", this.discoveredPermissions.size(), this.filePermissionMap.size());
         if (!this.discoveredPermissions.isEmpty()) {
            this.logDiscoveredPermissions();
         } else {
            LOGGER.info("No permissions discovered from file scanning. All permissions are registered in PermissionRegistry.");
         }
      } catch (Exception var5) {
         LOGGER.warn("Error during permission scanning: {}", var5.getMessage());
         LOGGER.info("Using fallback permission discovery method");
         this.generateKnownPermissions();
      }
   }

   private void scanSourceDirectory(Path rootPath) throws IOException {
      if (rootPath == null) {
         LOGGER.warn("Root path is null, cannot scan source directory");
      } else {
         Path javaSourcePath = rootPath.resolve("src").resolve("main").resolve("java");
         if (Files.exists(javaSourcePath)) {
            LOGGER.debug("Scanning source directory: {}", javaSourcePath);
            this.scanDirectory(javaSourcePath);
         } else {
            LOGGER.debug("Java source path not found, scanning from: {}", rootPath);
            this.scanDirectory(rootPath);
         }
      }
   }

   private void scanJarFile(URI jarUri) throws IOException {
      LOGGER.debug("Attempting to scan JAR file: {}", jarUri);

      try (FileSystem jarFs = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
         Path jarRoot = jarFs.getPath("/");

         try (Stream<Path> paths = Files.walk(jarRoot)) {
            long classCount = paths.filter(path -> path.toString().endsWith(".class"))
               .filter(path -> path.toString().contains("neoessentials"))
               .peek(path -> LOGGER.debug("Scanning class file: {}", path))
               .peek(this::scanClassFile)
               .count();
            LOGGER.debug("Scanned {} class files from JAR", classCount);
         }
      } catch (Exception var11) {
         LOGGER.warn("Failed to scan JAR file: {}. Error: {}", jarUri, var11.getMessage());
         LOGGER.info("This is normal in some deployment environments. Using registered permissions only.");
      }
   }

   private void scanDirectory(Path directory) throws IOException {
      if (Files.exists(directory)) {
         try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::scanJavaFile);
         }
      }
   }

   private void scanJavaFile(Path javaFile) {
      try {
         String content = Files.readString(javaFile);
         this.scanContent(content, javaFile.toString());
      } catch (IOException var3) {
         LOGGER.warn("Could not read Java file: {}", javaFile, var3);
      }
   }

   private void scanClassFile(Path classFile) {
      String className = classFile.toString();
      if (className.contains("neoessentials")) {
         LOGGER.debug("Found NeoEssentials class: {}", className);
      }
   }

   private void scanContent(String content, String fileName) {
      Set<String> filePermissions = new HashSet<>();

      for (Pattern pattern : PERMISSION_PATTERNS) {
         Matcher matcher = pattern.matcher(content);

         while (matcher.find()) {
            String permission = matcher.group(1).toLowerCase();
            if (this.isValidPermission(permission)) {
               this.discoveredPermissions.add(permission);
               filePermissions.add(permission);
               LOGGER.debug("Found permission '{}' in {}", permission, fileName);
            }
         }
      }

      for (Pattern pattern : DYNAMIC_PATTERNS) {
         Matcher matcher = pattern.matcher(content);

         while (matcher.find()) {
            String prefix = matcher.group(1).toLowerCase();
            if (this.isValidPermission(prefix)) {
               this.dynamicPermissionPrefixes.add(prefix);
               LOGGER.debug("Found dynamic permission prefix '{}' in {}", prefix, fileName);
            }
         }
      }

      if (!filePermissions.isEmpty()) {
         this.filePermissionMap.put(fileName, filePermissions);
      }
   }

   private boolean isValidPermission(String permission) {
      if (permission == null || permission.trim().isEmpty()) {
         return false;
      } else if (!permission.startsWith("neoessentials.")) {
         return false;
      } else if (!permission.matches("^[a-z0-9._-]+$")) {
         return false;
      } else if (permission.endsWith(".")) {
         return false;
      } else if (permission.contains("..")) {
         return false;
      } else {
         String[] parts = permission.split("\\.");
         return parts.length >= 2;
      }
   }

   public Set<String> getDiscoveredPermissions() {
      return new HashSet<>(this.discoveredPermissions);
   }

   public Set<String> getDynamicPermissionPrefixes() {
      return new HashSet<>(this.dynamicPermissionPrefixes);
   }

   public Map<String, Set<String>> getFilePermissionMap() {
      return new HashMap<>(this.filePermissionMap);
   }

   public Map<String, Set<String>> getPermissionsByCategory() {
      Map<String, Set<String>> categoryMap = new HashMap<>();

      for (String permission : this.discoveredPermissions) {
         String[] parts = permission.split("\\.");
         if (parts.length >= 2) {
            String category = parts[1];
            categoryMap.computeIfAbsent(category, k -> new HashSet<>()).add(permission);
         }
      }

      return categoryMap;
   }

   public Set<String> generateDynamicPermissions(Set<String> dynamicValues) {
      Set<String> generated = new HashSet<>();

      for (String prefix : this.dynamicPermissionPrefixes) {
         for (String value : dynamicValues) {
            String dynamicPermission = prefix + "." + value.toLowerCase();
            if (this.isValidPermission(dynamicPermission)) {
               generated.add(dynamicPermission);
            }
         }
      }

      return generated;
   }

   private void logDiscoveredPermissions() {
      Map<String, Set<String>> categories = this.getPermissionsByCategory();
      LOGGER.info("=== DISCOVERED PERMISSIONS BY CATEGORY ===");

      for (Entry<String, Set<String>> entry : categories.entrySet()) {
         String category = entry.getKey();
         Set<String> perms = entry.getValue();
         LOGGER.info("{} ({}): {}", new Object[]{category.toUpperCase(), perms.size(), String.join(", ", perms.stream().sorted().toArray(String[]::new))});
      }

      if (!this.dynamicPermissionPrefixes.isEmpty()) {
         LOGGER.info(
            "DYNAMIC PREFIXES ({}): {}",
            this.dynamicPermissionPrefixes.size(),
            String.join(", ", this.dynamicPermissionPrefixes.stream().sorted().toArray(String[]::new))
         );
      }

      LOGGER.info("=== END PERMISSION DISCOVERY REPORT ===");
   }

   public List<String> exportDiscoveredPermissions() {
      List<String> export = new ArrayList<>();
      export.add("# Auto-Discovered NeoEssentials Permissions");
      export.add("# Total discovered: " + this.discoveredPermissions.size() + " permissions");
      export.add("# Dynamic prefixes: " + this.dynamicPermissionPrefixes.size());
      export.add("");
      Map<String, Set<String>> categories = this.getPermissionsByCategory();

      for (Entry<String, Set<String>> entry : categories.entrySet()) {
         String category = entry.getKey();
         Set<String> perms = entry.getValue();
         export.add("## " + category.toUpperCase() + " (" + perms.size() + " permissions)");
         export.add("");
         perms.stream().sorted().forEach(perm -> export.add(perm + " - Auto-discovered permission"));
         export.add("");
      }

      if (!this.dynamicPermissionPrefixes.isEmpty()) {
         export.add("## DYNAMIC PERMISSION PREFIXES");
         export.add("# These prefixes are used to generate permissions dynamically (e.g., for kits)");
         export.add("");
         this.dynamicPermissionPrefixes.stream().sorted().forEach(prefix -> export.add(prefix + ".* - Dynamic permission prefix"));
      }

      return export;
   }

   private void generateKnownPermissions() {
      LOGGER.debug("Loading permissions from permissions_nodes.txt resource file");

      try {
         InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("data/config/permissions_nodes.txt");
         if (inputStream == null) {
            LOGGER.warn("Could not find permissions_nodes.txt in resources, using hardcoded fallback");
            this.loadHardcodedFallback();
            return;
         }

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            int loadedCount = 0;

            String line;
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("//")) {
                  int dashIndex = line.indexOf(" -");
                  String permission;
                  if (dashIndex > 0) {
                     permission = line.substring(0, dashIndex).trim();
                  } else {
                     permission = line;
                  }

                  if (this.isValidPermission(permission)) {
                     this.discoveredPermissions.add(permission);
                     loadedCount++;
                     LOGGER.debug("Loaded permission from file: {}", permission);
                  } else {
                     LOGGER.debug("Skipping invalid permission line: {}", line);
                  }
               }
            }

            LOGGER.info("Loaded {} permissions from permissions_nodes.txt for PermissionsEX integration", loadedCount);
         } catch (IOException var9) {
            LOGGER.error("Error reading permissions_nodes.txt: {}", var9.getMessage());
            this.loadHardcodedFallback();
         }
      } catch (Exception var10) {
         LOGGER.error("Unexpected error loading permissions from file: {}", var10.getMessage());
         this.loadHardcodedFallback();
      }
   }

   private void loadHardcodedFallback() {
      LOGGER.debug("Using hardcoded permission fallback");
      this.addDiscoveredPermission("neoessentials.*", "All NeoEssentials permissions");
      this.addDiscoveredPermission("neoessentials.teleport.*", "All teleportation permissions");
      this.addDiscoveredPermission("neoessentials.teleport.admin.*", "All admin teleport permissions");
      this.addDiscoveredPermission("neoessentials.teleport.home.*", "All home permissions");
      this.addDiscoveredPermission("neoessentials.teleport.spawn.*", "All spawn permissions");
      this.addDiscoveredPermission("neoessentials.teleport.warp.*", "All warp permissions");
      this.addDiscoveredPermission("neoessentials.teleport.request.*", "All teleport request permissions");
      this.addDiscoveredPermission("neoessentials.teleport.misc.*", "All misc teleport permissions");
      this.addDiscoveredPermission("neoessentials.economy.*", "All economy permissions");
      this.addDiscoveredPermission("neoessentials.chat.*", "All chat permissions");
      this.addDiscoveredPermission("neoessentials.kits.*", "All kit permissions");
      this.addDiscoveredPermission("neoessentials.admin.*", "All admin permissions");
      this.addDiscoveredPermission("neoessentials.utility.*", "All utility permissions");
      LOGGER.warn("Loaded {} hardcoded fallback permissions (permissions_nodes.txt not available)", this.discoveredPermissions.size());
   }

   private void addDiscoveredPermission(String permission, String source) {
      if (this.isValidPermission(permission)) {
         this.discoveredPermissions.add(permission);
         LOGGER.debug("Added fallback permission: {}", permission);
      }
   }

   private static class SingletonHolder {
      private static final PermissionScanner INSTANCE = new PermissionScanner();
   }
}
