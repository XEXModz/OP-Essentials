package com.zerog.neoessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.text.DecimalFormat;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class WhoisCommand {
   private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("whois")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("whois").requires(source -> {
                  PermissionValidator.PermissionResult result = PermissionValidator.validatePermission(source, "neoessentials.whois");
                  return result.hasPermission();
               })).then(Commands.argument("player", EntityArgument.player()).executes(WhoisCommand::whoisPlayer)))
               .then(Commands.argument("playername", StringArgumentType.word()).executes(WhoisCommand::whoisPlayerByName))
         );
      }
   }

   private static int whoisPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
      return showPlayerInfo(context, targetPlayer);
   }

   private static int whoisPlayerByName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      String playerName = StringArgumentType.getString(context, "playername");
      MinecraftServer server = ((CommandSourceStack)context.getSource()).getServer();
      ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(playerName);
      return targetPlayer != null ? showPlayerInfo(context, targetPlayer) : showOfflinePlayerInfo(context, playerName);
   }

   private static int showPlayerInfo(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      PermissionValidator.PermissionResult detailedResult = PermissionValidator.validatePermission(source, "neoessentials.whois.detailed");
      boolean canSeeDetailed = detailedResult.hasPermission();
      MutableComponent header = Component.literal("§6§l┌─ Player Information: " + targetPlayer.getName().getString() + " ─┐");
      source.sendSuccess(() -> header, false);
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.separator"), false);
      String displayName = targetPlayer.getDisplayName().getString();
      String realName = targetPlayer.getName().getString();
      if (!displayName.equals(realName)) {
         MutableComponent nickInfo = Component.literal("§aNickname: §f" + displayName + " §7(Real: " + realName + ")");
         source.sendSuccess(() -> nickInfo, false);
      } else {
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.username", realName), false);
      }

      if (canSeeDetailed) {
         MutableComponent uuidComponent = Component.literal("§bUUID: §7" + targetPlayer.getUUID().toString())
            .withStyle(
               style -> style.withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, targetPlayer.getUUID().toString()))
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy UUID")))
            );
         source.sendSuccess(() -> uuidComponent, false);
      }

      String status = "§aOnline";
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.status", status), false);
      GameType gameType = targetPlayer.gameMode.getGameModeForPlayer();

      String gameModeName = switch (gameType) {
         case SURVIVAL -> "§aSurvival";
         case CREATIVE -> "§6Creative";
         case ADVENTURE -> "§9Adventure";
         case SPECTATOR -> "§7Spectator";
         default -> throw new MatchException(null, null);
      };
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.gamemode", gameModeName), false);

      try {
         ServerPlayer viewer = source.getPlayerOrException();
         if (canSeeDetailed || viewer.equals(targetPlayer)) {
            float health = targetPlayer.getHealth();
            float maxHealth = targetPlayer.getMaxHealth();
            int foodLevel = targetPlayer.getFoodData().getFoodLevel();
            source.sendSuccess(
               () -> MessageUtil.component(
                     "commands.neoessentials.whois.health_online", DECIMAL_FORMAT.format((double)health), DECIMAL_FORMAT.format((double)maxHealth)
                  ),
               false
            );
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.food", foodLevel), false);
         }
      } catch (CommandSyntaxException var19) {
         if (canSeeDetailed) {
            float health = targetPlayer.getHealth();
            float maxHealth = targetPlayer.getMaxHealth();
            int foodLevel = targetPlayer.getFoodData().getFoodLevel();
            source.sendSuccess(
               () -> MessageUtil.component(
                     "commands.neoessentials.whois.health_offline", DECIMAL_FORMAT.format((double)health), DECIMAL_FORMAT.format((double)maxHealth)
                  ),
               false
            );
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.food", foodLevel), false);
         }
      }

      if (canSeeDetailed) {
         double x = targetPlayer.getX();
         double y = targetPlayer.getY();
         double z = targetPlayer.getZ();
         String dimension = targetPlayer.level().dimension().location().toString();
         MutableComponent locationComponent = Component.literal(
               "§dLocation: §f" + DECIMAL_FORMAT.format(x) + ", " + DECIMAL_FORMAT.format(y) + ", " + DECIMAL_FORMAT.format(z) + " §7in §f" + dimension
            )
            .withStyle(
               style -> style.withClickEvent(
                        new ClickEvent(
                           Action.SUGGEST_COMMAND, "/tp " + DECIMAL_FORMAT.format(x) + " " + DECIMAL_FORMAT.format(y) + " " + DECIMAL_FORMAT.format(z)
                        )
                     )
                     .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to get teleport command")))
            );
         source.sendSuccess(() -> locationComponent, false);
      }

      int expLevel = targetPlayer.experienceLevel;
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.experience_level", expLevel), false);
      if (canSeeDetailed && targetPlayer.connection != null) {
         String ipAddress = targetPlayer.connection.getRemoteAddress().toString();
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.ip_address", ipAddress), false);
      }

      showPlayTimeInfo(source, targetPlayer);
      source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.separator"), false);
      return 1;
   }

   private static int showOfflinePlayerInfo(CommandContext<CommandSourceStack> context, String playerName) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      PermissionValidator.PermissionResult detailedResult = PermissionValidator.validatePermission(source, "neoessentials.whois.detailed");
      boolean canSeeDetailed = detailedResult.hasPermission();
      if (canSeeDetailed) {
         Optional<Component> seenInfo = getOfflinePlayerSeenInfo(playerName);
         if (seenInfo.isPresent()) {
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.offline_header", playerName), false);
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.separator"), false);
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.status_offline"), false);
            source.sendSuccess(() -> seenInfo.get(), false);
            source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.separator"), false);
            return 1;
         }
      }

      source.sendFailure(Component.translatable("commands.neoessentials.whois.player_not_found", new Object[]{playerName}));
      return 0;
   }

   private static void showPlayTimeInfo(CommandSourceStack source, ServerPlayer player) {
      try {
         source.sendSuccess(() -> MessageUtil.component("commands.neoessentials.whois.session_time"), false);
      } catch (Exception var3) {
      }
   }

   private static Optional<Component> getOfflinePlayerSeenInfo(String playerName) {
      return Optional.empty();
   }
}
