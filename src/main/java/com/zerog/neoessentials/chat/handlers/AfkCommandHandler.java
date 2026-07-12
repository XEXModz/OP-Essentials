package com.zerog.neoessentials.chat.handlers;

import com.zerog.neoessentials.chat.AfkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class AfkCommandHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(AfkCommandHandler.class);

   @SubscribeEvent
   public static void onCommandExecute(CommandEvent event) {
      if (((CommandSourceStack)event.getParseResults().getContext().getSource()).getEntity() instanceof ServerPlayer player) {
         String commandName = getCommandName(event.getParseResults().getReader().getString());
         if (AfkManager.getInstance().getExcludedCommands().contains(commandName.toLowerCase())) {
            LOGGER.debug("Command '{}' excluded from AFK activity tracking for {}", commandName, player.getName().getString());
            return;
         }

         AfkManager.getInstance().updateActivity(player.getUUID());
         LOGGER.debug("Command activity tracked for {}: /{}", player.getName().getString(), commandName);
      }
   }

   private static String getCommandName(String fullCommand) {
      if (fullCommand != null && !fullCommand.isEmpty()) {
         String command = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
         int spaceIndex = command.indexOf(32);
         return spaceIndex != -1 ? command.substring(0, spaceIndex) : command;
      } else {
         return "";
      }
   }
}
