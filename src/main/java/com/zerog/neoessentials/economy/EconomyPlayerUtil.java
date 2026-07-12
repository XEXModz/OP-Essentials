package com.zerog.neoessentials.economy;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class EconomyPlayerUtil {
   public static Optional<ServerPlayer> getOnlinePlayer(MinecraftServer server, String name) {
      return server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name)).findFirst();
   }

   public static Optional<UUID> getUUIDByName(MinecraftServer server, String name) {
      Optional<ServerPlayer> online = getOnlinePlayer(server, name);
      if (online.isPresent()) {
         return Optional.of(online.get().getUUID());
      } else {
         GameProfile profile = (GameProfile)server.getProfileCache().get(name).orElse(null);
         return profile != null ? Optional.of(profile.getId()) : Optional.empty();
      }
   }
}
