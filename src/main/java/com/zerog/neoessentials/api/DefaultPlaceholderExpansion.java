package com.zerog.neoessentials.api;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPlaceholderExpansion extends PlaceholderExpansion {
   private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlaceholderExpansion.class);
   private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
   private final Set<String> placeholders = new HashSet<>();

   public DefaultPlaceholderExpansion() {
      this.initializePlaceholders();
   }

   private void initializePlaceholders() {
      this.placeholders.add("displayname");
      this.placeholders.add("username");
      this.placeholders.add("name");
      this.placeholders.add("prefix");
      this.placeholders.add("suffix");
      this.placeholders.add("group");
      this.placeholders.add("world");
      this.placeholders.add("x");
      this.placeholders.add("y");
      this.placeholders.add("z");
      this.placeholders.add("biome");
      this.placeholders.add("health");
      this.placeholders.add("max_health");
      this.placeholders.add("food");
      this.placeholders.add("level");
      this.placeholders.add("exp");
      this.placeholders.add("gamemode");
      this.placeholders.add("ping");
      this.placeholders.add("balance");
      this.placeholders.add("balance_formatted");
      this.placeholders.add("server_name");
      this.placeholders.add("online_players");
      this.placeholders.add("max_players");
      this.placeholders.add("time");
      this.placeholders.add("time_24");
      this.placeholders.add("date");
      this.placeholders.add("afk");
      this.placeholders.add("afk_time");
      this.placeholders.add("afk_reason");
      LOGGER.debug("Initialized {} default placeholders", this.placeholders.size());
   }

   @Override
   public String getIdentifier() {
      return "neoessentials";
   }

   @Override
   public String getVersion() {
      return "1.0.0";
   }

   @Override
   public String getAuthor() {
      return "ZeroG Network";
   }

   @Override
   public Set<String> getPlaceholders() {
      return new HashSet<>(this.placeholders);
   }

   @Nullable
   @Override
   public String onPlaceholderRequest(@Nullable ServerPlayer player, String identifier, @Nullable String params) {
      if (player == null && this.requiresPlayer(identifier)) {
         return null;
      } else {
         try {
            String e = identifier.toLowerCase();

            return switch (e) {
               case "displayname" -> player != null ? player.getDisplayName().getString() : null;
               case "username", "name" -> player != null ? player.getName().getString() : null;
               case "prefix" -> this.getPlayerPrefix(player);
               case "suffix" -> this.getPlayerSuffix(player);
               case "group" -> this.getPlayerGroup(player);
               case "world" -> player != null ? this.getWorldName(player) : null;
               case "x" -> player != null ? String.valueOf((int)player.getX()) : null;
               case "y" -> player != null ? String.valueOf((int)player.getY()) : null;
               case "z" -> player != null ? String.valueOf((int)player.getZ()) : null;
               case "biome" -> player != null ? this.getBiome(player) : null;
               case "health" -> player != null ? DECIMAL_FORMAT.format((double)player.getHealth()) : null;
               case "max_health" -> player != null ? DECIMAL_FORMAT.format((double)player.getMaxHealth()) : null;
               case "food" -> player != null ? String.valueOf(player.getFoodData().getFoodLevel()) : null;
               case "level" -> player != null ? String.valueOf(player.experienceLevel) : null;
               case "exp" -> player != null ? (int)(player.experienceProgress * 100.0F) + "%" : null;
               case "gamemode" -> player != null ? player.gameMode.getGameModeForPlayer().getName() : null;
               case "ping" -> player != null ? String.valueOf(player.connection.latency()) : null;
               case "balance" -> this.getBalance(player);
               case "balance_formatted" -> this.getFormattedBalance(player);
               case "server_name" -> this.getServerName(player);
               case "online_players" -> this.getOnlinePlayerCount(player);
               case "max_players" -> this.getMaxPlayerCount(player);
               case "time" -> this.getCurrentTime();
               case "time_24" -> this.getCurrentTime24();
               case "date" -> this.getCurrentDate();
               case "afk" -> this.getAfkStatus(player);
               case "afk_time" -> this.getAfkTime(player);
               case "afk_reason" -> this.getAfkReason(player);
               default -> null;
            };
         } catch (Exception var6) {
            LOGGER.error("Error resolving placeholder '{}': {}", new Object[]{identifier, var6.getMessage(), var6});
            return null;
         }
      }
   }

   private boolean requiresPlayer(String identifier) {
      String var2 = identifier.toLowerCase();

      return switch (var2) {
         case "server_name", "online_players", "max_players", "time", "time_24", "date" -> false;
         default -> true;
      };
   }

   @Nullable
   private String getPlayerPrefix(@Nullable ServerPlayer player) {
      if (player == null) {
         LOGGER.warn("getPlayerPrefix called with null player");
         return null;
      } else {
         boolean debugEnabled = ConfigManager.getInstance().isDebugLoggingEnabled();
         if (debugEnabled) {
            LOGGER.info(">>> DefaultPlaceholderExpansion.getPlayerPrefix() for: {}", player.getName().getString());
            LOGGER.info(">>> Player UUID: {}", player.getUUID());
         }

         try {
            String prefix = PermissionAPI.getPrefix(player.getUUID());
            if (debugEnabled) {
               LOGGER.info(">>> PermissionAPI returned prefix: [{}]", prefix);
               LOGGER.info(">>> Returning prefix: [{}]", prefix);
            }

            return prefix;
         } catch (Exception var4) {
            LOGGER.error("Error getting prefix for player {}: {}", new Object[]{player.getName().getString(), var4.getMessage(), var4});
            return "";
         }
      }
   }

   @Nullable
   private String getPlayerSuffix(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            return PermissionAPI.getSuffix(player.getUUID());
         } catch (Exception var3) {
            LOGGER.debug("Error getting suffix for player {}: {}", player.getName().getString(), var3.getMessage());
            return "";
         }
      }
   }

   @Nullable
   private String getPlayerGroup(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            PermissionManager manager = PermissionAPI.getManager();
            if (manager != null) {
               PermissionUser user = manager.getUser(player.getUUID());
               return user != null && user.getGroup() != null ? user.getGroup() : manager.getDefaultGroup();
            } else {
               return "default";
            }
         } catch (Exception var4) {
            LOGGER.debug("Error getting group for player {}: {}", player.getName().getString(), var4.getMessage());
            return "default";
         }
      }
   }

   @Nullable
   private String getWorldName(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            Level level = player.level();
            return level.dimension().location().getPath();
         } catch (Exception var3) {
            LOGGER.debug("Error getting world name for player {}: {}", player.getName().getString(), var3.getMessage());
            return "unknown";
         }
      }
   }

   @Nullable
   private String getBiome(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            Holder<Biome> biome = player.level().getBiome(player.blockPosition());
            return biome.unwrapKey().map(key -> key.location().getPath()).orElse("unknown");
         } catch (Exception var3) {
            LOGGER.debug("Error getting biome for player {}: {}", player.getName().getString(), var3.getMessage());
            return "unknown";
         }
      }
   }

   @Nullable
   private String getBalance(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            EconomyManager economyManager = EconomyManager.getInstance();
            if (economyManager != null) {
               BigDecimal balance = economyManager.getBalance(player.getUUID());
               return balance.toString();
            }
         } catch (Exception var4) {
            LOGGER.debug("Error getting balance for player {}: {}", player.getName().getString(), var4.getMessage());
         }

         return "0.0";
      }
   }

   @Nullable
   private String getFormattedBalance(@Nullable ServerPlayer player) {
      if (player == null) {
         return null;
      } else {
         try {
            EconomyManager economyManager = EconomyManager.getInstance();
            if (economyManager != null) {
               BigDecimal balance = economyManager.getBalance(player.getUUID());
               return DECIMAL_FORMAT.format(balance.doubleValue());
            }
         } catch (Exception var4) {
            LOGGER.debug("Error getting formatted balance for player {}: {}", player.getName().getString(), var4.getMessage());
         }

         return "0.00";
      }
   }

   private String getServerName(@Nullable ServerPlayer player) {
      try {
         if (player != null && player.getServer() != null) {
            return player.getServer().getMotd();
         }
      } catch (Exception var3) {
         LOGGER.debug("Error getting server name: {}", var3.getMessage());
      }

      return "Minecraft Server";
   }

   private String getOnlinePlayerCount(@Nullable ServerPlayer player) {
      try {
         if (player != null && player.getServer() != null) {
            return String.valueOf(player.getServer().getPlayerCount());
         }
      } catch (Exception var3) {
         LOGGER.debug("Error getting online player count: {}", var3.getMessage());
      }

      return "0";
   }

   private String getMaxPlayerCount(@Nullable ServerPlayer player) {
      try {
         if (player != null && player.getServer() != null) {
            return String.valueOf(player.getServer().getMaxPlayers());
         }
      } catch (Exception var3) {
         LOGGER.debug("Error getting max player count: {}", var3.getMessage());
      }

      return "20";
   }

   private String getCurrentTime() {
      try {
         return LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
      } catch (Exception var2) {
         LOGGER.debug("Error getting current time: {}", var2.getMessage());
         return "00:00 AM";
      }
   }

   private String getCurrentTime24() {
      try {
         return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      } catch (Exception var2) {
         LOGGER.debug("Error getting current time (24h): {}", var2.getMessage());
         return "00:00";
      }
   }

   private String getCurrentDate() {
      try {
         return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      } catch (Exception var2) {
         LOGGER.debug("Error getting current date: {}", var2.getMessage());
         return "1970-01-01";
      }
   }

   private String getAfkStatus(@Nullable ServerPlayer player) {
      if (player == null) {
         return "";
      } else {
         try {
            AfkManager afkManager = AfkManager.getInstance();
            boolean isAfk = afkManager.isAfk(player.getUUID());
            return isAfk ? "AFK" : "";
         } catch (Exception var4) {
            LOGGER.debug("Error getting AFK status for player {}: {}", player.getName().getString(), var4.getMessage());
            return "";
         }
      }
   }

   private String getAfkTime(@Nullable ServerPlayer player) {
      if (player == null) {
         return "";
      } else {
         try {
            AfkManager afkManager = AfkManager.getInstance();
            if (!afkManager.isAfk(player.getUUID())) {
               return "";
            } else {
               long afkMs = afkManager.getAfkDuration(player.getUUID());
               if (afkMs <= 0L) {
                  return "";
               } else {
                  long seconds = afkMs / 1000L;
                  long minutes = seconds / 60L;
                  long hours = minutes / 60L;
                  if (hours > 0L) {
                     return String.format("%dh %dm", hours, minutes % 60L);
                  } else {
                     return minutes > 0L ? String.format("%dm %ds", minutes, seconds % 60L) : String.format("%ds", seconds);
                  }
               }
            }
         } catch (Exception var11) {
            LOGGER.debug("Error getting AFK time for player {}: {}", player.getName().getString(), var11.getMessage());
            return "";
         }
      }
   }

   private String getAfkReason(@Nullable ServerPlayer player) {
      if (player == null) {
         return "";
      } else {
         try {
            AfkManager afkManager = AfkManager.getInstance();
            if (!afkManager.isAfk(player.getUUID())) {
               return "";
            } else {
               String reason = afkManager.getAfkReason(player.getUUID());
               return reason != null ? reason : "";
            }
         } catch (Exception var4) {
            LOGGER.debug("Error getting AFK reason for player {}: {}", player.getName().getString(), var4.getMessage());
            return "";
         }
      }
   }
}
