package com.zerog.neoessentials.util.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class NickCommand {
   private static final Map<UUID, String> NICKNAMES = new ConcurrentHashMap<>();
   private static final Path NICK_DATA_FILE = Paths.get("config", "neoessentials", "nickname_data.json");
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final Pattern VALID_NICK_PATTERN = Pattern.compile("^[a-zA-Z0-9_&§#]{1,32}$");
   private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]|&#[0-9a-fA-F]{6}");

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("nick")) {
         loadNicknameData();
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("nick")
                        .then(
                           Commands.argument("nickname", StringArgumentType.greedyString())
                              .executes(
                                 ctx -> {
                                    ServerPlayer player = CommandSourceHelper.requirePlayer(
                                       (CommandSourceStack)ctx.getSource(), "commands.neoessentials.nick.player_only"
                                    );
                                    if (player == null) {
                                       return 0;
                                    } else {
                                       PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                          (CommandSourceStack)ctx.getSource(), "neoessentials.nick"
                                       );
                                       if (!permResult.hasPermission()) {
                                          ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                          return 0;
                                       } else {
                                          String nickname = StringArgumentType.getString(ctx, "nickname");
                                          return setNickname(player, nickname);
                                       }
                                    }
                                 }
                              )
                        ))
                     .then(
                        Commands.literal("reset")
                           .executes(
                              ctx -> {
                                 ServerPlayer player = CommandSourceHelper.requirePlayer(
                                    (CommandSourceStack)ctx.getSource(), "commands.neoessentials.nick.player_only"
                                 );
                                 if (player == null) {
                                    return 0;
                                 } else {
                                    PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                       (CommandSourceStack)ctx.getSource(), "neoessentials.nick"
                                    );
                                    if (!permResult.hasPermission()) {
                                       ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                       return 0;
                                    } else {
                                       return resetNickname(player);
                                    }
                                 }
                              }
                           )
                     ))
                  .then(
                     Commands.literal("off")
                        .executes(
                           ctx -> {
                              ServerPlayer player = CommandSourceHelper.requirePlayer(
                                 (CommandSourceStack)ctx.getSource(), "commands.neoessentials.nick.player_only"
                              );
                              if (player == null) {
                                 return 0;
                              } else {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.nick"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    return resetNickname(player);
                                 }
                              }
                           }
                        )
                  ))
               .executes(
                  ctx -> {
                     ServerPlayer player = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.nick.player_only");
                     if (player == null) {
                        return 0;
                     } else {
                        PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                           (CommandSourceStack)ctx.getSource(), "neoessentials.nick"
                        );
                        if (!permResult.hasPermission()) {
                           ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                           return 0;
                        } else {
                           return showCurrentNickname(player);
                        }
                     }
                  }
               )
         );
         dispatcher.register(
            (LiteralArgumentBuilder)Commands.literal("setnick")
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("player", StringArgumentType.word())
                        .then(
                           Commands.argument("nickname", StringArgumentType.greedyString())
                              .executes(
                                 ctx -> {
                                    PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                       (CommandSourceStack)ctx.getSource(), "neoessentials.nick.others"
                                    );
                                    if (!permResult.hasPermission()) {
                                       ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                       return 0;
                                    } else {
                                       String playerName = StringArgumentType.getString(ctx, "player");
                                       String nickname = StringArgumentType.getString(ctx, "nickname");
                                       return setOtherPlayerNickname((CommandSourceStack)ctx.getSource(), playerName, nickname);
                                    }
                                 }
                              )
                        ))
                     .then(
                        Commands.literal("reset")
                           .executes(
                              ctx -> {
                                 PermissionValidator.PermissionResult permResult = PermissionValidator.validatePermission(
                                    (CommandSourceStack)ctx.getSource(), "neoessentials.nick.others"
                                 );
                                 if (!permResult.hasPermission()) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                    return 0;
                                 } else {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    return resetOtherPlayerNickname((CommandSourceStack)ctx.getSource(), playerName);
                                 }
                              }
                           )
                     )
               )
         );
      }
   }

   private static int setNickname(ServerPlayer player, String nickname) {
      if (nickname.equalsIgnoreCase("off") || nickname.equalsIgnoreCase("reset")) {
         return resetNickname(player);
      } else if (!isValidNickname(nickname)) {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.nick.invalid_format"));
         return 0;
      } else {
         String withoutColors = removeColorCodes(nickname);
         if (withoutColors.length() > 16) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.nick.too_long"));
            return 0;
         } else if (withoutColors.length() < 3) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.nick.too_short"));
            return 0;
         } else if (hasColorCodes(nickname)
            && !PermissionValidator.validatePermission(player.createCommandSourceStack(), "neoessentials.nick.color").hasPermission()) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.nick.no_color_permission"));
            return 0;
         } else if (isNicknameTaken(nickname, player.getUUID())) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.nick.already_taken"));
            return 0;
         } else {
            NICKNAMES.put(player.getUUID(), nickname);
            saveNicknameData();
            updatePlayerDisplayName(player);
            String formattedNick = nickname.replace("&", "§");
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.nick.set", formattedNick));
            return 1;
         }
      }
   }

   private static int resetNickname(ServerPlayer player) {
      if (!NICKNAMES.containsKey(player.getUUID())) {
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.nick.no_nickname"));
         return 0;
      } else {
         NICKNAMES.remove(player.getUUID());
         saveNicknameData();
         updatePlayerDisplayName(player);
         player.sendSystemMessage(MessageUtil.success("commands.neoessentials.nick.reset"));
         return 1;
      }
   }

   private static int showCurrentNickname(ServerPlayer player) {
      String nickname = NICKNAMES.get(player.getUUID());
      if (nickname == null) {
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.nick.no_nickname"));
      } else {
         String formattedNick = nickname.replace("&", "§");
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.nick.current", formattedNick));
      }

      return 1;
   }

   private static int setOtherPlayerNickname(CommandSourceStack source, String playerName, String nickname) {
      ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
      if (target == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.nick.player_not_found", playerName));
         return 0;
      } else if (nickname.equalsIgnoreCase("off") || nickname.equalsIgnoreCase("reset")) {
         return resetOtherPlayerNickname(source, playerName);
      } else if (!isValidNickname(nickname)) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.nick.invalid_format"));
         return 0;
      } else {
         String withoutColors = removeColorCodes(nickname);
         if (withoutColors.length() > 16 || withoutColors.length() < 3) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.nick.invalid_length"));
            return 0;
         } else if (isNicknameTaken(nickname, target.getUUID())) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.nick.already_taken"));
            return 0;
         } else {
            NICKNAMES.put(target.getUUID(), nickname);
            saveNicknameData();
            updatePlayerDisplayName(target);
            String formattedNick = nickname.replace("&", "§");
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.nick.set_other", target.getName().getString(), formattedNick), false);
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.nick.set_by_admin", formattedNick));
            return 1;
         }
      }
   }

   private static int resetOtherPlayerNickname(CommandSourceStack source, String playerName) {
      ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
      if (target == null) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.nick.player_not_found", playerName));
         return 0;
      } else if (!NICKNAMES.containsKey(target.getUUID())) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.nick.player_no_nickname", playerName));
         return 0;
      } else {
         NICKNAMES.remove(target.getUUID());
         saveNicknameData();
         updatePlayerDisplayName(target);
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.nick.reset_other", target.getName().getString()), false);
         target.sendSystemMessage(MessageUtil.info("commands.neoessentials.nick.reset_by_admin"));
         return 1;
      }
   }

   private static void updatePlayerDisplayName(ServerPlayer player) {
      String nickname = NICKNAMES.get(player.getUUID());
      if (nickname != null) {
         String formattedNick = nickname.replace("&", "§");
         player.setCustomName(MessageUtil.coloredText(formattedNick));
         player.setCustomNameVisible(true);
      } else {
         player.setCustomName(null);
         player.setCustomNameVisible(false);
      }
   }

   private static boolean isValidNickname(String nickname) {
      return VALID_NICK_PATTERN.matcher(nickname).matches();
   }

   private static boolean hasColorCodes(String nickname) {
      return COLOR_CODE_PATTERN.matcher(nickname).find();
   }

   private static String removeColorCodes(String nickname) {
      return COLOR_CODE_PATTERN.matcher(nickname).replaceAll("");
   }

   private static boolean isNicknameTaken(String nickname, UUID excludePlayer) {
      String cleanNickname = removeColorCodes(nickname).toLowerCase();
      return NICKNAMES.entrySet()
         .stream()
         .filter(entry -> !entry.getKey().equals(excludePlayer))
         .anyMatch(entry -> removeColorCodes(entry.getValue()).toLowerCase().equals(cleanNickname));
   }

   public static String getNickname(UUID playerId) {
      return NICKNAMES.get(playerId);
   }

   public static String getDisplayName(ServerPlayer player) {
      String nickname = NICKNAMES.get(player.getUUID());
      return nickname != null ? nickname.replace("&", "§") : player.getName().getString();
   }

   private static void loadNicknameData() {
      try {
         if (!Files.exists(NICK_DATA_FILE)) {
            Files.createDirectories(NICK_DATA_FILE.getParent());
            return;
         }

         String json = Files.readString(NICK_DATA_FILE);
         JsonObject data = JsonParser.parseString(json).getAsJsonObject();

         for (Entry<String, JsonElement> entry : data.entrySet()) {
            try {
               UUID playerId = UUID.fromString(entry.getKey());
               String nickname = entry.getValue().getAsString();
               NICKNAMES.put(playerId, nickname);
            } catch (Exception var6) {
            }
         }
      } catch (Exception var7) {
         System.err.println("Failed to load nickname data: " + var7.getMessage());
      }
   }

   private static void saveNicknameData() {
      try {
         JsonObject data = new JsonObject();

         for (Entry<UUID, String> entry : NICKNAMES.entrySet()) {
            data.addProperty(entry.getKey().toString(), entry.getValue());
         }

         Files.createDirectories(NICK_DATA_FILE.getParent());
         Files.writeString(NICK_DATA_FILE, GSON.toJson(data));
      } catch (Exception var3) {
         System.err.println("Failed to save nickname data: " + var3.getMessage());
      }
   }

   public static void applyNicknamesToOnlinePlayers(MinecraftServer server) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         updatePlayerDisplayName(player);
      }
   }
}
