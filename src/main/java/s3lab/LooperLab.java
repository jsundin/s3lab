package s3lab;

public class LooperLab {
  public final Looper looper = new Looper();

  public static void main(String[] args) {
    new LooperLab().runWithRunnables();
  }

  public void runWithMessages() {
    new Thread(rWithMessages).start();
    looper.loop((Looper.MessageHandler) System.out::println);
  }

  private Runnable rWithMessages = () -> {
    System.out.println("(thread is running)");
    for (int i = 0; i < 10; i++) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      looper.post(new IntegerMessage(i));
    }

    looper.finish();
    System.out.println("(thread has stopped)");
  };

  public void runWithRunnables() {
    new Thread(rWithRunnables).start();
    new Thread(() -> {
      looper.loop();
    }).start();
  }

  private Runnable rWithRunnables = () -> {
    System.out.println("(thread is running)");
    for (int i = 0; i < 10; i++) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      looper.post(() -> {
        System.out.println(Thread.currentThread().getName());
      });
    }

    looper.finish();
    System.out.println("(thread has stopped)");
  };

  public class IntegerMessage implements Looper.Message {
    private final int i;

    public IntegerMessage(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }

    @Override
    public String toString() {
      return "IntegerMessage{" +
              "i=" + i +
              '}';
    }
  }
}
