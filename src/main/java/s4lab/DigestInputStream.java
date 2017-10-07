package s4lab;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestInputStream extends InputStream {
  private final InputStream delegate;
  private final MessageDigest md;

  public DigestInputStream(InputStream delegate) throws NoSuchAlgorithmException {
    this.delegate = delegate;
    md = MessageDigest.getInstance("MD5");
  }

  public byte[] getDigest() {
    return md.digest();
  }

  @Override
  public int read() throws IOException {
    int v = delegate.read();
    md.update((byte) v);
    return v;
  }

  @Override
  public int read(byte[] b) throws IOException {
    int v = delegate.read(b);
    md.update(b, 0, v);
    return v;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int v = delegate.read(b, off, len);
    md.update(b, off, v);
    return v;
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
  public void mark(int readlimit) {
    delegate.mark(readlimit);
  }

  @Override
  public void reset() throws IOException {
    delegate.reset();
  }

  @Override
  public boolean markSupported() {
    return delegate.markSupported();
  }
}
