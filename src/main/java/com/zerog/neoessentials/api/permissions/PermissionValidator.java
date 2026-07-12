package com.zerog.neoessentials.api.permissions;

import com.zerog.neoessentials.permissions.PermissionGroup;
import com.zerog.neoessentials.permissions.PermissionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionValidator {
   private static final Logger LOGGER = LoggerFactory.getLogger(PermissionValidator.class);

   public static PermissionValidator.ValidationResult validate(PermissionManager manager) {
      LOGGER.info("═══════════════════════════════════════════════════════════");
      LOGGER.info("Validating Permission Nodes...");
      LOGGER.info("═══════════════════════════════════════════════════════════");
      PermissionRegistry registry = PermissionRegistry.getInstance();
      Set<String> registeredPermissions = registry.getAllPermissions();
      List<String> warnings = new ArrayList<>();
      List<String> suggestions = new ArrayList<>();
      int totalChecked = 0;
      int issuesFound = 0;

      for (PermissionGroup group : manager.getGroups()) {
         LOGGER.info("Checking group '{}'...", group.getName());

         for (String permission : group.getPermissions()) {
            totalChecked++;
            if (!permission.endsWith(".*") && !permission.equals("*")) {
               if (permission.startsWith("-")) {
                  String actualPerm = permission.substring(1);
                  if (!registeredPermissions.contains(actualPerm) && !actualPerm.endsWith(".*")) {
                     warnings.add(String.format("  ⚠ Group '%s': Negative permission '%s' not registered", group.getName(), permission));
                     issuesFound++;
                  }
               } else if (!registeredPermissions.contains(permission)) {
                  String suggestion = findSimilarPermission(permission, registeredPermissions);
                  if (suggestion != null) {
                     warnings.add(String.format("  ✗ Group '%s': Permission '%s' not registered", group.getName(), permission));
                     suggestions.add(String.format("    → Did you mean '%s'?", suggestion));
                     issuesFound++;
                  } else {
                     warnings.add(String.format("  ✗ Group '%s': Unknown permission '%s'", group.getName(), permission));
                     issuesFound++;
                  }
               }
            }
         }
      }

      LOGGER.info("─────────────────────────────────────────────────────────────");
      LOGGER.info("Validation Results:");
      LOGGER.info("  Total permissions checked: {}", totalChecked);
      LOGGER.info("  Registered permissions: {}", registeredPermissions.size());
      LOGGER.info("  Issues found: {}", issuesFound);
      if (!warnings.isEmpty()) {
         LOGGER.warn("─────────────────────────────────────────────────────────────");
         LOGGER.warn("Permission Issues Detected:");

         for (String warning : warnings) {
            LOGGER.warn(warning);
         }

         if (!suggestions.isEmpty()) {
            LOGGER.warn("Suggestions:");

            for (String suggestion : suggestions) {
               LOGGER.warn(suggestion);
            }
         }
      }

      LOGGER.info("═══════════════════════════════════════════════════════════");
      return new PermissionValidator.ValidationResult(totalChecked, issuesFound, warnings, suggestions);
   }

   private static String findSimilarPermission(String permission, Set<String> registeredPermissions) {
      int minDistance = Integer.MAX_VALUE;
      String bestMatch = null;

      for (String registered : registeredPermissions) {
         if (Math.abs(registered.length() - permission.length()) <= 10) {
            int distance = levenshteinDistance(permission, registered);
            if (distance < minDistance && distance <= 3) {
               minDistance = distance;
               bestMatch = registered;
            }
         }
      }

      if (bestMatch == null) {
         for (String registeredx : registeredPermissions) {
            if (permission.replace("items.", "item.").equals(registeredx)
               || permission.replace("teleportation.", "teleport.").equals(registeredx)
               || permission.replace("utils.", "").equals(registeredx)
               || permission.replace("paytoggle", "pay.toggle").equals(registeredx)) {
               return registeredx;
            }
         }
      }

      return bestMatch;
   }

   private static int levenshteinDistance(String s1, String s2) {
      int len1 = s1.length();
      int len2 = s2.length();
      int[][] dp = new int[len1 + 1][len2 + 1];
      int i = 0;

      while (i <= len1) {
         dp[i][0] = i++;
      }

      i = 0;

      while (i <= len2) {
         dp[0][i] = i++;
      }

      for (int ix = 1; ix <= len1; ix++) {
         for (int j = 1; j <= len2; j++) {
            int cost = s1.charAt(ix - 1) == s2.charAt(j - 1) ? 0 : 1;
            dp[ix][j] = Math.min(Math.min(dp[ix - 1][j] + 1, dp[ix][j - 1] + 1), dp[ix - 1][j - 1] + cost);
         }
      }

      return dp[len1][len2];
   }

   public static class ValidationResult {
      private final int totalChecked;
      private final int issuesFound;
      private final List<String> warnings;
      private final List<String> suggestions;

      public ValidationResult(int totalChecked, int issuesFound, List<String> warnings, List<String> suggestions) {
         this.totalChecked = totalChecked;
         this.issuesFound = issuesFound;
         this.warnings = new ArrayList<>(warnings);
         this.suggestions = new ArrayList<>(suggestions);
      }

      public int getTotalChecked() {
         return this.totalChecked;
      }

      public int getIssuesFound() {
         return this.issuesFound;
      }

      public List<String> getWarnings() {
         return Collections.unmodifiableList(this.warnings);
      }

      public List<String> getSuggestions() {
         return Collections.unmodifiableList(this.suggestions);
      }

      public boolean hasIssues() {
         return this.issuesFound > 0;
      }
   }
}
