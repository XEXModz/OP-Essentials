package com.zerog.neoessentials.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
   private static DatabaseManager instance;
   private final Map<String, DatabaseManager.DatabaseInfo> discoveredDatabases = new HashMap<>();
   private final Path configDirectory = Paths.get("config");

   private DatabaseManager() {
      this.discoverDatabases();
   }

   public static DatabaseManager getInstance() {
      if (instance == null) {
         instance = new DatabaseManager();
      }

      return instance;
   }

   public void discoverDatabases() {
      this.discoveredDatabases.clear();
      if (!Files.exists(this.configDirectory)) {
         LOGGER.warn("Config directory does not exist: {}", this.configDirectory);
      } else {
         try (Stream<Path> paths = Files.walk(this.configDirectory, 3)) {
            paths.filter(path -> path.toString().endsWith(".db") || path.toString().endsWith(".sqlite") || path.toString().endsWith(".sqlite3"))
               .forEach(this::registerDatabase);
         } catch (IOException var6) {
            LOGGER.error("Failed to discover databases", var6);
         }

         LOGGER.info("Discovered {} database(s)", this.discoveredDatabases.size());
      }
   }

   private void registerDatabase(Path dbPath) {
      try {
         if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
            return;
         }

         String fileName = dbPath.getFileName().toString();
         String id = this.generateDatabaseId(dbPath);
         long size = Files.size(dbPath);
         Instant modified = Files.getLastModifiedTime(dbPath).toInstant();
         DatabaseManager.DatabaseInfo info = new DatabaseManager.DatabaseInfo(id, fileName, dbPath, size, modified);
         this.discoveredDatabases.put(id, info);
         LOGGER.debug("Registered database: {} at {}", fileName, dbPath);
      } catch (IOException var8) {
         LOGGER.warn("Failed to register database: {}", dbPath, var8);
      }
   }

   private String generateDatabaseId(Path path) {
      String relativePath = this.configDirectory.relativize(path).toString();
      return relativePath.replace("\\", "/").replace(".db", "").replace(".sqlite", "").replace(".sqlite3", "");
   }

   public List<DatabaseManager.DatabaseInfo> getDatabases() {
      return new ArrayList<>(this.discoveredDatabases.values());
   }

   public DatabaseManager.DatabaseInfo getDatabase(String databaseId) {
      return this.discoveredDatabases.get(databaseId);
   }

   private Connection getConnection(String databaseId) throws SQLException {
      DatabaseManager.DatabaseInfo db = this.discoveredDatabases.get(databaseId);
      if (db == null) {
         throw new SQLException("Database not found: " + databaseId);
      } else {
         String url = "jdbc:sqlite:" + db.getPath().toString();
         Connection conn = DriverManager.getConnection(url);
         conn.setReadOnly(true);
         return conn;
      }
   }

   private static String sanitizeTableName(String tableName) throws SQLException {
      if (tableName == null || tableName.isEmpty()) {
         throw new SQLException("Table name cannot be null or empty");
      } else if (!tableName.matches("^[a-zA-Z0-9_\\-.]+$")) {
         throw new SQLException("Invalid table name: contains forbidden characters");
      } else if (tableName.length() > 128) {
         throw new SQLException("Invalid table name: exceeds maximum length");
      } else {
         return tableName;
      }
   }

   public List<DatabaseManager.TableInfo> getTables(String databaseId) throws SQLException {
      List<DatabaseManager.TableInfo> tables = new ArrayList<>();

      try (Connection conn = this.getConnection(databaseId)) {
         DatabaseMetaData meta = conn.getMetaData();

         try (ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
               String tableName = rs.getString("TABLE_NAME");
               String tableType = rs.getString("TABLE_TYPE");
               long rowCount = 0L;

               try (
                  Statement stmt = conn.createStatement();
                  ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + tableName + "\"");
               ) {
                  if (countRs.next()) {
                     rowCount = countRs.getLong(1);
                  }
               } catch (SQLException var20) {
                  LOGGER.warn("Failed to get row count for table: {}", tableName);
               }

               tables.add(new DatabaseManager.TableInfo(tableName, tableType, rowCount));
            }
         }
      }

      return tables;
   }

   public List<DatabaseManager.ColumnInfo> getTableSchema(String databaseId, String tableName) throws SQLException {
      List<DatabaseManager.ColumnInfo> columns = new ArrayList<>();
      tableName = sanitizeTableName(tableName);

      try (
         Connection conn = this.getConnection(databaseId);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("PRAGMA table_info(\"" + tableName + "\")");
      ) {
         while (rs.next()) {
            int cid = rs.getInt("cid");
            String name = rs.getString("name");
            String type = rs.getString("type");
            boolean notNull = rs.getInt("notnull") == 1;
            String defaultValue = rs.getString("dflt_value");
            boolean pk = rs.getInt("pk") > 0;
            columns.add(new DatabaseManager.ColumnInfo(cid, name, type, notNull, defaultValue, pk));
         }
      }

      return columns;
   }

   public DatabaseManager.QueryResult executeQuery(String databaseId, String query, int page, int pageSize) throws SQLException {
      String trimmedQuery = query.trim().toUpperCase();
      if (!trimmedQuery.startsWith("SELECT")) {
         throw new SQLException("Only SELECT queries are allowed");
      } else if (!trimmedQuery.contains("ATTACH")
         && !trimmedQuery.contains("PRAGMA")
         && !trimmedQuery.contains("INSERT")
         && !trimmedQuery.contains("UPDATE")
         && !trimmedQuery.contains("DELETE")
         && !trimmedQuery.contains("DROP")
         && !trimmedQuery.contains("ALTER")
         && !trimmedQuery.contains("CREATE")
         && !trimmedQuery.contains("REPLACE")
         && !trimmedQuery.contains("LOAD_EXTENSION")
         && !trimmedQuery.contains("DETACH")) {
         long startTime = System.currentTimeMillis();

         DatabaseManager.QueryResult var36;
         try (Connection conn = this.getConnection(databaseId)) {
            int totalRows = 0;
            String countQuery = "SELECT COUNT(*) FROM (" + query + ")";

            try (
               Statement countStmt = conn.createStatement();
               ResultSet countRs = countStmt.executeQuery(countQuery);
            ) {
               if (countRs.next()) {
                  totalRows = countRs.getInt(1);
               }
            }

            int offset = (page - 1) * pageSize;
            String paginatedQuery = query + " LIMIT " + pageSize + " OFFSET " + offset;
            List<String> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();

            try (
               Statement stmt = conn.createStatement();
               ResultSet rs = stmt.executeQuery(paginatedQuery);
            ) {
               ResultSetMetaData meta = rs.getMetaData();
               int columnCount = meta.getColumnCount();

               for (int i = 1; i <= columnCount; i++) {
                  columns.add(meta.getColumnName(i));
               }

               while (rs.next()) {
                  Map<String, Object> row = new LinkedHashMap<>();

                  for (int i = 1; i <= columnCount; i++) {
                     String columnName = meta.getColumnName(i);
                     Object value = rs.getObject(i);
                     row.put(columnName, value);
                  }

                  rows.add(row);
               }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            var36 = new DatabaseManager.QueryResult(columns, rows, totalRows, page, pageSize, executionTime);
         }

         return var36;
      } else {
         throw new SQLException("Query contains forbidden operations");
      }
   }

   public String exportTableAsCSV(String databaseId, String tableName) throws SQLException {
      StringBuilder csv = new StringBuilder();
      tableName = sanitizeTableName(tableName);

      try (
         Connection conn = this.getConnection(databaseId);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + tableName + "\"");
      ) {
         ResultSetMetaData meta = rs.getMetaData();
         int columnCount = meta.getColumnCount();

         for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
               csv.append(",");
            }

            csv.append(this.escapeCSV(meta.getColumnName(i)));
         }

         csv.append("\n");

         while (rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
               if (i > 1) {
                  csv.append(",");
               }

               Object value = rs.getObject(i);
               csv.append(this.escapeCSV(value != null ? value.toString() : ""));
            }

            csv.append("\n");
         }
      }

      return csv.toString();
   }

   private String escapeCSV(String value) {
      if (value == null) {
         return "";
      } else {
         return !value.contains(",") && !value.contains("\"") && !value.contains("\n") ? value : "\"" + value.replace("\"", "\"\"") + "\"";
      }
   }

   public Map<String, Object> getDatabaseStats(String databaseId) throws SQLException {
      Map<String, Object> stats = new HashMap<>();
      DatabaseManager.DatabaseInfo db = this.getDatabase(databaseId);
      if (db == null) {
         throw new SQLException("Database not found: " + databaseId);
      } else {
         stats.put("name", db.getName());
         stats.put("size", db.getSize());
         stats.put("sizeFormatted", this.formatFileSize(db.getSize()));
         stats.put("modified", db.getModified().toString());

         try (Connection conn = this.getConnection(databaseId)) {
            List<DatabaseManager.TableInfo> tables = this.getTables(databaseId);
            stats.put("tableCount", tables.size());
            long totalRows = tables.stream().mapToLong(DatabaseManager.TableInfo::getRowCount).sum();
            stats.put("totalRows", totalRows);

            try (
               Statement stmt = conn.createStatement();
               ResultSet rs = stmt.executeQuery("SELECT sqlite_version()");
            ) {
               if (rs.next()) {
                  stats.put("sqliteVersion", rs.getString(1));
               }
            }
         }

         return stats;
      }
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

   public static class ColumnInfo {
      private final int index;
      private final String name;
      private final String type;
      private final boolean notNull;
      private final String defaultValue;
      private final boolean primaryKey;

      public ColumnInfo(int index, String name, String type, boolean notNull, String defaultValue, boolean primaryKey) {
         this.index = index;
         this.name = name;
         this.type = type;
         this.notNull = notNull;
         this.defaultValue = defaultValue;
         this.primaryKey = primaryKey;
      }

      public int getIndex() {
         return this.index;
      }

      public String getName() {
         return this.name;
      }

      public String getType() {
         return this.type;
      }

      public boolean isNotNull() {
         return this.notNull;
      }

      public String getDefaultValue() {
         return this.defaultValue;
      }

      public boolean isPrimaryKey() {
         return this.primaryKey;
      }
   }

   public static class DatabaseInfo {
      private final String id;
      private final String name;
      private final Path path;
      private final long size;
      private final Instant modified;

      public DatabaseInfo(String id, String name, Path path, long size, Instant modified) {
         this.id = id;
         this.name = name;
         this.path = path;
         this.size = size;
         this.modified = modified;
      }

      public String getId() {
         return this.id;
      }

      public String getName() {
         return this.name;
      }

      public Path getPath() {
         return this.path;
      }

      public long getSize() {
         return this.size;
      }

      public Instant getModified() {
         return this.modified;
      }
   }

   public static class QueryResult {
      private final List<String> columns;
      private final List<Map<String, Object>> rows;
      private final int totalRows;
      private final int page;
      private final int pageSize;
      private final long executionTime;

      public QueryResult(List<String> columns, List<Map<String, Object>> rows, int totalRows, int page, int pageSize, long executionTime) {
         this.columns = columns;
         this.rows = rows;
         this.totalRows = totalRows;
         this.page = page;
         this.pageSize = pageSize;
         this.executionTime = executionTime;
      }

      public List<String> getColumns() {
         return this.columns;
      }

      public List<Map<String, Object>> getRows() {
         return this.rows;
      }

      public int getTotalRows() {
         return this.totalRows;
      }

      public int getPage() {
         return this.page;
      }

      public int getPageSize() {
         return this.pageSize;
      }

      public long getExecutionTime() {
         return this.executionTime;
      }
   }

   public static class TableInfo {
      private final String name;
      private final String type;
      private final long rowCount;

      public TableInfo(String name, String type, long rowCount) {
         this.name = name;
         this.type = type;
         this.rowCount = rowCount;
      }

      public String getName() {
         return this.name;
      }

      public String getType() {
         return this.type;
      }

      public long getRowCount() {
         return this.rowCount;
      }
   }
}
