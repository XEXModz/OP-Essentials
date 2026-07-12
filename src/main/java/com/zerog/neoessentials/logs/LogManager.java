package com.zerog.neoessentials.logs;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);
   private static LogManager instance;
   private final Path logsDirectory = Paths.get("logs");
   private final Path latestLogPath = this.logsDirectory.resolve("latest.log");

   private LogManager() {
      if (!Files.exists(this.logsDirectory)) {
         LOGGER.warn("Logs directory does not exist: {}", this.logsDirectory);
      }
   }

   public static LogManager getInstance() {
      if (instance == null) {
         instance = new LogManager();
      }

      return instance;
   }

   public List<LogManager.LogEntry> tailLog(int lineCount) {
      if (!Files.exists(this.latestLogPath)) {
         LOGGER.warn("Latest log file does not exist: {}", this.latestLogPath);
         return Collections.emptyList();
      } else {
         try {
            List<String> allLines = Files.readAllLines(this.latestLogPath);
            int totalLines = allLines.size();
            int startIndex = Math.max(0, totalLines - lineCount);
            List<LogManager.LogEntry> entries = new ArrayList<>();

            for (int i = startIndex; i < totalLines; i++) {
               LogManager.LogEntry entry = this.parseLogLine(allLines.get(i), (long)(i + 1));
               if (entry != null) {
                  entries.add(entry);
               }
            }

            return entries;
         } catch (IOException var8) {
            LOGGER.error("Failed to tail log file", var8);
            return Collections.emptyList();
         }
      }
   }

   public List<LogManager.LogEntry> searchLogs(String query, String logLevel, boolean useRegex, boolean caseSensitive, int maxResults) {
      if (!Files.exists(this.latestLogPath)) {
         return Collections.emptyList();
      } else {
         Pattern searchPattern = null;
         if (useRegex) {
            try {
               int flags = caseSensitive ? 0 : 2;
               searchPattern = Pattern.compile(query, flags);
            } catch (PatternSyntaxException var15) {
               LOGGER.warn("Invalid regex pattern: {}", query, var15);
               return Collections.emptyList();
            }
         }

         List<LogManager.LogEntry> results = new ArrayList<>();

         try (BufferedReader reader = Files.newBufferedReader(this.latestLogPath)) {
            long lineNumber = 0L;

            String line;
            while ((line = reader.readLine()) != null && results.size() < maxResults) {
               LogManager.LogEntry entry = this.parseLogLine(line, ++lineNumber);
               if (entry != null && (logLevel == null || logLevel.isEmpty() || logLevel.equalsIgnoreCase("ALL") || entry.getLevel().equalsIgnoreCase(logLevel))
                  )
                {
                  if (query != null && !query.isEmpty()) {
                     boolean matches;
                     if (useRegex) {
                        matches = searchPattern.matcher(line).find();
                     } else {
                        matches = caseSensitive ? line.contains(query) : line.toLowerCase().contains(query.toLowerCase());
                     }

                     if (!matches) {
                        continue;
                     }
                  }

                  results.add(entry);
               }
            }
         } catch (IOException var17) {
            LOGGER.error("Failed to search logs", var17);
         }

         return results;
      }
   }

   private LogManager.LogEntry parseLogLine(String line, long lineNumber) {
      if (line != null && !line.trim().isEmpty()) {
         try {
            String timestamp = "";
            String level = "INFO";
            String thread = "";
            String logger = "";
            String message = line;
            if (line.startsWith("[")) {
               int timestampEnd = line.indexOf("]");
               if (timestampEnd > 0) {
                  timestamp = line.substring(1, timestampEnd);
                  line = line.substring(timestampEnd + 1).trim();
               }
            }

            if (line.startsWith("[")) {
               int threadEnd = line.indexOf("]");
               if (threadEnd > 0) {
                  String threadLevel = line.substring(1, threadEnd);
                  int slashIndex = threadLevel.indexOf("/");
                  if (slashIndex > 0) {
                     thread = threadLevel.substring(0, slashIndex);
                     level = threadLevel.substring(slashIndex + 1);
                  }

                  line = line.substring(threadEnd + 1).trim();
               }
            }

            if (line.startsWith("[")) {
               int loggerEnd = line.indexOf("]:");
               if (loggerEnd > 0) {
                  logger = line.substring(1, loggerEnd);
                  message = line.substring(loggerEnd + 2).trim();
               } else {
                  message = line;
               }
            }

            return new LogManager.LogEntry(timestamp, level, thread, logger, message, lineNumber);
         } catch (Exception var12) {
            LOGGER.debug("Failed to parse log line: {}", line, var12);
            return new LogManager.LogEntry("", "INFO", "", "", line, lineNumber);
         }
      } else {
         return null;
      }
   }

   public List<LogManager.LogFileInfo> getLogFiles() {
      if (!Files.exists(this.logsDirectory)) {
         return Collections.emptyList();
      } else {
         List<LogManager.LogFileInfo> logFiles = new ArrayList<>();

         try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.logsDirectory, "*.log*")) {
            for (Path path : stream) {
               try {
                  long size = Files.size(path);
                  Instant modified = Files.getLastModifiedTime(path).toInstant();
                  boolean isCompressed = path.toString().endsWith(".gz");
                  boolean isLatest = path.equals(this.latestLogPath);
                  logFiles.add(new LogManager.LogFileInfo(path.getFileName().toString(), size, modified, isCompressed, isLatest));
               } catch (IOException var11) {
                  LOGGER.warn("Failed to get info for log file: {}", path, var11);
               }
            }
         } catch (IOException var13) {
            LOGGER.error("Failed to list log files", var13);
         }

         logFiles.sort((a, b) -> b.getModified().compareTo(a.getModified()));
         return logFiles;
      }
   }

   public byte[] getLogFileContent(String fileName) throws IOException {
      Path logFile = this.logsDirectory.resolve(fileName);
      if (!logFile.normalize().startsWith(this.logsDirectory.normalize())) {
         throw new SecurityException("Invalid log file path");
      } else if (!Files.exists(logFile)) {
         throw new FileNotFoundException("Log file not found: " + fileName);
      } else if (fileName.endsWith(".gz")) {
         byte[] var7;
         try (
            GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(logFile));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ) {
            byte[] buffer = new byte[8192];

            int len;
            while ((len = gzis.read(buffer)) > 0) {
               baos.write(buffer, 0, len);
            }

            var7 = baos.toByteArray();
         }

         return var7;
      } else {
         return Files.readAllBytes(logFile);
      }
   }

   public LogManager.LogStats getLogStats() {
      if (!Files.exists(this.latestLogPath)) {
         return new LogManager.LogStats(0L, 0L, new HashMap<>());
      } else {
         try {
            long fileSize = Files.size(this.latestLogPath);
            long lineCount = Files.lines(this.latestLogPath).count();
            Map<String, Long> levelCounts = new HashMap<>();

            String line;
            try (BufferedReader reader = Files.newBufferedReader(this.latestLogPath)) {
               while ((line = reader.readLine()) != null) {
                  LogManager.LogEntry entry = this.parseLogLine(line, 0L);
                  if (entry != null) {
                     levelCounts.merge(entry.getLevel(), 1L, Long::sum);
                  }
               }
            }

            return new LogManager.LogStats(fileSize, lineCount, levelCounts);
         } catch (IOException var11) {
            LOGGER.error("Failed to get log stats", var11);
            return new LogManager.LogStats(0L, 0L, new HashMap<>());
         }
      }
   }

   public static class LogEntry {
      private final String timestamp;
      private final String level;
      private final String thread;
      private final String logger;
      private final String message;
      private final long lineNumber;

      public LogEntry(String timestamp, String level, String thread, String logger, String message, long lineNumber) {
         this.timestamp = timestamp;
         this.level = level;
         this.thread = thread;
         this.logger = logger;
         this.message = message;
         this.lineNumber = lineNumber;
      }

      public String getTimestamp() {
         return this.timestamp;
      }

      public String getLevel() {
         return this.level;
      }

      public String getThread() {
         return this.thread;
      }

      public String getLogger() {
         return this.logger;
      }

      public String getMessage() {
         return this.message;
      }

      public long getLineNumber() {
         return this.lineNumber;
      }
   }

   public static class LogFileInfo {
      private final String name;
      private final long size;
      private final Instant modified;
      private final boolean compressed;
      private final boolean latest;

      public LogFileInfo(String name, long size, Instant modified, boolean compressed, boolean latest) {
         this.name = name;
         this.size = size;
         this.modified = modified;
         this.compressed = compressed;
         this.latest = latest;
      }

      public String getName() {
         return this.name;
      }

      public long getSize() {
         return this.size;
      }

      public Instant getModified() {
         return this.modified;
      }

      public boolean isCompressed() {
         return this.compressed;
      }

      public boolean isLatest() {
         return this.latest;
      }
   }

   public static class LogStats {
      private final long fileSize;
      private final long lineCount;
      private final Map<String, Long> levelCounts;

      public LogStats(long fileSize, long lineCount, Map<String, Long> levelCounts) {
         this.fileSize = fileSize;
         this.lineCount = lineCount;
         this.levelCounts = levelCounts;
      }

      public long getFileSize() {
         return this.fileSize;
      }

      public long getLineCount() {
         return this.lineCount;
      }

      public Map<String, Long> getLevelCounts() {
         return this.levelCounts;
      }
   }
}
