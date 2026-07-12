package com.zerog.neoessentials.vault.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.vault.api.VaultEconomy;
import com.zerog.neoessentials.vault.api.VaultServiceRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class VaultCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("vault")
                     .requires(
                        src -> src.hasPermission(3)
                              || PermissionAPI.hasPermission(src.getEntity() != null ? src.getEntity().getUUID() : null, "neoessentials.vault.admin")
                     ))
                  .then(Commands.literal("info").executes(ctx -> executeInfo((CommandSourceStack)ctx.getSource()))))
               .then(
                  Commands.literal("convert")
                     .then(
                        Commands.argument("from", StringArgumentType.word())
                           .then(
                              Commands.argument("to", StringArgumentType.word())
                                 .executes(
                                    ctx -> executeConvert(
                                          (CommandSourceStack)ctx.getSource(),
                                          StringArgumentType.getString(ctx, "from"),
                                          StringArgumentType.getString(ctx, "to")
                                       )
                                 )
                           )
                     )
               ))
            .executes(ctx -> executeInfo((CommandSourceStack)ctx.getSource()))
      );
   }

   private static int executeInfo(CommandSourceStack src) {
      VaultServiceRegistry reg = VaultServiceRegistry.getInstance();
      src.sendSuccess(() -> Component.literal("§6§l=== NeoEssentials Vault API ==="), false);
      String econList = buildProviderList(reg.getEconomyProviders(), r -> r.provider.getName() + " [" + r.registeredBy + "]");
      Optional<VaultEconomy> eco = reg.getEconomy();
      src.sendSuccess(
         () -> Component.literal(
               String.format("§eEconomy:    §f%s §7[all: %s]", eco.map(VaultEconomy::getName).orElse("§cnone"), econList.isEmpty() ? "none" : econList)
            ),
         false
      );
      String permList = buildProviderList(reg.getPermissionProviders(), r -> r.provider.getName() + " [" + r.registeredBy + "]");
      src.sendSuccess(
         () -> Component.literal(
               String.format(
                  "§ePermission: §f%s §7[all: %s]", reg.getPermission().map(p -> p.getName()).orElse("§cnone"), permList.isEmpty() ? "none" : permList
               )
            ),
         false
      );
      String chatList = buildProviderList(reg.getChatProviders(), r -> r.provider.getName() + " [" + r.registeredBy + "]");
      src.sendSuccess(
         () -> Component.literal(
               String.format("§eChat:       §f%s §7[all: %s]", reg.getChat().map(c -> c.getName()).orElse("§cnone"), chatList.isEmpty() ? "none" : chatList)
            ),
         false
      );
      return 1;
   }

   private static int executeConvert(CommandSourceStack src, String fromName, String toName) {
      List<VaultServiceRegistry.Registration<VaultEconomy>> providers = VaultServiceRegistry.getInstance().getEconomyProviders();
      if (providers.size() < 2) {
         src.sendFailure(Component.literal("§cYou need at least 2 economy providers registered to convert."));
         return 0;
      } else {
         VaultEconomy from = null;
         VaultEconomy to = null;
         StringBuilder nameList = new StringBuilder();

         for (VaultServiceRegistry.Registration<VaultEconomy> reg : providers) {
            String n = reg.provider.getName().replace(" ", "");
            if (n.equalsIgnoreCase(fromName)) {
               from = reg.provider;
            }

            if (n.equalsIgnoreCase(toName)) {
               to = reg.provider;
            }

            if (nameList.length() > 0) {
               nameList.append(", ");
            }

            nameList.append(n);
         }

         if (from == null) {
            src.sendFailure(Component.literal("§cEconomy '" + fromName + "' not found. Available: " + nameList));
            return 0;
         } else if (to == null) {
            src.sendFailure(Component.literal("§cEconomy '" + toName + "' not found. Available: " + nameList));
            return 0;
         } else {
            VaultEconomy fromFinal = from;
            VaultEconomy toFinal = to;
            src.sendSuccess(
               () -> Component.literal(
                     "§eConverting balances from §f" + fromFinal.getName() + " §eto §f" + toFinal.getName() + "§e... (this may take a moment)"
                  ),
               false
            );
            int[] count = new int[]{0};

            try {
               MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
               if (server != null) {
                  for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                     UUID id = player.getUUID();
                     if (fromFinal.hasAccount(id)) {
                        if (!toFinal.hasAccount(id)) {
                           toFinal.createPlayerAccount(id);
                        }

                        double diff = fromFinal.getBalance(id) - toFinal.getBalance(id);
                        if (diff > 0.0) {
                           toFinal.depositPlayer(id, diff);
                        } else if (diff < 0.0) {
                           toFinal.withdrawPlayer(id, -diff);
                        }

                        count[0]++;
                     }
                  }
               }
            } catch (Exception var16) {
               src.sendFailure(Component.literal("§cConversion failed: " + var16.getMessage()));
               return 0;
            }

            int converted = count[0];
            src.sendSuccess(() -> Component.literal("§aConversion complete. §f" + converted + " §aaccount(s) processed. Verify data before use."), false);
            return 1;
         }
      }
   }

   private static <T> String buildProviderList(Collection<T> regs, VaultCommand.ProviderLabel<T> labeler) {
      StringBuilder sb = new StringBuilder();

      for (T r : regs) {
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(labeler.label(r));
      }

      return sb.toString();
   }

   @FunctionalInterface
   private interface ProviderLabel<T> {
      String label(T var1);
   }
}
