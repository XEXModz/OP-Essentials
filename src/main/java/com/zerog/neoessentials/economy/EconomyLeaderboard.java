package com.zerog.neoessentials.economy;

import com.zerog.neoessentials.economy.managers.EconomyManager;
import com.zerog.neoessentials.util.MessageUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class EconomyLeaderboard {
   public static List<Entry<UUID, BigDecimal>> getTopPlayers(int topN) {
      Map<UUID, BigDecimal> allBalances = EconomyManager.getInstance().getAllBalances();
      return allBalances.entrySet().stream().sorted(Entry.<UUID, BigDecimal>comparingByValue().reversed()).limit((long)topN).collect(Collectors.toList());
   }

   public static List<String> formatLeaderboard(int topN) {
      List<Entry<UUID, BigDecimal>> top = getTopPlayers(topN);
      List<String> lines = new ArrayList<>();
      int rank = 1;

      for (Entry<UUID, BigDecimal> entry : top) {
         lines.add(MessageUtil.localize("commands.neoessentials.economy.leaderboard_entry", rank++, entry.getKey(), entry.getValue().toPlainString()));
      }

      return lines;
   }
}
