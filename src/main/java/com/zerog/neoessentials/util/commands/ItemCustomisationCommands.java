package com.zerog.neoessentials.util.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.util.MessageUtil;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemCustomisationCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(ItemCustomisationCommands.class);
   private static final Map<UUID, Boolean> tpToggleState = new ConcurrentHashMap<>();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerMe(dispatcher);
      registerTpToggle(dispatcher);
      registerGc(dispatcher);
      registerLightning(dispatcher);
      registerSkull(dispatcher);
      registerItemName(dispatcher);
      registerItemLore(dispatcher);
      registerRemove(dispatcher);
      registerLoom(dispatcher);
      registerCartography(dispatcher);
   }

   private static void registerMe(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("me").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.me");
      })).then(Commands.argument("action", StringArgumentType.greedyString()).executes(ctx -> {
         CommandSourceStack src = (CommandSourceStack)ctx.getSource();
         String action = StringArgumentType.getString(ctx, "action");
         String name = src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console";
         Component msg = MessageUtil.coloredText("§5* §d" + name + " §f" + action);
         src.getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
         src.getServer().sendSystemMessage(msg);
         return 1;
      })));
   }

   private static void registerTpToggle(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "tptoggle"
                        )
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.tptoggle");
                        }))
                     .executes(ctx -> executeTpToggle(ctx, null, null)))
                  .then(Commands.literal("on").executes(ctx -> executeTpToggle(ctx, null, true))))
               .then(Commands.literal("off").executes(ctx -> executeTpToggle(ctx, null, false))))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                           .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.tptoggle.others")))
                        .executes(ctx -> executeTpToggle(ctx, StringArgumentType.getString(ctx, "target"), null)))
                     .then(Commands.literal("on").executes(ctx -> executeTpToggle(ctx, StringArgumentType.getString(ctx, "target"), true))))
                  .then(Commands.literal("off").executes(ctx -> executeTpToggle(ctx, StringArgumentType.getString(ctx, "target"), false)))
            )
      );
   }

   private static int executeTpToggle(CommandContext<CommandSourceStack> ctx, String targetName, Boolean enable) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         boolean cur = tpToggleState.getOrDefault(target.getUUID(), true);
         boolean newState = enable != null ? enable : !cur;
         tpToggleState.put(target.getUUID(), newState);
         String label = newState ? "§aenabled" : "§cdisabled";
         boolean isOther = src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
         if (isOther) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tptoggle.other", target.getName().getString(), label), false);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.tptoggle.self", label));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.tptoggle.self", label), false);
         }

         return 1;
      }
   }

   public static boolean isTpToggleAllowed(UUID uuid) {
      return tpToggleState.getOrDefault(uuid, true);
   }

   private static void registerGc(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("gc").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.gc");
      })).executes(ctx -> executeGc(ctx)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("mem").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.gc");
      })).executes(ctx -> executeGc(ctx)));
   }

   private static int executeGc(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      Runtime rt = Runtime.getRuntime();
      long maxMb = rt.maxMemory() / 1024L / 1024L;
      long totalMb = rt.totalMemory() / 1024L / 1024L;
      long freeMb = rt.freeMemory() / 1024L / 1024L;
      long usedMb = totalMb - freeMb;
      long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
      String uptime = formatUptime(uptimeMs);
      double tps = 20.0;

      try {
         MinecraftServer server = src.getServer();
         double avgNs = (double)server.getAverageTickTimeNanos();
         if (avgNs > 0.0) {
            double avgMs = avgNs / 1000000.0;
            tps = Math.min(20.0, 1000.0 / avgMs);
         }
      } catch (Exception var29) {
      }

      String tpsColor = tps >= 18.0 ? "§a" : (tps >= 15.0 ? "§e" : "§c");
      int loaded = 0;

      for (ServerLevel level : src.getServer().getAllLevels()) {
         loaded += level.getChunkSource().getLoadedChunksCount();
      }

      double ftps = (double)Math.round(tps * 100.0) / 100.0;
      int fChunks = loaded;
      src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.gc.info", uptime, tpsColor + ftps, usedMb, totalMb, maxMb, fChunks), false);
      return 1;
   }

   private static String formatUptime(long ms) {
      long s = ms / 1000L;
      long m = s / 60L;
      long h = m / 60L;
      long d = h / 24L;
      if (d > 0L) {
         return d + "d " + h % 24L + "h " + m % 60L + "m";
      } else if (h > 0L) {
         return h + "h " + m % 60L + "m " + s % 60L + "s";
      } else {
         return m > 0L ? m + "m " + s % 60L + "s" : s + "s";
      }
   }

   private static void registerLightning(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("lightning").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.lightning");
            })).executes(ctx -> executeLightning(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.lightning.others")))
                  .executes(ctx -> executeLightning(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("smite").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.lightning");
            })).executes(ctx -> executeLightning(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.lightning.others")))
                  .executes(ctx -> executeLightning(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeLightning(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      if (targetName != null) {
         ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
            return 0;
         }

         strikeLightning(target.serverLevel(), target.getX(), target.getY(), target.getZ());
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.lightning.struck", targetName), true);
      } else {
         ServerPlayer self = src.getPlayer();
         if (self == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         }

         HitResult hit = self.pick(100.0, 1.0F, false);
         Vec3 pos = hit.getLocation();
         strikeLightning(self.serverLevel(), pos.x, pos.y, pos.z);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.lightning.self"), false);
      }

      return 1;
   }

   private static void strikeLightning(ServerLevel level, double x, double y, double z) {
      LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(level);
      if (bolt != null) {
         bolt.moveTo(x, y, z);
         level.addFreshEntity(bolt);
      }
   }

   private static void registerSkull(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("skull").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.skull");
            })).executes(ctx -> executeSkull(ctx, null)))
            .then(
               Commands.argument("player", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> executeSkull(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
   }

   private static int executeSkull(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer self = src.getPlayer();
      if (self == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         String ownerName = targetName != null ? targetName : self.getName().getString();
         ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
         GameProfileCache cache = src.getServer().getProfileCache();
         if (cache != null) {
            cache.get(ownerName).ifPresent(profile -> skull.set(DataComponents.PROFILE, new ResolvableProfile(profile)));
         }

         if (!skull.has(DataComponents.PROFILE)) {
            skull.set(DataComponents.PROFILE, new ResolvableProfile(new GameProfile(UUID.randomUUID(), ownerName)));
         }

         if (!self.getInventory().add(skull)) {
            self.drop(skull, false);
         }

         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.skull.success", ownerName), false);
         return 1;
      }
   }

   private static void registerItemName(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("itemname").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.itemname");
            })).executes(ctx -> executeItemName(ctx, null)))
            .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> executeItemName(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rename").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.itemname");
            })).executes(ctx -> executeItemName(ctx, null)))
            .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> executeItemName(ctx, StringArgumentType.getString(ctx, "name"))))
      );
   }

   private static int executeItemName(CommandContext<CommandSourceStack> ctx, String name) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.itemname.no_item"));
            return 0;
         } else {
            if (name != null && !name.equals("-") && !name.isBlank()) {
               held.set(DataComponents.CUSTOM_NAME, MessageUtil.coloredText(name));
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemname.set", name), false);
            } else {
               held.remove(DataComponents.CUSTOM_NAME);
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemname.cleared"), false);
            }

            return 1;
         }
      }
   }

   private static void registerItemLore(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "itemlore"
                        )
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.itemlore");
                        }))
                     .then(
                        Commands.literal("add")
                           .then(
                              Commands.argument("text", StringArgumentType.greedyString())
                                 .executes(ctx -> executeItemLore(ctx, "add", -1, StringArgumentType.getString(ctx, "text")))
                           )
                     ))
                  .then(
                     Commands.literal("set")
                        .then(
                           Commands.argument("line", IntegerArgumentType.integer(1))
                              .then(
                                 Commands.argument("text", StringArgumentType.greedyString())
                                    .executes(
                                       ctx -> executeItemLore(
                                             ctx, "set", IntegerArgumentType.getInteger(ctx, "line"), StringArgumentType.getString(ctx, "text")
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  Commands.literal("remove")
                     .then(
                        Commands.argument("line", IntegerArgumentType.integer(1))
                           .executes(ctx -> executeItemLore(ctx, "remove", IntegerArgumentType.getInteger(ctx, "line"), null))
                     )
               ))
            .then(Commands.literal("clear").executes(ctx -> executeItemLore(ctx, "clear", -1, null)))
      );
   }

   private static int executeItemLore(CommandContext<CommandSourceStack> ctx, String action, int line, String text) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.itemlore.no_item"));
            return 0;
         } else {
            ItemLore existing = (ItemLore)held.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
            List<Component> lore = new ArrayList<>(existing.lines());
            switch (action) {
               case "add":
                  lore.add(MessageUtil.coloredText("§r" + text));
                  src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemlore.added", text), false);
                  break;
               case "set":
                  int idx = line - 1;
                  if (idx < 0 || idx >= lore.size()) {
                     src.sendFailure(MessageUtil.error("commands.neoessentials.itemlore.invalid_line", line));
                     return 0;
                  }

                  lore.set(idx, MessageUtil.coloredText("§r" + text));
                  src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemlore.set", line, text), false);
                  break;
               case "remove":
                  int idx = line - 1;
                  if (idx < 0 || idx >= lore.size()) {
                     src.sendFailure(MessageUtil.error("commands.neoessentials.itemlore.invalid_line", line));
                     return 0;
                  }

                  lore.remove(idx);
                  src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemlore.removed", line), false);
                  break;
               case "clear":
                  lore.clear();
                  src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.itemlore.cleared"), false);
            }

            held.set(DataComponents.LORE, new ItemLore(Collections.unmodifiableList(lore)));
            return 1;
         }
      }
   }

   private static void registerRemove(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("remove").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.remove");
            }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("type", StringArgumentType.word())
                     .suggests(
                        (ctx, b) -> SharedSuggestionProvider.suggest(
                              Arrays.asList(
                                 "all", "items", "drops", "mobs", "animals", "monsters", "arrows", "xp", "paintings", "boats", "minecarts", "tnt", "boats"
                              ),
                              b
                           )
                     )
                     .executes(ctx -> executeRemove(ctx, StringArgumentType.getString(ctx, "type"), 200)))
                  .then(
                     Commands.argument("radius", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> executeRemove(ctx, StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "radius")))
                  )
            )
      );
   }

   private static int executeRemove(CommandContext<CommandSourceStack> ctx, String type, int radius) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         BlockPos pos = player.blockPosition();
         int removed = 0;

         for (Entity entity : level.getEntities(null, player.getBoundingBox().inflate((double)radius))) {
            if (!(entity instanceof ServerPlayer)) {
               String var11 = type.toLowerCase();

               boolean match = switch (var11) {
                  case "all" -> true;
                  case "items", "drops" -> entity instanceof ItemEntity;
                  case "mobs" -> entity instanceof Mob;
                  case "animals" -> entity instanceof Animal;
                  case "monsters" -> entity instanceof Monster;
                  case "arrows" -> entity instanceof Arrow;
                  case "xp" -> entity instanceof ExperienceOrb;
                  case "boats" -> entity instanceof Boat;
                  case "minecarts" -> entity instanceof AbstractMinecart;
                  case "tnt" -> entity instanceof PrimedTnt;
                  case "paintings" -> entity instanceof Painting;
                  default -> false;
               };
               if (match) {
                  entity.discard();
                  removed++;
               }
            }
         }

         int fr = removed;
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.remove.success", fr, type, radius), true);
         LOGGER.info("{} removed {} {} entities within {}r", new Object[]{senderName(src), fr, type, radius});
         return 1;
      }
   }

   private static void registerLoom(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("loom").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.loom");
      })).executes(ctx -> {
         CommandSourceStack src = (CommandSourceStack)ctx.getSource();
         ServerPlayer player = src.getPlayer();
         if (player == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         } else {
            player.openMenu(new MenuProvider() {
               @Nonnull
               public Component getDisplayName() {
                  return Component.literal("Loom");
               }

               @Nonnull
               public AbstractContainerMenu createMenu(int id, @Nonnull Inventory inv, @Nonnull Player p) {
                  return new LoomMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition()));
               }
            });
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.loom.opened"), false);
            return 1;
         }
      }));
   }

   private static void registerCartography(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("cartography").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.cartography");
      })).executes(ctx -> openCartography(ctx)));
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("cartographytable").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.cartography");
      })).executes(ctx -> openCartography(ctx)));
   }

   private static int openCartography(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         player.openMenu(new MenuProvider() {
            @Nonnull
            public Component getDisplayName() {
               return Component.literal("Cartography Table");
            }

            @Nonnull
            public AbstractContainerMenu createMenu(int id, @Nonnull Inventory inv, @Nonnull Player p) {
               return new CartographyTableMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition()));
            }
         });
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.cartography.opened"), false);
         return 1;
      }
   }

   private static ServerPlayer resolveTarget(CommandSourceStack src, String targetName) {
      if (targetName != null) {
         ServerPlayer p = src.getServer().getPlayerList().getPlayerByName(targetName);
         if (p == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         }

         return p;
      } else {
         ServerPlayer self = src.getPlayer();
         if (self == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         }

         return self;
      }
   }

   private static String senderName(CommandSourceStack src) {
      return src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console";
   }
}
