package ng3.common;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

public class SimpleThreadFactory implements ThreadFactory {
  private final String prefix;
  private final Map<String, Integer> prefixCounter = new HashMap<>();

  public SimpleThreadFactory(String prefix) {
    this.prefix = prefix;
    prefixCounter.put(prefix, 1);
  }

  @Override
  public Thread newThread(Runnable r) {
    int n = prefixCounter.get(prefix);
    String name = String.format("%s-%d", prefix, n);
    prefixCounter.put(prefix, n + 1);
    Map<String, String> mdcContext = MDC.getCopyOfContextMap();
    Thread t = new Thread(() -> {
      if (mdcContext != null) {
        MDC.setContextMap(mdcContext);
      }
      r.run();
    }, name);
    return t;
  }
}
