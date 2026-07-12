package com.zerog.neoessentials.economy;

import com.zerog.neoessentials.api.permissions.PermissionAPI;
import java.util.UUID;

public class EconomyPermissions {
   public static final String VIEW_BALANCE = "neoessentials.economy.balance";
   public static final String PAY = "neoessentials.economy.pay";
   public static final String ADMIN = "neoessentials.economy.eco";
   public static final String LEADERBOARD = "neoessentials.economy.baltop";

   public static boolean canViewBalance(UUID uuid) {
      return PermissionAPI.hasPermission(uuid, "neoessentials.economy.balance");
   }

   public static boolean canPay(UUID uuid) {
      return PermissionAPI.hasPermission(uuid, "neoessentials.economy.pay");
   }

   public static boolean isAdmin(UUID uuid) {
      return PermissionAPI.hasPermission(uuid, "neoessentials.economy.eco");
   }

   public static boolean canViewLeaderboard(UUID uuid) {
      return PermissionAPI.hasPermission(uuid, "neoessentials.economy.baltop");
   }
}
