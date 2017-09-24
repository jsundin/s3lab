package s3lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Looper {
  private static final Logger logger = LoggerFactory.getLogger(Looper.class);
  private static final BlockingQueue<LooperMessage> queue = new LinkedBlockingQueue<>();

  private void internalLoop(RunnableHandler rh, MessageHandler mh) {
    logger.debug("Looper running in thread '" + Thread.currentThread().getName() + "'");
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
            logger.error("EXECUTE without a runnableHandler is not allowed");
          } else {
            rh.run(m.runnable);
          }
          break;

        case MESSAGE:
          if (mh == null) {
            logger.error("MESSAGE without a messageHandler is not allowed");
          } else {
            mh.handleMessage(m.message);
          }
          break;

        case FINISH:
          finished = true;
          break;

        default:
          logger.error("Unknown messageType: {}", m.messageType);
      }
    } while (!finished);
    logger.debug("Looper finished in thread '" + Thread.currentThread().getName() + "'");
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
