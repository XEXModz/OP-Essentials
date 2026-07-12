package com.zerog.neoessentials.tablist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import com.zerog.neoessentials.permissions.PermissionSystem;
import com.zerog.neoessentials.permissions.PermissionUser;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TablistManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(TablistManager.class);
   private static final TablistManager INSTANCE = new TablistManager();
   private boolean enabled = true;
   private int refreshIntervalTicks = 20;
   private List<String> headerFrames = new ArrayList<>();
   private List<String> footerFrames = new ArrayList<>();
   private String playerFormat = "§f{prefix}§r{player}{suffix}";
   private boolean hideVanished = true;
   private boolean showAfkIndicator = true;
   private String afkSuffix = " §7[AFK]";
   private final Map<String, String> groupColors = new LinkedHashMap<>();
   private int headerFrame = 0;
   private int footerFrame = 0;
   private int tickCounter = 0;
   private final Map<UUID, String> customNames = new ConcurrentHashMap<>();

   public static TablistManager getInstance() {
      return INSTANCE;
   }

   private TablistManager() {
      this.headerFrames.add("§6§l{server_name} §8| §e{online}§8/§e{max} §7players");
      this.footerFrames.add("§7TPS: §a{tps} §8| §7Ping: §a{ping}ms §8| §7{world}");
   }

   public void loadConfig() {
      try {
         JsonObject tab = null;

         try {
            JsonObject standalone = ConfigManager.getInstance().getConfig("tablist.json");
            if (standalone != null && standalone.has("tablist")) {
               tab = standalone.getAsJsonObject("tablist");
               LOGGER.debug("TablistManager: loading from tablist.json");
            }
         } catch (Exception var5) {
            LOGGER.debug("TablistManager: tablist.json not available, trying config.json fallback: {}", var5.getMessage());
         }

         if (tab == null) {
            JsonObject cfg = ConfigManager.getInstance().getConfig("config.json");
            if (cfg != null && cfg.has("tablist")) {
               tab = cfg.getAsJsonObject("tablist");
               LOGGER.debug("TablistManager: loading from legacy tablist section in config.json");
            }
         }

         if (tab == null) {
            LOGGER.info("TablistManager: no tablist configuration found — using defaults.");
            return;
         }

         this.enabled = !tab.has("enabled") || tab.get("enabled").getAsBoolean();
         this.refreshIntervalTicks = tab.has("refreshInterval") ? tab.get("refreshInterval").getAsInt() : 20;
         this.hideVanished = !tab.has("hideVanished") || tab.get("hideVanished").getAsBoolean();
         this.showAfkIndicator = !tab.has("showAfkIndicator") || tab.get("showAfkIndicator").getAsBoolean();
         this.afkSuffix = tab.has("afkSuffix") ? tab.get("afkSuffix").getAsString().replace("&", "§") : " §7[AFK]";
         this.playerFormat = tab.has("playerFormat") ? tab.get("playerFormat").getAsString() : this.playerFormat;
         this.groupColors.clear();
         if (tab.has("groupColors") && tab.get("groupColors").isJsonObject()) {
            for (Entry<String, JsonElement> entry : tab.getAsJsonObject("groupColors").entrySet()) {
               this.groupColors.put(entry.getKey(), entry.getValue().getAsString().replace("&", "§"));
            }
         }

         this.headerFrames.clear();
         if (tab.has("header")) {
            JsonElement h = tab.get("header");
            if (h.isJsonArray()) {
               for (JsonElement el : h.getAsJsonArray()) {
                  this.headerFrames.add(el.getAsString());
               }
            } else {
               this.headerFrames.add(h.getAsString());
            }
         }

         this.footerFrames.clear();
         if (tab.has("footer")) {
            JsonElement f = tab.get("footer");
            if (f.isJsonArray()) {
               for (JsonElement el : f.getAsJsonArray()) {
                  this.footerFrames.add(el.getAsString());
               }
            } else {
               this.footerFrames.add(f.getAsString());
            }
         }

         if (this.headerFrames.isEmpty()) {
            this.headerFrames.add("§6§l{server_name}");
         }

         if (this.footerFrames.isEmpty()) {
            this.footerFrames.add("§7{online}§8/§7{max} online");
         }

         this.headerFrame = 0;
         this.footerFrame = 0;
         this.tickCounter = 0;
         LOGGER.info(
            "TablistManager loaded — {} header frame(s), {} footer frame(s), refresh every {} ticks.",
            new Object[]{this.headerFrames.size(), this.footerFrames.size(), this.refreshIntervalTicks}
         );
      } catch (Exception var6) {
         LOGGER.error("Failed to load tablist config: {}", var6.getMessage());
      }
   }

   public void onTick(MinecraftServer server) {
      if (this.enabled) {
         this.tickCounter++;
         if (this.tickCounter >= this.refreshIntervalTicks) {
            this.tickCounter = 0;
            if (this.headerFrames.size() > 1) {
               this.headerFrame = (this.headerFrame + 1) % this.headerFrames.size();
            }

            if (this.footerFrames.size() > 1) {
               this.footerFrame = (this.footerFrame + 1) % this.footerFrames.size();
            }

            this.updateAll(server);
         }
      }
   }

   public void updateAll(MinecraftServer server) {
      if (this.enabled && server != null) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            this.updatePlayer(player, server);
         }
      }
   }

   public void updatePlayer(ServerPlayer player, MinecraftServer server) {
      if (this.enabled) {
         try {
            String header = this.buildHeader(player, server);
            String footer = this.buildFooter(player, server);
            ClientboundTabListPacket packet = new ClientboundTabListPacket(Component.literal(header), Component.literal(footer));
            player.connection.send(packet);
            this.updatePlayerRows(player, server);
         } catch (Exception var6) {
            LOGGER.debug("Failed to send tablist packet to {}: {}", player.getName().getString(), var6.getMessage());
         }
      }
   }

   private void updatePlayerRows(ServerPlayer viewer, MinecraftServer server) {
      try {
         ServerScoreboard scoreboard = server.getScoreboard();

         for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (!this.isVanishedFromPlayer(target, viewer)) {
               String prefix = this.getPermissionPrefix(target);
               String group = this.getPermissionGroup(target);
               int ping = target.connection.latency();
               String pingColor;
               if (ping < 50) {
                  pingColor = "§a";
               } else if (ping < 100) {
                  pingColor = "§e";
               } else if (ping < 200) {
                  pingColor = "§6";
               } else {
                  pingColor = "§c";
               }

               String teamPrefix;
               if (prefix != null && !prefix.isEmpty()) {
                  String stripped = prefix.replaceAll("§[0-9a-fk-or]", "").trim();
                  if (stripped.isEmpty()) {
                     teamPrefix = prefix + "[player]§r ";
                  } else if (stripped.contains("[") && stripped.contains("]")) {
                     teamPrefix = prefix + "§r";
                  } else {
                     String colorCode = prefix.substring(0, prefix.indexOf(stripped));
                     teamPrefix = colorCode + "[" + stripped + "]§r ";
                  }
               } else {
                  String groupColor = this.groupColors.getOrDefault(group, "§7");
                  teamPrefix = groupColor + "[player]§r ";
               }

               String scoreboardName = target.getScoreboardName();
               String teamName = "ne_" + scoreboardName;
               if (teamName.length() > 16) {
                  teamName = "ne_" + Integer.toHexString(scoreboardName.hashCode());
                  if (teamName.length() > 16) {
                     teamName = teamName.substring(0, 16);
                  }
               }

               PlayerTeam team = scoreboard.getPlayerTeam(teamName);
               if (team == null) {
                  team = scoreboard.addPlayerTeam(teamName);
               }

               team.setPlayerPrefix(Component.literal(teamPrefix));
               team.setPlayerSuffix(Component.literal(" " + pingColor + ping + "ms"));
               if (!team.getPlayers().contains(scoreboardName)) {
                  scoreboard.addPlayerToTeam(scoreboardName, team);
               }
            }
         }
      } catch (Exception var14) {
         LOGGER.debug("Failed to update player rows: {}", var14.getMessage());
      }
   }

   public void cleanupPlayerTeam(ServerPlayer player) {
      try {
         MinecraftServer server = player.getServer();
         if (server == null) {
            return;
         }

         ServerScoreboard scoreboard = server.getScoreboard();
         String scoreboardName = player.getScoreboardName();
         String teamName = "ne_" + scoreboardName;
         if (teamName.length() > 16) {
            teamName = "ne_" + Integer.toHexString(scoreboardName.hashCode());
            if (teamName.length() > 16) {
               teamName = teamName.substring(0, 16);
            }
         }

         PlayerTeam team = scoreboard.getPlayerTeam(teamName);
         if (team != null) {
            scoreboard.removePlayerTeam(team);
         }
      } catch (Exception var7) {
         LOGGER.debug("Failed to clean up tablist team for {}: {}", player.getName().getString(), var7.getMessage());
      }
   }

   private String buildHeader(ServerPlayer player, MinecraftServer server) {
      String frame = this.headerFrames.get(Math.min(this.headerFrame, this.headerFrames.size() - 1));
      return this.applyPlaceholders(frame, player, server);
   }

   private String buildFooter(ServerPlayer player, MinecraftServer server) {
      String frame = this.footerFrames.get(Math.min(this.footerFrame, this.footerFrames.size() - 1));
      return this.applyPlaceholders(frame, player, server);
   }

   private String applyPlaceholders(String text, ServerPlayer player, MinecraftServer server) {
      if (text == null) {
         return "";
      } else {
         text = text.replace("&", "§");
         int online = (int)server.getPlayerList().getPlayers().stream().filter(p -> !this.isVanishedFromPlayer(p, player)).count();
         int max = server.getMaxPlayers();
         int ping = player.connection.latency();
         String world = player.serverLevel().dimension().location().getPath();
         String playerName = player.getName().getString();
         String displayName = this.getDisplayName(player);
         double tps = this.getTps(server);
         String tpsStr = tps >= 19.0
            ? "§a" + String.format("%.1f", tps)
            : (tps >= 15.0 ? "§e" + String.format("%.1f", tps) : "§c" + String.format("%.1f", tps));
         String time = new SimpleDateFormat("HH:mm").format(new Date());
         String serverName = server.getMotd();
         int x = player.getBlockX();
         int y = player.getBlockY();
         int z = player.getBlockZ();
         String balance = "0";

         try {
            // BUGFIX 1.1.7: the tab footer read the (disabled) NeoEssentials
            // economy and always showed 0. The server's REAL currency is the
            // EconomyCraft mod - read it via reflection (safe fallback to "0").
            Class<?> ec = Class.forName("com.reazip.economycraft.EconomyCraft");
            Object mgr = ec.getMethod("getManager", net.minecraft.server.MinecraftServer.class).invoke(null, player.getServer());
            Object bal = mgr.getClass().getMethod("getBalance", java.util.UUID.class, boolean.class).invoke(mgr, player.getUUID(), true);
            if (bal != null) {
               balance = String.valueOf(bal);
            }
         } catch (Throwable var24) {
            try {
               BigDecimal bd = EconomyManager.getInstance().getBalance(player.getUUID());
               balance = String.format("%.2f", bd.doubleValue());
            } catch (Exception var23x) {
            }
         }

         String prefix = this.getPermissionPrefix(player);
         String suffix = this.getPermissionSuffix(player);
         String group = this.getPermissionGroup(player);
         String groupColor = this.groupColors.getOrDefault(group, this.groupColors.getOrDefault("default", ""));
         String coloredDisplayName = groupColor.isEmpty() ? displayName : groupColor + displayName;
         return text.replace("{player}", playerName)
            .replace("{displayname}", coloredDisplayName)
            .replace("{online}", String.valueOf(online))
            .replace("{max}", String.valueOf(max))
            .replace("{ping}", String.valueOf(ping))
            .replace("{world}", world)
            .replace("{tps}", tpsStr)
            .replace("{time}", time)
            .replace("{server_name}", serverName)
            .replace("{x}", String.valueOf(x))
            .replace("{y}", String.valueOf(y))
            .replace("{z}", String.valueOf(z))
            .replace("{balance}", balance)
            .replace("{prefix}", prefix)
            .replace("{suffix}", suffix)
            .replace("{group}", group)
            .replace("{newline}", "\n")
            .replace("{bar}", "§8§m                              §r");
      }
   }

   private boolean isVanishedFromPlayer(ServerPlayer target, ServerPlayer viewer) {
      if (!this.hideVanished) {
         return false;
      } else {
         boolean targetVanished = VanishManager.getInstance().isPlayerVanished(target.getUUID());
         return !targetVanished ? false : !PermissionAPI.hasPermission(viewer.getUUID(), "neoessentials.vanish.see");
      }
   }

   private String getDisplayName(ServerPlayer player) {
      String custom = this.customNames.get(player.getUUID());
      return custom != null && !custom.isEmpty() ? custom : player.getName().getString();
   }

   private double getTps(MinecraftServer server) {
      try {
         double avgMs = (double)server.getAverageTickTimeNanos() / 1000000.0;
         return Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));
      } catch (Exception var4) {
         return 20.0;
      }
   }

   private String getPermissionPrefix(ServerPlayer player) {
      try {
         PermissionManager mgr = PermissionSystem.getManager();
         PermissionUser user = mgr.getUser(player.getUUID());
         if (user != null) {
            String groupName = user.getGroup();
            PermissionGroup group = mgr.getGroup(groupName);
            if (group != null && group.getPrefix() != null && !group.getPrefix().isEmpty()) {
               return group.getPrefix().replace("&", "§");
            }
         }
      } catch (Exception var6) {
      }

      return "";
   }

   private String getPermissionSuffix(ServerPlayer player) {
      try {
         PermissionManager mgr = PermissionSystem.getManager();
         PermissionUser user = mgr.getUser(player.getUUID());
         if (user != null) {
            String groupName = user.getGroup();
            PermissionGroup group = mgr.getGroup(groupName);
            if (group != null && group.getSuffix() != null && !group.getSuffix().isEmpty()) {
               return group.getSuffix().replace("&", "§");
            }
         }
      } catch (Exception var6) {
      }

      return "";
   }

   private String getPermissionGroup(ServerPlayer player) {
      try {
         PermissionManager mgr = PermissionSystem.getManager();
         PermissionUser user = mgr.getUser(player.getUUID());
         if (user != null) {
            return user.getGroup();
         }
      } catch (Exception var4) {
      }

      return "default";
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isHideVanished() {
      return this.hideVanished;
   }

   public int getRefreshIntervalTicks() {
      return this.refreshIntervalTicks;
   }

   public int getHeaderFrameCount() {
      return this.headerFrames.size();
   }

   public int getFooterFrameCount() {
      return this.footerFrames.size();
   }

   public void setHeaderOverride(String text) {
      this.headerFrames.clear();
      this.headerFrames.add(text);
      this.headerFrame = 0;
   }

   public void setFooterOverride(String text) {
      this.footerFrames.clear();
      this.footerFrames.add(text);
      this.footerFrame = 0;
   }

   public void setCustomName(UUID uuid, String name) {
      if (name != null && !name.isEmpty()) {
         this.customNames.put(uuid, name);
      } else {
         this.customNames.remove(uuid);
      }
   }

   public void clearCustomName(UUID uuid) {
      this.customNames.remove(uuid);
   }

   public String getAfkSuffix() {
      return this.afkSuffix;
   }

   public boolean isShowAfkIndicator() {
      return this.showAfkIndicator;
   }

   public void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
      server.execute(() -> this.updatePlayer(player, server));
   }

   public void onPlayerQuit(MinecraftServer server) {
      server.execute(() -> this.updateAll(server));
   }
}
