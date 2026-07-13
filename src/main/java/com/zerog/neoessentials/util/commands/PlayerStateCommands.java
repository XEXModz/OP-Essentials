package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.economy.worth.WorthManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerStateCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStateCommands.class);
   private static final Map<UUID, Boolean> godMode = new HashMap<>();
   private static final Map<UUID, Long> sessionStart = new HashMap<>();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      registerFly(dispatcher);
      // /god intentionally not registered: FTB Essentials provides /god, and the
      // two commands kept separate god states (players got stuck invincible).
      registerHeal(dispatcher);
      registerFeed(dispatcher);
      registerSpeed(dispatcher);
      registerExt(dispatcher);
      registerBurn(dispatcher);
      registerGive(dispatcher);
      registerMore(dispatcher);
      registerHat(dispatcher);
      registerExp(dispatcher);
      registerSudo(dispatcher);
      registerPlaytime(dispatcher);
   }

   private static void registerFly(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fly")
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.fly");
                        }))
                     .executes(ctx -> executeFly(ctx, null, null)))
                  .then(
                     ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                 .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                                 .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.fly.others")))
                              .executes(ctx -> executeFly(ctx, StringArgumentType.getString(ctx, "playername"), null)))
                           .then(Commands.literal("on").executes(ctx -> executeFly(ctx, StringArgumentType.getString(ctx, "playername"), true))))
                        .then(Commands.literal("off").executes(ctx -> executeFly(ctx, StringArgumentType.getString(ctx, "playername"), false)))
                  ))
               .then(Commands.literal("on").executes(ctx -> executeFly(ctx, null, true))))
            .then(Commands.literal("off").executes(ctx -> executeFly(ctx, null, false)))
      );
   }

   private static int executeFly(CommandContext<CommandSourceStack> ctx, String targetName, Boolean enable) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         boolean newState = enable != null ? enable : !target.getAbilities().mayfly;
         target.getAbilities().mayfly = newState;
         if (!newState) {
            target.getAbilities().flying = false;
         }

         target.onUpdateAbilities();
         target.fallDistance = 0.0F;
         String state = newState ? "§aenabled" : "§cdisabled";
         if (isOtherTarget(src, target)) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.fly.other", target.getName().getString(), state), true);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.fly.self", state));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.fly.self", state), false);
         }

         LOGGER.info("{} set fly={} for {}", new Object[]{senderName(src), newState, target.getName().getString()});
         return 1;
      }
   }

   public static boolean isGodMode(UUID uuid) {
      return godMode.getOrDefault(uuid, false);
   }

   public static void onPlayerQuit(UUID uuid) {
      godMode.remove(uuid);
      sessionStart.remove(uuid);
   }

   public static void onPlayerJoin(UUID uuid) {
      sessionStart.put(uuid, System.currentTimeMillis());
   }

   private static void registerHeal(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("heal").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.heal");
            })).executes(ctx -> executeHeal(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.heal.others")))
                  .executes(ctx -> executeHeal(ctx, StringArgumentType.getString(ctx, "playername")))
            )
      );
   }

   private static int executeHeal(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else if (target.isDeadOrDying()) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.heal.dead", target.getName().getString()));
         return 0;
      } else {
         target.setHealth(target.getMaxHealth());
         target.getFoodData().setFoodLevel(20);
         target.getFoodData().setSaturation(20.0F);
         target.removeAllEffects();
         if (isOtherTarget(src, target)) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.heal.other", target.getName().getString()), true);
            target.sendSystemMessage(MessageUtil.success("commands.neoessentials.heal.self"));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.heal.self"), false);
         }

         return 1;
      }
   }

   private static void registerFeed(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("feed").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.feed");
            })).executes(ctx -> executeFeed(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.feed.others")))
                  .executes(ctx -> executeFeed(ctx, StringArgumentType.getString(ctx, "playername")))
            )
      );
   }

   private static int executeFeed(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         target.getFoodData().setFoodLevel(20);
         target.getFoodData().setSaturation(20.0F);
         if (isOtherTarget(src, target)) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.feed.other", target.getName().getString()), true);
            target.sendSystemMessage(MessageUtil.success("commands.neoessentials.feed.self"));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.feed.self"), false);
         }

         return 1;
      }
   }

   private static void registerSpeed(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("speed").requires(src -> {
                     ServerPlayer p = src.getPlayer();
                     return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.speed");
                  }))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 10.0F))
                           .executes(ctx -> executeSpeed(ctx, null, FloatArgumentType.getFloat(ctx, "speed"), null)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                 .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                                 .requires(
                                    src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.speed.others")
                                 ))
                              .executes(
                                 ctx -> executeSpeed(ctx, null, FloatArgumentType.getFloat(ctx, "speed"), StringArgumentType.getString(ctx, "playername"))
                              )
                        )
                  ))
               .then(
                  Commands.literal("walk")
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 10.0F))
                              .executes(ctx -> executeSpeed(ctx, false, FloatArgumentType.getFloat(ctx, "speed"), null)))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                    .suggests(
                                       (ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b)
                                    )
                                    .requires(
                                       src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.speed.others")
                                    ))
                                 .executes(
                                    ctx -> executeSpeed(ctx, false, FloatArgumentType.getFloat(ctx, "speed"), StringArgumentType.getString(ctx, "playername"))
                                 )
                           )
                     )
               ))
            .then(
               Commands.literal("fly")
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 10.0F))
                           .executes(ctx -> executeSpeed(ctx, true, FloatArgumentType.getFloat(ctx, "speed"), null)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                 .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                                 .requires(
                                    src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.speed.others")
                                 ))
                              .executes(
                                 ctx -> executeSpeed(ctx, true, FloatArgumentType.getFloat(ctx, "speed"), StringArgumentType.getString(ctx, "playername"))
                              )
                        )
                  )
            )
      );
   }

   private static int executeSpeed(CommandContext<CommandSourceStack> ctx, Boolean fly, float speed, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         boolean isFly = fly != null ? fly : target.getAbilities().flying;
         float mcSpeed = Math.min(speed / 10.0F, 1.0F);
         if (isFly) {
            AttributeInstance attr = target.getAttribute(Attributes.FLYING_SPEED);
            if (attr != null) {
               attr.setBaseValue((double)mcSpeed);
            }

            target.onUpdateAbilities();
         } else {
            AttributeInstance attr = target.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
               attr.setBaseValue((double)mcSpeed);
            }
         }

         String type = isFly ? "fly" : "walk";
         if (isOtherTarget(src, target)) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.speed.other", target.getName().getString(), type, speed), true);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.speed.self", type, speed));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.speed.self", type, speed), false);
         }

         return 1;
      }
   }

   private static void registerExt(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ext").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.ext");
            })).executes(ctx -> executeExt(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ext.others")))
                  .executes(ctx -> executeExt(ctx, StringArgumentType.getString(ctx, "playername")))
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("extinguish").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.ext");
            })).executes(ctx -> executeExt(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.ext.others")))
                  .executes(ctx -> executeExt(ctx, StringArgumentType.getString(ctx, "playername")))
            )
      );
   }

   private static int executeExt(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         target.clearFire();
         if (isOtherTarget(src, target)) {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ext.other", target.getName().getString()), true);
            target.sendSystemMessage(MessageUtil.success("commands.neoessentials.ext.self"));
         } else {
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.ext.self"), false);
         }

         return 1;
      }
   }

   private static void registerBurn(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("burn").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.burn");
            }))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .executes(ctx -> executeBurn(ctx, StringArgumentType.getString(ctx, "playername"), 10)))
                  .then(
                     Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> executeBurn(ctx, StringArgumentType.getString(ctx, "playername"), IntegerArgumentType.getInteger(ctx, "seconds")))
                  )
            )
      );
   }

   private static int executeBurn(CommandContext<CommandSourceStack> ctx, String targetName, int seconds) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         target.setRemainingFireTicks(seconds * 20);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.burn.success", target.getName().getString(), seconds), true);
         return 1;
      }
   }

   private static void registerGive(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("give").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.give");
            }))
            .then(
               Commands.argument("playername", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word())
                           .executes(ctx -> executeGive(ctx, StringArgumentType.getString(ctx, "playername"), StringArgumentType.getString(ctx, "item"), 1)))
                        .then(
                           Commands.argument("amount", IntegerArgumentType.integer(1, 3456))
                              .executes(
                                 ctx -> executeGive(
                                       ctx,
                                       StringArgumentType.getString(ctx, "playername"),
                                       StringArgumentType.getString(ctx, "item"),
                                       IntegerArgumentType.getInteger(ctx, "amount")
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int executeGive(CommandContext<CommandSourceStack> ctx, String targetName, String itemId, int amount) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         ItemStack stack = WorthManager.resolveItem(itemId);
         if (stack == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.give.unknown_item", itemId));
            return 0;
         } else {
            int remaining = amount;
            int maxStack = stack.getMaxStackSize();
            Inventory inv = target.getInventory();

            while (remaining > 0) {
               int give = Math.min(remaining, maxStack);
               ItemStack toGive = stack.copyWithCount(give);
               if (!inv.add(toGive)) {
                  target.drop(toGive, false);
               }

               remaining -= give;
            }

            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.give.success", amount, itemId, target.getName().getString()), true);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.give.received", amount, itemId));
            return 1;
         }
      }
   }

   private static void registerMore(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("more").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.more");
            })).executes(ctx -> executeMore(ctx, 0)))
            .then(
               Commands.argument("amount", IntegerArgumentType.integer(1, 3456))
                  .executes(ctx -> executeMore(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
            )
      );
   }

   private static int executeMore(CommandContext<CommandSourceStack> ctx, int amount) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
         return 0;
      } else {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.more.no_item"));
            return 0;
         } else {
            int newCount = amount > 0 ? amount : held.getMaxStackSize();
            held.setCount(newCount);
            src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.more.success", newCount, WorthManager.getItemId(held)), false);
            return 1;
         }
      }
   }

   private static void registerHat(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("hat").requires(src -> {
         ServerPlayer p = src.getPlayer();
         return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.hat");
      })).executes(ctx -> {
         CommandSourceStack src = (CommandSourceStack)ctx.getSource();
         ServerPlayer player = src.getPlayer();
         if (player == null) {
            src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
            return 0;
         } else {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
               src.sendFailure(MessageUtil.error("commands.neoessentials.hat.no_item"));
               return 0;
            } else {
               ItemStack current = (ItemStack)player.getInventory().armor.get(3);
               player.getInventory().armor.set(3, held.copy());
               player.setItemInHand(InteractionHand.MAIN_HAND, current);
               src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.hat.success"), false);
               return 1;
            }
         }
      }));
   }

   private static void registerExp(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("exp")
                        .requires(src -> {
                           ServerPlayer p = src.getPlayer();
                           return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.exp");
                        }))
                     .executes(ctx -> executeExpShow(ctx, null)))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("show").executes(ctx -> executeExpShow(ctx, null)))
                        .then(
                           Commands.argument("playername", StringArgumentType.word())
                              .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                              .executes(ctx -> executeExpShow(ctx, StringArgumentType.getString(ctx, "playername")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("set")
                        .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.set")))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(0))
                              .executes(ctx -> executeExpSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"), null)))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                    .suggests(
                                       (ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b)
                                    )
                                    .requires(
                                       src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.set.others")
                                    ))
                                 .executes(
                                    ctx -> executeExpSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"), StringArgumentType.getString(ctx, "playername"))
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("give")
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.give")))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("amount", IntegerArgumentType.integer(1))
                           .executes(ctx -> executeExpGive(ctx, IntegerArgumentType.getInteger(ctx, "amount"), null)))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                                 .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                                 .requires(
                                    src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.give.others")
                                 ))
                              .executes(
                                 ctx -> executeExpGive(ctx, IntegerArgumentType.getInteger(ctx, "amount"), StringArgumentType.getString(ctx, "playername"))
                              )
                        )
                  )
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("xp").requires(src -> {
                  ServerPlayer p = src.getPlayer();
                  return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.exp");
               })).executes(ctx -> executeExpShow(ctx, null)))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("set")
                        .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.set")))
                     .then(
                        Commands.argument("amount", IntegerArgumentType.integer(0))
                           .executes(ctx -> executeExpSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"), null))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("give")
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.exp.give")))
                  .then(
                     Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeExpGive(ctx, IntegerArgumentType.getInteger(ctx, "amount"), null))
                  )
            )
      );
   }

   private static int executeExpShow(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         int totalXp = getExperienceTotal(target);
         int level = target.experienceLevel;
         src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.exp.show", target.getName().getString(), level, totalXp), false);
         return 1;
      }
   }

   private static int executeExpSet(CommandContext<CommandSourceStack> ctx, int amount, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         target.setExperienceLevels(0);
         target.setExperiencePoints(0);
         target.giveExperiencePoints(amount);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.exp.set", target.getName().getString(), amount), true);
         return 1;
      }
   }

   private static int executeExpGive(CommandContext<CommandSourceStack> ctx, int amount, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         target.giveExperiencePoints(amount);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.exp.give", target.getName().getString(), amount), true);
         return 1;
      }
   }

   private static int getExperienceTotal(ServerPlayer p) {
      int level = p.experienceLevel;
      float progress = p.experienceProgress;
      int xpToNextLevel = p.getXpNeededForNextLevel();
      return (int)((float)getXpForLevel(level) + progress * (float)xpToNextLevel);
   }

   private static int getXpForLevel(int level) {
      if (level <= 16) {
         return level * level + 6 * level;
      } else {
         return level <= 31
            ? (int)(2.5 * (double)level * (double)level - 40.5 * (double)level + 360.0)
            : (int)(4.5 * (double)level * (double)level - 162.5 * (double)level + 2220.0);
      }
   }

   private static void registerSudo(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("sudo").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.sudo");
            }))
            .then(
               Commands.argument("playername", StringArgumentType.word())
                  .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                  .then(
                     Commands.argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> executeSudo(ctx, StringArgumentType.getString(ctx, "playername"), StringArgumentType.getString(ctx, "command")))
                  )
            )
      );
   }

   private static int executeSudo(CommandContext<CommandSourceStack> ctx, String targetName, String command) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(targetName);
      if (target == null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.general.player_not_found", targetName));
         return 0;
      } else if (PermissionAPI.hasPermission(target.getUUID(), "neoessentials.sudo.exempt") && src.getPlayer() != null) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.sudo.exempt", targetName));
         return 0;
      } else if (src.getPlayer() != null && src.getPlayer().getUUID().equals(target.getUUID())) {
         src.sendFailure(MessageUtil.error("commands.neoessentials.sudo.self"));
         return 0;
      } else {
         String cmd = command.startsWith("/") ? command.substring(1) : command;
         src.getServer().getCommands().performPrefixedCommand(target.createCommandSourceStack(), cmd);
         src.sendSuccess(() -> MessageUtil.success("commands.neoessentials.sudo.success", targetName, cmd), true);
         LOGGER.info("{} sudoed {} to run: {}", new Object[]{senderName(src), targetName, cmd});
         return 1;
      }
   }

   private static void registerPlaytime(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("playtime").requires(src -> {
               ServerPlayer p = src.getPlayer();
               return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.playtime");
            })).executes(ctx -> executePlaytime(ctx, null)))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("playername", StringArgumentType.word())
                     .suggests((ctx, b) -> SharedSuggestionProvider.suggest(((CommandSourceStack)ctx.getSource()).getServer().getPlayerNames(), b))
                     .requires(src -> src.getPlayer() == null || PermissionAPI.hasPermission(src.getPlayer().getUUID(), "neoessentials.playtime.others")))
                  .executes(ctx -> executePlaytime(ctx, StringArgumentType.getString(ctx, "playername")))
            )
      );
   }

   private static int executePlaytime(CommandContext<CommandSourceStack> ctx, String targetName) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      ServerPlayer target = resolveTarget(src, targetName);
      if (target == null) {
         return 0;
      } else {
         long sessionMs = 0L;
         Long start = sessionStart.get(target.getUUID());
         if (start != null) {
            sessionMs = System.currentTimeMillis() - start;
         }

         int ticksPlayed = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
         long totalMs = (long)ticksPlayed * 50L + sessionMs;
         String formatted = formatDuration(totalMs);
         src.sendSuccess(() -> MessageUtil.info("commands.neoessentials.playtime.result", target.getName().getString(), formatted), false);
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

   private static boolean isOtherTarget(CommandSourceStack src, ServerPlayer target) {
      return src.getPlayer() == null || !src.getPlayer().getUUID().equals(target.getUUID());
   }

   private static String senderName(CommandSourceStack src) {
      return src.getPlayer() != null ? src.getPlayer().getName().getString() : "Console";
   }

   private static String formatDuration(long ms) {
      long s = ms / 1000L;
      long m = s / 60L;
      long h = m / 60L;
      long days = h / 24L;
      if (days > 0L) {
         return days + "d " + h % 24L + "h " + m % 60L + "m";
      } else if (h > 0L) {
         return h + "h " + m % 60L + "m " + s % 60L + "s";
      } else {
         return m > 0L ? m + "m " + s % 60L + "s" : s + "s";
      }
   }
}
