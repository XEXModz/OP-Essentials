package com.zerog.neoessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModerationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(ModerationManager.class);
   private static ModerationManager instance;
   private final Map<String, BanEntry> bans = new ConcurrentHashMap<>();
   private final Map<String, WhitelistEntry> whitelist = new ConcurrentHashMap<>();
   private final Path storageDirectory = Paths.get("neoessentials/moderation");
   private final Path bansFile = this.storageDirectory.resolve("bans.json");
   private final Path whitelistFile = this.storageDirectory.resolve("whitelist.json");
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private MinecraftServer server;
   private boolean whitelistEnabled = false;
   private static final String MODERATION_DIR = "neoessentials/moderation";
   private static final String BANS_FILE = "bans.json";
   private static final String WHITELIST_FILE = "whitelist.json";

   private ModerationManager() {
      try {
         Files.createDirectories(this.storageDirectory);
         this.loadBans();
         this.loadWhitelist();
      } catch (IOException var2) {
         LOGGER.error("Failed to create moderation directory", var2);
      }
   }

   public static ModerationManager getInstance() {
      if (instance == null) {
         instance = new ModerationManager();
      }

      return instance;
   }

   public void setServer(MinecraftServer server) {
      this.server = server;
   }

   public BanEntry addBan(BanEntry.BanType type, String target, String playerName, String reason, String evidence, Instant expiresAt, String bannedBy) {
      BanEntry ban = new BanEntry();
      ban.setType(type);
      ban.setTarget(target);
      ban.setPlayerName(playerName);
      ban.setReason(reason);
      ban.setEvidence(evidence);
      ban.setExpiresAt(expiresAt);
      ban.setBannedBy(bannedBy);
      ban.setActive(true);
      this.bans.put(ban.getId(), ban);
      this.saveBans();
      if (this.server != null && type != BanEntry.BanType.IP) {
         try {
            UUID uuid = UUID.fromString(target);
            ServerPlayer player = this.server.getPlayerList().getPlayer(uuid);
            if (player != null) {
               String kickMessage = "You have been banned from this server.\nReason: " + reason;
               if (!ban.isPermanent()) {
                  kickMessage = kickMessage + "\nExpires: " + expiresAt.toString();
               }

               player.connection.disconnect(Component.literal(kickMessage));
            }
         } catch (IllegalArgumentException var12) {
            LOGGER.debug("Target is not a valid UUID: {}", target);
         }
      }

      LOGGER.info("Ban added: {} banned {} ({})", new Object[]{bannedBy, playerName != null ? playerName : target, reason});
      return ban;
   }

   public boolean removeBan(String banId) {
      BanEntry ban = this.bans.remove(banId);
      if (ban != null) {
         this.saveBans();
         LOGGER.info("Ban removed: {} ({})", ban.getPlayerName() != null ? ban.getPlayerName() : ban.getTarget(), ban.getId());
         return true;
      } else {
         return false;
      }
   }

   public BanEntry getBan(String banId) {
      return this.bans.get(banId);
   }

   public Collection<BanEntry> getAllBans() {
      return this.bans.values();
   }

   public Collection<BanEntry> getActiveBans() {
      return this.bans.values().stream().filter(BanEntry::isActive).filter(ban -> !ban.isExpired()).collect(Collectors.toList());
   }

   public BanEntry checkBan(String uuid, String ip) {
      this.bans.values().stream().filter(ban -> ban.isActive() && ban.isExpired()).forEach(ban -> {
         ban.setActive(false);
         this.saveBans();
      });
      if (uuid != null) {
         Optional<BanEntry> uuidBan = this.bans
            .values()
            .stream()
            .filter(BanEntry::isActive)
            .filter(ban -> ban.getType() == BanEntry.BanType.UUID || ban.getType() == BanEntry.BanType.BOTH)
            .filter(ban -> ban.getTarget().equals(uuid))
            .filter(ban -> !ban.isExpired())
            .findFirst();
         if (uuidBan.isPresent()) {
            return uuidBan.get();
         }
      }

      if (ip != null) {
         Optional<BanEntry> ipBan = this.bans
            .values()
            .stream()
            .filter(BanEntry::isActive)
            .filter(ban -> ban.getType() == BanEntry.BanType.IP || ban.getType() == BanEntry.BanType.BOTH)
            .filter(ban -> ban.getTarget().equals(ip) || ban.getTarget().startsWith(ip.substring(0, ip.lastIndexOf(46))))
            .filter(ban -> !ban.isExpired())
            .findFirst();
         if (ipBan.isPresent()) {
            return ipBan.get();
         }
      }

      return null;
   }

   public List<BanEntry> getBanHistory(String target) {
      return this.bans
         .values()
         .stream()
         .filter(ban -> ban.getTarget().equals(target))
         .sorted(Comparator.comparing(BanEntry::getBannedAt).reversed())
         .collect(Collectors.toList());
   }

   public boolean submitAppeal(String banId, String appealText) {
      BanEntry ban = this.bans.get(banId);
      if (ban != null && ban.isActive()) {
         BanEntry.BanAppeal appeal = new BanEntry.BanAppeal();
         appeal.setAppealText(appealText);
         ban.setAppeal(appeal);
         this.saveBans();
         LOGGER.info("Ban appeal submitted for ban {}", banId);
         return true;
      } else {
         return false;
      }
   }

   public boolean reviewAppeal(String banId, BanEntry.BanAppeal.AppealStatus status, String reviewedBy, String reviewNotes) {
      BanEntry ban = this.bans.get(banId);
      if (ban != null && ban.hasAppeal()) {
         BanEntry.BanAppeal appeal = ban.getAppeal();
         appeal.setStatus(status);
         appeal.setReviewedBy(reviewedBy);
         appeal.setReviewedAt(Instant.now());
         appeal.setReviewNotes(reviewNotes);
         if (status == BanEntry.BanAppeal.AppealStatus.APPROVED) {
            ban.setActive(false);
         }

         this.saveBans();
         LOGGER.info("Ban appeal {} for ban {}: {}", new Object[]{status, banId, reviewNotes});
         return true;
      } else {
         return false;
      }
   }

   public void setWhitelistEnabled(boolean enabled) {
      this.whitelistEnabled = enabled;
      LOGGER.info("Whitelist {}", enabled ? "enabled" : "disabled");
   }

   public boolean isWhitelistEnabled() {
      return this.whitelistEnabled;
   }

   public WhitelistEntry addWhitelist(WhitelistEntry.WhitelistType type, String target, String playerName, String addedBy, String notes) {
      WhitelistEntry entry = new WhitelistEntry();
      entry.setType(type);
      entry.setTarget(target);
      entry.setPlayerName(playerName);
      entry.setAddedBy(addedBy);
      entry.setNotes(notes);
      this.whitelist.put(entry.getId(), entry);
      this.saveWhitelist();
      LOGGER.info("Whitelist entry added: {} by {}", playerName != null ? playerName : target, addedBy);
      return entry;
   }

   public boolean removeWhitelist(String entryId) {
      WhitelistEntry entry = this.whitelist.remove(entryId);
      if (entry != null) {
         this.saveWhitelist();
         LOGGER.info("Whitelist entry removed: {}", entry.getPlayerName() != null ? entry.getPlayerName() : entry.getTarget());
         return true;
      } else {
         return false;
      }
   }

   public Collection<WhitelistEntry> getAllWhitelist() {
      return this.whitelist.values();
   }

   public boolean isWhitelisted(String uuid, String ip) {
      if (!this.whitelistEnabled) {
         return true;
      } else {
         if (uuid != null) {
            boolean uuidWhitelisted = this.whitelist
               .values()
               .stream()
               .anyMatch(entry -> entry.getType() == WhitelistEntry.WhitelistType.UUID && entry.getTarget().equals(uuid));
            if (uuidWhitelisted) {
               return true;
            }
         }

         if (ip != null) {
            boolean ipWhitelisted = this.whitelist
               .values()
               .stream()
               .anyMatch(entry -> entry.getType() == WhitelistEntry.WhitelistType.IP && (entry.getTarget().equals(ip) || ip.startsWith(entry.getTarget())));
            if (ipWhitelisted) {
               return true;
            }
         }

         return false;
      }
   }

   public int importWhitelist(List<WhitelistEntry> entries, String importedBy) {
      int imported = 0;

      for (WhitelistEntry entry : entries) {
         entry.setId(UUID.randomUUID().toString());
         entry.setAddedBy(importedBy);
         entry.setAddedAt(Instant.now());
         this.whitelist.put(entry.getId(), entry);
         imported++;
      }

      this.saveWhitelist();
      LOGGER.info("Imported {} whitelist entries", imported);
      return imported;
   }

   private void loadBans() {
      if (Files.exists(this.bansFile)) {
         try {
            String json = Files.readString(this.bansFile);
            Map<String, BanEntry> loaded = (Map<String, BanEntry>)this.gson.fromJson(json, (new TypeToken<Map<String, BanEntry>>() {
            }).getType());
            if (loaded != null) {
               this.bans.putAll(loaded);
               LOGGER.info("Loaded {} bans", loaded.size());
            }
         } catch (Exception var3) {
            LOGGER.error("Failed to load bans", var3);
         }
      }
   }

   private void saveBans() {
      try {
         String json = this.gson.toJson(this.bans);
         Files.writeString(this.bansFile, json);
      } catch (Exception var2) {
         LOGGER.error("Failed to save bans", var2);
      }
   }

   private void loadWhitelist() {
      if (Files.exists(this.whitelistFile)) {
         try {
            String json = Files.readString(this.whitelistFile);
            Map<String, WhitelistEntry> loaded = (Map<String, WhitelistEntry>)this.gson.fromJson(json, (new TypeToken<Map<String, WhitelistEntry>>() {
            }).getType());
            if (loaded != null) {
               this.whitelist.putAll(loaded);
               LOGGER.info("Loaded {} whitelist entries", loaded.size());
            }
         } catch (Exception var3) {
            LOGGER.error("Failed to load whitelist", var3);
         }
      }
   }

   private void saveWhitelist() {
      try {
         String json = this.gson.toJson(this.whitelist);
         Files.writeString(this.whitelistFile, json);
      } catch (Exception var2) {
         LOGGER.error("Failed to save whitelist", var2);
      }
   }
}
