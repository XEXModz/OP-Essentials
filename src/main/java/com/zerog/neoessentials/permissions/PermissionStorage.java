package com.zerog.neoessentials.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerog.neoessentials.util.ResourceUtil;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PermissionStorage {
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().disableHtmlEscaping().create();
   private static final Path FILE_PATH = ResourceUtil.getConfigPath("permissions.json");
   private static final Path PLAYERDATA_PATH = ResourceUtil.getConfigPath("permissions/playerdata.json");

   public static void save(PermissionManager manager) throws IOException {
      if (!PermissionSystem.isUsingExternal()) {
         Map<String, Object> groupData = new HashMap<>();
         groupData.put("defaultGroup", manager.getDefaultGroup());
         List<Object> groups = new ArrayList<>();

         for (PermissionGroup group : manager.getGroups()) {
            Map<String, Object> g = new HashMap<>();
            g.put("name", group.getName());
            g.put("prefix", group.getPrefix());
            g.put("suffix", group.getSuffix());
            g.put("permissions", group.getPermissions());
            g.put("inherits", group.getInherits());
            groups.add(g);
         }

         groupData.put("groups", groups);
         Files.createDirectories(FILE_PATH.getParent());
         Path tempFile = FILE_PATH.resolveSibling(FILE_PATH.getFileName() + ".tmp");

         try (Writer writer = Files.newBufferedWriter(tempFile)) {
            GSON.toJson(groupData, writer);
         }

         Files.move(tempFile, FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         List<Object> users = new ArrayList<>();

         for (PermissionUser user : manager.getUsers()) {
            Map<String, Object> u = new HashMap<>();
            u.put("uuid", user.getUuid().toString());
            u.put("group", user.getGroup());
            u.put("permissions", user.getPermissions());
            if (!user.getPrefix().isEmpty()) {
               u.put("prefix", user.getPrefix());
            }

            if (!user.getSuffix().isEmpty()) {
               u.put("suffix", user.getSuffix());
            }

            users.add(u);
         }

         Map<String, Object> userData = new HashMap<>();
         userData.put("users", users);
         Files.createDirectories(PLAYERDATA_PATH.getParent());
         Path tempUserFile = PLAYERDATA_PATH.resolveSibling(PLAYERDATA_PATH.getFileName() + ".tmp");

         try (Writer writer = Files.newBufferedWriter(tempUserFile)) {
            GSON.toJson(userData, writer);
         }

         Files.move(tempUserFile, PLAYERDATA_PATH, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public static void load(PermissionManager manager) throws IOException {
      if (!PermissionSystem.isUsingExternal()) {
         if (Files.exists(FILE_PATH)) {
            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
               Map<?, ?> groupData = (Map<?, ?>)GSON.fromJson(reader, Map.class);
               if (groupData == null) {
                  return;
               }

               Object defaultGroup = groupData.get("defaultGroup");
               if (defaultGroup != null) {
                  manager.setDefaultGroup(defaultGroup.toString());
               }

               Object groups = groupData.get("groups");
               if (groups instanceof List) {
                  for (Object o : (List)groups) {
                     if (o instanceof Map) {
                        Map<?, ?> g = (Map<?, ?>)o;
                        PermissionGroup group = new PermissionGroup(g.get("name").toString());
                        group.setPrefix((String)g.get("prefix"));
                        group.setSuffix((String)g.get("suffix"));
                        Object permsObj = g.get("permissions");
                        if (permsObj instanceof List) {
                           for (Object perm : (List)permsObj) {
                              if (perm != null) {
                                 group.addPermission(perm.toString());
                              }
                           }
                        }

                        Object inheritsObj = g.get("inherits");
                        if (inheritsObj instanceof List) {
                           for (Object inh : (List)inheritsObj) {
                              if (inh != null) {
                                 group.addInheritance(inh.toString());
                              }
                           }
                        }

                        manager.addGroup(group);
                     }
                  }
               }
            }
         }

         if (Files.exists(PLAYERDATA_PATH)) {
            try (Reader reader = Files.newBufferedReader(PLAYERDATA_PATH)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

               for (JsonElement ue : root.getAsJsonArray("users")) {
                  JsonObject u = ue.getAsJsonObject();
                  PermissionUser user = new PermissionUser(UUID.fromString(u.get("uuid").getAsString()), u.get("group").getAsString());

                  for (JsonElement p : u.getAsJsonArray("permissions")) {
                     user.addPermission(p.getAsString());
                  }

                  if (u.has("prefix") && !u.get("prefix").isJsonNull()) {
                     user.setPrefix(u.get("prefix").getAsString());
                  }

                  if (u.has("suffix") && !u.get("suffix").isJsonNull()) {
                     user.setSuffix(u.get("suffix").getAsString());
                  }

                  manager.addUser(user);
               }
            }
         } else if (Files.exists(FILE_PATH)) {
            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               if (root.has("users")) {
                  JsonArray users = root.getAsJsonArray("users");
                  List<Object> migratedUsers = new ArrayList<>();

                  for (JsonElement ue : users) {
                     JsonObject u = ue.getAsJsonObject();
                     PermissionUser user = new PermissionUser(UUID.fromString(u.get("uuid").getAsString()), u.get("group").getAsString());

                     for (JsonElement p : u.getAsJsonArray("permissions")) {
                        user.addPermission(p.getAsString());
                     }

                     if (u.has("prefix") && !u.get("prefix").isJsonNull()) {
                        user.setPrefix(u.get("prefix").getAsString());
                     }

                     if (u.has("suffix") && !u.get("suffix").isJsonNull()) {
                        user.setSuffix(u.get("suffix").getAsString());
                     }

                     manager.addUser(user);
                     Map<String, Object> userMap = new HashMap<>();
                     userMap.put("uuid", user.getUuid().toString());
                     userMap.put("group", user.getGroup());
                     userMap.put("permissions", user.getPermissions());
                     if (!user.getPrefix().isEmpty()) {
                        userMap.put("prefix", user.getPrefix());
                     }

                     if (!user.getSuffix().isEmpty()) {
                        userMap.put("suffix", user.getSuffix());
                     }

                     migratedUsers.add(userMap);
                  }

                  Map<String, Object> userData = new HashMap<>();
                  userData.put("users", migratedUsers);
                  Files.createDirectories(PLAYERDATA_PATH.getParent());

                  try (Writer writer = Files.newBufferedWriter(PLAYERDATA_PATH)) {
                     GSON.toJson(userData, writer);
                  }

                  JsonObject newRoot = root.deepCopy();
                  newRoot.remove("users");

                  try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                     GSON.toJson(newRoot, writer);
                  }
               }
            }
         }
      }
   }

   public static boolean hasPermission(PermissionManager manager, UUID uuid, String permission) {
      return manager.hasPermission(uuid, permission);
   }
}
