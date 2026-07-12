package com.zerog.neoessentials.docs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentationManager {
   private static final Logger LOGGER = LoggerFactory.getLogger(DocumentationManager.class);
   private static DocumentationManager instance;
   private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
   private final Map<String, DocumentationManager.DocumentationSection> sections = new LinkedHashMap<>();
   private final Map<String, DocumentationManager.ApiEndpoint> apiEndpoints = new LinkedHashMap<>();
   private final List<DocumentationManager.Tutorial> tutorials = new ArrayList<>();
   private final List<DocumentationManager.FaqItem> faqItems = new ArrayList<>();
   private final List<DocumentationManager.VideoTutorial> videoTutorials = new ArrayList<>();
   private final Path docsDir = Paths.get("config", "neoessentials", "webdashboard", "docs");

   private DocumentationManager() {
   }

   public static synchronized DocumentationManager getInstance() {
      if (instance == null) {
         instance = new DocumentationManager();
      }

      return instance;
   }

   public void initialize() {
      LOGGER.info("Initializing NeoEssentials Documentation System...");

      try {
         Files.createDirectories(this.docsDir);
      } catch (IOException var2) {
         LOGGER.error("Failed to create documentation directory", var2);
      }

      this.loadDocumentation();
      LOGGER.info(
         "Documentation system initialized with {} sections, {} API endpoints, {} tutorials, {} FAQs",
         new Object[]{this.sections.size(), this.apiEndpoints.size(), this.tutorials.size(), this.faqItems.size()}
      );
   }

   private void loadDocumentation() {
      this.loadOrCreateSections();
      this.loadOrCreateApiDocumentation();
      this.loadOrCreateTutorials();
      this.loadOrCreateFaqItems();
      this.loadOrCreateVideoTutorials();
   }

   private void loadOrCreateSections() {
      this.sections
         .put(
            "getting-started",
            new DocumentationManager.DocumentationSection(
               "getting-started",
               "Getting Started",
               "Quick start guide to using the NeoEssentials dashboard",
               "# Getting Started with NeoEssentials Dashboard\n\nWelcome to the NeoEssentials Web Dashboard! This comprehensive administration panel allows you to manage your Minecraft server from anywhere.\n\n## Accessing the Dashboard\n\n1. Start your Minecraft server with NeoEssentials installed\n2. Open your web browser and navigate to: `http://localhost:8080`\n3. Log in with your administrator credentials\n\n## Dashboard Overview\n\nThe dashboard provides access to:\n- **Player Management**: View online players, manage inventories, and moderate users\n- **Server Control**: Monitor performance, view logs, execute commands\n- **Configuration**: Edit settings, manage permissions, configure features\n- **Database Tools**: Browse and query SQLite databases\n- **World Management**: Control world settings and properties\n\n## First Steps\n\n1. **Change Default Password**: Navigate to Settings → Security\n2. **Configure Permissions**: Set up user roles and permissions\n3. **Review Settings**: Check configuration in Settings → General\n4. **Explore Features**: Browse through the navigation menu to discover available tools\n\n## Need Help?\n\n- Check the FAQ section for common questions\n- Browse API Documentation for integration details\n- Watch video tutorials for step-by-step guides\n- Review feature tutorials for detailed instructions\n",
               1
            )
         );
      this.sections
         .put(
            "features",
            new DocumentationManager.DocumentationSection(
               "features",
               "Features Overview",
               "Complete list of dashboard features and capabilities",
               "# NeoEssentials Dashboard Features\n\n## Player Management\n- **User Management**: Create, edit, and delete user accounts\n- **Permission Editor**: Node-based permission system with inheritance\n- **Inventory Viewer**: View and modify player inventories\n- **Player Statistics**: Track player activity and achievements\n- **Online Status**: Monitor active players and sessions\n\n## Server Administration\n- **Server Console**: Execute commands and view live logs\n- **Performance Metrics**: Monitor TPS, memory, CPU usage\n- **Log Viewer**: Search and download server logs\n- **World Management**: Control world settings and dimensions\n- **Database Browser**: Query SQLite databases\n\n## Economy & Items\n- **Economy Overview**: Track transactions and balances\n- **Kit Configuration**: Create and manage item kits\n- **Resource Pack Manager**: Deploy and enforce resource packs\n\n## Communication\n- **Announcement System**: Broadcast messages to players\n- **Chat Moderation**: Monitor and moderate chat messages\n- **Event Calendar**: Schedule and manage server events\n\n## Teleportation\n- **Home & Warp Manager**: Manage player homes and warps\n- **TPA Management**: Control teleport requests\n- **Spawn Management**: Configure spawn points\n\n## Automation\n- **Scheduled Tasks**: Automate server maintenance\n- **Automated Backups**: Schedule world backups\n\n## Moderation\n- **Whitelist/Blacklist**: Control server access\n- **Ban Management**: Manage player bans and appeals\n- **Nickname Manager**: Control player display names\n\n## Advanced\n- **Map Viewer**: Interactive world map with player tracking\n- **Plugin Configuration**: Edit config files with live reload\n- **Multi-Language Support**: Localized dashboard in 11 languages\n",
               2
            )
         );
      this.sections
         .put(
            "security",
            new DocumentationManager.DocumentationSection(
               "security",
               "Security Best Practices",
               "Important security guidelines for dashboard administrators",
               "# Security Best Practices\n\n## Authentication\n\n1. **Change Default Credentials**: Immediately change the default admin password\n2. **Use Strong Passwords**: Require complex passwords for all accounts\n3. **Enable Two-Factor**: Consider implementing 2FA for admin accounts\n4. **Regular Password Rotation**: Change passwords periodically\n\n## Network Security\n\n1. **Firewall Configuration**: Restrict dashboard access to trusted IPs\n2. **HTTPS/SSL**: Use reverse proxy (nginx/Apache) with SSL certificates\n3. **Port Management**: Change default port 8080 if exposed to internet\n4. **VPN Access**: Consider requiring VPN for remote administration\n\n## Permission Management\n\n1. **Principle of Least Privilege**: Grant minimum necessary permissions\n2. **Role-Based Access**: Use roles instead of individual permissions\n3. **Regular Audits**: Review permission assignments regularly\n4. **Session Timeouts**: Configure appropriate session expiration\n\n## Data Protection\n\n1. **Regular Backups**: Enable automated backup system\n2. **Database Security**: Protect SQLite database files\n3. **Log Retention**: Configure appropriate log rotation\n4. **Sensitive Data**: Avoid storing sensitive information in configs\n\n## Monitoring\n\n1. **Audit Logs**: Review command execution logs regularly\n2. **Failed Login Attempts**: Monitor authentication failures\n3. **Unusual Activity**: Watch for suspicious API requests\n4. **Performance Alerts**: Set up alerts for resource abuse\n\n## Updates\n\n1. **Keep Updated**: Install security patches promptly\n2. **Dependency Management**: Update NeoForge and dependencies\n3. **Changelog Review**: Read update notes for security fixes\n",
               3
            )
         );
      this.sections
         .put(
            "troubleshooting",
            new DocumentationManager.DocumentationSection(
               "troubleshooting",
               "Troubleshooting",
               "Common issues and solutions",
               "# Troubleshooting Guide\n\n## Dashboard Won't Start\n\n**Problem**: Dashboard doesn't start on server launch\n\n**Solutions**:\n1. Check if port 8080 is already in use: `netstat -ano | grep 8080`\n2. Review server logs for error messages\n3. Verify NeoEssentials is properly installed in mods folder\n4. Check if Java has network permissions\n5. Try changing port in config file\n\n## Cannot Connect to Dashboard\n\n**Problem**: Browser shows \"Connection refused\" or timeout\n\n**Solutions**:\n1. Verify server is running and dashboard is started\n2. Check firewall rules allow connections to port 8080\n3. Try accessing from server: `http://localhost:8080`\n4. Verify correct IP address (use server's LAN IP)\n5. Check if reverse proxy (if used) is configured correctly\n\n## Login Issues\n\n**Problem**: Cannot log in with credentials\n\n**Solutions**:\n1. Verify username/password are correct (case-sensitive)\n2. Reset password using console command: `/neoessentials resetpassword`\n3. Check authentication logs in server console\n4. Clear browser cache and cookies\n5. Try incognito/private browsing mode\n\n## Features Not Loading\n\n**Problem**: Dashboard loads but features show errors\n\n**Solutions**:\n1. Check browser console (F12) for JavaScript errors\n2. Clear browser cache completely\n3. Try different browser (Chrome, Firefox, Edge)\n4. Verify API endpoints are responding: check Network tab\n5. Review server logs for backend errors\n\n## Performance Issues\n\n**Problem**: Dashboard is slow or unresponsive\n\n**Solutions**:\n1. Check server resources (CPU, RAM)\n2. Reduce log tail length in settings\n3. Limit database query result size\n4. Close unused browser tabs\n5. Check network latency to server\n\n## Permission Errors\n\n**Problem**: \"Access denied\" or \"Insufficient permissions\"\n\n**Solutions**:\n1. Verify user has correct role assigned\n2. Check permission nodes in Permission Editor\n3. Review role inheritance configuration\n4. Clear permission cache: `/neoessentials reloadperms`\n5. Check for permission negation entries\n\n## Database Issues\n\n**Problem**: Database browser shows errors\n\n**Solutions**:\n1. Verify database file exists and is readable\n2. Check file permissions on database\n3. Ensure database is not corrupted: use SQLite tools\n4. Refresh database list in dashboard\n5. Check if database is locked by another process\n\n## Map Viewer Issues\n\n**Problem**: Map not rendering or showing players\n\n**Solutions**:\n1. Verify world is loaded on server\n2. Check if players are in same dimension\n3. Clear map cache in browser\n4. Verify WebSocket connection is active\n5. Check console for map rendering errors\n\n## Getting More Help\n\nIf issues persist:\n1. Check server logs: `logs/latest.log`\n2. Enable debug logging in config\n3. Review GitHub Issues for similar problems\n4. Join Discord server for community support\n5. Submit bug report with logs and reproduction steps\n",
               4
            )
         );
      LOGGER.info("Loaded {} documentation sections", this.sections.size());
   }

   private void loadOrCreateApiDocumentation() {
      this.apiEndpoints
         .put(
            "/api/users",
            new DocumentationManager.ApiEndpoint(
               "/api/users",
               "User Management",
               "GET, POST, PUT, DELETE",
               "Manage user accounts, roles, and permissions",
               List.of(
                  new DocumentationManager.ApiExample(
                     "GET",
                     "/api/users",
                     null,
                     "List all users",
                     "{\n  \"success\": true,\n  \"users\": [\n    {\n      \"id\": \"uuid\",\n      \"username\": \"admin\",\n      \"role\": \"ADMIN\",\n      \"lastLogin\": \"2025-10-15T10:30:00Z\"\n    }\n  ]\n}"
                  ),
                  new DocumentationManager.ApiExample(
                     "POST",
                     "/api/users",
                     "{\n  \"username\": \"newuser\",\n  \"password\": \"secure_password\",\n  \"role\": \"MODERATOR\"\n}",
                     "Create new user",
                     "{\n  \"success\": true,\n  \"user\": {\n    \"id\": \"new-uuid\",\n    \"username\": \"newuser\",\n    \"role\": \"MODERATOR\"\n  }\n}"
                  )
               ),
               "Admin"
            )
         );
      this.apiEndpoints
         .put(
            "/api/performance/current",
            new DocumentationManager.ApiEndpoint(
               "/api/performance/current",
               "Performance Metrics",
               "GET",
               "Get current server performance metrics",
               List.of(
                  new DocumentationManager.ApiExample(
                     "GET",
                     "/api/performance/current",
                     null,
                     "Get current metrics",
                     "{\n  \"success\": true,\n  \"tps\": 20.0,\n  \"memoryUsed\": 2048,\n  \"memoryMax\": 4096,\n  \"cpuUsage\": 15.5,\n  \"playerCount\": 10,\n  \"entityCount\": 1523,\n  \"chunkCount\": 2048\n}"
                  )
               ),
               "All"
            )
         );
      this.apiEndpoints
         .put(
            "/api/database/query",
            new DocumentationManager.ApiEndpoint(
               "/api/database/query",
               "Database Query",
               "POST",
               "Execute read-only SQL queries on SQLite databases",
               List.of(
                  new DocumentationManager.ApiExample(
                     "POST",
                     "/api/database/query",
                     "{\n  \"database\": \"neoessentials.db\",\n  \"query\": \"SELECT * FROM players LIMIT 10\",\n  \"page\": 1,\n  \"pageSize\": 10\n}",
                     "Query database",
                     "{\n  \"success\": true,\n  \"columns\": [\"id\", \"uuid\", \"username\"],\n  \"rows\": [\n    [\"1\", \"uuid-here\", \"player1\"]\n  ],\n  \"totalRows\": 150,\n  \"page\": 1,\n  \"pageSize\": 10\n}"
                  )
               ),
               "Admin"
            )
         );
      this.apiEndpoints
         .put(
            "/api/i18n/languages",
            new DocumentationManager.ApiEndpoint(
               "/api/i18n/languages",
               "Available Languages",
               "GET",
               "List all supported dashboard languages",
               List.of(
                  new DocumentationManager.ApiExample(
                     "GET",
                     "/api/i18n/languages",
                     null,
                     "Get languages",
                     "{\n  \"success\": true,\n  \"count\": 11,\n  \"languages\": [\n    {\n      \"code\": \"en_us\",\n      \"nativeName\": \"English (United States)\",\n      \"englishName\": \"English\",\n      \"countryCode\": \"US\",\n      \"rtl\": false\n    }\n  ]\n}"
                  )
               ),
               "All"
            )
         );
      LOGGER.info("Loaded {} API endpoint documentations", this.apiEndpoints.size());
   }

   private void loadOrCreateTutorials() {
      this.tutorials
         .add(
            new DocumentationManager.Tutorial(
               "setup-permissions",
               "Setting Up Permissions",
               "Learn how to configure the permission system",
               "beginner",
               15,
               List.of(
                  new DocumentationManager.TutorialStep(1, "Navigate to Permission Editor", "Click 'Permissions' in the sidebar menu"),
                  new DocumentationManager.TutorialStep(2, "Create a Role", "Click 'Add Role' button and name it (e.g., 'Moderator')"),
                  new DocumentationManager.TutorialStep(
                     3, "Add Permission Nodes", "Click the role, then 'Add Permission'. Use wildcards like 'neoessentials.kick.*'"
                  ),
                  new DocumentationManager.TutorialStep(4, "Assign to Users", "Go to User Management, edit a user, and select the role"),
                  new DocumentationManager.TutorialStep(5, "Test Permissions", "Have the user log in and verify they can access features")
               )
            )
         );
      this.tutorials
         .add(
            new DocumentationManager.Tutorial(
               "create-backup",
               "Creating Automated Backups",
               "Set up scheduled world backups",
               "intermediate",
               10,
               List.of(
                  new DocumentationManager.TutorialStep(1, "Open Backup Manager", "Navigate to 'Backups' in sidebar"),
                  new DocumentationManager.TutorialStep(2, "Create Schedule", "Click 'New Schedule' button"),
                  new DocumentationManager.TutorialStep(3, "Configure Timing", "Use cron expression or interval (e.g., 'Every 6 hours')"),
                  new DocumentationManager.TutorialStep(4, "Set Retention Policy", "Configure how many backups to keep"),
                  new DocumentationManager.TutorialStep(5, "Test Backup", "Click 'Backup Now' to verify configuration")
               )
            )
         );
      this.tutorials
         .add(
            new DocumentationManager.Tutorial(
               "database-query",
               "Querying Databases",
               "How to use the database browser to query SQLite databases",
               "advanced",
               20,
               List.of(
                  new DocumentationManager.TutorialStep(1, "Open Database Browser", "Click 'Database' in navigation menu"),
                  new DocumentationManager.TutorialStep(2, "Select Database", "Choose a database from the list"),
                  new DocumentationManager.TutorialStep(3, "View Tables", "Click on a table to see its schema"),
                  new DocumentationManager.TutorialStep(4, "Execute Query", "Use the query editor to write SELECT statements"),
                  new DocumentationManager.TutorialStep(5, "Export Results", "Download results as CSV or JSON")
               )
            )
         );
      LOGGER.info("Loaded {} tutorials", this.tutorials.size());
   }

   private void loadOrCreateFaqItems() {
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "change-port",
               "How do I change the dashboard port?",
               "To change the dashboard port:\n1. Stop your server\n2. Open `config/neoessentials/main.json`\n3. Find the `webDashboard` section\n4. Change `port` value (e.g., from 8080 to 8081)\n5. Save the file and restart your server\n\nExample:\n```json\n\"webDashboard\": {\n  \"enabled\": true,\n  \"port\": 8081,\n  \"bindAddress\": \"0.0.0.0\"\n}\n```\n",
               List.of("configuration", "network")
            )
         );
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "reset-password",
               "How do I reset the admin password?",
               "If you've forgotten the admin password:\n1. Open server console\n2. Execute: `/neoessentials resetpassword admin newpassword`\n3. Or edit `config/neoessentials/users.json` directly\n4. Log in with new credentials\n\nFor security, change the password again after logging in via the dashboard Settings page.\n",
               List.of("security", "authentication")
            )
         );
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "ssl-https",
               "Can I use HTTPS/SSL with the dashboard?",
               "Yes! The recommended approach is using a reverse proxy:\n\n**Using Nginx**:\n```nginx\nserver {\n    listen 443 ssl;\n    server_name dashboard.example.com;\n\n    ssl_certificate /path/to/cert.pem;\n    ssl_certificate_key /path/to/key.pem;\n\n    location / {\n        proxy_pass http://localhost:8080;\n        proxy_set_header Host $host;\n        proxy_set_header X-Real-IP $remote_addr;\n    }\n}\n```\n\n**Using Apache**:\n```apache\n<VirtualHost *:443>\n    ServerName dashboard.example.com\n    SSLEngine on\n    SSLCertificateFile /path/to/cert.pem\n    SSLCertificateKeyFile /path/to/key.pem\n\n    ProxyPass / http://localhost:8080/\n    ProxyPassReverse / http://localhost:8080/\n</VirtualHost>\n```\n",
               List.of("security", "network", "advanced")
            )
         );
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "performance-impact",
               "Does the dashboard affect server performance?",
               "The dashboard has minimal performance impact:\n- **Idle**: Negligible (< 1% CPU, ~50MB RAM)\n- **Active Use**: Moderate (2-5% CPU, ~100MB RAM)\n- **Heavy Queries**: Can spike temporarily\n\n**Tips to minimize impact**:\n1. Limit concurrent users\n2. Reduce log tail length\n3. Use pagination for large datasets\n4. Schedule heavy operations during low-traffic times\n5. Adjust metric collection intervals\n\nThe dashboard runs asynchronously and won't block game server threads.\n",
               List.of("performance", "optimization")
            )
         );
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "mobile-access",
               "Can I use the dashboard on mobile?",
               "Yes! The dashboard is responsive and works on mobile devices:\n- **Tablets**: Full desktop experience\n- **Phones**: Optimized mobile layout\n- **Touch Support**: Touch-friendly controls\n\n**Recommendations**:\n- Use landscape mode for better visibility\n- Some features work better on larger screens\n- Consider using desktop for complex tasks (database queries, bulk operations)\n\nTested on:\n- iOS Safari\n- Android Chrome\n- Mobile Firefox\n",
               List.of("mobile", "accessibility")
            )
         );
      this.faqItems
         .add(
            new DocumentationManager.FaqItem(
               "backup-location",
               "Where are backups stored?",
               "Backups are stored in the server's backup directory:\n- **Default Location**: `backups/` folder in server root\n- **Custom Location**: Can be configured in settings\n\n**Backup Structure**:\n```\nbackups/\n  ├── world_2025-10-15_10-30-00.zip\n  ├── world_nether_2025-10-15_10-30-00.zip\n  └── world_the_end_2025-10-15_10-30-00.zip\n```\n\n**Important Notes**:\n- Backups are compressed (ZIP format)\n- Includes world data, playerdata, and region files\n- Automatic cleanup based on retention policy\n- Manual backups are never auto-deleted\n",
               List.of("backups", "storage")
            )
         );
      LOGGER.info("Loaded {} FAQ items", this.faqItems.size());
   }

   private void loadOrCreateVideoTutorials() {
      this.videoTutorials
         .add(
            new DocumentationManager.VideoTutorial(
               "dashboard-overview",
               "Dashboard Overview and Features",
               "Complete tour of the NeoEssentials dashboard",
               "https://youtube.com/watch?v=example1",
               480,
               "beginner"
            )
         );
      this.videoTutorials
         .add(
            new DocumentationManager.VideoTutorial(
               "permission-system",
               "Understanding the Permission System",
               "Deep dive into permission nodes and inheritance",
               "https://youtube.com/watch?v=example2",
               720,
               "intermediate"
            )
         );
      LOGGER.info("Loaded {} video tutorials", this.videoTutorials.size());
   }

   public Map<String, DocumentationManager.DocumentationSection> getAllSections() {
      return new LinkedHashMap<>(this.sections);
   }

   public DocumentationManager.DocumentationSection getSection(String sectionId) {
      return this.sections.get(sectionId);
   }

   public Map<String, DocumentationManager.ApiEndpoint> getAllApiEndpoints() {
      return new LinkedHashMap<>(this.apiEndpoints);
   }

   public DocumentationManager.ApiEndpoint getApiEndpoint(String endpoint) {
      return this.apiEndpoints.get(endpoint);
   }

   public List<DocumentationManager.Tutorial> getAllTutorials() {
      return new ArrayList<>(this.tutorials);
   }

   public DocumentationManager.Tutorial getTutorial(String tutorialId) {
      return this.tutorials.stream().filter(t -> t.id.equals(tutorialId)).findFirst().orElse(null);
   }

   public List<DocumentationManager.FaqItem> getAllFaqItems() {
      return new ArrayList<>(this.faqItems);
   }

   public List<DocumentationManager.FaqItem> searchFaq(String query) {
      String lowerQuery = query.toLowerCase();
      return this.faqItems
         .stream()
         .filter(
            faq -> faq.question.toLowerCase().contains(lowerQuery)
                  || faq.answer.toLowerCase().contains(lowerQuery)
                  || faq.tags.stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery))
         )
         .collect(Collectors.toList());
   }

   public List<DocumentationManager.VideoTutorial> getAllVideoTutorials() {
      return new ArrayList<>(this.videoTutorials);
   }

   public DocumentationManager.VideoTutorial getVideoTutorial(String videoId) {
      return this.videoTutorials.stream().filter(v -> v.id.equals(videoId)).findFirst().orElse(null);
   }

   public static class ApiEndpoint {
      public final String endpoint;
      public final String name;
      public final String methods;
      public final String description;
      public final List<DocumentationManager.ApiExample> examples;
      public final String requiredPermission;

      public ApiEndpoint(
         String endpoint, String name, String methods, String description, List<DocumentationManager.ApiExample> examples, String requiredPermission
      ) {
         this.endpoint = endpoint;
         this.name = name;
         this.methods = methods;
         this.description = description;
         this.examples = examples;
         this.requiredPermission = requiredPermission;
      }
   }

   public static class ApiExample {
      public final String method;
      public final String endpoint;
      public final String requestBody;
      public final String description;
      public final String responseBody;

      public ApiExample(String method, String endpoint, String requestBody, String description, String responseBody) {
         this.method = method;
         this.endpoint = endpoint;
         this.requestBody = requestBody;
         this.description = description;
         this.responseBody = responseBody;
      }
   }

   public static class DocumentationSection {
      public final String id;
      public final String title;
      public final String description;
      public final String content;
      public final int order;

      public DocumentationSection(String id, String title, String description, String content, int order) {
         this.id = id;
         this.title = title;
         this.description = description;
         this.content = content;
         this.order = order;
      }
   }

   public static class FaqItem {
      public final String id;
      public final String question;
      public final String answer;
      public final List<String> tags;

      public FaqItem(String id, String question, String answer, List<String> tags) {
         this.id = id;
         this.question = question;
         this.answer = answer;
         this.tags = tags;
      }
   }

   public static class Tutorial {
      public final String id;
      public final String title;
      public final String description;
      public final String difficulty;
      public final int estimatedMinutes;
      public final List<DocumentationManager.TutorialStep> steps;

      public Tutorial(String id, String title, String description, String difficulty, int estimatedMinutes, List<DocumentationManager.TutorialStep> steps) {
         this.id = id;
         this.title = title;
         this.description = description;
         this.difficulty = difficulty;
         this.estimatedMinutes = estimatedMinutes;
         this.steps = steps;
      }
   }

   public static class TutorialStep {
      public final int stepNumber;
      public final String title;
      public final String instructions;

      public TutorialStep(int stepNumber, String title, String instructions) {
         this.stepNumber = stepNumber;
         this.title = title;
         this.instructions = instructions;
      }
   }

   public static class VideoTutorial {
      public final String id;
      public final String title;
      public final String description;
      public final String videoUrl;
      public final int durationSeconds;
      public final String difficulty;

      public VideoTutorial(String id, String title, String description, String videoUrl, int durationSeconds, String difficulty) {
         this.id = id;
         this.title = title;
         this.description = description;
         this.videoUrl = videoUrl;
         this.durationSeconds = durationSeconds;
         this.difficulty = difficulty;
      }
   }
}
