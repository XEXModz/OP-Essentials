package com.zerog.neoessentials.api;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
interface PlaceholderProvider {
   @Nullable
   String onRequest(@Nullable ServerPlayer var1, @Nullable String var2);
}
