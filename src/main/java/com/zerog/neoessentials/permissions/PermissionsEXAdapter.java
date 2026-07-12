package com.zerog.neoessentials.permissions;

import com.zerog.neoessentials.api.permissions.PermissionRegistry;
import com.zerog.neoessentials.api.permissions.PermissionScanner;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionsEXAdapter implements ExternalPermissionAdapter {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsEXAdapter.class);
   private static final String PEX_PERMISSIONS_FILE = "neoessentials/pex-permissions.txt";

   public PermissionsEXAdapter() {
      this.exportPermissionsForPEX();
   }

   @Override
   public boolean hasPermission(UUID uuid, String permission) {
      return false;
   }

   @Override
   public String getPrefix(UUID uuid) {
      return null;
   }

   @Override
   public String getSuffix(UUID uuid) {
      return null;
   }

   @Override
   public void reload() {
      LOGGER.info("Reloading PermissionsEX adapter - re-exporting permissions");
      this.exportPermissionsForPEX();
   }

   @Override
   public String getName() {
      return "PermissionsEX";
   }

   @Override
   public boolean isAvailable() {
      return false;
   }

   private void exportPermissionsForPEX() {
      try {
         LOGGER.info("Exporting NeoEssentials permissions for PermissionsEX tab completion...");
         PermissionRegistry registry = PermissionRegistry.getInstance();
         PermissionScanner scanner = PermissionScanner.getInstance();
         scanner.scanForPermissions();
         Set<String> allPermissions = registry.getAllPermissions();
         Set<String> discoveredPermissions = scanner.getDiscoveredPermissions();
         File dataDir = new File("neoessentials/");
         if (!dataDir.exists() && !dataDir.mkdirs()) {
            LOGGER.error("Failed to create data directory: {}", dataDir.getAbsolutePath());
         }

         File permFile = new File("neoessentials/pex-permissions.txt");

         try (FileWriter writer = new FileWriter(permFile)) {
            writer.write("# NeoEssentials Permissions Export for PermissionsEX\n");
            writer.write("# Generated automatically - DO NOT EDIT MANUALLY\n");
            writer.write("# This file helps PermissionsEX provide tab completion for NeoEssentials permissions\n");
            writer.write("# Use these permissions with /pex group <group> add <permission>\n\n");
            writer.write("# === REGISTERED PERMISSIONS ===\n");
            allPermissions.stream().sorted().forEach(perm -> {
               try {
                  writer.write(perm + "\n");
               } catch (IOException var3x) {
                  LOGGER.error("Error writing permission: " + perm, var3x);
               }
            });
            writer.write("\n# === AUTO-DISCOVERED PERMISSIONS ===\n");
            discoveredPermissions.stream().filter(perm -> !allPermissions.contains(perm)).sorted().forEach(perm -> {
               try {
                  writer.write(perm + "\n");
               } catch (IOException var3x) {
                  LOGGER.error("Error writing discovered permission: " + perm, var3x);
               }
            });
            writer.write("\n# === WILDCARD PERMISSIONS ===\n");
            writer.write("neoessentials.*\n");
            writer.write("neoessentials.admin.*\n");
            writer.write("neoessentials.economy.*\n");
            writer.write("neoessentials.teleport.*\n");
            writer.write("neoessentials.chat.*\n");
            writer.write("neoessentials.kits.*\n");
            writer.write("neoessentials.moderation.*\n");
            writer.write("neoessentials.utilities.*\n");
         }

         LOGGER.info(
            "Successfully exported {} registered and {} discovered permissions to {}",
            new Object[]{allPermissions.size(), discoveredPermissions.size(), "neoessentials/pex-permissions.txt"}
         );
         LOGGER.info("=== PermissionsEX Integration Help ===");
         LOGGER.info("For PermissionsEX tab completion to work properly:");
         LOGGER.info("1. Install a permissions plugin that supports permission registration");
         LOGGER.info("2. Use the exported permissions file: {}", "neoessentials/pex-permissions.txt");
         LOGGER.info("3. Or manually register permissions with your permission plugin");
         LOGGER.info("4. Use '/neoessentials-permissions export pex' to regenerate this file");
      } catch (Exception var12) {
         LOGGER.error("Failed to export permissions for PermissionsEX", var12);
      }
   }

   public void exportPermissions(String format, File outputFile) throws IOException {
      PermissionRegistry registry = PermissionRegistry.getInstance();
      PermissionScanner scanner = PermissionScanner.getInstance();
      scanner.scanForPermissions();
      Set<String> allPermissions = registry.getAllPermissions();
      Set<String> discoveredPermissions = scanner.getDiscoveredPermissions();

      try (FileWriter writer = new FileWriter(outputFile)) {
         String var8 = format.toLowerCase();
         switch (var8) {
            case "pex":
            case "permissionsex":
               this.exportForPermissionsEX(writer, allPermissions, discoveredPermissions);
               break;
            case "luckperms":
               this.exportForLuckPerms(writer, allPermissions, discoveredPermissions);
               break;
            case "yaml":
               this.exportAsYAML(writer, allPermissions, discoveredPermissions);
               break;
            case "json":
               this.exportAsJSON(writer, allPermissions, discoveredPermissions);
               break;
            default:
               this.exportAsText(writer, allPermissions, discoveredPermissions);
         }
      }

      LOGGER.info("Exported permissions in {} format to {}", format, outputFile.getAbsolutePath());
   }

   private void exportForPermissionsEX(FileWriter writer, Set<String> registered, Set<String> discovered) throws IOException {
      writer.write("# PermissionsEX format - use with /pex group <group> add <permission>\n");
      registered.stream().sorted().forEach(perm -> {
         try {
            writer.write(perm + "\n");
         } catch (IOException var3x) {
         }
      });
      discovered.stream().filter(p -> !registered.contains(p)).sorted().forEach(perm -> {
         try {
            writer.write(perm + "\n");
         } catch (IOException var3x) {
         }
      });
   }

   private void exportForLuckPerms(FileWriter writer, Set<String> registered, Set<String> discovered) throws IOException {
      writer.write("# LuckPerms format - use with /lp group <group> permission set <permission> true\n");
      registered.stream().sorted().forEach(perm -> {
         try {
            writer.write("/lp group default permission set " + perm + " false\n");
         } catch (IOException var3x) {
         }
      });
   }

   private void exportAsYAML(FileWriter writer, Set<String> registered, Set<String> discovered) throws IOException {
      writer.write("neoessentials_permissions:\n");
      writer.write("  registered:\n");
      registered.stream().sorted().forEach(perm -> {
         try {
            writer.write("    - \"" + perm + "\"\n");
         } catch (IOException var3x) {
         }
      });
      writer.write("  discovered:\n");
      discovered.stream().filter(p -> !registered.contains(p)).sorted().forEach(perm -> {
         try {
            writer.write("    - \"" + perm + "\"\n");
         } catch (IOException var3x) {
         }
      });
   }

   private void exportAsJSON(FileWriter writer, Set<String> registered, Set<String> discovered) throws IOException {
      writer.write("{\n");
      writer.write("  \"neoessentials_permissions\": {\n");
      writer.write("    \"registered\": [\n");
      String[] regArray = registered.stream().sorted().toArray(String[]::new);

      for (int i = 0; i < regArray.length; i++) {
         writer.write("      \"" + regArray[i] + "\"" + (i < regArray.length - 1 ? "," : "") + "\n");
      }

      writer.write("    ],\n");
      writer.write("    \"discovered\": [\n");
      String[] discArray = discovered.stream().filter(p -> !registered.contains(p)).sorted().toArray(String[]::new);

      for (int i = 0; i < discArray.length; i++) {
         writer.write("      \"" + discArray[i] + "\"" + (i < discArray.length - 1 ? "," : "") + "\n");
      }

      writer.write("    ]\n");
      writer.write("  }\n");
      writer.write("}\n");
   }

   private void exportAsText(FileWriter writer, Set<String> registered, Set<String> discovered) throws IOException {
      writer.write("=== NeoEssentials Permissions ===\n\n");
      writer.write("Registered Permissions:\n");
      registered.stream().sorted().forEach(perm -> {
         try {
            writer.write(perm + "\n");
         } catch (IOException var3x) {
         }
      });
      writer.write("\nDiscovered Permissions:\n");
      discovered.stream().filter(p -> !registered.contains(p)).sorted().forEach(perm -> {
         try {
            writer.write(perm + "\n");
         } catch (IOException var3x) {
         }
      });
   }
}
