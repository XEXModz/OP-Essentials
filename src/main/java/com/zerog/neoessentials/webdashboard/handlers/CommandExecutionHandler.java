package com.zerog.neoessentials.webdashboard.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandExecutionHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutionHandler.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private static final List<CommandExecutionHandler.CommandHistoryEntry> commandHistory = Collections.synchronizedList(new ArrayList<>());
   private static final int MAX_HISTORY_SIZE = 100;
   private static final Map<String, List<String>> commandOutputs = new ConcurrentHashMap<>();
   private static final Set<String> RESTRICTED_COMMANDS = new HashSet<>(Arrays.asList("stop", "restart", "reload", "op", "deop", "whitelist", "ban", "ban-ip"));

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      CorsHandler.applyWithMethods(exchange, "POST, GET, OPTIONS");
      if ("OPTIONS".equals(exchange.getRequestMethod())) {
         exchange.sendResponseHeaders(204, -1L);
      } else {
         String method = exchange.getRequestMethod();
         String path = exchange.getRequestURI().getPath();

         try {
            if ("POST".equals(method) && path.endsWith("/execute")) {
               this.handleExecute(exchange);
            } else if ("GET".equals(method) && path.endsWith("/history")) {
               this.handleHistory(exchange);
            } else if ("GET".equals(method) && path.endsWith("/suggestions")) {
               this.handleSuggestions(exchange);
            } else {
               this.sendJsonResponse(exchange, 400, this.createErrorResponse("Invalid endpoint"));
            }
         } catch (Exception var5) {
            LOGGER.error("Error handling command execution request", var5);
            this.sendJsonResponse(exchange, 500, this.createErrorResponse("Internal server error: " + var5.getMessage()));
         }
      }
   }

   private void handleExecute(HttpExchange exchange) throws IOException {
      String requestBody = this.readRequestBody(exchange);
      JsonObject request = (JsonObject)GSON.fromJson(requestBody, JsonObject.class);
      if (!request.has("command")) {
         this.sendJsonResponse(exchange, 400, this.createErrorResponse("Missing 'command' field"));
      } else {
         String command = request.get("command").getAsString().trim();
         if (command.startsWith("/")) {
            command = command.substring(1);
         }

         String baseCommand = command.split(" ")[0];
         if (RESTRICTED_COMMANDS.contains(baseCommand.toLowerCase())) {
            this.sendJsonResponse(
               exchange, 403, this.createErrorResponse("Command '" + baseCommand + "' is restricted and cannot be executed from the dashboard")
            );
         } else {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
               this.sendJsonResponse(exchange, 503, this.createErrorResponse("Server not available"));
            } else {
               final String executionId = UUID.randomUUID().toString();
               List<String> output = new ArrayList<>();
               commandOutputs.put(executionId, output);

               try {
                  CommandSourceStack source = this.createDashboardCommandSource(server, output);
                  int result = server.getCommands().getDispatcher().execute(command, source);
                  this.addToHistory(command, result > 0, String.join("\n", output));
                  JsonObject response = new JsonObject();
                  response.addProperty("success", result > 0);
                  response.addProperty("command", command);
                  response.addProperty("result", result);
                  response.addProperty("executionId", executionId);
                  JsonArray outputArray = new JsonArray();
                  output.forEach(outputArray::add);
                  response.add("output", outputArray);
                  this.sendJsonResponse(exchange, 200, response);
               } catch (Exception var16) {
                  LOGGER.error("Error executing command: {}", command, var16);
                  this.addToHistory(command, false, "Error: " + var16.getMessage());
                  JsonObject responsex = new JsonObject();
                  responsex.addProperty("success", false);
                  responsex.addProperty("command", command);
                  responsex.addProperty("error", var16.getMessage());
                  responsex.addProperty("executionId", executionId);
                  this.sendJsonResponse(exchange, 200, responsex);
               } finally {
                  new Timer().schedule(new TimerTask() {
                     @Override
                     public void run() {
                        CommandExecutionHandler.commandOutputs.remove(executionId);
                     }
                  }, 60000L);
               }
            }
         }
      }
   }

   private void handleHistory(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      int limit = Integer.parseInt(params.getOrDefault("limit", "50"));
      JsonObject response = new JsonObject();
      JsonArray history = new JsonArray();
      synchronized (commandHistory) {
         int start = Math.max(0, commandHistory.size() - limit);
         commandHistory.subList(start, commandHistory.size()).forEach(entry -> {
            JsonObject item = new JsonObject();
            item.addProperty("command", entry.command);
            item.addProperty("success", entry.success);
            item.addProperty("output", entry.output);
            item.addProperty("timestamp", entry.timestamp);
            history.add(item);
         });
      }

      response.add("history", history);
      response.addProperty("total", commandHistory.size());
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleSuggestions(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String input = params.getOrDefault("input", "").toLowerCase();
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         this.sendJsonResponse(exchange, 503, this.createErrorResponse("Server not available"));
      } else {
         JsonObject response = new JsonObject();
         JsonArray suggestions = new JsonArray();
         server.getCommands().getDispatcher().getRoot().getChildren().forEach(node -> {
            String commandName = node.getName();
            if (commandName.toLowerCase().startsWith(input)) {
               JsonObject suggestion = new JsonObject();
               suggestion.addProperty("command", commandName);
               suggestion.addProperty("restricted", RESTRICTED_COMMANDS.contains(commandName.toLowerCase()));
               suggestions.add(suggestion);
            }
         });
         response.add("suggestions", suggestions);
         response.addProperty("input", input);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private CommandSourceStack createDashboardCommandSource(MinecraftServer server, final List<String> outputCapture) {
      ServerLevel overworld = server.overworld();
      CommandSource outputSource = new CommandSource() {
         public void sendSystemMessage(Component message) {
            outputCapture.add(message.getString());
         }

         public boolean acceptsSuccess() {
            return true;
         }

         public boolean acceptsFailure() {
            return true;
         }

         public boolean shouldInformAdmins() {
            return false;
         }
      };
      return new CommandSourceStack(outputSource, Vec3.ZERO, Vec2.ZERO, overworld, 4, "Dashboard", Component.literal("Dashboard"), server, null);
   }

   private void addToHistory(String command, boolean success, String output) {
      synchronized (commandHistory) {
         commandHistory.add(new CommandExecutionHandler.CommandHistoryEntry(command, success, output, System.currentTimeMillis()));
         if (commandHistory.size() > 100) {
            commandHistory.remove(0);
         }
      }
   }

   private Map<String, String> parseQueryParams(String query) {
      return query != null && !query.isEmpty()
         ? Arrays.stream(query.split("&"))
            .map(param -> param.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(Collectors.toMap(parts -> (String)parts[0], parts -> (String)parts[1]))
         : Collections.emptyMap();
   }

   private String readRequestBody(HttpExchange exchange) throws IOException {
      String var4;
      try (
         InputStream is = exchange.getRequestBody();
         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      ) {
         var4 = reader.lines().collect(Collectors.joining("\n"));
      }

      return var4;
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
      byte[] response = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.sendResponseHeaders(statusCode, (long)response.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(response);
      }
   }

   private JsonObject createErrorResponse(String message) {
      JsonObject error = new JsonObject();
      error.addProperty("error", message);
      error.addProperty("timestamp", System.currentTimeMillis());
      return error;
   }

   private static class CommandHistoryEntry {
      final String command;
      final boolean success;
      final String output;
      final long timestamp;

      CommandHistoryEntry(String command, boolean success, String output, long timestamp) {
         this.command = command;
         this.success = success;
         this.output = output;
         this.timestamp = timestamp;
      }
   }
}
