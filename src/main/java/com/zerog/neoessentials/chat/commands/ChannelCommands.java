package com.zerog.neoessentials.chat.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.chat.ChatHandler;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger(ChannelCommands.class);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      try {
         JsonObject mainConfig = ConfigManager.getInstance().getConfig("config.json");
         JsonObject chatConfig = mainConfig.has("chat") ? mainConfig.getAsJsonObject("chat") : null;
         JsonObject channelsConfig = chatConfig != null && chatConfig.has("channels") ? chatConfig.getAsJsonObject("channels") : null;
         if (channelsConfig == null) {
            LOGGER.warn("No channels configuration found, skipping channel command registration");
            return;
         }

         boolean channelsEnabled = true;
         if (channelsConfig.has("enabled")) {
            channelsEnabled = channelsConfig.get("enabled").getAsBoolean();
         }

         if (!channelsEnabled) {
            LOGGER.info("Chat channels system is disabled, skipping channel command registration");
            return;
         }

         int registeredCount = 0;

         for (String channelName : channelsConfig.keySet()) {
            if (!channelName.equals("enabled") && !channelName.endsWith("-description")) {
               JsonObject channelObj = channelsConfig.getAsJsonObject(channelName);
               if (channelObj.has("enabled") && !channelObj.get("enabled").getAsBoolean()) {
                  LOGGER.debug("Channel '{}' is disabled, skipping command registration", channelName);
               } else {
                  String command = channelObj.has("command") ? channelObj.get("command").getAsString() : channelName;
                  String permission = channelObj.has("permission") ? channelObj.get("permission").getAsString() : null;
                  registerChannelCommand(dispatcher, command, channelName, permission);
                  registeredCount++;
                  if (channelObj.has("aliases") && channelObj.get("aliases").isJsonArray()) {
                     for (JsonElement aliasElement : channelObj.getAsJsonArray("aliases")) {
                        String alias = aliasElement.getAsString();
                        registerChannelCommand(dispatcher, alias, channelName, permission);
                        registeredCount++;
                     }
                  }
               }
            }
         }

         LOGGER.info("Registered {} channel commands", registeredCount);
      } catch (Exception var15) {
         LOGGER.error("Failed to register channel commands: {}", var15.getMessage(), var15);
      }
   }

   private static void registerChannelCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName, String channelName, String permission) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(commandName).executes(ctx -> switchChannel(ctx, channelName, permission)))
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
               int result = switchChannel(ctx, channelName, permission);
               if (result == 1) {
                  String message = StringArgumentType.getString(ctx, "message");
                  ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                  ServerChatEvent chatEvent = new ServerChatEvent(player, message, Component.literal(message));
                  NeoForge.EVENT_BUS.post(chatEvent);
               }

               return result;
            }))
      );
   }

   private static int switchChannel(CommandContext<CommandSourceStack> ctx, String channelName, String permission) {
      try {
         ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         if (permission != null && !permission.isEmpty()) {
            PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission((CommandSourceStack)ctx.getSource(), permission);
            if (!permResult.hasPermission()) {
               ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
               return 0;
            }
         }

         ChatHandler.setPlayerChannel(player.getUUID(), channelName);
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.channel.switched", channelName), false);
         LOGGER.debug("Player {} switched to channel: {}", player.getName().getString(), channelName);
         return 1;
      } catch (Exception var5) {
         LOGGER.error("Error switching channel: {}", var5.getMessage(), var5);
         ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.channel.error"));
         return 0;
      }
   }
}
