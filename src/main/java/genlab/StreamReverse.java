package genlab;

import org.apache.commons.io.IOUtils;

import java.io.*;

public class StreamReverse {
  public static void main(String[] args) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream("hejsan hoppsan".getBytes());



    PipedInputStream pis = new PipedInputStream();
    PipedOutputStream pos = new PipedOutputStream();
    pis.connect(pos);
    //GZIPOutputStream gzOut = new GZIPOutputStream(pos);


    //uploadFile(pis);
  }

  public static void uploadFile(InputStream is) throws Exception {
    IOUtils.copy(is, System.out);
  }

  class MyInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      return 0;
    }
  }
}
