package com.zerog.neoessentials.webdashboard.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zerog.neoessentials.webdashboard.security.AuthenticationManager;
import com.zerog.neoessentials.webdashboard.security.Session;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardWebSocketServer extends WebSocketServer {
   private static final Logger LOGGER = LoggerFactory.getLogger(DashboardWebSocketServer.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
   private static final long MESSAGE_COOLDOWN_MS = 100L;
   private static DashboardWebSocketServer INSTANCE;
   private final AtomicBoolean started = new AtomicBoolean(false);
   private final Map<WebSocket, Set<String>> clientSubscriptions = new ConcurrentHashMap<>();
   private final Set<WebSocket> authenticatedClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
   private final Map<WebSocket, Long> lastMessageTime = new ConcurrentHashMap<>();

   private DashboardWebSocketServer(int port) {
      super(new InetSocketAddress(port));
      this.setReuseAddr(true);
      LOGGER.info("WebSocket server created on port {}", port);
   }

   public static synchronized DashboardWebSocketServer getInstance(int port) {
      if (INSTANCE == null) {
         INSTANCE = new DashboardWebSocketServer(port);
      }

      return INSTANCE;
   }

   public static synchronized DashboardWebSocketServer getInstance() {
      if (INSTANCE == null) {
         throw new IllegalStateException("WebSocket server not initialized. Call getInstance(port) first.");
      } else {
         return INSTANCE;
      }
   }

   public boolean startIfNotStarted() {
      if (this.started.compareAndSet(false, true)) {
         this.start();
         return true;
      } else {
         LOGGER.debug("WebSocket server start requested but already started; ignoring");
         return false;
      }
   }

   public boolean isStarted() {
      return this.started.get();
   }

   public static synchronized void shutdownAndReset(int timeoutMillis) {
      if (INSTANCE != null) {
         try {
            INSTANCE.stop(timeoutMillis);
         } catch (Exception var5) {
            LOGGER.warn("Error stopping WebSocket server during reset: {}", var5.getMessage());
         } finally {
            INSTANCE = null;
         }
      }
   }

   public void onOpen(WebSocket conn, ClientHandshake handshake) {
      this.clientSubscriptions.put(conn, ConcurrentHashMap.newKeySet());
      LOGGER.debug("WebSocket connection opened: {}", conn.getRemoteSocketAddress());
      JsonObject welcome = new JsonObject();
      welcome.addProperty("type", "welcome");
      welcome.addProperty("message", "Connected to NeoEssentials Dashboard");
      welcome.addProperty("timestamp", System.currentTimeMillis());
      this.sendToClient(conn, welcome);
   }

   public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      this.clientSubscriptions.remove(conn);
      this.authenticatedClients.remove(conn);
      this.lastMessageTime.remove(conn);
      LOGGER.debug("WebSocket connection closed: {} (code={}, reason={})", new Object[]{conn.getRemoteSocketAddress(), code, reason});
   }

   public void onMessage(WebSocket conn, String message) {
      long now = System.currentTimeMillis();
      Long lastTime = this.lastMessageTime.get(conn);
      if (lastTime != null && now - lastTime < 100L) {
         this.sendError(conn, "Rate limit exceeded. Please slow down.");
      } else {
         this.lastMessageTime.put(conn, now);

         try {
            JsonObject msg = (JsonObject)GSON.fromJson(message, JsonObject.class);
            String type = msg.has("type") ? msg.get("type").getAsString() : "";
            switch (type) {
               case "authenticate":
                  this.handleAuthenticate(conn, msg);
                  break;
               case "subscribe":
                  this.handleSubscribe(conn, msg);
                  break;
               case "unsubscribe":
                  this.handleUnsubscribe(conn, msg);
                  break;
               case "ping":
                  this.handlePing(conn);
                  break;
               default:
                  this.sendError(conn, "Unknown message type: " + type);
            }
         } catch (Exception var10) {
            LOGGER.warn("Error processing WebSocket message from {}: {}", conn.getRemoteSocketAddress(), var10.getMessage());
            this.sendError(conn, "Invalid message format");
         }
      }
   }

   public void onError(WebSocket conn, Exception ex) {
      LOGGER.error("WebSocket error on {}: {}", conn != null ? conn.getRemoteSocketAddress() : "unknown", ex.getMessage());
   }

   public void onStart() {
      LOGGER.info("WebSocket server started successfully on port {}", this.getPort());
      this.setConnectionLostTimeout(30);
   }

   private void handleAuthenticate(WebSocket conn, JsonObject msg) {
      if (!msg.has("sessionId")) {
         this.sendError(conn, "Missing sessionId in authentication request");
      } else {
         String sessionId = msg.get("sessionId").getAsString();
         AuthenticationManager authManager = AuthenticationManager.getInstance();
         Session session = authManager.validateSession(sessionId);
         if (session == null) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "auth_error");
            error.addProperty("message", "Invalid or expired session");
            this.sendToClient(conn, error);
         } else {
            this.authenticatedClients.add(conn);
            JsonObject response = new JsonObject();
            response.addProperty("type", "authenticated");
            response.addProperty("message", "Authentication successful");
            response.addProperty("username", session.getUsername());
            response.addProperty("role", session.getRole().name());
            response.addProperty("timestamp", System.currentTimeMillis());
            this.sendToClient(conn, response);
            LOGGER.debug("WebSocket client authenticated: {} ({})", session.getUsername(), conn.getRemoteSocketAddress());
         }
      }
   }

   private void handleSubscribe(WebSocket conn, JsonObject msg) {
      if (!this.authenticatedClients.contains(conn)) {
         this.sendError(conn, "Authentication required. Please authenticate first.");
      } else if (!msg.has("channels")) {
         this.sendError(conn, "Missing 'channels' field");
      } else {
         Set<String> subs = this.clientSubscriptions.get(conn);
         msg.getAsJsonArray("channels").forEach(ch -> subs.add(ch.getAsString()));
         JsonObject response = new JsonObject();
         response.addProperty("type", "subscribed");
         response.add("channels", msg.get("channels"));
         this.sendToClient(conn, response);
      }
   }

   private void handleUnsubscribe(WebSocket conn, JsonObject msg) {
      if (!msg.has("channels")) {
         this.sendError(conn, "Missing 'channels' field");
      } else {
         Set<String> subs = this.clientSubscriptions.getOrDefault(conn, Collections.emptySet());
         msg.getAsJsonArray("channels").forEach(ch -> subs.remove(ch.getAsString()));
         JsonObject response = new JsonObject();
         response.addProperty("type", "unsubscribed");
         response.add("channels", msg.get("channels"));
         this.sendToClient(conn, response);
      }
   }

   private void handlePing(WebSocket conn) {
      JsonObject pong = new JsonObject();
      pong.addProperty("type", "pong");
      pong.addProperty("timestamp", System.currentTimeMillis());
      this.sendToClient(conn, pong);
   }

   public void broadcast(String channel, JsonObject data) {
      data.addProperty("channel", channel);
      data.addProperty("timestamp", System.currentTimeMillis());
      String json = GSON.toJson(data);

      for (Entry<WebSocket, Set<String>> entry : this.clientSubscriptions.entrySet()) {
         if (entry.getValue().contains(channel)) {
            WebSocket client = entry.getKey();
            if (client.isOpen()) {
               client.send(json);
            }
         }
      }
   }

   public void broadcastToAll(JsonObject data) {
      data.addProperty("timestamp", System.currentTimeMillis());
      String json = GSON.toJson(data);

      for (WebSocket conn : this.getConnections()) {
         if (conn.isOpen()) {
            conn.send(json);
         }
      }
   }

   private void sendError(WebSocket conn, String message) {
      JsonObject error = new JsonObject();
      error.addProperty("type", "error");
      error.addProperty("message", message);
      this.sendToClient(conn, error);
   }

   public void sendToClient(WebSocket conn, JsonObject data) {
      if (conn != null && conn.isOpen()) {
         data.addProperty("timestamp", System.currentTimeMillis());
         conn.send(GSON.toJson(data));
      }
   }

   public int getClientCount() {
      return this.getConnections().size();
   }

   public int getAuthenticatedClientCount() {
      return this.authenticatedClients.size();
   }
}
