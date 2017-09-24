package s3lab.config;

import s3lab.Looper;

import java.nio.file.Paths;

public class ConfigurationManagerUsage {
  private static final String CONFIG_FILE = "/data/projects/s3lab/src/main/resources/config.json";

  public static void main(String[] args) throws Exception {
    new ConfigurationManagerUsage().run();
  }

  public final Looper looper = new Looper();

  private void run() throws Exception {
    ConfigurationManager cm = new ConfigurationManager(Paths.get(CONFIG_FILE));

    // callback - vi sätter main-loopern för att garantera att det körs i main-tråden
    cm.setOnConfigurationChanged(looper, c -> {
      System.out.println("# Now configuration change is happening in '" + Thread.currentThread().getName() + "'" + " [" + c + "]");
    });
    cm.start();

    // den här tråden har vi ENDAST för att slå ihjäl applikationen efter en viss tid
    new Thread(() -> {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("# Killing looper from '" + Thread.currentThread().getName() + "'");
      looper.finish();
    }).start();

    // hantera meddelanden
    looper.loop();

    // döda cm
    cm.stop();
  }
}
