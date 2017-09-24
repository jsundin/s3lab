package s3lab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import s3lab.Looper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class ConfigurationManager {
  private static final String CONFIG_FILE = "/data/projects/s3lab/src/main/resources/config.json";
  private final Path configFile;
  private Looper onConfigurationChangedLooper;
  private ConfigurationChangedEvent onConfigurationChanged;
  private final ConfigurationWatcherThread watcherThread;

  public ConfigurationManager(Path configFile) {
    this.configFile = configFile;
    watcherThread = new ConfigurationWatcherThread();
  }

  public void setOnConfigurationChanged(Looper looper, ConfigurationChangedEvent onConfigurationChanged) {
    synchronized (watcherThread) {
      this.onConfigurationChangedLooper = looper;
      this.onConfigurationChanged = onConfigurationChanged;
    }
  }

  public void start() throws IOException {
    watcherThread.startWatcher();
  }

  public void stop() {
    watcherThread.running = false;
    watcherThread.interrupt();
  }

  private class ConfigurationWatcherThread extends Thread {
    private WatchService watcher;
    private WatchKey watchKey;
    private volatile boolean running;

    public ConfigurationWatcherThread() {
      super("ConfigurationWatcherThread");
    }

    private void notifyNewConfiguration(Configuration configuration) {
      synchronized (watcherThread) {
        if (onConfigurationChanged != null) {
          if (onConfigurationChangedLooper != null) {
            onConfigurationChangedLooper.post(() -> {
              onConfigurationChanged.onConfigurationChanged(configuration);
            });
          } else {
            onConfigurationChanged.onConfigurationChanged(configuration);
          }
        }
      }
    }

    private void startWatcher() throws IOException {
      Configuration firstConfiguration = loadConfiguration();
      System.out.println("> Loaded first configuration from '" + configFile + "'");
      notifyNewConfiguration(firstConfiguration);

      watcher = FileSystems.getDefault().newWatchService();
      watchKey = configFile.getParent().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
      start();
    }

    @Override
    public void run() {
      System.out.println("> ConfigurationWatcher watching '" + configFile + "'");
      running = true;

      boolean modified;
      for (;;) {
        WatchKey key = null;
        try {
          key = watcher.take();
        } catch (InterruptedException e) {
          if (!running) {
            break;
          }
        }

        if (key == null) {
          continue;
        }

        modified = false;
        for (WatchEvent<?> watchEvent : key.pollEvents()) {
          WatchEvent<Path> event = cast(watchEvent);
          Path context = event.context();
          if (context.equals(configFile.getFileName())) {
            modified = true;
          }
        }
        key.reset();

        if (modified) {
          System.out.println("> ConfigurationWatcher detected change in '" + configFile + "'");
          try {
            Configuration newConfiguration = loadConfiguration();
            notifyNewConfiguration(newConfiguration);
          } catch (IOException e) {
            System.err.println("> ConfigurationWatcher could not read configuration from '" + configFile + "'");
            e.printStackTrace();
          }
        }
      }

      try {
        watcher.close();
      } catch (IOException e) {
        System.err.println("Could not close watcher");
        e.printStackTrace();
      }

      System.out.println("> ConfigurationWatcher stopped watching '" + configFile + "'");
    }

    @SuppressWarnings("unchecked")
    private WatchEvent<Path> cast(WatchEvent<?> watchEvent) {
      return (WatchEvent<Path>) watchEvent;
    }
  }

  private Configuration loadConfiguration() throws IOException {
    ObjectMapper om = new ObjectMapper();
    Configuration c = om.readValue(new File(CONFIG_FILE), Configuration.class);
    System.out.println(c.getIncludes());
    System.out.println(c.getExcludes());
    return c;
  }

  public interface ConfigurationChangedEvent {
    void onConfigurationChanged(Configuration configuration);
  }
}
