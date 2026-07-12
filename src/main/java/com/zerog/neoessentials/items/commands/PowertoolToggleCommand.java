package com.zerog.neoessentials.items.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;

@Deprecated
public class PowertoolToggleCommand {
   @Deprecated
   public static boolean isPowertoolEnabled(UUID playerUUID, String itemId) {
      return PowertoolCommand.isPowertoolEnabled(playerUUID);
   }

   @Deprecated
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
   }
}
