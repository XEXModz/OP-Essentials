package com.zerog.neoessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger(CommandRegistry.class);
   private static final CommandRegistry INSTANCE = new CommandRegistry();
   private final Map<String, CommandRegistry.CommandInfo> commands = new ConcurrentHashMap<>();
   private final Map<String, String> aliases = new ConcurrentHashMap<>();

   private CommandRegistry() {
   }

   public static CommandRegistry getInstance() {
      return INSTANCE;
   }

   public void registerCommand(String name, String description, String... aliases) {
      CommandRegistry.CommandInfo info = new CommandRegistry.CommandInfo(name, description, Arrays.asList(aliases));
      this.commands.put(name.toLowerCase(), info);

      for (String alias : aliases) {
         this.aliases.put(alias.toLowerCase(), name.toLowerCase());
      }

      LOGGER.debug("Registered command: {} with {} aliases", name, aliases.length);
   }

   public void registerCommand(String name) {
      this.registerCommand(name, "NeoEssentials command");
   }

   public Set<String> getAllCommandNames() {
      Set<String> allNames = new HashSet<>(this.commands.keySet());
      allNames.addAll(this.aliases.keySet());
      return allNames;
   }

   public Set<String> getPrimaryCommandNames() {
      return new HashSet<>(this.commands.keySet());
   }

   public CommandRegistry.CommandInfo getCommandInfo(String name) {
      String key = name.toLowerCase();
      CommandRegistry.CommandInfo info = this.commands.get(key);
      if (info != null) {
         return info;
      } else {
         String primaryName = this.aliases.get(key);
         return primaryName != null ? this.commands.get(primaryName) : null;
      }
   }

   public boolean isCommandRegistered(String name) {
      String key = name.toLowerCase();
      return this.commands.containsKey(key) || this.aliases.containsKey(key);
   }

   public boolean isCommandActuallyRegistered(String name, CommandDispatcher<CommandSourceStack> dispatcher) {
      String key = name.toLowerCase();
      if (!this.isCommandRegistered(key)) {
         return false;
      } else {
         try {
            RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
            return root.getChildren().stream().anyMatch(node -> node.getName().equalsIgnoreCase(key));
         } catch (Exception var5) {
            LOGGER.debug("Command '{}' not found in dispatcher: {}", key, var5.getMessage());
            return false;
         }
      }
   }

   public List<CommandRegistry.CommandInfo> getAllCommandsSorted() {
      List<CommandRegistry.CommandInfo> result = new ArrayList<>(this.commands.values());
      result.sort(Comparator.comparing(CommandRegistry.CommandInfo::getName));
      return result;
   }

   public void clear() {
      this.commands.clear();
      this.aliases.clear();
      LOGGER.debug("Command registry cleared");
   }

   public Map<String, Integer> getStats() {
      Map<String, Integer> stats = new HashMap<>();
      stats.put("commands", this.commands.size());
      stats.put("aliases", this.aliases.size());
      stats.put("total", this.commands.size() + this.aliases.size());
      return stats;
   }

   public static class CommandInfo {
      private final String name;
      private final String description;
      private final List<String> aliases;

      public CommandInfo(String name, String description, List<String> aliases) {
         this.name = name;
         this.description = description != null ? description : "NeoEssentials command";
         this.aliases = new ArrayList<>(aliases);
      }

      public String getName() {
         return this.name;
      }

      public String getDescription() {
         return this.description;
      }

      public List<String> getAliases() {
         return new ArrayList<>(this.aliases);
      }

      public boolean hasAliases() {
         return !this.aliases.isEmpty();
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder(this.name);
         if (!this.aliases.isEmpty()) {
            sb.append(" (").append(String.join(", ", this.aliases)).append(")");
         }

         sb.append(" - ").append(this.description);
         return sb.toString();
      }
   }
}
