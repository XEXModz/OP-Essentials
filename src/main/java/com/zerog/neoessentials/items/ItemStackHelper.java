package com.zerog.neoessentials.items;

import com.zerog.neoessentials.config.ConfigManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemStackHelper {
   private static final Logger LOGGER = LoggerFactory.getLogger(ItemStackHelper.class);

   public static int getMaxStackSize(Item item) {
      int defaultSize = ConfigManager.getDefaultStackSize();
      int oversizedSize = ConfigManager.getOversizedStackSize();
      if (defaultSize == -1) {
         int vanillaMax = item.getDefaultMaxStackSize();
         return Math.max(vanillaMax, oversizedSize);
      } else {
         return Math.min(defaultSize, oversizedSize);
      }
   }

   public static int getMaxStackSize(ItemStack stack) {
      return stack.isEmpty() ? 0 : getMaxStackSize(stack.getItem());
   }

   public static boolean canAcceptAmount(ItemStack stack, int amount) {
      return stack.isEmpty() ? amount <= getMaxStackSize(stack.getItem()) : stack.getCount() + amount <= getMaxStackSize(stack);
   }

   public static int setCount(ItemStack stack, int count) {
      int maxStack = getMaxStackSize(stack);
      int actualCount = Math.min(count, maxStack);
      stack.setCount(actualCount);
      return actualCount;
   }

   public static int growStack(ItemStack stack, int amount) {
      int maxStack = getMaxStackSize(stack);
      int currentCount = stack.getCount();
      int newCount = Math.min(currentCount + amount, maxStack);
      int actualAdded = newCount - currentCount;
      stack.setCount(newCount);
      return actualAdded;
   }

   public static boolean isStackable(Item item) {
      return getMaxStackSize(item) > 1;
   }
}
