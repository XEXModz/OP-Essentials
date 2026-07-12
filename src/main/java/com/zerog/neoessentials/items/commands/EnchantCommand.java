package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(EnchantCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("enchant")) {
         registerEnchantCommand(dispatcher, "enchant");
         registerEnchantCommand(dispatcher, "ench");
      }
   }

   private static void registerEnchantCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).requires(cs -> {
                     if (cs.hasPermission(2)) {
                        return true;
                     } else {
                        if (cs.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.enchant")) {
                           return true;
                        }

                        return false;
                     }
                  }))
                  .then(
                     ((RequiredArgumentBuilder)Commands.argument("enchantment", ResourceLocationArgument.id())
                           .suggests(
                              (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                    ((CommandSourceStack)ctx.getSource()).getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet(),
                                    builder
                                 )
                           )
                           .then(
                              Commands.argument("level", IntegerArgumentType.integer(1, 32767))
                                 .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.HAND_ONLY))
                           ))
                        .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.HAND_ONLY))
                  ))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("target", EntityArgument.player()).requires(cs -> {
                        if (cs.hasPermission(2)) {
                           return true;
                        } else {
                           if (cs.getEntity() instanceof ServerPlayer player
                              && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.enchant.others")) {
                              return true;
                           }

                           return false;
                        }
                     }))
                     .then(
                        ((RequiredArgumentBuilder)Commands.argument("enchantment", ResourceLocationArgument.id())
                              .suggests(
                                 (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                       ((CommandSourceStack)ctx.getSource()).getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet(),
                                       builder
                                    )
                              )
                              .then(
                                 Commands.argument("level", IntegerArgumentType.integer(1, 32767))
                                    .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.TARGET_HAND))
                              ))
                           .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.TARGET_HAND))
                     )
               ))
            .executes(ctx -> {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.usage"));
               return 0;
            })
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("enchanthand").requires(cs -> {
                  if (cs.hasPermission(2)) {
                     return true;
                  } else {
                     if (cs.getEntity() instanceof ServerPlayer player && PermissionAPI.hasPermission(player.getUUID(), "neoessentials.item.enchant")) {
                        return true;
                     }

                     return false;
                  }
               }))
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("enchantment", ResourceLocationArgument.id())
                        .suggests(
                           (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                 ((CommandSourceStack)ctx.getSource()).getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet(), builder
                              )
                        )
                        .then(
                           Commands.argument("level", IntegerArgumentType.integer(1, 32767))
                              .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.HAND_ONLY))
                        ))
                     .executes(ctx -> executeEnchant(ctx, EnchantCommand.EnchantMode.HAND_ONLY))
               ))
            .executes(ctx -> {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchanthand.usage"));
               return 0;
            })
      );
   }

   private static int executeEnchant(CommandContext<CommandSourceStack> ctx, EnchantCommand.EnchantMode mode) throws CommandSyntaxException {
      String requiredPermission = mode == EnchantCommand.EnchantMode.TARGET_HAND ? "neoessentials.item.enchant.others" : "neoessentials.item.enchant";
      PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), requiredPermission);
      if (!permResult.hasPermission()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
         return 0;
      } else {
         ServerPlayer executor = permResult.getPlayer();
         ServerPlayer targetPlayer;
         if (mode == EnchantCommand.EnchantMode.TARGET_HAND) {
            try {
               Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
               if (targets.isEmpty()) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.no_target"));
                  return 0;
               }

               targetPlayer = targets.iterator().next();
            } catch (Exception var17) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.invalid_target"));
               return 0;
            }
         } else {
            targetPlayer = executor;
         }

         ResourceLocation enchantId = ResourceLocationArgument.getId(ctx, "enchantment");
         int levelTemp = 1;

         try {
            levelTemp = IntegerArgumentType.getInteger(ctx, "level");
         } catch (IllegalArgumentException var16) {
         }

         boolean allowUnsafeEnchants = ConfigManager.isUnsafeEnchantsAllowed()
            || PermissionAPI.hasPermission(executor.getUUID(), "neoessentials.item.enchant.unsafe");
         InputValidator.ValidationResult levelValidation = InputValidator.validateEnchantmentLevel(levelTemp, allowUnsafeEnchants);
         if (!levelValidation.isValid()) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(levelValidation.getErrorMessage()));
            return 0;
         } else {
            int level = levelValidation.getValue(Integer.class);
            Registry<Enchantment> enchantRegistry = targetPlayer.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            if (!enchantRegistry.containsKey(enchantId)) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.unknown", enchantId.toString()));
               return 0;
            } else {
               Enchantment enchantment = (Enchantment)enchantRegistry.get(enchantId);
               if (enchantment == null) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.unknown", enchantId.toString()));
                  return 0;
               } else {
                  ItemStack stack = targetPlayer.getMainHandItem();
                  if (stack.isEmpty()) {
                     ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.no_item"));
                     return 0;
                  } else {
                     boolean canEnchantAny = PermissionAPI.hasPermission(executor.getUUID(), "neoessentials.item.enchant.any");
                     if (!canEnchantAny && !isEnchantmentCompatible(enchantment, stack)) {
                        ((CommandSourceStack)ctx.getSource())
                           .sendFailure(
                              MessageUtil.error("commands.neoessentials.enchant.incompatible", enchantId.toString(), stack.getDisplayName().getString())
                           );
                        return 0;
                     } else {
                        boolean success = applyEnchantment(targetPlayer, stack, enchantment, level);
                        if (!success) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.enchant.failed"));
                           return 0;
                        } else {
                           LOGGER.info(
                              "Player {} enchanted {} with {} level {} for player {}",
                              new Object[]{
                                 executor.getName().getString(),
                                 stack.getDisplayName().getString(),
                                 enchantId.toString(),
                                 level,
                                 targetPlayer.getName().getString()
                              }
                           );
                           if (mode == EnchantCommand.EnchantMode.TARGET_HAND && !executor.equals(targetPlayer)) {
                              ((CommandSourceStack)ctx.getSource())
                                 .sendSuccess(
                                    () -> MessageUtil.success(
                                          "commands.neoessentials.enchant.success.other",
                                          enchantId.toString(),
                                          level,
                                          stack.getDisplayName().getString(),
                                          targetPlayer.getDisplayName().getString()
                                       ),
                                    false
                                 );
                              targetPlayer.sendSystemMessage(
                                 MessageUtil.info(
                                    "commands.neoessentials.enchant.target.notified", enchantId.toString(), level, executor.getDisplayName().getString()
                                 )
                              );
                           } else {
                              ((CommandSourceStack)ctx.getSource())
                                 .sendSuccess(
                                    () -> MessageUtil.success(
                                          "commands.neoessentials.enchant.success", enchantId.toString(), level, stack.getDisplayName().getString()
                                       ),
                                    false
                                 );
                           }

                           return 1;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isEnchantmentCompatible(Enchantment enchantment, ItemStack stack) {
      try {
         if (!stack.getItem().isEnchantable(stack)) {
            return false;
         } else {
            return stack.getItem().toString().contains("book") ? true : stack.getItem().isEnchantable(stack);
         }
      } catch (Exception var3) {
         return true;
      }
   }

   public static boolean applyEnchantment(ServerPlayer player, ItemStack stack, Enchantment enchantment, int level) {
      if (stack != null && enchantment != null) {
         boolean allowUnsafeEnchants = ConfigManager.isUnsafeEnchantsAllowed();
         if (!allowUnsafeEnchants && level > enchantment.getMaxLevel()) {
            return false;
         } else {
            try {
               Registry<Enchantment> registry = player.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
               Holder<Enchantment> holder = null;

               for (Reference<Enchantment> entry : registry.holders().toList()) {
                  if (((Enchantment)entry.value()).equals(enchantment)) {
                     holder = entry;
                     break;
                  }
               }

               if (holder == null) {
                  return false;
               } else {
                  ItemEnchantments existing = (ItemEnchantments)stack.get(DataComponents.ENCHANTMENTS);
                  Mutable builder;
                  if (existing != null) {
                     builder = new Mutable(existing);
                  } else {
                     builder = new Mutable(ItemEnchantments.EMPTY);
                  }

                  builder.set(holder, level);
                  stack.set(DataComponents.ENCHANTMENTS, builder.toImmutable());
                  return true;
               }
            } catch (Exception var9) {
               LOGGER.error("Failed to apply enchantment to item", var9);
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static enum EnchantMode {
      HAND_ONLY,
      TARGET_HAND;
   }
}
