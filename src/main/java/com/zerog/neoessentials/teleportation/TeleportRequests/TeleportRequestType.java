package com.zerog.neoessentials.teleportation.TeleportRequests;

public enum TeleportRequestType {
   TPA("tpa"),
   TPAHERE("tpahere");

   private final String command;

   private TeleportRequestType(String command) {
      this.command = command;
   }

   public String getCommand() {
      return this.command;
   }

   @Override
   public String toString() {
      return this.command;
   }

   public static TeleportRequestType fromCommand(String command) {
      for (TeleportRequestType type : values()) {
         if (type.command.equalsIgnoreCase(command)) {
            return type;
         }
      }

      return null;
   }
}
