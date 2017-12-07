package throttling;

import org.apache.commons.codec.binary.Hex;

import java.io.FileInputStream;

/**
 * @author johdin
 * @since 2017-12-07
 */
public class Throttling {
  public static void main(String[] args) throws Exception {
    long rate = 1024 * 1024 * 1024;
    //long rate = 1;

    long t0 = System.currentTimeMillis();
    byte[] buf = new byte[1024];
    long bytes = 0;
    //ThrottledDigestInputStream in = new ThrottledDigestInputStream(new FileInputStream("/home/johdin/Documents/hydro/Desktop/complete-run-20160421/run.js-1st/dist/svs-hy-p_wl_98000-SE.3006.gml"), rate);
/*
    try (FileOutputStream out = new FileOutputStream("/tmp/gml")) {
      bytes += IOUtils.copy(in, out);
    }
    in.close();
*/
    try (ThrottledDigestInputStream in = new ThrottledDigestInputStream(new FileInputStream("/home/johdin/Documents/hydro/Desktop/complete-run-20160421/run.js-1st/dist/svs-hy-p_wl_98000-SE.3006.gml"), rate)) {
      int len;

      while ((len = in.read(buf)) >= 0) {
        //out.write(buf, 0, len);
        bytes += len;
        double s = (double) (System.currentTimeMillis() - t0) / 1000;
        double b = (double) bytes;
        long bps = (long) (b / s);
        System.out.println("bytes per sec: " + bps + " [" + (rate - bps) + "]");
      }
    }

    double s = (double) (System.currentTimeMillis() - t0) / 1000;
    double b = (double) bytes;
    System.out.println("b: " + b);
    System.out.println("t: " + s + "s");
    System.out.println("bytes per sec: " + (b / s));

    //System.out.println("md: " + Hex.encodeHexString(in.getDigest()));
  }

}
