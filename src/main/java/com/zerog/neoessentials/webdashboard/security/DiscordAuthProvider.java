package com.zerog.neoessentials.webdashboard.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordAuthProvider {
   private static final Logger LOGGER = LoggerFactory.getLogger(DiscordAuthProvider.class);
   private static DiscordAuthProvider INSTANCE;
   private boolean sdLinkAvailable;
   private Class<?> minecraftAccountClass;
   private Class<?> cacheManagerClass;
   private Method fromDiscordIdMethod;
   private Method getDiscordMembersMethod;

   private DiscordAuthProvider() {
      this.initialize();
   }

   public static DiscordAuthProvider getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DiscordAuthProvider();
      }

      return INSTANCE;
   }

   private void initialize() {
      this.sdLinkAvailable = ModList.get().isLoaded("sdlink");
      if (this.sdLinkAvailable) {
         try {
            LOGGER.info("Simple Discord Link mod detected, initializing integration...");
            this.minecraftAccountClass = Class.forName("com.hypherionmc.sdlink.api.accounts.MinecraftAccount");
            this.cacheManagerClass = Class.forName("com.hypherionmc.sdlink.core.managers.CacheManager");
            this.fromDiscordIdMethod = this.minecraftAccountClass.getMethod("fromDiscordId", String.class);
            this.getDiscordMembersMethod = this.cacheManagerClass.getMethod("getDiscordMembers");
            LOGGER.info("Discord authentication provider initialized successfully");
            LOGGER.info("SDLink API available: MinecraftAccount, CacheManager");
         } catch (ClassNotFoundException var2) {
            LOGGER.error("SDLink API classes not found. This version of SDLink may not have the required API: {}", var2.getMessage());
            LOGGER.error("Please ensure you're using Simple Discord Link v3.2.1 or newer (with developer API support)");
            this.sdLinkAvailable = false;
         } catch (NoSuchMethodException var3) {
            LOGGER.error("SDLink API methods not found: {}", var3.getMessage());
            this.sdLinkAvailable = false;
         } catch (Exception var4) {
            LOGGER.error("Failed to initialize Discord authentication provider: {}", var4.getMessage(), var4);
            this.sdLinkAvailable = false;
         }
      } else {
         LOGGER.warn("Simple Discord Link mod not found. Discord authentication will not be available.");
         LOGGER.warn("Install Simple Discord Link (sdlink) mod to enable Discord authentication.");
      }
   }

   public boolean isAvailable() {
      return this.sdLinkAvailable;
   }

   public DiscordUser getLinkedAccount(String minecraftUsername) {
      if (!this.sdLinkAvailable) {
         LOGGER.debug("SDLink not available, cannot get linked account for: {}", minecraftUsername);
         return null;
      } else {
         try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
               LOGGER.warn("Server not available, cannot retrieve linked account");
               return null;
            } else {
               ServerPlayer player = server.getPlayerList().getPlayerByName(minecraftUsername);
               if (player == null) {
                  LOGGER.debug("Player not online: {}", minecraftUsername);
                  UUID playerUuid = server.getProfileCache().get(minecraftUsername).map(profile -> profile.getId()).orElse(null);
                  if (playerUuid == null) {
                     LOGGER.warn("Cannot find UUID for player: {}", minecraftUsername);
                     return null;
                  } else {
                     return this.getLinkedAccountByUuid(playerUuid);
                  }
               } else {
                  return this.getLinkedAccountByUuid(player.getUUID());
               }
            }
         } catch (Exception var5) {
            LOGGER.error("Error getting linked account for {}: {}", new Object[]{minecraftUsername, var5.getMessage(), var5});
            return null;
         }
      }
   }

   public DiscordUser getLinkedAccountByUuid(UUID minecraftUuid) {
      if (!this.sdLinkAvailable) {
         return null;
      } else if (!this.isBotReady()) {
         LOGGER.warn("Discord bot is not ready yet, cannot retrieve linked account for UUID: {}", minecraftUuid);
         return null;
      } else {
         try {
            Object minecraftAccount = this.getMinecraftAccountByUuid(minecraftUuid);
            if (minecraftAccount == null) {
               LOGGER.debug("No linked account found for UUID: {}", minecraftUuid);
               return null;
            } else {
               Method getStoredAccountMethod = this.minecraftAccountClass.getMethod("getStoredAccount");
               Object sdLinkAccount = getStoredAccountMethod.invoke(minecraftAccount);
               if (sdLinkAccount == null) {
                  return null;
               } else {
                  Class<?> sdLinkAccountClass = sdLinkAccount.getClass();
                  Method getDiscordIDMethod = sdLinkAccountClass.getMethod("getDiscordID");
                  Method getInGameNameMethod = sdLinkAccountClass.getMethod("getInGameName");
                  Method getUuidMethod = sdLinkAccountClass.getMethod("getUuid");
                  String discordId = (String)getDiscordIDMethod.invoke(sdLinkAccount);
                  String mcUsername = (String)getInGameNameMethod.invoke(sdLinkAccount);
                  String uuidStr = (String)getUuidMethod.invoke(sdLinkAccount);
                  if (discordId != null && !discordId.isEmpty()) {
                     List<String> discordRoles = this.getDiscordRoles(discordId);
                     String discordUsername = this.getDiscordUsername(discordId);
                     return new DiscordUser(discordId, discordUsername != null ? discordUsername : discordId, mcUsername, uuidStr, discordRoles);
                  } else {
                     LOGGER.debug("No Discord ID linked for Minecraft account: {}", mcUsername);
                     return null;
                  }
               }
            }
         } catch (Exception var14) {
            LOGGER.error("Error getting linked account by UUID {}: {}", new Object[]{minecraftUuid, var14.getMessage(), var14});
            return null;
         }
      }
   }

   public DiscordUser getLinkedAccountByDiscordId(String discordId) {
      if (!this.sdLinkAvailable || discordId == null) {
         return null;
      } else if (!this.isBotReady()) {
         LOGGER.warn("Discord bot is not ready yet, cannot retrieve linked account for Discord ID: {}", discordId);
         return null;
      } else {
         try {
            Object minecraftAccount = this.fromDiscordIdMethod.invoke(null, discordId);
            if (minecraftAccount == null) {
               LOGGER.debug("No linked Minecraft account found for Discord ID: {}", discordId);
               return null;
            } else {
               Method getStoredAccountMethod = this.minecraftAccountClass.getMethod("getStoredAccount");
               Object sdLinkAccount = getStoredAccountMethod.invoke(minecraftAccount);
               if (sdLinkAccount == null) {
                  return null;
               } else {
                  Class<?> sdLinkAccountClass = sdLinkAccount.getClass();
                  Method getInGameNameMethod = sdLinkAccountClass.getMethod("getInGameName");
                  Method getUuidMethod = sdLinkAccountClass.getMethod("getUuid");
                  String mcUsername = (String)getInGameNameMethod.invoke(sdLinkAccount);
                  String uuidStr = (String)getUuidMethod.invoke(sdLinkAccount);
                  List<String> discordRoles = this.getDiscordRoles(discordId);
                  String discordUsername = this.getDiscordUsername(discordId);
                  return new DiscordUser(discordId, discordUsername != null ? discordUsername : discordId, mcUsername, uuidStr, discordRoles);
               }
            }
         } catch (Exception var12) {
            LOGGER.error("Error getting linked account by Discord ID {}: {}", new Object[]{discordId, var12.getMessage(), var12});
            return null;
         }
      }
   }

   public List<String> getDiscordRoles(String discordId) {
      if (!this.sdLinkAvailable || discordId == null) {
         return new ArrayList<>();
      } else if (!this.isBotReady()) {
         LOGGER.debug("Discord bot is not ready yet, cannot retrieve roles");
         return new ArrayList<>();
      } else {
         try {
            Object membersObj = this.getDiscordMembersMethod.invoke(null);
            if (membersObj == null) {
               LOGGER.debug("No cached Discord members available");
               return new ArrayList<>();
            } else if (!(membersObj instanceof Collection<?> members)) {
               LOGGER.warn("Unexpected return type from getDiscordMembers: {}", membersObj.getClass());
               return new ArrayList<>();
            } else if (members.isEmpty()) {
               LOGGER.debug("No cached Discord members available");
               return new ArrayList<>();
            } else {
               for (Object memberObj : members) {
                  Method getIdMethod = memberObj.getClass().getMethod("getId");
                  String memberId = (String)getIdMethod.invoke(memberObj);
                  if (memberId.equals(discordId)) {
                     Method getRolesMethod = memberObj.getClass().getMethod("getRoles");
                     Object rolesObj = getRolesMethod.invoke(memberObj);
                     List<String> roleIds = new ArrayList<>();
                     if (rolesObj instanceof Collection) {
                        for (Object roleObj : (Collection)rolesObj) {
                           Method getRoleIdMethod = roleObj.getClass().getMethod("getId");
                           String roleId = (String)getRoleIdMethod.invoke(roleObj);
                           roleIds.add(roleId);
                        }
                     }

                     LOGGER.debug("Found {} roles for Discord ID {}: {}", new Object[]{roleIds.size(), discordId, roleIds});
                     return roleIds;
                  }
               }

               LOGGER.debug("Discord member {} not found in cache", discordId);
               return new ArrayList<>();
            }
         } catch (Exception var16) {
            LOGGER.error("Error getting Discord roles for {}: {}", new Object[]{discordId, var16.getMessage(), var16});
            return new ArrayList<>();
         }
      }
   }

   private String getDiscordUsername(String discordId) {
      try {
         List<?> members = (List<?>)this.getDiscordMembersMethod.invoke(null);
         if (members == null || members.isEmpty()) {
            return null;
         }

         for (Object memberObj : members) {
            Method getIdMethod = memberObj.getClass().getMethod("getId");
            String memberId = (String)getIdMethod.invoke(memberObj);
            if (memberId.equals(discordId)) {
               Method getEffectiveNameMethod = memberObj.getClass().getMethod("getEffectiveName");
               return (String)getEffectiveNameMethod.invoke(memberObj);
            }
         }
      } catch (Exception var8) {
         LOGGER.debug("Could not get Discord username for {}: {}", discordId, var8.getMessage());
      }

      return null;
   }

   private Object getMinecraftAccountByUuid(UUID minecraftUuid) {
      try {
         SDLinkDataReader dataReader = new SDLinkDataReader(ServerLifecycleHooks.getCurrentServer().getServerDirectory());
         String discordId = dataReader.getDiscordId(minecraftUuid);
         return discordId == null ? null : this.fromDiscordIdMethod.invoke(null, discordId);
      } catch (Exception var4) {
         LOGGER.debug("Could not get MinecraftAccount for UUID {}: {}", minecraftUuid, var4.getMessage());
         return null;
      }
   }

   public boolean isAccountLinked(String minecraftUsername) {
      DiscordUser user = this.getLinkedAccount(minecraftUsername);
      return user != null && user.isLinked();
   }

   public boolean isAccountLinkedByUuid(UUID minecraftUuid) {
      DiscordUser user = this.getLinkedAccountByUuid(minecraftUuid);
      return user != null && user.isLinked();
   }

   public void refreshCache() {
      if (this.sdLinkAvailable) {
         try {
            if (this.cacheManagerClass != null) {
               try {
                  Method getInstanceMethod = this.cacheManagerClass.getMethod("getInstance");
                  Object cacheManagerInstance = getInstanceMethod.invoke(null);
                  if (cacheManagerInstance != null) {
                     try {
                        Method refreshMethod = this.cacheManagerClass.getMethod("refreshCache");
                        refreshMethod.invoke(cacheManagerInstance);
                        LOGGER.info("Discord cache refreshed successfully");
                     } catch (NoSuchMethodException var6) {
                        try {
                           Method reloadMethod = this.cacheManagerClass.getMethod("reload");
                           reloadMethod.invoke(cacheManagerInstance);
                           LOGGER.info("Discord cache reloaded successfully");
                        } catch (NoSuchMethodException var5) {
                           LOGGER.debug("SDLink CacheManager does not provide refresh/reload methods");
                        }
                     }
                  }
               } catch (NoSuchMethodException var7) {
                  LOGGER.debug("SDLink CacheManager does not provide getInstance method");
               }
            }

            LOGGER.debug("Discord cache refresh requested");
         } catch (Exception var8) {
            LOGGER.error("Error refreshing Discord cache: {}", var8.getMessage(), var8);
         }
      }
   }

   private boolean isBotReady() {
      if (!SDLinkEventListener.isBotReady()) {
         return false;
      } else {
         try {
            Class<?> botControllerClass = Class.forName("com.hypherionmc.sdlink.core.discord.BotController");
            Object botController = botControllerClass.getField("INSTANCE").get(null);
            Method isBotReadyMethod = botControllerClass.getMethod("isBotReady");
            Boolean ready = (Boolean)isBotReadyMethod.invoke(botController);
            return ready != null && ready;
         } catch (Exception var5) {
            LOGGER.debug("Could not check BotController.isBotReady(), using event listener state: {}", var5.getMessage());
            return SDLinkEventListener.isBotReady();
         }
      }
   }
}
