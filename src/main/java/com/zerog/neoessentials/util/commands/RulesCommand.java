package com.zerog.neoessentials.util.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(RulesCommand.class);
   private static List<String> serverRules = new ArrayList<>();
   private static final Path RULES_DATA_FILE = Paths.get("config", "neoessentials", "rules_data.json");
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final int RULES_PER_PAGE = 10;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("rules")) {
         loadRulesData();
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                       "rules"
                                    )
                                    .executes(
                                       ctx -> {
                                          PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                             (CommandSourceStack)ctx.getSource(), "neoessentials.rules"
                                          );
                                          if (!permResult.hasPermission()) {
                                             ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                             return 0;
                                          } else {
                                             return showRules((CommandSourceStack)ctx.getSource(), 1);
                                          }
                                       }
                                    ))
                                 .then(
                                    Commands.argument("page", IntegerArgumentType.integer(1))
                                       .executes(
                                          ctx -> {
                                             PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                                (CommandSourceStack)ctx.getSource(), "neoessentials.rules"
                                             );
                                             if (!permResult.hasPermission()) {
                                                ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                                return 0;
                                             } else {
                                                int page = IntegerArgumentType.getInteger(ctx, "page");
                                                return showRules((CommandSourceStack)ctx.getSource(), page);
                                             }
                                          }
                                       )
                                 ))
                              .then(
                                 Commands.literal("add")
                                    .then(
                                       Commands.argument("rule", StringArgumentType.greedyString())
                                          .executes(
                                             ctx -> {
                                                PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                                   (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                                                );
                                                if (!permResult.hasPermission()) {
                                                   ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                                   return 0;
                                                } else {
                                                   String rule = StringArgumentType.getString(ctx, "rule");
                                                   return addRule((CommandSourceStack)ctx.getSource(), rule);
                                                }
                                             }
                                          )
                                    )
                              ))
                           .then(
                              Commands.literal("remove")
                                 .then(
                                    Commands.argument("number", IntegerArgumentType.integer(1))
                                       .executes(
                                          ctx -> {
                                             PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                                (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                                             );
                                             if (!permResult.hasPermission()) {
                                                ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                                return 0;
                                             } else {
                                                int number = IntegerArgumentType.getInteger(ctx, "number");
                                                return removeRule((CommandSourceStack)ctx.getSource(), number);
                                             }
                                          }
                                       )
                                 )
                           ))
                        .then(
                           Commands.literal("edit")
                              .then(
                                 Commands.argument("number", IntegerArgumentType.integer(1))
                                    .then(
                                       Commands.argument("newText", StringArgumentType.greedyString())
                                          .executes(
                                             ctx -> {
                                                PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                                   (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                                                );
                                                if (!permResult.hasPermission()) {
                                                   ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                                   return 0;
                                                } else {
                                                   int number = IntegerArgumentType.getInteger(ctx, "number");
                                                   String newText = StringArgumentType.getString(ctx, "newText");
                                                   return editRule((CommandSourceStack)ctx.getSource(), number, newText);
                                                }
                                             }
                                          )
                                    )
                              )
                        ))
                     .then(
                        Commands.literal("insert")
                           .then(
                              Commands.argument("number", IntegerArgumentType.integer(1))
                                 .then(
                                    Commands.argument("rule", StringArgumentType.greedyString())
                                       .executes(
                                          ctx -> {
                                             PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                                (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                                             );
                                             if (!permResult.hasPermission()) {
                                                ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                                return 0;
                                             } else {
                                                int number = IntegerArgumentType.getInteger(ctx, "number");
                                                String rule = StringArgumentType.getString(ctx, "rule");
                                                return insertRule((CommandSourceStack)ctx.getSource(), number, rule);
                                             }
                                          }
                                       )
                                 )
                           )
                     ))
                  .then(
                     Commands.literal("clear")
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 return clearRules((CommandSourceStack)ctx.getSource());
                              }
                           }
                        )
                  ))
               .then(
                  Commands.literal("reload")
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.rules.admin"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              return reloadRules((CommandSourceStack)ctx.getSource());
                           }
                        }
                     )
               )
         );
      }
   }

   private static int showRules(CommandSourceStack source, int page) {
      if (serverRules.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.rules.no_rules"), false);
         return 1;
      } else {
         int totalPages = (int)Math.ceil((double)serverRules.size() / 10.0);
         if (page > totalPages) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.rules.invalid_page", page, totalPages));
            return 0;
         } else {
            int startIndex = (page - 1) * 10;
            int endIndex = Math.min(startIndex + 10, serverRules.size());
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.header", page, totalPages), false);

            for (int i = startIndex; i < endIndex; i++) {
               String rule = serverRules.get(i);
               String formattedRule = rule.replace("&", "§");
               MutableComponent ruleComponent = Component.literal(String.format("§6%d. §f%s", i + 1, formattedRule));
               source.sendSuccess(() -> ruleComponent, false);
            }

            if (totalPages > 1) {
               MutableComponent footer = Component.literal("§7Page " + page + "/" + totalPages + " ");
               if (page > 1) {
                  footer.append(
                     Component.literal("§7[§a◀ Prev§7]")
                        .withStyle(
                           style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/rules " + (page - 1)))
                                 .withHoverEvent(
                                    new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to view previous page"))
                                 )
                        )
                  );
                  footer.append(Component.literal(" "));
               }

               if (page < totalPages) {
                  footer.append(
                     Component.literal("§7[§aNext ▶§7]")
                        .withStyle(
                           style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/rules " + (page + 1)))
                                 .withHoverEvent(
                                    new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to view next page"))
                                 )
                        )
                  );
               }

               source.sendSuccess(() -> footer, false);
            }

            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.rules.footer"), false);
            return 1;
         }
      }
   }

   private static int addRule(CommandSourceStack source, String rule) {
      if (rule.length() > 200) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.too_long"));
         return 0;
      } else {
         serverRules.add(rule);
         saveRulesData();
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.added", serverRules.size()), false);
         return 1;
      }
   }

   private static int removeRule(CommandSourceStack source, int number) {
      if (number >= 1 && number <= serverRules.size()) {
         String removedRule = serverRules.remove(number - 1);
         saveRulesData();
         String formattedRule = removedRule.replace("&", "§");
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.removed", number, formattedRule), false);
         return 1;
      } else {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.invalid_number", number, serverRules.size()));
         return 0;
      }
   }

   private static int editRule(CommandSourceStack source, int number, String newText) {
      if (number >= 1 && number <= serverRules.size()) {
         if (newText.length() > 200) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.rules.too_long"));
            return 0;
         } else {
            serverRules.set(number - 1, newText);
            saveRulesData();
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.edited", number), false);
            return 1;
         }
      } else {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.invalid_number", number, serverRules.size()));
         return 0;
      }
   }

   private static int insertRule(CommandSourceStack source, int number, String rule) {
      if (number < 1 || number > serverRules.size() + 1) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.invalid_insert_position", number, serverRules.size() + 1));
         return 0;
      } else if (rule.length() > 200) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.too_long"));
         return 0;
      } else {
         serverRules.add(number - 1, rule);
         saveRulesData();
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.inserted", number), false);
         return 1;
      }
   }

   private static int clearRules(CommandSourceStack source) {
      if (serverRules.isEmpty()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.rules.already_empty"));
         return 0;
      } else {
         int count = serverRules.size();
         serverRules.clear();
         saveRulesData();
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.cleared", count), false);
         return 1;
      }
   }

   private static int reloadRules(CommandSourceStack source) {
      loadRulesData();
      int newCount = serverRules.size();
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.rules.reloaded", newCount), false);
      return 1;
   }

   private static void loadRulesData() {
      try {
         if (!Files.exists(RULES_DATA_FILE)) {
            createDefaultRules();
            return;
         }

         String json = Files.readString(RULES_DATA_FILE);
         JsonObject data = JsonParser.parseString(json).getAsJsonObject();
         serverRules.clear();
         if (data.has("rules")) {
            for (JsonElement element : data.getAsJsonArray("rules")) {
               serverRules.add(element.getAsString());
            }
         }
      } catch (Exception var5) {
         LOGGER.error("Failed to load rules data: {}", var5.getMessage());
         createDefaultRules();
      }
   }

   private static void createDefaultRules() {
      serverRules.clear();
      serverRules.add("&6Be respectful to all players and staff members");
      serverRules.add("&cNo griefing, stealing, or destroying other players' builds");
      serverRules.add("&eNo spamming in chat or using excessive caps");
      serverRules.add("&bNo cheating, hacking, or using unauthorized mods");
      serverRules.add("&5Keep builds appropriate and family-friendly");
      serverRules.add("&aFollow staff instructions and respect their decisions");
      serverRules.add("&9No advertising other servers or external content");
      serverRules.add("&dUse common sense and help maintain a fun environment");
      serverRules.add("&7Report any issues or rule violations to staff");
      serverRules.add("&2Have fun and enjoy your time on the server!");
      saveRulesData();
   }

   private static void saveRulesData() {
      try {
         JsonObject data = new JsonObject();
         JsonArray rulesArray = new JsonArray();

         for (String rule : serverRules) {
            rulesArray.add(rule);
         }

         data.add("rules", rulesArray);
         Files.createDirectories(RULES_DATA_FILE.getParent());
         Files.writeString(RULES_DATA_FILE, GSON.toJson(data));
      } catch (Exception var4) {
         LOGGER.error("Failed to save rules data: {}", var4.getMessage());
      }
   }

   public static List<String> getRules() {
      return new ArrayList<>(serverRules);
   }

   public static void showRulesToPlayer(ServerPlayer player) {
      showRules(player.createCommandSourceStack(), 1);
   }
}
