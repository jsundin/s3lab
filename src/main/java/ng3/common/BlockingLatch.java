package ng3.common;

import java.util.concurrent.CountDownLatch;

public class BlockingLatch extends Thread {
  private final CountDownLatch countDownLatch = new CountDownLatch(1);

  public BlockingLatch(String name) {
    super(name);
  }

  public void release() {
    countDownLatch.countDown();
  }

  public void joinUninterruptibly() {
    while (true) {
      try {
        join();
        break;
      } catch (InterruptedException ignored) {
        interrupted();
      }
    }
  }

  @Override
  public void run() {
    while (true) {
      try {
        countDownLatch.await();
        break;
      } catch (InterruptedException ignored) {
        Thread.interrupted();
      }
    }
  }
}
