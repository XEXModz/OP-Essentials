package com.zerog.neoessentials.teleportation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class TeleportLocation {
   private final String worldName;
   private final double x;
   private final double y;
   private final double z;
   private final float yaw;
   private final float pitch;
   private final long timestamp;
   private final String createdBy;

   public TeleportLocation(String worldName, double x, double y, double z, float yaw, float pitch, String createdBy) {
      this.worldName = worldName;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = !Float.isNaN(yaw) && !Float.isInfinite(yaw) ? yaw : 0.0F;
      this.pitch = !Float.isNaN(pitch) && !Float.isInfinite(pitch) ? pitch : 0.0F;
      this.timestamp = System.currentTimeMillis();
      this.createdBy = createdBy;
   }

   public TeleportLocation(ServerPlayer player) {
      this(
         player.level().dimension().location().toString(),
         player.getX(),
         player.getY(),
         player.getZ(),
         !Float.isNaN(player.getYRot()) && !Float.isInfinite(player.getYRot()) ? player.getYRot() : 0.0F,
         !Float.isNaN(player.getXRot()) && !Float.isInfinite(player.getXRot()) ? player.getXRot() : 0.0F,
         player.getName().getString()
      );
   }

   public TeleportLocation(ServerLevel level, BlockPos pos, float yaw, float pitch, String createdBy) {
      this(level.dimension().location().toString(), (double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, yaw, pitch, createdBy);
   }

   public String getWorldName() {
      return this.worldName;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }

   public long getTimestamp() {
      return this.timestamp;
   }

   public String getCreatedBy() {
      return this.createdBy;
   }

   public ServerLevel getLevel() {
      try {
         ResourceLocation worldKey;
         if (this.worldName.contains(":")) {
            String[] parts = this.worldName.split(":", 2);
            worldKey = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
         } else {
            worldKey = ResourceLocation.fromNamespaceAndPath("minecraft", this.worldName);
         }

         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         return server == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, worldKey));
      } catch (Exception var3) {
         return null;
      }
   }

   public boolean isSafe() {
      ServerLevel level = this.getLevel();
      if (level == null) {
         return false;
      } else {
         BlockPos pos = new BlockPos((int)Math.floor(this.x), (int)Math.floor(this.y), (int)Math.floor(this.z));
         if (!level.isLoaded(pos)) {
            return false;
         } else {
            BlockPos ground = pos.below();
            BlockPos head = pos.above();
            BlockState groundState = level.getBlockState(ground);
            BlockState feetState = level.getBlockState(pos);
            BlockState headState = level.getBlockState(head);
            if (groundState.isAir() || groundState.getCollisionShape(level, ground).isEmpty()) {
               return false;
            } else if (!feetState.getCollisionShape(level, pos).isEmpty() && !feetState.isAir()) {
               return false;
            } else if (!headState.getCollisionShape(level, head).isEmpty() && !headState.isAir()) {
               return false;
            } else {
               return this.isDangerous(level, ground) ? false : !this.isDangerous(level, pos);
            }
         }
      }
   }

   private boolean isDangerous(ServerLevel level, BlockPos pos) {
      Block block = level.getBlockState(pos).getBlock();
      return block == Blocks.LAVA
         || block == Blocks.WATER
         || block == Blocks.FIRE
         || block == Blocks.SOUL_FIRE
         || block == Blocks.MAGMA_BLOCK
         || block == Blocks.CACTUS
         || block == Blocks.SWEET_BERRY_BUSH
         || block == Blocks.WITHER_ROSE
         || block == Blocks.NETHER_PORTAL
         || block == Blocks.CAMPFIRE
         || block == Blocks.SOUL_CAMPFIRE
         || block == Blocks.POWDER_SNOW;
   }

   public TeleportLocation findSafeLocation() {
      ServerLevel level = this.getLevel();
      if (level == null) {
         return null;
      } else if (this.isSafe()) {
         return this;
      } else {
         int ix = (int)Math.floor(this.x);
         int iz = (int)Math.floor(this.z);
         TeleportLocation col = this.scanColumnTopDown(level, ix, iz);
         if (col != null) {
            return col;
         } else {
            for (int radius = 1; radius <= 16; radius++) {
               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        int bx = ix + dx;
                        int bz = iz + dz;
                        int startY = (int)Math.floor(this.y);

                        for (int dy = -8; dy <= 8; dy++) {
                           BlockPos testPos = new BlockPos(bx, startY + dy, bz);
                           TeleportLocation testLoc = new TeleportLocation(level, testPos, this.yaw, this.pitch, this.createdBy);
                           if (testLoc.isSafe()) {
                              return testLoc;
                           }
                        }
                     }
                  }
               }
            }

            return null;
         }
      }
   }

   private TeleportLocation scanColumnTopDown(ServerLevel level, int bx, int bz) {
      int maxY = level.getMaxBuildHeight() - 2;
      int minY = level.getMinBuildHeight() + 1;

      for (int by = maxY; by >= minY; by--) {
         BlockPos candidate = new BlockPos(bx, by, bz);
         TeleportLocation loc = new TeleportLocation(level, candidate, this.yaw, this.pitch, this.createdBy);
         if (loc.isSafe()) {
            return loc;
         }
      }

      return null;
   }

   public String getCoordinatesString() {
      return String.format("%.1f, %.1f, %.1f", this.x, this.y, this.z);
   }

   public String getLocationString() {
      return String.format("%s (%.1f, %.1f, %.1f)", this.getWorldDisplayName(), this.x, this.y, this.z);
   }

   public String getWorldDisplayName() {
      if (this.worldName.contains("overworld")) {
         return "Overworld";
      } else if (this.worldName.contains("nether")) {
         return "Nether";
      } else {
         return this.worldName.contains("end") ? "End" : this.worldName;
      }
   }

   public double distanceTo(TeleportLocation other) {
      if (!this.worldName.equals(other.worldName)) {
         return Double.MAX_VALUE;
      } else {
         double dx = this.x - other.x;
         double dy = this.y - other.y;
         double dz = this.z - other.z;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("world", this.worldName);
      json.addProperty("x", this.x);
      json.addProperty("y", this.y);
      json.addProperty("z", this.z);
      json.addProperty("yaw", this.yaw);
      json.addProperty("pitch", this.pitch);
      json.addProperty("timestamp", this.timestamp);
      json.addProperty("createdBy", this.createdBy);
      return json;
   }

   public String toLocationString() {
      return this.toJson().toString();
   }

   public static TeleportLocation fromJson(JsonObject json) {
      try {
         String world = json.get("world").getAsString();
         double x = json.get("x").getAsDouble();
         double y = json.get("y").getAsDouble();
         double z = json.get("z").getAsDouble();
         float yaw = 0.0F;
         float pitch = 0.0F;
         if (json.has("yaw")) {
            yaw = json.get("yaw").getAsFloat();
            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) {
               yaw = 0.0F;
            }
         }

         if (json.has("pitch")) {
            pitch = json.get("pitch").getAsFloat();
            if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
               pitch = 0.0F;
            }
         }

         String createdBy = json.has("createdBy") ? json.get("createdBy").getAsString() : "Unknown";
         return new TeleportLocation(world, x, y, z, yaw, pitch, createdBy);
      } catch (Exception var11) {
         return null;
      }
   }

   public static TeleportLocation fromLocationString(String locationString) {
      if (locationString != null && !locationString.trim().isEmpty()) {
         try {
            if (locationString.trim().startsWith("{")) {
               JsonObject json = JsonParser.parseString(locationString).getAsJsonObject();
               return fromJson(json);
            }

            String[] parts = locationString.split(",");
            if (parts.length >= 4) {
               String world = parts[0].trim();
               double x = Double.parseDouble(parts[1].trim());
               double y = Double.parseDouble(parts[2].trim());
               double z = Double.parseDouble(parts[3].trim());
               float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0F;
               float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0.0F;
               return new TeleportLocation(world, x, y, z, yaw, pitch, "System");
            }
         } catch (Exception var11) {
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public String toString() {
      return String.format(
         "TeleportLocation{world=%s, x=%.2f, y=%.2f, z=%.2f, yaw=%.1f, pitch=%.1f}", this.worldName, this.x, this.y, this.z, this.yaw, this.pitch
      );
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof TeleportLocation other)
            ? false
            : this.worldName.equals(other.worldName)
               && Math.abs(this.x - other.x) < 0.1
               && Math.abs(this.y - other.y) < 0.1
               && Math.abs(this.z - other.z) < 0.1;
      }
   }

   @Override
   public int hashCode() {
      return this.worldName.hashCode() ^ Double.hashCode(this.x) ^ Double.hashCode(this.y) ^ Double.hashCode(this.z);
   }
}
