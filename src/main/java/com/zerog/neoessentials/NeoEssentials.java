package com.zerog.neoessentials;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.zerog.neoessentials.api.ChatAPI;
import com.zerog.neoessentials.api.DefaultPlaceholderExpansion;
import com.zerog.neoessentials.api.PlaceholderAPI;
import com.zerog.neoessentials.api.PlaceholderManager;
import com.zerog.neoessentials.api.permissions.PermissionAPI;
import com.zerog.neoessentials.chat.AfkManager;
import com.zerog.neoessentials.chat.BadgeManager;
import com.zerog.neoessentials.chat.ChatManager;
import com.zerog.neoessentials.chat.command.IgnoreCommand;
import com.zerog.neoessentials.chat.command.MsgCommand;
import com.zerog.neoessentials.chat.command.MsgToggleCommand;
import com.zerog.neoessentials.chat.command.MuteCommand;
import com.zerog.neoessentials.chat.command.MuteListCommand;
import com.zerog.neoessentials.chat.command.ReplyCommand;
import com.zerog.neoessentials.chat.command.SocialSpyCommand;
import com.zerog.neoessentials.chat.command.UnignoreCommand;
import com.zerog.neoessentials.chat.command.UnmuteCommand;
import com.zerog.neoessentials.chat.commands.ChannelCommands;
import com.zerog.neoessentials.chat.handlers.AfkMovementDetector;
import com.zerog.neoessentials.commands.CommandRegistry;
import com.zerog.neoessentials.commands.LanguageCommand;
import com.zerog.neoessentials.commands.ModRootCommand;
import com.zerog.neoessentials.commands.teleportation.HomeCommands;
import com.zerog.neoessentials.commands.teleportation.PwarpCommands;
import com.zerog.neoessentials.commands.teleportation.SpawnCommands;
import com.zerog.neoessentials.commands.teleportation.WarpCommands;
import com.zerog.neoessentials.commands.utility.DashboardCommand;
import com.zerog.neoessentials.commands.utility.DashboardRegisterCommand;
import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.config.ConfigSplitter;
import com.zerog.neoessentials.core.ManagerRegistry;
import com.zerog.neoessentials.economy.commands.EconomyCommands;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.managers.PayToggleManager;
import com.zerog.neoessentials.economy.managers.TransactionHistoryManager;
import com.zerog.neoessentials.economy.worth.SellCommand;
import com.zerog.neoessentials.economy.worth.WorthCommand;
import com.zerog.neoessentials.economy.worth.WorthManager;
import com.zerog.neoessentials.i18n.CustomLanguageManager;
import com.zerog.neoessentials.integrations.ChatIntegrationManager;
import com.zerog.neoessentials.inventory.InventoryViewCommands;
import com.zerog.neoessentials.items.commands.ClearInventoryCommand;
import com.zerog.neoessentials.items.commands.DisposeCommand;
import com.zerog.neoessentials.items.commands.EnchantCommand;
import com.zerog.neoessentials.items.commands.MiscItemCommands;
import com.zerog.neoessentials.items.commands.PowertoolCommand;
import com.zerog.neoessentials.items.commands.RepairCommand;
import com.zerog.neoessentials.kits.KitManager;
import com.zerog.neoessentials.kits.command.KitCommands;
import com.zerog.neoessentials.moderation.BanManager;
import com.zerog.neoessentials.moderation.FreezeManager;
import com.zerog.neoessentials.moderation.JailManager;
import com.zerog.neoessentials.moderation.VanishManager;
import com.zerog.neoessentials.moderation.commands.BanCommand;
import com.zerog.neoessentials.moderation.commands.FreezeCommand;
import com.zerog.neoessentials.moderation.commands.JailCommand;
import com.zerog.neoessentials.moderation.commands.KickCommand;
import com.zerog.neoessentials.moderation.commands.VanishCommand;
import com.zerog.neoessentials.permissions.PermissionSystem;
import com.zerog.neoessentials.permissions.command.PermissionsCommand;
import com.zerog.neoessentials.resourcepack.ResourcePackManager;
import com.zerog.neoessentials.shop.ShopManager;
import com.zerog.neoessentials.shop.commands.ShopCommand;
import com.zerog.neoessentials.tablist.TablistManager;
import com.zerog.neoessentials.teleportation.HomeManager;
import com.zerog.neoessentials.teleportation.DirectTeleport.DirectTeleportCommands;
import com.zerog.neoessentials.teleportation.Misc.MiscTeleportCommands;
import com.zerog.neoessentials.teleportation.Spawn.SpawnManager;
import com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestCommands;
import com.zerog.neoessentials.teleportation.TeleportRequests.TeleportRequestManager;
import com.zerog.neoessentials.teleportation.Warp.WarpManager;
import com.zerog.neoessentials.util.MessageUtil;
import com.zerog.neoessentials.util.commands.AfkCommand;
import com.zerog.neoessentials.util.commands.AnvilCommand;
import com.zerog.neoessentials.util.commands.BookCommand;
import com.zerog.neoessentials.util.commands.CompassCommand;
import com.zerog.neoessentials.util.commands.CraftingCommand;
import com.zerog.neoessentials.util.commands.DepthCommand;
import com.zerog.neoessentials.util.commands.FunCommands;
import com.zerog.neoessentials.util.commands.GamemodeCommand;
import com.zerog.neoessentials.util.commands.GetPosCommand;
import com.zerog.neoessentials.util.commands.GrindstoneCommand;
import com.zerog.neoessentials.util.commands.HelpCommand;
import com.zerog.neoessentials.util.commands.HelpopCommand;
import com.zerog.neoessentials.util.commands.ItemCustomisationCommands;
import com.zerog.neoessentials.util.commands.ListCommand;
import com.zerog.neoessentials.util.commands.MailCommand;
import com.zerog.neoessentials.util.commands.MotdCommand;
import com.zerog.neoessentials.util.commands.NearCommand;
import com.zerog.neoessentials.util.commands.NickCommand;
import com.zerog.neoessentials.util.commands.PingCommand;
import com.zerog.neoessentials.util.commands.PlayerInfoCommands;
import com.zerog.neoessentials.util.commands.PlayerStateCommands;
import com.zerog.neoessentials.util.commands.RealnameCommand;
import com.zerog.neoessentials.util.commands.RulesCommand;
import com.zerog.neoessentials.util.commands.SeenCommand;
import com.zerog.neoessentials.util.commands.ServerAdminCommands;
import com.zerog.neoessentials.util.commands.SignCommand;
import com.zerog.neoessentials.util.commands.SmithingCommand;
import com.zerog.neoessentials.util.commands.StonecuttingCommand;
import com.zerog.neoessentials.util.commands.SuicideCommand;
import com.zerog.neoessentials.util.commands.UtilityCommands;
import com.zerog.neoessentials.util.commands.WhoisCommand;
import com.zerog.neoessentials.util.commands.WorldInteractionCommands;
import com.zerog.neoessentials.vault.VaultManager;
import com.zerog.neoessentials.vault.command.VaultCommand;
import com.zerog.neoessentials.webdashboard.security.AuthenticationManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("neoessentials")
public class NeoEssentials {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeoEssentials.class);
   private static final String MOD_VERSION = "1.1.2-beta";
   private static final String MOD_NAME = "NeoEssentials";
   private static final String BUILD_NUMBER = readBuildNumber();
   private static final String MINECRAFT_VERSION = "1.21.1-1.21.10";
   private static final String NEOFORGE_VERSION = "21.1.179+";

   public NeoEssentials(IEventBus modEventBus) {
      long startTime = System.currentTimeMillis();
      LOGGER.info("╔════════════════════════════════════════════════════════════════╗");
      LOGGER.info("║         {} v{} (Build #{})         ║", new Object[]{"NeoEssentials", "1.1.2-beta", BUILD_NUMBER});
      LOGGER.info("║    Minecraft {} | NeoForge {}        ║", "1.21.1-1.21.10", "21.1.179+");
      LOGGER.info("╚════════════════════════════════════════════════════════════════╝");
      LOGGER.info("");
      LOGGER.info("Initializing {} systems...", "NeoEssentials");

      try {
         LOGGER.info("⚙ Initializing PlaceholderAPI system...");
         this.initializePlaceholderAPI();
         LOGGER.info("✓ PlaceholderAPI system initialized successfully");
      } catch (Exception var7) {
         LOGGER.error("✗ PlaceholderAPI initialization failed: {}", var7.getMessage(), var7);
      }

      try {
         LOGGER.info("⚙ Registering system managers...");
         this.registerAllManagers();
         LOGGER.info(
            "✓ Registered {} managers across {} categories",
            ManagerRegistry.getInstance().getManagerCount(),
            ManagerRegistry.getInstance().getManagersByCategory().size()
         );
      } catch (Exception var6) {
         LOGGER.error("✗ Manager registration failed: {}", var6.getMessage(), var6);
      }

      MessageUtil.ensureCustomLanguageFile();
      long duration = System.currentTimeMillis() - startTime;
      LOGGER.info("");
      LOGGER.info("✓ {} initialized successfully in {}ms", "NeoEssentials", duration);
      LOGGER.info("════════════════════════════════════════════════════════════════");
      LOGGER.info("");
   }

   private static String readBuildNumber() {
      try (InputStream is = NeoEssentials.class.getResourceAsStream("/build_number.txt")) {
         if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
               String buildNumber = reader.lines().collect(Collectors.joining()).trim();
               return buildNumber.isEmpty() ? "UNKNOWN" : buildNumber;
            }
         } else {
            return "UNKNOWN";
         }
      } catch (Exception var8) {
         LOGGER.debug("Could not read build number: {}", var8.getMessage());
         return "UNKNOWN";
      }
   }

   private void registerAllManagers() {
      ManagerRegistry registry = ManagerRegistry.getInstance();
      registry.registerManager("EconomyManager", "economy", EconomyManager.class, EconomyManager::getInstance);
      registry.registerManager("AfkManager", "chat", AfkManager.class, AfkManager::getInstance);
      registry.registerManager("VanishManager", "moderation", VanishManager.class, VanishManager::getInstance);
      registry.registerManager("FreezeManager", "moderation", FreezeManager.class, FreezeManager::getInstance);
      registry.registerManager("JailManager", "moderation", JailManager.class, JailManager::getInstance);
      registry.registerManager("HomeManager", "teleportation", HomeManager.class, HomeManager::getInstance);
      registry.registerManager("WarpManager", "teleportation", WarpManager.class, WarpManager::getInstance);
      registry.registerManager("SpawnManager", "teleportation", SpawnManager.class, SpawnManager::getInstance);
      registry.registerManager("KitManager", "kits", KitManager.class, KitManager::getInstance);
      registry.registerManager("AuthenticationManager", "dashboard", AuthenticationManager.class, AuthenticationManager::getInstance);
      registry.registerManager("PlaceholderManager", "api", PlaceholderManager.class, PlaceholderManager::getInstance);
      registry.registerManager("ConfigManager", "core", ConfigManager.class, ConfigManager::getInstance);
      registry.registerManager("PermissionSystem", "core", PermissionSystem.class);
      LOGGER.debug("Manager registration complete - {} managers registered", registry.getManagerCount());
   }

   private static void registerAllCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandRegistry registry) {
      ModRootCommand.register(dispatcher);
      registry.registerCommand("warp", "Teleport to a warp");
      registry.registerCommand("setwarp", "Create a warp");
      registry.registerCommand("delwarp", "Delete a warp");
      registry.registerCommand("warps", "List all warps");
      WarpCommands.register(dispatcher);
      if (WarpManager.getInstance().isPlayerWarpsEnabled()) {
         registry.registerCommand("pwarp", "Teleport to your player warp");
         registry.registerCommand("setpwarp", "Create a player warp");
         registry.registerCommand("delpwarp", "Delete a player warp");
         registry.registerCommand("pwarps", "List your player warps");
         PwarpCommands.register(dispatcher);
      }

      registry.registerCommand("home", "Teleport to your home");
      registry.registerCommand("sethome", "Set your home location");
      registry.registerCommand("delhome", "Delete your home");
      registry.registerCommand("deletehome", "Delete your home (alias)");
      registry.registerCommand("homes", "List your homes");
      HomeCommands.register(dispatcher);
      registry.registerCommand("spawn", "Teleport to spawn");
      registry.registerCommand("setspawn", "Set spawn location");
      SpawnCommands.register(dispatcher);
      registry.registerCommand("tpa", "Request to teleport to a player");
      registry.registerCommand("tpahere", "Request a player to teleport to you");
      registry.registerCommand("tpaccept", "Accept a teleport request");
      registry.registerCommand("tpdeny", "Deny a teleport request");
      registry.registerCommand("tpacancel", "Cancel your teleport request");
      TeleportRequestCommands.register(dispatcher);
      registry.registerCommand("tp", "Teleport to a player or location");
      registry.registerCommand("tphere", "Teleport a player to you");
      registry.registerCommand("tpall", "Teleport all players to you");
      registry.registerCommand("tppos", "Teleport to coordinates");
      registry.registerCommand("tpr", "Random teleportation", "randomtp", "randomteleport");
      DirectTeleportCommands.register(dispatcher);
      registry.registerCommand("neoe tpr", "Random teleportation (alias)");
      registry.registerCommand("neoe randomtp", "Random teleportation (alias)");
      registry.registerCommand("neoe randomteleport", "Random teleportation (alias)");
      registry.registerCommand("back", "Return to previous location");
      registry.registerCommand("top", "Teleport to highest block");
      registry.registerCommand("jump", "Jump through walls");
      registry.registerCommand("jumpto", "Teleport to block you're looking at");
      MiscTeleportCommands.register(dispatcher);
      registry.registerCommand("pay", "Send money to another player");
      registry.registerCommand("balance", "Check your balance");
      registry.registerCommand("bal", "Check your balance (alias)");
      registry.registerCommand("baltop", "View top balances");
      registry.registerCommand("balancetop", "View top balances (alias)");
      registry.registerCommand("eco", "Admin economy commands");
      registry.registerCommand("paytoggle", "Toggle receiving payments");
      registry.registerCommand("pt", "Toggle receiving payments (alias)");
      EconomyCommands.register(dispatcher);
      registry.registerCommand("ban", "Ban a player");
      registry.registerCommand("unban", "Unban a player");
      registry.registerCommand("banip", "Ban an IP address");
      registry.registerCommand("unbanip", "Unban an IP address");
      registry.registerCommand("banlist", "List banned players");
      registry.registerCommand("tempban", "Temporarily ban a player");
      registry.registerCommand("tempbanip", "Temporarily ban an IP address");
      registry.registerCommand("kick", "Kick a player");
      registry.registerCommand("kickall", "Kick all players");
      registry.registerCommand("mute", "Mute a player");
      registry.registerCommand("unmute", "Unmute a player");
      registry.registerCommand("mutelist", "List muted players");
      registry.registerCommand("jail", "Jail a player");
      registry.registerCommand("jailfor", "Jail a player for a set duration");
      registry.registerCommand("unjail", "Release a player from jail");
      registry.registerCommand("setjail", "Set jail location");
      registry.registerCommand("deljail", "Delete a jail location");
      registry.registerCommand("jaillist", "List all jail locations");
      registry.registerCommand("jailinfo", "Show info about a jail");
      registry.registerCommand("jails", "List all jail locations (alias)");
      registry.registerCommand("togglejail", "Toggle a player's jail state");
      registry.registerCommand("freeze", "Freeze a player");
      registry.registerCommand("unfreeze", "Unfreeze a player");
      registry.registerCommand("freezeall", "Freeze all players");
      registry.registerCommand("unfreezeall", "Unfreeze all players");
      registry.registerCommand("freezelist", "List frozen players");
      registry.registerCommand("vanish", "Toggle vanish mode");
      registry.registerCommand("v", "Toggle vanish mode (alias)");
      registry.registerCommand("unvanish", "Disable vanish mode");
      registry.registerCommand("vanishlist", "List vanished players");
      BanCommand.register(dispatcher);
      KickCommand.register(dispatcher);
      JailCommand.register(dispatcher);
      FreezeCommand.register(dispatcher);
      VanishCommand.register(dispatcher);
      registry.registerCommand("msg", "Send a private message");
      registry.registerCommand("message", "Send a private message (alias)");
      registry.registerCommand("tell", "Send a private message (alias)");
      registry.registerCommand("whisper", "Send a private message (alias)");
      registry.registerCommand("w", "Send a private message (alias)");
      registry.registerCommand("reply", "Reply to last private message");
      registry.registerCommand("r", "Reply to last private message (alias)");
      registry.registerCommand("ignore", "Ignore a player");
      registry.registerCommand("unignore", "Unignore a player");
      registry.registerCommand("socialspy", "Spy on private messages");
      registry.registerCommand("msgtoggle", "Toggle receiving private messages");
      registry.registerCommand("mail", "Manage mail messages");
      MsgCommand.register(dispatcher);
      ReplyCommand.register(dispatcher);
      IgnoreCommand.register(dispatcher);
      UnignoreCommand.register(dispatcher);
      SocialSpyCommand.register(dispatcher);
      MuteCommand.register(dispatcher);
      UnmuteCommand.register(dispatcher);
      MuteListCommand.register(dispatcher);
      MsgToggleCommand.register(dispatcher);
      ChannelCommands.register(dispatcher);
      registry.registerCommand("language", "Manage custom language files");
      LanguageCommand.register(dispatcher);
      registry.registerCommand("permissions", "Manage permissions");
      registry.registerCommand("pex", "Manage permissions (alias)");
      PermissionsCommand.register(dispatcher);
      registry.registerCommand("kit", "Claim a kit");
      registry.registerCommand("kits", "List available kits");
      registry.registerCommand("listkits", "List available kits (alias)");
      registry.registerCommand("createkit", "Create a new kit");
      registry.registerCommand("delkit", "Delete a kit");
      registry.registerCommand("kitreset", "Reset a kit cooldown");
      KitCommands.register(dispatcher);
      registry.registerCommand("afk", "Toggle AFK status");
      registry.registerCommand("away", "Toggle AFK status (alias)");
      registry.registerCommand("help", "Show available commands");
      registry.registerCommand("?", "Show available commands (alias)");
      registry.registerCommand("nick", "Change your nickname");
      registry.registerCommand("nickname", "Change your nickname (alias)");
      registry.registerCommand("anvil", "Open portable anvil");
      registry.registerCommand("workbench", "Open portable crafting table");
      registry.registerCommand("book", "Manage books");
      registry.registerCommand("compass", "Show your compass direction");
      registry.registerCommand("direction", "Show your compass direction (alias)");
      registry.registerCommand("crafting", "Open portable crafting table");
      registry.registerCommand("craft", "Open portable crafting table (alias)");
      registry.registerCommand("depth", "Show your depth");
      registry.registerCommand("getpos", "Get your current position");
      registry.registerCommand("coords", "Get your current position (alias)");
      registry.registerCommand("whereami", "Get your current position (alias)");
      registry.registerCommand("grindstone", "Open portable grindstone");
      registry.registerCommand("helpop", "Request help from staff");
      registry.registerCommand("ac", "Request help from staff (alias)");
      registry.registerCommand("amsg", "Request help from staff (alias)");
      registry.registerCommand("list", "List online players");
      registry.registerCommand("who", "List online players (alias)");
      registry.registerCommand("online", "List online players (alias)");
      registry.registerCommand("mail", "Manage mail messages");
      registry.registerCommand("motd", "View message of the day");
      registry.registerCommand("near", "Find nearby players");
      registry.registerCommand("nearby", "Find nearby players (alias)");
      registry.registerCommand("ping", "Check your ping");
      registry.registerCommand("pong", "Check your ping (alias)");
      registry.registerCommand("realname", "Find player by nickname");
      registry.registerCommand("rules", "View server rules");
      registry.registerCommand("seen", "Check when player was last seen");
      registry.registerCommand("sign", "Edit sign text");
      registry.registerCommand("smithing", "Open portable smithing table");
      registry.registerCommand("stonecutting", "Open portable stonecutter");
      registry.registerCommand("stonecutter", "Open portable stonecutter (alias)");
      registry.registerCommand("suicide", "Kill yourself");
      registry.registerCommand("killme", "Kill yourself (alias)");
      registry.registerCommand("whois", "Get player information");
      registry.registerCommand("info", "Get player information (alias)");
      registry.registerCommand("gms", "Change to survival mode");
      registry.registerCommand("gmc", "Change to creative mode");
      registry.registerCommand("gmsp", "Change to spectator mode");
      registry.registerCommand("gma", "Change to adventure mode");
      InventoryViewCommands.register(dispatcher);
      AfkCommand.register(dispatcher);
      HelpCommand.register(dispatcher);
      AnvilCommand.register(dispatcher);
      BookCommand.register(dispatcher);
      CompassCommand.register(dispatcher);
      CraftingCommand.register(dispatcher);
      DepthCommand.register(dispatcher);
      GetPosCommand.register(dispatcher);
      GrindstoneCommand.register(dispatcher);
      HelpopCommand.register(dispatcher);
      ListCommand.register(dispatcher);
      MailCommand.register(dispatcher);
      MotdCommand.register(dispatcher);
      NearCommand.register(dispatcher);
      NickCommand.register(dispatcher);
      PingCommand.register(dispatcher);
      RealnameCommand.register(dispatcher);
      RulesCommand.register(dispatcher);
      SeenCommand.register(dispatcher);
      SignCommand.register(dispatcher);
      SmithingCommand.register(dispatcher);
      StonecuttingCommand.register(dispatcher);
      SuicideCommand.register(dispatcher);
      WhoisCommand.register(dispatcher);
      GamemodeCommand.register(dispatcher);
      registry.registerCommand("dashboard", "Manage web dashboard");
      DashboardCommand.register(dispatcher);
      registry.registerCommand("dashboardregister", "Register a dashboard account");
      DashboardRegisterCommand.register(dispatcher);
      registry.registerCommand("repair", "Repair items");
      registry.registerCommand("fix", "Repair items (alias)");
      registry.registerCommand("dispose", "Dispose of items");
      registry.registerCommand("trash", "Dispose of items (alias)");
      registry.registerCommand("powertool", "Bind commands to items");
      registry.registerCommand("pt", "Bind commands to items (alias)");
      registry.registerCommand("enchant", "Enchant items");
      registry.registerCommand("clearinventory", "Clear inventory");
      registry.registerCommand("ci", "Clear inventory (alias)");
      registry.registerCommand("clear", "Clear inventory (alias)");
      registry.registerCommand("invsee", "View another player's inventory");
      registry.registerCommand("inv", "View another player's inventory (alias)");
      registry.registerCommand("invseeedit", "View and edit another player's inventory");
      registry.registerCommand("enderchest", "View another player's ender chest");
      registry.registerCommand("ec", "View another player's ender chest (alias)");
      registry.registerCommand("enderchestedit", "View and edit another player's ender chest");
      registry.registerCommand("ecedit", "View and edit another player's ender chest (alias)");
      registry.registerCommand("condense", "Compact items to their block forms");
      registry.registerCommand("showkit", "Preview kit contents without claiming");
      registry.registerCommand("powertoollist", "List all active powertool bindings");
      registry.registerCommand("ptlist", "List all active powertool bindings (alias)");
      registry.registerCommand("customtext", "Display a custom server text page");
      registry.registerCommand("ctext", "Display a custom server text page (alias)");
      registry.registerCommand("payconfirmtoggle", "Toggle payment confirmation prompts");
      registry.registerCommand("ciconfirmtoggle", "Toggle /ci confirmation prompts");
      registry.registerCommand("clearinventoryconfirmtoggle", "Toggle /ci confirmation prompts (alias)");
      registry.registerCommand("item", "Give yourself an item by name");
      registry.registerCommand("i", "Give yourself an item by name (alias)");
      registry.registerCommand("rtoggle", "Toggle /r reply-to-sender direction");
      RepairCommand.register(dispatcher);
      DisposeCommand.register(dispatcher);
      PowertoolCommand.register(dispatcher);
      EnchantCommand.register(dispatcher);
      ClearInventoryCommand.register(dispatcher);
      MiscItemCommands.register(dispatcher);
      registry.registerCommand("worth", "Check the sell value of an item");
      registry.registerCommand("sell", "Sell items for money");
      registry.registerCommand("setworth", "Set the sell price of an item");
      WorthManager.getInstance().initialize();
      WorthCommand.register(dispatcher);
      SellCommand.register(dispatcher);
      registry.registerCommand("fly", "Toggle flight mode");
      registry.registerCommand("god", "Toggle god mode");
      registry.registerCommand("heal", "Restore player health and hunger");
      registry.registerCommand("feed", "Restore player hunger");
      registry.registerCommand("speed", "Set walk or fly speed");
      registry.registerCommand("ext", "Extinguish a player");
      registry.registerCommand("extinguish", "Extinguish a player (alias)");
      registry.registerCommand("burn", "Set a player on fire");
      registry.registerCommand("give", "Give items to a player");
      registry.registerCommand("more", "Fill held stack to max");
      registry.registerCommand("hat", "Wear held item as helmet");
      registry.registerCommand("exp", "Manage player experience");
      registry.registerCommand("xp", "Manage player experience (alias)");
      registry.registerCommand("sudo", "Run a command as another player");
      registry.registerCommand("playtime", "Check player play time");
      PlayerStateCommands.register(dispatcher);
      registry.registerCommand("broadcast", "Broadcast a message to all players");
      registry.registerCommand("bc", "Broadcast a message (alias)");
      registry.registerCommand("announce", "Broadcast a message (alias)");
      registry.registerCommand("time", "Get or set world time");
      registry.registerCommand("day", "Set time to day");
      registry.registerCommand("night", "Set time to night");
      registry.registerCommand("weather", "Set world weather");
      registry.registerCommand("sun", "Set weather to clear");
      registry.registerCommand("storm", "Set weather to storm");
      registry.registerCommand("thunder", "Set weather to thunder");
      registry.registerCommand("kill", "Kill a player");
      registry.registerCommand("gamemode", "Change player gamemode");
      registry.registerCommand("tpo", "Teleport override (bypass tptoggle)");
      registry.registerCommand("tpohere", "Bring player here override");
      registry.registerCommand("tpoffline", "Teleport to offline player's last position");
      ServerAdminCommands.register(dispatcher);
      registry.registerCommand("ptime", "Set per-player time override");
      registry.registerCommand("pweather", "Set per-player weather override");
      registry.registerCommand("effect", "Apply potion effects to players");
      registry.registerCommand("spawnmob", "Spawn entities");
      registry.registerCommand("mob", "Spawn entities (alias)");
      registry.registerCommand("unlimited", "Toggle unlimited item use");
      registry.registerCommand("condense", "Condense items to storage blocks");
      UtilityCommands.register(dispatcher);
      registry.registerCommand("me", "Broadcast an action message");
      registry.registerCommand("tptoggle", "Toggle teleport request acceptance");
      registry.registerCommand("gc", "Show server memory and TPS info");
      registry.registerCommand("mem", "Show server memory info (alias)");
      registry.registerCommand("lightning", "Strike lightning at a player");
      registry.registerCommand("smite", "Strike lightning (alias)");
      registry.registerCommand("skull", "Get a player head item");
      registry.registerCommand("itemname", "Rename held item");
      registry.registerCommand("rename", "Rename held item (alias)");
      registry.registerCommand("itemlore", "Edit held item lore");
      registry.registerCommand("remove", "Remove entities in radius");
      registry.registerCommand("loom", "Open portable loom");
      registry.registerCommand("cartography", "Open portable cartography table");
      registry.registerCommand("cartographytable", "Open portable cartography table (alias)");
      ItemCustomisationCommands.register(dispatcher);
      registry.registerCommand("fireball", "Shoot a projectile");
      registry.registerCommand("tree", "Grow a tree at look target");
      registry.registerCommand("bigtree", "Grow a large tree (alias)");
      registry.registerCommand("break", "Break the looked-at block");
      registry.registerCommand("ice", "Freeze a player");
      registry.registerCommand("bottom", "Teleport to the bottom of the world");
      registry.registerCommand("tpaall", "Send tpa-here to all online players");
      registry.registerCommand("broadcastworld", "Broadcast to players in your world");
      registry.registerCommand("bcastworld", "Broadcast to world (alias)");
      WorldInteractionCommands.register(dispatcher);
      registry.registerCommand("seen", "Show when a player was last online");
      registry.registerCommand("near", "List nearby players");
      registry.registerCommand("ping", "Show your network latency");
      registry.registerCommand("playtime", "Show total play time");
      registry.registerCommand("whois", "Show detailed player info");
      registry.registerCommand("realname", "Look up real name from nickname");
      registry.registerCommand("sudo", "Force a player to run a command");
      registry.registerCommand("suicide", "Kill yourself");
      registry.registerCommand("msgtoggle", "Toggle incoming private messages");
      registry.registerCommand("rtoggle", "Toggle reply-to-last-sender");
      registry.registerCommand("motd", "Show message of the day");
      registry.registerCommand("rules", "Show server rules");
      PlayerInfoCommands.register(dispatcher);
      WarpCommands.registerWarpInfoCommand(dispatcher);
      ServerAdminCommands.registerWorldCommands(dispatcher);
      registry.registerCommand("renamehome", "Rename a home");
      registry.registerCommand("warpinfo", "Show info about a warp");
      registry.registerCommand("world", "Teleport to a world/dimension");
      registry.registerCommand("spawner", "Change a spawner type");
      registry.registerCommand("recipe", "Show/unlock recipe for an item");
      registry.registerCommand("tpauto", "Auto-accept all teleport requests");
      registry.registerCommand("firework", "Edit or fire held firework rockets");
      registry.registerCommand("fw", "Edit or fire held firework rockets (alias)");
      registry.registerCommand("nuke", "Rain TNT on a player");
      registry.registerCommand("antioch", "Spawn lit TNT at your look target (\ud83d\udc07 easter egg)");
      registry.registerCommand("kittycannon", "Launch an exploding baby cat \ud83d\udc31");
      registry.registerCommand("beezooka", "Launch angry bees \ud83d\udc1d");
      registry.registerCommand("itemdb", "Look up item registry info");
      registry.registerCommand("potion", "Edit potion effects on held potion item");
      registry.registerCommand("info", "Show server info/MOTD");
      registry.registerCommand("rest", "Reset your sleep timer (prevent phantoms)");
      registry.registerCommand("backup", "Trigger a server world save and backup");
      FunCommands.register(dispatcher);
      registry.registerCommand("vault", "NeoEssentials Vault API info and management");
      VaultCommand.register(dispatcher);
      if (ConfigManager.isChestShopEnabled()) {
         registry.registerCommand("chestshop", "Sign-based chest shop system");
         registry.registerCommand("cshop", "Sign-based chest shop (alias)");
         ShopCommand.register(dispatcher);
      }
   }

   private void initializePlaceholderAPI() {
      LOGGER.debug("Initializing PlaceholderAPI system...");

      try {
         DefaultPlaceholderExpansion defaultExpansion = new DefaultPlaceholderExpansion();
         LOGGER.debug("Created DefaultPlaceholderExpansion with {} placeholders", defaultExpansion.getPlaceholders().size());
         boolean registered = PlaceholderAPI.registerExpansion(defaultExpansion);
         if (registered) {
            LOGGER.info("PlaceholderAPI initialized with {} default placeholders", defaultExpansion.getPlaceholders().size());
            LOGGER.debug("Available placeholders: {}", PlaceholderAPI.getRegisteredPlaceholders());
            ManagerRegistry.getInstance().markInitialized("PlaceholderManager");
         } else {
            LOGGER.error("Failed to register default placeholder expansion");
            ManagerRegistry.getInstance().markFailed("PlaceholderManager", "Failed to register default expansion");
         }
      } catch (Exception var3) {
         LOGGER.error("PlaceholderAPI initialization failed: {}", var3.getMessage(), var3);
         ManagerRegistry.getInstance().markFailed("PlaceholderManager", var3.getMessage());
      }
   }

   @EventBusSubscriber(
      modid = "neoessentials",
      bus = Bus.GAME
   )
   public static class GameEvents {
      @SubscribeEvent
      public static void onServerStarting(ServerStartingEvent event) {
         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");
         NeoEssentials.LOGGER.info("Server starting - initializing NeoEssentials systems...");
         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");

         try {
            ConfigSplitter.checkAndPromptMigration();
         } catch (Exception var10) {
            NeoEssentials.LOGGER.debug("Config split check failed: {}", var10.getMessage());
         }

         try {
            NeoEssentials.LOGGER.info("⚙ Initializing Permission System...");
            PermissionSystem.initialize();
            ManagerRegistry.getInstance().markInitialized("PermissionSystem");
            NeoEssentials.LOGGER.info("✓ Permission System initialized successfully");
         } catch (Exception var9) {
            NeoEssentials.LOGGER.error("✗ CRITICAL: Permission system failed to initialize!", var9);
            ManagerRegistry.getInstance().markFailed("PermissionSystem", var9.getMessage());
         }

         try {
            NeoEssentials.LOGGER.info("⚙ Initializing Vault API...");
            VaultManager.initialize();
            NeoEssentials.LOGGER.info("✓ Vault API initialized successfully");
         } catch (Exception var8) {
            NeoEssentials.LOGGER.error("✗ Vault API initialization failed: {}", var8.getMessage(), var8);
         }

         if (ConfigManager.isChestShopEnabled()) {
            try {
               NeoEssentials.LOGGER.info("⚙ Initializing ChestShop system...");
               ShopManager.getInstance().initialize();
               NeoEssentials.LOGGER.info("✓ ChestShop system initialized ({} shop(s) loaded)", ShopManager.getInstance().getShopCount());
            } catch (Exception var7) {
               NeoEssentials.LOGGER.error("✗ ChestShop system failed to initialize: {}", var7.getMessage(), var7);
            }
         } else {
            NeoEssentials.LOGGER.info("⚙ ChestShop system disabled in config");
         }

         try {
            NeoEssentials.LOGGER.info("⚙ Initializing custom language system...");
            CustomLanguageManager.getInstance().initialize();
            NeoEssentials.LOGGER.info("✓ Custom language system initialized successfully");
         } catch (Exception var6) {
            NeoEssentials.LOGGER.error("✗ Custom language system failed to initialize!", var6);
         }

         try {
            NeoEssentials.LOGGER.info("⚙ Loading custom badge images...");
            BadgeManager.getInstance().loadCustomBadgeImages();
            NeoEssentials.LOGGER.info("✓ Badge images loaded successfully");
         } catch (Exception var5) {
            NeoEssentials.LOGGER.warn("⚠ Failed to load badge images: {}", var5.getMessage());
         }

         try {
            NeoEssentials.LOGGER.info("⚙ Initializing resource pack system...");
            ResourcePackManager.getInstance().initialize();
            NeoEssentials.LOGGER.info("✓ Resource pack system initialized");
         } catch (Exception var4) {
            NeoEssentials.LOGGER.warn("⚠ Failed to initialize resource pack system: {}", var4.getMessage());
         }

         try {
            String diagnosticReport = ManagerRegistry.getInstance().generateDiagnosticReport();
            NeoEssentials.LOGGER.info(diagnosticReport);
            int failedCount = ManagerRegistry.getInstance().getFailedCount();
            if (failedCount > 0) {
               NeoEssentials.LOGGER.warn("⚠ {} manager(s) failed to initialize - some features may be unavailable", failedCount);
            }
         } catch (Exception var3) {
            NeoEssentials.LOGGER.error("Failed to generate manager diagnostics: {}", var3.getMessage());
         }

         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");
      }

      @SubscribeEvent
      public static void onServerStarted(ServerStartedEvent event) {
         NeoEssentials.LOGGER.info("Server started - initializing chat system...");

         try {
            ChatIntegrationManager.initialize();
         } catch (Exception var10) {
            NeoEssentials.LOGGER.error("Failed to initialize chat integration adapters", var10);
         }

         try {
            ConfigManager configManager = ConfigManager.getInstance();
            JsonObject config = configManager.getConfig("config.json");
            JsonObject chatObj = config.has("chat") ? config.getAsJsonObject("chat") : new JsonObject();
            JsonObject commandsObj = config.has("commands") ? config.getAsJsonObject("commands") : new JsonObject();
            ChatManager chatManager = new ChatManager(chatObj, commandsObj);
            ChatAPI.setChatManager(chatManager);
            NeoEssentials.LOGGER.info("ChatManager initialized successfully");
         } catch (Exception var9) {
            NeoEssentials.LOGGER.error("Failed to initialize ChatManager on server start", var9);
         }

         try {
            ConfigManager configManager = ConfigManager.getInstance();
            JsonObject config = configManager.getConfig("config.json");
            JsonObject afkObj = config.has("afk") ? config.getAsJsonObject("afk") : new JsonObject();
            AfkManager.getInstance().loadConfiguration(afkObj);
            NeoEssentials.LOGGER.info("AfkManager configuration loaded successfully");
         } catch (Exception var8) {
            NeoEssentials.LOGGER.error("Failed to initialize AfkManager configuration on server start", var8);
         }

         NeoEssentials.LOGGER.info("Server started - applying player nicknames...");

         try {
            NickCommand.applyNicknamesToOnlinePlayers(event.getServer());
            NeoEssentials.LOGGER.info("Player nicknames applied successfully");
         } catch (Exception var7) {
            NeoEssentials.LOGGER.error("Failed to apply player nicknames on server start", var7);
         }

         try {
            TablistManager.getInstance().loadConfig();
            NeoEssentials.LOGGER.info("TablistManager initialized successfully");
         } catch (Exception var6) {
            NeoEssentials.LOGGER.error("Failed to initialize TablistManager: {}", var6.getMessage());
         }
      }

      @SubscribeEvent
      public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
         if (ConfigSplitter.shouldNotifyAdmins()
            && event.getEntity() instanceof ServerPlayer player
            && (
               player.hasPermissions(4)
                  || PermissionAPI.hasPermission(player.getUUID(), "*")
                  || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.*")
                  || PermissionAPI.hasPermission(player.getUUID(), "neoessentials.admin.*")
            )) {
            ConfigSplitter.markAdminsNotified();
            MinecraftServer server = player.getServer();
            if (server != null) {
               server.execute(() -> {
                  try {
                     Thread.sleep(2000L);
                     player.sendSystemMessage(Component.literal(""));
                     player.sendSystemMessage(Component.literal("§6§l════════════════════════════════════════════════════════════════"));
                     player.sendSystemMessage(Component.literal("§e§l                    CONFIG SPLITTING AVAILABLE"));
                     player.sendSystemMessage(Component.literal("§6§l════════════════════════════════════════════════════════════════"));
                     player.sendSystemMessage(Component.literal(""));
                     player.sendSystemMessage(Component.literal("§7Your config.json file is large and could be easier to manage!"));
                     player.sendSystemMessage(Component.literal("§7NeoEssentials can split it into smaller, focused files."));
                     player.sendSystemMessage(Component.literal(""));
                     player.sendSystemMessage(Component.literal("§a✓ Easier to edit §7- Each system in its own file"));
                     player.sendSystemMessage(Component.literal("§a✓ Safer §7- Automatic backup before splitting"));
                     player.sendSystemMessage(Component.literal("§a✓ Organized §7- Find settings faster"));
                     player.sendSystemMessage(Component.literal("§a✓ Reversible §7- Keep backup to restore anytime"));
                     player.sendSystemMessage(Component.literal(""));
                     player.sendSystemMessage(Component.literal("§eRun: §b/neoessentials config split §eto enable"));
                     player.sendSystemMessage(Component.literal(""));
                     player.sendSystemMessage(Component.literal("§6§l════════════════════════════════════════════════════════════════"));
                     player.sendSystemMessage(Component.literal(""));
                  } catch (InterruptedException var2) {
                  }
               });
            }
         }
      }

      @SubscribeEvent
      public static void onServerStopping(ServerStoppingEvent event) {
         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");
         NeoEssentials.LOGGER.info("Server stopping - shutting down NeoEssentials systems...");
         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");

         try {
            NeoEssentials.LOGGER.info("Shutting down Permission System...");
            PermissionSystem.shutdown();
         } catch (Exception var12) {
            NeoEssentials.LOGGER.error("Failed to save permissions on shutdown", var12);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down Economy Manager...");
            EconomyManager.getInstance().shutdown();
         } catch (Exception var11) {
            NeoEssentials.LOGGER.error("Failed to shutdown Economy Manager", var11);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down Transaction History Manager...");
            TransactionHistoryManager.getInstance().shutdown();
         } catch (Exception var10) {
            NeoEssentials.LOGGER.error("Failed to shutdown Transaction History Manager", var10);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down Pay Toggle Manager...");
            PayToggleManager.getInstance().shutdown();
         } catch (Exception var9) {
            NeoEssentials.LOGGER.error("Failed to shutdown Pay Toggle Manager", var9);
         }

         try {
            VaultManager.shutdown();
         } catch (Exception var8) {
            NeoEssentials.LOGGER.error("Failed to shutdown Vault API", var8);
         }

         try {
            ShopManager.getInstance().shutdown();
         } catch (Exception var7) {
            NeoEssentials.LOGGER.error("Failed to shutdown ChestShop system", var7);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down chat integration adapters...");
            ChatIntegrationManager.shutdown();
         } catch (Exception var6) {
            NeoEssentials.LOGGER.error("Failed to shutdown chat integration adapters", var6);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down AFK Manager...");
            AfkManager.getInstance().shutdown();
         } catch (Exception var5) {
            NeoEssentials.LOGGER.error("Failed to shutdown AFK Manager", var5);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down AFK Movement Detector...");
            AfkMovementDetector.shutdown();
         } catch (Exception var4) {
            NeoEssentials.LOGGER.error("Failed to shutdown AFK Movement Detector", var4);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down Ban Manager scheduler...");
            BanManager.getInstance().shutdownScheduler();
         } catch (Exception var3) {
            NeoEssentials.LOGGER.error("Failed to shutdown Ban Manager", var3);
         }

         try {
            NeoEssentials.LOGGER.info("Shutting down Teleport Request Manager...");
            TeleportRequestManager.getInstance().shutdown();
         } catch (Exception var2) {
            NeoEssentials.LOGGER.error("Failed to shutdown Teleport Request Manager", var2);
         }

         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");
         NeoEssentials.LOGGER.info("NeoEssentials shutdown complete");
         NeoEssentials.LOGGER.info("════════════════════════════════════════════════════════════════");
      }

      @SubscribeEvent
      public static void onRegisterCommands(RegisterCommandsEvent event) {
         NeoEssentials.LOGGER.info("Registering NeoEssentials commands...");
         CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
         CommandRegistry registry = CommandRegistry.getInstance();
         removeVanillaCommand(dispatcher, "msg");
         removeVanillaCommand(dispatcher, "tell");
         removeVanillaCommand(dispatcher, "w");
         NeoEssentials.registerAllCommands(dispatcher, registry);
      }

      private static void removeVanillaCommand(CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
         try {
            Collection<CommandNode<CommandSourceStack>> commands = dispatcher.getRoot().getChildren();
            commands.removeIf(node -> node.getName().equals(commandName));
            NeoEssentials.LOGGER.debug("Removed vanilla command: /{}", commandName);
         } catch (Exception var3) {
            NeoEssentials.LOGGER.warn("Failed to remove vanilla command /{}: {}", commandName, var3.getMessage());
         }
      }
   }
}
