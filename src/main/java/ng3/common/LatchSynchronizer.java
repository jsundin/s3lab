package ng3.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class LatchSynchronizer {
  private final List<Semaphore> semaphores = new ArrayList<>();

  public void addSemaphore(Semaphore semaphore) {
    synchronized (semaphores) {
      semaphores.add(semaphore);
    }
  }

  public void removeSemaphore(Semaphore semaphore) {
    synchronized (semaphores) {
      semaphores.remove(semaphore);
    }
  }

  public void releaseAllSemaphores() {
    synchronized (semaphores) {
      for (Semaphore semaphore : semaphores) {
        semaphore.release();
      }
    }
  }
}
