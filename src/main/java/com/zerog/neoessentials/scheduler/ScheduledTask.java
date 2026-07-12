package com.zerog.neoessentials.scheduler;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScheduledTask {
   private String id = UUID.randomUUID().toString();
   private String name;
   private String description;
   private String cronExpression;
   private ScheduledTask.TaskType taskType;
   private List<String> commands;
   private boolean enabled = true;
   private String timezone = ZoneId.systemDefault().getId();
   private ScheduledTask.TaskConditions conditions;
   private String createdBy;
   private long createdAt;
   private long updatedAt;
   private long lastExecutionTime;
   private long nextExecutionTime;
   private int executionCount;

   public ScheduledTask() {
      this.commands = new ArrayList<>();
      this.conditions = new ScheduledTask.TaskConditions();
      this.createdAt = System.currentTimeMillis();
      this.executionCount = 0;
   }

   public ScheduledTask(String name, String cronExpression, ScheduledTask.TaskType taskType) {
      this();
      this.name = name;
      this.cronExpression = cronExpression;
      this.taskType = taskType;
   }

   public String getId() {
      return this.id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return this.description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getCronExpression() {
      return this.cronExpression;
   }

   public void setCronExpression(String cronExpression) {
      this.cronExpression = cronExpression;
   }

   public ScheduledTask.TaskType getTaskType() {
      return this.taskType;
   }

   public void setTaskType(ScheduledTask.TaskType taskType) {
      this.taskType = taskType;
   }

   public List<String> getCommands() {
      return this.commands;
   }

   public void setCommands(List<String> commands) {
      this.commands = commands;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public String getTimezone() {
      return this.timezone;
   }

   public void setTimezone(String timezone) {
      this.timezone = timezone;
   }

   public ScheduledTask.TaskConditions getConditions() {
      return this.conditions;
   }

   public void setConditions(ScheduledTask.TaskConditions conditions) {
      this.conditions = conditions;
   }

   public String getCreatedBy() {
      return this.createdBy;
   }

   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public long getUpdatedAt() {
      return this.updatedAt;
   }

   public void setUpdatedAt(long updatedAt) {
      this.updatedAt = updatedAt;
   }

   public long getLastExecutionTime() {
      return this.lastExecutionTime;
   }

   public void setLastExecutionTime(long lastExecutionTime) {
      this.lastExecutionTime = lastExecutionTime;
   }

   public long getNextExecutionTime() {
      return this.nextExecutionTime;
   }

   public void setNextExecutionTime(long nextExecutionTime) {
      this.nextExecutionTime = nextExecutionTime;
   }

   public int getExecutionCount() {
      return this.executionCount;
   }

   public void setExecutionCount(int executionCount) {
      this.executionCount = executionCount;
   }

   public void incrementExecutionCount() {
      this.executionCount++;
   }

   public static class TaskConditions {
      private Integer minPlayers;
      private Integer maxPlayers;
      private Double maxServerLoad;
      private Long startTime;
      private Long endTime;
      private List<String> requiredDimensions = new ArrayList<>();

      public Integer getMinPlayers() {
         return this.minPlayers;
      }

      public void setMinPlayers(Integer minPlayers) {
         this.minPlayers = minPlayers;
      }

      public Integer getMaxPlayers() {
         return this.maxPlayers;
      }

      public void setMaxPlayers(Integer maxPlayers) {
         this.maxPlayers = maxPlayers;
      }

      public Double getMaxServerLoad() {
         return this.maxServerLoad;
      }

      public void setMaxServerLoad(Double maxServerLoad) {
         this.maxServerLoad = maxServerLoad;
      }

      public Long getStartTime() {
         return this.startTime;
      }

      public void setStartTime(Long startTime) {
         this.startTime = startTime;
      }

      public Long getEndTime() {
         return this.endTime;
      }

      public void setEndTime(Long endTime) {
         this.endTime = endTime;
      }

      public List<String> getRequiredDimensions() {
         return this.requiredDimensions;
      }

      public void setRequiredDimensions(List<String> requiredDimensions) {
         this.requiredDimensions = requiredDimensions;
      }
   }

   public static enum TaskType {
      COMMAND,
      BACKUP,
      RESTART,
      BROADCAST,
      CUSTOM;
   }
}
