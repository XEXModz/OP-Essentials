package com.zerog.neoessentials.webdashboard.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public class PlayerDataHandler implements HttpHandler {
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      CorsHandler.applyWithMethods(exchange, "GET, OPTIONS");
      if ("OPTIONS".equals(exchange.getRequestMethod())) {
         exchange.sendResponseHeaders(204, -1L);
      } else if (!"GET".equals(exchange.getRequestMethod())) {
         this.sendJsonResponse(exchange, 405, this.createErrorResponse("Method not allowed"));
      } else {
         String path = exchange.getRequestURI().getPath();
         String[] parts = path.replaceAll("^/+|/+$", "").split("/");
         if (parts.length >= 3 && parts[0].equals("api") && parts[1].equals("playerdata")) {
            String uuid = parts[2];
            File cwd = new File(System.getProperty("user.dir"));
            File foundFile = null;
            String foundWorld = null;
            if (cwd.exists() && cwd.isDirectory()) {
               File[] worldDirs = cwd.listFiles(File::isDirectory);
               if (worldDirs != null) {
                  for (File worldDir : worldDirs) {
                     File playerdataDir = new File(worldDir, "playerdata");
                     File candidate = new File(playerdataDir, uuid + ".dat");
                     if (candidate.exists()) {
                        foundFile = candidate;
                        foundWorld = worldDir.getName();
                        break;
                     }
                  }
               }
            }

            if (foundFile == null) {
               this.sendJsonResponse(
                  exchange,
                  404,
                  this.createErrorResponse(
                     "Player data file not found for uuid: " + uuid + " in any world folder under server root ('" + cwd.getAbsolutePath() + "')"
                  )
               );
            } else {
               try (FileInputStream fis = new FileInputStream(foundFile)) {
                  CompoundTag tag = null;

                  try {
                     tag = NbtIo.readCompressed(fis, NbtAccounter.create(Long.MAX_VALUE));
                  } catch (Exception var17) {
                     this.sendJsonResponse(
                        exchange, 500, this.createErrorResponse("NBT parse error for file: " + foundFile.getAbsolutePath() + ", reason: " + var17)
                     );
                     return;
                  }

                  if (tag != null) {
                     JsonObject result = this.nbtToJson(tag);
                     result.addProperty("_world", foundWorld);
                     this.sendJsonResponse(exchange, 200, result);
                  } else {
                     this.sendJsonResponse(exchange, 500, this.createErrorResponse("NBT tag is null for file: " + foundFile.getAbsolutePath()));
                  }
               } catch (Exception var19) {
                  this.sendJsonResponse(exchange, 500, this.createErrorResponse("Error reading file: " + foundFile.getAbsolutePath() + ", reason: " + var19));
               }
            }
         } else {
            try {
               JsonObject response = this.getAllPlayerNBTData();
               this.sendJsonResponse(exchange, 200, response);
            } catch (Exception var16) {
               this.sendJsonResponse(exchange, 500, this.createErrorResponse("Internal error: " + var16.getMessage()));
            }
         }
      }
   }

   private JsonObject getPlayerNBTData(String uuid) {
      File file = new File("run/world/playerdata/" + uuid + ".dat");
      if (file.exists()) {
         try {
            JsonObject e;
            try (FileInputStream fis = new FileInputStream(file)) {
               CompoundTag tag = null;

               try {
                  tag = NbtIo.readCompressed(fis, NbtAccounter.create(Long.MAX_VALUE));
               } catch (Exception var8) {
                  System.err.println("[PlayerDataHandler] Failed to parse NBT for file: " + file.getName() + " - " + var8);
                  return null;
               }

               if (tag == null) {
                  return null;
               }

               e = this.nbtToJson(tag);
            }

            return e;
         } catch (Exception var10) {
            System.err.println("[PlayerDataHandler] Error reading file: " + file.getName() + " - " + var10);
         }
      }

      return null;
   }

   private JsonObject getAllPlayerNBTData() {
      JsonObject allPlayers = new JsonObject();
      Map<String, String> uuidToName = new HashMap<>();

      try {
         File usercacheFile = new File("run/usercache.json");
         if (usercacheFile.exists()) {
            String usercacheContent = Files.readString(usercacheFile.toPath());

            for (JsonElement el : JsonParser.parseString(usercacheContent).getAsJsonArray()) {
               JsonObject obj = el.getAsJsonObject();
               String uuid = obj.get("uuid").getAsString();
               String name = obj.get("name").getAsString();
               uuidToName.put(uuid, name);
            }
         }
      } catch (Exception var27) {
         System.err.println("[PlayerDataHandler] Failed to load usercache.json: " + var27);
      }

      try {
         File usernamecacheFile = new File("run/usernamecache.json");
         if (usernamecacheFile.exists()) {
            String usernamecacheContent = Files.readString(usernamecacheFile.toPath());
            JsonObject obj = JsonParser.parseString(usernamecacheContent).getAsJsonObject();

            for (String uuid : obj.keySet()) {
               String name = obj.get(uuid).getAsString();
               uuidToName.put(uuid, name);
            }
         }
      } catch (Exception var26) {
         System.err.println("[PlayerDataHandler] Failed to load usernamecache.json: " + var26);
      }

      Set<String> allUuids = new HashSet<>(uuidToName.keySet());
      File cwd = new File(System.getProperty("user.dir"));
      if (cwd.exists() && cwd.isDirectory()) {
         File[] worldDirs = cwd.listFiles(File::isDirectory);
         if (worldDirs != null) {
            for (File worldDir : worldDirs) {
               File playerdataDir = new File(worldDir, "playerdata");
               if (playerdataDir.exists() && playerdataDir.isDirectory()) {
                  File[] files = playerdataDir.listFiles((dir, namex) -> namex.endsWith(".dat"));
                  if (files != null) {
                     for (File file : files) {
                        String uuid = file.getName().replace(".dat", "");
                        allUuids.add(uuid);

                        try (FileInputStream fis = new FileInputStream(file)) {
                           CompoundTag tag = null;

                           try {
                              tag = NbtIo.readCompressed(fis, NbtAccounter.create(Long.MAX_VALUE));
                           } catch (Exception var23) {
                              System.err.println("[PlayerDataHandler] Failed to parse NBT for file: " + file.getName() + " - " + var23);
                              continue;
                           }

                           if (tag != null) {
                              JsonObject playerJson = this.nbtToJson(tag);
                              playerJson.addProperty("_world", worldDir.getName());
                              String playerName = uuidToName.getOrDefault(uuid, uuid);
                              if (tag.contains("Name")) {
                                 playerName = tag.getString("Name");
                              } else if (tag.contains("name")) {
                                 playerName = tag.getString("name");
                              } else if (playerJson.has("Name")) {
                                 playerName = playerJson.get("Name").getAsString();
                              } else if (playerJson.has("name")) {
                                 playerName = playerJson.get("name").getAsString();
                              }

                              JsonObject entry = new JsonObject();
                              entry.addProperty("uuid", uuid);
                              entry.addProperty("name", playerName != null ? playerName : uuid);
                              entry.add("data", playerJson);
                              allPlayers.add(uuid, entry);
                           } else {
                              System.err.println("[PlayerDataHandler] NBT tag is null for file: " + file.getName());
                           }
                        } catch (Exception var25) {
                           System.err.println("[PlayerDataHandler] Error reading file: " + file.getName() + " - " + var25);
                        }
                     }
                  }
               }
            }
         }
      }

      for (String uuid : allUuids) {
         if (!allPlayers.has(uuid)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", uuid);
            entry.addProperty("name", uuidToName.getOrDefault(uuid, uuid));
            entry.add("data", new JsonObject());
            allPlayers.add(uuid, entry);
         }
      }

      return allPlayers;
   }

   private JsonObject nbtToJson(CompoundTag tag) {
      JsonObject obj = new JsonObject();

      for (String key : tag.getAllKeys()) {
         if (tag.contains(key)) {
            Tag nbtValue = tag.get(key);
            if (nbtValue == null) {
               obj.addProperty(key, (String)null);
            } else {
               int typeId = nbtValue.getId();
               switch (typeId) {
                  case 1:
                     obj.addProperty(key, tag.getByte(key));
                     break;
                  case 2:
                     obj.addProperty(key, tag.getShort(key));
                     break;
                  case 3:
                     obj.addProperty(key, tag.getInt(key));
                     break;
                  case 4:
                     obj.addProperty(key, tag.getLong(key));
                     break;
                  case 5:
                     obj.addProperty(key, tag.getFloat(key));
                     break;
                  case 6:
                     obj.addProperty(key, tag.getDouble(key));
                     break;
                  case 7:
                  default:
                     try {
                        obj.addProperty(key, nbtValue != null ? nbtValue.toString() : "null");
                     } catch (Exception var8) {
                        obj.addProperty(key, "null");
                     }
                     break;
                  case 8:
                     obj.addProperty(key, tag.getString(key));
               }
            }
         }
      }

      return obj;
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
      byte[] response = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.sendResponseHeaders(statusCode, (long)response.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(response);
      }
   }

   private JsonObject createErrorResponse(String message) {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("timestamp", System.currentTimeMillis());
      return error;
   }
}
