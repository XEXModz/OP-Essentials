package com.zerog.neoessentials.docs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zerog.neoessentials.webdashboard.security.CorsHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentationHandler implements HttpHandler {
   private static final Logger LOGGER = LoggerFactory.getLogger(DocumentationHandler.class);
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final DocumentationManager docManager = DocumentationManager.getInstance();

   @Override
   public void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();

      try {
         if (path.equals("/api/docs/sections") && method.equals("GET")) {
            this.handleGetSections(exchange);
         } else if (path.startsWith("/api/docs/sections/") && method.equals("GET")) {
            this.handleGetSection(exchange, path);
         } else if (path.equals("/api/docs/api") && method.equals("GET")) {
            this.handleGetApiEndpoints(exchange);
         } else if (path.startsWith("/api/docs/api/") && method.equals("GET")) {
            this.handleGetApiEndpoint(exchange, path);
         } else if (path.equals("/api/docs/tutorials") && method.equals("GET")) {
            this.handleGetTutorials(exchange);
         } else if (path.startsWith("/api/docs/tutorials/") && method.equals("GET")) {
            this.handleGetTutorial(exchange, path);
         } else if (path.equals("/api/docs/faq") && method.equals("GET")) {
            this.handleGetFaq(exchange);
         } else if (path.equals("/api/docs/faq/search") && method.equals("GET")) {
            this.handleSearchFaq(exchange);
         } else if (path.equals("/api/docs/videos") && method.equals("GET")) {
            this.handleGetVideos(exchange);
         } else if (path.startsWith("/api/docs/videos/") && method.equals("GET")) {
            this.handleGetVideo(exchange, path);
         } else if (path.equals("/api/docs/search") && method.equals("GET")) {
            this.handleSearchAll(exchange);
         } else {
            this.sendResponse(exchange, 404, Map.of("success", false, "error", "Endpoint not found"));
         }
      } catch (Exception var5) {
         LOGGER.error("Error handling documentation request", var5);
         this.sendResponse(exchange, 500, Map.of("success", false, "error", "Internal server error: " + var5.getMessage()));
      }
   }

   private void handleGetSections(HttpExchange exchange) throws IOException {
      Map<String, DocumentationManager.DocumentationSection> sections = this.docManager.getAllSections();
      this.sendResponse(exchange, 200, Map.of("success", true, "count", sections.size(), "sections", sections.values()));
   }

   private void handleGetSection(HttpExchange exchange, String path) throws IOException {
      String sectionId = path.substring("/api/docs/sections/".length());
      DocumentationManager.DocumentationSection section = this.docManager.getSection(sectionId);
      if (section == null) {
         this.sendResponse(exchange, 404, Map.of("success", false, "error", "Section not found: " + sectionId));
      } else {
         this.sendResponse(exchange, 200, Map.of("success", true, "section", section));
      }
   }

   private void handleGetApiEndpoints(HttpExchange exchange) throws IOException {
      Map<String, DocumentationManager.ApiEndpoint> endpoints = this.docManager.getAllApiEndpoints();
      this.sendResponse(exchange, 200, Map.of("success", true, "count", endpoints.size(), "endpoints", endpoints.values()));
   }

   private void handleGetApiEndpoint(HttpExchange exchange, String path) throws IOException {
      String endpoint = path.substring("/api/docs/api".length());
      if (!endpoint.startsWith("/")) {
         endpoint = "/" + endpoint;
      }

      DocumentationManager.ApiEndpoint apiEndpoint = this.docManager.getApiEndpoint(endpoint);
      if (apiEndpoint == null) {
         this.sendResponse(exchange, 404, Map.of("success", false, "error", "API endpoint documentation not found: " + endpoint));
      } else {
         this.sendResponse(exchange, 200, Map.of("success", true, "endpoint", apiEndpoint));
      }
   }

   private void handleGetTutorials(HttpExchange exchange) throws IOException {
      List<DocumentationManager.Tutorial> tutorials = this.docManager.getAllTutorials();
      this.sendResponse(exchange, 200, Map.of("success", true, "count", tutorials.size(), "tutorials", tutorials));
   }

   private void handleGetTutorial(HttpExchange exchange, String path) throws IOException {
      String tutorialId = path.substring("/api/docs/tutorials/".length());
      DocumentationManager.Tutorial tutorial = this.docManager.getTutorial(tutorialId);
      if (tutorial == null) {
         this.sendResponse(exchange, 404, Map.of("success", false, "error", "Tutorial not found: " + tutorialId));
      } else {
         this.sendResponse(exchange, 200, Map.of("success", true, "tutorial", tutorial));
      }
   }

   private void handleGetFaq(HttpExchange exchange) throws IOException {
      List<DocumentationManager.FaqItem> faqItems = this.docManager.getAllFaqItems();
      Map<String, List<DocumentationManager.FaqItem>> byTag = new HashMap<>();
      Set<String> allTags = new HashSet<>();

      for (DocumentationManager.FaqItem item : faqItems) {
         for (String tag : item.tags) {
            allTags.add(tag);
            byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(item);
         }
      }

      this.sendResponse(exchange, 200, Map.of("success", true, "count", faqItems.size(), "items", faqItems, "tags", allTags, "byTag", byTag));
   }

   private void handleSearchFaq(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String query = params.get("q");
      if (query != null && !query.trim().isEmpty()) {
         List<DocumentationManager.FaqItem> results = this.docManager.searchFaq(query);
         this.sendResponse(exchange, 200, Map.of("success", true, "query", query, "count", results.size(), "results", results));
      } else {
         this.sendResponse(exchange, 400, Map.of("success", false, "error", "Missing query parameter 'q'"));
      }
   }

   private void handleGetVideos(HttpExchange exchange) throws IOException {
      List<DocumentationManager.VideoTutorial> videos = this.docManager.getAllVideoTutorials();
      this.sendResponse(exchange, 200, Map.of("success", true, "count", videos.size(), "videos", videos));
   }

   private void handleGetVideo(HttpExchange exchange, String path) throws IOException {
      String videoId = path.substring("/api/docs/videos/".length());
      DocumentationManager.VideoTutorial video = this.docManager.getVideoTutorial(videoId);
      if (video == null) {
         this.sendResponse(exchange, 404, Map.of("success", false, "error", "Video tutorial not found: " + videoId));
      } else {
         this.sendResponse(exchange, 200, Map.of("success", true, "video", video));
      }
   }

   private void handleSearchAll(HttpExchange exchange) throws IOException {
      Map<String, String> params = this.parseQueryParams(exchange.getRequestURI().getQuery());
      String query = params.get("q");
      if (query != null && !query.trim().isEmpty()) {
         String lowerQuery = query.toLowerCase();
         List<DocumentationManager.DocumentationSection> sectionResults = this.docManager
            .getAllSections()
            .values()
            .stream()
            .filter(
               s -> s.title.toLowerCase().contains(lowerQuery)
                     || s.description.toLowerCase().contains(lowerQuery)
                     || s.content.toLowerCase().contains(lowerQuery)
            )
            .toList();
         List<DocumentationManager.ApiEndpoint> apiResults = this.docManager
            .getAllApiEndpoints()
            .values()
            .stream()
            .filter(
               a -> a.name.toLowerCase().contains(lowerQuery)
                     || a.description.toLowerCase().contains(lowerQuery)
                     || a.endpoint.toLowerCase().contains(lowerQuery)
            )
            .toList();
         List<DocumentationManager.Tutorial> tutorialResults = this.docManager
            .getAllTutorials()
            .stream()
            .filter(t -> t.title.toLowerCase().contains(lowerQuery) || t.description.toLowerCase().contains(lowerQuery))
            .toList();
         List<DocumentationManager.FaqItem> faqResults = this.docManager.searchFaq(query);
         int totalResults = sectionResults.size() + apiResults.size() + tutorialResults.size() + faqResults.size();
         this.sendResponse(
            exchange,
            200,
            Map.of(
               "success",
               true,
               "query",
               query,
               "totalResults",
               totalResults,
               "results",
               Map.<String, List<DocumentationManager.DocumentationSection>>of(
                  "sections", sectionResults, "api", apiResults, "tutorials", tutorialResults, "faq", faqResults
               )
            )
         );
      } else {
         this.sendResponse(exchange, 400, Map.of("success", false, "error", "Missing query parameter 'q'"));
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
                  LOGGER.warn("Failed to decode parameter: {}", param, var10);
               }
            }
         }

         return params;
      } else {
         return params;
      }
   }

   private void sendResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
      String jsonResponse = this.gson.toJson(data);
      byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      CorsHandler.apply(exchange);
      exchange.sendResponseHeaders(statusCode, (long)responseBytes.length);

      try (OutputStream os = exchange.getResponseBody()) {
         os.write(responseBytes);
      }
   }
}
