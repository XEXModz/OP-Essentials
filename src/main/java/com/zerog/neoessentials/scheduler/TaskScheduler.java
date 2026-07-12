package com.zerog.neoessentials.scheduler;

import java.time.ZoneId;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(
   modid = "neoessentials"
)
public class TaskScheduler {
   private static final Logger LOGGER = LoggerFactory.getLogger(TaskScheduler.class);
   private static TaskScheduler INSTANCE;
   private static MinecraftServer server;
   private static int tickCounter = 0;
   private static final int CHECK_INTERVAL = 20;

   private TaskScheduler() {
   }

   public static TaskScheduler getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new TaskScheduler();
      }

      return INSTANCE;
   }

   public void setServer(MinecraftServer server) {
      TaskScheduler.server = server;
   }

   @SubscribeEvent
   public static void onServerTick(Post event) {
      if (server == null) {
         server = event.getServer();
      }

      tickCounter++;
      if (tickCounter >= 20) {
         tickCounter = 0;
         checkAndExecuteTasks();
      }
   }

   private static void checkAndExecuteTasks() {
      if (server != null) {
         TaskManager manager = TaskManager.getInstance();
         List<ScheduledTask> enabledTasks = manager.getEnabledTasks();
         long currentTime = System.currentTimeMillis();

         for (ScheduledTask task : enabledTasks) {
            try {
               if (task.getNextExecutionTime() > 0L && currentTime >= task.getNextExecutionTime()) {
                  if (manager.checkConditions(task, server)) {
                     executeTask(task);
                  } else {
                     LOGGER.debug("Task conditions not met, skipping: {}", task.getName());
                     CronParser parser = new CronParser(task.getCronExpression());
                     ZoneId tz = ZoneId.of(task.getTimezone());
                     long nextExecution = parser.getNextExecutionTime(tz);
                     task.setNextExecutionTime(nextExecution);
                  }
               }
            } catch (Exception var10) {
               LOGGER.error("Error checking task: {}", task.getName(), var10);
            }
         }
      }
   }

   public static void executeTask(ScheduledTask task) {
      LOGGER.info("Executing scheduled task: {}", task.getName());
      long startTime = System.currentTimeMillis();

      try {
         switch (task.getTaskType()) {
            case COMMAND:
               executeCommands(task);
               break;
            case BACKUP:
               executeBackup(task);
               break;
            case RESTART:
               executeRestart(task);
               break;
            case BROADCAST:
               executeBroadcast(task);
               break;
            case CUSTOM:
               executeCustom(task);
         }

         long executionTime = System.currentTimeMillis() - startTime;
         TaskManager.getInstance().recordExecution(task.getId(), true, "Task executed successfully", executionTime);
         LOGGER.info("Task executed successfully: {} ({}ms)", task.getName(), executionTime);
      } catch (Exception var6) {
         long executionTime = System.currentTimeMillis() - startTime;
         TaskManager.getInstance().recordExecution(task.getId(), false, "Error: " + var6.getMessage(), executionTime);
         LOGGER.error("Failed to execute task: {}", task.getName(), var6);
      }
   }

   private static void executeCommands(ScheduledTask task) {
      if (server == null) {
         throw new RuntimeException("Server not initialized");
      } else {
         List<String> commands = task.getCommands();
         if (commands != null && !commands.isEmpty()) {
            for (String command : commands) {
               try {
                  String cmd = command.startsWith("/") ? command.substring(1) : command;
                  server.execute(() -> {
                     try {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                        LOGGER.debug("Executed command: {}", cmd);
                     } catch (Exception var2) {
                        LOGGER.error("Failed to execute command: {}", cmd, var2);
                     }
                  });
               } catch (Exception var5) {
                  LOGGER.error("Error executing command: {}", command, var5);
               }
            }
         } else {
            throw new RuntimeException("No commands specified");
         }
      }
   }

   private static void executeBackup(ScheduledTask task) {
      if (server == null) {
         throw new RuntimeException("Server not initialized");
      } else {
         LOGGER.info("Executing backup task: {}", task.getName());
         server.execute(() -> {
            try {
               server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "save-all");
               LOGGER.info("Backup completed: {}", task.getName());
            } catch (Exception var2) {
               LOGGER.error("Failed to execute backup", var2);
            }
         });
      }
   }

   private static void executeRestart(ScheduledTask task) {
      if (server == null) {
         throw new RuntimeException("Server not initialized");
      } else {
         LOGGER.warn("Executing server restart task: {}", task.getName());
         Component message = Component.literal("§cServer restart scheduled by task: " + task.getName());

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
         }

         server.execute(() -> {
            try {
               server.halt(false);
            } catch (Exception var1x) {
               LOGGER.error("Failed to restart server", var1x);
            }
         });
      }
   }

   private static void executeBroadcast(ScheduledTask task) {
      if (server == null) {
         throw new RuntimeException("Server not initialized");
      } else {
         List<String> commands = task.getCommands();
         if (commands != null && !commands.isEmpty()) {
            String message = commands.get(0);
            Component chatMessage = Component.literal(message);
            server.execute(() -> {
               for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                  player.sendSystemMessage(chatMessage);
               }

               LOGGER.info("Broadcast message sent: {}", message);
            });
         } else {
            throw new RuntimeException("No message specified");
         }
      }
   }

   private static void executeCustom(ScheduledTask task) {
      LOGGER.info("Executing custom task: {}", task.getName());
      executeCommands(task);
   }
}
