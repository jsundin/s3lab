package throttling;

import com.google.common.util.concurrent.RateLimiter;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author johdin
 * @since 2017-12-07
 */
public class ThrottledDigestInputStream extends InputStream {
  private final InputStream delegate;
  private final RateLimiter rateLimiter;
  private final MessageDigest md;

  public ThrottledDigestInputStream(InputStream inputStream, RateLimiter rateLimiter) throws NoSuchAlgorithmException {
    this.delegate = inputStream;
    this.rateLimiter = rateLimiter;
    md = MessageDigest.getInstance("MD5");
  }

  public ThrottledDigestInputStream(InputStream inputStream, long bytesPerSecond) throws NoSuchAlgorithmException {
    this(inputStream, RateLimiter.create(bytesPerSecond));
  }

  public byte[] getDigest() {
    return md.digest();
  }

  private void rateLimit(int n) {
    rateLimiter.acquire(n);
  }

  @Override
  public int read(byte[] b) throws IOException {
    int n = delegate.read(b);
    if (n > 0) {
      md.update(b, 0, n);
      rateLimit(n);
    }
    return n;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int n = delegate.read(b, off, len);
    if (n > 0) {
      md.update(b, off, n);
      rateLimit(n);
    }
    return n;
  }

  @Override
  public int read() throws IOException {
    int b = delegate.read();
    if (b > 0) {
      md.update((byte) b);
      rateLimit(1);
    }
    return b;
  }

  @Override
  public long skip(long n) throws IOException {
    return delegate.skip(n);
  }

  @Override
  public int available() throws IOException {
    return delegate.available();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    delegate.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    delegate.reset();
  }

  @Override
  public boolean markSupported() {
    return delegate.markSupported();
  }
}
