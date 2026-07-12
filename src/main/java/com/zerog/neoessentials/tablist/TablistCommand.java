package com.zerog.neoessentials.tablist;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class TablistCommand {
   private static final String PERM_ADMIN = "neoessentials.tablist.admin";

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("tablist")) {
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                       "tablist"
                                    )
                                    .requires(src -> {
                                       ServerPlayer p = src.getPlayer();
                                       return p == null || PermissionAPI.hasPermission(p.getUUID(), "neoessentials.tablist.admin");
                                    }))
                                 .executes(ctx -> {
                                    showHelp((CommandSourceStack)ctx.getSource());
                                    return 1;
                                 }))
                              .then(Commands.literal("reload").executes(ctx -> {
                                 TablistManager.getInstance().loadConfig();
                                 MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                                 if (server != null) {
                                    TablistManager.getInstance().updateAll(server);
                                 }

                                 ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.tablist.reloaded"), false);
                                 return 1;
                              })))
                           .then(Commands.literal("enable").executes(ctx -> {
                              TablistManager.getInstance().setEnabled(true);
                              MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                              if (server != null) {
                                 TablistManager.getInstance().updateAll(server);
                              }

                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.tablist.enabled"), false);
                              return 1;
                           })))
                        .then(Commands.literal("disable").executes(ctx -> {
                           TablistManager.getInstance().setEnabled(false);
                           MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                           if (server != null) {
                              ClientboundTabListPacket emptyPacket = new ClientboundTabListPacket(Component.empty(), Component.empty());

                              for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                 p.connection.send(emptyPacket);
                              }
                           }

                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.tablist.disabled"), false);
                           return 1;
                        })))
                     .then(Commands.literal("preview").executes(ctx -> {
                        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
                        if (player == null) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error("commands.neoessentials.general.player_only"));
                           return 0;
                        } else {
                           MinecraftServer server = player.getServer();
                           if (server != null) {
                              TablistManager.getInstance().updatePlayer(player, server);
                           }

                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§aPreviewing your tablist header/footer."), false);
                           return 1;
                        }
                     })))
                  .then(
                     Commands.literal("info")
                        .executes(
                           ctx -> {
                              boolean enabled = TablistManager.getInstance().isEnabled();
                              ((CommandSourceStack)ctx.getSource())
                                 .sendSuccess(
                                    () -> Component.literal(
                                          "§6Tablist System §8— §"
                                             + (enabled ? "aEnabled" : "cDisabled")
                                             + "\n§7Use §e/tablist reload §7to reload config, §e/tablist preview §7to preview your tab."
                                       ),
                                    false
                                 );
                              return 1;
                           }
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("set")
                        .then(Commands.literal("header").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                           String text = StringArgumentType.getString(ctx, "text");
                           TablistManager tablist = TablistManager.getInstance();
                           tablist.setHeaderOverride(text);
                           MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                           if (server != null) {
                              tablist.updateAll(server);
                           }

                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.tablist.header_set"), false);
                           return 1;
                        }))))
                     .then(Commands.literal("footer").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                        String text = StringArgumentType.getString(ctx, "text");
                        TablistManager tablist = TablistManager.getInstance();
                        tablist.setFooterOverride(text);
                        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                        if (server != null) {
                           tablist.updateAll(server);
                        }

                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> MessageUtil.success("commands.neoessentials.tablist.footer_set"), false);
                        return 1;
                     })))
               )
         );
      }
   }

   private static void showHelp(CommandSourceStack src) {
      src.sendSuccess(
         () -> Component.literal(
               "§6§lTablist Commands:\n§e/tablist reload §7— reload tablist.json config\n§e/tablist enable §7— enable tablist\n§e/tablist disable §7— disable tablist\n§e/tablist preview §7— preview your header/footer\n§e/tablist set header <text> §7— runtime header override\n§e/tablist set footer <text> §7— runtime footer override\n§e/tablist info §7— show status and config file path\n§e/tablist config §7— show current settings summary\n§7Config file: §fconfig/neoessentials/tablist.json"
            ),
         false
      );
   }
}
