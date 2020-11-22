package grakkit;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import org.graalvm.polyglot.Value;

public final class Main extends JavaPlugin {

   public static CommandMap registry;
   public static Map<String, Custom> commands = new HashMap<>();

   static {
      try {
         URLClassLoader loader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
         Class<URLClassLoader> clazz = URLClassLoader.class;
         Method method = clazz.getDeclaredMethod("addURL", URL.class);
         method.setAccessible(true);
         method.invoke(loader, Main.locate(Main.class));
      } catch (Exception error) {
         throw new RuntimeException("Failed to add plugin to class path!", error);
      }
   }

   public void reload () {
      getServer().getPluginManager().disablePlugin(this);
      getServer().getPluginManager().enablePlugin(this);
   }

   public void register (String key, String name, String description, String usage, List<String> aliases, String permission, String message, String fallback, Value executor, Value tabCompleter) {
      
      // check if command already exists
      if (Main.commands.containsKey(key)) {

         // modify existing command
         Custom command = commands.get(key);
         command.setUsage(usage);
         command.setDescription(description);
         command.setPermission(permission);
         command.setPermissionMessage(message);
         command.executor = executor;
         command.tabCompleter = tabCompleter;
      } else {

         // create new command
         Custom command = new Custom(name, description, usage, aliases, permission, message, fallback, executor, tabCompleter);
         Main.registry.register(fallback, command);
         Main.commands.put(key, command);
      }
   }

   private static URL locate (Class<?> clazz) {
      try {
         URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
         if (location instanceof URL) return location;
      } catch (SecurityException | NullPointerException error) {
         // ignore errors to try other method
      }
      URL resource = clazz.getResource(clazz.getSimpleName() + ".class");
      if (resource instanceof URL) {
         String link = resource.toString();
         String suffix = clazz.getCanonicalName().replace('.', '/') + ".class";
         if (link.endsWith(suffix)) {
            String base = link.substring(0, link.length() - suffix.length()), path = base;
            if (path.startsWith("jar:")) path = path.substring(4, path.length() - 2);
            try {
               return new URL(path);
            } catch (MalformedURLException error) {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Override
   public void onLoad() {
      try {

         // expose command map (reflection)
         Field internal = getServer().getClass().getDeclaredField("commandMap");
         internal.setAccessible(true);
         Main.registry = (CommandMap) internal.get(getServer());
      } catch (Exception error) {

         // handle init errors and exit
         error.printStackTrace(System.err);
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   @Override
   public void onEnable() {

      // de-reference executors and tab-completers for each command
      Main.commands.values().forEach(command -> {
         command.executor = Value.asValue(new Object());
         command.tabCompleter = Value.asValue(new Object());
      });

      // create plugin folder
      this.getDataFolder().mkdir();

      // copy default config from resources
      this.getConfig().options().copyDefaults(true);

      // save config
      this.saveDefaultConfig();

      // get index file
      File index = Paths.get(getDataFolder().getPath(), getConfig().getString("main", "index.js")).toFile();

      // load context
      if (Core.load(index)) {
      
         // begin thread tick loop
         this.getServer().getScheduler().runTaskTimer(this, Core::tick, 0, 1);
      } else {

         // handle failed init
         this.getServer().getLogger().severe("The entry point specified \"" + index.getPath().replace('\\', '/') + "\" could not be found. Create this file and reload the plugin.");
         
         // disable plugin
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   @Override
   public void onDisable() {
   }
}