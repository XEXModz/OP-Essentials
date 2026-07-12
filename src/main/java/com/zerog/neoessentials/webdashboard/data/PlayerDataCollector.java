package com.zerog.neoessentials.webdashboard.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.teleportation.HomeManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerDataCollector {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataCollector.class);
   private final MinecraftServer server;

   public PlayerDataCollector(MinecraftServer server) {
      this.server = server;
   }

   public JsonObject getPlayerProfile(UUID playerUuid) {
      JsonObject profile = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         profile.addProperty("uuid", playerUuid.toString());
         profile.addProperty("username", player.getName().getString());
         profile.addProperty("displayName", player.getDisplayName().getString());
         profile.addProperty("online", true);
         profile.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
         profile.addProperty("operator", player.hasPermissions(4));
      } else {
         profile.addProperty("uuid", playerUuid.toString());
         profile.addProperty("online", false);
         String username = null;
         GameProfileCache cache = this.server.getProfileCache();
         if (cache != null) {
            Optional<GameProfile> profileOpt = cache.get(playerUuid);
            if (profileOpt.isPresent() && profileOpt.get().getName() != null) {
               username = profileOpt.get().getName();
            }
         }

         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (username == null && playerData != null) {
            if (playerData.contains("bukkit")) {
               CompoundTag bukkitData = playerData.getCompound("bukkit");
               if (bukkitData.contains("lastKnownName")) {
                  username = bukkitData.getString("lastKnownName");
               }
            }

            if (username == null && playerData.contains("lastKnownName")) {
               username = playerData.getString("lastKnownName");
            }
         }

         if (username != null) {
            profile.addProperty("username", username);
            profile.addProperty("displayName", username);
         } else {
            profile.addProperty("username", "Unknown");
            profile.addProperty("displayName", "Unknown Player");
         }

         if (playerData != null) {
            int gameModeId = playerData.getInt("playerGameType");

            String gameMode = switch (gameModeId) {
               case 0 -> "survival";
               case 1 -> "creative";
               case 2 -> "adventure";
               case 3 -> "spectator";
               default -> "unknown";
            };
            profile.addProperty("gameMode", gameMode);

            try {
               ServerLevel overworld = this.server.overworld();
               Path worldPath = overworld.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
               Path playerDataFile = worldPath.resolve(playerUuid + ".dat");
               if (Files.exists(playerDataFile)) {
                  long lastModified = Files.getLastModifiedTime(playerDataFile).toMillis();
                  profile.addProperty("lastSeen", lastModified);
               }
            } catch (Exception var14) {
               LOGGER.debug("Could not get last seen time for {}: {}", playerUuid, var14.getMessage());
            }
         } else {
            profile.addProperty("gameMode", "unknown");
         }
      }

      return profile;
   }

   public JsonObject getPlayerStatistics(UUID playerUuid) {
      JsonObject stats = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         JsonObject combat = new JsonObject();
         stats.add("combat", combat);
         JsonObject building = new JsonObject();
         stats.add("building", building);
         JsonObject movement = new JsonObject();
         stats.add("movement", movement);
      }

      return stats;
   }

   public JsonObject getPlayerAchievements(UUID playerUuid) {
      JsonObject achievements = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         JsonArray completed = new JsonArray();
         JsonArray inProgress = new JsonArray();
         int[] notStarted = new int[]{0};
         PlayerAdvancements playerAdvancements = player.getAdvancements();
         this.server.getAdvancements().getAllAdvancements().forEach(advancement -> {
            if (advancement.value().display().isPresent()) {
               AdvancementProgress progress = playerAdvancements.getOrStartProgress(advancement);
               DisplayInfo display = (DisplayInfo)advancement.value().display().get();
               JsonObject adv = new JsonObject();
               adv.addProperty("id", advancement.id().toString());
               adv.addProperty("title", display.getTitle().getString());
               adv.addProperty("description", display.getDescription().getString());
               adv.addProperty("isDone", progress.isDone());
               if (progress.isDone()) {
                  completed.add(adv);
               } else if (progress.hasProgress()) {
                  adv.addProperty("progress", progress.getPercent());
                  inProgress.add(adv);
               } else {
                  notStarted[0]++;
               }
            }
         });
         int totalAdvancements = completed.size() + inProgress.size() + notStarted[0];
         achievements.add("completed", completed);
         achievements.add("inProgress", inProgress);
         achievements.addProperty("total", totalAdvancements);
         achievements.addProperty("completedCount", completed.size());
         achievements.addProperty("inProgressCount", inProgress.size());
         achievements.addProperty("notStartedCount", notStarted[0]);
      } else {
         try {
            LevelResource advancementsFolder = new LevelResource("advancements");
            Path advPath = this.server.getWorldPath(advancementsFolder).resolve(playerUuid.toString() + ".json");
            if (Files.exists(advPath)) {
               String jsonContent = Files.readString(advPath);
               JsonObject advData = JsonParser.parseString(jsonContent).getAsJsonObject();
               JsonArray completed = new JsonArray();
               JsonArray inProgress = new JsonArray();
               int[] notStarted = new int[]{0};
               int[] totalAdvancements = new int[]{0};
               this.server.getAdvancements().getAllAdvancements().forEach(advancement -> {
                  if (advancement.value().display().isPresent()) {
                     totalAdvancements[0]++;
                     String advId = advancement.id().toString();
                     if (advData.has(advId)) {
                        JsonObject advProgress = advData.getAsJsonObject(advId);
                        if (advProgress.has("done") && advProgress.get("done").getAsBoolean()) {
                           JsonObject adv = new JsonObject();
                           adv.addProperty("id", advId);
                           adv.addProperty("title", ((DisplayInfo)advancement.value().display().get()).getTitle().getString());
                           adv.addProperty("isDone", true);
                           completed.add(adv);
                        } else if (advProgress.has("criteria")) {
                           JsonObject adv = new JsonObject();
                           adv.addProperty("id", advId);
                           adv.addProperty("title", ((DisplayInfo)advancement.value().display().get()).getTitle().getString());
                           adv.addProperty("isDone", false);
                           inProgress.add(adv);
                        }
                     } else {
                        notStarted[0]++;
                     }
                  }
               });
               achievements.add("completed", completed);
               achievements.add("inProgress", inProgress);
               achievements.addProperty("total", totalAdvancements[0]);
               achievements.addProperty("completedCount", completed.size());
               achievements.addProperty("inProgressCount", inProgress.size());
               achievements.addProperty("notStartedCount", notStarted[0]);
            } else {
               int totalAdvancements = (int)this.server
                  .getAdvancements()
                  .getAllAdvancements()
                  .stream()
                  .filter(adv -> adv.value().display().isPresent())
                  .count();
               achievements.add("completed", new JsonArray());
               achievements.add("inProgress", new JsonArray());
               achievements.addProperty("total", totalAdvancements);
               achievements.addProperty("completedCount", 0);
               achievements.addProperty("inProgressCount", 0);
               achievements.addProperty("notStartedCount", totalAdvancements);
            }
         } catch (Exception var12) {
            LOGGER.error("Error loading offline player advancements for {}", playerUuid, var12);
            achievements.add("completed", new JsonArray());
            achievements.add("inProgress", new JsonArray());
            achievements.addProperty("total", 0);
            achievements.addProperty("completedCount", 0);
            achievements.addProperty("inProgressCount", 0);
            achievements.addProperty("error", "Could not load advancement data: " + var12.getMessage());
         }
      }

      return achievements;
   }

   public JsonObject getPlayerInventory(UUID playerUuid) {
      JsonObject inventory = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         JsonArray mainInventory = new JsonArray();
         JsonArray armor = new JsonArray();
         JsonArray offhand = new JsonArray();

         for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            JsonObject item = this.serializeItemStack(player.getInventory().getItem(i));
            item.addProperty("slot", i);
            mainInventory.add(item);
         }

         player.getInventory().armor.forEach(itemStack -> armor.add(this.serializeItemStack(itemStack)));
         player.getInventory().offhand.forEach(itemStack -> offhand.add(this.serializeItemStack(itemStack)));
         inventory.add("main", mainInventory);
         inventory.add("armor", armor);
         inventory.add("offhand", offhand);
      } else {
         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (playerData != null) {
            inventory = this.parseInventoryFromNBT(playerData);
         }
      }

      return inventory;
   }

   private CompoundTag loadOfflinePlayerData(UUID playerUuid) {
      try {
         ServerLevel overworld = this.server.overworld();
         Path worldPath = overworld.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
         Path playerDataFile = worldPath.resolve(playerUuid + ".dat");
         LOGGER.debug("Loading offline player data from: {}", playerDataFile);
         if (Files.exists(playerDataFile)) {
            return NbtIo.readCompressed(playerDataFile, NbtAccounter.unlimitedHeap());
         }

         LOGGER.debug("Player data file not found: {}", playerDataFile);
      } catch (IOException var5) {
         LOGGER.error("Failed to load offline player data for UUID: {}", playerUuid, var5);
      }

      return null;
   }

   private JsonObject parseInventoryFromNBT(CompoundTag playerData) {
      JsonObject inventory = new JsonObject();
      JsonArray mainInventory = new JsonArray();
      JsonArray armor = new JsonArray();
      JsonArray offhand = new JsonArray();
      if (playerData.contains("Inventory", 9)) {
         ListTag invList = playerData.getList("Inventory", 10);

         for (int i = 0; i < invList.size(); i++) {
            CompoundTag itemTag = invList.getCompound(i);
            byte slot = itemTag.getByte("Slot");
            ItemStack itemStack = ItemStack.parseOptional(this.server.registryAccess(), itemTag);
            JsonObject item = this.serializeItemStack(itemStack);
            item.addProperty("slot", slot);
            if (slot >= 100 && slot <= 103) {
               armor.add(item);
            } else if (slot == -106) {
               offhand.add(item);
            } else {
               mainInventory.add(item);
            }
         }
      }

      inventory.add("main", mainInventory);
      inventory.add("armor", armor);
      inventory.add("offhand", offhand);
      return inventory;
   }

   public JsonObject getPlayerStatus(UUID playerUuid) {
      JsonObject status = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      status.addProperty("online", player != null);
      if (player != null) {
         status.addProperty("username", player.getName().getString());
         status.addProperty("ping", player.connection.latency());
         status.addProperty("health", player.getHealth());
         status.addProperty("maxHealth", player.getMaxHealth());
         status.addProperty("healthPercent", player.getHealth() / player.getMaxHealth() * 100.0F);
         status.addProperty("foodLevel", player.getFoodData().getFoodLevel());
         status.addProperty("saturation", player.getFoodData().getSaturationLevel());
         status.addProperty("armorValue", player.getArmorValue());
         status.addProperty("absorptionAmount", player.getAbsorptionAmount());
         status.addProperty("experienceLevel", player.experienceLevel);
         status.addProperty("experienceProgress", player.experienceProgress);
         status.addProperty("totalExperience", player.totalExperience);
         AfkManager afkManager = AfkManager.getInstance();
         boolean isAfk = afkManager.isAfk(playerUuid);
         status.addProperty("afk", isAfk);
         if (isAfk) {
            long afkDuration = afkManager.getAfkDuration(playerUuid);
            status.addProperty("afkDuration", afkDuration);
            status.addProperty("afkDurationSeconds", afkDuration / 1000L);
            String afkReason = afkManager.getAfkReason(playerUuid);
            if (afkReason != null) {
               status.addProperty("afkReason", afkReason);
            }
         }
      } else {
         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (playerData != null) {
            if (playerData.contains("Health")) {
               float health = playerData.getFloat("Health");
               status.addProperty("health", health);
               status.addProperty("maxHealth", 20.0F);
               status.addProperty("healthPercent", health / 20.0F * 100.0F);
            }

            if (playerData.contains("foodLevel")) {
               status.addProperty("foodLevel", playerData.getInt("foodLevel"));
            }

            if (playerData.contains("foodSaturationLevel")) {
               status.addProperty("saturation", playerData.getFloat("foodSaturationLevel"));
            }

            if (playerData.contains("AbsorptionAmount")) {
               status.addProperty("absorptionAmount", playerData.getFloat("AbsorptionAmount"));
            }

            status.addProperty("armorValue", 0);
            if (playerData.contains("XpLevel")) {
               status.addProperty("experienceLevel", playerData.getInt("XpLevel"));
            }

            if (playerData.contains("XpP")) {
               status.addProperty("experienceProgress", playerData.getFloat("XpP"));
            }

            if (playerData.contains("XpTotal")) {
               status.addProperty("totalExperience", playerData.getInt("XpTotal"));
            }

            status.addProperty("afk", false);
         }
      }

      return status;
   }

   public JsonObject getPlayerHealth(UUID playerUuid) {
      JsonObject health = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         health.addProperty("health", player.getHealth());
         health.addProperty("maxHealth", player.getMaxHealth());
         health.addProperty("healthPercent", player.getHealth() / player.getMaxHealth() * 100.0F);
         health.addProperty("foodLevel", player.getFoodData().getFoodLevel());
         health.addProperty("saturation", player.getFoodData().getSaturationLevel());
         health.addProperty("exhaustion", player.getFoodData().getExhaustionLevel());
         health.addProperty("armor", player.getArmorValue());
         health.addProperty("absorptionAmount", player.getAbsorptionAmount());
         health.addProperty("air", player.getAirSupply());
         health.addProperty("maxAir", player.getMaxAirSupply());
      } else {
         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (playerData != null) {
            if (playerData.contains("Health")) {
               float playerHealth = playerData.getFloat("Health");
               health.addProperty("health", playerHealth);
               health.addProperty("maxHealth", 20.0F);
               health.addProperty("healthPercent", playerHealth / 20.0F * 100.0F);
            }

            if (playerData.contains("foodLevel")) {
               health.addProperty("foodLevel", playerData.getInt("foodLevel"));
            }

            if (playerData.contains("foodSaturationLevel")) {
               health.addProperty("saturation", playerData.getFloat("foodSaturationLevel"));
            }

            if (playerData.contains("foodExhaustionLevel")) {
               health.addProperty("exhaustion", playerData.getFloat("foodExhaustionLevel"));
            }

            if (playerData.contains("Air")) {
               health.addProperty("air", playerData.getShort("Air"));
               health.addProperty("maxAir", 300);
            }

            if (playerData.contains("AbsorptionAmount")) {
               health.addProperty("absorptionAmount", playerData.getFloat("AbsorptionAmount"));
            }

            health.addProperty("armor", 0);
         }
      }

      return health;
   }

   public JsonObject getPlayerXP(UUID playerUuid) {
      JsonObject xp = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         xp.addProperty("level", player.experienceLevel);
         xp.addProperty("progress", player.experienceProgress);
         xp.addProperty("totalXp", player.totalExperience);
         xp.addProperty("xpToNextLevel", player.getXpNeededForNextLevel());
      } else {
         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (playerData != null) {
            if (playerData.contains("XpLevel")) {
               xp.addProperty("level", playerData.getInt("XpLevel"));
            }

            if (playerData.contains("XpP")) {
               xp.addProperty("progress", playerData.getFloat("XpP"));
            }

            if (playerData.contains("XpTotal")) {
               xp.addProperty("totalXp", playerData.getInt("XpTotal"));
            }

            xp.addProperty("xpToNextLevel", 0);
         }
      }

      return xp;
   }

   public JsonObject getPlayerLocation(UUID playerUuid) {
      JsonObject location = new JsonObject();
      ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
      if (player != null) {
         BlockPos pos = player.blockPosition();
         location.addProperty("x", pos.getX());
         location.addProperty("y", pos.getY());
         location.addProperty("z", pos.getZ());
         location.addProperty("exactX", player.getX());
         location.addProperty("exactY", player.getY());
         location.addProperty("exactZ", player.getZ());
         location.addProperty("yaw", player.getYRot());
         location.addProperty("pitch", player.getXRot());
         Level level = player.level();
         location.addProperty("dimension", this.getDimensionName(level));
         location.addProperty("world", level.dimension().location().toString());
         location.addProperty("biome", this.getBiomeName(level, pos));
      } else {
         CompoundTag playerData = this.loadOfflinePlayerData(playerUuid);
         if (playerData != null && playerData.contains("Pos")) {
            ListTag posList = playerData.getList("Pos", 6);
            if (posList.size() >= 3) {
               double x = posList.getDouble(0);
               double y = posList.getDouble(1);
               double z = posList.getDouble(2);
               location.addProperty("x", (int)Math.floor(x));
               location.addProperty("y", (int)Math.floor(y));
               location.addProperty("z", (int)Math.floor(z));
               location.addProperty("exactX", x);
               location.addProperty("exactY", y);
               location.addProperty("exactZ", z);
            }

            if (playerData.contains("Rotation")) {
               ListTag rotList = playerData.getList("Rotation", 5);
               if (rotList.size() >= 2) {
                  location.addProperty("yaw", rotList.getFloat(0));
                  location.addProperty("pitch", rotList.getFloat(1));
               }
            }

            if (playerData.contains("Dimension")) {
               String dimension = playerData.getString("Dimension");
               location.addProperty("dimension", dimension);
               location.addProperty("world", dimension);
            }

            location.addProperty("biome", "Unknown (Offline)");
         }
      }

      return location;
   }

   public JsonObject getPlayerHomes(String username) {
      JsonObject response = new JsonObject();
      JsonArray homesList = new JsonArray();
      ServerPlayer player = this.server.getPlayerList().getPlayerByName(username);
      if (player == null) {
         response.addProperty("error", "Player not found or offline");
         response.add("homes", homesList);
         response.addProperty("count", 0);
         return response;
      } else {
         HomeManager homeManager = HomeManager.getInstance();
         Map<String, TeleportLocation> playerHomes = homeManager.getPlayerHomes(player);

         for (Entry<String, TeleportLocation> entry : playerHomes.entrySet()) {
            JsonObject homeObj = new JsonObject();
            TeleportLocation location = entry.getValue();
            homeObj.addProperty("name", entry.getKey());
            homeObj.addProperty("x", location.getX());
            homeObj.addProperty("y", location.getY());
            homeObj.addProperty("z", location.getZ());
            homeObj.addProperty("yaw", location.getYaw());
            homeObj.addProperty("pitch", location.getPitch());
            homeObj.addProperty("dimension", location.getWorldName());
            homeObj.addProperty("createdBy", location.getCreatedBy());
            homeObj.addProperty("timestamp", location.getTimestamp());
            homesList.add(homeObj);
         }

         response.add("homes", homesList);
         response.addProperty("count", homesList.size());
         response.addProperty("maxHomes", homeManager.getMaxHomesForPlayer(player));
         return response;
      }
   }

   public JsonObject getOnlinePlayers() {
      JsonObject response = new JsonObject();
      JsonArray onlinePlayers = new JsonArray();
      JsonArray offlinePlayers = new JsonArray();
      LOGGER.debug("=== Starting getOnlinePlayers data collection ===");
      List<ServerPlayer> online = this.server.getPlayerList().getPlayers();
      Set<String> onlineUsernames = new HashSet<>();
      online.forEach(player -> {
         JsonObject playerObj = new JsonObject();
         playerObj.addProperty("uuid", player.getUUID().toString());
         playerObj.addProperty("username", player.getName().getString());
         playerObj.addProperty("displayName", player.getDisplayName().getString());
         playerObj.addProperty("ping", player.connection.latency());
         playerObj.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
         playerObj.addProperty("health", player.getHealth());
         playerObj.addProperty("foodLevel", player.getFoodData().getFoodLevel());
         playerObj.addProperty("experienceLevel", player.experienceLevel);
         onlinePlayers.add(playerObj);
         onlineUsernames.add(player.getName().getString());
      });
      LOGGER.debug("Found {} online players", onlinePlayers.size());

      try {
         ServerLevel overworld = this.server.overworld();
         Path playerDataDir = overworld.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
         LOGGER.debug("Looking for offline players in: {}", playerDataDir);
         if (Files.exists(playerDataDir)) {
            GameProfileCache cache = this.server.getProfileCache();
            LOGGER.debug("Player data directory exists, cache available: {}", cache != null);
            int maxOffline = 50;

            try (Stream<Path> stream = Files.list(playerDataDir)) {
               stream.filter(path -> path.toString().endsWith(".dat")).limit((long)maxOffline).forEach(path -> {
                  try {
                     String fileName = path.getFileName().toString();
                     String uuidStr = fileName.replace(".dat", "");
                     UUID uuid = UUID.fromString(uuidStr);
                     if (this.server.getPlayerList().getPlayer(uuid) == null) {
                        String username = null;
                        if (cache != null) {
                           Optional<GameProfile> profileOpt = cache.get(uuid);
                           if (profileOpt.isPresent() && profileOpt.get().getName() != null) {
                              username = profileOpt.get().getName();
                           }
                        }

                        if (username == null) {
                           try {
                              CompoundTag playerData = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                              boolean hasBukkitData = playerData != null && playerData.contains("bukkit");
                              if (hasBukkitData) {
                                 CompoundTag bukkitData = playerData.getCompound("bukkit");
                                 if (bukkitData.contains("lastKnownName")) {
                                    username = bukkitData.getString("lastKnownName");
                                 }
                              }

                              boolean hasLastKnownName = username == null && playerData != null && playerData.contains("lastKnownName");
                              if (hasLastKnownName) {
                                 username = playerData.getString("lastKnownName");
                              }
                           } catch (Exception var13) {
                              LOGGER.debug("Could not load username from NBT for {}: {}", uuid, var13.getMessage());
                           }
                        }

                        if (username != null && !onlineUsernames.contains(username)) {
                           JsonObject playerObj = new JsonObject();
                           playerObj.addProperty("uuid", uuid.toString());
                           playerObj.addProperty("username", username);

                           try {
                              long lastModified = Files.getLastModifiedTime(path).toMillis();
                              playerObj.addProperty("lastSeen", this.formatLastSeenTimestamp(lastModified));
                           } catch (Exception var12) {
                              playerObj.addProperty("lastSeen", "Unknown");
                           }

                           offlinePlayers.add(playerObj);
                        }
                     }
                  } catch (Exception var14) {
                     LOGGER.debug("Skipping invalid player data file: {}", path.getFileName());
                  }
               });
            } catch (IOException var15) {
               LOGGER.warn("Error reading player data directory: {}", var15.getMessage());
            }

            LOGGER.debug("Found {} offline players", offlinePlayers.size());
         } else {
            LOGGER.warn("Player data directory does not exist: {}", playerDataDir);
         }
      } catch (Exception var16) {
         LOGGER.warn("Could not load offline players from playerdata: {}", var16.getMessage());
      }

      response.add("players", onlinePlayers);
      response.add("offlinePlayers", offlinePlayers);
      response.addProperty("count", onlinePlayers.size());
      response.addProperty("offlineCount", offlinePlayers.size());
      response.addProperty("max", this.server.getMaxPlayers());
      LOGGER.debug("=== Completed getOnlinePlayers: {} online, {} offline ===", onlinePlayers.size(), offlinePlayers.size());
      return response;
   }

   private String formatLastSeenTimestamp(long timestamp) {
      long now = System.currentTimeMillis();
      long diff = now - timestamp;
      long seconds = diff / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      long days = hours / 24L;
      if (days > 0L) {
         return days + " day" + (days > 1L ? "s" : "") + " ago";
      } else if (hours > 0L) {
         return hours + " hour" + (hours > 1L ? "s" : "") + " ago";
      } else {
         return minutes > 0L ? minutes + " minute" + (minutes > 1L ? "s" : "") + " ago" : "Just now";
      }
   }

   private JsonObject serializeItemStack(ItemStack itemStack) {
      JsonObject item = new JsonObject();
      if (!itemStack.isEmpty()) {
         ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
         String registryName = itemId.toString();
         item.addProperty("id", registryName);
         item.addProperty("count", itemStack.getCount());
         item.addProperty("displayName", itemStack.getDisplayName().getString());
         item.addProperty("namespace", itemId.getNamespace());
         item.addProperty("path", itemId.getPath());
         String itemType = this.determineItemType(itemStack);
         item.addProperty("type", itemType);
         boolean isModded = !itemId.getNamespace().equals("minecraft");
         item.addProperty("modded", isModded);
         if (isModded) {
            String modName = this.getModName(itemId.getNamespace());
            if (modName != null) {
               item.addProperty("modName", modName);
            }
         }

         boolean hasEnchantments = itemStack.isEnchanted();
         item.addProperty("enchanted", hasEnchantments);
         Component hoverName = itemStack.getHoverName();
         Component displayName = itemStack.getDisplayName();
         if (!hoverName.equals(displayName)) {
            item.addProperty("customName", hoverName.getString());
         }
      }

      return item;
   }

   private String determineItemType(ItemStack itemStack) {
      Item item = itemStack.getItem();
      if (item instanceof SwordItem) {
         return "sword";
      } else if (item instanceof PickaxeItem) {
         return "pickaxe";
      } else if (item instanceof AxeItem) {
         return "axe";
      } else if (item instanceof ShovelItem) {
         return "shovel";
      } else if (item instanceof HoeItem) {
         return "hoe";
      } else if (item instanceof ArmorItem armorItem) {
         return "armor_" + armorItem.getType().getName();
      } else if (item instanceof BowItem) {
         return "bow";
      } else if (item instanceof CrossbowItem) {
         return "crossbow";
      } else if (item instanceof TridentItem) {
         return "trident";
      } else if (item instanceof ShieldItem) {
         return "shield";
      } else if (item instanceof BlockItem) {
         return "block";
      } else if (item.components().has(DataComponents.FOOD)) {
         return "food";
      } else if (item instanceof PotionItem) {
         return "potion";
      } else {
         return item instanceof EnchantedBookItem ? "enchanted_book" : "item";
      }
   }

   private String getModName(String namespace) {
      try {
         Optional<? extends ModContainer> modContainerOpt = ModList.get().getModContainerById(namespace);
         if (modContainerOpt.isPresent()) {
            return modContainerOpt.get().getModInfo().getDisplayName();
         }
      } catch (Exception var3) {
         LOGGER.debug("Could not get mod name for namespace: {}", namespace);
      }

      return null;
   }

   private String getDimensionName(Level level) {
      String dimensionKey = level.dimension().location().toString();

      return switch (dimensionKey) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:the_end" -> "End";
         default -> dimensionKey;
      };
   }

   private String getBiomeName(Level level, BlockPos pos) {
      try {
         ResourceLocation biomeKey = level.registryAccess().registryOrThrow(Registries.BIOME).getKey((Biome)level.getBiome(pos).value());
         return biomeKey != null ? biomeKey.toString() : "Unknown";
      } catch (Exception var4) {
         return "Unknown";
      }
   }
}
