package com.zerog.neoessentials.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHandler.class);
   private final Gson gson = new Gson();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();

      try {
         String endpoint = path.replace("/api/database", "");
         if (endpoint.isEmpty() || endpoint.equals("/")) {
            endpoint = "/list";
         }

         switch (endpoint) {
            case "/list":
               if ("GET".equals(method)) {
                  this.handleListDatabases(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/tables":
               if ("GET".equals(method)) {
                  this.handleListTables(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/schema":
               if ("GET".equals(method)) {
                  this.handleGetSchema(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/query":
               if ("POST".equals(method)) {
                  this.handleQuery(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/export":
               if ("GET".equals(method)) {
                  this.handleExport(exchange);
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
            case "/refresh":
               if ("POST".equals(method)) {
                  this.handleRefresh(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            default:
               this.sendNotFound(exchange);
         }
      } catch (Exception var7) {
         LOGGER.error("Error handling database request", var7);
         this.sendError(exchange, "Internal server error: " + var7.getMessage());
      }
   }

   private void handleListDatabases(HttpExchange exchange) throws IOException {
      List<DatabaseManager.DatabaseInfo> databases = DatabaseManager.getInstance().getDatabases();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("count", databases.size());
      JsonArray dbArray = new JsonArray();

      for (DatabaseManager.DatabaseInfo db : databases) {
         JsonObject dbObj = new JsonObject();
         dbObj.addProperty("id", db.getId());
         dbObj.addProperty("name", db.getName());
         dbObj.addProperty("path", db.getPath().toString());
         dbObj.addProperty("size", db.getSize());
         dbObj.addProperty("sizeFormatted", this.formatFileSize(db.getSize()));
         dbObj.addProperty("modified", db.getModified().toString());
         dbArray.add(dbObj);
      }

      response.add("databases", dbArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleListTables(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String databaseId = params.get("database");
      if (databaseId != null && !databaseId.isEmpty()) {
         try {
            List<DatabaseManager.TableInfo> tables = DatabaseManager.getInstance().getTables(databaseId);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("database", databaseId);
            response.addProperty("tableCount", tables.size());
            JsonArray tablesArray = new JsonArray();

            for (DatabaseManager.TableInfo table : tables) {
               JsonObject tableObj = new JsonObject();
               tableObj.addProperty("name", table.getName());
               tableObj.addProperty("type", table.getType());
               tableObj.addProperty("rowCount", table.getRowCount());
               tablesArray.add(tableObj);
            }

            response.add("tables", tablesArray);
            this.sendJsonResponse(exchange, 200, response);
         } catch (SQLException var10) {
            LOGGER.error("Failed to list tables", var10);
            this.sendError(exchange, "Failed to list tables: " + var10.getMessage());
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'database' parameter");
      }
   }

   private void handleGetSchema(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String databaseId = params.get("database");
      String tableName = params.get("table");
      if (databaseId == null || databaseId.isEmpty()) {
         this.sendBadRequest(exchange, "Missing 'database' parameter");
      } else if (tableName != null && !tableName.isEmpty()) {
         try {
            List<DatabaseManager.ColumnInfo> columns = DatabaseManager.getInstance().getTableSchema(databaseId, tableName);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("database", databaseId);
            response.addProperty("table", tableName);
            response.addProperty("columnCount", columns.size());
            JsonArray columnsArray = new JsonArray();

            for (DatabaseManager.ColumnInfo column : columns) {
               JsonObject colObj = new JsonObject();
               colObj.addProperty("index", column.getIndex());
               colObj.addProperty("name", column.getName());
               colObj.addProperty("type", column.getType());
               colObj.addProperty("notNull", column.isNotNull());
               colObj.addProperty("defaultValue", column.getDefaultValue());
               colObj.addProperty("primaryKey", column.isPrimaryKey());
               columnsArray.add(colObj);
            }

            response.add("columns", columnsArray);
            this.sendJsonResponse(exchange, 200, response);
         } catch (SQLException var11) {
            LOGGER.error("Failed to get schema", var11);
            this.sendError(exchange, "Failed to get schema: " + var11.getMessage());
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'table' parameter");
      }
   }

   private void handleQuery(HttpExchange exchange) throws IOException {
      try (InputStream is = exchange.getRequestBody()) {
         String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         JsonObject request = (JsonObject)this.gson.fromJson(body, JsonObject.class);
         String databaseId = request.has("database") ? request.get("database").getAsString() : null;
         String query = request.has("query") ? request.get("query").getAsString() : null;
         int page = request.has("page") ? request.get("page").getAsInt() : 1;
         int pageSize = request.has("pageSize") ? request.get("pageSize").getAsInt() : 100;
         if (databaseId != null && !databaseId.isEmpty()) {
            if (query != null && !query.isEmpty()) {
               page = Math.max(1, page);
               pageSize = Math.max(10, Math.min(pageSize, 1000));

               try {
                  DatabaseManager.QueryResult result = DatabaseManager.getInstance().executeQuery(databaseId, query, page, pageSize);
                  JsonObject response = new JsonObject();
                  response.addProperty("success", true);
                  response.addProperty("database", databaseId);
                  response.addProperty("query", query);
                  response.addProperty("page", result.getPage());
                  response.addProperty("pageSize", result.getPageSize());
                  response.addProperty("totalRows", result.getTotalRows());
                  response.addProperty("totalPages", (int)Math.ceil((double)result.getTotalRows() / (double)result.getPageSize()));
                  response.addProperty("executionTime", result.getExecutionTime());
                  JsonArray columnsArray = new JsonArray();

                  for (String column : result.getColumns()) {
                     columnsArray.add(column);
                  }

                  response.add("columns", columnsArray);
                  JsonArray rowsArray = new JsonArray();

                  for (Map<String, Object> row : result.getRows()) {
                     JsonObject rowObj = new JsonObject();

                     for (Entry<String, Object> entry : row.entrySet()) {
                        Object value = entry.getValue();
                        if (value == null) {
                           rowObj.add(entry.getKey(), null);
                        } else if (value instanceof Number) {
                           rowObj.addProperty(entry.getKey(), (Number)value);
                        } else if (value instanceof Boolean) {
                           rowObj.addProperty(entry.getKey(), (Boolean)value);
                        } else {
                           rowObj.addProperty(entry.getKey(), value.toString());
                        }
                     }

                     rowsArray.add(rowObj);
                  }

                  response.add("rows", rowsArray);
                  this.sendJsonResponse(exchange, 200, response);
               } catch (SQLException var20) {
                  LOGGER.error("Failed to execute query", var20);
                  this.sendError(exchange, "Failed to execute query: " + var20.getMessage());
               }

               return;
            }

            this.sendBadRequest(exchange, "Missing 'query' field");
            return;
         }

         this.sendBadRequest(exchange, "Missing 'database' field");
      }
   }

   private void handleExport(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String databaseId = params.get("database");
      String tableName = params.get("table");
      String format = params.getOrDefault("format", "csv");
      if (databaseId == null || databaseId.isEmpty()) {
         this.sendBadRequest(exchange, "Missing 'database' parameter");
      } else if (tableName != null && !tableName.isEmpty()) {
         try {
            if ("csv".equalsIgnoreCase(format)) {
               String csv = DatabaseManager.getInstance().exportTableAsCSV(databaseId, tableName);
               exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
               exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + tableName + ".csv\"");
               byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
               exchange.sendResponseHeaders(200, (long)bytes.length);

               try (OutputStream os = exchange.getResponseBody()) {
                  os.write(bytes);
               }
            } else if ("json".equalsIgnoreCase(format)) {
               if (!tableName.matches("^[a-zA-Z0-9_\\-.]+$")) {
                  this.sendBadRequest(exchange, "Invalid table name");
                  return;
               }

               DatabaseManager.QueryResult result = DatabaseManager.getInstance().executeQuery(databaseId, "SELECT * FROM \"" + tableName + "\"", 1, 10000);
               JsonObject exportData = new JsonObject();
               exportData.addProperty("table", tableName);
               exportData.addProperty("rowCount", result.getTotalRows());
               JsonArray columnsArray = new JsonArray();

               for (String column : result.getColumns()) {
                  columnsArray.add(column);
               }

               exportData.add("columns", columnsArray);
               JsonArray rowsArray = new JsonArray();

               for (Map<String, Object> row : result.getRows()) {
                  JsonObject rowObj = new JsonObject();

                  for (Entry<String, Object> entry : row.entrySet()) {
                     Object value = entry.getValue();
                     if (value == null) {
                        rowObj.add(entry.getKey(), null);
                     } else if (value instanceof Number) {
                        rowObj.addProperty(entry.getKey(), (Number)value);
                     } else {
                        rowObj.addProperty(entry.getKey(), value.toString());
                     }
                  }

                  rowsArray.add(rowObj);
               }

               exportData.add("rows", rowsArray);
               String json = this.gson.toJson(exportData);
               byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
               exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
               exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + tableName + ".json\"");
               exchange.sendResponseHeaders(200, (long)bytes.length);

               try (OutputStream os = exchange.getResponseBody()) {
                  os.write(bytes);
               }
            } else {
               this.sendBadRequest(exchange, "Invalid format. Use 'csv' or 'json'");
            }
         } catch (SQLException var20) {
            LOGGER.error("Failed to export table", var20);
            this.sendError(exchange, "Failed to export table: " + var20.getMessage());
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'table' parameter");
      }
   }

   private void handleStats(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String databaseId = params.get("database");
      if (databaseId != null && !databaseId.isEmpty()) {
         try {
            Map<String, Object> stats = DatabaseManager.getInstance().getDatabaseStats(databaseId);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("database", databaseId);

            for (Entry<String, Object> entry : stats.entrySet()) {
               Object value = entry.getValue();
               if (value instanceof Number) {
                  response.addProperty(entry.getKey(), (Number)value);
               } else {
                  response.addProperty(entry.getKey(), value.toString());
               }
            }

            this.sendJsonResponse(exchange, 200, response);
         } catch (SQLException var9) {
            LOGGER.error("Failed to get stats", var9);
            this.sendError(exchange, "Failed to get stats: " + var9.getMessage());
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'database' parameter");
      }
   }

   private void handleRefresh(HttpExchange exchange) throws IOException {
      DatabaseManager.getInstance().discoverDatabases();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("message", "Database discovery refreshed");
      response.addProperty("count", DatabaseManager.getInstance().getDatabases().size());
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 200, response);
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
