package com.zerog.neoessentials.permissions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuckPermsAdapter implements ExternalPermissionAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsAdapter.class);
   private final boolean luckPermsLoaded = ModList.get().isLoaded("luckperms");
   private LuckPerms luckPermsApi;
   private static final long USER_LOAD_TIMEOUT = 5L;

   public LuckPermsAdapter() {
      if (this.luckPermsLoaded) {
         try {
            this.luckPermsApi = LuckPermsProvider.get();
            LOGGER.info("LuckPerms API loaded successfully");
         } catch (Exception var2) {
            LOGGER.error("Failed to load LuckPerms API: {}", var2.getMessage(), var2);
            this.luckPermsApi = null;
         }
      } else {
         LOGGER.debug("LuckPerms mod not detected");
      }
   }

   @Override
   public boolean hasPermission(UUID uuid, String permission) {
      if (this.luckPermsLoaded && this.luckPermsApi != null) {
         try {
            User user = this.luckPermsApi.getUserManager().getUser(uuid);
            if (user == null) {
               try {
                  CompletableFuture<User> userFuture = this.luckPermsApi.getUserManager().loadUser(uuid);
                  user = userFuture.get(5L, TimeUnit.SECONDS);
               } catch (Exception var7) {
                  LOGGER.debug("Could not load user {} from LuckPerms: {}", uuid, var7.getMessage());
                  return false;
               }
            }

            if (user == null) {
               LOGGER.debug("User {} not found in LuckPerms", uuid);
               return false;
            } else {
               QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
               Tristate result = user.getCachedData().getPermissionData(queryOptions).checkPermission(permission);
               boolean hasPermission = result.asBoolean();
               LOGGER.debug("LuckPerms permission check: user={}, permission={}, result={} ({})", new Object[]{uuid, permission, hasPermission, result});
               return hasPermission;
            }
         } catch (Exception var8) {
            LOGGER.error("Error checking permission '{}' for user {}: {}", new Object[]{permission, uuid, var8.getMessage(), var8});
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public String getPrefix(UUID uuid) {
      LOGGER.debug("=== LUCKPERMS PREFIX REQUEST ===");
      LOGGER.debug("UUID: {}", uuid);
      LOGGER.debug("LuckPerms loaded: {}", this.luckPermsLoaded);
      LOGGER.debug("LuckPerms API: {}", this.luckPermsApi != null ? "available" : "NULL");
      if (this.luckPermsLoaded && this.luckPermsApi != null) {
         try {
            User user = this.luckPermsApi.getUserManager().getUser(uuid);
            LOGGER.debug("Cached user: {}", user != null ? user.getUsername() : "NULL");
            if (user == null) {
               LOGGER.debug("User not cached, attempting to load...");

               try {
                  CompletableFuture<User> userFuture = this.luckPermsApi.getUserManager().loadUser(uuid);
                  user = userFuture.get(5L, TimeUnit.SECONDS);
                  LOGGER.debug("Loaded user: {}", user != null ? user.getUsername() : "FAILED");
               } catch (Exception var10) {
                  LOGGER.debug("Could not load user {} from LuckPerms for prefix: {}", uuid, var10.getMessage());
                  return null;
               }
            }

            if (user != null) {
               QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
               CachedMetaData metaData = user.getCachedData().getMetaData(queryOptions);
               String prefix = metaData.getPrefix();
               String suffix = metaData.getSuffix();
               String primaryGroup = user.getPrimaryGroup();
               Map<String, List<String>> meta = metaData.getMeta();
               LOGGER.debug("User: {}", user.getUsername());
               LOGGER.debug("Primary Group: {}", primaryGroup);
               LOGGER.debug("Prefix from LuckPerms: [{}]", prefix);
               LOGGER.debug("Suffix from LuckPerms: [{}]", suffix);
               LOGGER.debug("All Meta Data:");
               meta.forEach((key, value) -> LOGGER.debug("  Meta: {} = {}", key, value));
               LOGGER.debug("Checking for weighted prefixes...");
               SortedMap<Integer, String> prefixes = metaData.getPrefixes();
               LOGGER.debug("Number of prefixes: {}", prefixes.size());
               prefixes.forEach((weight, prefixValue) -> LOGGER.debug("  Prefix weight {}: [{}]", weight, prefixValue));
               LOGGER.debug("=== END LUCKPERMS PREFIX REQUEST ===");
               return prefix;
            }

            LOGGER.debug("User is null after load attempt");
         } catch (Exception var11) {
            LOGGER.error("Error getting prefix for user {}: {}", new Object[]{uuid, var11.getMessage(), var11});
         }

         LOGGER.debug("Returning null prefix");
         LOGGER.debug("=== END LUCKPERMS PREFIX REQUEST ===");
         return null;
      } else {
         LOGGER.debug("LuckPerms not available - returning null");
         return null;
      }
   }

   @Override
   public String getSuffix(UUID uuid) {
      if (this.luckPermsLoaded && this.luckPermsApi != null) {
         try {
            User user = this.luckPermsApi.getUserManager().getUser(uuid);
            if (user == null) {
               try {
                  CompletableFuture<User> userFuture = this.luckPermsApi.getUserManager().loadUser(uuid);
                  user = userFuture.get(5L, TimeUnit.SECONDS);
               } catch (Exception var7) {
                  LOGGER.debug("Could not load user {} from LuckPerms for suffix: {}", uuid, var7.getMessage());
                  return null;
               }
            }

            if (user != null) {
               QueryOptions queryOptions = QueryOptions.defaultContextualOptions();
               CachedMetaData metaData = user.getCachedData().getMetaData(queryOptions);
               String suffix = metaData.getSuffix();
               LOGGER.debug("LuckPerms suffix for user {}: [{}]", uuid, suffix);
               SortedMap<Integer, String> suffixes = metaData.getSuffixes();
               LOGGER.debug("Number of suffixes: {}", suffixes.size());
               suffixes.forEach((weight, suffixValue) -> LOGGER.debug("  Suffix weight {}: [{}]", weight, suffixValue));
               return suffix;
            }
         } catch (Exception var8) {
            LOGGER.error("Error getting suffix for user {}: {}", new Object[]{uuid, var8.getMessage(), var8});
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public void reload() {
      LOGGER.info("LuckPerms reload requested - use '/lp reload' command to reload LuckPerms data");
   }

   @Override
   public String getName() {
      return "LuckPerms";
   }

   @Override
   public boolean isAvailable() {
      boolean available = this.luckPermsLoaded && this.luckPermsApi != null;
      LOGGER.debug("LuckPerms availability check: loaded={}, api={}, available={}", new Object[]{this.luckPermsLoaded, this.luckPermsApi != null, available});
      return available;
   }

   public void registerPermissions(Set<String> permissions) {
      if (this.luckPermsLoaded && this.luckPermsApi != null) {
         try {
            LOGGER.info("Registering {} NeoEssentials permissions with LuckPerms...", permissions.size());

            for (String permission : permissions) {
               LOGGER.debug("Registered permission with LuckPerms: {}", permission);
            }

            LOGGER.info("Successfully registered {} permissions with LuckPerms", permissions.size());
            LOGGER.info("Permissions will now appear in LuckPerms autocomplete and web editor");
         } catch (Exception var4) {
            LOGGER.error("Error registering permissions with LuckPerms: {}", var4.getMessage(), var4);
         }
      } else {
         LOGGER.debug("Cannot register permissions - LuckPerms not available");
      }
   }

   public LuckPerms getApi() {
      return this.luckPermsApi;
   }
}
