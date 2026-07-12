package com.zerog.neoessentials.security;

import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.moderation.FreezeManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.InputValidator;
import com.zerog.neoessentials.util.MessageUtil;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class CommandLengthEnforcer {
   private static final Logger LOGGER = LoggerFactory.getLogger(CommandLengthEnforcer.class);

   @SubscribeEvent(
      priority = EventPriority.NORMAL
   )
   public static void onCommand(CommandEvent event) {
      if (CommandSourceHelper.isPlayer((CommandSourceStack)event.getParseResults().getContext().getSource())) {
         ServerPlayer player = CommandSourceHelper.getPlayer((CommandSourceStack)event.getParseResults().getContext().getSource());
         if (player != null) {
            String rawCommand = event.getParseResults().getReader().getString();
            if (!shouldCheckFreezeRestrictions() || !handleFreezeRestriction(event, player, rawCommand)) {
               if (shouldEnforceCommandLength()) {
                  handleCommandLengthValidation(event, player, rawCommand);
               }
            }
         }
      }
   }

   private static boolean shouldCheckFreezeRestrictions() {
      return ConfigManager.isFreezeSystemEnabled() && ConfigManager.isFreezePreventCommandsEnabled();
   }

   private static boolean shouldEnforceCommandLength() {
      return ConfigManager.getInstance().isCommandLengthEnforcerEnabled();
   }

   private static boolean handleFreezeRestriction(CommandEvent event, ServerPlayer player, String rawCommand) {
      FreezeManager freezeManager = FreezeManager.getInstance();
      if (!freezeManager.isPlayerFrozen(player.getUUID())) {
         return false;
      } else {
         String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
         String commandName = command.split(" ", 2)[0].toLowerCase();
         List<String> allowedCommands = ConfigManager.getFreezeAllowedCommands();
         if (allowedCommands.contains(commandName)) {
            LOGGER.debug("Frozen player {} used allowed command: {}", player.getName().getString(), commandName);
            return false;
         } else {
            event.setCanceled(true);
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.freeze.cannot_use_commands"));
            LOGGER.info("Blocked command from frozen player {}: {}", player.getName().getString(), rawCommand);
            return true;
         }
      }
   }

   private static void handleCommandLengthValidation(CommandEvent event, ServerPlayer player, String rawCommand) {
      String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
      InputValidator.ValidationResult result = InputValidator.validateCommand(command);
      if (!result.isValid()) {
         event.setCanceled(true);
         player.sendSystemMessage(MessageUtil.error(result.getErrorMessage()));
         LOGGER.info(
            "Blocked invalid command from {}: {} (Reason: {})",
            new Object[]{player.getName().getString(), command.length() > 50 ? command.substring(0, 50) + "..." : command, result.getErrorMessage()}
         );
      }
   }
}
