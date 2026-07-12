package com.zerog.neoessentials.api;

import com.zerog.neoessentials.config.ConfigManager;
import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.economy.managers.PayToggleManager;
import com.zerog.neoessentials.economy.managers.TransactionHistoryManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class EconomyAPI {
   public static BigDecimal getBalance(UUID player) {
      return EconomyManager.getInstance().getBalance(player);
   }

   public static void setBalance(UUID player, BigDecimal amount) {
      EconomyManager.getInstance().setBalance(player, amount);
   }

   public static boolean deposit(UUID player, BigDecimal amount) {
      return EconomyManager.getInstance().addBalance(player, amount);
   }

   public static boolean withdraw(UUID player, BigDecimal amount) {
      return EconomyManager.getInstance().subtractBalance(player, amount);
   }

   public static boolean isPayToggled(UUID player) {
      return PayToggleManager.getInstance().getPayToggle(player);
   }

   public static void setPayToggle(UUID player, boolean enabled) {
      PayToggleManager.getInstance().setPayToggle(player, enabled);
   }

   public static List<String> getTransactionHistory(UUID player) {
      return TransactionHistoryManager.getInstance().getHistory(player);
   }

   public static boolean payPlayer(UUID sender, UUID receiver, BigDecimal amount) {
      if (sender.equals(receiver)) {
         return false;
      } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
         return false;
      } else {
         EconomyManager manager = EconomyManager.getInstance();
         double taxPercent = ConfigManager.getTaxPercentage();
         BigDecimal fee = amount.multiply(BigDecimal.valueOf(taxPercent / 100.0));
         BigDecimal netAmount = amount.subtract(fee);
         if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
         } else {
            boolean senderSuccess = manager.subtractBalance(sender, amount);
            if (!senderSuccess) {
               return false;
            } else {
               boolean receiverSuccess = manager.addBalance(receiver, netAmount);
               if (!receiverSuccess) {
                  manager.addBalance(sender, amount);
                  return false;
               } else {
                  return true;
               }
            }
         }
      }
   }
}
