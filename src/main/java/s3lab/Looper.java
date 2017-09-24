package s3lab;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Looper {
  private static final BlockingQueue<LooperMessage> queue = new LinkedBlockingQueue<>();

  private void internalLoop(RunnableHandler rh, MessageHandler mh) {
    System.out.println("> Looper running on '" + Thread.currentThread().getName() + "'");
    boolean finished = false;
    do {
      LooperMessage m;
      try {
        m = queue.take();
      } catch (InterruptedException e) {
        continue;
      }

      switch (m.messageType) {
        case EXECUTE:
          if (rh == null) {
            System.err.println("EXECUTE without a runnableHandler is not allowed");
          } else {
            rh.run(m.runnable);
          }
          break;

        case MESSAGE:
          if (mh == null) {
            System.err.println("MESSAGE without a messageHandler is not allowed");
          } else {
            mh.handleMessage(m.message);
          }
          break;

        case FINISH:
          finished = true;
          break;

        default:
          System.err.println("Unknown messageType: " + m.messageType);
      }
    } while (!finished);
    System.out.println("> Looper finished on '" + Thread.currentThread().getName() + "'");
  }

  public void loop(MessageHandler mh) {
    internalLoop(null, mh);
  }

  public void loop(RunnableHandler rh) {
    internalLoop(rh, null);
  }

  public void loop(RunnableHandler rh, MessageHandler mh) {
    internalLoop(rh, mh);
  }

  public void loop() {
    internalLoop(Runnable::run, null);
  }

  public void post(Runnable r) {
    LooperMessage m = new LooperMessage(LooperMessageType.EXECUTE);
    m.runnable = r;
    queue.add(m);
  }

  public void post(Message message) {
    LooperMessage m = new LooperMessage(LooperMessageType.MESSAGE);
    m.message = message;
    queue.add(m);
  }

  public void finish() {
    LooperMessage m = new LooperMessage(LooperMessageType.FINISH);
    queue.add(m);
  }

  private enum LooperMessageType {
    EXECUTE,
    MESSAGE,
    FINISH
  }

  private class LooperMessage {
    private LooperMessageType messageType;
    private Runnable runnable;
    private Message message;

    public LooperMessage(LooperMessageType messageType) {
      this.messageType = messageType;
    }
  }

  public interface Message {
  }

  public interface RunnableHandler {
    void run(Runnable r);
  }

  public interface MessageHandler {
    void handleMessage(Message m);
  }
}
