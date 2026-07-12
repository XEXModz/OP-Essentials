package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FunCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(FunCommands.class);
   private static final Random RANDOM = new Random();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerFirework(dispatcher);
      registerNuke(dispatcher);
      registerAntioch(dispatcher);
      registerKittyCannon(dispatcher);
      registerBeezooka(dispatcher);
      registerItemDb(dispatcher);
      registerPotion(dispatcher);
      registerInfo(dispatcher);
      registerRest(dispatcher);
      registerBackup(dispatcher);
   }

   private static void registerFirework(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "firework"
                        )
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.firework");
                        }))
                     .then(Commands.literal("clear").executes(ctx -> executeFireworkClear(ctx))))
                  .then(
                     Commands.literal("power")
                        .then(
                           Commands.argument("level", IntegerArgumentType.integer(0, 127))
                              .executes(ctx -> executeFireworkPower(ctx, IntegerArgumentType.getInteger(ctx, "level")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fire").requires(src -> {
                        ServerPlayer p = src.getPlayer();
                        return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.firework.fire");
                     })).executes(ctx -> executeFireworkFire(ctx, 1)))
                     .then(
                        Commands.argument("amount", IntegerArgumentType.integer(1, 50))
                           .executes(ctx -> executeFireworkFire(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                     )
               ))
            .then(
               Commands.literal("color")
                  .then(
                     Commands.argument("options", StringArgumentType.greedyString())
                        .suggests(
                           (ctx, b) -> SharedSuggestionProvider.suggest(
                                 Arrays.asList("FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF", "FFFFFF", "000000", "FF8800", "8800FF"), b
                              )
                        )
                        .executes(ctx -> executeFireworkColor(ctx, StringArgumentType.getString(ctx, "options")))
                  )
            )
      );
   }

   private static int executeFireworkClear(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!(held.getItem() instanceof FireworkRocketItem)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.firework.not_firework"));
            return 0;
         } else {
            Fireworks current = (Fireworks)held.get(DataComponents.FIREWORKS);
            int power = current != null ? current.flightDuration() : 1;
            held.set(DataComponents.FIREWORKS, new Fireworks(power, List.of()));
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.firework.cleared"), false);
            return 1;
         }
      }
   }

   private static int executeFireworkPower(CommandContext<CommandSourceStack> ctx, int level) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!(held.getItem() instanceof FireworkRocketItem)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.firework.not_firework"));
            return 0;
         } else {
            Fireworks current = (Fireworks)held.get(DataComponents.FIREWORKS);
            List<FireworkExplosion> effects = current != null ? current.explosions() : List.of();
            held.set(DataComponents.FIREWORKS, new Fireworks((byte)Math.min(level, 127), effects));
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.firework.power_set", level), false);
            return 1;
         }
      }
   }

   private static int executeFireworkFire(CommandContext<CommandSourceStack> ctx, int amount) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!(held.getItem() instanceof FireworkRocketItem)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.firework.not_firework"));
            return 0;
         } else {
            ServerLevel level = player.serverLevel();

            for (int i = 0; i < amount; i++) {
               Vec3 look = player.getLookAngle().normalize();
               FireworkRocketEntity fw = new FireworkRocketEntity(level, held.copy(), player.getX(), player.getEyeY(), player.getZ(), true);
               fw.setDeltaMovement(look.x * 0.5, look.y * 0.5, look.z * 0.5);
               level.addFreshEntity(fw);
            }

            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.firework.fired", amount), false);
            LOGGER.info("{} fired {}x firework", player.getName().getString(), amount);
            return 1;
         }
      }
   }

   private static int executeFireworkColor(CommandContext<CommandSourceStack> ctx, String options) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!(held.getItem() instanceof FireworkRocketItem)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.firework.not_firework"));
            return 0;
         } else {
            List<Integer> colors = parseColorList(options, "");
            List<Integer> fadeColors = parseColorList(options, "fade:");
            Shape shape = parseFireworkShape(options);
            boolean trail = options.contains("trail");
            boolean twinkle = options.contains("twinkle") || options.contains("flicker");
            if (colors.isEmpty()) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.firework.no_color"));
               return 0;
            } else {
               IntArrayList intColors = new IntArrayList(colors.stream().mapToInt(Integer::intValue).toArray());
               IntArrayList intFades = new IntArrayList(fadeColors.stream().mapToInt(Integer::intValue).toArray());
               FireworkExplosion explosion = new FireworkExplosion(shape, intColors, intFades, trail, twinkle);
               Fireworks current = (Fireworks)held.get(DataComponents.FIREWORKS);
               int power = current != null ? current.flightDuration() : 1;
               List<FireworkExplosion> existingEffects = current != null ? current.explosions() : List.of();
               ArrayList<FireworkExplosion> newEffects = new ArrayList<>(existingEffects);
               newEffects.add(explosion);
               held.set(DataComponents.FIREWORKS, new Fireworks(power, newEffects));
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.firework.effect_added"), false);
               return 1;
            }
         }
      }
   }

   private static List<Integer> parseColorList(String input, String prefix) {
      String lc = input.toLowerCase();
      int idx = prefix.isEmpty() ? 0 : lc.indexOf(prefix);
      if (idx < 0) {
         return List.of();
      } else {
         String segment = input.substring(idx + prefix.length()).trim();
         String colorPart = segment.split("\\s+")[0].replace(",", " ").trim();
         List<Integer> result = new ArrayList<>();

         for (String hex : colorPart.split("[,\\s]+")) {
            hex = hex.trim().replace("#", "");
            if (!hex.isEmpty()) {
               try {
                  result.add((int)Long.parseLong(hex, 16));
               } catch (NumberFormatException var12) {
               }
            }
         }

         return result;
      }
   }

   private static Shape parseFireworkShape(String options) {
      String lc = options.toLowerCase();
      if (lc.contains("star")) {
         return Shape.STAR;
      } else if (lc.contains("large")) {
         return Shape.LARGE_BALL;
      } else if (lc.contains("creeper")) {
         return Shape.CREEPER;
      } else {
         return lc.contains("burst") ? Shape.BURST : Shape.SMALL_BALL;
      }
   }

   private static void registerNuke(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("nuke").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.nuke");
            })).executes(ctx -> executeNuke(ctx, null)))
            .then(
               Commands.argument("target", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .executes(ctx -> executeNuke(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeNuke(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      List<ServerPlayer> targets;
      if (targetName != null) {
         ServerPlayer p = src.getServer().getPlayerList().getPlayerByName(targetName);
         if (p == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
            return 0;
         }

         targets = List.of(p);
      } else {
         targets = src.getServer().getPlayerList().getPlayers();
      }

      int nuked = 0;

      for (ServerPlayer target : targets) {
         ServerLevel level = target.serverLevel();
         target.sendSystemMessage(Component.literal("§c☢ INCOMING NUKE! ☢"));
         int bx = target.getBlockX();
         int bz = target.getBlockZ();
         int topY = level.getMaxBuildHeight();

         for (int x = -10; x <= 10; x += 5) {
            for (int z = -10; z <= 10; z += 5) {
               PrimedTnt tnt = (PrimedTnt)EntityType.TNT.create(level);
               if (tnt != null) {
                  tnt.moveTo((double)(bx + x), (double)topY, (double)(bz + z));
                  tnt.setFuse(80);
                  level.addFreshEntity(tnt);
               }
            }
         }

         nuked++;
      }

      int fn = nuked;
      src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.nuke.success", fn), true);
      LOGGER.info("{} nuked {} player(s)", src.getTextName(), nuked);
      return 1;
   }

   private static void registerItemDb(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("itemdb").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.itemdb");
            })).executes(ctx -> executeItemDb(ctx, null)))
            .then(
               Commands.argument("item", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), b))
                  .executes(ctx -> executeItemDb(ctx, StringArgumentType.getString(ctx, "item")))
            )
      );
   }

   private static int executeItemDb(CommandContext<CommandSourceStack> ctx, String itemArg) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ItemStack stack;
      if (itemArg == null) {
         ServerPlayer player = src.getPlayer();
         if (player == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         }

         stack = player.getMainHandItem();
         if (stack.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.itemdb.nothing_held"));
            return 0;
         }
      } else {
         String id = itemArg.contains(":") ? itemArg : "minecraft:" + itemArg;
         ResourceLocation loc = ResourceLocation.tryParse(id);
         if (loc == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.itemdb.unknown", itemArg));
            return 0;
         }

         Item item = (Item)BuiltInRegistries.ITEM.get(loc);
         if (item == Items.AIR && !itemArg.equalsIgnoreCase("air")) {
            item = BuiltInRegistries.ITEM
               .entrySet()
               .stream()
               .filter(e -> ((ResourceKey)e.getKey()).location().getPath().equals(itemArg.toLowerCase()))
               .map(Entry::getValue)
               .findFirst()
               .orElse(null);
         }

         if (item == null || item == Items.AIR && !itemArg.equalsIgnoreCase("air")) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.itemdb.unknown", itemArg));
            return 0;
         }

         stack = new ItemStack(item);
      }

      ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
      String registryId = itemKey != null ? itemKey.toString() : "unknown";
      String displayName = stack.getItem().getDescription().getString();
      int maxStack = stack.getMaxStackSize();
      Integer maxDamageComp = (Integer)stack.get(DataComponents.MAX_DAMAGE);
      int maxDamage = maxDamageComp != null ? maxDamageComp : 0;
      StringBuilder sb = new StringBuilder();
      sb.append("§e--- §fItem Info: §b").append(displayName).append(" §e---\n");
      sb.append("§7ID: §f").append(registryId).append("\n");
      sb.append("§7Max Stack: §f").append(maxStack);
      if (maxDamage > 0) {
         sb.append("  §7Max Durability: §f").append(maxDamage);
      }

      Component customName = (Component)stack.get(DataComponents.CUSTOM_NAME);
      if (customName != null) {
         sb.append("\n§7Custom Name: §f").append(customName.getString());
      }

      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return 1;
   }

   private static void registerPotion(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("potion").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.potion");
            })).then(Commands.literal("clear").executes(ctx -> executePotionClear(ctx))))
            .then(
               Commands.literal("add")
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("effect", StringArgumentType.word())
                           .suggests(
                              (ctx, b) -> SharedSuggestionProvider.suggest(BuiltInRegistries.MOB_EFFECT.keySet().stream().map(ResourceLocation::getPath), b)
                           )
                           .executes(ctx -> executePotionAdd(ctx, StringArgumentType.getString(ctx, "effect"), 30, 0)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                 .executes(
                                    ctx -> executePotionAdd(
                                          ctx, StringArgumentType.getString(ctx, "effect"), IntegerArgumentType.getInteger(ctx, "duration"), 0
                                       )
                                 ))
                              .then(
                                 Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                    .executes(
                                       ctx -> executePotionAdd(
                                             ctx,
                                             StringArgumentType.getString(ctx, "effect"),
                                             IntegerArgumentType.getInteger(ctx, "duration"),
                                             IntegerArgumentType.getInteger(ctx, "amplifier")
                                          )
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int executePotionClear(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!isPotionItem(held)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.potion.not_potion"));
            return 0;
         } else {
            held.remove(DataComponents.POTION_CONTENTS);
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.potion.cleared"), false);
            return 1;
         }
      }
   }

   private static int executePotionAdd(CommandContext<CommandSourceStack> ctx, String effectId, int durationSecs, int amplifier) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (!isPotionItem(held)) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.potion.not_potion"));
            return 0;
         } else {
            String id = effectId.contains(":") ? effectId : "minecraft:" + effectId;
            ResourceLocation loc = ResourceLocation.tryParse(id);
            MobEffect effectHolder = loc != null ? (MobEffect)BuiltInRegistries.MOB_EFFECT.get(loc) : null;
            if (effectHolder == null) {
               effectHolder = BuiltInRegistries.MOB_EFFECT
                  .entrySet()
                  .stream()
                  .filter(e -> ((ResourceKey)e.getKey()).location().getPath().equals(effectId.toLowerCase()))
                  .map(Entry::getValue)
                  .findFirst()
                  .orElse(null);
            }

            if (effectHolder == null) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.effect.unknown", effectId));
               return 0;
            } else {
               MobEffectInstance instance = new MobEffectInstance(Holder.direct(effectHolder), durationSecs * 20, amplifier, false, true);
               PotionContents existing = (PotionContents)held.get(DataComponents.POTION_CONTENTS);
               List<MobEffectInstance> custom;
               if (existing != null) {
                  custom = new ArrayList<>(existing.customEffects());
               } else {
                  custom = new ArrayList<>();
               }

               custom.add(instance);
               PotionContents newContents = existing != null
                  ? new PotionContents(existing.potion(), existing.customColor(), custom)
                  : new PotionContents(Optional.empty(), Optional.empty(), custom);
               held.set(DataComponents.POTION_CONTENTS, newContents);
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.potion.added", effectId, amplifier, durationSecs), false);
               return 1;
            }
         }
      }
   }

   private static boolean isPotionItem(ItemStack stack) {
      return stack.getItem() == Items.POTION
         || stack.getItem() == Items.SPLASH_POTION
         || stack.getItem() == Items.LINGERING_POTION
         || stack.getItem() == Items.TIPPED_ARROW;
   }

   private static void registerInfo(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("info").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.info");
      })).executes(ctx -> executeInfo(ctx)));
   }

   private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      ConfigManager cfg = ConfigManager.getInstance();
      String motd = cfg.getMotd();
      String rules = cfg.getRules();
      if (motd == null) {
         motd = "";
      }

      if (rules == null) {
         rules = "";
      }

      String playerName = player != null ? player.getName().getString() : "Server";
      String motdResolved = motd.replace("{player}", playerName).replace("{name}", playerName);
      String finalRules = rules;
      src.sendSuccess(
         () -> Component.literal("§b============ §fServer Info §b============\n" + motdResolved + "\n§7Type §f/rules §7to view server rules."), false
      );
      if (!finalRules.isEmpty() && !finalRules.equals("No rules set.")) {
         src.sendSuccess(() -> Component.literal("§e--- §fRules §e---\n" + finalRules), false);
      }

      return 1;
   }

   private static BlockPos getHighestBlock(ServerLevel level, int x, int z) {
      return BlockPos.containing((double)x, (double)level.getMaxBuildHeight(), (double)z);
   }

   private static void registerAntioch(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("antioch").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.antioch");
            })).executes(ctx -> executeAntioch(ctx, false)))
            .then(Commands.argument("flavour", StringArgumentType.greedyString()).executes(ctx -> executeAntioch(ctx, true)))
      );
   }

   private static int executeAntioch(CommandContext<CommandSourceStack> ctx, boolean flavour) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         if (flavour) {
            MinecraftServer server = src.getServer();
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6...lobbest thou thy Holy Hand Grenade of Antioch towards thy foe,"), false);
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6who being naughty in My sight, shall snuff it."), false);
         }

         HitResult hit = player.pick(20.0, 1.0F, false);
         Vec3 pos = hit.getLocation();
         ServerLevel level = player.serverLevel();
         PrimedTnt tnt = (PrimedTnt)EntityType.TNT.create(level);
         if (tnt != null) {
            tnt.moveTo(pos.x, pos.y, pos.z);
            tnt.setFuse(80);
            level.addFreshEntity(tnt);
         }

         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.antioch.success"), false);
         return 1;
      }
   }

   private static void registerKittyCannon(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("kittycannon").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.kittycannon");
      })).executes(ctx -> executeKittyCannon(ctx)));
   }

   private static int executeKittyCannon(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         Cat cat = (Cat)EntityType.CAT.create(level);
         if (cat == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.error"));
            return 0;
         } else {
            List<Reference<CatVariant>> variants = BuiltInRegistries.CAT_VARIANT.holders().toList();
            if (!variants.isEmpty()) {
               cat.setVariant((Holder)variants.get(RANDOM.nextInt(variants.size())));
            }

            cat.setAge(-24000);
            cat.moveTo(player.getX(), player.getEyeY(), player.getZ());
            Vec3 look = player.getLookAngle().normalize().scale(2.0);
            cat.setDeltaMovement(look.x, look.y, look.z);
            level.addFreshEntity(cat);
            level.getServer().tell(new TickTask(level.getServer().getTickCount() + 20, () -> {
               if (cat.isAlive()) {
                  Vec3 loc = cat.position();
                  cat.discard();
                  level.explode(null, loc.x, loc.y, loc.z, 0.0F, ExplosionInteraction.NONE);
               }
            }));
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.kittycannon.fired"), false);
            return 1;
         }
      }
   }

   private static void registerBeezooka(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("beezooka").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.beezooka");
            })).executes(ctx -> executeBeezooka(ctx, 1)))
            .then(
               Commands.argument("amount", IntegerArgumentType.integer(1, 20))
                  .executes(ctx -> executeBeezooka(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
            )
      );
   }

   private static int executeBeezooka(CommandContext<CommandSourceStack> ctx, int amount) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ServerLevel level = player.serverLevel();
         int spawned = 0;

         for (int i = 0; i < amount; i++) {
            Bee bee = (Bee)EntityType.BEE.create(level);
            if (bee != null) {
               bee.moveTo(player.getX(), player.getEyeY(), player.getZ());
               Vec3 look = player.getLookAngle().normalize().scale(1.5 + RANDOM.nextDouble() * 0.5);
               bee.setDeltaMovement(look.x, look.y + 0.1, look.z);
               bee.setRemainingPersistentAngerTime(400 + RANDOM.nextInt(400));
               bee.setTarget(null);
               level.addFreshEntity(bee);
               spawned++;
            }
         }

         int fs = spawned;
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.beezooka.fired", fs), false);
         return 1;
      }
   }

   private static void registerRest(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("rest").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.rest");
            })).executes(ctx -> executeRest(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("target", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.rest.others")))
                  .executes(ctx -> executeRest(ctx, StringArgumentType.getString(ctx, "target")))
            )
      );
   }

   private static int executeRest(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target;
      if (targetName != null) {
         target = src.getServer().getPlayerList().getPlayerByName(targetName);
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
            return 0;
         }
      } else {
         target = src.getPlayer();
         if (target == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         }
      }

      target.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
      String name = target.getName().getString();
      if (targetName != null) {
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rest.other", name), true);
         target.sendSystemMessage(MessageUtil.success("commands.neoessentials.rest.self"));
      } else {
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rest.self"), false);
      }

      return 1;
   }

   private static void registerBackup(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("backup").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.backup");
      })).executes(ctx -> executeBackup(ctx)));
   }

   private static int executeBackup(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      MinecraftServer server = src.getServer();
      src.sendSuccess(() -> Component.literal("§eSaving world data..."), false);
      server.getAllLevels().forEach(level -> level.save(null, true, false));
      String backupCmd = ConfigManager.getInstance().getBackupCommand();
      if (backupCmd != null && !backupCmd.isBlank() && !backupCmd.equalsIgnoreCase("save-all")) {
         src.sendSuccess(() -> Component.literal("§eRunning backup command: §f" + backupCmd), false);

         try {
            new ProcessBuilder(backupCmd.split("\\s+")).inheritIO().start();
         } catch (Exception var5) {
            src.sendFailure(Component.literal("§cBackup command failed: " + var5.getMessage()));
            LOGGER.error("Backup command '{}' failed: {}", new Object[]{backupCmd, var5.getMessage(), var5});
            return 0;
         }
      }

      src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.backup.done"), true);
      LOGGER.info("{} triggered a server backup", src.getTextName());
      return 1;
   }
}
