package com.zerog.neoessentials.economy.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class BaltopCommand {
   private static final int PAGE_SIZE = 10;
   private static volatile List<BaltopCommand.BaltopEntry> cachedTop = Collections.emptyList();
   private static volatile BigDecimal cachedTotal = BigDecimal.ZERO;
   private static volatile long cacheAge = 0L;
   private static final AtomicBoolean cacheBuilding = new AtomicBoolean(false);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      for (String name : new String[]{"baltop", "balancetop", "btop"}) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(name).requires(src -> {
                  ServerPlayer player = src.getPlayer();
                  return player == null || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.economy.baltop");
               })).executes(ctx -> execute((CommandSourceStack)ctx.getSource(), 1)))
               .then(
                  Commands.argument("page", IntegerArgumentType.integer(1))
                     .executes(ctx -> execute((CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))
               )
         );
      }
   }

   private static int execute(CommandSourceStack source, int page) {
      if (!EconomyManager.getInstance().isEnabled()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.eco.disabled"));
         return 0;
      } else {
         if (System.currentTimeMillis() - cacheAge > 60000L || cachedTop.isEmpty()) {
            refreshCacheAsync(source.getServer());
         }

         List<BaltopCommand.BaltopEntry> top = cachedTop;
         if (top.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.baltop.empty"), false);
            return 1;
         } else {
            int totalPages = (int)Math.ceil((double)top.size() / 10.0);
            int clampedPage = Math.max(1, Math.min(page, totalPages));
            int start = (clampedPage - 1) * 10;
            int end = Math.min(start + 10, top.size());
            String currency = EconomyManager.getInstance().getCurrencySymbol();
            long ageSeconds = (System.currentTimeMillis() - cacheAge) / 1000L;
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.baltop.header", clampedPage, totalPages, ageSeconds), false);

            for (int i = start; i < end; i++) {
               BaltopCommand.BaltopEntry entry = top.get(i);
               int rank = i + 1;
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.baltop.entry", rank, entry.name(), entry.balance(), currency), false);
            }

            BigDecimal total = cachedTotal;
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.baltop.total", total, currency), false);
            if (cacheBuilding.get()) {
               source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.baltop.refreshing"), false);
            }

            return 1;
         }
      }
   }

   public static CompletableFuture<Void> refreshCacheAsync(MinecraftServer server) {
      return !cacheBuilding.compareAndSet(false, true) ? CompletableFuture.completedFuture(null) : CompletableFuture.runAsync(() -> {
         try {
            Map<UUID, BigDecimal> all = EconomyManager.getInstance().getAllBalances();
            List<BaltopCommand.BaltopEntry> entries = new CopyOnWriteArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            for (Entry<UUID, BigDecimal> e : all.entrySet()) {
               if (!PermissionAPI.hasPermission(e.getKey(), "neoessentials.economy.baltop.exempt")) {
                  String displayName = e.getKey().toString();

                  try {
                     Optional<GameProfile> profile = server.getProfileCache().get(e.getKey());
                     if (profile.isPresent() && profile.get().getName() != null) {
                        displayName = profile.get().getName();
                     }
                  } catch (Exception var11) {
                  }

                  entries.add(new BaltopCommand.BaltopEntry(e.getKey(), displayName, e.getValue()));
                  total = total.add(e.getValue());
               }
            }

            entries.sort((a, b) -> b.balance().compareTo(a.balance()));
            cachedTop = Collections.unmodifiableList(entries);
            cachedTotal = total;
            cacheAge = System.currentTimeMillis();
         } finally {
            cacheBuilding.set(false);
         }
      });
   }

   public static void invalidateCache() {
      cacheAge = 0L;
   }

   private static record BaltopEntry(UUID uuid, String name, BigDecimal balance) {
   }
}
