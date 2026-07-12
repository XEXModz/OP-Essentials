package com.zerog.neoessentials.webdashboard.api;

public interface APIEndpoint {
   String getPath();

   String[] getSupportedMethods();

   boolean requiresAuthentication();

   String getRequiredPermission();
}
