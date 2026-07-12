package com.zerog.neoessentials.webdashboard.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudProviderManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(CloudProviderManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final File TOKENS_FILE = new File("config/neoessentials/cloud_tokens.json");
   private static CloudProviderManager INSTANCE;
   private final Map<String, CloudProviderManager.CloudProviderToken> tokens = new HashMap<>();

   private CloudProviderManager() {
      this.loadTokens();
   }

   public static CloudProviderManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new CloudProviderManager();
      }

      return INSTANCE;
   }

   public boolean isProviderLinked(String providerName) {
      CloudProviderManager.CloudProviderToken token = this.tokens.get(providerName.toLowerCase());
      if (token == null) {
         return false;
      } else if (token.isExpired()) {
         LOGGER.debug("Token for {} is expired", providerName);
         return false;
      } else {
         return true;
      }
   }

   public String getAccessToken(String providerName) {
      CloudProviderManager.CloudProviderToken token = this.tokens.get(providerName.toLowerCase());
      return token != null && !token.isExpired() ? token.getAccessToken() : null;
   }

   public void storeToken(String providerName, String accessToken, String refreshToken, long expiresIn) {
      CloudProviderManager.CloudProviderToken token = new CloudProviderManager.CloudProviderToken(
         providerName, accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000L
      );
      this.tokens.put(providerName.toLowerCase(), token);
      this.saveTokens();
      LOGGER.info("Stored OAuth token for provider: {}", providerName);
   }

   public void removeToken(String providerName) {
      this.tokens.remove(providerName.toLowerCase());
      this.saveTokens();
      LOGGER.info("Removed OAuth token for provider: {}", providerName);
   }

   private void loadTokens() {
      if (!TOKENS_FILE.exists()) {
         LOGGER.debug("Cloud tokens file does not exist, starting with empty tokens");
      } else {
         try (FileReader reader = new FileReader(TOKENS_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("tokens")) {
               JsonObject tokensObj = root.getAsJsonObject("tokens");

               for (String providerName : tokensObj.keySet()) {
                  JsonObject tokenObj = tokensObj.getAsJsonObject(providerName);
                  CloudProviderManager.CloudProviderToken token = new CloudProviderManager.CloudProviderToken(
                     tokenObj.get("provider").getAsString(),
                     tokenObj.get("accessToken").getAsString(),
                     tokenObj.has("refreshToken") ? tokenObj.get("refreshToken").getAsString() : null,
                     tokenObj.get("expiresAt").getAsLong()
                  );
                  this.tokens.put(providerName.toLowerCase(), token);
               }
            }

            LOGGER.info("Loaded {} cloud provider token(s)", this.tokens.size());
         } catch (Exception var10) {
            LOGGER.error("Failed to load cloud tokens", var10);
         }
      }
   }

   private void saveTokens() {
      try {
         File parentDir = TOKENS_FILE.getParentFile();
         if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            LOGGER.error("Failed to create directory for cloud tokens");
            return;
         }

         JsonObject root = new JsonObject();
         JsonObject tokensObj = new JsonObject();

         for (Entry<String, CloudProviderManager.CloudProviderToken> entry : this.tokens.entrySet()) {
            CloudProviderManager.CloudProviderToken token = entry.getValue();
            JsonObject tokenObj = new JsonObject();
            tokenObj.addProperty("provider", token.getProviderName());
            tokenObj.addProperty("accessToken", token.getAccessToken());
            if (token.getRefreshToken() != null) {
               tokenObj.addProperty("refreshToken", token.getRefreshToken());
            }

            tokenObj.addProperty("expiresAt", token.getExpiresAt());
            tokensObj.add(entry.getKey(), tokenObj);
         }

         root.add("tokens", tokensObj);
         root.addProperty("_version", 1);

         try (FileWriter writer = new FileWriter(TOKENS_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
         }

         LOGGER.debug("Saved {} cloud provider token(s)", this.tokens.size());
      } catch (IOException var10) {
         LOGGER.error("Failed to save cloud tokens", var10);
      }
   }

   private static class CloudProviderToken {
      private final String providerName;
      private final String accessToken;
      private final String refreshToken;
      private final long expiresAt;

      public CloudProviderToken(String providerName, String accessToken, String refreshToken, long expiresAt) {
         this.providerName = providerName;
         this.accessToken = accessToken;
         this.refreshToken = refreshToken;
         this.expiresAt = expiresAt;
      }

      public String getProviderName() {
         return this.providerName;
      }

      public String getAccessToken() {
         return this.accessToken;
      }

      public String getRefreshToken() {
         return this.refreshToken;
      }

      public long getExpiresAt() {
         return this.expiresAt;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() >= this.expiresAt;
      }
   }
}
