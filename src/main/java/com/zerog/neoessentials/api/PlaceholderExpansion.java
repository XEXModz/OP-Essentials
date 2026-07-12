package com.zerog.neoessentials.api;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

abstract class PlaceholderExpansion {
   public abstract String getIdentifier();

   public abstract String getVersion();

   public abstract String getAuthor();

   @Nullable
   public abstract String onPlaceholderRequest(@Nullable ServerPlayer var1, String var2, @Nullable String var3);

   public abstract Set<String> getPlaceholders();
}
