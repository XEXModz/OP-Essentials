package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(SignCommand.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("sign")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("sign").requires(source -> {
                     PermissionValidator.PermissionResult result = PermissionValidator.validatePermission(source, "neoessentials.sign");
                     return result.hasPermission();
                  })).executes(SignCommand::editLookingAtSign))
                  .then(
                     Commands.argument("line", IntegerArgumentType.integer(1, 4))
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(SignCommand::editLookingAtSignWithText))
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("clear").executes(SignCommand::clearLookingAtSign))
                     .then(Commands.argument("line", IntegerArgumentType.integer(1, 4)).executes(SignCommand::clearLookingAtSignLine))
               )
         );
      }
   }

   private static int editLookingAtSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      SignBlockEntity signBlockEntity = getTargetedSign(player);
      if (signBlockEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.sign.not_looking_at_sign"));
         return 0;
      } else {
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.sign.current_contents"), false);

         for (int i = 0; i < 4; i++) {
            Component lineText = signBlockEntity.getFrontText().getMessage(i, false);
            String textContent = lineText.getString();
            if (textContent.isEmpty()) {
               textContent = "§7(empty)";
            }

            MutableComponent lineComponent = Component.literal("§6Line " + (i + 1) + ": §f" + textContent);
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> lineComponent, false);
         }

         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MessageUtil.info("commands.neoessentials.sign.usage"), false);
         return 1;
      }
   }

   private static int editLookingAtSignWithText(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      int line = IntegerArgumentType.getInteger(context, "line") - 1;
      String originalText = StringArgumentType.getString(context, "text");
      PermissionValidator.PermissionResult colorResult = PermissionValidator.validatePermission(
         (CommandSourceStack)context.getSource(), "neoessentials.sign.colors"
      );
      String processedText;
      if (colorResult.hasPermission()) {
         processedText = originalText.replace("&", "§");
      } else {
         processedText = originalText.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
      }

      String finalText;
      if (processedText.length() > 15) {
         finalText = processedText.substring(0, 15);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MessageUtil.warning("commands.neoessentials.sign.text_truncated"), false);
      } else {
         finalText = processedText;
      }

      SignBlockEntity signBlockEntity = getTargetedSign(player);
      if (signBlockEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.sign.not_looking_at_sign"));
         return 0;
      } else {
         BlockPos pos = signBlockEntity.getBlockPos();
         updateSignLine(signBlockEntity, line, finalText, player.serverLevel());
         LOGGER.info("Player {} edited sign at {} line {} to: {}", new Object[]{player.getName().getString(), pos, line + 1, finalText});
         ((CommandSourceStack)context.getSource())
            .sendSuccess(() -> MessageUtil.success("commands.neoessentials.sign.updated", line + 1, finalText.isEmpty() ? "§7(empty)" : finalText), false);
         return 1;
      }
   }

   private static int clearLookingAtSign(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      SignBlockEntity signBlockEntity = getTargetedSign(player);
      if (signBlockEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.sign.not_looking_at_sign"));
         return 0;
      } else {
         BlockPos pos = signBlockEntity.getBlockPos();

         for (int i = 0; i < 4; i++) {
            updateSignLine(signBlockEntity, i, "", player.serverLevel());
         }

         LOGGER.info("Player {} cleared all lines on sign at {}", player.getName().getString(), pos);
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.sign.cleared_all"), false);
         return 1;
      }
   }

   private static int clearLookingAtSignLine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
      int line = IntegerArgumentType.getInteger(context, "line") - 1;
      SignBlockEntity signBlockEntity = getTargetedSign(player);
      if (signBlockEntity == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.sign.not_looking_at_sign"));
         return 0;
      } else {
         BlockPos pos = signBlockEntity.getBlockPos();
         updateSignLine(signBlockEntity, line, "", player.serverLevel());
         LOGGER.info("Player {} cleared line {} on sign at {}", new Object[]{player.getName().getString(), line + 1, pos});
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.sign.cleared_line", line + 1), false);
         return 1;
      }
   }

   private static SignBlockEntity getTargetedSign(ServerPlayer player) {
      HitResult hitResult = player.pick(5.0, 0.0F, false);
      if (hitResult.getType() != Type.BLOCK) {
         return null;
      } else {
         BlockHitResult blockHitResult = (BlockHitResult)hitResult;
         BlockPos pos = blockHitResult.getBlockPos();
         ServerLevel level = player.serverLevel();
         BlockEntity blockEntity = level.getBlockEntity(pos);
         return blockEntity instanceof SignBlockEntity ? (SignBlockEntity)blockEntity : null;
      }
   }

   private static void updateSignLine(SignBlockEntity signBlockEntity, int line, String text, ServerLevel level) {
      Component textComponent = MessageUtil.coloredText(text);
      SignText currentText = signBlockEntity.getFrontText();
      SignText newText = currentText.setMessage(line, textComponent);
      signBlockEntity.updateText(signBlockEntity_ -> newText, true);
      signBlockEntity.setChanged();
      BlockState blockState = level.getBlockState(signBlockEntity.getBlockPos());
      level.sendBlockUpdated(signBlockEntity.getBlockPos(), blockState, blockState, 3);
   }
}
