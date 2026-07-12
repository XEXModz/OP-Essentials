package com.zerog.neoessentials.api;

import com.zerog.neoessentials.NeoEssentialsManager;
import com.zerog.neoessentials.api.economy.EconomyService;

public class NeoEssentialsAPI {
   public static final String API_VERSION = "1.0.0";

   public static boolean isAvailable() {
      return true;
   }

   public static EconomyService getEconomyService() {
      return NeoEssentialsManager.getInstance().getEconomyService();
   }
}
