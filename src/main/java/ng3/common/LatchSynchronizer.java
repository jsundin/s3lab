package ng3.common;

import java.util.ArrayList;
import java.util.List;

public class LatchSynchronizer {
  private final List<BlockingLatch> latches = new ArrayList<>();

  public void addLatch(BlockingLatch latch) {
    synchronized (latches) {
      latches.add(latch);
    }
  }

  public void removeLatch(BlockingLatch latch) {
    synchronized (latches) {
      latches.remove(latch);
    }
  }

  public void releaseLatches() {
    synchronized (latches) {
      for (BlockingLatch latch : latches) {
        latch.release();
      }
    }
  }
}
