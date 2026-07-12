package com.zerog.neoessentials.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static TaskManager INSTANCE;
   private static final Path TASKS_DIR = Paths.get("neoessentials", "scheduler");
   private static final Path TASKS_FILE = TASKS_DIR.resolve("tasks.json");
   private static final Path HISTORY_FILE = TASKS_DIR.resolve("execution_history.json");
   private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
   private final Map<String, List<TaskManager.TaskExecution>> executionHistory = new ConcurrentHashMap<>();
   private static final int MAX_HISTORY_PER_TASK = 100;

   private TaskManager() {
      try {
         if (!Files.exists(TASKS_DIR)) {
            Files.createDirectories(TASKS_DIR);
         }

         this.loadTasks();
         this.loadExecutionHistory();
      } catch (IOException var2) {
         LOGGER.error("Failed to initialize tasks directory", var2);
      }
   }

   public static TaskManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new TaskManager();
      }

      return INSTANCE;
   }

   public ScheduledTask createTask(
      String name, String cronExpression, ScheduledTask.TaskType taskType, List<String> commands, String timezone, String createdBy
   ) {
      if (!CronParser.isValid(cronExpression)) {
         throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
      } else {
         ScheduledTask task = new ScheduledTask(name, cronExpression, taskType);
         task.setCommands(commands);
         task.setTimezone(timezone != null ? timezone : ZoneId.systemDefault().getId());
         task.setCreatedBy(createdBy);

         try {
            CronParser parser = new CronParser(cronExpression);
            ZoneId tz = ZoneId.of(task.getTimezone());
            long nextExecution = parser.getNextExecutionTime(tz);
            task.setNextExecutionTime(nextExecution);
         } catch (Exception var12) {
            LOGGER.error("Failed to calculate next execution time", var12);
         }

         this.tasks.put(task.getId(), task);
         this.saveTasks();
         LOGGER.info("Created scheduled task: {} ({})", name, task.getId());
         return task;
      }
   }

   public boolean updateTask(
      String id,
      String name,
      String cronExpression,
      Boolean enabled,
      List<String> commands,
      String timezone,
      String description,
      ScheduledTask.TaskConditions conditions
   ) {
      ScheduledTask task = this.tasks.get(id);
      if (task == null) {
         return false;
      } else {
         if (name != null) {
            task.setName(name);
         }

         if (description != null) {
            task.setDescription(description);
         }

         if (enabled != null) {
            task.setEnabled(enabled);
         }

         if (commands != null) {
            task.setCommands(commands);
         }

         if (timezone != null) {
            task.setTimezone(timezone);
         }

         if (conditions != null) {
            task.setConditions(conditions);
         }

         if (cronExpression != null && !cronExpression.equals(task.getCronExpression())) {
            if (!CronParser.isValid(cronExpression)) {
               throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
            }

            task.setCronExpression(cronExpression);

            try {
               CronParser parser = new CronParser(cronExpression);
               ZoneId tz = ZoneId.of(task.getTimezone());
               long nextExecution = parser.getNextExecutionTime(tz);
               task.setNextExecutionTime(nextExecution);
            } catch (Exception var14) {
               LOGGER.error("Failed to recalculate next execution time", var14);
            }
         }

         task.setUpdatedAt(System.currentTimeMillis());
         this.saveTasks();
         LOGGER.info("Updated scheduled task: {} ({})", task.getName(), id);
         return true;
      }
   }

   public boolean deleteTask(String id) {
      ScheduledTask removed = this.tasks.remove(id);
      if (removed != null) {
         this.executionHistory.remove(id);
         this.saveTasks();
         this.saveExecutionHistory();
         LOGGER.info("Deleted scheduled task: {} ({})", removed.getName(), id);
         return true;
      } else {
         return false;
      }
   }

   public ScheduledTask getTask(String id) {
      return this.tasks.get(id);
   }

   public Collection<ScheduledTask> getAllTasks() {
      return new ArrayList<>(this.tasks.values());
   }

   public List<ScheduledTask> getEnabledTasks() {
      List<ScheduledTask> enabled = new ArrayList<>();

      for (ScheduledTask task : this.tasks.values()) {
         if (task.isEnabled()) {
            enabled.add(task);
         }
      }

      return enabled;
   }

   public void recordExecution(String taskId, boolean success, String message, long executionTime) {
      TaskManager.TaskExecution execution = new TaskManager.TaskExecution();
      execution.taskId = taskId;
      execution.timestamp = System.currentTimeMillis();
      execution.success = success;
      execution.message = message;
      execution.executionTime = executionTime;
      List<TaskManager.TaskExecution> history = this.executionHistory.computeIfAbsent(taskId, k -> new ArrayList<>());
      history.add(0, execution);
      if (history.size() > 100) {
         history.remove(history.size() - 1);
      }

      ScheduledTask task = this.tasks.get(taskId);
      if (task != null) {
         task.setLastExecutionTime(execution.timestamp);
         task.incrementExecutionCount();

         try {
            CronParser parser = new CronParser(task.getCronExpression());
            ZoneId tz = ZoneId.of(task.getTimezone());
            long nextExecution = parser.getNextExecutionTime(tz);
            task.setNextExecutionTime(nextExecution);
         } catch (Exception var13) {
            LOGGER.error("Failed to calculate next execution time", var13);
         }
      }

      this.saveExecutionHistory();
      this.saveTasks();
   }

   public List<TaskManager.TaskExecution> getExecutionHistory(String taskId) {
      return this.executionHistory.getOrDefault(taskId, new ArrayList<>());
   }

   public boolean checkConditions(ScheduledTask task, MinecraftServer server) {
      ScheduledTask.TaskConditions conditions = task.getConditions();
      if (conditions == null) {
         return true;
      } else {
         int playerCount = server.getPlayerCount();
         if (conditions.getMinPlayers() != null && playerCount < conditions.getMinPlayers()) {
            return false;
         } else if (conditions.getMaxPlayers() != null && playerCount > conditions.getMaxPlayers()) {
            return false;
         } else {
            double avgTickTime = (double)server.getAverageTickTimeNanos() / 1000000.0;
            double tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgTickTime));
            double load = (20.0 - tps) / 20.0 * 100.0;
            if (conditions.getMaxServerLoad() != null && load > conditions.getMaxServerLoad()) {
               return false;
            } else {
               if (conditions.getStartTime() != null && conditions.getEndTime() != null) {
                  ZoneId tz = ZoneId.of(task.getTimezone());
                  ZonedDateTime now = ZonedDateTime.now(tz);
                  long currentTime = (long)now.getHour() * 3600000L + (long)now.getMinute() * 60000L + (long)now.getSecond() * 1000L;
                  if (currentTime < conditions.getStartTime() || currentTime > conditions.getEndTime()) {
                     return false;
                  }
               }

               return true;
            }
         }
      }
   }

   private void loadTasks() {
      try {
         if (Files.exists(TASKS_FILE)) {
            String json = Files.readString(TASKS_FILE, StandardCharsets.UTF_8);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            if (data.has("tasks")) {
               for (JsonElement element : data.getAsJsonArray("tasks")) {
                  try {
                     ScheduledTask task = this.parseTask(element.getAsJsonObject());
                     this.tasks.put(task.getId(), task);
                  } catch (Exception var7) {
                     LOGGER.error("Failed to parse task", var7);
                  }
               }
            }

            LOGGER.info("Loaded {} scheduled tasks from disk", this.tasks.size());
         }
      } catch (Exception var8) {
         LOGGER.error("Failed to load tasks", var8);
      }
   }

   private void saveTasks() {
      try {
         JsonObject data = new JsonObject();
         data.addProperty("lastUpdated", System.currentTimeMillis());
         data.addProperty("version", "1.0");
         JsonArray tasksArray = new JsonArray();

         for (ScheduledTask task : this.tasks.values()) {
            tasksArray.add(this.taskToJson(task));
         }

         data.add("tasks", tasksArray);
         Files.writeString(TASKS_FILE, GSON.toJson(data), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception var5) {
         LOGGER.error("Failed to save tasks", var5);
      }
   }

   private void loadExecutionHistory() {
      try {
         if (Files.exists(HISTORY_FILE)) {
            String json = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            if (data.has("history")) {
               JsonObject historyObj = data.getAsJsonObject("history");

               for (String taskId : historyObj.keySet()) {
                  JsonArray executions = historyObj.getAsJsonArray(taskId);
                  List<TaskManager.TaskExecution> execList = new ArrayList<>();

                  for (JsonElement element : executions) {
                     execList.add((TaskManager.TaskExecution)GSON.fromJson(element, TaskManager.TaskExecution.class));
                  }

                  this.executionHistory.put(taskId, execList);
               }
            }

            LOGGER.info("Loaded execution history for {} tasks", this.executionHistory.size());
         }
      } catch (Exception var10) {
         LOGGER.error("Failed to load execution history", var10);
      }
   }

   private void saveExecutionHistory() {
      try {
         JsonObject data = new JsonObject();
         data.addProperty("lastUpdated", System.currentTimeMillis());
         JsonObject historyObj = new JsonObject();

         for (Entry<String, List<TaskManager.TaskExecution>> entry : this.executionHistory.entrySet()) {
            JsonArray executions = new JsonArray();

            for (TaskManager.TaskExecution exec : entry.getValue()) {
               executions.add(GSON.toJsonTree(exec));
            }

            historyObj.add(entry.getKey(), executions);
         }

         data.add("history", historyObj);
         Files.writeString(HISTORY_FILE, GSON.toJson(data), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception var8) {
         LOGGER.error("Failed to save execution history", var8);
      }
   }

   private JsonObject taskToJson(ScheduledTask task) {
      return GSON.toJsonTree(task).getAsJsonObject();
   }

   private ScheduledTask parseTask(JsonObject obj) {
      return (ScheduledTask)GSON.fromJson(obj, ScheduledTask.class);
   }

   public static class TaskExecution {
      public String taskId;
      public long timestamp;
      public boolean success;
      public String message;
      public long executionTime;
   }
}
