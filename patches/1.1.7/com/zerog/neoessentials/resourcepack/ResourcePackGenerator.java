package com.zerog.neoessentials.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcePackGenerator {
   private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackGenerator.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final int PACK_FORMAT = 34;
   private static final String PACK_NAME = "NeoEssentials-Badges";

   public static Path generateResourcePack() {
      try {
         LOGGER.info("Generating NeoEssentials badge resource pack...");
         Path badgesDir = getBadgesDirectory();
         if (!Files.exists(badgesDir)) {
            LOGGER.warn("Badges directory does not exist: {}", badgesDir);
            return null;
         } else {
            Map<String, Path> badgeImages = findBadgeImages(badgesDir);
            if (badgeImages.isEmpty()) {
               LOGGER.warn("No badge images found in {}", badgesDir);
               return null;
            } else {
               LOGGER.info("Found {} badge images to pack", badgeImages.size());
               Path tempDir = Files.createTempDirectory("neoessentials-pack-");

               Path var5;
               try {
                  buildPackStructure(tempDir, badgeImages);
                  Path outputZip = Paths.get("config/neoessentials/", "NeoEssentials-Badges.zip");
                  Files.createDirectories(outputZip.getParent());
                  createZipFile(tempDir, outputZip);
                  LOGGER.info("Resource pack generated successfully: {}", outputZip.toAbsolutePath());
                  String sha1 = calculateSHA1(outputZip);
                  LOGGER.info("Resource pack SHA-1: {}", sha1);
                  saveSHA1(sha1);
                  var5 = outputZip;
               } finally {
                  deleteDirectory(tempDir);
               }

               return var5;
            }
         }
      } catch (Exception var10) {
         LOGGER.error("Failed to generate resource pack: {}", var10.getMessage(), var10);
         return null;
      }
   }

   private static void buildPackStructure(Path packDir, Map<String, Path> badgeImages) throws IOException {
      createPackMeta(packDir);
      Path assetsDir = packDir.resolve("assets/neoessentials");
      Path fontDir = assetsDir.resolve("font");
      Path texturesDir = assetsDir.resolve("textures/badges");
      Files.createDirectories(fontDir);
      Files.createDirectories(texturesDir);

      for (Entry<String, Path> entry : badgeImages.entrySet()) {
         String rankName = entry.getKey();
         Path sourceImage = entry.getValue();
         Path targetImage = texturesDir.resolve(rankName + ".png");
         Files.copy(sourceImage, targetImage, StandardCopyOption.REPLACE_EXISTING);
         LOGGER.debug("Copied badge image: {} -> {}", rankName, targetImage);
      }

      createFontDefinition(fontDir, badgeImages);
   }

   private static void createPackMeta(Path packDir) throws IOException {
      JsonObject packMeta = new JsonObject();
      JsonObject pack = new JsonObject();
      pack.addProperty("pack_format", 34);
      pack.addProperty("description", "NeoEssentials Custom Badge Images");
      packMeta.add("pack", pack);
      Path metaFile = packDir.resolve("pack.mcmeta");

      try (Writer writer = Files.newBufferedWriter(metaFile)) {
         GSON.toJson(packMeta, writer);
      }

      LOGGER.debug("Created pack.mcmeta");
   }

   private static void createFontDefinition(Path fontDir, Map<String, Path> badgeImages) throws IOException {
      JsonObject fontDef = new JsonObject();
      JsonArray providers = new JsonArray();
      int unicodePoint = 57600;
      int imageSize = getConfiguredImageSize();

      for (String rankName : badgeImages.keySet()) {
         JsonObject provider = new JsonObject();
         provider.addProperty("type", "bitmap");
         provider.addProperty("file", "neoessentials:badges/" + rankName + ".png");
         provider.addProperty("ascent", imageSize / 2);
         provider.addProperty("height", imageSize);
         JsonArray chars = new JsonArray();
         chars.add(String.valueOf((char)unicodePoint));
         provider.add("chars", chars);
         providers.add(provider);
         LOGGER.debug("Mapped {} to \\u{}", rankName, Integer.toHexString(unicodePoint).toUpperCase());
         unicodePoint++;
      }

      fontDef.add("providers", providers);
      Path fontFile = fontDir.resolve("badges.json");

      try (Writer writer = Files.newBufferedWriter(fontFile)) {
         GSON.toJson(fontDef, writer);
      }

      LOGGER.debug("Created font definition with {} badge mappings", badgeImages.size());
   }

   private static void createZipFile(Path sourceDir, Path zipFile) throws IOException {
      if (Files.exists(zipFile)) {
         Files.delete(zipFile);
      }

      try (
         ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
         Stream<Path> pathStream = Files.walk(sourceDir);
      ) {
         pathStream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
            try {
               String zipEntryName = sourceDir.relativize(path).toString().replace("\\", "/");
               zos.putNextEntry(new ZipEntry(zipEntryName));
               Files.copy(path, zos);
               zos.closeEntry();
            } catch (IOException var4) {
               throw new UncheckedIOException(var4);
            }
         });
      }

      LOGGER.debug("Created ZIP file: {} ({}bytes)", zipFile, Files.size(zipFile));
   }

   private static String calculateSHA1(Path file) throws Exception {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");

      try (InputStream is = Files.newInputStream(file)) {
         byte[] buffer = new byte[8192];

         int read;
         while ((read = is.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
         }
      }

      byte[] hash = digest.digest();
      StringBuilder hexString = new StringBuilder();

      for (byte b : hash) {
         String hex = Integer.toHexString(255 & b);
         if (hex.length() == 1) {
            hexString.append('0');
         }

         hexString.append(hex);
      }

      return hexString.toString();
   }

   private static void saveSHA1(String sha1) throws IOException {
      Path sha1File = Paths.get("config/neoessentials/", "NeoEssentials-Badges.sha1");
      Files.writeString(sha1File, sha1);
      LOGGER.debug("Saved SHA-1 to {}", sha1File);
   }

   private static Map<String, Path> findBadgeImages(Path badgesDir) throws IOException {
      Map<String, Path> images = new HashMap<>();

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(badgesDir, "*.png")) {
         for (Path path : stream) {
            String fileName = path.getFileName().toString();
            String rankName = fileName.substring(0, fileName.lastIndexOf(46));
            images.put(rankName, path);
         }
      }

      return images;
   }

   private static void deleteDirectory(Path dir) {
      try {
         if (Files.exists(dir)) {
            try (Stream<Path> pathStream = Files.walk(dir)) {
               pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
                  try {
                     Files.delete(path);
                  } catch (IOException var2) {
                  }
               });
            }
         }
      } catch (IOException var6) {
         LOGGER.warn("Failed to cleanup temp directory: {}", var6.getMessage());
      }
   }

   private static Path getBadgesDirectory() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("customImagePath")) {
               return Paths.get(badges.get("customImagePath").getAsString());
            }
         }
      } catch (Exception var2) {
      }

      return Paths.get("config/neoessentials/badges");
   }

   private static int getConfiguredImageSize() {
      try {
         JsonObject chatConfig = ConfigManager.getInstance().getConfig("chat.json");
         if (chatConfig.has("badges")) {
            JsonObject badges = chatConfig.getAsJsonObject("badges");
            if (badges.has("customImageSize")) {
               return badges.get("customImageSize").getAsInt();
            }
         }
      } catch (Exception var2) {
      }

      return 16;
   }
}
