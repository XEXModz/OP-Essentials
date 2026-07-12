package com.zerog.neoessentials.commands.utility;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zerog.neoessentials.util.PermissionValidator;
import com.zerog.neoessentials.webdashboard.security.DashboardAccountRegistration;
import com.zerog.neoessentials.webdashboard.security.DashboardRegistrationManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DashboardRegisterCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "dashboardregister"
                        )
                        .requires(
                           source -> !source.isPlayer()
                                 ? source.hasPermission(2)
                                 : PermissionValidator.validatePermission(source, "neoessentials.dashboard.access").hasPermission()
                        ))
                     .executes(context -> {
                        CommandSourceStack source = (CommandSourceStack)context.getSource();
                        source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
                        source.sendSuccess(() -> Component.literal("§e§lDashboard Registration"), false);
                        source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
                        source.sendSuccess(() -> Component.literal(""), false);
                        source.sendSuccess(() -> Component.literal("§7Available commands:"), false);
                        source.sendSuccess(() -> Component.literal("  §e/dashboardregister start §7- Begin registration"), false);
                        source.sendSuccess(() -> Component.literal("  §e/dashboardregister complete <user> <pass> §7- Finish registration"), false);
                        source.sendSuccess(() -> Component.literal("  §e/dashboardregister status §7- Check your status"), false);
                        source.sendSuccess(() -> Component.literal(""), false);
                        source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
                        return 1;
                     }))
                  .then(Commands.literal("start").executes(DashboardRegisterCommand::startRegistration)))
               .then(
                  Commands.literal("complete")
                     .then(
                        Commands.argument("username", StringArgumentType.word())
                           .then(Commands.argument("password", StringArgumentType.greedyString()).executes(DashboardRegisterCommand::completeRegistration))
                     )
               ))
            .then(Commands.literal("status").executes(DashboardRegisterCommand::checkStatus))
      );
   }

   private static int startRegistration(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      if (!source.isPlayer()) {
         source.sendSuccess(() -> Component.literal("§cThis command can only be used by players"), false);
         return 0;
      } else {
         ServerPlayer player = (ServerPlayer)source.getEntity();
         DashboardRegistrationManager manager = DashboardRegistrationManager.getInstance();
         System.out.println("[DashboardRegister] Player " + player.getName().getString() + " (" + player.getUUID() + ") attempting registration");
         if (manager.isRegistered(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§e§lINFO: §eYou already have a registered dashboard account!"), false);
            source.sendSuccess(() -> Component.literal("§7Use your dashboard credentials to log in"), false);
            System.out.println("[DashboardRegister] Player already registered");
            return 0;
         } else {
            String token = manager.startRegistration(player.getUUID(), player.getName().getString());
            System.out.println("[DashboardRegister] Registration token generated: " + (token != null ? "SUCCESS" : "FAILED"));
            if (token == null) {
               source.sendSuccess(() -> Component.literal("§c§lERROR: §cFailed to start registration"), false);
               source.sendSuccess(() -> Component.literal("§7Please contact a server administrator"), false);
               return 0;
            } else {
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               source.sendSuccess(() -> Component.literal("§a§lDashboard Registration Started"), false);
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§7Your registration token: §e" + token), false);
               source.sendSuccess(() -> Component.literal("§7This token expires in §c5 minutes"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§7To complete registration, use:"), false);
               source.sendSuccess(() -> Component.literal("§b/dashboardregister complete <username> <password>"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§eExample:"), false);
               source.sendSuccess(() -> Component.literal("§7/dashboardregister complete myusername MySecurePass123"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§c§lWARNING: §cPassword must be at least 8 characters"), false);
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               return 1;
            }
         }
      }
   }

   private static int completeRegistration(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      if (!source.isPlayer()) {
         source.sendSuccess(() -> Component.literal("§cThis command can only be used by players"), false);
         return 0;
      } else {
         ServerPlayer player = (ServerPlayer)source.getEntity();
         String username = StringArgumentType.getString(context, "username");
         String password = StringArgumentType.getString(context, "password");
         DashboardRegistrationManager manager = DashboardRegistrationManager.getInstance();
         if (manager.isRegistered(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§c§lERROR: §cYou already have a registered dashboard account!"), false);
            return 0;
         } else if (username.length() < 3 || username.length() > 20) {
            source.sendSuccess(() -> Component.literal("§c§lERROR: §cUsername must be 3-20 characters"), false);
            return 0;
         } else if (password.length() < 8) {
            source.sendSuccess(() -> Component.literal("§c§lERROR: §cPassword must be at least 8 characters"), false);
            return 0;
         } else {
            source.sendSuccess(() -> Component.literal("§6Processing registration..."), false);
            DashboardAccountRegistration registration = completeRegistrationByUuid(player.getUUID(), username, password);
            if (registration == null) {
               source.sendSuccess(() -> Component.literal("§c§lERROR: §cRegistration failed!"), false);
               source.sendSuccess(() -> Component.literal("§7Possible reasons:"), false);
               source.sendSuccess(() -> Component.literal("§7- Registration token expired (5 min limit)"), false);
               source.sendSuccess(() -> Component.literal("§7- Username already taken"), false);
               source.sendSuccess(() -> Component.literal("§7- No registration started"), false);
               source.sendSuccess(() -> Component.literal("§7Use §e/dashboardregister start §7to begin"), false);
               return 0;
            } else {
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               source.sendSuccess(() -> Component.literal("§a§l✓ Registration Successful!"), false);
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§7Dashboard Username: §a" + registration.getDashboardUsername()), false);
               source.sendSuccess(() -> Component.literal("§7Linked to: §e" + player.getName().getString()), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§7You can now log in to the dashboard at:"), false);
               source.sendSuccess(() -> Component.literal("§b§n/dashboard url"), false);
               source.sendSuccess(() -> Component.literal(""), false);
               source.sendSuccess(() -> Component.literal("§7Use your dashboard username and password to log in"), false);
               source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
               return 1;
            }
         }
      }
   }

   private static int checkStatus(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      if (!source.isPlayer()) {
         source.sendSuccess(() -> Component.literal("§cThis command can only be used by players"), false);
         return 0;
      } else {
         ServerPlayer player = (ServerPlayer)source.getEntity();
         DashboardRegistrationManager manager = DashboardRegistrationManager.getInstance();
         source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
         source.sendSuccess(() -> Component.literal("§e§lDashboard Registration Status"), false);
         source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
         source.sendSuccess(() -> Component.literal(""), false);
         if (manager.isRegistered(player.getUUID())) {
            DashboardAccountRegistration reg = manager.getRegistration(player.getUUID());
            source.sendSuccess(() -> Component.literal("§a§l✓ §aRegistered"), false);
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§7Dashboard Username: §a" + reg.getDashboardUsername()), false);
            source.sendSuccess(() -> Component.literal("§7Minecraft Account: §e" + reg.getMinecraftUsername()), false);
            source.sendSuccess(() -> Component.literal("§7Registered: §7" + formatTimestamp(reg.getRegisteredAt())), false);
            if (reg.isDiscordLinked()) {
               source.sendSuccess(() -> Component.literal("§7Discord: §b" + reg.getDiscordUsername() + " §a(Linked)"), false);
            } else {
               source.sendSuccess(() -> Component.literal("§7Discord: §c(Not Linked)"), false);
            }
         } else {
            source.sendSuccess(() -> Component.literal("§c§l✗ §cNot Registered"), false);
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("§7Use §e/dashboardregister start §7to register"), false);
         }

         source.sendSuccess(() -> Component.literal(""), false);
         source.sendSuccess(() -> Component.literal("§6§l═══════════════════════════════════"), false);
         return 1;
      }
   }

   private static DashboardAccountRegistration completeRegistrationByUuid(UUID playerUuid, String username, String password) {
      DashboardRegistrationManager manager = DashboardRegistrationManager.getInstance();
      return manager.completeRegistrationByUuid(playerUuid, username, password);
   }

   private static String formatTimestamp(long timestamp) {
      SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
      return sdf.format(new Date(timestamp));
   }
}
