package com.zerog.neoessentials.scheduler;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CronParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(CronParser.class);
   private final String cronExpression;
   private final int[] minutes;
   private final int[] hours;
   private final int[] daysOfMonth;
   private final int[] months;
   private final int[] daysOfWeek;

   public CronParser(String cronExpression) throws IllegalArgumentException {
      this.cronExpression = cronExpression;
      String[] parts = cronExpression.trim().split("\\s+");
      if (parts.length != 5) {
         throw new IllegalArgumentException("Invalid cron expression. Expected 5 fields: minute hour day month dayOfWeek");
      } else {
         try {
            this.minutes = this.parseField(parts[0], 0, 59);
            this.hours = this.parseField(parts[1], 0, 23);
            this.daysOfMonth = this.parseField(parts[2], 1, 31);
            this.months = this.parseField(parts[3], 1, 12);
            this.daysOfWeek = this.parseField(parts[4], 0, 6);
         } catch (Exception var4) {
            throw new IllegalArgumentException("Failed to parse cron expression: " + cronExpression, var4);
         }
      }
   }

   private int[] parseField(String field, int min, int max) {
      if ("*".equals(field)) {
         int[] values = new int[max - min + 1];

         for (int i = 0; i < values.length; i++) {
            values[i] = min + i;
         }

         return values;
      } else if (field.contains(",")) {
         String[] parts = field.split(",");
         int[] values = new int[parts.length];

         for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
         }

         return values;
      } else if (field.contains("/")) {
         String[] parts = field.split("/");
         int step = Integer.parseInt(parts[1]);
         int rangeMin = min;
         int rangeMax = max;
         if (!parts[0].equals("*")) {
            if (parts[0].contains("-")) {
               String[] range = parts[0].split("-");
               rangeMin = Integer.parseInt(range[0]);
               rangeMax = Integer.parseInt(range[1]);
            } else {
               rangeMin = Integer.parseInt(parts[0]);
               rangeMax = max;
            }
         }

         int count = (rangeMax - rangeMin) / step + 1;
         int[] values = new int[count];

         for (int i = 0; i < count; i++) {
            values[i] = rangeMin + i * step;
         }

         return values;
      } else if (!field.contains("-")) {
         return new int[]{Integer.parseInt(field)};
      } else {
         String[] parts = field.split("-");
         int rangeMin = Integer.parseInt(parts[0]);
         int rangeMax = Integer.parseInt(parts[1]);
         int[] values = new int[rangeMax - rangeMin + 1];

         for (int i = 0; i < values.length; i++) {
            values[i] = rangeMin + i;
         }

         return values;
      }
   }

   public long getNextExecutionTime(ZoneId timezone) {
      return this.getNextExecutionTime(ZonedDateTime.now(timezone));
   }

   public long getNextExecutionTime(ZonedDateTime from) {
      ZonedDateTime next = from.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1L);

      for (int i = 0; i < 2102400; i++) {
         if (this.matches(next)) {
            return next.toInstant().toEpochMilli();
         }

         next = next.plusMinutes(1L);
      }

      LOGGER.error("Failed to find next execution time for cron: {}", this.cronExpression);
      return -1L;
   }

   public boolean matches(ZonedDateTime time) {
      int minute = time.getMinute();
      int hour = time.getHour();
      int dayOfMonth = time.getDayOfMonth();
      int month = time.getMonthValue();
      int dayOfWeek = time.getDayOfWeek().getValue() % 7;
      return this.contains(this.minutes, minute)
         && this.contains(this.hours, hour)
         && this.contains(this.daysOfMonth, dayOfMonth)
         && this.contains(this.months, month)
         && this.contains(this.daysOfWeek, dayOfWeek);
   }

   private boolean contains(int[] array, int value) {
      for (int v : array) {
         if (v == value) {
            return true;
         }
      }

      return false;
   }

   public static boolean isValid(String cronExpression) {
      try {
         new CronParser(cronExpression);
         return true;
      } catch (Exception var2) {
         return false;
      }
   }

   public String getDescription() {
      StringBuilder desc = new StringBuilder();
      if (this.isEveryMinute()) {
         desc.append("Every minute");
      } else if (this.isHourly()) {
         desc.append("Every hour at minute ").append(this.minutes[0]);
      } else if (this.isDaily()) {
         desc.append("Every day at ").append(this.formatTime(this.hours[0], this.minutes[0]));
      } else if (this.isWeekly()) {
         desc.append("Every ").append(this.getDayName(this.daysOfWeek[0])).append(" at ").append(this.formatTime(this.hours[0], this.minutes[0]));
      } else if (this.isMonthly()) {
         desc.append("Every month on day ").append(this.daysOfMonth[0]).append(" at ").append(this.formatTime(this.hours[0], this.minutes[0]));
      } else {
         desc.append("Custom schedule: ").append(this.cronExpression);
      }

      return desc.toString();
   }

   private boolean isEveryMinute() {
      return this.minutes.length == 60 && this.hours.length == 24;
   }

   private boolean isHourly() {
      return this.minutes.length == 1 && this.hours.length == 24;
   }

   private boolean isDaily() {
      return this.minutes.length == 1 && this.hours.length == 1 && this.daysOfMonth.length == 31;
   }

   private boolean isWeekly() {
      return this.minutes.length == 1 && this.hours.length == 1 && this.daysOfWeek.length == 1;
   }

   private boolean isMonthly() {
      return this.minutes.length == 1 && this.hours.length == 1 && this.daysOfMonth.length == 1 && this.months.length == 12;
   }

   private String formatTime(int hour, int minute) {
      return String.format("%02d:%02d", hour, minute);
   }

   private String getDayName(int dayOfWeek) {
      String[] days = new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
      return days[dayOfWeek];
   }
}
