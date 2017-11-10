package ng3.common;

import java.util.ArrayList;
import java.util.List;

/**
 * @author johdin
 * @since 2017-11-10
 */
public class ShutdownSynchronizer {
  private final List<Runnable> listeners = new ArrayList<>();

  public void addListener(Runnable listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public void removeListener(Runnable listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  public void notifyListeners() {
    synchronized (listeners) {
      for (Runnable listener : listeners) {
        listener.run();
      }
    }
  }
}
