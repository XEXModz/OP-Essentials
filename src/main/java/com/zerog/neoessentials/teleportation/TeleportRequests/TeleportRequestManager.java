package com.zerog.neoessentials.teleportation.TeleportRequests;

import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.teleportation.TeleportLocation;
import com.zerog.neoessentials.teleportation.TeleportUtil;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportCommands;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.commands.ItemCustomisationCommands;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeleportRequestManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(TeleportRequestManager.class);
   private final Map<UUID, TeleportRequest> pendingRequests = new ConcurrentHashMap<>();
   private final Map<UUID, TeleportRequest> sentRequests = new ConcurrentHashMap<>();
   private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
      Thread t = new Thread(r, "TeleportRequest-Scheduler");
      t.setDaemon(true);
      return t;
   });
   private int requestTimeoutSeconds;
   private int teleportDelay = 3;
   private boolean allowTpaHere = true;
   private boolean allowTpaAll = true;
   private int maxPendingRequests;
   private final int cooldownBetweenRequestsSeconds;
   private final boolean allowMultipleRequests;
   private final boolean enableRequestNotifications;
   private final boolean autoAcceptFromFriends;
   private boolean enableTeleportSafety;
   private final boolean logTeleportRequests;
   private final Map<UUID, Long> lastRequestTimestamps = new ConcurrentHashMap<>();
   private final Map<UUID, Set<UUID>> friendsMap = new ConcurrentHashMap<>();

   public static TeleportRequestManager getInstance() {
      return TeleportRequestManager.SingletonHolder.INSTANCE;
   }

   private TeleportRequestManager() {
      this.requestTimeoutSeconds = ConfigManager.getInstance().getTeleportRequestTimeoutSeconds();
      this.maxPendingRequests = ConfigManager.getInstance().getMaxPendingTeleportRequests();
      this.cooldownBetweenRequestsSeconds = ConfigManager.getInstance().getCooldownBetweenTeleportRequestsSeconds();
      this.allowMultipleRequests = ConfigManager.getInstance().isAllowMultipleTeleportRequestsEnabled();
      this.enableRequestNotifications = ConfigManager.getInstance().isTeleportRequestNotificationsEnabled();
      this.autoAcceptFromFriends = ConfigManager.getInstance().isAutoAcceptTeleportFromFriendsEnabled();
      this.logTeleportRequests = ConfigManager.getInstance().isLogTeleportRequestsEnabled();
      this.enableTeleportSafety = false;

      try {
         ConfigManager configManager = ConfigManager.getInstance();
         if (configManager != null) {
            JsonObject config = configManager.getConfig("config.json");
            if (config.has("teleportation")) {
               JsonObject tp = config.getAsJsonObject("teleportation");
               if (tp.has("teleportRequestSettings")) {
                  JsonObject req = tp.getAsJsonObject("teleportRequestSettings");
                  if (req.has("enableTeleportSafety")) {
                     this.enableTeleportSafety = req.get("enableTeleportSafety").getAsBoolean();
                  }
               }
            }
         }
      } catch (Exception var5) {
         LOGGER.warn("Failed to load teleport request safety config, defaulting to disabled: {}", var5.getMessage());
      }

      this.scheduler.scheduleAtFixedRate(this::cleanupExpiredRequests, 30L, 30L, TimeUnit.SECONDS);
   }

   public boolean sendTeleportRequest(ServerPlayer requester, ServerPlayer target, TeleportRequestType type) {
      UUID requesterId = requester.getUUID();
      UUID targetId = target.getUUID();
      if (this.cooldownBetweenRequestsSeconds > 0) {
         long now = System.currentTimeMillis();
         Long last = this.lastRequestTimestamps.putIfAbsent(requesterId, now);
         if (last != null) {
            if (now - last < (long)this.cooldownBetweenRequestsSeconds * 1000L) {
               long wait = ((long)this.cooldownBetweenRequestsSeconds * 1000L - (now - last)) / 1000L + 1L;
               requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.cooldown", wait));
               return false;
            }

            this.lastRequestTimestamps.put(requesterId, now);
         }
      }

      if (this.sentRequests.containsKey(requesterId)) {
         requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.already_sent"));
         return false;
      } else {
         if (!this.allowMultipleRequests) {
            boolean alreadyRequested = this.pendingRequests
               .values()
               .stream()
               .anyMatch(req -> req != null && req.getRequesterId().equals(requesterId) && req.getTargetId().equals(targetId));
            if (alreadyRequested) {
               requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.duplicate", target.getName().getString()));
               return false;
            }
         }

         if (!ItemCustomisationCommands.isTpToggleAllowed(targetId) && !PermissionAPI.hasPermission(requesterId, "neoessentials.teleport.tpo")) {
            requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.tptoggle_off", target.getName().getString()));
            return false;
         } else if (MiscTeleportCommands.isTpAutoEnabled(targetId)) {
            this.executeTeleportRequest(requester, target, type);
            requester.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.auto_accepted", target.getName().getString()));
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.auto_accepted_target", requester.getName().getString()));
            return true;
         } else {
            long targetPendingCount = this.pendingRequests.values().stream().filter(req -> req != null && req.getTargetId().equals(targetId)).count();
            if (targetPendingCount >= (long)this.maxPendingRequests) {
               requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.target_busy", target.getName().getString()));
               return false;
            } else {
               TeleportRequest request = new TeleportRequest(
                  requesterId,
                  requester.getName().getString(),
                  targetId,
                  target.getName().getString(),
                  type,
                  System.currentTimeMillis() + (long)this.requestTimeoutSeconds * 1000L
               );
               this.sentRequests.put(requesterId, request);
               TeleportRequest existingPending = this.pendingRequests.putIfAbsent(targetId, request);
               if (existingPending != null) {
                  this.sentRequests.remove(requesterId);
                  requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.target_busy", target.getName().getString()));
                  return false;
               } else if (this.autoAcceptFromFriends && this.isFriend(target, requester)) {
                  this.cleanupRequest(request);
                  this.executeTeleportRequest(requester, target, type);
                  requester.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.auto_accepted", target.getName().getString()));
                  target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.auto_accepted_target", requester.getName().getString()));
                  if (this.logTeleportRequests) {
                     LOGGER.info("Teleport request from {} to {} auto-accepted (friends)", requester.getName().getString(), target.getName().getString());
                  }

                  return true;
               } else {
                  this.scheduler.schedule(() -> {
                     TeleportRequest currentRequest = this.pendingRequests.get(targetId);
                     if (currentRequest != null && currentRequest.equals(request)) {
                        this.timeoutRequest(request);
                     }
                  }, (long)this.requestTimeoutSeconds, TimeUnit.SECONDS);
                  String typeText = type == TeleportRequestType.TPA ? "to you" : "you to them";
                  requester.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.sent", target.getName().getString(), typeText));
                  if (this.enableRequestNotifications) {
                     target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.received", requester.getName().getString(), typeText));
                     target.sendSystemMessage(MessageUtil.component("commands.neoessentials.teleport.request.instructions"));
                     Component acceptBtn = MessageUtil.clickableCommand("[Accept]", "tpaccept", "Click to accept the teleport request");
                     Component denyBtn = MessageUtil.clickableCommand("[Deny]", "tpdeny", "Click to deny the teleport request");
                     target.sendSystemMessage(Component.literal("").append(acceptBtn).append(" ").append(denyBtn));
                  }

                  if (this.logTeleportRequests) {
                     LOGGER.info("Player {} sent {} request to {}", new Object[]{requester.getName().getString(), type, target.getName().getString()});
                  }

                  return true;
               }
            }
         }
      }
   }

   private boolean isFriend(ServerPlayer player, ServerPlayer other) {
      UUID playerId = player.getUUID();
      UUID otherId = other.getUUID();
      Set<UUID> friends = this.friendsMap.get(playerId);
      return friends != null && friends.contains(otherId);
   }

   public void addFriend(UUID playerId, UUID friendId) {
      this.friendsMap.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(friendId);
   }

   public void removeFriend(UUID playerId, UUID friendId) {
      Set<UUID> friends = this.friendsMap.get(playerId);
      if (friends != null) {
         friends.remove(friendId);
      }
   }

   public boolean acceptTeleportRequest(ServerPlayer accepter) {
      UUID accepterId = accepter.getUUID();
      TeleportRequest request = this.pendingRequests.get(accepterId);
      if (request == null) {
         accepter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.none_pending"));
         return false;
      } else if (System.currentTimeMillis() > request.getExpiryTime()) {
         this.cleanupRequest(request);
         accepter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.expired"));
         return false;
      } else {
         ServerPlayer requester = this.getPlayerById(request.getRequesterId());
         if (requester == null) {
            this.cleanupRequest(request);
            accepter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.requester_offline"));
            return false;
         } else {
            this.cleanupRequest(request);
            this.executeTeleportRequest(requester, accepter, request.getType());
            return true;
         }
      }
   }

   public boolean denyTeleportRequest(ServerPlayer denier) {
      UUID denierId = denier.getUUID();
      TeleportRequest request = this.pendingRequests.get(denierId);
      if (request == null) {
         denier.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.none_pending"));
         return false;
      } else {
         ServerPlayer requester = this.getPlayerById(request.getRequesterId());
         this.cleanupRequest(request);
         denier.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.denied_by_you", request.getRequesterName()));
         if (requester != null) {
            requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.denied_by_target", denier.getName().getString()));
         }

         if (this.logTeleportRequests) {
            LOGGER.info("Player {} denied {} request from {}", new Object[]{denier.getName().getString(), request.getType(), request.getRequesterName()});
         }

         return true;
      }
   }

   public boolean cancelTeleportRequest(ServerPlayer canceller) {
      UUID cancellerId = canceller.getUUID();
      TeleportRequest request = this.sentRequests.get(cancellerId);
      if (request == null) {
         canceller.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.none_sent"));
         return false;
      } else {
         ServerPlayer target = this.getPlayerById(request.getTargetId());
         this.cleanupRequest(request);
         canceller.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.cancelled", request.getTargetName()));
         if (target != null) {
            target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.cancelled_by_requester", canceller.getName().getString()));
         }

         if (this.logTeleportRequests) {
            LOGGER.info("Player {} cancelled {} request to {}", new Object[]{canceller.getName().getString(), request.getType(), request.getTargetName()});
         }

         return true;
      }
   }

   private void executeTeleportRequest(ServerPlayer requester, ServerPlayer target, TeleportRequestType type) {
      ServerPlayer teleporter;
      ServerPlayer destination;
      if (type == TeleportRequestType.TPA) {
         teleporter = requester;
         destination = target;
      } else {
         teleporter = target;
         destination = requester;
      }

      MiscTeleportManager.getInstance().saveBackLocation(teleporter);
      TeleportLocation targetLocation = new TeleportLocation(destination);
      if (this.enableTeleportSafety && !targetLocation.isSafe()) {
         TeleportLocation safeLocation = targetLocation.findSafeLocation();
         if (safeLocation == null) {
            teleporter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.unsafe_location", destination.getName().getString()));
            destination.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.unsafe_location_other", teleporter.getName().getString()));
            if (this.logTeleportRequests) {
               LOGGER.warn(
                  "Teleport request from {} to {} blocked: no safe location found near destination",
                  teleporter.getName().getString(),
                  destination.getName().getString()
               );
            }

            return;
         }

         teleporter.sendSystemMessage(MessageUtil.warning("commands.neoessentials.teleport.request.moved_to_safety"));
         targetLocation = safeLocation;
      }

      int delayTicks = this.teleportDelay * 20;
      TeleportUtil.teleportPlayer(teleporter, targetLocation, delayTicks, true)
         .thenAccept(
            result -> {
               if (result.isSuccess()) {
                  teleporter.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.teleported_to", destination.getName().getString()));
                  destination.sendSystemMessage(
                     MessageUtil.success("commands.neoessentials.teleport.request.player_teleported_to_you", teleporter.getName().getString())
                  );
                  if (this.logTeleportRequests) {
                     LOGGER.info(
                        "Player {} teleported to {} via {} request", new Object[]{teleporter.getName().getString(), destination.getName().getString(), type}
                     );
                  }
               } else {
                  teleporter.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.failed", result.getMessage()));
                  destination.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.failed_other", teleporter.getName().getString()));
                  if (this.logTeleportRequests) {
                     LOGGER.warn(
                        "Failed teleport request between {} and {}: {}",
                        new Object[]{teleporter.getName().getString(), destination.getName().getString(), result.getMessage()}
                     );
                  }
               }
            }
         );
   }

   private void timeoutRequest(TeleportRequest request) {
      this.cleanupRequest(request);
      ServerPlayer requester = this.getPlayerById(request.getRequesterId());
      ServerPlayer target = this.getPlayerById(request.getTargetId());
      if (requester != null) {
         requester.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.timed_out", request.getTargetName()));
      }

      if (target != null) {
         target.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.expired_received", request.getRequesterName()));
      }

      if (this.logTeleportRequests) {
         LOGGER.info("Teleport request from {} to {} timed out", request.getRequesterName(), request.getTargetName());
      }
   }

   private void cleanupRequest(TeleportRequest request) {
      this.pendingRequests.remove(request.getTargetId());
      this.sentRequests.remove(request.getRequesterId());
   }

   private void cleanupExpiredRequests() {
      long currentTime = System.currentTimeMillis();
      this.pendingRequests.values().removeIf(request -> {
         if (currentTime > request.getExpiryTime()) {
            this.sentRequests.remove(request.getRequesterId());
            return true;
         } else {
            return false;
         }
      });
   }

   private ServerPlayer getPlayerById(UUID playerId) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayers().stream().filter(player -> player.getUUID().equals(playerId)).findFirst().orElse(null);
   }

   public boolean hasPendingRequest(ServerPlayer player) {
      return this.pendingRequests.containsKey(player.getUUID());
   }

   public boolean hasSentRequest(ServerPlayer player) {
      return this.sentRequests.containsKey(player.getUUID());
   }

   public String getPendingRequestInfo(ServerPlayer player) {
      TeleportRequest request = this.pendingRequests.get(player.getUUID());
      if (request == null) {
         return MessageUtil.localize("commands.neoessentials.teleport.request.no_pending");
      } else {
         long timeLeft = (request.getExpiryTime() - System.currentTimeMillis()) / 1000L;
         String typeText = request.getType() == TeleportRequestType.TPA ? "to teleport to you" : "you to teleport to them";
         return MessageUtil.localize("teleport.request.pending_info", request.getRequesterName(), typeText, timeLeft);
      }
   }

   public int getRequestTimeoutSeconds() {
      return this.requestTimeoutSeconds;
   }

   public void setRequestTimeoutSeconds(int timeout) {
      this.requestTimeoutSeconds = Math.max(10, timeout);
   }

   public int getTeleportDelay() {
      return this.teleportDelay;
   }

   public void setTeleportDelay(int delay) {
      this.teleportDelay = Math.max(0, delay);
   }

   public boolean isAllowTpaHere() {
      return this.allowTpaHere;
   }

   public void setAllowTpaHere(boolean allow) {
      this.allowTpaHere = allow;
   }

   public boolean isAllowTpaAll() {
      return this.allowTpaAll;
   }

   public void setAllowTpaAll(boolean allow) {
      this.allowTpaAll = allow;
   }

   public int getMaxPendingRequests() {
      return this.maxPendingRequests;
   }

   public void setMaxPendingRequests(int max) {
      this.maxPendingRequests = Math.max(1, max);
   }

   public void shutdown() {
      this.scheduler.shutdown();

      try {
         if (!this.scheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.scheduler.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.scheduler.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   public String getStatistics() {
      return String.format(
         "TeleportRequest Statistics: %d pending, %d sent, timeout: %ds", this.pendingRequests.size(), this.sentRequests.size(), this.requestTimeoutSeconds
      );
   }

   public void sendTpaRequest(ServerPlayer sender, ServerPlayer target, boolean here) {
      UUID senderId = sender.getUUID();
      UUID targetId = target.getUUID();
      if (!this.pendingRequests.containsKey(senderId) && !this.sentRequests.containsKey(senderId)) {
         long targetPendingCount = this.pendingRequests.values().stream().filter(req -> req != null && req.getTargetId().equals(targetId)).count();
         if (targetPendingCount >= (long)this.maxPendingRequests) {
            sender.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.target_busy", target.getName().getString()));
         } else {
            TeleportRequest request = new TeleportRequest(
               senderId,
               sender.getName().getString(),
               targetId,
               target.getName().getString(),
               TeleportRequestType.TPA,
               System.currentTimeMillis() + (long)this.requestTimeoutSeconds * 1000L
            );
            this.sentRequests.put(senderId, request);
            TeleportRequest existingPending = this.pendingRequests.putIfAbsent(targetId, request);
            if (existingPending != null) {
               this.sentRequests.remove(senderId);
               sender.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.target_busy", target.getName().getString()));
            } else if (this.autoAcceptFromFriends && this.isFriend(target, sender)) {
               this.cleanupRequest(request);
               this.executeTeleportRequest(sender, target, TeleportRequestType.TPA);
               sender.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.auto_accepted", target.getName().getString()));
               target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.auto_accepted_target", sender.getName().getString()));
               if (this.logTeleportRequests) {
                  LOGGER.info("Teleport request from {} to {} auto-accepted (friends)", sender.getName().getString(), target.getName().getString());
               }
            } else {
               this.scheduler.schedule(() -> {
                  TeleportRequest currentRequest = this.pendingRequests.get(targetId);
                  if (currentRequest != null && currentRequest.equals(request)) {
                     this.timeoutRequest(request);
                  }
               }, (long)this.requestTimeoutSeconds, TimeUnit.SECONDS);
               String typeText = "to you";
               sender.sendSystemMessage(MessageUtil.success("commands.neoessentials.teleport.request.sent", target.getName().getString(), typeText));
               if (this.enableRequestNotifications) {
                  target.sendSystemMessage(MessageUtil.info("commands.neoessentials.teleport.request.received", sender.getName().getString(), typeText));
                  target.sendSystemMessage(MessageUtil.component("commands.neoessentials.teleport.request.instructions"));
                  MutableComponent accept = Component.literal("[Accept]")
                     .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true))
                     .withStyle(style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/tpaaccept " + sender.getName().getString())))
                     .withStyle(
                        style -> style.withHoverEvent(
                              new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Accept teleport request"))
                           )
                     );
                  MutableComponent deny = Component.literal("[Deny]")
                     .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true))
                     .withStyle(style -> style.withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/tpadeny " + sender.getName().getString())))
                     .withStyle(
                        style -> style.withHoverEvent(
                              new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Deny teleport request"))
                           )
                     );
                  MutableComponent message = Component.literal("").append(accept).append(Component.literal(" ")).append(deny);
                  target.sendSystemMessage(message);
               }

               if (this.logTeleportRequests) {
                  LOGGER.info("Player {} sent TPA request to {}", sender.getName().getString(), target.getName().getString());
               }
            }
         }
      } else {
         sender.sendSystemMessage(MessageUtil.error("commands.neoessentials.teleport.request.already_sent"));
      }
   }

   private static class SingletonHolder {
      private static final TeleportRequestManager INSTANCE = new TeleportRequestManager();
   }
}
