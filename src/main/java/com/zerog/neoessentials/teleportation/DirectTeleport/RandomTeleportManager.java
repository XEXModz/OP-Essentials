package com.zerog.neoessentials.teleportation.DirectTeleport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomTeleportManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(RandomTeleportManager.class);
   private static final Random RANDOM = new Random();
   private static final String DEFAULT_LOCATION_KEY = "default";
   private final Map<String, ConcurrentLinkedQueue<TeleportLocation>> locationCache = new ConcurrentHashMap<>();
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

   public static RandomTeleportManager getInstance() {
      return RandomTeleportManager.Holder.INSTANCE;
   }

   private RandomTeleportManager() {
   }

   public CompletableFuture<Boolean> randomTeleport(ServerPlayer player, String locationName) {
      int cooldownSecs = this.getTprCooldown();
      if (cooldownSecs > 0) {
         long last = this.cooldowns.getOrDefault(player.getUUID(), 0L);
         long remaining = last + (long)cooldownSecs * 1000L - System.currentTimeMillis();
         if (remaining > 0L) {
            long secs = remaining / 1000L + 1L;
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_cooldown", String.valueOf(secs)));
            return CompletableFuture.completedFuture(false);
         }
      }

      String name = locationName != null && !locationName.isEmpty() ? locationName : this.resolveDefaultName(player);
      player.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.misc.tpr_searching"));
      CompletableFuture<Boolean> result = new CompletableFuture<>();
      this.getRandomLocation(player.serverLevel(), name)
         .thenAccept(
            loc -> {
               if (loc == null) {
                  player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_no_safe_location"));
                  result.complete(false);
               } else {
                  MiscTeleportManager.getInstance().saveBackLocation(player);
                  int delayTicks = this.getTeleportDelaySecs() * 20;
                  TeleportUtil.teleportPlayer(player, loc, delayTicks, false)
                     .thenAccept(
                        tpResult -> {
                           if (tpResult.isSuccess()) {
                              this.cooldowns.put(player.getUUID(), System.currentTimeMillis());
                              player.sendSystemMessage(
                                 MessageUtil.success(
                                    "commands.neoessentials.teleport.misc.tpr_success",
                                    String.valueOf((int)loc.getX()),
                                    String.valueOf((int)loc.getY()),
                                    String.valueOf((int)loc.getZ())
                                 )
                              );
                              LOGGER.info(
                                 "Player {} randomly teleported to ({}, {}, {}) in {}",
                                 new Object[]{player.getName().getString(), (int)loc.getX(), (int)loc.getY(), (int)loc.getZ(), loc.getWorldName()}
                              );
                              result.complete(true);
                              this.prewarmCache(player.serverLevel(), name);
                           } else {
                              player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_failed", tpResult.getMessage()));
                              result.complete(false);
                           }
                        }
                     );
               }
            }
         )
         .exceptionally(ex -> {
            LOGGER.error("RandomTeleport error for {}: {}", new Object[]{player.getName().getString(), ex.getMessage(), ex});
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.misc.tpr_failed", ex.getMessage()));
            result.complete(false);
            return null;
         });
      return result;
   }

   public CompletableFuture<TeleportLocation> getRandomLocation(ServerLevel level, String name) {
      Queue<TeleportLocation> cache = this.getCache(name);
      if (!cache.isEmpty()) {
         return CompletableFuture.completedFuture(cache.poll());
      } else {
         double[] center = this.getCenter(level, name);
         double minRange = this.getMinRange(name);
         double maxRange = this.getMaxRange(level, name);
         int attempts = this.getFindAttempts();
         return this.attemptFind(level, center[0], center[1], center[2], minRange, maxRange, name, attempts);
      }
   }

   public CompletableFuture<TeleportLocation> getRandomLocation(ServerLevel level, double cx, double cy, double cz, double minRange, double maxRange) {
      return this.attemptFind(level, cx, cy, cz, minRange, maxRange, null, this.getFindAttempts());
   }

   private CompletableFuture<TeleportLocation> attemptFind(
      ServerLevel level, double cx, double cy, double cz, double minRange, double maxRange, String locationName, int attemptsLeft
   ) {
      if (attemptsLeft <= 0) {
         return CompletableFuture.completedFuture(null);
      } else {
         double[] offset = this.randomOffset(minRange, maxRange);
         double rx = cx + offset[0];
         double rz = cz + offset[1];
         rx = this.clampToWorldBorder(level, rx, true);
         rz = this.clampToWorldBorder(level, rz, false);
         return CompletableFuture.<TeleportLocation>supplyAsync(() -> this.findSafeY(level, rx, rz, locationName), level.getServer())
            .thenCompose(
               loc -> loc != null && this.isValid(loc, locationName)
                     ? CompletableFuture.completedFuture(loc)
                     : this.attemptFind(level, cx, cy, cz, minRange, maxRange, locationName, attemptsLeft - 1)
            );
      }
   }

   private TeleportLocation findSafeY(ServerLevel level, double x, double z, String locationName) {
      try {
         int chunkX = (int)x >> 4;
         int chunkZ = (int)z >> 4;
         LevelChunk chunk = level.getChunk(chunkX, chunkZ);
         boolean isNether = level.dimensionType().ultraWarm();
         int ix = (int)Math.floor(x);
         int iz = (int)Math.floor(z);
         int y;
         if (isNether) {
            y = this.findNetherY(level, ix, iz);
         } else {
            y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
         }

         if (y <= level.getMinBuildHeight()) {
            return null;
         } else {
            BlockPos feet = new BlockPos(ix, y, iz);
            if (!this.isSafeSpot(level, feet)) {
               for (int dy = 1; dy <= 4; dy++) {
                  BlockPos candidate = feet.above(dy);
                  if (this.isSafeSpot(level, candidate)) {
                     y = candidate.getY();
                     feet = candidate;
                     break;
                  }
               }

               if (!this.isSafeSpot(level, feet)) {
                  return null;
               }
            }

            return new TeleportLocation(level, feet, RANDOM.nextFloat() * 360.0F - 180.0F, 0.0F, "RandomTeleport");
         }
      } catch (Exception var17) {
         LOGGER.debug("findSafeY error at ({},{}): {}", new Object[]{x, z, var17.getMessage()});
         return null;
      }
   }

   private int findNetherY(ServerLevel level, int x, int z) {
      int maxScan = level.getMaxBuildHeight() - 1;

      for (int y = 32; y < maxScan; y++) {
         BlockPos pos = new BlockPos(x, y, z);
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.BEDROCK)) {
            break;
         }

         if (this.isSafeSpot(level, pos)) {
            return y;
         }
      }

      return Integer.MIN_VALUE;
   }

   private boolean isSafeSpot(ServerLevel level, BlockPos feet) {
      BlockPos ground = feet.below();
      BlockPos head = feet.above();
      BlockState groundState = level.getBlockState(ground);
      BlockState feetState = level.getBlockState(feet);
      BlockState headState = level.getBlockState(head);
      if (!groundState.isSolid()) {
         return false;
      } else if (!feetState.isAir()) {
         return false;
      } else if (!headState.isAir()) {
         return false;
      } else {
         return this.isDangerous(groundState) ? false : !this.isDangerous(feetState);
      }
   }

   private boolean isDangerous(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.LAVA
         || block == Blocks.WATER
         || block == Blocks.FIRE
         || block == Blocks.SOUL_FIRE
         || block == Blocks.MAGMA_BLOCK
         || block == Blocks.CACTUS
         || block == Blocks.SWEET_BERRY_BUSH
         || block == Blocks.WITHER_ROSE
         || block == Blocks.NETHER_PORTAL;
   }

   private double[] randomOffset(double minRange, double maxRange) {
      double rectX = RANDOM.nextDouble() * (maxRange - minRange) + minRange;
      double rectZ = RANDOM.nextDouble() * (maxRange + minRange) - minRange;
      int transform = RANDOM.nextInt(4);
      double offX;
      double offZ;
      switch (transform) {
         case 0:
            offX = rectX;
            offZ = rectZ;
            break;
         case 1:
            offX = -rectZ;
            offZ = rectX;
            break;
         case 2:
            offX = -rectX;
            offZ = -rectZ;
            break;
         default:
            offX = rectZ;
            offZ = -rectX;
      }

      return new double[]{offX, offZ};
   }

   private double clampToWorldBorder(ServerLevel level, double coord, boolean isX) {
      WorldBorder border = level.getWorldBorder();
      double cx = border.getCenterX();
      double cz = border.getCenterZ();
      double half = border.getSize() / 2.0;
      return isX ? Math.max(cx - half, Math.min(cx + half, coord)) : Math.max(cz - half, Math.min(cz + half, coord));
   }

   private boolean isValid(TeleportLocation loc, String locationName) {
      ServerLevel level = loc.getLevel();
      if (level == null) {
         return false;
      } else if (loc.getY() <= (double)level.getMinBuildHeight()) {
         return false;
      } else {
         if (locationName != null) {
            List<String> excluded = this.getExcludedBiomes(locationName);
            if (!excluded.isEmpty()) {
               BlockPos pos = new BlockPos((int)loc.getX(), (int)loc.getY(), (int)loc.getZ());
               String biomeName = this.getBiomeName(level, pos);
               if (biomeName != null && excluded.contains(biomeName.toLowerCase())) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private String getBiomeName(ServerLevel level, BlockPos pos) {
      try {
         net.minecraft.core.Holder<Biome> biomeHolder = level.getBiome(pos);
         return biomeHolder.unwrapKey().map(key -> key.location().toString()).orElse(null);
      } catch (Exception var4) {
         return null;
      }
   }

   private ConcurrentLinkedQueue<TeleportLocation> getCache(String name) {
      return this.locationCache.computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>());
   }

   private void prewarmCache(ServerLevel level, String name) {
      int threshold = this.getCacheThreshold();
      int current = this.getCache(name).size();
      if (current < threshold) {
         int toFill = threshold - current;
         double[] center = this.getCenter(level, name);
         double minRange = this.getMinRange(name);
         double maxRange = this.getMaxRange(level, name);

         for (int i = 0; i < toFill; i++) {
            double[] offset = this.randomOffset(minRange, maxRange);
            double rx = this.clampToWorldBorder(level, center[0] + offset[0], true);
            double rz = this.clampToWorldBorder(level, center[2] + offset[1], false);
            CompletableFuture.runAsync(() -> {
               TeleportLocation loc = this.findSafeY(level, rx, rz, name);
               if (loc != null && this.isValid(loc, name)) {
                  this.getCache(name).add(loc);
               }
            }, level.getServer());
         }
      }
   }

   public void clearCache() {
      this.locationCache.clear();
   }

   public void clearCache(String name) {
      ConcurrentLinkedQueue<TeleportLocation> q = this.locationCache.get(name);
      if (q != null) {
         q.clear();
      }
   }

   private String resolveDefaultName(ServerPlayer player) {
      String def = this.getConfigString("defaultLocation", "{world}");
      return def.replace("{world}", player.level().dimension().location().toString());
   }

   private double[] getCenter(ServerLevel level, String name) {
      JsonObject tpr = this.getTprConfig();
      if (tpr != null && tpr.has("locations")) {
         JsonObject locs = tpr.getAsJsonObject("locations");
         if (locs.has(name) && locs.getAsJsonObject(name).has("center")) {
            JsonObject c = locs.getAsJsonObject(name).getAsJsonObject("center");
            double x = c.has("x") ? c.get("x").getAsDouble() : 0.0;
            double y = c.has("y") ? c.get("y").getAsDouble() : 64.0;
            double z = c.has("z") ? c.get("z").getAsDouble() : 0.0;
            return new double[]{x, y, z};
         }
      }

      WorldBorder border = level.getWorldBorder();
      return new double[]{border.getCenterX(), (double)level.getSeaLevel(), border.getCenterZ()};
   }

   private double getMinRange(String name) {
      JsonObject tpr = this.getTprConfig();
      if (tpr != null && tpr.has("locations")) {
         JsonObject locs = tpr.getAsJsonObject("locations");
         if (locs.has(name) && locs.getAsJsonObject(name).has("minRange")) {
            return locs.getAsJsonObject(name).get("minRange").getAsDouble();
         }
      }

      return this.getConfigDouble("defaultMinRange", 0.0);
   }

   private double getMaxRange(ServerLevel level, String name) {
      JsonObject tpr = this.getTprConfig();
      if (tpr != null && tpr.has("locations")) {
         JsonObject locs = tpr.getAsJsonObject("locations");
         if (locs.has(name) && locs.getAsJsonObject(name).has("maxRange")) {
            return locs.getAsJsonObject(name).get("maxRange").getAsDouble();
         }
      }

      double def = this.getConfigDouble("defaultMaxRange", -1.0);
      return def <= 0.0 ? level.getWorldBorder().getSize() / 2.0 : def;
   }

   private int getFindAttempts() {
      return (int)this.getConfigDouble("findAttempts", 10.0);
   }

   private int getCacheThreshold() {
      return (int)this.getConfigDouble("cacheThreshold", 10.0);
   }

   private int getTprCooldown() {
      return (int)this.getConfigDouble("cooldown", 60.0);
   }

   private int getTeleportDelaySecs() {
      try {
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("generalSettings")) {
               JsonObject gs = tp.getAsJsonObject("generalSettings");
               if (gs.has("teleportDelay")) {
                  return gs.get("teleportDelay").getAsInt();
               }
            }
         }
      } catch (Exception var4) {
      }

      return 3;
   }

   private List<String> getExcludedBiomes(String name) {
      List<String> result = new ArrayList<>();
      JsonObject tpr = this.getTprConfig();
      if (tpr == null) {
         return result;
      } else {
         if (tpr.has("locations")) {
            JsonObject locs = tpr.getAsJsonObject("locations");
            if (locs.has(name) && locs.getAsJsonObject(name).has("excludedBiomes")) {
               JsonArray arr = locs.getAsJsonObject(name).getAsJsonArray("excludedBiomes");
               arr.forEach(e -> result.add(e.getAsString().toLowerCase()));
               return result;
            }
         }

         if (tpr.has("excludedBiomes")) {
            JsonArray arr = tpr.getAsJsonArray("excludedBiomes");
            arr.forEach(e -> result.add(e.getAsString().toLowerCase()));
         }

         return result;
      }
   }

   private JsonObject getTprConfig() {
      try {
         JsonObject config = ConfigManager.getInstance().getConfig("config.json");
         if (config.has("teleportation")) {
            JsonObject tp = config.getAsJsonObject("teleportation");
            if (tp.has("randomTeleportSettings")) {
               return tp.getAsJsonObject("randomTeleportSettings");
            }
         }
      } catch (Exception var3) {
      }

      return null;
   }

   private double getConfigDouble(String key, double def) {
      JsonObject tpr = this.getTprConfig();
      return tpr != null && tpr.has(key) ? tpr.get(key).getAsDouble() : def;
   }

   private String getConfigString(String key, String def) {
      JsonObject tpr = this.getTprConfig();
      return tpr != null && tpr.has(key) ? tpr.get(key).getAsString() : def;
   }

   private static class Holder {
      static final RandomTeleportManager INSTANCE = new RandomTeleportManager();
   }
}
