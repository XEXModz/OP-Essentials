package com.zerog.neoessentials.util.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.zerog.neoessentials.chat.IgnoreManager;
import com.zerog.neoessentials.chat.MuteManager;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.util.CommandSourceHelper;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.PermissionValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(MailCommand.class);
   private static final Map<UUID, List<MailCommand.MailMessage>> MAIL_BOX = new ConcurrentHashMap<>();
   private static final Path MAIL_DATA_FILE = Paths.get("config", "neoessentials", "mail_data.json");
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
   private static final AtomicInteger mailsThisMinute = new AtomicInteger(0);
   private static final AtomicLong rateLimitWindowStart = new AtomicLong(0L);
   private static final int DEFAULT_MAILS_PER_MINUTE = 10;
   private static final int MAX_MESSAGE_LENGTH = 1000;
   private static final int MAX_MAILBOX_SIZE = 100;
   private static final int ITEMS_PER_PAGE = 9;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (ConfigManager.getInstance().isCommandEnabled("mail")) {
         loadMailData();
         dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "mail"
                                       )
                                       .executes(ctx -> {
                                          ServerPlayer player = CommandSourceHelper.requirePlayer(
                                             (CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only"
                                          );
                                          if (player == null) {
                                             return 0;
                                          } else {
                                             return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail") ? showMailStatus(player) : 0;
                                          }
                                       }))
                                    .then(
                                       ((LiteralArgumentBuilder)Commands.literal("read").executes(ctx -> {
                                             ServerPlayer p = CommandSourceHelper.requirePlayer(
                                                (CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only"
                                             );
                                             if (p == null) {
                                                return 0;
                                             } else {
                                                return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail") ? readMail(p, 1) : 0;
                                             }
                                          }))
                                          .then(
                                             Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(
                                                   ctx -> {
                                                      ServerPlayer p = CommandSourceHelper.requirePlayer(
                                                         (CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only"
                                                      );
                                                      if (p == null) {
                                                         return 0;
                                                      } else {
                                                         return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail")
                                                            ? readMail(p, IntegerArgumentType.getInteger(ctx, "page"))
                                                            : 0;
                                                      }
                                                   }
                                                )
                                          )
                                    ))
                                 .then(
                                    Commands.literal("send")
                                       .then(
                                          Commands.argument("player", StringArgumentType.word())
                                             .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                                                if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.send")) {
                                                   return 0;
                                                } else {
                                                   String targetName = StringArgumentType.getString(ctx, "player");
                                                   String message = StringArgumentType.getString(ctx, "message");
                                                   ServerPlayer sender = ((CommandSourceStack)ctx.getSource()).getPlayer();
                                                   return sendMail((CommandSourceStack)ctx.getSource(), sender, targetName, message, 0L);
                                                }
                                             }))
                                       )
                                 ))
                              .then(
                                 Commands.literal("sendtemp")
                                    .then(
                                       Commands.argument("player", StringArgumentType.word())
                                          .then(
                                             Commands.argument("duration", StringArgumentType.word())
                                                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                                                   if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.sendtemp")) {
                                                      return 0;
                                                   } else {
                                                      String targetName = StringArgumentType.getString(ctx, "player");
                                                      String duration = StringArgumentType.getString(ctx, "duration");
                                                      String message = StringArgumentType.getString(ctx, "message");
                                                      ServerPlayer sender = ((CommandSourceStack)ctx.getSource()).getPlayer();
                                                      long expireAt = parseDuration(duration);
                                                      if (expireAt < 0L) {
                                                         ((CommandSourceStack)ctx.getSource())
                                                            .sendFailure(MessageUtil.error("commands.neoessentials.mail.invalid_duration", duration));
                                                         return 0;
                                                      } else {
                                                         return sendMail(
                                                            (CommandSourceStack)ctx.getSource(),
                                                            sender,
                                                            targetName,
                                                            message,
                                                            System.currentTimeMillis() + expireAt
                                                         );
                                                      }
                                                   }
                                                }))
                                          )
                                    )
                              ))
                           .then(
                              Commands.literal("sendall")
                                 .then(
                                    Commands.argument("message", StringArgumentType.greedyString())
                                       .executes(
                                          ctx -> {
                                             if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.sendall")) {
                                                return 0;
                                             } else {
                                                String message = StringArgumentType.getString(ctx, "message");
                                                String senderName = ((CommandSourceStack)ctx.getSource()).getPlayer() != null
                                                   ? ((CommandSourceStack)ctx.getSource()).getPlayer().getName().getString()
                                                   : "Console";
                                                String senderUuid = ((CommandSourceStack)ctx.getSource()).getPlayer() != null
                                                   ? ((CommandSourceStack)ctx.getSource()).getPlayer().getUUID().toString()
                                                   : null;
                                                return sendMailAll((CommandSourceStack)ctx.getSource(), senderName, senderUuid, message, 0L);
                                             }
                                          }
                                       )
                                 )
                           ))
                        .then(
                           Commands.literal("sendtempall")
                              .then(
                                 Commands.argument("duration", StringArgumentType.word())
                                    .then(
                                       Commands.argument("message", StringArgumentType.greedyString())
                                          .executes(
                                             ctx -> {
                                                if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.sendtempall")) {
                                                   return 0;
                                                } else {
                                                   String duration = StringArgumentType.getString(ctx, "duration");
                                                   String message = StringArgumentType.getString(ctx, "message");
                                                   long expireAt = parseDuration(duration);
                                                   if (expireAt < 0L) {
                                                      ((CommandSourceStack)ctx.getSource())
                                                         .sendFailure(MessageUtil.error("commands.neoessentials.mail.invalid_duration", duration));
                                                      return 0;
                                                   } else {
                                                      String senderName = ((CommandSourceStack)ctx.getSource()).getPlayer() != null
                                                         ? ((CommandSourceStack)ctx.getSource()).getPlayer().getName().getString()
                                                         : "Console";
                                                      String senderUuid = ((CommandSourceStack)ctx.getSource()).getPlayer() != null
                                                         ? ((CommandSourceStack)ctx.getSource()).getPlayer().getUUID().toString()
                                                         : null;
                                                      return sendMailAll(
                                                         (CommandSourceStack)ctx.getSource(),
                                                         senderName,
                                                         senderUuid,
                                                         message,
                                                         System.currentTimeMillis() + expireAt
                                                      );
                                                   }
                                                }
                                             }
                                          )
                                    )
                              )
                        ))
                     .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayer p = CommandSourceHelper.requirePlayer((CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only");
                        if (p == null) {
                           return 0;
                        } else {
                           return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail")
                              ? deleteMail(p, StringArgumentType.getString(ctx, "id"))
                              : 0;
                        }
                     }))))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("clear")
                           .executes(
                              ctx -> {
                                 ServerPlayer p = CommandSourceHelper.requirePlayer(
                                    (CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only"
                                 );
                                 if (p == null) {
                                    return 0;
                                 } else {
                                    return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.clear")
                                       ? clearMail((CommandSourceStack)ctx.getSource(), p, -1)
                                       : 0;
                                 }
                              }
                           ))
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("indexOrPlayer", StringArgumentType.word())
                                 .executes(
                                    ctx -> {
                                       String arg = StringArgumentType.getString(ctx, "indexOrPlayer");
                                       if (isPositiveInt(arg)) {
                                          ServerPlayer p = CommandSourceHelper.requirePlayer(
                                             (CommandSourceStack)ctx.getSource(), "commands.neoessentials.mail.player_only"
                                          );
                                          if (p == null) {
                                             return 0;
                                          } else {
                                             return checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.clear")
                                                ? clearMail((CommandSourceStack)ctx.getSource(), p, Integer.parseInt(arg))
                                                : 0;
                                          }
                                       } else if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.clear.others")) {
                                          return 0;
                                       } else {
                                          ServerPlayer target = ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayerByName(arg);
                                          if (target == null) {
                                             ((CommandSourceStack)ctx.getSource())
                                                .sendFailure(MessageUtil.error("commands.neoessentials.mail.player_not_found", arg));
                                             return 0;
                                          } else {
                                             return clearMail((CommandSourceStack)ctx.getSource(), target, -1);
                                          }
                                       }
                                    }
                                 ))
                              .then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(ctx -> {
                                 if (!checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.clear.others")) {
                                    return 0;
                                 } else {
                                    String playerName = StringArgumentType.getString(ctx, "indexOrPlayer");
                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                    ServerPlayer target = ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayerByName(playerName);
                                    if (target == null) {
                                       ((CommandSourceStack)ctx.getSource())
                                          .sendFailure(MessageUtil.error("commands.neoessentials.mail.player_not_found", playerName));
                                       return 0;
                                    } else {
                                       return clearMail((CommandSourceStack)ctx.getSource(), target, index);
                                    }
                                 }
                              }))
                        )
                  ))
               .then(
                  Commands.literal("clearall")
                     .executes(
                        ctx -> !checkPerm((CommandSourceStack)ctx.getSource(), "neoessentials.mail.clearall")
                              ? 0
                              : clearAll((CommandSourceStack)ctx.getSource())
                     )
               )
         );
      }
   }

   private static int showMailStatus(ServerPlayer player) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(player.getUUID());
      if (msgs != null && !msgs.isEmpty()) {
         long unread = msgs.stream().filter(m -> !m.read && !m.isExpired()).count();
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.mail.status", msgs.size(), unread));
         player.sendSystemMessage(Component.literal("§7Use §f/mail read §7to read, §f/mail send <player> <msg> §7to send"));
         return 1;
      } else {
         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.mail.no_mail"));
         return 1;
      }
   }

   private static int readMail(ServerPlayer player, int page) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
      boolean removed = msgs.removeIf(MailCommand.MailMessage::isExpired);
      if (msgs.isEmpty()) {
         if (removed) {
            saveMailData();
         }

         player.sendSystemMessage(MessageUtil.info("commands.neoessentials.mail.no_mail"));
         return 1;
      } else {
         int totalPages = (int)Math.ceil((double)msgs.size() / 9.0);
         if (page > totalPages) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.mail.invalid_page", page, totalPages));
            return 0;
         } else {
            int start = (page - 1) * 9;
            int end = Math.min(start + 9, msgs.size());
            player.sendSystemMessage(Component.literal("§6══════ §eMail §7(§f" + page + "§7/§f" + totalPages + "§7) §6══════"));

            for (int i = start; i < end; i++) {
               MailCommand.MailMessage mail = msgs.get(i);
               int displayIndex = i + 1;
               boolean wasUnread = !mail.read;
               mail.read = true;
               String unreadMarker = wasUnread ? "§e● " : "";
               String expireInfo = mail.timeExpire > 0L ? " §7[expires: §f" + mail.formattedExpiry() + "§7]" : "";
               MutableComponent line = Component.literal(
                  String.format("§7[§f%d§7] %s§f%s§7: %s%s", displayIndex, unreadMarker, mail.senderName, mail.message, expireInfo)
               );
               MutableComponent hover = Component.literal("§6Sent: §f" + mail.formattedTime() + "\n")
                  .append(Component.literal("§6ID: §f" + mail.id + "\n"))
                  .append(Component.literal("§6From: §f" + mail.senderName + "\n"))
                  .append(Component.literal("§7Click to delete this message"));
               line = line.withStyle(
                  s -> s.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, hover))
                        .withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/mail delete " + mail.id))
               );
               player.sendSystemMessage(line);
            }

            if (totalPages > 1) {
               MutableComponent footer = Component.literal("§7");
               if (page > 1) {
                  footer.append(
                     Component.literal("§7[§a◀ Prev§7] ")
                        .withStyle(s -> s.withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/mail read " + (page - 1))))
                  );
               }

               footer.append(Component.literal("§7Page §f" + page + "§7/§f" + totalPages));
               if (page < totalPages) {
                  footer.append(
                     Component.literal(" §7[§aNext ▶§7]")
                        .withStyle(s -> s.withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/mail read " + (page + 1))))
                  );
               }

               player.sendSystemMessage(footer);
            }

            player.sendSystemMessage(Component.literal("§7Use §f/mail clear §7to clear all mail."));
            if (removed) {
               saveMailData();
            } else {
               saveMailData();
            }

            return 1;
         }
      }
   }

   private static int sendMail(CommandSourceStack source, ServerPlayer sender, String targetName, String message, long expireAt) {
      if (sender != null && MuteManager.isMuted(sender)) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mail.muted"));
         return 0;
      } else if (message.length() > 1000) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mail.message_too_long", 1000));
         return 0;
      } else {
         long now = System.currentTimeMillis();
         if (now - rateLimitWindowStart.get() > 60000L) {
            rateLimitWindowStart.set(now);
            mailsThisMinute.set(0);
         }

         int limit = getMailsPerMinute();
         if (mailsThisMinute.incrementAndGet() > limit) {
            source.sendFailure(MessageUtil.error("commands.neoessentials.mail.rate_limit", limit));
            return 0;
         } else {
            UUID targetUUID = getPlayerUUID(source.getServer(), targetName);
            if (targetUUID == null) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mail.player_not_found", targetName));
               return 0;
            } else if (sender != null && targetUUID.equals(sender.getUUID())) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mail.cannot_mail_self"));
               return 0;
            } else {
               if (sender != null) {
                  ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(targetUUID);
                  if (onlineTarget != null && IgnoreManager.isIgnoring(onlineTarget, sender)) {
                     source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.sent", targetName), false);
                     return 1;
                  }
               }

               String senderName = sender != null ? sender.getName().getString() : "Console";
               String senderUuid = sender != null ? sender.getUUID().toString() : null;
               MailCommand.MailMessage mail = new MailCommand.MailMessage(senderName, senderUuid, message, expireAt);
               List<MailCommand.MailMessage> box = MAIL_BOX.computeIfAbsent(targetUUID, k -> new ArrayList<>());
               box.add(0, mail);

               while (box.size() > 100) {
                  box.remove(box.size() - 1);
               }

               saveMailData();
               source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.sent", targetName), false);
               ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(targetUUID);
               if (onlineTarget != null) {
                  onlineTarget.sendSystemMessage(MessageUtil.info("commands.neoessentials.mail.received", senderName));
               }

               return 1;
            }
         }
      }
   }

   private static int sendMailAll(CommandSourceStack source, String senderName, String senderUuid, String message, long expireAt) {
      if (message.length() > 1000) {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mail.message_too_long", 1000));
         return 0;
      } else {
         Thread t = new Thread(() -> {
            source.getServer().getPlayerList().getPlayers().forEach(p -> {
               MailCommand.MailMessage mail = new MailCommand.MailMessage(senderName, senderUuid, message, expireAt);
               MAIL_BOX.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(0, mail);
            });
            saveMailData();
            LOGGER.info("sendall from {} completed: {} players", senderName, source.getServer().getPlayerList().getPlayerCount());
         }, "NeoEssentials-MailSendAll");
         t.setDaemon(true);
         t.start();
         source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.sent_all"), false);
         return 1;
      }
   }

   private static int deleteMail(ServerPlayer player, String id) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(player.getUUID());
      if (msgs != null && !msgs.isEmpty()) {
         boolean removed = msgs.removeIf(m -> m.id.equals(id));
         if (!removed) {
            player.sendSystemMessage(MessageUtil.error("commands.neoessentials.mail.invalid_id", id));
            return 0;
         } else {
            if (msgs.isEmpty()) {
               MAIL_BOX.remove(player.getUUID());
            }

            saveMailData();
            player.sendSystemMessage(MessageUtil.success("commands.neoessentials.mail.deleted", id));
            return 1;
         }
      } else {
         player.sendSystemMessage(MessageUtil.error("commands.neoessentials.mail.no_mail"));
         return 0;
      }
   }

   private static int clearMail(CommandSourceStack source, ServerPlayer target, int index) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(target.getUUID());
      if (msgs != null && !msgs.isEmpty()) {
         if (index > 0) {
            if (index > msgs.size()) {
               source.sendFailure(MessageUtil.error("commands.neoessentials.mail.invalid_index", msgs.size()));
               return 0;
            }

            msgs.remove(index - 1);
            if (msgs.isEmpty()) {
               MAIL_BOX.remove(target.getUUID());
            }

            saveMailData();
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.deleted_index", index), false);
         } else {
            int count = msgs.size();
            MAIL_BOX.remove(target.getUUID());
            saveMailData();
            source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.cleared", count), false);
         }

         return 1;
      } else {
         source.sendFailure(MessageUtil.error("commands.neoessentials.mail.no_mail"));
         return 0;
      }
   }

   private static int clearAll(CommandSourceStack source) {
      int count = MAIL_BOX.size();
      MAIL_BOX.clear();
      saveMailData();
      source.sendSuccess(() -> MessageUtil.success("commands.neoessentials.mail.cleared_all", count), false);
      LOGGER.info("[Mail] clearall executed by {}", source.getTextName());
      return 1;
   }

   public static void notifyOnLogin(ServerPlayer player) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(player.getUUID());
      if (msgs != null) {
         long unread = msgs.stream().filter(m -> !m.read && !m.isExpired()).count();
         if (unread > 0L) {
            player.sendSystemMessage(MessageUtil.info("commands.neoessentials.mail.login_notification", unread));
         }
      }
   }

   public static boolean hasUnreadMail(UUID playerId) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(playerId);
      return msgs != null && msgs.stream().anyMatch(m -> !m.read && !m.isExpired());
   }

   public static int getUnreadMailCount(UUID playerId) {
      List<MailCommand.MailMessage> msgs = MAIL_BOX.get(playerId);
      return msgs == null ? 0 : (int)msgs.stream().filter(m -> !m.read && !m.isExpired()).count();
   }

   private static boolean checkPerm(CommandSourceStack source, String node) {
      PermissionValidator.PermissionResult r = PermissionValidator.validatePermission(source, node);
      if (!r.hasPermission()) {
         source.sendFailure(MessageUtil.error(r.getErrorMessage()));
         return false;
      } else {
         return true;
      }
   }

   private static boolean isPositiveInt(String s) {
      try {
         return Integer.parseInt(s) > 0;
      } catch (NumberFormatException var2) {
         return false;
      }
   }

   public static long parseDuration(String input) {
      if (input != null && !input.isBlank()) {
         try {
            String s = input.trim().toLowerCase();
            long multiplier = 1000L;
            if (s.endsWith("w")) {
               multiplier = 604800000L;
               s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("d")) {
               multiplier = 86400000L;
               s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("h")) {
               multiplier = 3600000L;
               s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("m")) {
               multiplier = 60000L;
               s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("s")) {
               s = s.substring(0, s.length() - 1);
            }

            return Long.parseLong(s) * multiplier;
         } catch (Exception var4) {
            return -1L;
         }
      } else {
         return -1L;
      }
   }

   private static int getMailsPerMinute() {
      try {
         JsonObject cfg = ConfigManager.getInstance().getConfig("config.json");
         if (cfg.has("mail") && cfg.getAsJsonObject("mail").has("mailsPerMinute")) {
            return cfg.getAsJsonObject("mail").get("mailsPerMinute").getAsInt();
         }
      } catch (Exception var1) {
      }

      return 10;
   }

   private static UUID getPlayerUUID(MinecraftServer server, String name) {
      ServerPlayer online = server.getPlayerList().getPlayerByName(name);
      if (online != null) {
         return online.getUUID();
      } else {
         try {
            GameProfile p = (GameProfile)server.getProfileCache().get(name).orElse(null);
            if (p != null) {
               return p.getId();
            }
         } catch (Exception var4) {
         }

         return null;
      }
   }

   public static void loadMailData() {
      try {
         if (!Files.exists(MAIL_DATA_FILE)) {
            Files.createDirectories(MAIL_DATA_FILE.getParent());
            return;
         }

         String json = Files.readString(MAIL_DATA_FILE);
         JsonObject data = JsonParser.parseString(json).getAsJsonObject();

         for (Entry<String, JsonElement> entry : data.entrySet()) {
            try {
               UUID uuid = UUID.fromString(entry.getKey());
               JsonArray arr = entry.getValue().getAsJsonArray();
               List<MailCommand.MailMessage> msgs = new ArrayList<>();

               for (JsonElement el : arr) {
                  JsonObject o = el.getAsJsonObject();
                  MailCommand.MailMessage m;
                  if (o.has("timeSent")) {
                     m = new MailCommand.MailMessage(
                        o.has("senderName") ? o.get("senderName").getAsString() : "unknown",
                        o.has("senderUuid") && !o.get("senderUuid").isJsonNull() ? o.get("senderUuid").getAsString() : null,
                        o.get("message").getAsString(),
                        o.has("timeExpire") ? o.get("timeExpire").getAsLong() : 0L
                     );
                     m.id = o.has("id") ? o.get("id").getAsString() : m.id;
                     m.timeSent = o.get("timeSent").getAsLong();
                     m.read = o.has("read") && o.get("read").getAsBoolean();
                     m.legacy = o.has("legacy") && o.get("legacy").getAsBoolean();
                  } else {
                     m = new MailCommand.MailMessage(o.has("sender") ? o.get("sender").getAsString() : "unknown", o.get("message").getAsString());
                     m.id = o.has("id") ? o.get("id").getAsString() : m.id;
                     m.read = o.has("read") && o.get("read").getAsBoolean();
                  }

                  msgs.add(m);
               }

               if (!msgs.isEmpty()) {
                  MAIL_BOX.put(uuid, msgs);
               }
            } catch (Exception var11) {
               LOGGER.warn("Skipped invalid mail entry for key '{}': {}", entry.getKey(), var11.getMessage());
            }
         }

         LOGGER.debug("Loaded mail data for {} players", MAIL_BOX.size());
      } catch (Exception var12) {
         LOGGER.error("Failed to load mail data: {}", var12.getMessage(), var12);
      }
   }

   private static synchronized void saveMailData() {
      try {
         JsonObject data = new JsonObject();

         for (Entry<UUID, List<MailCommand.MailMessage>> entry : MAIL_BOX.entrySet()) {
            JsonArray arr = new JsonArray();

            for (MailCommand.MailMessage m : entry.getValue()) {
               JsonObject o = new JsonObject();
               o.addProperty("id", m.id);
               o.addProperty("senderName", m.senderName);
               if (m.senderUuid != null) {
                  o.addProperty("senderUuid", m.senderUuid);
               }

               o.addProperty("message", m.message);
               o.addProperty("timeSent", m.timeSent);
               o.addProperty("timeExpire", m.timeExpire);
               o.addProperty("read", m.read);
               o.addProperty("legacy", m.legacy);
               arr.add(o);
            }

            data.add(entry.getKey().toString(), arr);
         }

         Files.createDirectories(MAIL_DATA_FILE.getParent());
         Files.writeString(MAIL_DATA_FILE, GSON.toJson(data));
      } catch (Exception var7) {
         LOGGER.error("Failed to save mail data: {}", var7.getMessage(), var7);
      }
   }

   private static class MailMessage {
      String id = UUID.randomUUID().toString().substring(0, 8);
      String senderName;
      String senderUuid;
      String message;
      long timeSent;
      long timeExpire;
      boolean read;
      boolean legacy;

      MailMessage(String senderName, String senderUuid, String message, long timeExpire) {
         this.senderName = senderName;
         this.senderUuid = senderUuid;
         this.message = message;
         this.timeSent = System.currentTimeMillis();
         this.timeExpire = timeExpire;
         this.read = false;
         this.legacy = false;
      }

      MailMessage(String senderName, String message) {
         this(senderName, null, message, 0L);
         this.legacy = true;
      }

      boolean isExpired() {
         return this.timeExpire > 0L && System.currentTimeMillis() > this.timeExpire;
      }

      String formattedTime() {
         return this.timeSent == 0L
            ? "unknown"
            : LocalDateTime.ofInstant(Instant.ofEpochMilli(this.timeSent), ZoneId.systemDefault()).format(MailCommand.TIME_FORMAT);
      }

      String formattedExpiry() {
         return this.timeExpire == 0L
            ? "never"
            : LocalDateTime.ofInstant(Instant.ofEpochMilli(this.timeExpire), ZoneId.systemDefault()).format(MailCommand.TIME_FORMAT);
      }
   }
}
