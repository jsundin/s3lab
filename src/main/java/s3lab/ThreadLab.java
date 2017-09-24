package s3lab;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadLab {
  private static final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

  public static void main(String[] args) throws Exception {
    new ThreadLab().doIt();
  }

  public void doIt() throws Exception {
    new Thread(r).start();
    boolean finish = false;
    do {
      Message m = queue.take();
      switch (m.messageType) {
        case EXECUTE:
          System.out.println("(got execute message)");
          m.r.run();
          break;

        case FINISH:
          System.out.println("(got finish message)");
          finish = true;
          break;
      }
    } while (!finish);
  }

  public void finish() {
    Message m = new Message(MessageType.FINISH);
    queue.add(m);
  }

  public void post(Runnable r) {
    Message m = new Message(MessageType.EXECUTE);
    m.setR(r);
    queue.add(m);
  }

  private enum MessageType {
    EXECUTE,
    FINISH
  }

  private class Message {
    private MessageType messageType;
    private Runnable r;

    public Message(MessageType messageType) {
      this.messageType = messageType;
    }

    public void setR(Runnable r) {
      this.r = r;
    }
  }

  private Runnable r = () -> {
    System.out.println("(börjar)");
    System.out.println("Tråden heter " + Thread.currentThread().getName());
    for (int i = 0; i < 10; i++) {
      post(() -> System.out.println(Thread.currentThread().getName()));
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    System.out.println("(klar)");
    finish();
  };
}
