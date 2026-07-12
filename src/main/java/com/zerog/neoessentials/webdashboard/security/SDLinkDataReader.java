package com.zerog.neoessentials.webdashboard.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SDLinkDataReader {
   private static final Logger LOGGER = LoggerFactory.getLogger(SDLinkDataReader.class);
   private final Path serverDirectory;

   public SDLinkDataReader(Path serverDirectory) {
      this.serverDirectory = serverDirectory;
   }

   public String getDiscordId(UUID minecraftUuid) {
      try {
         File dataFile = this.serverDirectory.resolve("config/sdlink/verifiedaccounts.json").toFile();
         if (!dataFile.exists()) {
            return null;
         } else {
            try (FileReader reader = new FileReader(dataFile)) {
               JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();

               for (String discordId : data.keySet()) {
                  JsonElement element = data.get(discordId);
                  if (element.isJsonObject()) {
                     JsonObject account = element.getAsJsonObject();
                     if (account.has("minecraftUuid")) {
                        String storedUuid = account.get("minecraftUuid").getAsString();
                        if (storedUuid.equals(minecraftUuid.toString())) {
                           return discordId;
                        }
                     }
                  }
               }

               return null;
            }
         }
      } catch (Exception var13) {
         LOGGER.error("Error reading SDLink data: {}", var13.getMessage());
         return null;
      }
   }
}
