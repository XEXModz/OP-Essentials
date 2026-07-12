package com.zerog.neoessentials.i18n;

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
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslationHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(TranslationHandler.class);
   private final Gson gson = new Gson();
   private final Map<String, String> userLanguages = new ConcurrentHashMap<>();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();

      try {
         String endpoint = path.replace("/api/i18n", "");
         if (endpoint.isEmpty() || endpoint.equals("/")) {
            endpoint = "/languages";
         }

         switch (endpoint) {
            case "/languages":
               if ("GET".equals(method)) {
                  this.handleGetLanguages(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/translations":
               if ("GET".equals(method)) {
                  this.handleGetTranslations(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/set":
               if ("POST".equals(method)) {
                  this.handleSetLanguage(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/get":
               if ("GET".equals(method)) {
                  this.handleGetLanguage(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/translate":
               if ("GET".equals(method)) {
                  this.handleTranslate(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/format":
               if ("GET".equals(method)) {
                  this.handleFormat(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            case "/reload":
               if ("POST".equals(method)) {
                  this.handleReload(exchange);
               } else {
                  this.sendMethodNotAllowed(exchange);
               }
               break;
            default:
               this.sendNotFound(exchange);
         }
      } catch (Exception var7) {
         LOGGER.error("Error handling translation request", var7);
         this.sendError(exchange, "Internal server error: " + var7.getMessage());
      }
   }

   private void handleGetLanguages(HttpExchange exchange) throws IOException {
      List<LocalizationManager.LanguageInfo> languages = LocalizationManager.getInstance().getAvailableLanguages();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("count", languages.size());
      JsonArray languagesArray = new JsonArray();

      for (LocalizationManager.LanguageInfo lang : languages) {
         JsonObject langObj = new JsonObject();
         langObj.addProperty("code", lang.getCode());
         langObj.addProperty("nativeName", lang.getNativeName());
         langObj.addProperty("englishName", lang.getEnglishName());
         langObj.addProperty("countryCode", lang.getCountryCode());
         langObj.addProperty("rtl", lang.isRtl());
         languagesArray.add(langObj);
      }

      response.add("languages", languagesArray);
      this.sendJsonResponse(exchange, 200, response);
   }

   private void handleGetTranslations(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String language = params.getOrDefault("language", "en_us");
      if (!LocalizationManager.getInstance().isLanguageSupported(language)) {
         this.sendBadRequest(exchange, "Unsupported language: " + language);
      } else {
         Map<String, String> translations = LocalizationManager.getInstance().getTranslations(language);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("language", language);
         response.addProperty("count", translations.size());
         response.addProperty("rtl", LocalizationManager.getInstance().isRTL(language));
         JsonObject translationsObj = new JsonObject();
         translations.forEach(translationsObj::addProperty);
         response.add("translations", translationsObj);
         this.sendJsonResponse(exchange, 200, response);
      }
   }

   private void handleSetLanguage(HttpExchange exchange) throws IOException {
      try {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         JsonObject request = (JsonObject)this.gson.fromJson(body, JsonObject.class);
         String session = request.has("session") ? request.get("session").getAsString() : null;
         String language = request.has("language") ? request.get("language").getAsString() : null;
         if (session == null || session.isEmpty()) {
            this.sendBadRequest(exchange, "Missing 'session' field");
            return;
         }

         if (language == null || language.isEmpty()) {
            this.sendBadRequest(exchange, "Missing 'language' field");
            return;
         }

         if (!LocalizationManager.getInstance().isLanguageSupported(language)) {
            this.sendBadRequest(exchange, "Unsupported language: " + language);
            return;
         }

         this.userLanguages.put(session, language);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("message", "Language preference saved");
         response.addProperty("session", session);
         response.addProperty("language", language);
         response.addProperty("rtl", LocalizationManager.getInstance().isRTL(language));
         this.sendJsonResponse(exchange, 200, response);
      } catch (Exception var7) {
         LOGGER.error("Failed to set language", var7);
         this.sendError(exchange, "Failed to set language: " + var7.getMessage());
      }
   }

   private void handleGetLanguage(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String session = params.get("session");
      if (session != null && !session.isEmpty()) {
         String language = this.userLanguages.getOrDefault(session, "en_us");
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("session", session);
         response.addProperty("language", language);
         response.addProperty("rtl", LocalizationManager.getInstance().isRTL(language));
         this.sendJsonResponse(exchange, 200, response);
      } else {
         this.sendBadRequest(exchange, "Missing 'session' parameter");
      }
   }

   private void handleTranslate(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String key = params.get("key");
      String language = params.getOrDefault("language", "en_us");
      if (key != null && !key.isEmpty()) {
         String translation = LocalizationManager.getInstance().translate(key, language);
         JsonObject response = new JsonObject();
         response.addProperty("success", true);
         response.addProperty("key", key);
         response.addProperty("language", language);
         response.addProperty("translation", translation);
         this.sendJsonResponse(exchange, 200, response);
      } else {
         this.sendBadRequest(exchange, "Missing 'key' parameter");
      }
   }

   private void handleFormat(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String type = params.get("type");
      String value = params.get("value");
      String language = params.getOrDefault("language", "en_us");
      String style = params.getOrDefault("style", "MEDIUM");
      if (type == null || type.isEmpty()) {
         this.sendBadRequest(exchange, "Missing 'type' parameter");
      } else if (value != null && !value.isEmpty()) {
         try {
            FormatStyle formatStyle = FormatStyle.valueOf(style.toUpperCase());
            String response = type.toLowerCase();
            String formatted;
            switch (response) {
               case "datetime": {
                  Instant instant = Instant.parse(value);
                  formatted = LocalizationManager.getInstance().formatDateTime(instant, language, formatStyle);
                  break;
               }
               case "date": {
                  Instant instant = Instant.parse(value);
                  formatted = LocalizationManager.getInstance().formatDate(instant, language, formatStyle);
                  break;
               }
               case "time": {
                  Instant instant = Instant.parse(value);
                  formatted = LocalizationManager.getInstance().formatTime(instant, language, formatStyle);
                  break;
               }
               case "number": {
                  Number number = Double.parseDouble(value);
                  formatted = LocalizationManager.getInstance().formatNumber(number, language);
                  break;
               }
               case "currency": {
                  Number number = Double.parseDouble(value);
                  formatted = LocalizationManager.getInstance().formatCurrency(number, language);
                  break;
               }
               case "percent": {
                  Number number = Double.parseDouble(value);
                  formatted = LocalizationManager.getInstance().formatPercent(number, language);
                  break;
               }
               default:
                  this.sendBadRequest(exchange, "Invalid format type: " + type);
                  return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("type", type);
            response.addProperty("value", value);
            response.addProperty("language", language);
            response.addProperty("formatted", formatted);
            this.sendJsonResponse(exchange, 200, response);
         } catch (Exception var13) {
            LOGGER.error("Failed to format value", var13);
            this.sendError(exchange, "Failed to format value: " + var13.getMessage());
         }
      } else {
         this.sendBadRequest(exchange, "Missing 'value' parameter");
      }
   }

   private void handleReload(HttpExchange exchange) throws IOException {
      LocalizationManager.getInstance().reload();
      JsonObject response = new JsonObject();
      response.addProperty("success", true);
      response.addProperty("message", "Language files reloaded");
      response.addProperty("timestamp", Instant.now().toString());
      this.sendJsonResponse(exchange, 200, response);
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
