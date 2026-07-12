package com.zerog.neoessentials.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zerog.neoessentials.util.MessageUtil;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Locale.Builder;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalizationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(LocalizationManager.class);
   private static LocalizationManager INSTANCE;
   private final Path langDirectory;
   private final Map<String, Map<String, String>> dashboardTranslations = new ConcurrentHashMap<>();
   private final Map<String, LocalizationManager.LanguageInfo> availableLanguages = new LinkedHashMap<>();
   private final String defaultLanguage = "en_us";
   private final Gson gson = new Gson();
   private static final Set<String> RTL_LANGUAGES = Set.of("ar", "ar_sa", "he", "he_il", "fa", "fa_ir", "ur", "ur_pk");

   private LocalizationManager() {
      this.langDirectory = Paths.get("neoessentials", "webdashboard", "lang");
      this.initialize();
   }

   public static LocalizationManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new LocalizationManager();
      }

      return INSTANCE;
   }

   public void initialize() {
      try {
         if (!Files.exists(this.langDirectory)) {
            Files.createDirectories(this.langDirectory);
            LOGGER.info("Created language directory: {}", this.langDirectory.toAbsolutePath());
         }

         this.registerBuiltInLanguages();
         this.loadDashboardTranslations();
         if (!this.dashboardTranslations.containsKey("en_us")) {
            this.createDefaultDashboardTranslations();
         }

         LOGGER.info("Dashboard localization initialized with {} languages", this.availableLanguages.size());
         LOGGER.info("Dashboard-specific translations loaded for: {}", this.dashboardTranslations.keySet());
      } catch (Exception var2) {
         LOGGER.error("Failed to initialize localization", var2);
      }
   }

   private void registerBuiltInLanguages() {
      this.availableLanguages.put("en_us", new LocalizationManager.LanguageInfo("en_us", "English (US)", "English", "US", false));
      this.availableLanguages.put("es_es", new LocalizationManager.LanguageInfo("es_es", "Español (España)", "Spanish", "ES", false));
      this.availableLanguages.put("fr_fr", new LocalizationManager.LanguageInfo("fr_fr", "Français (France)", "French", "FR", false));
      this.availableLanguages.put("de_de", new LocalizationManager.LanguageInfo("de_de", "Deutsch (Deutschland)", "German", "DE", false));
      this.availableLanguages.put("zh_cn", new LocalizationManager.LanguageInfo("zh_cn", "简体中文", "Chinese (Simplified)", "CN", false));
      this.availableLanguages.put("ja_jp", new LocalizationManager.LanguageInfo("ja_jp", "日本語", "Japanese", "JP", false));
      this.availableLanguages.put("ko_kr", new LocalizationManager.LanguageInfo("ko_kr", "한국어", "Korean", "KR", false));
      this.availableLanguages.put("pt_br", new LocalizationManager.LanguageInfo("pt_br", "Português (Brasil)", "Portuguese (Brazil)", "BR", false));
      this.availableLanguages.put("ru_ru", new LocalizationManager.LanguageInfo("ru_ru", "Русский", "Russian", "RU", false));
      this.availableLanguages.put("ar_sa", new LocalizationManager.LanguageInfo("ar_sa", "العربية (السعودية)", "Arabic (Saudi Arabia)", "SA", true));
      this.availableLanguages.put("he_il", new LocalizationManager.LanguageInfo("he_il", "עברית (ישראל)", "Hebrew (Israel)", "IL", true));
   }

   private void loadDashboardTranslations() {
      try {
         if (!Files.exists(this.langDirectory)) {
            return;
         }

         Files.list(this.langDirectory).filter(path -> path.toString().endsWith("_dashboard.json")).forEach(this::loadDashboardTranslationFile);
      } catch (IOException var2) {
         LOGGER.error("Failed to load dashboard translation files", var2);
      }
   }

   private void loadDashboardTranslationFile(Path filePath) {
      try {
         String fileName = filePath.getFileName().toString();
         String langCode = fileName.replace("_dashboard.json", "");
         String content = Files.readString(filePath, StandardCharsets.UTF_8);
         Type type = (new TypeToken<Map<String, String>>() {
         }).getType();
         Map<String, String> langTranslations = (Map<String, String>)this.gson.fromJson(content, type);
         this.dashboardTranslations.put(langCode, langTranslations);
         LOGGER.debug("Loaded dashboard translations: {} ({} keys)", langCode, langTranslations.size());
      } catch (Exception var7) {
         LOGGER.error("Failed to load dashboard translation file: {}", filePath, var7);
      }
   }

   private void createDefaultDashboardTranslations() {
      Map<String, String> defaultTranslations = new LinkedHashMap<>();
      defaultTranslations.put("dashboard.title", "NeoEssentials Dashboard");
      defaultTranslations.put("dashboard.welcome", "Welcome to NeoEssentials");
      defaultTranslations.put("dashboard.logout", "Logout");
      defaultTranslations.put("dashboard.language", "Language");
      defaultTranslations.put("nav.overview", "Overview");
      defaultTranslations.put("nav.players", "Players");
      defaultTranslations.put("nav.server", "Server");
      defaultTranslations.put("nav.console", "Console");
      defaultTranslations.put("nav.logs", "Logs");
      defaultTranslations.put("nav.config", "Configuration");
      defaultTranslations.put("nav.files", "Files");
      defaultTranslations.put("nav.database", "Database");
      defaultTranslations.put("nav.moderation", "Moderation");
      defaultTranslations.put("nav.analytics", "Analytics");
      defaultTranslations.put("nav.settings", "Settings");
      defaultTranslations.put("action.save", "Save");
      defaultTranslations.put("action.cancel", "Cancel");
      defaultTranslations.put("action.delete", "Delete");
      defaultTranslations.put("action.edit", "Edit");
      defaultTranslations.put("action.create", "Create");
      defaultTranslations.put("action.refresh", "Refresh");
      defaultTranslations.put("action.search", "Search");
      defaultTranslations.put("action.filter", "Filter");
      defaultTranslations.put("action.export", "Export");
      defaultTranslations.put("action.import", "Import");
      defaultTranslations.put("action.download", "Download");
      defaultTranslations.put("action.upload", "Upload");
      defaultTranslations.put("players.online", "Online Players");
      defaultTranslations.put("players.total", "Total Players");
      defaultTranslations.put("players.kick", "Kick Player");
      defaultTranslations.put("players.ban", "Ban Player");
      defaultTranslations.put("players.unban", "Unban Player");
      defaultTranslations.put("players.mute", "Mute Player");
      defaultTranslations.put("players.unmute", "Unmute Player");
      defaultTranslations.put("players.whitelist", "Whitelist");
      defaultTranslations.put("players.inventory", "Inventory");
      defaultTranslations.put("players.statistics", "Statistics");
      defaultTranslations.put("server.status", "Server Status");
      defaultTranslations.put("server.uptime", "Uptime");
      defaultTranslations.put("server.tps", "TPS");
      defaultTranslations.put("server.memory", "Memory");
      defaultTranslations.put("server.cpu", "CPU");
      defaultTranslations.put("server.disk", "Disk Space");
      defaultTranslations.put("server.version", "Version");
      defaultTranslations.put("server.worlds", "Worlds");
      defaultTranslations.put("database.list", "Databases");
      defaultTranslations.put("database.tables", "Tables");
      defaultTranslations.put("database.query", "Query");
      defaultTranslations.put("database.export", "Export");
      defaultTranslations.put("database.rows", "Rows");
      defaultTranslations.put("database.schema", "Schema");
      defaultTranslations.put("logs.viewer", "Log Viewer");
      defaultTranslations.put("logs.level", "Log Level");
      defaultTranslations.put("logs.search", "Search Logs");
      defaultTranslations.put("logs.download", "Download Log");
      defaultTranslations.put("logs.tail", "Tail Logs");
      defaultTranslations.put("time.seconds", "seconds");
      defaultTranslations.put("time.minutes", "minutes");
      defaultTranslations.put("time.hours", "hours");
      defaultTranslations.put("time.days", "days");
      defaultTranslations.put("time.weeks", "weeks");
      defaultTranslations.put("time.months", "months");
      defaultTranslations.put("time.years", "years");
      defaultTranslations.put("time.ago", "{0} ago");
      defaultTranslations.put("time.in", "in {0}");
      defaultTranslations.put("message.success", "Operation completed successfully");
      defaultTranslations.put("message.error", "An error occurred");
      defaultTranslations.put("message.loading", "Loading...");
      defaultTranslations.put("message.confirm", "Are you sure?");
      defaultTranslations.put("message.no_data", "No data available");
      defaultTranslations.put("message.saved", "Changes saved successfully");
      defaultTranslations.put("pagination.page", "Page");
      defaultTranslations.put("pagination.of", "of");
      defaultTranslations.put("pagination.showing", "Showing {0} to {1} of {2} entries");
      defaultTranslations.put("pagination.per_page", "Per page");
      this.dashboardTranslations.put("en_us", defaultTranslations);

      try {
         Path langFile = this.langDirectory.resolve("en_us_dashboard.json");
         Files.writeString(langFile, this.gson.toJson(defaultTranslations), StandardCharsets.UTF_8);
         LOGGER.info("Created default dashboard translation file: {}", langFile);
      } catch (IOException var3) {
         LOGGER.error("Failed to create default dashboard translation file", var3);
      }
   }

   public String translate(String key, String language) {
      return this.translate(key, language, key);
   }

   public String translate(String key, String language, String fallback) {
      Map<String, String> dashboardMap = this.dashboardTranslations.get(language);
      if (dashboardMap != null && dashboardMap.containsKey(key)) {
         return dashboardMap.get(key);
      } else {
         if (!language.equals("en_us")) {
            dashboardMap = this.dashboardTranslations.get("en_us");
            if (dashboardMap != null && dashboardMap.containsKey(key)) {
               return dashboardMap.get(key);
            }
         }

         return MessageUtil.hasTranslation(key) ? MessageUtil.localize(key) : fallback;
      }
   }

   public String translate(String key, String language, Object... args) {
      String translation = this.translate(key, language);

      for (int i = 0; i < args.length; i++) {
         translation = translation.replace("{" + i + "}", String.valueOf(args[i]));
      }

      return translation;
   }

   public Map<String, String> getTranslations(String language) {
      Map<String, String> dashboardMap = this.dashboardTranslations.get(language);
      return dashboardMap != null ? new HashMap<>(dashboardMap) : new HashMap<>(this.dashboardTranslations.getOrDefault("en_us", new HashMap<>()));
   }

   public Map<String, String> getAllTranslations(String language) {
      Map<String, String> combined = new HashMap<>();
      Map<String, String> dashboardMap = this.dashboardTranslations.get(language);
      if (dashboardMap != null) {
         combined.putAll(dashboardMap);
      }

      return combined;
   }

   public List<LocalizationManager.LanguageInfo> getAvailableLanguages() {
      return new ArrayList<>(this.availableLanguages.values());
   }

   public boolean isLanguageSupported(String language) {
      return this.availableLanguages.containsKey(language);
   }

   public boolean isRTL(String language) {
      LocalizationManager.LanguageInfo info = this.availableLanguages.get(language);
      return info != null ? info.isRtl() : RTL_LANGUAGES.contains(language);
   }

   public String formatDateTime(Instant instant, String language, FormatStyle style) {
      Locale locale = this.getLocaleFromLanguage(language);
      DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(style).withLocale(locale).withZone(ZoneId.systemDefault());
      return formatter.format(instant);
   }

   public String formatDate(Instant instant, String language, FormatStyle style) {
      Locale locale = this.getLocaleFromLanguage(language);
      DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).withZone(ZoneId.systemDefault());
      return formatter.format(instant);
   }

   public String formatTime(Instant instant, String language, FormatStyle style) {
      Locale locale = this.getLocaleFromLanguage(language);
      DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedTime(style).withLocale(locale).withZone(ZoneId.systemDefault());
      return formatter.format(instant);
   }

   public String formatNumber(Number number, String language) {
      Locale locale = this.getLocaleFromLanguage(language);
      NumberFormat formatter = NumberFormat.getInstance(locale);
      return formatter.format(number);
   }

   public String formatCurrency(Number amount, String language) {
      Locale locale = this.getLocaleFromLanguage(language);
      NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
      return formatter.format(amount);
   }

   public String formatPercent(Number value, String language) {
      Locale locale = this.getLocaleFromLanguage(language);
      NumberFormat formatter = NumberFormat.getPercentInstance(locale);
      return formatter.format(value);
   }

   private Locale getLocaleFromLanguage(String language) {
      String[] parts = language.split("_");
      if (parts.length == 2) {
         return new Builder().setLanguage(parts[0]).setRegion(parts[1].toUpperCase()).build();
      } else {
         return parts.length == 1 ? new Builder().setLanguage(parts[0]).build() : Locale.US;
      }
   }

   public void reload() {
      this.dashboardTranslations.clear();
      this.loadDashboardTranslations();
      MessageUtil.reloadTranslations();
      LOGGER.info("Reloaded dashboard translation files and MessageUtil translations");
   }

   public static class LanguageInfo {
      private final String code;
      private final String nativeName;
      private final String englishName;
      private final String countryCode;
      private final boolean rtl;

      public LanguageInfo(String code, String nativeName, String englishName, String countryCode, boolean rtl) {
         this.code = code;
         this.nativeName = nativeName;
         this.englishName = englishName;
         this.countryCode = countryCode;
         this.rtl = rtl;
      }

      public String getCode() {
         return this.code;
      }

      public String getNativeName() {
         return this.nativeName;
      }

      public String getEnglishName() {
         return this.englishName;
      }

      public String getCountryCode() {
         return this.countryCode;
      }

      public boolean isRtl() {
         return this.rtl;
      }
   }
}
