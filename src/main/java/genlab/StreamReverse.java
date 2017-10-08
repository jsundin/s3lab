package genlab;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s5lab.notification.Notification;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class StreamReverse {
  private static final Logger logger = LoggerFactory.getLogger(StreamReverse.class);
  public static void main(String[] args) throws Exception {
    FileInputStream fileIn = new FileInputStream("/etc/passwd");
    InputStream gzIn = gzipInputStream(fileIn);
    uploadFile(gzIn);
  }

  private static void uploadFile(InputStream is) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(is, baos);
    logger.info("uploadFile() received {} bytes", baos.size());

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

    GZIPInputStream gzIn = new GZIPInputStream(bais);
    int bytes = IOUtils.copy(gzIn, System.out);
    logger.info("uploadFile() resulted in {} bytes decompressed", bytes);
  }

  private static InputStream gzipInputStream(InputStream in) throws IOException {
    return new GZipInputStreamThread(in).zip();
    /*
    PipedInputStream zipped = new PipedInputStream();
    PipedOutputStream pipe = new PipedOutputStream(zipped);
    new Thread(
            () -> {
              try(OutputStream zipper = new GZIPOutputStream(pipe)){
                int copied = IOUtils.copy(in, zipper);
                logger.info("Thread copied {} bytes", copied);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
    ).start();
    return zipped;
    */
  }

  private static class GZipInputStreamThread extends Thread {
    private static int threadIndex = 0;
    private final InputStream in;
    private final PipedInputStream zipped = new PipedInputStream();
    private volatile Throwable error;

    public GZipInputStreamThread(InputStream in) {
      super("GZipInputStreamThread-" + (threadIndex++));
      this.in = in;
    }

    public InputStream zip() {
      start();
      return zipped;
    }

    public boolean hasError() {
      return error != null;
    }

    public Throwable getError() {
      return error;
    }

    @Override
    public void run() {
      try {
        PipedOutputStream pipe = new PipedOutputStream(zipped);
        try (OutputStream zipper = new GZIPOutputStream(pipe)) {
          throw new Exception("Hej");
          //IOUtils.copy(in, zipper);
        }
      } catch (Throwable t) {
        error = t;
      }
    }
  }
}
