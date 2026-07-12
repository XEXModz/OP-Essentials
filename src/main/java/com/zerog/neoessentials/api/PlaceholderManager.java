package com.zerog.neoessentials.api;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceholderManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlaceholderManager.class);
   private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");
   private static volatile PlaceholderManager instance;
   private final Map<String, PlaceholderProvider> placeholders = new ConcurrentHashMap<>();
   private final Map<String, PlaceholderExpansion> expansions = new ConcurrentHashMap<>();

   private PlaceholderManager() {
      LOGGER.info("PlaceholderAPI Manager initialized");
   }

   public static PlaceholderManager getInstance() {
      if (instance == null) {
         synchronized (PlaceholderManager.class) {
            if (instance == null) {
               instance = new PlaceholderManager();
            }
         }
      }

      return instance;
   }

   public boolean registerPlaceholder(String identifier, PlaceholderProvider provider) {
      if (identifier == null || identifier.trim().isEmpty()) {
         LOGGER.warn("Attempted to register placeholder with null or empty identifier");
         return false;
      } else if (provider == null) {
         LOGGER.warn("Attempted to register placeholder '{}' with null provider", identifier);
         return false;
      } else {
         String normalizedIdentifier = identifier.toLowerCase().trim();
         if (this.placeholders.containsKey(normalizedIdentifier)) {
            LOGGER.warn("Placeholder '{}' is already registered", normalizedIdentifier);
            return false;
         } else {
            this.placeholders.put(normalizedIdentifier, provider);
            LOGGER.debug("Registered placeholder: {}", normalizedIdentifier);
            return true;
         }
      }
   }

   public boolean unregisterPlaceholder(String identifier) {
      if (identifier != null && !identifier.trim().isEmpty()) {
         String normalizedIdentifier = identifier.toLowerCase().trim();
         boolean removed = this.placeholders.remove(normalizedIdentifier) != null;
         if (removed) {
            LOGGER.debug("Unregistered placeholder: {}", normalizedIdentifier);
         }

         return removed;
      } else {
         return false;
      }
   }

   public String setPlaceholders(@Nullable ServerPlayer player, String text) {
      if (text != null && !text.isEmpty()) {
         Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
         StringBuilder result = new StringBuilder();

         while (matcher.find()) {
            String fullPlaceholder = matcher.group(0);
            String placeholderContent = matcher.group(1);
            String params = null;
            int colonIndex = placeholderContent.indexOf(58);
            String identifier;
            if (colonIndex != -1) {
               identifier = placeholderContent.substring(0, colonIndex);
               params = placeholderContent.substring(colonIndex + 1);
            } else {
               identifier = placeholderContent;
            }

            String value = this.getPlaceholderValue(player, identifier, params);
            if (value == null && player != null) {
               value = this.resolveExternalPlaceholder(player, identifier);
            }

            if (value != null) {
               value = Matcher.quoteReplacement(value);
               matcher.appendReplacement(result, value);
            } else {
               matcher.appendReplacement(result, Matcher.quoteReplacement(fullPlaceholder));
            }
         }

         matcher.appendTail(result);
         return result.toString();
      } else {
         return text;
      }
   }

   private String resolveExternalPlaceholder(ServerPlayer player, String identifier) {
      String luckPermsValue = this.resolveLuckPermsPlaceholder(player, identifier);
      return luckPermsValue != null ? luckPermsValue : this.resolveFTBRanksPlaceholder(player, identifier);
   }

   private String resolveLuckPermsPlaceholder(ServerPlayer player, String identifier) {
      if (!identifier.startsWith("luckperms_")) {
         return null;
      } else {
         try {
            String permMeta = identifier.substring("luckperms_".length());

            return switch (permMeta) {
               case "prefix" -> this.getLuckPermsPrefix(player);
               case "suffix" -> this.getLuckPermsSuffix(player);
               case "group", "primary_group" -> this.getLuckPermsPrimaryGroup(player);
               case "displayname" -> this.getLuckPermsDisplayName(player);
               default -> null;
            };
         } catch (Exception var6) {
            LOGGER.debug("Failed to resolve LuckPerms placeholder '{}': {}", identifier, var6.getMessage());
            return null;
         }
      }
   }

   private String getLuckPermsPrefix(ServerPlayer player) {
      try {
         return PermissionAPI.getPrefix(player.getUUID());
      } catch (Exception var3) {
         LOGGER.debug("Error getting LuckPerms prefix: {}", var3.getMessage());
         return "";
      }
   }

   private String getLuckPermsSuffix(ServerPlayer player) {
      try {
         return PermissionAPI.getSuffix(player.getUUID());
      } catch (Exception var3) {
         LOGGER.debug("Error getting LuckPerms suffix: {}", var3.getMessage());
         return "";
      }
   }

   private String getLuckPermsPrimaryGroup(ServerPlayer player) {
      try {
         PermissionManager permManager = PermissionAPI.getManager();
         if (permManager != null) {
            PermissionUser user = permManager.getUser(player.getUUID());
            if (user != null && user.getGroup() != null) {
               return user.getGroup();
            }
         }
      } catch (Exception var4) {
         LOGGER.debug("Error getting LuckPerms group: {}", var4.getMessage());
      }

      return "";
   }

   private String getLuckPermsDisplayName(ServerPlayer player) {
      String prefix = this.getLuckPermsPrefix(player);
      String suffix = this.getLuckPermsSuffix(player);
      String name = player.getName().getString();
      return prefix + name + suffix;
   }

   private String resolveFTBRanksPlaceholder(ServerPlayer player, String identifier) {
      if (!identifier.startsWith("ftbranks_")) {
         return null;
      } else {
         String ftbMeta = identifier.substring("ftbranks_".length());

         return switch (ftbMeta) {
            case "prefix" -> this.getLuckPermsPrefix(player);
            case "suffix" -> this.getLuckPermsSuffix(player);
            case "rank", "group" -> this.getLuckPermsPrimaryGroup(player);
            default -> null;
         };
      }
   }

   @Nullable
   public String getPlaceholderValue(@Nullable ServerPlayer player, String identifier, @Nullable String params) {
      if (identifier != null && !identifier.trim().isEmpty()) {
         String normalizedIdentifier = identifier.toLowerCase().trim();
         PlaceholderProvider provider = this.placeholders.get(normalizedIdentifier);
         if (provider != null) {
            try {
               return provider.onRequest(player, params);
            } catch (Exception var11) {
               LOGGER.error("Error resolving placeholder '{}': {}", new Object[]{normalizedIdentifier, var11.getMessage(), var11});
               return null;
            }
         } else {
            int colonIndex = normalizedIdentifier.indexOf(95);
            if (colonIndex != -1) {
               String expansionId = normalizedIdentifier.substring(0, colonIndex);
               String placeholderName = normalizedIdentifier.substring(colonIndex + 1);
               PlaceholderExpansion expansion = this.expansions.get(expansionId);
               if (expansion != null) {
                  try {
                     return expansion.onPlaceholderRequest(player, placeholderName, params);
                  } catch (Exception var12) {
                     LOGGER.error(
                        "Error resolving expansion placeholder '{}' from '{}': {}", new Object[]{placeholderName, expansionId, var12.getMessage(), var12}
                     );
                     return null;
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public boolean isPlaceholderRegistered(String identifier) {
      if (identifier != null && !identifier.trim().isEmpty()) {
         String normalizedIdentifier = identifier.toLowerCase().trim();
         if (this.placeholders.containsKey(normalizedIdentifier)) {
            return true;
         } else {
            int underscoreIndex = normalizedIdentifier.indexOf(95);
            if (underscoreIndex != -1) {
               String expansionId = normalizedIdentifier.substring(0, underscoreIndex);
               String placeholderName = normalizedIdentifier.substring(underscoreIndex + 1);
               PlaceholderExpansion expansion = this.expansions.get(expansionId);
               if (expansion != null) {
                  return expansion.getPlaceholders().contains(placeholderName);
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public Set<String> getRegisteredPlaceholders() {
      Set<String> result = new HashSet<>(this.placeholders.keySet());

      for (Entry<String, PlaceholderExpansion> entry : this.expansions.entrySet()) {
         String expansionId = entry.getKey();
         PlaceholderExpansion expansion = entry.getValue();

         for (String placeholder : expansion.getPlaceholders()) {
            result.add(expansionId + "_" + placeholder);
         }
      }

      return Collections.unmodifiableSet(result);
   }

   public boolean registerExpansion(PlaceholderExpansion expansion) {
      if (expansion == null) {
         LOGGER.warn("Attempted to register null expansion");
         return false;
      } else {
         String identifier = expansion.getIdentifier();
         if (identifier != null && !identifier.trim().isEmpty()) {
            String normalizedIdentifier = identifier.toLowerCase().trim();
            if (this.expansions.containsKey(normalizedIdentifier)) {
               LOGGER.warn("Expansion '{}' is already registered", normalizedIdentifier);
               return false;
            } else {
               this.expansions.put(normalizedIdentifier, expansion);
               LOGGER.info("Registered placeholder expansion: {} v{} by {}", new Object[]{normalizedIdentifier, expansion.getVersion(), expansion.getAuthor()});
               return true;
            }
         } else {
            LOGGER.warn("Attempted to register expansion with null or empty identifier");
            return false;
         }
      }
   }

   public boolean unregisterExpansion(PlaceholderExpansion expansion) {
      if (expansion == null) {
         return false;
      } else {
         String identifier = expansion.getIdentifier();
         if (identifier != null && !identifier.trim().isEmpty()) {
            String normalizedIdentifier = identifier.toLowerCase().trim();
            boolean removed = this.expansions.remove(normalizedIdentifier) != null;
            if (removed) {
               LOGGER.info("Unregistered placeholder expansion: {}", normalizedIdentifier);
            }

            return removed;
         } else {
            return false;
         }
      }
   }

   public Map<String, Object> getStatistics() {
      Map<String, Object> stats = new HashMap<>();
      stats.put("total_placeholders", this.placeholders.size());
      stats.put("total_expansions", this.expansions.size());
      stats.put("registered_placeholders", this.getRegisteredPlaceholders().size());
      return Collections.unmodifiableMap(stats);
   }

   public void clear() {
      this.placeholders.clear();
      this.expansions.clear();
      LOGGER.info("Cleared all placeholders and expansions");
   }
}
