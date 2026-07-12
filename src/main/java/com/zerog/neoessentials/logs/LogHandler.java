package com.zerog.neoessentials.logs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(LogHandler.class);
   private final Gson gson = new Gson();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();

      try {
         String endpoint = path.replace("/api/logs", "");
         switch (endpoint) {
            case "/tail":
               if ("GET".equals(method)) {
                  this.handleTail(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/search":
               if ("GET".equals(method)) {
                  this.handleSearch(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/files":
               if ("GET".equals(method)) {
                  this.handleListFiles(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/download":
               if ("GET".equals(method)) {
                  this.handleDownload(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/stats":
               if ("GET".equals(method)) {
                  this.handleStats(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            default:
               this.sendNotFound(exchange);
         }
      } catch (Exception var7) {
         LOGGER.error("Error handling log request", var7);
         this.sendError(exchange, "Internal server error: " + var7.getMessage());
      }
   }

   private void handleTail(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      int lineCount = Integer.parseInt(params.getOrDefault("lines", "100"));
      lineCount = Math.max(1, Math.min(lineCount, 10000));
      List<LogManager.LogEntry> entries = LogManager.getInstance().tailLog(lineCount);
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("lineCount", entries.size());
      response.addProperty("timestamp", Instant.now().toString());
      JsonArray logsArray = new JsonArray();

      for (LogManager.LogEntry entry : entries) {
         logsArray.add(this.logEntryToJson(entry));
      }

      response.add("logs", logsArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleSearch(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String query = params.get("query");
      String level = params.get("level");
      boolean useRegex = Boolean.parseBoolean(params.getOrDefault("regex", "false"));
      boolean caseSensitive = Boolean.parseBoolean(params.getOrDefault("caseSensitive", "false"));
      int maxResults = Integer.parseInt(params.getOrDefault("maxResults", "500"));
      maxResults = Math.max(1, Math.min(maxResults, 5000));
      List<LogManager.LogEntry> results = LogManager.getInstance().searchLogs(query, level, useRegex, caseSensitive, maxResults);
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("resultCount", results.size());
      response.addProperty("query", query);
      response.addProperty("level", level);
      response.addProperty("useRegex", useRegex);
      response.addProperty("caseSensitive", caseSensitive);
      response.addProperty("timestamp", Instant.now().toString());
      JsonArray resultsArray = new JsonArray();

      for (LogManager.LogEntry entry : results) {
         resultsArray.add(this.logEntryToJson(entry));
      }

      response.add("results", resultsArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleListFiles(HttpExchange exchange) throws IOException {
      List<LogManager.LogFileInfo> files = LogManager.getInstance().getLogFiles();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("fileCount", files.size());
      JsonArray filesArray = new JsonArray();

      for (LogManager.LogFileInfo file : files) {
         JsonObject fileObj = new JsonObject();
         fileObj.addProperty("name", file.getName());
         fileObj.addProperty("size", file.getSize());
         fileObj.addProperty("sizeFormatted", this.formatFileSize(file.getSize()));
         fileObj.addProperty("modified", file.getModified().toString());
         fileObj.addProperty("compressed", file.isCompressed());
         fileObj.addProperty("latest", file.isLatest());
         filesArray.add(fileObj);
      }

      response.add("files", filesArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleDownload(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String fileName = params.get("file");
      if (fileName != null && !fileName.isEmpty()) {
         try {
            byte[] content = LogManager.getInstance().getLogFileContent(fileName);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.sendResponseHeaders(200, (long)content.length);

            try (OutputStream os = exchange.getResponseBody()) {
               os.write(content);
            }
         } catch (SecurityException var10) {
            this.sendBadRequest(exchange, "Invalid file path");
         } catch (IOException var11) {
            this.sendNotFound(exchange);
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'file' parameter");
      }
   }

   private void handleStats(HttpExchange exchange) throws IOException {
      LogManager.LogStats stats = LogManager.getInstance().getLogStats();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("fileSize", stats.getFileSize());
      response.addProperty("fileSizeFormatted", this.formatFileSize(stats.getFileSize()));
      response.addProperty("lineCount", stats.getLineCount());
      JsonObject levelCounts = new JsonObject();

      for (Entry<String, Long> entry : stats.getLevelCounts().entrySet()) {
         levelCounts.addProperty(entry.getKey(), entry.getValue());
      }

      response.add("levelCounts", levelCounts);
      this.sendJsonResponse(exchange, 200, response);
   }

   private JsonObject logEntryToJson(LogManager.LogEntry entry) {
      JsonObject obj = new JsonObject();
      obj.addProperty("timestamp", entry.getTimestamp());
      obj.addProperty("level", entry.getLevel());
      obj.addProperty("thread", entry.getThread());
      obj.addProperty("logger", entry.getLogger());
      obj.addProperty("message", entry.getMessage());
      obj.addProperty("lineNumber", entry.getLineNumber());
      return obj;
   }

   private String formatFileSize(long bytes) {
      if (bytes < 1024L) {
         return bytes + " B";
      } else {
         int exp = (int)(Math.log((double)bytes) / Math.log(1024.0));
         String pre = "KMGTPE".charAt(exp - 1) + "B";
         return String.format("%.2f %s", (double)bytes / Math.pow(1024.0, (double)exp), pre);
      }
   }

   private Map<String, String> parseQueryParams(String query) {
      Map<String, String> params = new HashMap<>();
      if (query != null && !query.isEmpty()) {
         for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
               try {
                  String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                  String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                  params.put(key, value);
               } catch (Exception var10) {
                  LOGGER.warn("Failed to decode parameter: {}", param);
               }
            }
         }

         return params;
      } else {
         return params;
      }
   }

   private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject response) throws IOException {
      String jsonResponse = this.gson.toJson(response);
      byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.sendResponseHeaders(statusCode, (long)bytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }

   private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty("error", "Method not allowed");
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 405, response);
   }

   private void sendNotFound(HttpExchange exchange) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty("error", "Resource not found");
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 404, response);
   }

   private void sendBadRequest(HttpExchange exchange, String message) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty("error", message);
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 400, response);
   }

   private void sendError(HttpExchange exchange, String message) throws IOException {
      JsonObject response = new JsonObject();
      response.addProperty("success", false);
      response.addProperty("error", message);
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 500, response);
   }
}
