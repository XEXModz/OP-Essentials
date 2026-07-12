package com.zerog.neoessentials.permissions;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtbRanksAdapter implements ExternalPermissionAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(FtbRanksAdapter.class);
   private static final int MAX_FAILURES = 5;
   private static final String LAST_TESTED_VERSION = "2101.1.3";
   private final boolean ftbRanksLoaded;
   private final String detectedVersion;
   private Method resolvedMethod = null;
   private Object resolvedInstance = null;
   private int resolvedStrategy = 0;
   private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

   public FtbRanksAdapter() {
      this.ftbRanksLoaded = ModList.get().isLoaded("ftbranks");
      this.detectedVersion = ModList.get().getModContainerById("ftbranks").map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
      if (this.ftbRanksLoaded) {
         LOGGER.info("FTB Ranks detected — version: {}", this.detectedVersion);
         if (!this.detectedVersion.equals("unknown") && !this.detectedVersion.startsWith("2101.1.3".substring(0, "2101.1.3".lastIndexOf(46)))) {
            LOGGER.warn("╔══════════════════════════════════════════════════════════════╗");
            LOGGER.warn("║  FTB RANKS COMPATIBILITY WARNING                              ║");
            LOGGER.warn("║  Detected version : {}                              ║", padRight(this.detectedVersion, 30));
            LOGGER.warn("║  Last tested with : {}                              ║", padRight("2101.1.3", 30));
            LOGGER.warn("║  If permissions stop working, please report this version     ║");
            LOGGER.warn("║  mismatch at github.com/your-repo/neoessentials/issues       ║");
            LOGGER.warn("╚══════════════════════════════════════════════════════════════╝");
         }

         this.probeApi();
      }
   }

   private void probeApi() {
      try {
         Class<?> apiClass = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");

         try {
            Method m = apiClass.getMethod("getPermissionValue", ServerPlayer.class, String.class);
            this.resolvedMethod = m;
            this.resolvedInstance = null;
            this.resolvedStrategy = 1;
            LOGGER.info("FTB Ranks adapter: strategy 1 — getPermissionValue(ServerPlayer, String)");
            return;
         } catch (NoSuchMethodException var11) {
            try {
               Object apiInstance = apiClass.getMethod("getInstance").invoke(null);
               if (apiInstance != null) {
                  Object rankManager = apiInstance.getClass().getMethod("getManager").invoke(apiInstance);
                  if (rankManager != null) {
                     Method mx = rankManager.getClass().getMethod("getPermissionValue", ServerPlayer.class, String.class);
                     this.resolvedMethod = mx;
                     this.resolvedInstance = rankManager;
                     this.resolvedStrategy = 2;
                     LOGGER.info("FTB Ranks adapter: strategy 2 — RankManager.getPermissionValue(ServerPlayer, String)");
                     return;
                  }
               }
            } catch (Exception var7) {
            }

            try {
               Method mx = apiClass.getMethod("hasPermission", ServerPlayer.class, String.class);
               this.resolvedMethod = mx;
               this.resolvedInstance = null;
               this.resolvedStrategy = 3;
               LOGGER.info("FTB Ranks adapter: strategy 3 — hasPermission(ServerPlayer, String)");
               return;
            } catch (NoSuchMethodException var10) {
               try {
                  Method mxx = apiClass.getMethod("checkPermission", ServerPlayer.class, String.class);
                  this.resolvedMethod = mxx;
                  this.resolvedInstance = null;
                  this.resolvedStrategy = 4;
                  LOGGER.info("FTB Ranks adapter: strategy 4 — checkPermission(ServerPlayer, String)");
                  return;
               } catch (NoSuchMethodException var9) {
                  Object instance = null;

                  try {
                     instance = apiClass.getField("INSTANCE").get(null);
                  } catch (NoSuchFieldException var6) {
                     try {
                        instance = apiClass.getMethod("getInstance").invoke(null);
                     } catch (Exception var5) {
                     }
                  }

                  if (instance != null) {
                     try {
                        Method mxxx = instance.getClass().getMethod("hasPermission", UUID.class, String.class);
                        this.resolvedMethod = mxxx;
                        this.resolvedInstance = instance;
                        this.resolvedStrategy = 5;
                        LOGGER.info("FTB Ranks adapter: strategy 5 — instance.hasPermission(UUID, String)");
                        return;
                     } catch (NoSuchMethodException var8) {
                     }
                  }

                  LOGGER.warn("╔══════════════════════════════════════════════════════════════╗");
                  LOGGER.warn("║  FTB RANKS API NOT RESOLVED                                  ║");
                  LOGGER.warn("║  Version {} did not match any known API signature.  ║", padRight(this.detectedVersion, 24));
                  LOGGER.warn("║  Permission checks will fall back to OP / internal system.   ║");
                  LOGGER.warn("║  Please report this at the NeoEssentials issue tracker.      ║");
                  LOGGER.warn("╚══════════════════════════════════════════════════════════════╝");
               }
            }
         }
      } catch (ClassNotFoundException var12) {
         LOGGER.debug("FTB Ranks API class not found — mod may not be installed");
      } catch (Exception var13) {
         LOGGER.warn("FTB Ranks adapter init failed: {}", var13.getMessage());
      }
   }

   @Override
   public boolean hasPermission(UUID uuid, String permission) {
      if (this.ftbRanksLoaded && this.resolvedMethod != null) {
         try {
            boolean result = this.invokeResolvedMethod(uuid, permission);
            this.consecutiveFailures.set(0);
            return result;
         } catch (Exception var5) {
            int failures = this.consecutiveFailures.incrementAndGet();
            this.emitHealthWarnIfNeeded(failures, permission, var5);
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean invokeResolvedMethod(UUID uuid, String permission) throws Exception {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (this.resolvedStrategy == 1) {
         if (server == null) {
            return false;
         } else {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            return player == null ? false : this.extractBoolean(this.resolvedMethod.invoke(null, player, permission));
         }
      } else if (this.resolvedStrategy == 2) {
         if (server == null) {
            return false;
         } else {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            return player == null ? false : this.extractBoolean(this.resolvedMethod.invoke(this.resolvedInstance, player, permission));
         }
      } else if (this.resolvedStrategy != 3 && this.resolvedStrategy != 4) {
         if (this.resolvedStrategy != 5) {
            return false;
         } else {
            if (this.resolvedMethod.invoke(this.resolvedInstance, uuid, permission) instanceof Boolean b && b) {
               return true;
            }

            return false;
         }
      } else if (server == null) {
         return false;
      } else {
         ServerPlayer player = server.getPlayerList().getPlayer(uuid);
         return player == null ? false : this.extractBoolean(this.resolvedMethod.invoke(null, player, permission));
      }
   }

   private boolean extractBoolean(Object result) {
      if (result == null) {
         return false;
      } else if (result instanceof Boolean b) {
         return b;
      } else {
         try {
            if (result.getClass().getMethod("asBooleanOrFalse").invoke(result) instanceof Boolean b) {
               return b;
            }
         } catch (Exception var5) {
         }

         if (result instanceof Optional<?> opt && opt.orElse(null) instanceof Boolean b) {
            return b;
         }

         try {
            return (Boolean)result.getClass().getMethod("get").invoke(result);
         } catch (Exception var6) {
            String s = result.toString().toUpperCase();
            return !s.equals("FALSE") && !s.equals("UNDEFINED") && !s.equals("DENY") && !s.equals("MISSING");
         }
      }
   }

   private void emitHealthWarnIfNeeded(int failures, String permission, Exception cause) {
      if (failures == 1) {
         LOGGER.error("FTB Ranks permission check failed for '{}': {}", permission, cause.getMessage());
      } else if (failures == 5) {
         LOGGER.warn("╔══════════════════════════════════════════════════════════════╗");
         LOGGER.warn("║  FTB RANKS ADAPTER UNHEALTHY — {} consecutive failures    ║", 5);
         LOGGER.warn("║  Version     : {}                                   ║", padRight(this.detectedVersion, 21));
         LOGGER.warn(
            "║  Last error  : {}  ║",
            padRight(cause.getMessage() != null ? cause.getMessage().substring(0, Math.min(cause.getMessage().length(), 42)) : "n/a", 42)
         );
         LOGGER.warn("║  NeoEssentials will fall back to internal permissions.       ║");
         LOGGER.warn("║  Resolve the FTB Ranks API issue and run /neoe reload.       ║");
         LOGGER.warn("╚══════════════════════════════════════════════════════════════╝");
      }
   }

   @Override
   public String getPrefix(UUID uuid) {
      return null;
   }

   @Override
   public String getSuffix(UUID uuid) {
      return null;
   }

   @Override
   public void reload() {
   }

   @Override
   public String getName() {
      return "FTB Ranks";
   }

   @Override
   public boolean isAvailable() {
      return this.ftbRanksLoaded && this.resolvedMethod != null;
   }

   @Override
   public String getVersion() {
      return this.detectedVersion;
   }

   @Override
   public boolean isHealthy() {
      return this.consecutiveFailures.get() < 5;
   }

   @Override
   public int getConsecutiveFailures() {
      return this.consecutiveFailures.get();
   }

   private static String padRight(String s, int width) {
      if (s == null) {
         s = "";
      }

      return s.length() >= width ? s.substring(0, width) : s + " ".repeat(width - s.length());
   }
}
