package com.zerog.neoessentials.util.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotdCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(MotdCommand.class);
   private static String currentMotd = "";
   private static String motdAuthor = "Server";
   private static String motdTimestamp = "";
   private static final Path MOTD_DATA_FILE = Paths.get("config", "neoessentials", "motd_data.json");
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("motd")) {
         loadMotdData();
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("motd")
                           .executes(
                              ctx -> {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.motd"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    return showMotd((CommandSourceStack)ctx.getSource());
                                 }
                              }
                           ))
                        .then(
                           Commands.literal("reload")
                              .executes(
                                 ctx -> {
                                    PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                       (CommandSourceStack)ctx.getSource(), "neoessentials.motd.reload"
                                    );
                                    if (!permResult.hasPermission()) {
                                       ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                       return 0;
                                    } else {
                                       return reloadMotd((CommandSourceStack)ctx.getSource());
                                    }
                                 }
                              )
                        ))
                     .then(
                        Commands.literal("set")
                           .then(
                              Commands.argument("message", StringArgumentType.greedyString())
                                 .executes(
                                    ctx -> {
                                       PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                          (CommandSourceStack)ctx.getSource(), "neoessentials.motd.set"
                                       );
                                       if (!permResult.hasPermission()) {
                                          ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                          return 0;
                                       } else {
                                          String message = StringArgumentType.getString(ctx, "message");
                                          String author = ((CommandSourceStack)ctx.getSource()).getEntity() instanceof ServerPlayer
                                             ? ((ServerPlayer)((CommandSourceStack)ctx.getSource()).getEntity()).getName().getString()
                                             : "Console";
                                          return setMotd((CommandSourceStack)ctx.getSource(), message, author);
                                       }
                                    }
                                 )
                           )
                     ))
                  .then(
                     Commands.literal("clear")
                        .executes(
                           ctx -> {
                              PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                 (CommandSourceStack)ctx.getSource(), "neoessentials.motd.set"
                              );
                              if (!permResult.hasPermission()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                 return 0;
                              } else {
                                 String author = ((CommandSourceStack)ctx.getSource()).getEntity() instanceof ServerPlayer
                                    ? ((ServerPlayer)((CommandSourceStack)ctx.getSource()).getEntity()).getName().getString()
                                    : "Console";
                                 return clearMotd((CommandSourceStack)ctx.getSource(), author);
                              }
                           }
                        )
                  ))
               .then(
                  Commands.literal("broadcast")
                     .executes(
                        ctx -> {
                           PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                              (CommandSourceStack)ctx.getSource(), "neoessentials.motd.broadcast"
                           );
                           if (!permResult.hasPermission()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                              return 0;
                           } else {
                              return broadcastMotd((CommandSourceStack)ctx.getSource());
                           }
                        }
                     )
               )
         );
      }
   }

   private static int showMotd(CommandSourceStack source) {
      if (currentMotd.isEmpty()) {
         source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.motd.no_motd"), false);
         return 1;
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.motd.header"), false);
         String formattedMotd = currentMotd.replace("&", "§");
         Component motdComponent = Component.literal(formattedMotd);
         source.sendSuccess(() -> motdComponent, false);
         if (!motdTimestamp.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.info("commands.neoessentials.motd.footer", motdAuthor, motdTimestamp), false);
         }

         return 1;
      }
   }

   private static int setMotd(CommandSourceStack source, String message, String author) {
      if (message.length() > 500) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.motd.too_long"));
         return 0;
      } else {
         currentMotd = message;
         motdAuthor = author;
         motdTimestamp = LocalDateTime.now().format(TIME_FORMAT);
         if (!saveMotdData()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.motd.save_failed"));
            return 0;
         } else {
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.motd.set"), false);
            return 1;
         }
      }
   }

   private static int clearMotd(CommandSourceStack source, String author) {
      currentMotd = "";
      motdAuthor = author;
      motdTimestamp = LocalDateTime.now().format(TIME_FORMAT);
      if (!saveMotdData()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.motd.save_failed"));
         return 0;
      } else {
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.motd.cleared"), false);
         return 1;
      }
   }

   private static int reloadMotd(CommandSourceStack source) {
      loadMotdData();
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.motd.reloaded"), false);
      return 1;
   }

   private static int broadcastMotd(CommandSourceStack source) {
      if (currentMotd.isEmpty()) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.motd.no_motd_to_broadcast"));
         return 0;
      } else {
         List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
         if (players.isEmpty()) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.motd.no_players_online"));
            return 0;
         } else {
            String formattedMotd = currentMotd.replace("&", "§");
            Component motdComponent = Component.literal(formattedMotd);
            int sentCount = 0;

            for (ServerPlayer player : players) {
               PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(player.createCommandSourceStack(), "neoessentials.motd");
               if (permResult.hasPermission()) {
                  player.sendSystemMessage(MessageUtil.success("commands.neoessentials.motd.broadcast_header"));
                  player.sendSystemMessage(motdComponent);
                  if (!motdTimestamp.isEmpty()) {
                     player.sendSystemMessage(MessageUtil.info("commands.neoessentials.motd.footer", motdAuthor, motdTimestamp));
                  }

                  sentCount++;
               }
            }

            int finalSentCount = sentCount;
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.motd.broadcasted", finalSentCount), false);
            return 1;
         }
      }
   }

   private static void loadMotdData() {
      try {
         if (!Files.exists(MOTD_DATA_FILE)) {
            Files.createDirectories(MOTD_DATA_FILE.getParent());
            return;
         }

         String json = Files.readString(MOTD_DATA_FILE);
         JsonObject data = JsonParser.parseString(json).getAsJsonObject();
         currentMotd = data.has("motd") ? data.get("motd").getAsString() : "";
         motdAuthor = data.has("author") ? data.get("author").getAsString() : "Server";
         motdTimestamp = data.has("timestamp") ? data.get("timestamp").getAsString() : "";
      } catch (Exception var2) {
         LOGGER.error("Failed to load MOTD data, keeping current in-memory state: {}", var2.getMessage());
      }
   }

   private static boolean saveMotdData() {
      try {
         JsonObject data = new JsonObject();
         data.addProperty("motd", currentMotd);
         data.addProperty("author", motdAuthor);
         data.addProperty("timestamp", motdTimestamp);
         Files.createDirectories(MOTD_DATA_FILE.getParent());
         Files.writeString(MOTD_DATA_FILE, GSON.toJson(data));
         return true;
      } catch (Exception var1) {
         LOGGER.error("Failed to save MOTD data: {}", var1.getMessage());
         return false;
      }
   }

   public static String getCurrentMotd() {
      return currentMotd;
   }

   public static boolean hasMotd() {
      return !currentMotd.isEmpty();
   }

   public static void showMotdToPlayer(ServerPlayer player) {
      if (hasMotd()) {
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.motd.join_header"));
         String formattedMotd = currentMotd.replace("&", "§");
         Component motdComponent = Component.literal(formattedMotd);
         player.sendSystemMessage(motdComponent);
         if (!motdTimestamp.isEmpty()) {
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.motd.footer", motdAuthor, motdTimestamp));
         }
      }
   }
}
