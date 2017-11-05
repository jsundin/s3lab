package waitlab;

import java.util.Random;
import java.util.concurrent.Semaphore;

public class SemLab {
  static Semaphore s;
  public static void main(String[] args) throws Exception {
    s = new Semaphore(0);
    int threads = 2 + Math.abs(new Random().nextInt() % 10);
    System.out.println("Threads: " + threads);
    for (int i = 0; i < threads; i++) {
      new Thread(new MyTask()).start();
    }
    s.acquire(threads);
    System.out.println("Semaphore acquired");
  }

  static class MyTask implements Runnable {
    @Override
    public void run() {
      System.out.println("Waiting...");
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      s.release();
    }
  }
}
