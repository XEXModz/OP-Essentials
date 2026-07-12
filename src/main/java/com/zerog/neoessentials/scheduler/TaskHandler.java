package com.zerog.neoessentials.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(TaskHandler.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String method = exchange.getRequestMethod();

      try {
         if ("GET".equals(method)) {
            this.handleGet(exchange, path);
         } else if ("POST".equals(method)) {
            this.handlePost(exchange, path);
         } else if ("PUT".equals(method)) {
            this.handlePut(exchange, path);
         } else if ("DELETE".equals(method)) {
            this.handleDelete(exchange, path);
         } else {
            this.sendErrorResponse(exchange, 405, "Method not allowed");
         }
      } catch (Exception var5) {
         LOGGER.error("Error handling tasks request", var5);
         this.sendErrorResponse(exchange, 500, "Internal server error: " + var5.getMessage());
      }
   }

   private void handleGet(HttpExchange exchange, String path) throws IOException {
      if (path.endsWith("/tasks")) {
         this.handleGetTasks(exchange);
      } else if (path.contains("/tasks/") && path.endsWith("/history")) {
         this.handleGetTaskHistory(exchange, path);
      } else if (path.contains("/tasks/")) {
         this.handleGetTask(exchange, path);
      } else if (path.endsWith("/tasks/timezones")) {
         this.handleGetTimezones(exchange);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handlePost(HttpExchange exchange, String path) throws IOException {
      if (path.endsWith("/tasks")) {
         this.handleCreateTask(exchange);
      } else if (path.contains("/tasks/") && path.endsWith("/execute")) {
         this.handleExecuteTask(exchange, path);
      } else if (path.endsWith("/tasks/validate")) {
         this.handleValidateCron(exchange);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handlePut(HttpExchange exchange, String path) throws IOException {
      if (path.contains("/tasks/")) {
         this.handleUpdateTask(exchange, path);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handleDelete(HttpExchange exchange, String path) throws IOException {
      if (path.contains("/tasks/")) {
         this.handleDeleteTask(exchange, path);
      } else {
         this.sendErrorResponse(exchange, 404, "Endpoint not found");
      }
   }

   private void handleGetTasks(HttpExchange exchange) throws IOException {
      TaskManager manager = TaskManager.getInstance();
      Collection<ScheduledTask> tasks = manager.getAllTasks();
      JsonObject response = new JsonObject();
      response.addProperty("timestamp", System.currentTimeMillis());
      response.addProperty("taskCount", tasks.size());
      JsonArray tasksArray = new JsonArray();

      for (ScheduledTask task : tasks) {
         tasksArray.add(this.taskToJson(task));
      }

      response.add("tasks", tasksArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleGetTask(HttpExchange exchange, String path) throws IOException {
      String taskId = this.extractTaskId(path);
      TaskManager manager = TaskManager.getInstance();
      ScheduledTask task = manager.getTask(taskId);
      if (task == null) {
         this.sendErrorResponse(exchange, 404, "Task not found");
      } else {
         JsonObject response = new JsonObject();
         response.addProperty("timestamp", System.currentTimeMillis());
         response.add("task", this.taskToJson(task));
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleGetTaskHistory(HttpExchange exchange, String path) throws IOException {
      String taskId = this.extractTaskId(path);
      TaskManager manager = TaskManager.getInstance();
      List<TaskManager.TaskExecution> history = manager.getExecutionHistory(taskId);
      JsonObject response = new JsonObject();
      response.addProperty("timestamp", System.currentTimeMillis());
      response.addProperty("taskId", taskId);
      response.addProperty("executionCount", history.size());
      JsonArray historyArray = new JsonArray();

      for (TaskManager.TaskExecution exec : history) {
         JsonObject execObj = new JsonObject();
         execObj.addProperty("timestamp", exec.timestamp);
         execObj.addProperty("success", exec.success);
         execObj.addProperty("message", exec.message);
         execObj.addProperty("executionTime", exec.executionTime);
         historyArray.add(execObj);
      }

      response.add("history", historyArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleGetTimezones(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("timestamp", System.currentTimeMillis());
      JsonArray timezonesArray = new JsonArray();

      for (String zoneId : ZoneId.getAvailableZoneIds()) {
         timezonesArray.add(zoneId);
      }

      response.add("timezones", timezonesArray);
      response.addProperty("timezoneCount", timezonesArray.size());
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleCreateTask(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject data = JsonParser.parseString(requestBody).getAsJsonObject();
      String name = data.get("name").getAsString();
      String cronExpression = data.get("cronExpression").getAsString();
      String taskTypeStr = data.get("taskType").getAsString();
      ScheduledTask.TaskType taskType = ScheduledTask.TaskType.valueOf(taskTypeStr.toUpperCase());
      List<String> commands = new ArrayList<>();
      if (data.has("commands")) {
         JsonArray commandsArray = data.getAsJsonArray("commands");

         for (int i = 0; i < commandsArray.size(); i++) {
            commands.add(commandsArray.get(i).getAsString());
         }
      }

      String timezone = data.has("timezone") ? data.get("timezone").getAsString() : null;
      String createdBy = this.getUsernameFromSession(exchange);

      try {
         TaskManager manager = TaskManager.getInstance();
         ScheduledTask task = manager.createTask(name, cronExpression, taskType, commands, timezone, createdBy);
         if (data.has("description")) {
            task.setDescription(data.get("description").getAsString());
         }

         if (data.has("conditions")) {
            JsonObject condObj = data.getAsJsonObject("conditions");
            ScheduledTask.TaskConditions conditions = new ScheduledTask.TaskConditions();
            if (condObj.has("minPlayers")) {
               conditions.setMinPlayers(condObj.get("minPlayers").getAsInt());
            }

            if (condObj.has("maxPlayers")) {
               conditions.setMaxPlayers(condObj.get("maxPlayers").getAsInt());
            }

            if (condObj.has("maxServerLoad")) {
               conditions.setMaxServerLoad(condObj.get("maxServerLoad").getAsDouble());
            }

            task.setConditions(conditions);
         }

         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Task created successfully");
         response.addProperty("taskId", task.getId());
         response.add("task", this.taskToJson(task));
         this.sendJsonResponse(exchange, 201, response);
      } catch (IllegalArgumentException var15) {
         this.sendErrorResponse(exchange, 400, var15.getMessage());
      }
   }

   private void handleUpdateTask(HttpExchange exchange, String path) throws IOException {
      String taskId = this.extractTaskId(path);
      String requestBody = this.readRequestBody(exchange);
      JsonObject data = JsonParser.parseString(requestBody).getAsJsonObject();
      String name = data.has("name") ? data.get("name").getAsString() : null;
      String cronExpression = data.has("cronExpression") ? data.get("cronExpression").getAsString() : null;
      Boolean enabled = data.has("enabled") ? data.get("enabled").getAsBoolean() : null;
      String timezone = data.has("timezone") ? data.get("timezone").getAsString() : null;
      String description = data.has("description") ? data.get("description").getAsString() : null;
      List<String> commands = null;
      if (data.has("commands")) {
         commands = new ArrayList<>();
         JsonArray commandsArray = data.getAsJsonArray("commands");

         for (int i = 0; i < commandsArray.size(); i++) {
            commands.add(commandsArray.get(i).getAsString());
         }
      }

      ScheduledTask.TaskConditions conditions = null;
      if (data.has("conditions")) {
         JsonObject condObj = data.getAsJsonObject("conditions");
         conditions = new ScheduledTask.TaskConditions();
         if (condObj.has("minPlayers")) {
            conditions.setMinPlayers(condObj.get("minPlayers").getAsInt());
         }

         if (condObj.has("maxPlayers")) {
            conditions.setMaxPlayers(condObj.get("maxPlayers").getAsInt());
         }

         if (condObj.has("maxServerLoad")) {
            conditions.setMaxServerLoad(condObj.get("maxServerLoad").getAsDouble());
         }
      }

      try {
         TaskManager manager = TaskManager.getInstance();
         boolean success = manager.updateTask(taskId, name, cronExpression, enabled, commands, timezone, description, conditions);
         if (success) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Task updated successfully");
            this.sendJsonResponse(exchange, 200, response);
         } else {
            this.sendErrorResponse(exchange, 404, "Task not found");
         }
      } catch (IllegalArgumentException var16) {
         this.sendErrorResponse(exchange, 400, var16.getMessage());
      }
   }

   private void handleDeleteTask(HttpExchange exchange, String path) throws IOException {
      String taskId = this.extractTaskId(path);
      TaskManager manager = TaskManager.getInstance();
      boolean success = manager.deleteTask(taskId);
      if (success) {
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Task deleted successfully");
         this.sendJsonResponse(exchange, 200, response);
      } else {
         this.sendErrorResponse(exchange, 404, "Task not found");
      }
   }

   private void handleExecuteTask(HttpExchange exchange, String path) throws IOException {
      String taskId = this.extractTaskId(path);
      TaskManager manager = TaskManager.getInstance();
      ScheduledTask task = manager.getTask(taskId);
      if (task == null) {
         this.sendErrorResponse(exchange, 404, "Task not found");
      } else {
         TaskScheduler.executeTask(task);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Task execution triggered");
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleValidateCron(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject data = JsonParser.parseString(requestBody).getAsJsonObject();
      String cronExpression = data.get("cronExpression").getAsString();
      boolean isValid = CronParser.isValid(cronExpression);
      JsonObject response = new JsonObject();
      response.addProperty("valid", isValid);
      response.addProperty("cronExpression", cronExpression);
      if (isValid) {
         try {
            CronParser parser = new CronParser(cronExpression);
            response.addProperty("description", parser.getDescription());
            JsonArray nextExecutions = new JsonArray();
            ZoneId tz = ZoneId.systemDefault();
            long nextTime = parser.getNextExecutionTime(tz);

            for (int i = 0; i < 5; i++) {
               nextExecutions.add(nextTime);
               CronParser tempParser = new CronParser(cronExpression);
               nextTime = tempParser.getNextExecutionTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextTime), tz));
            }

            response.add("nextExecutions", nextExecutions);
         } catch (Exception var14) {
            response.addProperty("error", var14.getMessage());
         }
      } else {
         response.addProperty("error", "Invalid cron expression format");
      }

      this.sendJsonResponse(exchange, 200, response);
   }

   private JsonObject taskToJson(ScheduledTask task) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", task.getId());
      obj.addProperty("name", task.getName());
      obj.addProperty("description", task.getDescription());
      obj.addProperty("cronExpression", task.getCronExpression());
      obj.addProperty("taskType", task.getTaskType().name());
      obj.addProperty("enabled", task.isEnabled());
      obj.addProperty("timezone", task.getTimezone());
      obj.addProperty("createdBy", task.getCreatedBy());
      obj.addProperty("createdAt", task.getCreatedAt());
      obj.addProperty("updatedAt", task.getUpdatedAt());
      obj.addProperty("lastExecutionTime", task.getLastExecutionTime());
      obj.addProperty("nextExecutionTime", task.getNextExecutionTime());
      obj.addProperty("executionCount", task.getExecutionCount());
      JsonArray commandsArray = new JsonArray();

      for (String cmd : task.getCommands()) {
         commandsArray.add(cmd);
      }

      obj.add("commands", commandsArray);

      try {
         CronParser parser = new CronParser(task.getCronExpression());
         obj.addProperty("cronDescription", parser.getDescription());
      } catch (Exception var6) {
         obj.addProperty("cronDescription", "Invalid expression");
      }

      return obj;
   }

   private String extractTaskId(String path) {
      String[] parts = path.split("/");

      for (int i = 0; i < parts.length; i++) {
         if ("tasks".equals(parts[i]) && i + 1 < parts.length) {
            return parts[i + 1];
         }
      }

      return null;
   }

   private String readRequestBody(HttpExchange exchange) throws IOException {
      return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
   }

   private String getUsernameFromSession(HttpExchange exchange) {
      Object username = exchange.getAttribute("username");
      return username != null ? username.toString() : "unknown";
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject data) throws IOException {
      String response = GSON.toJson(data);
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(statusCode, (long)bytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }

   private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("timestamp", System.currentTimeMillis());
      this.sendJsonResponse(exchange, statusCode, error);
   }
}
